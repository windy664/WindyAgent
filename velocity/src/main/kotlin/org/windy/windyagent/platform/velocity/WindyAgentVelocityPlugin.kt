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
import org.windy.windyagent.Messages
import org.windy.windyagent.agent.AgentRouter
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.agent.ContextCompressor
import org.windy.windyagent.agent.PersonalityLoader
import org.windy.windyagent.agent.PlanExecuteAgent
import org.windy.windyagent.agent.ReActAgent
import org.windy.windyagent.agent.RemoteAppraiseTool
import org.windy.windyagent.agent.RemoteBalanceTool
import org.windy.windyagent.agent.RemoteCommandTool
import org.windy.windyagent.agent.RemoteProposePackTool
import org.windy.windyagent.agent.RemoteRefreshItemsTool
import org.windy.windyagent.agent.UserProfileManager
import org.windy.windyagent.capability.CapabilityRegistry
import org.windy.windyagent.capability.SearchCapabilitiesTool
import org.windy.windyagent.command.AgentCommandRouter
import org.windy.windyagent.bus.InProcessBus
import org.windy.windyagent.llm.LLMUsageTracker
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.bus.RedisBus
import org.windy.windyagent.bus.ToolReply
import org.windy.windyagent.bus.socket.SocketHubBus
import org.windy.windyagent.buildEmbeddingProvider
import org.windy.windyagent.buildFastProvider
import org.windy.windyagent.buildProvider
import org.windy.windyagent.knowledge.KnowledgeManager
import org.windy.windyagent.knowledge.PlayerQa
import org.windy.windyagent.knowledge.ReferenceLibrary
import org.windy.windyagent.ops.ScheduledTask
import org.windy.windyagent.agent.AgentContext
import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.safety.TrustLevel
import org.windy.windyagent.memory.FileLongTermMemory
import org.windy.windyagent.buildCommandGuard
import org.windy.windyagent.rag.LlmQueryExpander
import org.windy.windyagent.safety.AuditLog
import org.windy.windyagent.safety.PendingApprovals
import org.windy.windyagent.platform.SessionManager
import org.windy.windyagent.llm.LLMProvider
import org.windy.windyagent.ops.CompositeNotifier
import org.windy.windyagent.ops.HealthMonitor
import org.windy.windyagent.ops.HealthSnapshot
import org.windy.windyagent.ops.Incident
import org.windy.windyagent.ops.IncidentKind
import org.windy.windyagent.ops.LogNotifier
import org.windy.windyagent.ops.TaskScheduler
import org.windy.windyagent.web.AlertCenter
import org.windy.windyagent.web.DashboardServer
import org.windy.windyagent.ui.WindyLog
import com.fasterxml.jackson.databind.ObjectMapper
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
    private var web: DashboardServer? = null
    private var chatWords: ChatWordCollector? = null
    private var sentinel: HealthMonitor? = null
    private var scheduler: TaskScheduler? = null
    private var usageTrackerInst: LLMUsageTracker? = null
    private var configWatcherInst: org.windy.windyagent.ConfigWatcher? = null
    // 新增组件（shutdown 需清理）
    private var sessionStoreInst: org.windy.windyagent.platform.SessionStore? = null
    private var consolidatorInst: org.windy.windyagent.memory.MemoryConsolidator? = null
    private var opsInsightInst: OpsInsightTool? = null
    private var trajectoryRecorderInst: org.windy.windyagent.agent.TrajectoryRecorder? = null

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        logger.info(WindyLog.tag("Boot", "中心 Agent 初始化中…（Velocity）"))

        val cfg = runCatching { AgentConfig.load(dataDirectory) }.getOrElse {
            logger.error(WindyLog.tag("Boot", "加载 windyagent-config.yml 失败，插件未启用：{}"), it.message)
            return
        }
        Messages.init(cfg.language())

        // 首启未配 LLM：起一个「配置模式」的精简 web 向导 + 控制台提示，不再全盘退出（配好重启即可）
        if (cfg.needsLlmSetup()) { startSetupMode(cfg); return }

        val llm = runCatching { buildProvider(cfg) }.getOrElse {
            logger.error(WindyLog.tag("LLM", "提供方初始化失败，插件未启用：{}"), it.message)
            return
        }
        // 用量追踪：包装主模型；下方 fast 模型也并入同一统计后端，使两者花费都计入 usage.db
        val usageTracker = if (cfg.usageEnabled()) LLMUsageTracker.wrap(llm, dataDirectory, cfg.llmBudgetDailyTokens()).also { usageTrackerInst = it } else null
        val effectiveLlm = usageTracker ?: llm
        // 元任务（路由/扩展）便宜模型，省 token；未配则回退主模型。配了则同样纳入用量统计（model 字段区分）。
        val fastLlmRaw = buildFastProvider(cfg)
        val fastLlm = if (fastLlmRaw != null && usageTracker != null) usageTracker.track(fastLlmRaw) else fastLlmRaw
        if (fastLlm != null) logger.info(WindyLog.tag("LLM", "元任务便宜模型：{}（已纳入用量统计）"), cfg.fastModel())

        val extraTools = mutableListOf<AgentTool>()
        var valueExecutor: org.windy.windyagent.command.ValueExecutor? = null
        var connectedServers: () -> Set<String> = { emptySet() }   // 跨服启用后指向能力注册表
        val alerts = AlertCenter()   // 哨兵告警缓冲（供 WebUI /api/alerts 拉取），早建以便哨兵与看板共用

        // 安全护栏 + 审计 + 人工审批闸（拦 AI 自动跑高危命令；可信来源高危走审批）
        val guard = buildCommandGuard(cfg)
        val audit = AuditLog(dataDirectory.resolve("audit.log"))
        val pending = PendingApprovals()
        logger.info(WindyLog.tag("Safety", "安全护栏：mode={}"), cfg.safetyMode())

        // 会话历史持久化（SQLite FTS5）：注入 FileLongTermMemory 供 recall 时搜历史原文
        val sessionStore = if (cfg.sessionStoreEnabled()) {
            org.windy.windyagent.platform.SessionStore(dataDirectory.resolve("sessions.db")).also { ss ->
                sessionStoreInst = ss
                logger.info(WindyLog.tag("SessionStore", "会话持久化已启用 — SQLite FTS5"))
            }
        } else null

        // 长期记忆（跨会话）：有则自动召回；注入 sessionStore 供 recall 搜历史原文。remember 工具随核心组统一装配。
        val memory = if (cfg.memoryEnabled()) FileLongTermMemory(dataDirectory.resolve("memory"), cfg.memoryMaxEntries(), cfg.memoryRecallMinScore(), sessionStore) else null
        if (memory != null) logger.info(WindyLog.tag("Memory", "长期记忆已启用"))

        // 无 embedding 的 RAG 语义增强：稀疏命中不足时用 LLM 扩展查询（用便宜模型，默认开，可关）
        val expander = if (cfg.ragQueryExpansion()) LlmQueryExpander(fastLlm ?: effectiveLlm) else null

        // 知识库（可读写 + 热重载，供 WebUI 编辑）+ 内置只读参考库（CMI 等官方文档，并进检索不落盘）
        val reference = if (cfg.knowledgeReferenceEnabled()) ReferenceLibrary.load(cfg.knowledgeReferencePacks()) else ReferenceLibrary.EMPTY
        val knowledge = KnowledgeManager(dataDirectory.resolve("knowledge"), reference)
        // 核心工具统一装配（knowledge/usage/memory/mcp）——与 Bukkit 同源，加一个只改 CoreToolContributors 一处。
        // MCP 也并入此处（原下方单独注册的 mcpTools 已收编）。
        extraTools += org.windy.windyagent.agent.ToolAssembly.assemble(
            org.windy.windyagent.agent.CoreToolContributors.of(knowledge, expander, cfg.ragMinHits(), usageTracker, memory, cfg.mcpServers())
        ) { logger.info(WindyLog.tag("Tools", it)) }
        logger.info(WindyLog.tag("Knowledge", "知识库已加载 — {} 条"), knowledge.size())
        // 玩家问答：游戏内 !ai / 非管理员 /ai 走这个——只检索知识库作答，不进 Agent、不碰工具
        val playerQa = PlayerQa(fastLlm ?: effectiveLlm, knowledge, expander)

        // 插件命令目录（只读，供知识库面板展示）：registry 在更内层的总线块里，这里先占位，块内再赋值
        var kbCapsProvider: () -> String = { "[]" }

        // 技能库（中心权威）：中心持有唯一技能库。**文字技能**（流程型）在中心直接挂为工具执行；
        // **脚本技能**在中心存源、由 SkillSync 经总线下发到子服执行（脚本需 Bukkit API）。首启释放默认技能。
        val skills = if (cfg.skillsEnabled()) {
            val sdir = dataDirectory.resolve(cfg.skillsDir()).toFile()
            org.windy.windyagent.skill.SkillDefaults.releaseIfEmpty(sdir)
            org.windy.windyagent.skill.SkillRegistry(sdir).also { it.reload() }
        } else null
        skills?.let { reg ->
            val sdirFile = dataDirectory.resolve(cfg.skillsDir()).toFile()
            val texts = reg.all().filter { !it.isScript }
            val toolsRef = { extraTools.toList() }
            texts.forEach { def ->
                extraTools += org.windy.windyagent.agent.TextSkillTool(def, audit, toolsRef, reg, sdirFile)
            }
            // 对话式技能管理：服主说人话 → AI 生成 → 落盘
            extraTools += org.windy.windyagent.agent.CreateSkillTool(reg, audit, isUpdate = false)
            extraTools += org.windy.windyagent.agent.CreateSkillTool(reg, audit, isUpdate = true)
            extraTools += org.windy.windyagent.agent.ListSkillsTool(reg)
            extraTools += org.windy.windyagent.agent.ReadSkillTool(reg)
            // 脚本验证：Velocity 中心无 Groovy 运行时，验证委托给子服（standalone/hub 的 bukkit 侧有完整验证）
            logger.info(WindyLog.tag("Skill", "技能库已加载 — 共 {} 个（文字 {} 在中心执行 / 脚本 {} 下发子服，其中 {} 个工作流）"),
                reg.all().size, texts.size, reg.all().size - texts.size, reg.all().count { it.isWorkflow })
        }
        var skillSync: SkillSync? = null

        // 日志读取工具（Agent 可主动读日志做诊断）
        val logDir = dataDirectory.resolve("../logs").normalize().toFile()
        extraTools += org.windy.windyagent.agent.ReadLogTool(logDir)
        // 日志异常缓冲（各子服经总线推来的错误，Agent 可读取分析，持久化到 errors.json）
        val errorBuffer = org.windy.windyagent.ops.RecentErrorBuffer(
            persistFile = dataDirectory.resolve("errors.json").toFile()
        )
        extraTools += org.windy.windyagent.agent.GetRecentErrorsTool(errorBuffer)
        // LLM 状态查询（管理员问"用的什么模型"时）
        extraTools += org.windy.windyagent.agent.LLMStatusTool(llm)

        // MCP 工具接入已收编进上方 CoreToolContributors（"mcp" 组），此处不再单独注册。

        // 跨服总线（可选）：启用后把远端子服能力包装成工具加入 Agent。
        // 传输实现由配置 cross-server.transport 决定，RemoteCommandTool 只依赖 MessageBus 接口。
        if (cfg.crossServerEnabled()) {
            runCatching {
                val b = buildBus(cfg.crossServerTransport(), cfg)
                b.startReplyListener()
                extraTools += RemoteCommandTool(b, cfg.remoteTimeoutMs(), guard, audit, pending)
                extraTools += RemoteBalanceTool(b, cfg.remoteTimeoutMs())
                extraTools += org.windy.windyagent.agent.RemotePlayerProfileTool(b, cfg.remoteTimeoutMs())
                // 服主编写的子服技能（GroovyShell 执行）：人工审过的确定性扩展，不过 guard，记 audit
                extraTools += org.windy.windyagent.agent.RemoteSkillTool(b, cfg.remoteTimeoutMs(), audit)
                // 物品估值 / 礼包提案（数据在子服，远端调用）
                extraTools += RemoteAppraiseTool(b, cfg.remoteTimeoutMs())
                extraTools += RemoteProposePackTool(b, cfg.remoteTimeoutMs())
                extraTools += RemoteRefreshItemsTool(b, cfg.remoteTimeoutMs())
                // 子服文件管理 + 配置版本化（自动改配置/装插件的落地手）：默认关，files.enabled 显式开
                if (cfg.filesEnabled()) {
                    extraTools += org.windy.windyagent.agent.RemoteReadFileTool(b, cfg.remoteTimeoutMs(), audit)
                    extraTools += org.windy.windyagent.agent.RemoteListDirTool(b, cfg.remoteTimeoutMs(), audit)
                    extraTools += org.windy.windyagent.agent.RemoteWriteFileTool(b, cfg.remoteTimeoutMs(), audit)
                    extraTools += org.windy.windyagent.agent.RemoteDeleteFileTool(b, cfg.remoteTimeoutMs(), audit, pending)
                    extraTools += org.windy.windyagent.agent.RemoteGitHistoryTool(b, cfg.remoteTimeoutMs())
                    extraTools += org.windy.windyagent.agent.RemoteRollbackTool(b, cfg.remoteTimeoutMs(), audit, pending)
                }
                // 子服能力目录：收齐推来的目录入中心注册表，Agent 用 search_capabilities 本地检索（零往返）。
                // 配了 embedding 则走语义检索（L3 RAG），否则关键词（L2）。
                val registry = CapabilityRegistry(buildEmbeddingProvider(cfg), dataDirectory.resolve("capability"))
                registry.load() // 永久记忆：重启直接从盘载入，不靠子服重推
                // 脚本技能下发器：子服上线（宣告目录）时把命中它的脚本技能推到其 skills/ 目录执行
                skillSync = skills?.let { SkillSync(b, it, cfg.remoteTimeoutMs()) }
                b.onCatalog { json -> registry.accept(json); skillSync?.onServerAnnounced(json) }
                // 子服日志异常 → 存入缓冲 + 控制台告警
                b.onError { json ->
                    errorBuffer.addFromJson(json)
                    // 严重异常打控制台日志，管理员不看 WebUI 也能注意到
                    runCatching {
                        val node = ObjectMapper().readTree(json)
                        val server = node["server"]?.asText() ?: "?"
                        val severity = node["severity"]?.asText() ?: "?"
                        val pattern = node["pattern"]?.asText() ?: "?"
                        if (severity == "critical" || severity == "error") {
                            logger.warn(WindyLog.tag("LogMon", "子服 {} 检测到 {} 级异常：{}"), server, severity, pattern)
                        }
                    }
                }
                if (cfg.embeddingEnabled()) logger.info(WindyLog.tag("Bus", "能力检索启用语义向量（embedding: {}）"), cfg.embeddingModel())
                extraTools += SearchCapabilitiesTool(registry, expander, cfg.ragMinHits())
                // 知识库面板「插件命令」只读视图：实时把能力注册表按 子服 → 来源插件 → 命令 分组成 JSON
                kbCapsProvider = {
                    val cm = ObjectMapper()
                    val root = cm.createArrayNode()
                    registry.catalogs().sortedBy { it.server }.forEach { cat ->
                        val s = root.addObject()
                        s.put("server", cat.server)
                        val plugins = s.putArray("plugins")
                        cat.commands.groupBy { it.source.ifBlank { "其它" } }.toSortedMap().forEach { (plugin, cmds) ->
                            val p = plugins.addObject()
                            p.put("plugin", plugin)
                            val arr = p.putArray("commands")
                            cmds.sortedBy { it.name }.forEach { c ->
                                arr.addObject().put("name", c.name).put("description", c.description).putPOJO("aliases", c.aliases)
                            }
                        }
                    }
                    root.toString()
                }
                // 在线判定：优先用总线的"真实在线"集（Socket 中枢=活动连接）；传输报不了(如 Redis)才退回
                // 注册表的"曾见过"集。避免选/派到离线子服白等超时（"假在线"）。
                val online = { b.onlineServers() ?: registry.servers() }
                // value / 运维命令的远端执行后端：子服名取当前在线
                valueExecutor = RemoteValueExecutor(b, cfg.remoteTimeoutMs(), fastLlm ?: effectiveLlm, cfg.itemLlmBatchSize(), cfg.itemRarityTiers(), online)
                connectedServers = online
                bus = b
                logger.info(WindyLog.tag("Bus", "跨服总线已启用 — transport: {}"), cfg.crossServerTransport())

                // Agent 读「今日运营洞察」的手（各子服统计/分群/词云 + 告警）——夜间整理任务靠它取数据。
                // 带持久化缓存 + 白天定时刷新：00:00 无人在线时回退最近快照，避免夜间整理拉空、空手而归。
                opsInsightInst = OpsInsightTool(
                    b, online, alerts, cfg.remoteTimeoutMs(),
                    dataDirectory.resolve("ops-digest-cache.json"), cfg.opsDigestCacheMaxAgeHours()
                ).also { it.startAutoRefresh(cfg.opsDigestRefreshMin()) }
                extraTools += opsInsightInst!!
                // 代理层捕获聊天做词云（绕开 Bukkit 侧聊天事件 bug），词频经总线回各子服
                chatWords = ChatWordCollector(b, cfg.remoteTimeoutMs()).also { server.eventManager.register(this, it); it.start() }

                // 主动运维哨兵：定时巡检在线子服健康 → 异常调 LLM 出处置建议 → 通知(控制台 + WebUI)。
                // 处置走"建议→人工"（高危仍走已有审批闸），哨兵自身不自动执行命令。
                if (cfg.sentinelEnabled()) {
                    val smapper = ObjectMapper()
                    val probe: (String) -> HealthSnapshot? = { srv ->
                        runCatching {
                            val rep = b.dispatch(srv, "health_query", "{}", cfg.remoteTimeoutMs())
                                .get(cfg.remoteTimeoutMs() + 1000, java.util.concurrent.TimeUnit.MILLISECONDS)
                            if (!rep.success) null else smapper.readTree(rep.content).let { j ->
                                HealthSnapshot(srv, true, j["tps"]?.asDouble() ?: -1.0, j["online"]?.asInt() ?: -1,
                                    j["memUsedMb"]?.asLong() ?: 0L, j["memMaxMb"]?.asLong() ?: 0L,
                                    j["platform"]?.asText() ?: "", j["mcVersion"]?.asText() ?: "",
                                    j["brand"]?.asText() ?: "", j["modCount"]?.asInt() ?: -1)
                            }
                        }.getOrNull()
                    }
                    val notifier = CompositeNotifier(listOf(LogNotifier(), alerts))
                    val adviser = fastLlm ?: effectiveLlm
                    sentinel = HealthMonitor(online, probe,
                        HealthMonitor.Config(cfg.sentinelIntervalSec(), cfg.sentinelTpsMin(), cfg.sentinelMemPct(), cfg.sentinelPlayerDrop())
                    ) { inc ->
                        val advice = if (cfg.sentinelAdvise() && inc.kind != IncidentKind.RECOVERED) sentinelAdvise(inc, adviser) else null
                        notifier.notify(inc, advice)
                    }.also { it.start() }
                }
            }.onFailure { logger.error(WindyLog.tag("Bus", "跨服总线启动失败，将仅以本代理模式运行：{}"), it.message) }
        }

        // 人格文件（可选）
        val personality = PersonalityLoader.load(dataDirectory, cfg.personalityFile())

        val platform = VelocityPlatform(server, audit, extraTools)
        platform.personality = personality

        // 上下文压缩器
        val compressor = if (cfg.compressionEnabled()) ContextCompressor(fastLlm ?: effectiveLlm, cfg.compressionThreshold(), cfg.compressionKeepRecent()) else null

        // 用户画像管理器
        val profileManager = if (cfg.profilesEnabled()) UserProfileManager(dataDirectory.resolve("profiles")) else null

        // ── 新增组件接线 ──

        // Prompt 版本化：加载自定义 system prompt（如有），追加到 personality
        val promptVersioning = if (cfg.promptVersioningEnabled()) {
            org.windy.windyagent.agent.PromptVersioning(dataDirectory.resolve("prompts")).also {
                val loaded = it.load()
                if (loaded.isNotBlank()) {
                    platform.personality = if (platform.personality.isNotBlank()) platform.personality + "\n\n" + loaded else loaded
                    logger.info(WindyLog.tag("Prompt", "已加载自定义 prompt（{} 字符）"), loaded.length)
                }
            }
        } else null

        // 记忆整合器：定期合并去重长期记忆
        val consolidator = if (cfg.memoryConsolidateEnabled() && memory != null) {
            org.windy.windyagent.memory.MemoryConsolidator(memory!!, fastLlm ?: effectiveLlm).also {
                consolidatorInst = it
                it.start(cfg.memoryConsolidateIntervalHours())
            }
        } else null

        // 成本路由：按复杂度自动选便宜/贵模型
        val costRouterLlm = if (cfg.costRouterEnabled() && fastLlm != null) {
            org.windy.windyagent.agent.CostRouter(effectiveLlm, fastLlm).also {
                logger.info(WindyLog.tag("LLM", "成本路由已启用 — cheap: ${fastLlm.name}, expensive: ${effectiveLlm.name}"))
            }
        } else effectiveLlm

        // 失败检测 + 工具缓存 + 自检 + 轨迹记录
        val failureDetector = if (cfg.failureDetectEnabled()) org.windy.windyagent.agent.FailureDetector() else null
        val toolResultCache = if (cfg.toolCacheEnabled()) org.windy.windyagent.agent.ToolResultCache(cfg.toolCacheMaxSize(), cfg.toolCacheTtlSeconds() * 1000) else null
        val selfChecker = if (cfg.selfCheckEnabled()) org.windy.windyagent.agent.SelfChecker(fastLlm ?: effectiveLlm) else null
        val trajectoryRecorder = if (cfg.trajectoryEnabled()) {
            org.windy.windyagent.agent.TrajectoryRecorder(dataDirectory.resolve("trajectories")).also { trajectoryRecorderInst = it }
        } else null

        // 子任务并行编排器
        val subAgent = if (cfg.subAgentEnabled()) {
            org.windy.windyagent.agent.SubAgentOrchestrator(fastLlm ?: effectiveLlm, { platform.tools }, platform.systemPrompt)
        } else null

        // 自动在简单(ReAct) / 复杂多步(Plan-Execute) 任务间路由；注入长期记忆做自动召回
        val sessions = SessionManager(cfg.maxHistory())
        // SystemHealth 数据聚合（供 Dashboard /api/system）——早建以便工具回调注册
        val systemHealth = org.windy.windyagent.ops.SystemHealth(usageTracker, sessions)
        val toolCallRecorder: (String, Long, Boolean) -> Unit = { tool, latency, success -> systemHealth.recordToolCall(tool, latency, success) }
        val react = ReActAgent(costRouterLlm, failureDetector = failureDetector, toolResultCache = toolResultCache, selfChecker = selfChecker, trajectoryRecorder = trajectoryRecorder, onToolCall = toolCallRecorder)
        val plan = PlanExecuteAgent(costRouterLlm, failureDetector = failureDetector, toolResultCache = toolResultCache, selfChecker = selfChecker, trajectoryRecorder = trajectoryRecorder, onToolCall = toolCallRecorder)
        val agent = AgentRouter(costRouterLlm, react, plan, memory, cfg.memoryRecallTopK(), fastLlm, compressor, profileManager, subAgent, cfg.profileUpdateMinIntervalSec() * 1000L)

        // 定时任务调度器：broadcast/command 走总线下发；**agent 任务交给 Agent 自己执行**（无人值守、高危自动拦截）。
        // 建在 agent 之后（agent 任务要用它）；需跨服总线（下发/取数走总线）。内置一条凌晨 0 点的知识整理任务。
        val schedMapper = ObjectMapper()
        scheduler = bus?.let { b ->
            // 一步确定性执行（broadcast/command 下发到目标子服）；脚本任务逐步调它
            val runStep: (String, String, String) -> String = { action, target, payload ->
                val targets = if (target == "*" || target.isBlank()) connectedServers() else setOf(target)
                if (targets.isEmpty()) "无目标子服（未连接）" else {
                    val act = if (action == "command") "run_command" else "broadcast"
                    val field = if (action == "command") "command" else "message"
                    val args = schedMapper.createObjectNode().put(field, payload).toString()
                    targets.joinToString("；") { srv ->
                        val rep = runCatching { b.dispatch(srv, act, args, cfg.remoteTimeoutMs()).get(cfg.remoteTimeoutMs() + 1000, java.util.concurrent.TimeUnit.MILLISECONDS) }.getOrNull()
                        "$srv:" + (rep?.content?.take(40) ?: "超时")
                    }
                }
            }
            TaskScheduler(dataDirectory.resolve("tasks.json")) { task ->
                when (task.action) {
                    // 实时：现场交给 Agent 判断执行（夜间整理这类要读数据决策的）
                    "agent" -> {
                        val sid = "scheduler-${task.id}"
                        // {today} 占位符 → 运行时实际日期（供夜间报告按日期归档，不依赖模型自己算日期）
                        val payload = task.payload.replace("{today}", java.time.LocalDate.now().toString())
                        val resp = agent.run(AgentContext(sid, payload, platform, sessions.getHistory(sid), TrustLevel.TRUSTED, unattended = true))
                        sessions.trimHistory(sid); resp.message ?: "(无回复)"
                    }
                    // 脚本：跑创建时 LLM 编译好的固定步骤，确定性、不调 LLM
                    "script" -> if (task.script.isEmpty()) "脚本为空（未生成步骤）" else
                        task.script.mapIndexed { i, s -> "步骤${i + 1} " + runStep(s.action, s.target, s.payload) }.joinToString("\n")
                    // 技能：经总线在目标子服执行服主创建的技能（skill_name 存在 payload）
                    "skill" -> {
                        val targets = if (task.target == "*" || task.target.isBlank()) connectedServers() else setOf(task.target)
                        if (targets.isEmpty()) "无目标子服（未连接）" else {
                            val skillPayload = schedMapper.createObjectNode().put("skill", task.payload).toString()
                            targets.joinToString("；") { srv ->
                                val rep = runCatching { b.dispatch(srv, "run_skill", skillPayload, cfg.remoteTimeoutMs()).get(cfg.remoteTimeoutMs() + 1000, java.util.concurrent.TimeUnit.MILLISECONDS) }.getOrNull()
                                "$srv:" + (rep?.content?.take(60) ?: "超时")
                            }
                        }
                    }
                    // 单动作：广播 / 命令
                    else -> runStep(task.action, task.target, task.payload)
                }
            }.also { sch ->
                val def = defaultNightlyTask()
                val existing = sch.list().firstOrNull { it.id == def.id }
                when {
                    // 首装（无任何任务）：播种内置夜间整理
                    existing == null && sch.list().isEmpty() -> sch.upsert(def)
                    // 已有内置任务：升级文案到最新（保留用户改过的开关/时间/周几），换 jar 自动生效
                    existing != null && existing.payload != def.payload -> sch.upsert(existing.copy(payload = def.payload))
                }
                sch.start()
            }
        }
        // 对话式定时任务管理
        scheduler?.let { sch -> extraTools += org.windy.windyagent.agent.ScheduleTool(sch, audit) }

        // 载体无关的元命令路由（help/clear/history/status/approve…）
        val statusSupplier = {
            "提供方：${llm.name}\n工具：${platform.tools.size} 个\n安全：mode=${cfg.safetyMode()}\n" +
                "跨服：${if (cfg.crossServerEnabled()) cfg.crossServerTransport() else "未启用"}"
        }
        val router = AgentCommandRouter(sessions, pending, audit, memory, statusSupplier, valueExecutor, usageTracker, compressor, profileManager)

        // AI 管理控制台（WebUI）：聊天接 router/agent（多轮靠 SessionManager），知识库接 KnowledgeManager，
        // 行为看板经总线。放最后装配（依赖 agent/会话）；webEnabled 即开，与跨服是否启用无关。

        // 共享聊天存档：web 控制台与 IM 联动（QQ 等）共用同一实例、同 session id → 记录互通、对话无缝衔接。
        val chatArchive = org.windy.windyagent.web.ChatArchive(dataDirectory)
        // 平台无关的会话函数：元命令路由优先，否则跑带工具 Agent；带多轮上下文 + 磁盘持久化(sessionStore)。
        // web 与 IM 共用，保证「同一 session id → 同一段对话」。
        // 会话函数（带过程回调 onStep + 同会话串行 #1）：元命令路由优先，否则跑带工具 Agent；
        // 多轮上下文 + 持久化。onStep 供流式对话实时展示工具/skill 过程；withSessionLock 防并发破坏 history。
        val processChat: (String, String, (String, Boolean, Long) -> Unit) -> String = { sid, msg, onStep ->
            sessions.withSessionLock(sid) {
                router.dispatch(msg, sid, TrustLevel.TRUSTED) ?: run {
                    // web/QQ：若 session id 恰是在线玩家，顺带注入其所在子服（"子服不用报"）；否则留空。
                    val reqServer = server.getPlayer(sid).flatMap { it.currentServer }.map { it.serverInfo.name }.orElse("")
                    val resp = agent.run(AgentContext(sid, msg, platform, sessions.getHistory(sid), TrustLevel.TRUSTED, requester = sid, requesterServer = reqServer, onStep = onStep))
                    sessions.trimHistory(sid); resp.message ?: "(无回复)"
                }.also { reply -> sessionStore?.let { it.append(sid, "user", msg); it.append(sid, "assistant", reply) } }
            }
        }
        // 无过程版：web 非流式 / QQ 联动复用（onStep 空）
        val agentChat: (String, String) -> String = { sid, msg -> processChat(sid, msg) { _, _, _ -> } }

        // 玩家管理：注册「在线玩家基础行」供给器 + 「踢人」操作到可扩展的 PlayerDirectory。
        // 未来经济/领地/封禁等插件再注册 Contributor(加列) / Action(加操作)，玩家管理面板自动聚合、无需改动。
        org.windy.windyagent.player.PlayerDirectory.setBaseSupplier {
            server.allPlayers.map { p ->
                mapOf<String, Any?>(
                    "name" to p.username,
                    "server" to p.currentServer.map { it.serverInfo.name }.orElse("—"),
                    "ping" to p.ping,
                )
            }
        }
        org.windy.windyagent.player.PlayerDirectory.registerAction(object : org.windy.windyagent.player.PlayerDirectory.PlayerAction {
            override val id = "kick"
            override val label = "踢出"
            override val danger = true
            override fun run(player: String, args: Map<String, String>): String {
                val p = server.getPlayer(player).orElse(null) ?: return "玩家「$player」不在线"
                val reason = args["reason"]?.takeIf { it.isNotBlank() } ?: "你已被管理员踢出服务器"
                p.disconnect(net.kyori.adventure.text.Component.text(reason))
                return "已将「$player」踢出"
            }
        })
        // 封禁 / 解封：经总线在玩家所在子服执行 ban/pardon 命令（复用现有跨服命令下发；需启用跨服）。
        // 子服装了 CMI 等会由其接管 ban 语义；否则走原版 /ban。
        fun runOnPlayerServer(player: String, cmd: String, fallbackAny: Boolean): String {
            val b = bus ?: return "未启用跨服总线，无法在子服执行封禁（cross-server.enabled: false）"
            val online = server.getPlayer(player).flatMap { it.currentServer }.map { it.serverInfo.name }.orElse(null)
            val target = online ?: (if (fallbackAny) connectedServers().firstOrNull() else null)
                ?: return "玩家「$player」不在线且无可用子服，无法定位执行封禁"
            val payload = ObjectMapper().createObjectNode().put("command", cmd).toString()
            val reply = runCatching {
                b.dispatch(target, "run_command", payload, cfg.remoteTimeoutMs())
                    .get(cfg.remoteTimeoutMs() + 1000, java.util.concurrent.TimeUnit.MILLISECONDS)
            }.getOrNull() ?: return "在子服「$target」执行超时"
            return if (reply.success) "已在子服「$target」执行：$cmd" else "执行失败：${reply.content}"
        }
        org.windy.windyagent.player.PlayerDirectory.registerAction(object : org.windy.windyagent.player.PlayerDirectory.PlayerAction {
            override val id = "ban"
            override val label = "封禁"
            override val danger = true
            override fun run(player: String, args: Map<String, String>): String {
                val reason = args["reason"]?.takeIf { it.isNotBlank() }
                val cmd = if (reason != null) "ban $player $reason" else "ban $player"
                return runOnPlayerServer(player, cmd, fallbackAny = true)
            }
        })
        org.windy.windyagent.player.PlayerDirectory.registerAction(object : org.windy.windyagent.player.PlayerDirectory.PlayerAction {
            override val id = "pardon"
            override val label = "解封"
            override val danger = false
            override fun run(player: String, args: Map<String, String>): String =
                runOnPlayerServer(player, "pardon $player", fallbackAny = true)
        })

        var webUrl: String? = null   // 供启动横幅展示；null = 未开启
        if (cfg.webEnabled()) {
            // 默认开启 web：token 为空则首启自动生成随机串写回配置，避免裸奔
            val tokenWasBlank = cfg.webToken().isBlank()
            val webToken = cfg.ensureWebToken()
            if (tokenWasBlank && webToken.isNotBlank()) {
                // 不在日志打印 token 本身（防截图/上传泄漏）；只提示去配置文件查看
                logger.info(WindyLog.tag("Web", "首次启动已自动生成访问令牌并写入 windyagent-config.yml 的 web.token —— 请从配置文件查看（日志不显示以防泄漏）。"))
            }
            val webHostShown = if (cfg.webHost() == "0.0.0.0") "<本机IP>" else cfg.webHost()
            webUrl = "http://$webHostShown:${cfg.webPort()}/"
            val chat = agentChat // web 与 IM 共用同一会话函数
            // 流式聊天：LLM 实现 StreamingProvider 时启用真流式（逐块推送）
            // Web 对话流式：曾用 costRouterLlm.chatStream(...) 逐 token 真流式，但那是「裸 LLM」
            // (工具传 emptyList) —— 不能查在线 / 执行命令，AI 只能瞎编(如 0 人在线却答 15 人)，
            // 还会把思维链模型的 null delta 发成满屏 "null"。
            // 故置 null：ChatHandler 退回「假切片」路径，用带工具的 Agent(chat = agent.run) 跑出
            // 真实回复后再按块推送 —— 既能真正调用工具，又保留打字机观感。
            val streamChat: ((String, String, (String) -> Unit) -> String)? = null
            val draftSys = "你帮服主把一段话整理成一条服务器知识库条目。只输出 JSON：" +
                "{\"title\":简短标题,\"content\":正文,\"tags\":[2-4个关键词]}。不要解释、不要代码块标记。"
            val draft: (String) -> String = { nl -> (fastLlm ?: effectiveLlm).chat(draftSys, listOf(LLMMessage.User(nl))).textContent ?: "" }
            // 知识库 AI 编辑：按【要求】改写【正文】，只回改写后的正文本身（供面板 AI 栏/右键动作用）
            val kbAiSys = "你是知识库正文编辑助手。根据【要求】处理【正文】，只输出处理后的正文本身：" +
                "不要加解释、不要加标题、不要用 ``` 代码块围栏。保持 Markdown 结构，语言默认简体中文（除非要求翻译）。"
            val kbAi: (String, String) -> String = { instruction, text ->
                (fastLlm ?: effectiveLlm).chat(kbAiSys, listOf(LLMMessage.User("【要求】$instruction\n\n【正文】\n$text"))).textContent ?: ""
            }
            // AI 整理：把服主一句话需求润成给 Agent 执行的清晰任务描述
            val refineSys = "把服主的一句话需求整理成给运维 AI 执行的清晰任务描述。只输出整理后的任务描述本身（一段话），不要解释、不要步骤编号、不要客套。"
            val refine: (String) -> String = { nl -> (fastLlm ?: effectiveLlm).chat(refineSys, listOf(LLMMessage.User(nl))).textContent ?: "" }
            // 脚本编译：把需求描述编译成"待执行步骤"JSON 数组（创建时跑一次 LLM，到点确定性执行，不再调 LLM）
            val cmapper = ObjectMapper()
            val compileScript: (String, String) -> String = { desc, server ->
                val sys = "你是运维任务编译器。把管理员的需求编译成一串可执行步骤。可用动作仅两种：" +
                    "broadcast(向玩家广播一句话，payload=文案)、command(在子服后台执行一条控制台命令，payload=命令不带斜杠)。" +
                    "target 填子服名（默认用「${server.ifBlank { "*" }}」），或 * 表示全部已连子服。" +
                    "严格只输出 JSON 数组，每项 {\"action\":\"broadcast或command\",\"target\":\"...\",\"payload\":\"...\"}，不要解释、不要代码块标记。"
                val raw = runCatching { (fastLlm ?: effectiveLlm).chat(sys, listOf(LLMMessage.User(desc))).textContent }.getOrNull().orEmpty()
                val i = raw.indexOf('['); val j = raw.lastIndexOf(']')
                val arr = if (i in 0 until j) raw.substring(i, j + 1) else "[]"
                runCatching { cmapper.readTree(arr).takeIf { it.isArray }?.toString() }.getOrNull() ?: "[]"
            }
            // 技能起草：把服主一句话需求 → 一份「纯文字技能」SKILL.md（流程型，给 Agent 照做用）
            val draftSkillSys = "你帮服主把一段话整理成一个 WindyAgent「纯文字技能」的 SKILL.md。" +
                "格式：以 --- 开头的 YAML frontmatter，含 name(英文小写下划线) 和 description(一句话说明「何时使用本技能」)，" +
                "再 --- 结束，之后是 markdown 正文，写清 Agent 应遵循的操作步骤。" +
                "正文可引用现有工具名，如 get_balance / run_command_on_server / knowledge_search / remember 等，让 Agent 用它们办事而非写代码。" +
                "只输出 SKILL.md 内容本身，不要解释、不要代码块围栏。"
            val draftSkill: (String) -> String = { nl -> (fastLlm ?: effectiveLlm).chat(draftSkillSys, listOf(LLMMessage.User(nl))).textContent ?: "" }
            web = DashboardServer(cfg.webHost(), cfg.webPort(), webToken,
                org.windy.windyagent.web.WebSecurity(webToken, cfg.webSecurityEntry(), cfg.webSecurityMaxFails(), cfg.webSecurityLockMinutes())).also { srv ->
                val proxyMeta = {
                    val mapper = ObjectMapper()
                    val obj = mapper.createObjectNode()
                    obj.put("name", cfg.serverName().ifBlank { "WindyAgent" })
                    obj.put("platform", "Velocity")
                    obj.put("proxyVersion", "${server.version.name} ${server.version.version}")
                    obj.put("onlinePlayers", server.playerCount)
                    obj.put("connectedServers", connectedServers().size)
                    obj.toString()
                }
                srv.register(org.windy.windyagent.web.handlers.ServerHandler(srv, bus, cfg.remoteTimeoutMs(), connectedServers, alerts, { sentinel?.snapshotsJson() ?: "[]" }, proxyMeta))
                srv.register(org.windy.windyagent.web.handlers.ChatHandler(srv, chat, chatArchive, streamChat, processChat))
                srv.register(org.windy.windyagent.web.handlers.ImHandler(srv))
                srv.register(org.windy.windyagent.web.handlers.PlayerHandler(srv))
                srv.register(org.windy.windyagent.web.handlers.BoardHandler(srv, bus, cfg.remoteTimeoutMs(), connectedServers))
                srv.register(org.windy.windyagent.web.handlers.KnowledgeHandler(srv, knowledge, draft, kbAi, kbCapsProvider))
                srv.register(org.windy.windyagent.web.handlers.TaskHandler(srv, scheduler, refine, compileScript))
                srv.register(org.windy.windyagent.web.handlers.ApprovalHandler(srv, pending))
                srv.register(org.windy.windyagent.web.handlers.SkillHandler(srv, skills, draftSkill, { skillSync?.syncAll(connectedServers()) ?: "跨服总线未启用" }, bus, cfg.remoteTimeoutMs(), connectedServers))
                srv.register(org.windy.windyagent.web.handlers.UsageHandler(srv, usageTracker, systemHealth))
                srv.register(org.windy.windyagent.web.handlers.SetupHandler(srv, cfg))   // 已配置时返回 configured:true
                val wpLoader = try {
                    val container = server.pluginManager.getPlugin("windypurchase").orElse(null)
                    logger.info("WindyPurchase container: {}", container)
                    val instance = container?.instance?.orElse(null)
                    logger.info("WindyPurchase instance: {}", instance)
                    val loader = instance?.javaClass?.classLoader
                    logger.info("WindyPurchase classloader: {}", loader)
                    loader
                } catch (e: Exception) {
                    logger.warn("Failed to get WindyPurchase classloader: {}", e.message)
                    null
                }
                srv.register(org.windy.windyagent.web.handlers.PurchaseHandler(srv, org.windy.windyagent.web.handlers.PurchaseBridge(wpLoader)))     // 充值管理（WindyPurchase 可选）
                srv.start()
            }
        }

        // 速率限制器
        val rateLimiter = if (cfg.rateLimitEnabled()) org.windy.windyagent.safety.RateLimiter(cfg.rateLimitBucketSize(), cfg.rateLimitRefillRate()) else null

        // 配置热重载
        val configWatcher = org.windy.windyagent.ConfigWatcher(dataDirectory).also { configWatcherInst = it }
        configWatcher.addListener("messages") { newCfg -> Messages.init(newCfg.language()) }
        configWatcher.start()

        // ── IM 平台联动（可选，Velocity 与 Bukkit 共用 core 的 ImConnectors）──
        // 把「外部超管消息 → 运维 Agent」接到各 IM 平台：服主在 IM 上用自然语言远程管理服务器。
        // 0 配置、复用各平台自己的超管名单、同进程内存直调（非 HTTP）。
        // 目前支持昕途(QQ)；未来加飞书/钉钉/企业微信只需在 ImConnectors 注册新 Connector，本处无需改动。
        // IM 会话函数 = 共享 agentChat（含上下文/持久化）+ 兼写共享 ChatArchive，
        // 使 QQ 超管聊的内容出现在 web 对应固定对话里（web↔QQ 无缝衔接）。
        val imChat: (String, String) -> String = { sid, msg ->
            chatArchive.append(sid, "u", msg)
            val reply = agentChat(sid, msg)
            chatArchive.append(sid, "a", reply)
            reply
        }
        org.windy.windyagent.bridge.ImConnectors.installAll(
            org.windy.windyagent.bridge.InstallEnv(
                chat = imChat,
                logger = logger,
                // Velocity：按 id 取插件主类实例（供 Connector cast 成 HostProvider）
                lookupPlugin = { id -> server.pluginManager.getPlugin(id).flatMap { it.instance }.orElse(null) },
                // Velocity 无 ServicesManager 机制
                lookupService = { null },
            )
        )

        // 玩家游戏内聊天触发：!ai <message>（永远只走知识库问答，不进 Agent）
        server.eventManager.register(this, VelocityChatListener(playerQa, platform, router, logger, cfg.trigger()))

        // 命令触发（控制台 + 玩家）：/ai <message>，命令名由 trigger 去掉前缀符号得出
        val commandName = cfg.trigger().trimStart('!', '/', '.', ' ').ifBlank { "ai" }
        val commandManager = server.commandManager
        val meta = commandManager.metaBuilder(commandName).plugin(this).build()
        commandManager.register(meta, AgentCommand(agent, playerQa, platform, sessions, router, logger, rateLimiter))

        // 顶层审批命令（薄适配 → router）
        for (act in listOf("approve", "deny", "pending")) {
            val m = commandManager.metaBuilder("ai-$act").plugin(this).build()
            commandManager.register(m, AgentApprovalCommand(router, act))
        }

        logger.info(WindyLog.banner(buildList {
            add("角色" to "中心 Agent · Velocity")
            add("模型" to (llm.name + (cfg.fastModel().takeIf { fastLlm != null }?.let { "  (fast: $it)" } ?: "")))
            add("工具" to "${platform.tools.size} 个")
            add("触发" to "聊天 '${cfg.trigger()} <消息>'  /  命令 '/$commandName <消息>'")
            add("跨服" to if (cfg.crossServerEnabled()) cfg.crossServerTransport() else "未启用")
            add("控制台" to (webUrl ?: "已关闭（web.enabled=false）"))
        }))
    }

    /**
     * 首启未配 LLM 时的「配置模式」：起一个只挂 SetupHandler 的精简 web 向导，并在控制台醒目提示。
     * 服主在网页填好 provider/key/model（或手改配置）后重启代理，即走完整初始化。
     */
    private fun startSetupMode(cfg: AgentConfig) {
        if (cfg.webEnabled()) {
            val token = cfg.ensureWebToken()
            val hostShown = if (cfg.webHost() == "0.0.0.0") "<本机IP>" else cfg.webHost()
            val url = "http://$hostShown:${cfg.webPort()}/"
            web = DashboardServer(cfg.webHost(), cfg.webPort(), token,
                org.windy.windyagent.web.WebSecurity(token, cfg.webSecurityEntry(), cfg.webSecurityMaxFails(), cfg.webSecurityLockMinutes())).also { srv ->
                srv.register(org.windy.windyagent.web.handlers.SetupHandler(srv, cfg))
                srv.start()
            }
            logger.warn(WindyLog.banner(buildList {
                add("状态" to "⚠ 尚未配置 LLM API Key —— 进入配置向导")
                add("配置页" to url)
                // 不在日志打印 token 本身（防截图/上传泄漏）；token 看配置文件 web.token 行
                add("token" to "见 plugins/windyagent/windyagent-config.yml 的 web.token")
                add("手动改" to "或编辑同文件的 llm.api-key")
                add("生效" to "配好后重启 Velocity 代理")
            }))
        } else {
            logger.warn(WindyLog.banner(buildList {
                add("状态" to "⚠ 尚未配置 LLM API Key，且 web 已关闭")
                add("请手动" to "编辑 windyagent-config.yml 填 llm.api-key 后重启")
                add("或" to "设 web.enabled: true 用网页向导配置")
            }))
        }
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
            logger.info(WindyLog.tag("Bus", "使用 SocketHubBus（自建 TCP 中枢）— bind {}:{}"), cfg.socketHost(), cfg.socketPort())
        }
        "inprocess", "memory" -> InProcessBus().also { bus ->
            server.allServers.forEach { rs ->
                val name = rs.serverInfo.name
                bus.listen(name) { req ->
                    ToolReply(req.requestId, true, "[模拟子服 $name] 已接收 ${req.action}：${req.argsJson}")
                }
            }
            logger.warn(WindyLog.tag("Bus", "使用 InProcessBus（测试用，不跨进程）— 已为 {} 个子服注册回显 stub"), server.allServers.size)
        }
        else -> RedisBus(cfg.redisHost(), cfg.redisPort(), cfg.redisPassword())
    }

    /** 内置定时任务：每天凌晨 0 点，让 Agent 无人值守整理今日数据 → 沉淀知识库 + 记忆。 */
    private fun defaultNightlyTask() = ScheduledTask(
        id = "builtin-nightly-curation",
        name = "夜间知识整理（内置）",
        enabled = true,
        action = "agent",
        target = "",
        payload = "现在是夜间无人值守整理时间。请依次：" +
            "1) 调用 ops_digest 拉取今日各子服运营摘要（若返回的是「最近一次快照」也照常沉淀）；" +
            "2) 从高频聊天词/命令里识别玩家反复关注或常问的点，用 knowledge_write 沉淀/更新「玩家常见问答(FAQ)」，folder 一律填 FAQ、tags 含「自动」（只留长期有用的，别记一次性琐碎）；" +
            "3) 写一条当日运营报告：knowledge_write，folder 填 运营/报告、标题「运营报告 {today}」、tags 含「自动」，正文含今日关键运营数据与运维要点；" +
            "4) 再把近期要点滚动汇总更新到运营日志：knowledge_write，folder 填 运营、标题「运营日志」、tags 含「自动」，正文保留近期数天要点、删去过时内容（同标题同目录=覆盖更新）；" +
            "5) 用 remember 把玩家结构变化（新增/活跃/流失趋势）记入管理方记忆。" +
            "注意：无人值守模式下任何高危服务器命令都会被拦截，只做整理与沉淀，不要破坏性操作。",
        type = "daily",
        time = "00:00",
        days = emptyList()
    )

    /** 哨兵告警 → 调 LLM 给一句话诊断 + 处置建议（建议式，人工执行/审批）。用便宜模型省 token。 */
    private fun sentinelAdvise(inc: Incident, advisor: LLMProvider): String {
        val sys = "你是资深 Minecraft 服务器运维。下面是一条服务器健康告警。用最多 4 行中文给出：" +
            "①一句话判断最可能原因；②建议的具体处置（可含命令）。高危命令请注明「需人工确认」。不要寒暄、不要客套。"
        val s = inc.snapshot
        val detail = buildString {
            append("子服：${inc.server}\n类型：${inc.kind}\n详情：${inc.detail}")
            if (s != null) append("\n快照：TPS=${s.tps} 在线=${s.online} 内存=${s.memUsedMb}/${s.memMaxMb}MB")
        }
        return runCatching { advisor.chat(sys, listOf(LLMMessage.User(detail))).textContent }.getOrNull()?.trim().orEmpty()
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        configWatcherInst?.stop(); configWatcherInst = null
        sentinel?.stop(); sentinel = null
        scheduler?.stop(); scheduler = null
        chatWords?.stop(); chatWords = null
        usageTrackerInst?.close(); usageTrackerInst = null
        consolidatorInst?.stop(); consolidatorInst = null
        opsInsightInst?.stop(); opsInsightInst = null
        trajectoryRecorderInst?.close(); trajectoryRecorderInst = null
        sessionStoreInst?.close(); sessionStoreInst = null
        web?.stop(); web = null
        bus?.close()
        bus = null
        logger.info(WindyLog.tag("Boot", "WindyAgent 已停止。"))
    }
}
