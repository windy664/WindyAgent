package org.windy.windyagent.llm

data class LLMResponse(
    val textContent: String?,
    val toolCalls: List<ToolCall>,
    val stopReason: StopReason,
    /** 输入 token 数（provider 返回时填；-1=未知）。 */
    val inputTokens: Int = -1,
    /** 输出 token 数（provider 返回时填；-1=未知）。 */
    val outputTokens: Int = -1
) {
    enum class StopReason { END_TURN, TOOL_USE, MAX_TOKENS, ERROR }

    val hasToolCalls get() = toolCalls.isNotEmpty()
    val isComplete get() = stopReason == StopReason.END_TURN
}
