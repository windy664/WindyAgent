package org.windy.windyagent.llm

data class LLMResponse(
    val textContent: String?,
    val toolCalls: List<ToolCall>,
    val stopReason: StopReason
) {
    enum class StopReason { END_TURN, TOOL_USE, MAX_TOKENS, ERROR }

    val hasToolCalls get() = toolCalls.isNotEmpty()
    val isComplete get() = stopReason == StopReason.END_TURN
}
