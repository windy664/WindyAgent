package org.windy.windyagent.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.llm.ToolResult
import java.util.concurrent.TimeUnit

/**
 * 查询指定后端子服上某玩家的 Vault 经济余额（经 [MessageBus] 下发 get_balance 动作）。
 *
 * 与 [RemoteCommandTool] 同套路：远端能力包装成本地 AgentTool，传输无关。
 * 子服侧需安装 Vault + 一个经济插件；未安装时回包会说明。
 */
class RemoteBalanceTool(
    private val bus: MessageBus,
    private val timeoutMs: Long
) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = "get_player_balance_on_server"
    override val description =
        "查询指定后端子服上某玩家的经济余额（Vault）。需要知道玩家在哪个子服时使用。"
    override val inputSchema = """{"type":"object","properties":{"server":{"type":"string","description":"目标子服名（须与已注册子服一致）"},"player":{"type":"string","description":"玩家名"}},"required":["server","player"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val node = mapper.readTree(inputJson)
        val server = node["server"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少 server 参数")
        val player = node["player"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少 player 参数")

        val argsJson = mapper.createObjectNode().put("player", player).toString()
        val reply = bus.dispatch(server, "get_balance", argsJson, timeoutMs)
            .get(timeoutMs + 1000, TimeUnit.MILLISECONDS)

        if (reply.success) ToolResult.success(toolCallId, reply.content)
        else ToolResult.error(toolCallId, reply.content)
    }.getOrElse { ToolResult.error(toolCallId, "查询子服余额失败：${it.message}") }
}
