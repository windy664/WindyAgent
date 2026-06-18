package org.windy.windyagent.platform.bukkit

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.behavior.PlayerLifecycle
import org.windy.windyagent.llm.ToolResult

/**
 * 玩家生命周期查询工具：让 Agent 能查玩家阶段、获取需关注的玩家。
 */
class PlayerLifecycleTool(private val lifecycle: PlayerLifecycle) : AgentTool {
    override val name = "player_lifecycle"
    override val description = "查询玩家生命周期阶段（新人/活跃/沉睡/流失/回归），或获取需要关注的玩家列表。"
    override val inputSchema = """{"type":"object","properties":{
        "action":{"type":"string","description":"query=查单个玩家,distribution=阶段分布,attention=需关注的玩家","enum":["query","distribution","attention"]},
        "player":{"type":"string","description":"玩家名（action=query时必填）"}
    },"required":["action"]}"""

    private val mapper = ObjectMapper()

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val node = mapper.readTree(inputJson)
        val action = node["action"]?.asText() ?: return ToolResult.error(toolCallId, "缺少 action 参数")
        when (action) {
            "query" -> {
                val name = node["player"]?.asText()?.takeIf { it.isNotBlank() }
                    ?: return ToolResult.error(toolCallId, "查询单个玩家需要 player 参数")
                val info = lifecycle.getPlayerStage(name)
                    ?: return ToolResult.error(toolCallId, "未找到玩家「$name」")
                val result = mapper.createObjectNode()
                    .put("name", info.name).put("stage", info.stage.name)
                    .put("playtimeHours", String.format("%.1f", info.playtimeHours))
                    .put("daysSinceLastSeen", String.format("%.1f", info.daysSinceLastSeen))
                    .put("sessions", info.sessions)
                    .putPOJO("suggestedActions", info.suggestedActions)
                ToolResult.success(toolCallId, result.toString())
            }
            "distribution" -> {
                val dist = lifecycle.getStageDistribution()
                val result = mapper.createObjectNode()
                for ((stage, players) in dist) {
                    result.put(stage.name, players.size)
                }
                ToolResult.success(toolCallId, result.toString())
            }
            "attention" -> {
                val players = lifecycle.getAttentionNeeded()
                val arr = mapper.createArrayNode()
                for (p in players) {
                    arr.addObject().put("name", p.name).put("stage", p.stage.name)
                        .put("daysSinceLastSeen", String.format("%.1f", p.daysSinceLastSeen))
                        .putPOJO("actions", p.suggestedActions)
                }
                ToolResult.success(toolCallId, arr.toString())
            }
            else -> ToolResult.error(toolCallId, "未知 action: $action")
        }
    }.getOrElse { ToolResult.error(toolCallId, "生命周期查询失败：${it.message}") }
}
