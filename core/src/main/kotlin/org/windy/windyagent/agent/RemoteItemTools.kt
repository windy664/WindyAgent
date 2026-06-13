package org.windy.windyagent.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.llm.ToolResult
import java.util.concurrent.TimeUnit

/**
 * 中心侧物品估值远端工具：把 appraise/propose/refresh 派发到子服执行（子服有 mods/ + SQLite 物品库）。
 * 与 RemoteBalanceTool 同套路——DB 留子服，只有查询/结果过总线。
 */
private val itemMapper = ObjectMapper()

private fun MessageBus.dispatchText(server: String, action: String, args: String, timeoutMs: Long): String {
    val reply = dispatch(server, action, args, timeoutMs).get(timeoutMs + 1000, TimeUnit.MILLISECONDS)
    return if (reply.success) reply.content else "子服未成功：${reply.content}"
}

class RemoteAppraiseTool(private val bus: MessageBus, private val timeoutMs: Long) : AgentTool {
    override val name = "appraise_item_on_server"
    override val description = "估算某子服上某个物品的价值（含模组物品，沿合成树算成本）。物品可传中文名/英文名/id。"
    override val inputSchema = """{"type":"object","properties":{"server":{"type":"string","description":"目标子服名"},"item":{"type":"string","description":"物品名或 namespaced id"}},"required":["server","item"]}"""
    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val n = itemMapper.readTree(inputJson)
        val server = n["server"]?.asText()?.takeIf { it.isNotBlank() } ?: return ToolResult.error(toolCallId, "缺少 server 参数")
        val item = n["item"]?.asText()?.takeIf { it.isNotBlank() } ?: return ToolResult.error(toolCallId, "缺少 item 参数")
        val args = itemMapper.createObjectNode().put("item", item).toString()
        ToolResult.success(toolCallId, bus.dispatchText(server, "appraise_item", args, timeoutMs))
    }.getOrElse { ToolResult.error(toolCallId, "远端估值失败：${it.message}") }
}

class RemoteProposePackTool(private val bus: MessageBus, private val timeoutMs: Long) : AgentTool {
    override val name = "propose_pack_on_server"
    override val description = "在某子服按目标价位组一个礼包提案（基于物品估值）。提案需管理员确认才落商店。"
    override val inputSchema = """{"type":"object","properties":{"server":{"type":"string","description":"目标子服名"},"target_value":{"type":"number","description":"礼包目标价值"}},"required":["server","target_value"]}"""
    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val n = itemMapper.readTree(inputJson)
        val server = n["server"]?.asText()?.takeIf { it.isNotBlank() } ?: return ToolResult.error(toolCallId, "缺少 server 参数")
        val target = n["target_value"]?.asDouble()?.takeIf { it > 0 } ?: return ToolResult.error(toolCallId, "缺少 target_value 参数")
        val args = itemMapper.createObjectNode().put("target_value", target).toString()
        ToolResult.success(toolCallId, bus.dispatchText(server, "propose_pack", args, timeoutMs))
    }.getOrElse { ToolResult.error(toolCallId, "远端组礼包失败：${it.message}") }
}

class RemoteRefreshItemsTool(private val bus: MessageBus, private val timeoutMs: Long) : AgentTool {
    override val name = "refresh_item_index_on_server"
    override val description = "让某子服重新解析模组、重建物品/配方库（装/卸模组后用）。"
    override val inputSchema = """{"type":"object","properties":{"server":{"type":"string","description":"目标子服名"}},"required":["server"]}"""
    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val server = itemMapper.readTree(inputJson)["server"]?.asText()?.takeIf { it.isNotBlank() } ?: return ToolResult.error(toolCallId, "缺少 server 参数")
        ToolResult.success(toolCallId, bus.dispatchText(server, "refresh_items", "{}", timeoutMs))
    }.getOrElse { ToolResult.error(toolCallId, "远端刷新失败：${it.message}") }
}
