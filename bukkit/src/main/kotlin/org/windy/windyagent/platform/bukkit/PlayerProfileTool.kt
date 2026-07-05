package org.windy.windyagent.platform.bukkit

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.profile.ProfileDataRegistry

/**
 * 查询本服某玩家的「聚合画像」——各插件（CMI/经济/领地/昕途绑定…）经 [ProfileDataRegistry]
 * 贡献的玩家数据合并。管理员问「windy 什么情况 / 他的画像」时用。
 *
 * 本地版（玩家在本服，无需 server 参数）。跨服场景用 velocity 的 RemotePlayerProfileTool。
 */
class PlayerProfileTool(
    private val profiles: ProfileDataRegistry
) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = "get_player_profile"
    override val description =
        "查询本服某玩家的聚合画像（各插件贡献合并：余额/领地/权限组/绑定QQ/最近登录等，随本服插件而定）。想了解某玩家整体情况时使用。"
    override val inputSchema = """{"type":"object","properties":{"player":{"type":"string","description":"玩家名"}},"required":["player"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val player = mapper.readTree(inputJson)?.get("player")?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少 player 参数")
        val data = profiles.snapshot(player)
        if (data.isEmpty()) return ToolResult.success(toolCallId, "玩家「$player」暂无画像数据（可能未登录过或未装贡献插件）。")
        val body = data.entries.joinToString("\n") { "${it.key}：${it.value}" }
        ToolResult.success(toolCallId, "玩家「$player」画像：\n$body")
    }.getOrElse { ToolResult.error(toolCallId, "查询画像失败：${it.message}") }
}
