package org.windy.windyagent.platform.bukkit

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.tools.AgentTool
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.platform.bukkit.item.ItemService

/** 本机物品估值工具（standalone/hub 嵌入式 Agent 用）。底层共用 [ItemService]。 */

private val itemMapper = ObjectMapper()

class BukkitRefreshItemsTool(private val items: ItemService) : AgentTool {
    override val name = "refresh_item_index"
    override val description = "重新解析本服模组、重建物品/配方库（装/卸模组后用）。"
    override val inputSchema = """{"type":"object","properties":{}}"""
    override fun execute(toolCallId: String, inputJson: String): ToolResult =
        runCatching { ToolResult.success(toolCallId, items.build()) }.getOrElse { ToolResult.error(toolCallId, "刷新物品库失败：${it.message}") }
}

class BukkitAppraiseTool(private val items: ItemService) : AgentTool {
    override val name = "appraise_item"
    override val description = "估算某个物品的价值（沿合成树递归算成本）。可传中文名/英文名/namespaced id。"
    override val inputSchema = """{"type":"object","properties":{"item":{"type":"string","description":"物品名或 id，如「终极精华」或 mysticalagriculture:supremium_essence"}},"required":["item"]}"""
    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val q = itemMapper.readTree(inputJson)["item"]?.asText()?.takeIf { it.isNotBlank() } ?: return ToolResult.error(toolCallId, "缺少 item 参数")
        ToolResult.success(toolCallId, items.get(q))
    }.getOrElse { ToolResult.error(toolCallId, "估值失败：${it.message}") }
}

class BukkitProposePackTool(private val items: ItemService) : AgentTool {
    override val name = "propose_pack"
    override val description = "按目标价位组一个礼包提案（一篮子物品 + 估值 + 建议售价）。提案需管理员确认才落商店。"
    override val inputSchema = """{"type":"object","properties":{"target_value":{"type":"number","description":"礼包目标价值（本服货币）"}},"required":["target_value"]}"""
    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val target = itemMapper.readTree(inputJson)["target_value"]?.asDouble()?.takeIf { it > 0 } ?: return ToolResult.error(toolCallId, "缺少 target_value 参数")
        ToolResult.success(toolCallId, items.propose(target))
    }.getOrElse { ToolResult.error(toolCallId, "组礼包失败：${it.message}") }
}
