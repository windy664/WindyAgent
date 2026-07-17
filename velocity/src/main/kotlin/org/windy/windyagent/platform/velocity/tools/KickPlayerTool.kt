package org.windy.windyagent.platform.velocity.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import org.windy.windyagent.tools.AgentTool
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.safety.ActionGate
import org.windy.windyagent.safety.AuditLog

/**
 * 将指定玩家从代理端踢下线。破坏性动作：不可信来源（玩家聊天）禁止，挡注入/滥用。
 */
class KickPlayerTool(private val server: ProxyServer, private val audit: AuditLog) : AgentTool {
    private val mapper = ObjectMapper()

    override val name = "kick_player"
    override val description = "将指定玩家从服务器踢下线。需要提供玩家名，建议附带踢出理由。"
    override val inputSchema = """{"type":"object","properties":{"player":{"type":"string","description":"要踢出的玩家名"},"reason":{"type":"string","description":"踢出理由，会展示给被踢玩家"}},"required":["player"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val node = mapper.readTree(inputJson)
        val playerName = node["player"].asText()
        val reason = node["reason"]?.asText()?.takeIf { it.isNotBlank() } ?: "你已被管理员踢出服务器"

        ActionGate.guardTrusted("kick", playerName, audit)?.let { return ToolResult.error(toolCallId, it) }

        val player = server.getPlayer(playerName).orElse(null)
            ?: return ToolResult.error(toolCallId, "玩家「$playerName」当前不在线，无法踢出")

        player.disconnect(Component.text(reason))
        ToolResult.success(toolCallId, "已将玩家「$playerName」踢出，理由：$reason")
    }.getOrElse { ToolResult.error(toolCallId, "踢出失败：${it.message}") }
}
