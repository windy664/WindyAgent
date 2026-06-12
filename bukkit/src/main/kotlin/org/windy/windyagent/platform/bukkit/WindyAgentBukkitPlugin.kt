package org.windy.windyagent.platform.bukkit

import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.AgentConfig
import org.windy.windyagent.buildCommandGuard
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.platform.bukkit.item.ItemService
import org.windy.windyagent.safety.AuditLog
import org.windy.windyagent.safety.PendingApprovals
import org.windy.windyagent.bus.RedisBus
import org.windy.windyagent.bus.socket.SocketClientBus
import org.windy.windyagent.bus.socket.SocketHubBus

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

    override fun onEnable() {
        val cfg = runCatching { AgentConfig.load(dataFolder.toPath()) }.getOrElse {
            logger.severe("加载 windyagent-config.yml 失败，插件未启用：${it.message}")
            return
        }
        when (cfg.mode()) {
            "standalone" -> startStandalone(cfg)
            "hub" -> startHub(cfg)
            else -> startProvider(cfg)
        }
    }

    /** provider：连总线 + 能力 handler。 */
    private fun startProvider(cfg: AgentConfig) {
        val serverName = cfg.serverName().takeIf { it.isNotBlank() }
        if (serverName == null) {
            logger.severe("provider 模式需在 windyagent-config.yml 设置 deployment.server-name。总线未启用。")
            return
        }
        val transport = cfg.crossServerTransport()
        // provider 经 executeCommand 直接执行中心已 gate 的命令，pending 在此模式不参与
        val actions = BukkitActions(this, buildCommandGuard(cfg), AuditLog(dataFolder.toPath().resolve("audit.log")), PendingApprovals())
        val itemService = ItemService.build(this, cfg)?.also { it.warmup() }
        val handler = BukkitCapabilityHandler(this, actions, itemService)

        bus = runCatching {
            buildClientBus(cfg, transport).also { it.listen(serverName) { req -> handler.handle(req) } }
        }.getOrElse {
            logger.severe("总线启动失败，能力提供方未启用：${it.message}")
            null
        }
        bus?.let { b ->
            logger.info("能力提供方已启动 — server-name: $serverName, transport: $transport")
            // 启动后建能力目录，经总线推回中心（取代中心每次现扫）
            CapabilitySync(this, actions, serverName) { cat -> b.publishCatalog(CapabilitySync.toJson(cat)) }.start()
        }
    }

    /** standalone：仅嵌入式 Agent。 */
    private fun startStandalone(cfg: AgentConfig) {
        if (BukkitAgentRunner(this).start(cfg)) logger.info("WindyAgent 已启动（standalone：本机嵌入式 Agent）")
        else logger.severe("standalone 启动失败，请检查 windyagent-config.yml 的 LLM 配置")
    }

    /** hub：嵌入式 Agent + 总线中枢。 */
    private fun startHub(cfg: AgentConfig) {
        val transport = cfg.crossServerTransport()
        bus = runCatching { buildHubBus(cfg, transport).also { it.startReplyListener() } }.getOrElse {
            logger.severe("中枢总线启动失败：${it.message}")
            null
        }
        if (BukkitAgentRunner(this).start(cfg, remoteBus = bus, remoteTimeoutMs = cfg.remoteTimeoutMs())) {
            logger.info("WindyAgent 已启动（hub：嵌入式 Agent + 总线中枢，transport: $transport）")
        } else {
            logger.severe("hub 启动失败，请检查 windyagent-config.yml 的 LLM 配置")
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
        bus?.close()
        bus = null
        logger.info("WindyAgent 已停止")
    }
}
