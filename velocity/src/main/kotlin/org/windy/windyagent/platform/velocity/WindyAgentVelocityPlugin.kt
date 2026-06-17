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
import org.windy.windyagent.agent.PlanExecuteAgent
import org.windy.windyagent.agent.ReActAgent
import org.windy.windyagent.agent.RemoteAppraiseTool
import org.windy.windyagent.agent.RemoteBalanceTool
import org.windy.windyagent.agent.RemoteCommandTool
import org.windy.windyagent.agent.RemoteProposePackTool
import org.windy.windyagent.agent.RemoteRefreshItemsTool
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
import org.windy.windyagent.knowledge.KnowledgeManager
import org.windy.windyagent.knowledge.KnowledgeWriteTool
import org.windy.windyagent.knowledge.PlayerQa
import org.windy.windyagent.ops.ScheduledTask
import org.windy.windyagent.agent.AgentContext
import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.safety.TrustLevel
import org.windy.windyagent.memory.FileLongTermMemory
import org.windy.windyagent.memory.RememberTool
import org.windy.windyagent.buildCommandGuard
import org.windy.windyagent.mcp.McpLoader
import org.windy.windyagent.rag.LlmQueryExpander
import org.windy.windyagent.safety.AuditLog
import org.windy.windyagent.safety.PendingApprovals
import org.windy.windyagent.knowledge.KnowledgeSearchTool
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

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        logger.info("WindyAgent initializing...")

        val cfg = runCatching { AgentConfig.load(dataDirectory) }.getOrElse {
            logger.error("Failed to load windyagent-config.yml: {}", it.message)
            return
        }
        Messages.init(cfg.language())

        val llm = runCatching { buildProvider(cfg) }.getOrElse {
            logger.error(it.message)
            return
        }
        // 元任务（路由/扩展）便宜模型，省 token；未配则用主模型
        val fastLlm = buildFastProvider(cfg)
        if (fastLlm != null) logger.info("元任务便宜模型：{}", cfg.fastModel())

        val extraTools = mutableListOf<AgentTool>()
        var valueExecutor: org.windy.windyagent.command.ValueExecutor? = null
        var connectedServers: () -> Set<String> = { emptySet() }   // 跨服启用后指向能力注册表
        val alerts = AlertCenter()   // 哨兵告警缓冲（供 WebUI /api/alerts 拉取），早建以便哨兵与看板共用

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

        // 知识库（可读写 + 热重载，供 WebUI 编辑）：挂上 knowledge_search 工具
        val knowledge = KnowledgeManager(dataDirectory.resolve("knowledge"))
        extraTools += KnowledgeSearchTool(knowledge, expander, cfg.ragMinHits())
        extraTools += KnowledgeWriteTool(knowledge)   // 让 Agent 能写知识库（夜间整理沉淀用，仅 TRUSTED）
        logger.info("知识库已加载 — {} 条", knowledge.size())
        // 玩家问答：游戏内 !ai / 非管理员 /ai 走这个——只检索知识库作答，不进 Agent、不碰工具
        val playerQa = PlayerQa(fastLlm ?: llm, knowledge, expander)

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
            logger.info("技能库已加载 — 共 {} 个（文字 {} 在中心执行 / 脚本 {} 下发子服，其中 {} 个工作流）",
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
                // 服主编写的子服技能（GroovyShell 执行）：人工审过的确定性扩展，不过 guard，记 audit
                extraTools += org.windy.windyagent.agent.RemoteSkillTool(b, cfg.remoteTimeoutMs(), audit)
                // 物品估值 / 礼包提案（数据在子服，远端调用）
                extraTools += RemoteAppraiseTool(b, cfg.remoteTimeoutMs())
                extraTools += RemoteProposePackTool(b, cfg.remoteTimeoutMs())
                extraTools += RemoteRefreshItemsTool(b, cfg.remoteTimeoutMs())
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
                            logger.warn("[日志监控] 子服 {} 检测到 {} 级异常：{}", server, severity, pattern)
                        }
                    }
                }
                if (cfg.embeddingEnabled()) logger.info("能力检索启用语义向量（embedding: {}）", cfg.embeddingModel())
                extraTools += SearchCapabilitiesTool(registry, expander, cfg.ragMinHits())
                // 在线判定：优先用总线的"真实在线"集（Socket 中枢=活动连接）；传输报不了(如 Redis)才退回
                // 注册表的"曾见过"集。避免选/派到离线子服白等超时（"假在线"）。
                val online = { b.onlineServers() ?: registry.servers() }
                // value / 运维命令的远端执行后端：子服名取当前在线
                valueExecutor = RemoteValueExecutor(b, cfg.remoteTimeoutMs(), fastLlm ?: llm, cfg.itemLlmBatchSize(), cfg.itemRarityTiers(), online)
                connectedServers = online
                bus = b
                logger.info("跨服总线已启用 — transport: {}", cfg.crossServerTransport())

                // Agent 读「今日运营洞察」的手（各子服统计/分群/词云 + 告警）——夜间整理任务靠它取数据
                extraTools += OpsInsightTool(b, online, alerts, cfg.remoteTimeoutMs())
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
                    val adviser = fastLlm ?: llm
                    sentinel = HealthMonitor(online, probe,
                        HealthMonitor.Config(cfg.sentinelIntervalSec(), cfg.sentinelTpsMin(), cfg.sentinelMemPct(), cfg.sentinelPlayerDrop())
                    ) { inc ->
                        val advice = if (cfg.sentinelAdvise() && inc.kind != IncidentKind.RECOVERED) sentinelAdvise(inc, adviser) else null
                        notifier.notify(inc, advice)
                    }.also { it.start() }
                }
            }.onFailure { logger.error("跨服总线启动失败，将仅以本代理模式运行：{}", it.message) }
        }

        val platform = VelocityPlatform(server, audit, extraTools)
        // 自动在简单(ReAct) / 复杂多步(Plan-Execute) 任务间路由；注入长期记忆做自动召回
        val agent = AgentRouter(llm, ReActAgent(llm), PlanExecuteAgent(llm), memory, cfg.memoryRecallTopK(), fastLlm)
        val sessions = SessionManager(cfg.maxHistory())

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
                        val resp = agent.run(AgentContext(sid, task.payload, platform, sessions.getHistory(sid), TrustLevel.TRUSTED, unattended = true))
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
            }.also { sch -> if (sch.list().isEmpty()) sch.upsert(defaultNightlyTask()); sch.start() }
        }
        // 对话式定时任务管理
        scheduler?.let { sch -> extraTools += org.windy.windyagent.agent.ScheduleTool(sch, audit) }

        // 载体无关的元命令路由（help/clear/history/status/approve…）
        val statusSupplier = {
            "提供方：${llm.name}\n工具：${platform.tools.size} 个\n安全：mode=${cfg.safetyMode()}\n" +
                "跨服：${if (cfg.crossServerEnabled()) cfg.crossServerTransport() else "未启用"}"
        }
        val router = AgentCommandRouter(sessions, pending, audit, memory, statusSupplier, valueExecutor)

        // AI 管理控制台（WebUI）：聊天接 router/agent（多轮靠 SessionManager），知识库接 KnowledgeManager，
        // 行为看板经总线。放最后装配（依赖 agent/会话）；webEnabled 即开，与跨服是否启用无关。
        if (cfg.webEnabled()) {
            val chat: (String, String) -> String = { sid, msg ->
                router.dispatch(msg, sid, TrustLevel.TRUSTED) ?: run {
                    val resp = agent.run(AgentContext(sid, msg, platform, sessions.getHistory(sid), TrustLevel.TRUSTED))
                    sessions.trimHistory(sid); resp.message ?: "(无回复)"
                }
            }
            val draftSys = "你帮服主把一段话整理成一条服务器知识库条目。只输出 JSON：" +
                "{\"title\":简短标题,\"content\":正文,\"tags\":[2-4个关键词]}。不要解释、不要代码块标记。"
            val draft: (String) -> String = { nl -> (fastLlm ?: llm).chat(draftSys, listOf(LLMMessage.User(nl))).textContent ?: "" }
            // AI 整理：把服主一句话需求润成给 Agent 执行的清晰任务描述
            val refineSys = "把服主的一句话需求整理成给运维 AI 执行的清晰任务描述。只输出整理后的任务描述本身（一段话），不要解释、不要步骤编号、不要客套。"
            val refine: (String) -> String = { nl -> (fastLlm ?: llm).chat(refineSys, listOf(LLMMessage.User(nl))).textContent ?: "" }
            // 脚本编译：把需求描述编译成"待执行步骤"JSON 数组（创建时跑一次 LLM，到点确定性执行，不再调 LLM）
            val cmapper = ObjectMapper()
            val compileScript: (String, String) -> String = { desc, server ->
                val sys = "你是运维任务编译器。把管理员的需求编译成一串可执行步骤。可用动作仅两种：" +
                    "broadcast(向玩家广播一句话，payload=文案)、command(在子服后台执行一条控制台命令，payload=命令不带斜杠)。" +
                    "target 填子服名（默认用「${server.ifBlank { "*" }}」），或 * 表示全部已连子服。" +
                    "严格只输出 JSON 数组，每项 {\"action\":\"broadcast或command\",\"target\":\"...\",\"payload\":\"...\"}，不要解释、不要代码块标记。"
                val raw = runCatching { (fastLlm ?: llm).chat(sys, listOf(LLMMessage.User(desc))).textContent }.getOrNull().orEmpty()
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
            val draftSkill: (String) -> String = { nl -> (fastLlm ?: llm).chat(draftSkillSys, listOf(LLMMessage.User(nl))).textContent ?: "" }
            web = DashboardServer(cfg.webHost(), cfg.webPort(), cfg.webToken(), bus, cfg.remoteTimeoutMs(), dataDirectory,
                connectedServers, chat, knowledge, draft, alerts, { sentinel?.snapshotsJson() ?: "[]" }, scheduler, refine, pending, compileScript, draftSkill,
                skills, { skillSync?.syncAll(connectedServers()) ?: "跨服总线未启用，无法下发" }).also { it.start() }
        }

        // 玩家游戏内聊天触发：!ai <message>（永远只走知识库问答，不进 Agent）
        server.eventManager.register(this, VelocityChatListener(playerQa, platform, router, logger, cfg.trigger()))

        // 命令触发（控制台 + 玩家）：/ai <message>，命令名由 trigger 去掉前缀符号得出
        val commandName = cfg.trigger().trimStart('!', '/', '.', ' ').ifBlank { "ai" }
        val commandManager = server.commandManager
        val meta = commandManager.metaBuilder(commandName).plugin(this).build()
        commandManager.register(meta, AgentCommand(agent, playerQa, platform, sessions, router, logger))

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

    /** 内置定时任务：每天凌晨 0 点，让 Agent 无人值守整理今日数据 → 沉淀知识库 + 记忆。 */
    private fun defaultNightlyTask() = ScheduledTask(
        id = "builtin-nightly-curation",
        name = "夜间知识整理（内置）",
        enabled = true,
        action = "agent",
        target = "",
        payload = "现在是夜间无人值守整理时间。请依次：" +
            "1) 调用 ops_digest 拉取今日各子服运营摘要；" +
            "2) 从高频聊天词/命令里识别玩家反复关注或常问的点，用 knowledge_write 整理成或更新「玩家常见问答(FAQ)」类知识条目（只沉淀长期有用的，避免一次性琐碎）；" +
            "3) 把今日运维告警与异常归纳，用 knowledge_write 追加到 id 为 ops-log、标题「运营日志」的滚动条目（覆盖更新，正文保留近期要点）；" +
            "4) 把玩家结构变化（新增/活跃/流失趋势）用 remember 记入管理方记忆。" +
            "注意：你处于无人值守模式，任何高危服务器命令都会被拦截，不要尝试破坏性操作，只做整理与沉淀。",
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
        sentinel?.stop(); sentinel = null
        scheduler?.stop(); scheduler = null
        chatWords?.stop(); chatWords = null
        web?.stop(); web = null
        bus?.close()
        bus = null
        logger.info("WindyAgent stopped.")
    }
}
