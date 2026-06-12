package org.windy.windyagent.platform.velocity.tools

import com.velocitypowered.api.proxy.ProxyServer
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.llm.ToolResult

class GetOnlinePlayersTool(private val server: ProxyServer) : AgentTool {
    override val name = "get_online_players"
    override val description = "获取所有后端子服当前在线的玩家列表。"
    override val inputSchema = """{"type":"object","properties":{}}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult {
        val players = server.allPlayers
        if (players.isEmpty()) return ToolResult.success(toolCallId, "当前没有玩家在线。")
        val list = players.joinToString("\n") { p ->
            val serverName = p.currentServer.map { it.serverInfo.name }.orElse("未知")
            "- ${p.username}（$serverName）"
        }
        return ToolResult.success(toolCallId, "在线玩家（${players.size} 人）：\n$list")
    }
}
