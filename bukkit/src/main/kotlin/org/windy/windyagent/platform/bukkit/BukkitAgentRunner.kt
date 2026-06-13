package org.windy.windyagent.platform.bukkit

import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.AgentConfig
import org.windy.windyagent.agent.AgentRouter
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.agent.PlanExecuteAgent
import org.windy.windyagent.agent.ReActAgent
import org.windy.windyagent.agent.RemoteAppraiseTool
import org.windy.windyagent.agent.RemoteBalanceTool
import org.windy.windyagent.agent.RemoteCommandTool
import org.windy.windyagent.agent.RemoteProposePackTool
import org.windy.windyagent.agent.RemoteRefreshItemsTool
import org.windy.windyagent.platform.bukkit.item.ItemService
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.buildCommandGuard
import org.windy.windyagent.buildEmbeddingProvider
import org.windy.windyagent.buildFastProvider
import org.windy.windyagent.buildProvider
import org.windy.windyagent.safety.AuditLog
import org.windy.windyagent.capability.CapabilityRegistry
import org.windy.windyagent.capability.SearchCapabilitiesTool
import org.windy.windyagent.command.AgentCommandRouter
import org.windy.windyagent.mcp.McpLoader
import org.windy.windyagent.memory.FileLongTermMemory
import org.windy.windyagent.memory.RememberTool
import org.windy.windyagent.platform.SessionManager
import org.windy.windyagent.rag.LlmQueryExpander
import org.windy.windyagent.safety.PendingApprovals

/**
 * 嵌入式 Agent 装配：把 core 的 Agent 大脑接到本 Bukkit 服。
 *
 * standalone：只用本地工具；hub：再把 [remoteBus] 包成 [RemoteCommandTool] 派发到其它子服。
 *
 * LLM 配置复用 core 的 windyagent-config.yml（首启动从 jar 内模板释放到本插件数据目录），
 * 与 Velocity 端同一套格式；mode/总线相关配置则在 Bukkit 原生 config.yml。
 */
class BukkitAgentRunner(private val plugin: JavaPlugin) {

    /** @return 是否成功启动（失败已记日志，调用方据此决定后续）。cfg 由插件入口统一加载后传入。 */
    fun start(cfg: AgentConfig, remoteBus: MessageBus? = null, remoteTimeoutMs: Long = 5000L): Boolean {
        val llm = runCatching { buildProvider(cfg) }.getOrElse {
            plugin.logger.severe(it.message)
            return false
        }
        val fastLlm = buildFastProvider(cfg)

        val guard = buildCommandGuard(cfg)
        val audit = AuditLog(plugin.dataFolder.toPath().resolve("audit.log"))
        val pending = PendingApprovals()
        val actions = BukkitActions(plugin, guard, audit, pending)

        // 能力目录：本机命令建好放入本地注册表，Agent 经 search_capabilities 本地检索（零往返）。
        // 配了 embedding 则语义检索（L3），否则关键词（L2）。带持久化（重启免重建）。
        val registry = CapabilityRegistry(buildEmbeddingProvider(cfg), plugin.dataFolder.toPath().resolve("capability"))
        registry.load()
        val expander = if (cfg.ragQueryExpansion()) LlmQueryExpander(fastLlm ?: llm) else null
        val extraTools = mutableListOf<AgentTool>()
        extraTools += SearchCapabilitiesTool(registry, expander, cfg.ragMinHits())
        // hub 模式：把派发到其它子服的远端能力挂上，并接收其它子服推来的目录
        // 本机物品估值（standalone/hub：本服有 mods/）
        val items = ItemService.build(plugin, cfg)?.also { it.warmup() }
        items?.let {
            extraTools += BukkitRefreshItemsTool(it); extraTools += BukkitAppraiseTool(it); extraTools += BukkitProposePackTool(it)
        }
        if (remoteBus != null) {
            extraTools += RemoteCommandTool(remoteBus, remoteTimeoutMs, guard, audit, pending)
            extraTools += RemoteBalanceTool(remoteBus, remoteTimeoutMs)
            // hub：也能对其它子服远端估值
            extraTools += RemoteAppraiseTool(remoteBus, remoteTimeoutMs)
            extraTools += RemoteProposePackTool(remoteBus, remoteTimeoutMs)
            extraTools += RemoteRefreshItemsTool(remoteBus, remoteTimeoutMs)
            remoteBus.onCatalog { registry.accept(it) }
        }
        // 长期记忆（跨会话）
        val memory = if (cfg.memoryEnabled()) FileLongTermMemory(plugin.dataFolder.toPath().resolve("memory"), cfg.memoryMaxEntries(), cfg.memoryRecallMinScore()) else null
        memory?.let { extraTools += RememberTool(it) }
        // MCP 工具接入（可选）
        extraTools += McpLoader.load(cfg.mcpServers())
        val platform = BukkitPlatform(plugin, actions, extraTools)
        val agent = AgentRouter(llm, ReActAgent(llm), PlanExecuteAgent(llm), memory, cfg.memoryRecallTopK(), fastLlm)
        val sessions = SessionManager(cfg.maxHistory())

        // 载体无关的元命令路由（help/clear/history/status/approve…）
        val statusSupplier = {
            "提供方：${llm.name}\n工具：${platform.tools.size} 个\n安全：mode=${cfg.safetyMode()}\n模式：${cfg.mode()}"
        }
        val valueExecutor = items?.let { LocalValueExecutor(it) }
        val router = AgentCommandRouter(sessions, pending, audit, memory, statusSupplier, valueExecutor)

        // 启动后建本机能力目录，放进本地注册表
        val selfName = cfg.serverName().ifBlank { "local" }
        CapabilitySync(plugin, actions, selfName) { cat -> registry.put(cat) }.start()

        // /ai 命令（需在 plugin.yml 声明 commands.ai）
        plugin.getCommand("ai")?.setExecutor(BukkitCommand(plugin, agent, platform, sessions, router))
            ?: plugin.logger.warning("未找到 /ai 命令声明，控制台/玩家命令入口不可用（聊天触发仍可用）")

        // 顶层审批命令（薄适配 → router）
        val approval = BukkitApprovalCommand(router)
        listOf("ai-approve", "ai-deny", "ai-pending").forEach { plugin.getCommand(it)?.setExecutor(approval) }

        // 聊天触发 <trigger> <消息>
        plugin.server.pluginManager.registerEvents(
            BukkitChatListener(plugin, agent, platform, sessions, router, cfg.trigger()), plugin
        )

        plugin.logger.info("嵌入式 Agent 已就绪 — provider: ${llm.name}, 触发: '${cfg.trigger()} <消息>' / '/ai <消息>'")
        return true
    }
}
