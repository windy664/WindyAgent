package org.windy.windyagent.platform.bukkit

import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.AgentConfig
import org.windy.windyagent.Messages
import org.windy.windyagent.buildCommandGuard
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.platform.bukkit.behavior.BehaviorService
import org.windy.windyagent.platform.bukkit.item.ItemService
import org.windy.windyagent.platform.bukkit.skill.SkillEngine
import org.windy.windyagent.skill.SkillRegistry
import org.windy.windyagent.safety.AuditLog
import org.windy.windyagent.safety.PendingApprovals
import org.windy.windyagent.bus.RedisBus
import org.windy.windyagent.bus.socket.SocketClientBus
import org.windy.windyagent.bus.socket.SocketHubBus
import org.windy.windyagent.ui.WindyLog
import org.windy.windyagent.profile.ProfileDataRegistry
import org.windy.windyagent.platform.bukkit.cmi.CmiProfileSource
import org.windy.windyagent.platform.bukkit.profile.PlayerProfileListener
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * WindyAgent Bukkit 插件入口。**配置与 Velocity 统一**：只读插件数据目录下的
 * windyagent-config.yml（首启动从 jar 内模板释放），不再用 Bukkit 原生 config.yml。
 *
 * 按 `deployment.mode` 决定形态：
 *  - provider（默认）：纯能力提供方，连总线（redis/socket 客户端）执行中心下发的动作。
 *  - standalone：本机嵌入式 Agent，无总线（单台 Paper 服）。
 *  - hub：嵌入式 Agent + 总线中枢（socket/redis 中心侧），服务本服并派发到其它子服。
 */
class WindyAgentBukkitPlugin : JavaPlugin() {

    private var bus: MessageBus? = null
    private var behavior: BehaviorService? = null
    /** auto 模式下 standalone 启动的 Agent runner，探测到中枢后需要 shutdown。 */
    private var standaloneRunner: BukkitAgentRunner? = null
    /** auto-standalone 后台探测调度器。 */
    private var switchProbe: ScheduledExecutorService? = null
    /** 画像数据注册表（CMI / LuckPerms / … 各 Source 自管缓存）。 */
    private val profileRegistry = ProfileDataRegistry()

    override fun onEnable() {
        val cfg = runCatching { AgentConfig.load(dataFolder.toPath()) }.getOrElse {
            logger.severe(WindyLog.tag("Boot", "加载 windyagent-config.yml 失败，插件未启用：${it.message}"))
            return
        }
        Messages.init(cfg.language())
        // 角色判定：mode=auto 时探测中枢自动选 provider/standalone；显式 mode 直接用
        val (mode, note) = resolveMode(cfg)
        // auto→provider 且未命名：自动命名为 server-<端口> 写回配置（中心按子服自报名寻址，无需预先登记）
        if (cfg.mode() == "auto" && mode == "provider" && cfg.serverName().isBlank()) {
            cfg.ensureServerName("server-${server.port}")
        }
        val role = when (mode) {
            "standalone" -> "嵌入式 Agent · standalone"
            "hub" -> "嵌入式 Agent + 总线中枢 · hub"
            else -> "能力提供方 · provider"
        }
        logger.info(WindyLog.banner(buildList {
            add("角色" to role)
            add("判定" to note)
            add("子服名" to cfg.serverName().ifBlank { "(未设)" })
            if (mode != "standalone") add("传输" to cfg.crossServerTransport())
        }))
        // 行为采集与部署形态无关：事件都在本子服发生，三种模式都跑
        behavior = runCatching { BehaviorService.build(this, cfg)?.also { it.start() } }
            .getOrElse { logger.warning(WindyLog.tag("Behavior", "行为采集启动失败：${it.message}")); null }
        // 画像数据源注册（CMI 等插件的玩家数据缓存），与模式无关
        profileRegistry.register(CmiProfileSource(this))
        server.pluginManager.registerEvents(PlayerProfileListener(profileRegistry), this)
        logger.info(WindyLog.tag("Profile", "画像数据源已注册 — ${profileRegistry.available().size} 个可用"))
        when (mode) {
            "standalone" -> {
                startStandalone(cfg)
                // auto 模式下 standalone = 中枢暂未上线，后台持续探测，上线后自动切 provider
                if (cfg.mode() == "auto") scheduleAutoSwitchProbe(cfg)
            }
            "hub" -> startHub(cfg)
            else -> startProvider(cfg)
        }
    }

    /**
     * 解析部署角色。返回 (实际角色, 判定说明)。
     * - 显式 provider/standalone/hub：原样返回。
     * - auto：仅 transport=socket 时对中枢地址做一次短超时 TCP 探测——连得上=provider，连不上=standalone；
     *   非 socket（redis）下 auto 直接当 provider（redis=有意搭集群）。
     */
    private fun resolveMode(cfg: AgentConfig): Pair<String, String> {
        val raw = cfg.mode()
        if (raw != "auto") return raw to "显式配置 mode: $raw"
        val transport = cfg.crossServerTransport()
        if (transport != "socket") return "provider" to "auto：transport=$transport（非 socket）→ provider"
        val host = cfg.socketHost().let { if (it == "0.0.0.0") "127.0.0.1" else it }
        val port = cfg.socketPort()
        return if (hubReachable(host, port))
            "provider" to "auto：探测到中枢 $host:$port → provider"
        else
            "standalone" to "auto：未探测到中枢（$host:$port）→ standalone 单机"
    }

