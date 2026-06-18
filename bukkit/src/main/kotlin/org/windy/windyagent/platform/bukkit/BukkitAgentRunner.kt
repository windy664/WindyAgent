package org.windy.windyagent.platform.bukkit

import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.AgentConfig
import org.windy.windyagent.agent.AgentRouter
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.agent.ContextCompressor
import org.windy.windyagent.agent.PersonalityLoader
import org.windy.windyagent.agent.PlanExecuteAgent
import org.windy.windyagent.agent.ReActAgent
import org.windy.windyagent.agent.UserProfileManager
import org.windy.windyagent.agent.RemoteAppraiseTool
import org.windy.windyagent.agent.RemoteBalanceTool
import org.windy.windyagent.agent.RemoteCommandTool
import org.windy.windyagent.agent.RemoteProposePackTool
import org.windy.windyagent.agent.RemoteRefreshItemsTool
import org.windy.windyagent.llm.LLMUsageTracker
import org.windy.windyagent.platform.bukkit.item.ItemService
import org.windy.windyagent.platform.bukkit.skill.SkillEngine
import org.windy.windyagent.platform.bukkit.skill.SkillTool
import org.windy.windyagent.skill.SkillRegistry
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.buildCommandGuard
import org.windy.windyagent.buildEmbeddingProvider
import org.windy.windyagent.buildFastProvider
import org.windy.windyagent.buildProvider
import org.windy.windyagent.safety.AuditLog
import org.windy.windyagent.capability.CapabilityRegistry
import org.windy.windyagent.capability.SearchCapabilitiesTool
import org.windy.windyagent.command.AgentCommandRouter
import org.windy.windyagent.knowledge.KnowledgeManager
import org.windy.windyagent.knowledge.KnowledgeSearchTool
import org.windy.windyagent.knowledge.KnowledgeWriteTool
import org.windy.windyagent.knowledge.PlayerQa
import org.windy.windyagent.mcp.McpLoader
import org.windy.windyagent.memory.FileLongTermMemory
import org.windy.windyagent.memory.RememberTool
import org.windy.windyagent.platform.SessionManager
import org.windy.windyagent.rag.LlmQueryExpander
import org.windy.windyagent.safety.PendingApprovals
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

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
        // 知识库（读+写）——对齐 Velocity，让本机 Agent 也能查/沉淀知识；玩家问答也用它
        val knowledge = KnowledgeManager(plugin.dataFolder.toPath().resolve("knowledge"))
        extraTools += KnowledgeSearchTool(knowledge, expander, cfg.ragMinHits())
        extraTools += KnowledgeWriteTool(knowledge)
        // 玩家问答：游戏内 !ai / 非管理员 /ai 走这个——只检索知识库作答，不进 Agent、不碰工具
        val playerQa = PlayerQa(fastLlm ?: llm, knowledge, expander, cfg.serverName().ifBlank { "本服务器" })
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
            // hub：也能调其它子服推上来的技能（本机技能则已是本地工具）
            extraTools += org.windy.windyagent.agent.RemoteSkillTool(remoteBus, remoteTimeoutMs, audit)
            remoteBus.onCatalog { registry.accept(it) }
        }
        // 长期记忆（跨会话）
        val memory = if (cfg.memoryEnabled()) FileLongTermMemory(plugin.dataFolder.toPath().resolve("memory"), cfg.memoryMaxEntries(), cfg.memoryRecallMinScore()) else null
        memory?.let { extraTools += RememberTool(it) }
        // MCP 工具接入（可选）
        extraTools += McpLoader.load(cfg.mcpServers())
        // 服主编写的 Groovy 技能：扫 skills/ 目录，每个技能挂成本地工具（与 Agent 同 JVM，直接调）
        // standalone/hub 本机即中心，技能库在本地 → 首启释放默认技能（provider 模式不释放，由中心下发）。
        val skillsDir = plugin.dataFolder.toPath().resolve(cfg.skillsDir()).toFile()
        val skills = if (cfg.skillsEnabled()) SkillRegistry(skillsDir) else null
        val skillEngine = skills?.let { SkillEngine(plugin, actions, cfg.skillTimeoutSec()) }
        if (skills != null && skillEngine != null) {
            org.windy.windyagent.skill.SkillDefaults.releaseIfEmpty(skillsDir)
            val n = skills.reload()
            // allTools 用 lazy 回调：SkillTool 被调用时 extraTools 已填满
            val toolsRef = { extraTools.toList() }
            skills.all().forEach { def ->
                extraTools += SkillTool(def, skillEngine, audit, toolsRef, skills, skillsDir)
            }
            // 对话式技能管理
            extraTools += org.windy.windyagent.agent.CreateSkillTool(skills, audit, isUpdate = false)
            extraTools += org.windy.windyagent.agent.CreateSkillTool(skills, audit, isUpdate = true)
            extraTools += org.windy.windyagent.agent.ListSkillsTool(skills)
            extraTools += org.windy.windyagent.agent.ReadSkillTool(skills)
            // 脚本验证：编译检查 + dry-run（bukkit 侧有 SkillEngine）
            extraTools += org.windy.windyagent.agent.ValidateSkillTool(
                compileCheck = { script -> skillEngine.compile(script) },
                dryRun = { script, args ->
                    val r = skillEngine.dryRun(script, args)
                    org.windy.windyagent.agent.DryRunSummary(r.success, r.operations, r.error)
                }
            )
            plugin.logger.info("技能已加载 — $n 个（skills/ 目录，其中 ${skills.all().count { it.isWorkflow }} 个工作流）")
        }
        // 日志读取工具 + 日志监控
        val logDir = plugin.server.worldContainer.resolve("logs")
        extraTools += org.windy.windyagent.agent.ReadLogTool(logDir)
        if (logDir.exists()) {
            val watcher = org.windy.windyagent.ops.LogWatcher(
                logFiles = listOf(File(logDir, "latest.log")),
                onError = { err ->
                    // 本地记审计
                    audit.record("local", "log_error", err.pattern, err.severity.uppercase(), err.errorLine.take(200))
                    plugin.logger.warning("[日志监控] ${err.pattern} — ${err.errorLine.take(100)}")
                    // 经总线推送到中心（如果已连接）
                    if (remoteBus != null) {
                        val json = ObjectMapper().writeValueAsString(mapOf(
                            "server" to cfg.serverName(),
                            "file" to err.file,
                            "lineNum" to err.lineNum,
                            "errorLine" to err.errorLine,
                            "context" to err.context,
                            "pattern" to err.pattern,
                            "severity" to err.severity,
                            "ts" to err.ts
                        ))
                        runCatching { remoteBus.publishError(json) }
                    }
                }
            )
            watcher.start()
        }
        // 插件集成（自动发现已安装插件，注册对应工具）
        org.windy.windyagent.platform.bukkit.integration.IntegrationRegistry.discoverAndRegister(plugin, audit).let { pluginTools ->
            extraTools += pluginTools
        }
        // 人格文件
        val personality = PersonalityLoader.load(plugin.dataFolder.toPath(), cfg.personalityFile())
        val platform = BukkitPlatform(plugin, actions, extraTools)
        platform.personality = personality

        // 用量追踪
        val usageTracker = if (cfg.usageEnabled()) LLMUsageTracker.wrap(llm, plugin.dataFolder.toPath()) else null
        val effectiveLlm = usageTracker ?: llm

        // 上下文压缩 + 用户画像
        val compressor = if (cfg.compressionEnabled()) ContextCompressor(fastLlm ?: effectiveLlm, cfg.compressionThreshold(), cfg.compressionKeepRecent()) else null
        val profileManager = if (cfg.profilesEnabled()) UserProfileManager(plugin.dataFolder.toPath().resolve("profiles")) else null

        val agent = AgentRouter(effectiveLlm, ReActAgent(effectiveLlm), PlanExecuteAgent(effectiveLlm), memory, cfg.memoryRecallTopK(), fastLlm, compressor, profileManager)
        val sessions = SessionManager(cfg.maxHistory())

        // 载体无关的元命令路由
        val statusSupplier = {
            "提供方：${llm.name}\n工具：${platform.tools.size} 个\n安全：mode=${cfg.safetyMode()}\n模式：${cfg.mode()}"
        }
        val valueExecutor = items?.let { LocalValueExecutor(it, fastLlm ?: llm, cfg.itemLlmBatchSize(), cfg.itemRarityTiers()) }
        val router = AgentCommandRouter(sessions, pending, audit, memory, statusSupplier, valueExecutor, usageTracker, compressor, profileManager)

        // 启动后建本机能力目录，放进本地注册表
        val selfName = cfg.serverName().ifBlank { "local" }
        // 嵌入式：本机技能已是本地工具（LLM 直接可见），不重复进可搜索目录，避免"目录有名、无对应工具"。
        CapabilitySync(plugin, actions, selfName, deliver = { cat -> registry.put(cat) }).start()

        // /ai 命令（需在 plugin.yml 声明 commands.ai）
        plugin.getCommand("ai")?.setExecutor(BukkitCommand(plugin, agent, playerQa, platform, sessions, router))
            ?: plugin.logger.warning("未找到 /ai 命令声明，控制台/玩家命令入口不可用（聊天触发仍可用）")

        // 顶层审批命令（薄适配 → router）
        val approval = BukkitApprovalCommand(router)
        listOf("ai-approve", "ai-deny", "ai-pending").forEach { plugin.getCommand(it)?.setExecutor(approval) }

        // 聊天触发 <trigger> <消息>
        plugin.server.pluginManager.registerEvents(
            BukkitChatListener(plugin, playerQa, platform, router, cfg.trigger()), plugin
        )

        plugin.logger.info("嵌入式 Agent 已就绪 — provider: ${llm.name}, 触发: '${cfg.trigger()} <消息>' / '/ai <消息>'")
        return true
    }
}
