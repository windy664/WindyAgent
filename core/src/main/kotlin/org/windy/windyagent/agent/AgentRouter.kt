package org.windy.windyagent.agent

import org.slf4j.LoggerFactory
import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.llm.LLMProvider
import org.windy.windyagent.safety.RequestContext

/**
 * 在 [ReActAgent]（简单任务）与 [PlanExecuteAgent]（复杂多步任务）之间自动选择。
 *
 * 策略：**启发式优先 + LLM 兜底**，契合「结构化优先、省 LLM 成本、可审计」的取向。
 * 1. 明显简单（短、问候/闲聊、无多步信号）→ ReAct，零额外成本。
 * 2. 明显复杂（多步连接词、编号步骤、批量、长文本）→ PlanExecute。
 * 3. 模棱两可 → 一次轻量 LLM 分类兜底；分类调用本身失败则保守走 ReAct。
 */
class AgentRouter(
    private val llmProvider: LLMProvider,
    private val react: Agent,
    private val planExecute: Agent
) : Agent {
    override val name = "router"
    private val logger = LoggerFactory.getLogger(AgentRouter::class.java)

    override fun run(context: AgentContext): AgentResponse {
        // 把触发信任级别放进请求级上下文，供深层命令工具读取（安全护栏分权）
        RequestContext.enter(context.trust)
        try {
            val chosen = select(context.userMessage)
            logger.info("Router → {} for session {} (trust={})", chosen.name, context.sessionId, context.trust)
            return chosen.run(context)
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
            llmProvider.chat(CLASSIFY_SYSTEM, listOf(LLMMessage.User("用户请求：$message"))).textContent
        }.getOrNull()?.trim()?.lowercase().orEmpty()
        return answer.contains("complex") || answer.contains("复杂")
    }

    companion object {
        private const val SHORT_LEN = 12
        private const val LONG_LEN = 60

        private val NUMBERED = Regex("""(^|\n)\s*\d+\s*[.、)]""")

        private val GREETINGS = listOf("你好", "您好", "hi", "hello", "在吗", "谢谢", "哈喽", "在不在")

        /** 中文多步/批量信号。刻意避开「再」「了一遍」这类高歧义单字，减少误判。 */
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
