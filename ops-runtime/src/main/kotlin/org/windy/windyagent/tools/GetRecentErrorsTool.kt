package org.windy.windyagent.tools

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.ops.RecentErrorBuffer

/**
 * 让 Agent 读取各子服经总线推来的日志异常——管理员说"最近有什么报错"时调用，
 * 或 Agent 收到日志异常通知后主动查看更多上下文。
 */
class GetRecentErrorsTool(
    private val buffer: RecentErrorBuffer
) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = "get_recent_errors"
    override val description = "读取各子服最近的日志异常（ERROR/Exception/WARN）。可按子服名或严重级别过滤。当管理员问「最近有什么报错」或收到日志异常通知时调用。"
    override val inputSchema = """{"type":"object","properties":{"server":{"type":"string","description":"按子服名过滤（可选）"},"severity":{"type":"string","description":"按严重级别过滤：critical / error / warn（可选）"},"limit":{"type":"integer","description":"返回条数（默认 20）"}},"required":[]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val node = runCatching { mapper.readTree(inputJson) }.getOrNull()
        val server = node?.get("server")?.asText()
        val severity = node?.get("severity")?.asText()
        val limit = node?.get("limit")?.asInt()?.coerceIn(1, 100) ?: 20

        val entries = when {
            !server.isNullOrBlank() -> buffer.byServer(server, limit)
            !severity.isNullOrBlank() -> buffer.bySeverity(severity, limit)
            else -> buffer.recent(limit)
        }

        if (entries.isEmpty()) {
            val filter = listOfNotNull(server?.let { "子服=$it" }, severity?.let { "级别=$it" } )
                .joinToString("，").ifBlank { "无过滤条件" }
            return ToolResult.success(toolCallId, "最近没有日志异常。（$filter）")
        }

        val sb = StringBuilder("共 ${entries.size} 条日志异常：\n\n")
        for (e in entries) {
            sb.appendLine("• [${e.severity.uppercase()}] ${e.server} — ${e.pattern}")
            sb.appendLine("  文件：${e.file}:${e.lineNum}")
            sb.appendLine("  错误：${e.errorLine.take(200)}")
            if (e.context.isNotEmpty()) {
                sb.appendLine("  上下文：")
                val ctx = e.context.joinToString("\n").take(500)
                sb.appendLine("  $ctx")
            }
            sb.appendLine()
        }
        ToolResult.success(toolCallId, sb.toString().trimEnd())
    }.getOrElse { ToolResult.error(toolCallId, "读取日志异常失败：${it.message}") }
}
