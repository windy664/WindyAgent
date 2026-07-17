package org.windy.windyagent.platform.bukkit

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.tools.AgentTool
import org.windy.windyagent.llm.ToolResult

/**
 * 嵌入式 Agent（standalone / hub 模式）在本子服直接调用的本地工具。
 *
 * 与能力提供方的 [BukkitCapabilityHandler] 共用同一套 [BukkitActions]——
 * 区别仅在于：那边由总线请求驱动，这边由 Agent 的 ReAct 循环驱动。
 */

private val mapper = ObjectMapper()

private fun String.field(json: String): String? =
    mapper.readTree(json)[this]?.asText()?.takeIf { it.isNotBlank() }

class BukkitBroadcastTool(private val actions: BukkitActions) : AgentTool {
    override val name = "broadcast"
    override val description = "向本服所有在线玩家广播一条消息。"
    override val inputSchema = """{"type":"object","properties":{"message":{"type":"string","description":"要广播的消息内容"}},"required":["message"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val msg = "message".field(inputJson) ?: return ToolResult.error(toolCallId, "缺少 message 参数")
        ToolResult.success(toolCallId, actions.broadcast(msg))
    }.getOrElse { ToolResult.error(toolCallId, "广播失败：${it.message}") }
}

class BukkitOnlinePlayersTool(private val actions: BukkitActions) : AgentTool {
    override val name = "get_online_players"
    override val description = "查询本服当前在线玩家列表与人数。"
    override val inputSchema = """{"type":"object","properties":{}}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        ToolResult.success(toolCallId, actions.onlinePlayers())
    }.getOrElse { ToolResult.error(toolCallId, "查询在线玩家失败：${it.message}") }
}

class BukkitKickTool(private val actions: BukkitActions) : AgentTool {
    override val name = "kick_player"
    override val description = "将指定玩家踢出本服。仅在有明确理由时使用。"
    override val inputSchema = """{"type":"object","properties":{"player":{"type":"string","description":"要踢出的玩家名"},"reason":{"type":"string","description":"踢出理由（会展示给玩家）"}},"required":["player"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val name = "player".field(inputJson) ?: return ToolResult.error(toolCallId, "缺少 player 参数")
        val reason = "reason".field(inputJson) ?: "你已被管理员踢出"
        val (ok, msg) = actions.kick(name, reason)
        if (ok) ToolResult.success(toolCallId, msg) else ToolResult.error(toolCallId, msg)
    }.getOrElse { ToolResult.error(toolCallId, "踢出玩家失败：${it.message}") }
}

class BukkitBalanceTool(private val actions: BukkitActions) : AgentTool {
    override val name = "get_player_balance"
    override val description = "查询本服某玩家的经济余额（需安装 Vault + 经济插件）。"
    override val inputSchema = """{"type":"object","properties":{"player":{"type":"string","description":"玩家名"}},"required":["player"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val name = "player".field(inputJson) ?: return ToolResult.error(toolCallId, "缺少 player 参数")
        val (ok, msg) = actions.balance(name)
        if (ok) ToolResult.success(toolCallId, msg) else ToolResult.error(toolCallId, msg)
    }.getOrElse { ToolResult.error(toolCallId, "查询余额失败：${it.message}") }
}

class BukkitDescribeCommandTool(private val actions: BukkitActions) : AgentTool {
    override val name = "describe_command"
    override val description =
        "只读查询本服某条命令的用法（描述/usage/别名/子命令补全）。对不熟悉的插件命令、或 search_capabilities 只给出命令名没给用法时，" +
        "执行前先用它探查：会综合命令 usage、内置 /help 帮助主题、tab 补全候选。纯读、绝不执行命令本体。" +
        "若返回⚠️表示查不到用法，此时不要猜参数硬跑，改为向用户确认。"
    override val inputSchema = """{"type":"object","properties":{"command":{"type":"string","description":"要查询的命令名，不含前导斜杠（如 home、cmi、money）"}},"required":["command"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val cmd = "command".field(inputJson) ?: return ToolResult.error(toolCallId, "缺少 command 参数")
        ToolResult.success(toolCallId, actions.describeCommand(cmd))
    }.getOrElse { ToolResult.error(toolCallId, "查询命令用法失败：${it.message}") }
}

class BukkitRunCommandTool(private val actions: BukkitActions) : AgentTool {
    override val name = "run_command"
    override val description = "在本服控制台执行一条指令（如 give、tp 等），不含前导斜杠。对不确定用法的插件命令，先用 describe_command 探查再执行。"
    override val inputSchema = """{"type":"object","properties":{"command":{"type":"string","description":"要执行的控制台指令，不含前导斜杠"}},"required":["command"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val cmd = "command".field(inputJson) ?: return ToolResult.error(toolCallId, "缺少 command 参数")
        val (ok, msg) = actions.runCommand(cmd)
        if (ok) ToolResult.success(toolCallId, msg) else ToolResult.error(toolCallId, msg)
    }.getOrElse { ToolResult.error(toolCallId, "执行指令失败：${it.message}") }
}
