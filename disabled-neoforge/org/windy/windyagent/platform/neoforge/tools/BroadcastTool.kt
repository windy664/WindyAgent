package org.windy.windyagent.platform.neoforge.tools

import com.fasterxml.jackson.databind.ObjectMapper
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.llm.ToolResult

class BroadcastTool(private val server: MinecraftServer) : AgentTool {
    private val mapper = ObjectMapper()

    override val name = "broadcast"
    override val description = "Broadcast a message to all online players."
    override val inputSchema = """{"type":"object","properties":{"message":{"type":"string","description":"The message to broadcast"}},"required":["message"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val message = mapper.readTree(inputJson)["message"].asText()
        server.playerList.broadcastSystemMessage(Component.literal("[WindyAgent] $message"), false)
        ToolResult.success(toolCallId, "Broadcast sent: $message")
    }.getOrElse { ToolResult.error(toolCallId, "Failed to broadcast: ${it.message}") }
}
