package org.windy.windyagent.platform.bukkit

import org.bukkit.Bukkit
import taboolib.common.platform.Plugin
import taboolib.common.platform.Platform
import taboolib.common.platform.PlatformSide
import taboolib.platform.BukkitPlugin
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


@PlatformSide(Platform.BUKKIT)
object WindyAgentBukkitPlugin : Plugin() {

    private var bus: MessageBus? = null
    private var behavior: BehaviorService? = null
    private var standaloneRunner: BukkitAgentRunner? = null
    private val profileRegistry = ProfileDataRegistry()

    override fun onEnable() {
        val plugin = BukkitPlugin.getInstance()
        val dataFolder = plugin.dataFolder
        val logger = plugin.logger
        val server = Bukkit.getServer()

        val cfg = runCatching { AgentConfig.load(dataFolder.toPath()) }.getOrElse {
            logger.severe(WindyLog.tag("Boot", "加载 windyagent-config.yml 失败，插件未启用：${it.message}"))
            return
        }
        Messages.init(cfg.language())

        val mode = resolveMode(cfg, logger)

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

        // 注意：原来传 this 的地方，现在传 plugin
        behavior = runCatching { BehaviorService.build(plugin, cfg)?.also { it.start() } }
            .getOrElse { logger.warning(WindyLog.tag("Behavior", "行为采集启动失败：${it.message}")); null }

        profileRegistry.register(PapiProfileSource(plugin, cfg.papiProfilePlaceholders()))
        behavior?.let { profileRegistry.register(BehaviorProfileSource(it)) }
        server.pluginManager.registerEvents(PlayerProfileListener(profileRegistry), plugin)

        logger.info(WindyLog.tag("Profile", "画像数据源已注册 — ${profileRegistry.available().size} 个可用" +
                if (server.pluginManager.getPlugin("PlaceholderAPI") == null) "（未检测到 PlaceholderAPI，画像为空，请装 PAPI）" else ""))

        when (mode) {
            "standalone" -> startStandalone(cfg, plugin)
            "hub" -> startHub(cfg, plugin)
            else -> startProvider(cfg, plugin)
        }
    }

    private fun resolveMode(cfg: AgentConfig, logger: java.util.logging.Logger): String = when (val raw = cfg.mode()) {
        "provider", "standalone", "hub" -> raw
        else -> {
            logger.warning(WindyLog.tag("Boot",
                "deployment.mode=\"$raw\" 不是合法值。请显式设为 provider/standalone/hub。本次按 standalone 启动。"))
            "standalone"
        }
    }

