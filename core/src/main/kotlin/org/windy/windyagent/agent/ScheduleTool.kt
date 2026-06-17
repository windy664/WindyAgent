package org.windy.windyagent.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.ops.ScheduledTask
import org.windy.windyagent.ops.TaskScheduler
import org.windy.windyagent.safety.AuditLog

/**
 * 让 Agent **对话式管理定时任务**——服主说"每天晚上8点发钻石"，AI 创建技能 + 定时任务。
 *
 * 操作：
 *  - `create`：创建定时任务（可关联已有技能，也可独立执行广播/命令）
 *  - `list`：列出所有定时任务
 *  - `delete`：删除指定任务
 *  - `toggle`：启用/禁用任务
 *
 * 与技能的分工：
 *  - 技能 = 做什么（能力）→ create_skill
 *  - 定时任务 = 什么时候做（触发器）→ 本工具
 *
 * 典型流程：
 *  服主: "每天晚上8点给在线玩家发5个钻石"
 *  AI:   ① create_skill("daily_gift", ...) → 创建技能
 *        ② create_schedule(name="每日钻石", action="skill", skill="daily_gift", time="20:00") → 创建定时任务
 */
class ScheduleTool(
    private val scheduler: TaskScheduler,
    private val audit: AuditLog
) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = "manage_schedule"
    override val description = "管理定时任务。操作：create（创建）/ list（列出）/ delete（删除）/ toggle（启用/禁用）。" +
        "创建时可关联技能（action=skill，到点自动调用指定技能）或直接执行广播/命令。" +
        "典型用法：用户说「每天晚上8点发钻石」→ 先用 create_skill 创建技能，再用本工具创建定时任务关联该技能。"
    override val inputSchema = """{"type":"object","properties":{"operation":{"type":"string","description":"操作类型","enum":["create","list","delete","toggle"]},"name":{"type":"string","description":"任务名称（create 时必填）"},"action":{"type":"string","description":"动作类型：skill（调技能）/ broadcast（广播）/ command（执行命令）/ agent（AI实时决策）","enum":["skill","broadcast","command","agent"]},"target":{"type":"string","description":"目标子服名（skill/command/broadcast 用；* 或留空=全部在线子服）"},"skill":{"type":"string","description":"技能名（action=skill 时必填，须已存在）"},"payload":{"type":"string","description":"动作内容：action=broadcast 时为广播文案，action=command 时为命令，action=agent 时为 AI 指令"},"schedule":{"type":"string","description":"调度方式，如 'daily 20:00'（每天20点）、'daily 08:00 1,2,3,4,5'（工作日8点）、'interval 30'（每30分钟）"},"id":{"type":"string","description":"任务 ID（delete/toggle 时必填）"}},"required":["operation"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val node = mapper.readTree(inputJson)
        val op = node["operation"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少 operation（create/list/delete/toggle）")

        when (op) {
            "list" -> doList(toolCallId)
            "create" -> doCreate(toolCallId, node)
            "delete" -> doDelete(toolCallId, node)
            "toggle" -> doToggle(toolCallId, node)
            else -> ToolResult.error(toolCallId, "未知操作：$op（支持 create/list/delete/toggle）")
        }
    }.getOrElse {
        audit.record("center", "manage_schedule", "?", "ERROR", it.message ?: "")
        ToolResult.error(toolCallId, "定时任务操作失败：${it.message}")
    }

    private fun doList(toolCallId: String): ToolResult {
        val tasks = scheduler.list()
        if (tasks.isEmpty()) return ToolResult.success(toolCallId, "当前没有任何定时任务。")

        val sb = StringBuilder("共 ${tasks.size} 个定时任务：\n\n")
        for (t in tasks) {
            val status = if (t.enabled) "✅启用" else "⏸️禁用"
            val schedule = when (t.type) {
                "daily" -> "每天 ${t.time}" + if (t.days.isNotEmpty()) " (周${t.days.joinToString(",")})" else ""
                else -> "每 ${t.intervalMin} 分钟"
            }
            val actionDesc = when (t.action) {
                "skill" -> "调技能「${t.payload}」"
                "broadcast" -> "广播「${t.payload.take(30)}」"
                "command" -> "执行「${t.payload.take(30)}」"
                "agent" -> "AI决策「${t.payload.take(30)}」"
                "script" -> "脚本(${t.script.size}步)"
                else -> t.action
            }
            val target = if (t.target.isBlank() || t.target == "*") "全部子服" else t.target
            val lastRun = if (t.lastRun > 0) java.time.Instant.ofEpochMilli(t.lastRun).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString() else "从未"
            sb.append("• **${t.name}** [${t.id}] $status\n")
            sb.append("  $schedule → $actionDesc → $target\n")
            sb.append("  上次执行：$lastRun\n\n")
        }
        return ToolResult.success(toolCallId, sb.toString().trimEnd())
    }

    private fun doCreate(toolCallId: String, node: com.fasterxml.jackson.databind.JsonNode): ToolResult {
        val name = node["name"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少任务名称（name）")
        val action = node["action"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少动作类型（action）")
        val target = node["target"]?.asText() ?: "*"
        val scheduleStr = node["schedule"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少调度方式（schedule），如 'daily 20:00' 或 'interval 30'")

        // 解析 payload
        val payload = when (action) {
            "skill" -> node["skill"]?.asText()?.takeIf { it.isNotBlank() }
                ?: return ToolResult.error(toolCallId, "action=skill 时需要指定技能名（skill）")
            "broadcast" -> node["payload"]?.asText()?.takeIf { it.isNotBlank() }
                ?: return ToolResult.error(toolCallId, "action=broadcast 时需要指定广播文案（payload）")
            "command" -> node["payload"]?.asText()?.takeIf { it.isNotBlank() }
                ?: return ToolResult.error(toolCallId, "action=command 时需要指定命令（payload）")
            "agent" -> node["payload"]?.asText()?.takeIf { it.isNotBlank() }
                ?: return ToolResult.error(toolCallId, "action=agent 时需要指定 AI 指令（payload）")
            else -> return ToolResult.error(toolCallId, "未知动作类型：$action（支持 skill/broadcast/command/agent）")
        }

        // 解析调度方式
        val (type, intervalMin, time, days) = parseSchedule(scheduleStr)

        val task = scheduler.upsert(ScheduledTask(
            name = name,
            action = action,
            target = target,
            payload = payload,
            type = type,
            intervalMin = intervalMin,
            time = time,
            days = days
        ))

        audit.record("center", "create_schedule", task.id, "ALLOW")
        val scheduleDesc = when (type) {
            "daily" -> "每天 $time" + if (days.isNotEmpty()) " (周${days.joinToString(",")})" else ""
            else -> "每 $intervalMin 分钟"
        }
        return ToolResult.success(toolCallId, "已创建定时任务「${task.name}」[${task.id}]\n$scheduleDesc → $action「$payload」→ $target")
    }

    private fun doDelete(toolCallId: String, node: com.fasterxml.jackson.databind.JsonNode): ToolResult {
        val id = node["id"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少任务 ID（id）。用 list 查看现有任务。")
        val ok = scheduler.delete(id)
        audit.record("center", "delete_schedule", id, if (ok) "ALLOW" else "NOT_FOUND")
        return if (ok) ToolResult.success(toolCallId, "已删除任务 [$id]")
        else ToolResult.error(toolCallId, "任务 [$id] 不存在")
    }

    private fun doToggle(toolCallId: String, node: com.fasterxml.jackson.databind.JsonNode): ToolResult {
        val id = node["id"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少任务 ID（id）。用 list 查看现有任务。")
        val t = scheduler.toggle(id)
        audit.record("center", "toggle_schedule", id, if (t != null) "ALLOW" else "NOT_FOUND")
        return if (t != null) ToolResult.success(toolCallId, "已${if (t.enabled) "启用" else "禁用"}任务「${t.name}」[${t.id}]")
        else ToolResult.error(toolCallId, "任务 [$id] 不存在")
    }

    /** 解析调度方式字符串：'daily 20:00' / 'daily 08:00 1,2,3,4,5' / 'interval 30'。 */
    private fun parseSchedule(s: String): ScheduleSpec {
        val parts = s.trim().split(Regex("\\s+"))
        return when {
            parts[0].equals("daily", true) -> {
                val time = parts.getOrElse(1) { "12:00" }
                val days = parts.getOrElse(2) { "" }.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }
                ScheduleSpec("daily", 60, time, days)
            }
            parts[0].equals("interval", true) -> {
                val min = parts.getOrElse(1) { "60" }.toIntOrNull()?.coerceIn(1, 1440) ?: 60
                ScheduleSpec("interval", min, "12:00", emptyList())
            }
            else -> ScheduleSpec("interval", 60, "12:00", emptyList())
        }
    }

    private data class ScheduleSpec(val type: String, val intervalMin: Int, val time: String, val days: List<Int>)
}
