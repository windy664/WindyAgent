package org.windy.windyagent.llm

data class LLMResponse(
    val textContent: String?,
    val toolCalls: List<ToolCall>,
    val stopReason: StopReason,
    /** Input token count from the provider, or -1 when unknown. */
    val inputTokens: Int = -1,
    /** Output token count from the provider, or -1 when unknown. */
    val outputTokens: Int = -1
) {
    enum class StopReason { END_TURN, TOOL_USE, MAX_TOKENS, ERROR }

    val hasToolCalls get() = toolCalls.isNotEmpty()
    val isComplete get() = stopReason == StopReason.END_TURN
}
