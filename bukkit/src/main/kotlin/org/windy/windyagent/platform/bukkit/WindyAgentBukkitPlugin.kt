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
import org.windy.windyagent.skill.toCapabilityCommand
import org.windy.windyagent.safety.AuditLog
import org.windy.windyagent.safety.PendingApprovals
import org.windy.windyagent.bus.RedisBus
import org.windy.windyagent.bus.socket.SocketClientBus
import org.windy.windyagent.bus.socket.SocketHubBus
import org.windy.windyagent.ui.WindyLog
import org.windy.windyagent.profile.ProfileDataRegistry
import org.windy.windyagent.platform.bukkit.profile.BehaviorProfileSource
import org.windy.windyagent.platform.bukkit.profile.PapiProfileSource
import org.windy.windyagent.platform.bukkit.profile.PlayerProfileListener

/**
 * WindyAgent Bukkit 插件入口。**配置与 Velocity 统一**：只读插件数据目录下的
 * windyagent-config.yml（首启动从 jar 内模板释放），不再用 Bukkit 原生 config.yml。
 *
 * 形态由 `deployment.mode` **显式指定**（服主自己选，不再自动探测）：
 *  - standalone（默认）：本机嵌入式 Agent，无总线（单台 Paper 服）。
 *  - provider：纯能力提供方，连总线（redis/socket 客户端）执行中心下发的动作（Velocity 主导群组）。
 *  - hub：嵌入式 Agent + 总线中枢（socket/redis 中心侧），服务本服并派发到其它子服。
 */
class WindyAgentBukkitPlugin : JavaPlugin() {

    private var bus: MessageBus? = null
    private var behavior: BehaviorService? = null
    /** standalone 模式启动的 Agent runner，关服时需 shutdown。 */
    private var standaloneRunner: BukkitAgentRunner? = null
    /** 画像数据注册表（CMI / LuckPerms / … 各 Source 自管缓存）。 */
    private val profileRegistry = ProfileDataRegistry()

    override fun onEnable() {
        val cfg = runCatching { AgentConfig.load(dataFolder.toPath()) }.getOrElse {
            logger.severe(WindyLog.tag("Boot", "加载 windyagent-config.yml 失败，插件未启用：${it.message}"))
            return
        }
        Messages.init(cfg.language())
        // 角色由配置显式指定（不再自动探测中枢）
        val mode = resolveMode(cfg)
        // provider 未命名：自动命名为 server-<端口> 写回配置（中心按子服自报名寻址，无需预先登记）
        if (mode == "provider" && cfg.serverName().isBlank()) {
            cfg.ensureServerName("server-${server.port}")
        }
        val role = when (mode) {
            "standalone" -> "嵌入式 Agent · standalone"
            "hub" -> "嵌入式 Agent + 总线中枢 · hub"
            else -> "能力提供方 · provider"
        }
        logger.info(WindyLog.banner(buildList {
            add("角色" to role)
            add("子服名" to cfg.serverName().ifBlank { "(未设)" })
            if (mode != "standalone") add("传输" to cfg.crossServerTransport())
        }))
        // 行为采集与部署形态无关：事件都在本子服发生，三种模式都跑
        behavior = runCatching { BehaviorService.build(this, cfg)?.also { it.start() } }
            .getOrElse { logger.warning(WindyLog.tag("Behavior", "行为采集启动失败：${it.message}")); null }
        // 画像数据源注册（通过 PlaceholderAPI 读各插件占位符，config 驱动清单），与模式无关
        profileRegistry.register(PapiProfileSource(this, cfg.papiProfilePlaceholders()))
        // 行为画像也贡献进聚合画像：Agent 查画像时同时拿到属性（PAPI）+ 行为（主玩法/活跃/标签）
        behavior?.let { profileRegistry.register(BehaviorProfileSource(it)) }
        server.pluginManager.registerEvents(PlayerProfileListener(profileRegistry), this)
        logger.info(WindyLog.tag("Profile", "画像数据源已注册 — ${profileRegistry.available().size} 个可用" +
            if (server.pluginManager.getPlugin("PlaceholderAPI") == null) "（未检测到 PlaceholderAPI，画像为空，请装 PAPI）" else ""))
        when (mode) {
            "standalone" -> startStandalone(cfg)
            "hub" -> startHub(cfg)
            else -> startProvider(cfg)
        }
    }

    /**
     * 读取显式部署角色。合法值：provider / standalone / hub。
     * 非法或遗留值（如已移除的 auto）→ 警告并回落 standalone，提示服主在配置里显式选择。
     */
    private fun resolveMode(cfg: AgentConfig): String = when (val raw = cfg.mode()) {
        "provider", "standalone", "hub" -> raw
        else -> {
            logger.warning(WindyLog.tag("Boot",
                "deployment.mode=\"$raw\" 不是合法值（auto 自动判定已移除）。请在 windyagent-config.yml 显式设为 provider/standalone/hub。本次按 standalone 启动。"))
            "standalone"
        }
    }

