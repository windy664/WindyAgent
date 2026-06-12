package org.windy.windyagent.llm

sealed interface LLMMessage {
    data class User(val content: String) : LLMMessage
    data class Assistant(val content: String? = null, val toolCalls: List<ToolCall> = emptyList()) : LLMMessage
    data class ToolResults(val results: List<ToolResult>) : LLMMessage
}
