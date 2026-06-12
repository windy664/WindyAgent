package org.windy.windyagent.agent

import org.windy.windyagent.llm.ToolResult

interface AgentTool {
    val name: String
    val description: String
    val inputSchema: String
    fun execute(toolCallId: String, inputJson: String): ToolResult
}
