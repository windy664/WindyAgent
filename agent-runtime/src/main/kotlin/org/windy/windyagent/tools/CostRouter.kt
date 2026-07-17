package org.windy.windyagent.tools

import org.slf4j.LoggerFactory
import org.windy.windyagent.llm.LLMProvider

/**
 * 成本感知路由器：按任务复杂度自动选便宜/贵模型。
 *
 * 与 [AgentRouter] 配合：AgentRouter 决定 ReAct vs PlanExecute，
 * CostRouter 决定用哪个 LLM Provider。
 *
 * 策略：
 * - 闲聊/问候/简单查询 → cheap（fastLlm）
 * - 多步操作/复杂推理/代码生成 → expensive（主模型）
 * - 不确定 → cheap（保守省钱，大部分请求其实简单）
 */
class CostRouter(
    private val expensive: LLMProvider,
    private val cheap: LLMProvider
) : LLMProvider, StreamingProvider {

    override val name: String get() = "cost-router(${cheap.name}/${expensive.name})"

    private val log = LoggerFactory.getLogger(CostRouter::class.java)

    /** 选择模型后调用。 */
    override fun chat(systemPrompt: String, messages: List<org.windy.windyagent.llm.LLMMessage>, tools: List<AgentTool>): org.windy.windyagent.llm.LLMResponse {
        val provider = selectProvider(messages, tools)
        return provider.chat(systemPrompt, messages, tools)
    }

    /** 流式同样按复杂度选 provider，并透传其流式能力；选中的不支持流式则回退一次性 emit。
     *  否则 CostRouter 会“吃掉” StreamingProvider 接口，使前端拿不到真流式。 */
    override fun chatStream(systemPrompt: String, messages: List<org.windy.windyagent.llm.LLMMessage>, tools: List<AgentTool>): org.windy.windyagent.llm.ChatStream {
        val provider = selectProvider(messages, tools)
        (provider as? StreamingProvider)?.let { return it.chatStream(systemPrompt, messages, tools) }
        val stream = org.windy.windyagent.llm.ChatStream()
        Thread({
            runCatching { provider.chat(systemPrompt, messages, tools).textContent ?: "" }
                .onSuccess { stream.emit(org.windy.windyagent.llm.StreamChunk.Text(it)); stream.emit(org.windy.windyagent.llm.StreamChunk.Done) }
                .onFailure { stream.emit(org.windy.windyagent.llm.StreamChunk.Error(it.message ?: "chat failed")) }
        }, "costrouter-stream-fallback").apply { isDaemon = true }.start()
        return stream
    }

    private fun selectProvider(messages: List<org.windy.windyagent.llm.LLMMessage>, tools: List<AgentTool>): LLMProvider {
        // 取最后一条用户消息
        val lastUser = messages.lastOrNull { it is org.windy.windyagent.llm.LLMMessage.User }
            ?.let { (it as org.windy.windyagent.llm.LLMMessage.User).content } ?: ""

        val score = complexityScore(lastUser, tools.size)
        val chosen = if (score >= COMPLEX_THRESHOLD) expensive else cheap
        if (chosen === cheap) {
            log.debug("成本路由：cheap（score={}/{}）", score, COMPLEX_THRESHOLD)
        } else {
            log.info("成本路由：expensive（score={}）", score)
        }
        return chosen
    }

    private fun complexityScore(message: String, toolCount: Int): Int {
        var score = 0
        val text = message.trim()

        // 长度
        if (text.length > 100) score += 2
        if (text.length > 300) score += 3

        // 多步信号
        for (signal in COMPLEX_SIGNALS) if (text.contains(signal)) score++

        // 编号步骤
        if (Regex("""(^|\n)\s*\d+\s*[.、)]""").containsMatchIn(text)) score += 3

        // 工具数量多 = 需要更强的工具选择能力
        if (toolCount > 10) score += 2

        // 代码/脚本相关
        if (text.contains("脚本") || text.contains("script") || text.contains("kether")) score += 2

        return score
    }

    companion object {
        private const val COMPLEX_THRESHOLD = 4
        private val COMPLEX_SIGNALS = listOf(
            "然后", "接着", "依次", "逐个", "批量", "之后", "最后", "首先", "其次", "步骤",
            "并且", "同时", "全部", "所有", "每个", "每位", "先查", "再", "最后"
        )
    }
}
