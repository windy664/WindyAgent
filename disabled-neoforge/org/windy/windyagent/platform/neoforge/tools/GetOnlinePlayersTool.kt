package org.windy.windyagent.platform.neoforge.tools

import net.minecraft.server.MinecraftServer
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.llm.ToolResult

class GetOnlinePlayersTool(private val server: MinecraftServer) : AgentTool {
    override val name = "get_online_players"
    override val description = "Get the list of currently online players."
    override val inputSchema = """{"type":"object","properties":{}}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult {
        val players = server.playerList.players
        if (players.isEmpty()) return ToolResult.success(toolCallId, "No players online.")
        val list = players.joinToString("\n") { "- ${it.name.string}" }
        return ToolResult.success(toolCallId, "Online players (${players.size}):\n$list")
    }
}