    /** 对中枢地址做一次短超时 TCP 探测，判断 SocketHubBus 是否在监听。 */
    private fun hubReachable(host: String, port: Int, timeoutMs: Int = 1200): Boolean =
        runCatching {
            java.net.Socket().use { it.connect(java.net.InetSocketAddress(host, port), timeoutMs); true }
        }.getOrDefault(false)

    /** provider：连总线 + 能力 handler。 */
    private fun startProvider(cfg: AgentConfig) {
        val serverName = cfg.serverName().takeIf { it.isNotBlank() }
        if (serverName == null) {
            logger.severe(WindyLog.tag("Provider", "provider 模式需在 windyagent-config.yml 设置 deployment.server-name。总线未启用。"))
            return
        }
        val transport = cfg.crossServerTransport()
        // provider 经 executeCommand 直接执行中心已 gate 的命令，pending 在此模式不参与
        val actions = BukkitActions(this, buildCommandGuard(cfg), AuditLog(dataFolder.toPath().resolve("audit.log")), PendingApprovals())
        val itemService = ItemService.build(this, cfg)?.also { it.warmup() }
        // 服主编写的 Groovy 技能：provider 无嵌入式 Agent，故技能经 run_skill 动作执行，
        // 并把技能目录随能力目录推回中心（中心用 search_capabilities 查、run_skill_on_server 调）。
        val skills = if (cfg.skillsEnabled())
            SkillRegistry(dataFolder.toPath().resolve(cfg.skillsDir()).toFile()) else null
        val skillEngine = skills?.let { SkillEngine(this, actions, cfg.skillTimeoutSec()) }
        skills?.let { logger.info(WindyLog.tag("Skill", "技能已加载 — ${it.reload()} 个（skills/ 目录）")) }
        val handler = BukkitCapabilityHandler(this, actions, itemService, behavior, skills, skillEngine, profileRegistry)

        bus = runCatching {
            buildClientBus(cfg, transport).also { it.listen(serverName) { req -> handler.handle(req) } }
        }.getOrElse {
            logger.severe(WindyLog.tag("Bus", "总线启动失败，能力提供方未启用：${it.message}"))
            null
        }
        bus?.let { b ->
            logger.info(WindyLog.tag("Provider", "能力提供方已启动 — server-name: $serverName, transport: $transport"))
            // 启动后建能力目录（含技能条目），经总线推回中心（取代中心每次现扫）
            val capSync = CapabilitySync(this, actions, serverName, { cat -> b.publishCatalog(CapabilitySync.toJson(cat)) },
                { skills?.let { it.reload(); it.all().map { d -> d.toCapabilityCommand() } } ?: emptyList() }
            )
            // 中心下发技能（skill_save/delete/reload）后重建目录，让新脚本技能尽快进目录被搜到
            handler.onSkillsChanged = { capSync.rebuildSoon() }
            capSync.start()
        }
    }

    /** standalone：仅嵌入式 Agent。 */
    private fun startStandalone(cfg: AgentConfig) {
        if (cfg.needsLlmSetup()) { llmSetupHint("standalone"); return }
        val runner = BukkitAgentRunner(this)
        if (runner.start(cfg, profileRegistry = profileRegistry)) {
            standaloneRunner = runner
            logger.info(WindyLog.tag("Boot", "已启动（standalone：本机嵌入式 Agent）"))
        } else {
            logger.severe(WindyLog.tag("Boot", "standalone 启动失败，请检查 windyagent-config.yml 的 LLM 配置"))
        }
    }

    /**
     * 嵌入式 Agent（standalone/hub）未配 LLM key 时的控制台引导。
     * Bukkit 端无 WebUI，只能提示手改配置；provider 模式不需 key（中心持有 LLM），不触发。
     */
    private fun llmSetupHint(mode: String) {
        val path = dataFolder.toPath().resolve("windyagent-config.yml")
        logger.warning(WindyLog.banner(buildList {
            add("状态" to "⚠ 尚未配置 LLM API Key（$mode 模式需要）")
            add("请编辑" to path.toString())
            add("填写" to "llm.api-key（claude/openai 必填；或设 provider: ollama 用本地模型免 key）")
            add("生效" to "保存后重启本服")
        }))
    }

