package org.windy.windyagent.platform.neoforge.tools

import net.minecraft.server.MinecraftServer
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.llm.ToolResult

class GetServerInfoTool(private val server: MinecraftServer) : AgentTool {
    override val name = "get_server_info"
    override val description = "Get server status including player count and world info."
    override val inputSchema = """{"type":"object","properties":{}}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult {
        val info = """
            Server: ${server.serverModName}
            Online players: ${server.playerList.playerCount} / ${server.playerList.maxPlayers}
            Worlds: ${server.allLevels.map { it.dimension().location() }}
        """.trimIndent()
        return ToolResult.success(toolCallId, info)
    }
}
