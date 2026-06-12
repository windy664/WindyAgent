package org.windy.windyagent.platform.velocity.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.llm.ToolResult

class BroadcastTool(private val server: ProxyServer) : AgentTool {
    private val mapper = ObjectMapper()

    override val name = "broadcast"
    override val description = "向代理端所有在线玩家广播一条消息。"
    override val inputSchema = """{"type":"object","properties":{"message":{"type":"string","description":"要广播的消息内容"}},"required":["message"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val message = mapper.readTree(inputJson)["message"].asText()
        server.sendMessage(Component.text("[WindyAgent] $message"))
        ToolResult.success(toolCallId, "广播已发送：$message")
    }.getOrElse { ToolResult.error(toolCallId, "发送失败：${it.message}") }
}