    /** hub：嵌入式 Agent + 总线中枢。 */
    private fun startHub(cfg: AgentConfig) {
        if (cfg.needsLlmSetup()) { llmSetupHint("hub"); return }
        val transport = cfg.crossServerTransport()
        bus = runCatching { buildHubBus(cfg, transport).also { it.startReplyListener() } }.getOrElse {
            logger.severe(WindyLog.tag("Bus", "中枢总线启动失败：${it.message}"))
            null
        }
        if (BukkitAgentRunner(this).start(cfg, remoteBus = bus, remoteTimeoutMs = cfg.remoteTimeoutMs(), profileRegistry = profileRegistry)) {
            logger.info(WindyLog.tag("Boot", "已启动（hub：嵌入式 Agent + 总线中枢，transport: $transport）"))
        } else {
            logger.severe(WindyLog.tag("Boot", "hub 启动失败，请检查 windyagent-config.yml 的 LLM 配置"))
        }
    }

    /** 子服侧总线（provider）：redis 客户端 / socket 客户端。 */
    private fun buildClientBus(cfg: AgentConfig, transport: String): MessageBus = when (transport) {
        "socket" -> SocketClientBus(cfg.socketHost(), cfg.socketPort(), cfg.socketSecret().ifBlank { null })
        else -> RedisBus(cfg.redisHost(), cfg.redisPort(), cfg.redisPassword())
    }

    /** 中枢侧总线（hub）：redis 中心 / socket 中枢。 */
    private fun buildHubBus(cfg: AgentConfig, transport: String): MessageBus = when (transport) {
        "redis" -> RedisBus(cfg.redisHost(), cfg.redisPort(), cfg.redisPassword())
        else -> SocketHubBus(cfg.socketHost(), cfg.socketPort(), cfg.socketSecret().ifBlank { null })
    }

    /**
     * auto-standalone 后台探测：每 30s TCP 探中枢，连上后自动切换到 provider。
     * 探测到后停止探测器，切换完成。
     */
    private fun scheduleAutoSwitchProbe(cfg: AgentConfig) {
        val transport = cfg.crossServerTransport()
        if (transport != "socket") return
        val host = cfg.socketHost().let { if (it == "0.0.0.0") "127.0.0.1" else it }
        val port = cfg.socketPort()
        val probe = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "windyagent-autoswitch-probe").apply { isDaemon = true }
        }
        switchProbe = probe
        probe.scheduleAtFixedRate({
            runCatching {
                if (hubReachable(host, port)) {
                    logger.info(WindyLog.tag("Switch", "探测到中枢 $host:$port 已上线，自动切换到 provider 模式"))
                    probe.shutdown()
                    switchProbe = null
                    // 切换：关 standalone 壳 → 起 provider 壳（共用组件 behavior 不动）
                    standaloneRunner?.shutdown()
                    standaloneRunner = null
                    startProviderWithBus(cfg)
                }
            }
        }, 30, 30, TimeUnit.SECONDS)
        logger.info(WindyLog.tag("Switch", "auto 模式：后台每 30s 探测中枢 $host:$port，上线后自动切 provider"))
    }

    /** 切换用的 provider 启动：复用 onEnable 已创建的 behavior，其余与 startProvider 一致。 */
    private fun startProviderWithBus(cfg: AgentConfig) {
        val serverName = cfg.serverName().takeIf { it.isNotBlank() }
        if (serverName == null) {
            // auto→provider 且未命名：自动命名
            cfg.ensureServerName("server-${server.port}")
        }
        val finalName = cfg.serverName()
        val transport = cfg.crossServerTransport()
        val actions = BukkitActions(this, buildCommandGuard(cfg), AuditLog(dataFolder.toPath().resolve("audit.log")), PendingApprovals())
        val itemService = ItemService.build(this, cfg)?.also { it.warmup() }
        val skills = if (cfg.skillsEnabled())
            SkillRegistry(dataFolder.toPath().resolve(cfg.skillsDir()).toFile()) else null
        val skillEngine = skills?.let { SkillEngine(this, actions, cfg.skillTimeoutSec()) }
        skills?.let { logger.info(WindyLog.tag("Skill", "技能已加载 — ${it.reload()} 个（skills/ 目录）")) }
        val handler = BukkitCapabilityHandler(this, actions, itemService, behavior, skills, skillEngine, profileRegistry)
        bus = runCatching {
            buildClientBus(cfg, transport).also { it.listen(finalName) { req -> handler.handle(req) } }
        }.getOrElse {
            logger.severe(WindyLog.tag("Bus", "总线启动失败，切换回 provider 未完成：${it.message}"))
            null
        }
        bus?.let { b ->
            logger.info(WindyLog.tag("Provider", "已切换到 provider 模式 — server-name: $finalName, transport: $transport"))
            val capSync = CapabilitySync(this, actions, finalName, { cat -> b.publishCatalog(CapabilitySync.toJson(cat)) },
                { skills?.let { it.reload(); it.all().map { d -> d.toCapabilityCommand() } } ?: emptyList() }
            )
            handler.onSkillsChanged = { capSync.rebuildSoon() }
            capSync.start()
        }
    }

    override fun onDisable() {
        switchProbe?.shutdownNow()
        switchProbe = null
        standaloneRunner?.shutdown()
        standaloneRunner = null
        behavior?.stop()
        behavior = null
        bus?.close()
        bus = null
        logger.info(WindyLog.tag("Boot", "WindyAgent 已停止"))
    }
}
