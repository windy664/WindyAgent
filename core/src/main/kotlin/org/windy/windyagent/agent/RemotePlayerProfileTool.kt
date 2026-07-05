package org.windy.windyagent.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.llm.ToolResult
import java.util.concurrent.TimeUnit

/**
 * 查询指定后端子服上某玩家的「聚合画像」（经 [MessageBus] 下发 player_profile 动作）。
 *
 * 画像 = 子服上各插件（CMI / 经济 / 领地 / 昕途绑定…）经 ProfileDataRegistry 贡献的玩家数据合并。
 * 与 [RemoteBalanceTool] 同套路：远端能力包装成本地 AgentTool，传输无关。
 * 管理员问「windy 这个玩家什么情况 / 他的画像」时用；数据随子服装了哪些插件而定。
 */
class RemotePlayerProfileTool(
    private val bus: MessageBus,
    private val timeoutMs: Long
) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = "get_player_profile_on_server"
    override val description =
        "查询指定后端子服上某玩家的聚合画像（各插件贡献的数据合并：余额/领地/权限组/绑定QQ/最近登录等，随子服插件而定）。管理员想了解某玩家整体情况时使用。"
    override val inputSchema = """{"type":"object","properties":{"server":{"type":"string","description":"目标子服名（须与已注册子服一致）"},"player":{"type":"string","description":"玩家名"}},"required":["server","player"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val node = mapper.readTree(inputJson)
        val server = node["server"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少 server 参数")
        val player = node["player"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少 player 参数")

        val argsJson = mapper.createObjectNode().put("player", player).toString()
        val reply = bus.dispatch(server, "player_profile", argsJson, timeoutMs)
            .get(timeoutMs + 1000, TimeUnit.MILLISECONDS)

        if (reply.success) {
            // 子服返回 Map JSON；空画像给个友好提示
            val content = reply.content.takeIf { it.isNotBlank() && it != "{}" }
                ?: return ToolResult.success(toolCallId, "玩家「$player」在子服「$server」暂无画像数据（可能未登录过或子服未装贡献插件）。")
            ToolResult.success(toolCallId, "玩家「$player」@「$server」画像：\n$content")
        } else ToolResult.error(toolCallId, reply.content)
    }.getOrElse { ToolResult.error(toolCallId, "查询子服画像失败：${it.message}") }
}
