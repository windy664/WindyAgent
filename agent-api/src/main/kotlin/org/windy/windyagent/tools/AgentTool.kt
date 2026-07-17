package org.windy.windyagent.tools

import org.windy.windyagent.llm.ToolResult

interface AgentTool {
    val name: String
    val description: String
    val inputSchema: String
    fun execute(toolCallId: String, inputJson: String): ToolResult
}