    /** provider：连总线 + 能力 handler。 */
    private fun startProvider(cfg: AgentConfig) {
        val serverName = cfg.serverName().takeIf { it.isNotBlank() }
        if (serverName == null) {
            logger.severe(WindyLog.tag("Provider", "provider 模式需在 windyagent-config.yml 设置 deployment.server-name。总线未启用。"))
            return
        }
        val transport = cfg.crossServerTransport()
        // provider 经 executeCommand 直接执行中心已 gate 的命令，pending 在此模式不参与
        val actions = BukkitActions(
            this,
            buildCommandGuard(cfg),
            AuditLog(dataFolder.toPath().resolve("audit.log")),
            PendingApprovals(executionFailureMessage = { Messages.t("approval.exec_failed", it) })
        )
        val itemService = ItemService.build(this, cfg)?.also { it.warmup() }
        // 服主编写的 Groovy 技能：provider 无嵌入式 Agent，故技能经 run_skill 动作执行，
        // 并把技能目录随能力目录推回中心（中心用 search_capabilities 查、run_skill_on_server 调）。
        val skills = if (cfg.skillsEnabled())
            SkillRegistry(dataFolder.toPath().resolve(cfg.skillsDir()).toFile()) else null
        val skillEngine = skills?.let { SkillEngine(this, actions, cfg.skillTimeoutSec()) }
        skills?.let { logger.info(WindyLog.tag("Skill", "技能已加载 — ${it.reload()} 个（skills/ 目录）")) }
        // 文件管理 + 配置版本化（自动改配置/装插件的落地手；默认关，files.enabled 显式开）
        val serverRoot = server.worldContainer
        // ⚠ 存档名不一定叫 "world"（server.properties 的 level-name 可改），别硬编——从 Bukkit 现取真实存档目录。
        //   serverconfig（Forge/NeoForge 的每世界 mod 配置）只存在于「带 serverconfig 子目录的那个存档」（主存档，
        //   nether/end 没有），据此唯一定位后单独纳入版本化；存档其余部分仍被作用域挡在外。
        val rootPath = runCatching { serverRoot.canonicalFile.toPath() }.getOrNull()
        val worldFolders = runCatching { server.worlds.map { it.worldFolder.canonicalFile } }.getOrDefault(emptyList())
        fun relUnderRoot(f: java.io.File): String? = rootPath?.let { rp ->
            runCatching { rp.relativize(f.toPath()).toString().replace('\\', '/') }.getOrNull()
                ?.takeIf { it.isNotBlank() && !it.startsWith("..") }
        }
        val worldNames = worldFolders.mapNotNull { relUnderRoot(it) }
        val serverConfigRel = worldFolders.firstOrNull { java.io.File(it, "serverconfig").isDirectory }
            ?.let { relUnderRoot(java.io.File(it, "serverconfig")) }
        val fileRoots = if (serverConfigRel != null) cfg.fileRoots() + serverConfigRel else cfg.fileRoots()
        val fileProtected = (cfg.fileProtected() + worldNames).distinct()   // 真实存档名一并纳入保护，不再只靠硬编 world
        serverConfigRel?.let { logger.info(WindyLog.tag("Files", "已按 serverconfig 子目录定位主存档并纳入版本化：$it")) }
        val files = if (cfg.filesEnabled())
            org.windy.windyagent.platform.bukkit.fs.ServerFileService(serverRoot, fileRoots, fileProtected, cfg.fileMaxReadBytes()) else null
        val git = if (cfg.filesEnabled() && cfg.fileGitEnabled())
            org.windy.windyagent.platform.bukkit.fs.GitRepoService(serverRoot, logger, cfg.fileGitRemote(), cfg.fileGitBranch(), cfg.fileGitUser(), cfg.fileGitToken(), cfg.fileGitAutoPush(), cfg.fileGitMaxCommitBytes()) else null
        files?.let { logger.info(WindyLog.tag("Files", "文件管理已启用 — 作用域 $fileRoots；git 版本化 ${if (git != null) "开" else "关"}${if (cfg.fileGitRemote().isNotBlank()) "，远端已配置" else ""}")) }
        val handler = BukkitCapabilityHandler(this, actions, itemService, behavior, skills, skillEngine, profileRegistry, files, git)

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

    override fun onDisable() {
        standaloneRunner?.shutdown()
        standaloneRunner = null
        behavior?.stop()
        behavior = null
        bus?.close()
        bus = null
        logger.info(WindyLog.tag("Boot", "WindyAgent 已停止"))
    }
}
