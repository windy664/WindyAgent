package org.windy.windyagent.platform.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import org.windy.windyagent.AgentConfig
import org.windy.windyagent.agent.AgentRouter
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.agent.PlanExecuteAgent
import org.windy.windyagent.agent.ReActAgent
import org.windy.windyagent.agent.RemoteBalanceTool
import org.windy.windyagent.agent.RemoteCommandTool
import org.windy.windyagent.capability.CapabilityRegistry
import org.windy.windyagent.capability.SearchCapabilitiesTool
import org.windy.windyagent.command.AgentCommandRouter
import org.windy.windyagent.bus.InProcessBus
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.bus.RedisBus
import org.windy.windyagent.bus.ToolReply
import org.windy.windyagent.bus.socket.SocketHubBus
import org.windy.windyagent.buildEmbeddingProvider
import org.windy.windyagent.buildFastProvider
import org.windy.windyagent.buildProvider
import org.windy.windyagent.knowledge.KnowledgeLoader
import org.windy.windyagent.memory.FileLongTermMemory
import org.windy.windyagent.memory.RememberTool
import org.windy.windyagent.buildCommandGuard
import org.windy.windyagent.mcp.McpLoader
import org.windy.windyagent.rag.LlmQueryExpander
import org.windy.windyagent.safety.AuditLog
import org.windy.windyagent.safety.PendingApprovals
import org.windy.windyagent.knowledge.KnowledgeSearchTool
import org.windy.windyagent.platform.SessionManager
import java.nio.file.Path