    private fun startProvider(cfg: AgentConfig, plugin: org.bukkit.plugin.java.JavaPlugin) {
        val serverName = cfg.serverName().takeIf { it.isNotBlank() }
        val logger = plugin.logger
        val dataFolder = plugin.dataFolder
        val server = plugin.server

        if (serverName == null) {
            logger.severe(WindyLog.tag("Provider", "provider 模式需在 config 中设置 server-name。"))
            return
        }
        val transport = cfg.crossServerTransport()
        val actions = BukkitActions(
            plugin,
            buildCommandGuard(cfg),
            AuditLog(dataFolder.toPath().resolve("audit.log")),
            PendingApprovals(executionFailureMessage = { Messages.t("approval.exec_failed", it) })
        )
        val itemService = ItemService.build(plugin, cfg)?.also { it.warmup() }

        val skills = if (cfg.skillsEnabled())
            SkillRegistry(dataFolder.toPath().resolve(cfg.skillsDir()).toFile()) else null

        // [关键更改] 直接初始化，Kether 环境由 TabooLib 保证可用
        val skillEngine = skills?.let { SkillEngine(plugin, actions, cfg.skillTimeoutSec()) }
        skills?.let { logger.info(WindyLog.tag("Skill", "技能已加载 — ${it.reload()} 个（skills/ 目录）")) }

        val serverRoot = server.worldContainer
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
        val fileProtected = (cfg.fileProtected() + worldNames).distinct()
        serverConfigRel?.let { logger.info(WindyLog.tag("Files", "已按 serverconfig 子目录定位主存档并纳入版本化：$it")) }

        val files = if (cfg.filesEnabled())
            org.windy.windyagent.platform.bukkit.fs.ServerFileService(serverRoot, fileRoots, fileProtected, cfg.fileMaxReadBytes()) else null
        val git = if (cfg.filesEnabled() && cfg.fileGitEnabled())
            org.windy.windyagent.platform.bukkit.fs.GitRepoService(serverRoot, logger, cfg.fileGitRemote(), cfg.fileGitBranch(), cfg.fileGitUser(), cfg.fileGitToken(), cfg.fileGitAutoPush(), cfg.fileGitMaxCommitBytes()) else null
        files?.let { logger.info(WindyLog.tag("Files", "文件管理已启用 — 作用域 $fileRoots")) }

        val handler = BukkitCapabilityHandler(plugin, actions, itemService, behavior, skills, skillEngine, profileRegistry, files, git)

        bus = runCatching {
            buildClientBus(cfg, transport).also { it.listen(serverName) { req -> handler.handle(req) } }
        }.getOrElse {
            logger.severe(WindyLog.tag("Bus", "总线启动失败，能力提供方未启用：${it.message}"))
            null
        }
        bus?.let { b ->
            logger.info(WindyLog.tag("Provider", "能力提供方已启动 — server-name: $serverName, transport: $transport"))
            val capSync = CapabilitySync(plugin, actions, serverName, { cat -> b.publishCatalog(CapabilitySync.toJson(cat)) },
                { skills?.let { it.reload(); it.all().map { d -> d.toCapabilityCommand() } } ?: emptyList() }
            )
            handler.onSkillsChanged = { capSync.rebuildSoon() }
            capSync.start()
        }
    }

    private fun startStandalone(cfg: AgentConfig, plugin: org.bukkit.plugin.java.JavaPlugin) {
        if (cfg.needsLlmSetup()) { llmSetupHint("standalone", plugin); return }
        val runner = BukkitAgentRunner(plugin)
        if (runner.start(cfg, profileRegistry = profileRegistry)) {
            standaloneRunner = runner
            plugin.logger.info(WindyLog.tag("Boot", "已启动（standalone：本机嵌入式 Agent）"))
        } else {
            plugin.logger.severe(WindyLog.tag("Boot", "standalone 启动失败，请检查 LLM 配置"))
        }
    }

    private fun llmSetupHint(mode: String, plugin: org.bukkit.plugin.java.JavaPlugin) {
        val path = plugin.dataFolder.toPath().resolve("windyagent-config.yml")
        plugin.logger.warning(WindyLog.tag("Boot", "⚠ 尚未配置 LLM API Key（$mode 模式需要），请编辑 $path"))
    }

    private fun startHub(cfg: AgentConfig, plugin: org.bukkit.plugin.java.JavaPlugin) {
        if (cfg.needsLlmSetup()) { llmSetupHint("hub", plugin); return }
        val transport = cfg.crossServerTransport()
        bus = runCatching { buildHubBus(cfg, transport).also { it.startReplyListener() } }.getOrElse {
            plugin.logger.severe(WindyLog.tag("Bus", "中枢总线启动失败：${it.message}"))
            null
        }
        if (BukkitAgentRunner(plugin).start(cfg, remoteBus = bus, remoteTimeoutMs = cfg.remoteTimeoutMs(), profileRegistry = profileRegistry)) {
            plugin.logger.info(WindyLog.tag("Boot", "已启动（hub：嵌入式 Agent + 总线中枢，transport: $transport）"))
        } else {
            plugin.logger.severe(WindyLog.tag("Boot", "hub 启动失败，请检查 LLM 配置"))
        }
    }

    private fun buildClientBus(cfg: AgentConfig, transport: String): MessageBus = when (transport) {
        "socket" -> SocketClientBus(cfg.socketHost(), cfg.socketPort(), cfg.socketSecret().ifBlank { null })
        else -> RedisBus(cfg.redisHost(), cfg.redisPort(), cfg.redisPassword())
    }

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
        BukkitPlugin.getInstance().logger.info(WindyLog.tag("Boot", "WindyAgent 已停止"))
    }
}