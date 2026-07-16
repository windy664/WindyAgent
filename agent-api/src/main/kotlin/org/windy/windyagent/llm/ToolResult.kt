package org.windy.windyagent.llm

data class ToolResult(val toolCallId: String, val content: String, val isError: Boolean) {
    companion object {
        fun success(toolCallId: String, content: String) = ToolResult(toolCallId, content, false)
        fun error(toolCallId: String, message: String) = ToolResult(toolCallId, message, true)
    }
}