@Plugin(
    id = "windyagent",
    name = "WindyAgent",
    version = "1.0-SNAPSHOT",
    description = "AI Agent for Minecraft server management"
)
class WindyAgentVelocityPlugin @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    @DataDirectory private val dataDirectory: Path
) {
    private var bus: MessageBus? = null

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        logger.info("WindyAgent initializing...")

        val cfg = runCatching { AgentConfig.load(dataDirectory) }.getOrElse {
            logger.error("Failed to load windyagent-config.yml: {}", it.message)
            return
        }

        val llm = runCatching { buildProvider(cfg) }.getOrElse {
            logger.error(it.message)
            return
        }
        // 元任务（路由/扩展）便宜模型，省 token；未配则用主模型
        val fastLlm = buildFastProvider(cfg)
        if (fastLlm != null) logger.info("元任务便宜模型：{}", cfg.fastModel())

        val extraTools = mutableListOf<AgentTool>()

        // 安全护栏 + 审计 + 人工审批闸（拦 AI 自动跑高危命令；可信来源高危走审批）
        val guard = buildCommandGuard(cfg)
        val audit = AuditLog(dataDirectory.resolve("audit.log"))
        val pending = PendingApprovals()
        logger.info("安全护栏：mode={}", cfg.safetyMode())

        // 长期记忆（跨会话）：有则挂 remember 工具 + 自动召回
        val memory = if (cfg.memoryEnabled()) FileLongTermMemory(dataDirectory.resolve("memory"), cfg.memoryMaxEntries(), cfg.memoryRecallMinScore()) else null
        memory?.let { extraTools += RememberTool(it); logger.info("长期记忆已启用") }

        // 无 embedding 的 RAG 语义增强：稀疏命中不足时用 LLM 扩展查询（用便宜模型，默认开，可关）
        val expander = if (cfg.ragQueryExpansion()) LlmQueryExpander(fastLlm ?: llm) else null

        // 知识库检索（RAG 结构化优先起步）：有知识时挂上 knowledge_search 工具
        val knowledge = KnowledgeLoader.load(dataDirectory)
        if (knowledge.size() > 0) {
            extraTools += KnowledgeSearchTool(knowledge, expander, cfg.ragMinHits())
            logger.info("知识库已加载 — {} 条", knowledge.size())
        }

        // MCP 工具接入（可选）：把外部 MCP server 暴露的工具喂给 Agent（与跨服总线正交）
        val mcpTools = McpLoader.load(cfg.mcpServers())
        if (mcpTools.isNotEmpty()) {
            extraTools += mcpTools
            logger.info("MCP 工具已接入 — {} 个", mcpTools.size)
        }

        // 跨服总线（可选）：启用后把远端子服能力包装成工具加入 Agent。
        // 传输实现由配置 cross-server.transport 决定，RemoteCommandTool 只依赖 MessageBus 接口。
        if (cfg.crossServerEnabled()) {
            runCatching {
                val b = buildBus(cfg.crossServerTransport(), cfg)
                b.startReplyListener()
                extraTools += RemoteCommandTool(b, cfg.remoteTimeoutMs(), guard, audit, pending)
                extraTools += RemoteBalanceTool(b, cfg.remoteTimeoutMs())
                // 子服能力目录：收齐推来的目录入中心注册表，Agent 用 search_capabilities 本地检索（零往返）。
                // 配了 embedding 则走语义检索（L3 RAG），否则关键词（L2）。
                val registry = CapabilityRegistry(buildEmbeddingProvider(cfg), dataDirectory.resolve("capability"))
                registry.load() // 永久记忆：重启直接从盘载入，不靠子服重推
                b.onCatalog { json -> registry.accept(json) }
                if (cfg.embeddingEnabled()) logger.info("能力检索启用语义向量（embedding: {}）", cfg.embeddingModel())
                extraTools += SearchCapabilitiesTool(registry, expander, cfg.ragMinHits())
                bus = b
                logger.info("跨服总线已启用 — transport: {}", cfg.crossServerTransport())
            }.onFailure { logger.error("跨服总线启动失败，将仅以本代理模式运行：{}", it.message) }
        }

        val platform = VelocityPlatform(server, audit, extraTools)
        // 自动在简单(ReAct) / 复杂多步(Plan-Execute) 任务间路由；注入长期记忆做自动召回
        val agent = AgentRouter(llm, ReActAgent(llm), PlanExecuteAgent(llm), memory, cfg.memoryRecallTopK(), fastLlm)
        val sessions = SessionManager(cfg.maxHistory())

        // 载体无关的元命令路由（help/clear/history/status/approve…）
        val statusSupplier = {
            "提供方：${llm.name}\n工具：${platform.tools.size} 个\n安全：mode=${cfg.safetyMode()}\n" +
                "跨服：${if (cfg.crossServerEnabled()) cfg.crossServerTransport() else "未启用"}"
        }
        val router = AgentCommandRouter(sessions, pending, audit, memory, statusSupplier)

        // 玩家游戏内聊天触发：!ai <message>
        server.eventManager.register(this, VelocityChatListener(agent, platform, sessions, router, logger, cfg.trigger()))

        // 命令触发（控制台 + 玩家）：/ai <message>，命令名由 trigger 去掉前缀符号得出
        val commandName = cfg.trigger().trimStart('!', '/', '.', ' ').ifBlank { "ai" }
        val commandManager = server.commandManager
        val meta = commandManager.metaBuilder(commandName).plugin(this).build()
        commandManager.register(meta, AgentCommand(agent, platform, sessions, router, logger))

        // 顶层审批命令（薄适配 → router）
        for (act in listOf("approve", "deny", "pending")) {
            val m = commandManager.metaBuilder("ai-$act").plugin(this).build()
            commandManager.register(m, AgentApprovalCommand(router, act))
        }

        logger.info(
            "WindyAgent started — provider: {} — chat: '{} <message>' — command: '/{} <message>' (console supported)",
            llm.name, cfg.trigger(), commandName
        )
    }

    /**
     * 按 transport 选择总线实现：
     *  - redis：生产传输（Redis pub/sub）。
     *  - socket：无 Redis 的自建 TCP 中枢——Velocity 当 broker，子服 dial-home 连入。
     *  - inprocess：无 Redis 的单实例测试——为每个已知后端子服注册回显 stub，
     *    让「Agent → RemoteCommandTool → 总线 → 子服 → 回包」整条链在一个进程内即可跑通。
     */
    private fun buildBus(transport: String, cfg: AgentConfig): MessageBus = when (transport) {
        "socket" -> SocketHubBus(cfg.socketHost(), cfg.socketPort(), cfg.socketSecret().ifBlank { null }).also {
            logger.info("跨服总线使用 SocketHubBus（自建 TCP 中枢）— bind {}:{}", cfg.socketHost(), cfg.socketPort())
        }
        "inprocess", "memory" -> InProcessBus().also { bus ->
            server.allServers.forEach { rs ->
                val name = rs.serverInfo.name
                bus.listen(name) { req ->
                    ToolReply(req.requestId, true, "[模拟子服 $name] 已接收 ${req.action}：${req.argsJson}")
                }
            }
            logger.warn("跨服总线使用 InProcessBus（测试用，不跨进程）— 已为 {} 个子服注册回显 stub", server.allServers.size)
        }
        else -> RedisBus(cfg.redisHost(), cfg.redisPort(), cfg.redisPassword())
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        bus?.close()
        bus = null
        logger.info("WindyAgent stopped.")
    }
}
