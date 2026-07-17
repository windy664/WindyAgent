package org.windy.windyagent.platform.velocity.tools

import com.velocitypowered.api.proxy.ProxyServer
import org.windy.windyagent.tools.AgentTool
import org.windy.windyagent.llm.ToolResult

class GetServerInfoTool(private val server: ProxyServer) : AgentTool {
    override val name = "get_server_info"
    override val description = "获取代理端信息，包括已注册的后端子服和在线人数。"
    override val inputSchema = """{"type":"object","properties":{}}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult {
        val servers = server.allServers.joinToString("\n") { s ->
            "  - ${s.serverInfo.name}（${s.serverInfo.address}）"
        }
        val info = """
            代理端版本：${server.version.version}
            在线玩家数：${server.playerCount}
            已注册子服：
            $servers
        """.trimIndent()
        return ToolResult.success(toolCallId, info)
    }
}
