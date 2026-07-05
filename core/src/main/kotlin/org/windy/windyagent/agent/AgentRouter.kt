package org.windy.windyagent.agent

import org.slf4j.LoggerFactory
import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.llm.LLMProvider
import org.windy.windyagent.memory.LongTermMemory
import org.windy.windyagent.safety.RequestContext
import org.windy.windyagent.safety.TrustLevel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 在 [ReActAgent]（简单任务）与 [PlanExecuteAgent]（复杂多步任务）之间自动选择。
 *
 * 策略：**启发式优先 + LLM 兜底**，契合「结构化优先、省 LLM 成本、可审计」的取向。
 * 1. 明显简单（短、问候/闲聊、无多步信号）→ ReAct，零额外成本。
 * 2. 明显复杂（多步连接词、编号步骤、批量、长文本）→ PlanExecute。
 * 3. 模棱两可 → 一次轻量 LLM 分类兜底；分类调用本身失败则保守走 ReAct。
 *
 * 集成：上下文压缩 + 用户画像召回 + 工具集动态选择。
 */
class AgentRouter(
    private val llmProvider: LLMProvider,
    private val react: Agent,
    private val planExecute: Agent,
    private val memory: LongTermMemory? = null,
    private val recallTopK: Int = 3,
    /** 元任务（复杂度分类）用的便宜模型；null=用主模型。 */
    private val fastLlm: LLMProvider? = null,
    private val compressor: ContextCompressor? = null,
    private val profileManager: UserProfileManager? = null,
    /** 子任务并行编排器（可选）：复杂请求先尝试拆分子任务并行执行。 */
    private val subAgent: SubAgentOrchestrator? = null,
    /** 同一会话两次画像更新的最小间隔（毫秒）；<=0=每条都更。画像更新已改为后台异步，节流进一步降负载。 */
    private val profileUpdateMinIntervalMs: Long = 60_000
) : Agent {
    override val name = "router"
    private val logger = LoggerFactory.getLogger(AgentRouter::class.java)

    /** 画像更新后台线程池：守护线程，不阻塞回复返回，也不阻塞 JVM 退出。 */
    private val profilePool = Executors.newSingleThreadExecutor { r ->
        Thread(r, "windyagent-profile").apply { isDaemon = true }
    }
    /** 每会话上次画像更新时间戳，用于节流。 */
    private val lastProfileUpdate = ConcurrentHashMap<String, Long>()

    /**
     * 后台异步更新用户画像：不阻塞回复返回。
     * 按 [profileUpdateMinIntervalMs] 节流——距上次更新太近则跳过（省一次 LLM 调用）。
     */
    private fun updateProfileAsync(sessionId: String, userMessage: String, reply: String) {
        val pm = profileManager ?: return
        val now = System.currentTimeMillis()
        if (profileUpdateMinIntervalMs > 0) {
            val last = lastProfileUpdate[sessionId]
            if (last != null && now - last < profileUpdateMinIntervalMs) return
        }
        lastProfileUpdate[sessionId] = now
        val fast = fastLlm ?: llmProvider
        profilePool.execute {
            runCatching { pm.updateFromConversation(sessionId, userMessage, reply, fast) }
                .onFailure { logger.debug("画像后台更新失败（可忽略）：{}", it.message) }
        }
    }

    override fun run(context: AgentContext): AgentResponse {
        // 请求级上下文：信任级别 + 会话 id，供深层工具（安全护栏分权 / remember 作用域）读取
        RequestContext.enter(context.trust, context.sessionId, context.unattended)
        try {
            // 1. 自动召回长期记忆
            memory?.let { m ->
                val incAdmin = context.trust == TrustLevel.TRUSTED
                val hits = runCatching { m.recall(context.sessionId, context.userMessage, recallTopK, incAdmin) }.getOrNull().orEmpty()
                if (hits.isNotEmpty()) context.recalled = hits.joinToString("\n") { "- ${it.content}" }
            }

            // 2. 用户画像召回
            val profileText = profileManager?.get(context.sessionId)?.toText() ?: ""

            // 3. 上下文压缩（历史超阈值时摘要旧消息）
            compressor?.compress(context.history)

            // 4. 拼接画像到用户消息
            if (profileText.isNotBlank()) {
                context.recalled = if (context.recalled.isNotBlank())
                    "$profileText\n\n${context.recalled}" else profileText
            }

            // 5. 尝试子任务并行（仅 TRUSTED 且 subAgent 可用时）
            //    门控：先用廉价启发式过滤——明显简单（问候/闲聊/短消息）的请求直接跳过，
            //    不为它们白跑一次拆分 LLM（否则"你好"也要等一次 plan 往返）。
            val sa = subAgent
            if (sa != null && context.trust == TrustLevel.TRUSTED && heuristic(context.userMessage) != Verdict.SIMPLE) {
                val subTasks = runCatching { sa.plan(context.userMessage) }.getOrNull()
                if (subTasks != null && subTasks.size >= 2) {
                    logger.info("Router → sub-agent 并行 ({} 个子任务) for session {}", subTasks.size, context.sessionId)
                    val result = sa.execute(subTasks)
                    updateProfileAsync(context.sessionId, context.userMessage, result.take(500))
                    return AgentResponse(result, true, listOf("sub-agent"))
                }
            }

            // 7. 选择 Agent 执行
            val chosen = select(context.userMessage)
            logger.info("Router → {} for session {} (trust={})", chosen.name, context.sessionId, context.trust)
            val response = chosen.run(context)

            // 8. 后台异步更新用户画像（不阻塞本次回复返回）
            updateProfileAsync(context.sessionId, context.userMessage, response.message.take(500))

            return response
        } finally {
            RequestContext.clear()
        }
    }

    /** 暴露给单测/调试：仅做选择，不执行。 */
    internal fun select(message: String): Agent = when (heuristic(message)) {
        Verdict.SIMPLE -> react
        Verdict.COMPLEX -> planExecute
        Verdict.UNSURE -> if (classifyByLlm(message)) planExecute else react
    }

    private enum class Verdict { SIMPLE, COMPLEX, UNSURE }

    private fun heuristic(message: String): Verdict {
        val text = message.trim()
        val complexHits = COMPLEX_SIGNALS.count { text.contains(it) }
        val numberedSteps = NUMBERED.containsMatchIn(text)

        return when {
            numberedSteps || complexHits >= 2 || text.length >= LONG_LEN -> Verdict.COMPLEX
            complexHits == 0 && (text.length <= SHORT_LEN || GREETINGS.any { text.contains(it) }) -> Verdict.SIMPLE
            else -> Verdict.UNSURE
        }
    }

    /** 模棱两可时的兜底：一次性、无工具的复杂度分类。失败保守判为简单。 */
    private fun classifyByLlm(message: String): Boolean {
        val answer = runCatching {
            (fastLlm ?: llmProvider).chat(CLASSIFY_SYSTEM, listOf(LLMMessage.User("用户请求：$message"))).textContent
        }.getOrNull()?.trim()?.lowercase().orEmpty()
        return answer.contains("complex") || answer.contains("复杂")
    }

    companion object {
        private const val SHORT_LEN = 12
        private const val LONG_LEN = 60

        private val NUMBERED = Regex("""(^|\n)\s*\d+\s*[.、)]""")

        private val GREETINGS = listOf("你好", "您好", "hi", "hello", "在吗", "谢谢", "哈喽", "在不在")

        private val COMPLEX_SIGNALS = listOf(
            "然后", "接着", "依次", "逐个", "逐一", "批量", "之后", "最后",
            "首先", "其次", "步骤", "并且", "同时", "全部", "所有", "每个", "每位", "先查"
        )

        private val CLASSIFY_SYSTEM = """
            你是任务复杂度分类器。判断用户请求属于哪一类：
            - simple：一步就能完成，或只是问答 / 闲聊 / 单个操作。
            - complex：需要多步骤按顺序执行、批量处理，或先查询再操作。
            只回复一个英文单词：simple 或 complex，不要任何多余内容。
        """.trimIndent()
    }
}
