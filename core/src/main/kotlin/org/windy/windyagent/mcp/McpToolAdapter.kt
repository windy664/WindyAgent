package org.windy.windyagent.mcp

import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.llm.ToolResult

/**
 * 把一个远端 MCP 工具包装成本地 [AgentTool]，喂给 ReAct 核心循环——**核心零改动**。
 *
 * 这正是 DEVLOG 一直预留的口子：Agent 对 MCP 工具、跨服 RemoteTool、本地工具一视同仁。
 * 名称沿用 MCP server 给的工具名；若与本地工具撞名，靠 server 配置侧规避（MVP 不自动加前缀）。
 */
class McpToolAdapter(
    private val client: McpClient,
    private val def: McpToolDef
) : AgentTool {

    override val name = def.name
    override val description = def.description
    override val inputSchema = def.inputSchema

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        ToolResult.success(toolCallId, client.callTool(def.name, inputJson))
    }.getOrElse { ToolResult.error(toolCallId, "MCP 工具「${def.name}」调用失败：${it.message}") }
}
