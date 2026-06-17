package org.windy.windyagent.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.llm.ToolResult
import java.io.File

/**
 * 让 Agent 读取服务器日志文件——管理员说"看下日志有没有报错"时调用。
 *
 * 两种用法：
 *  - 主动查看：管理员要求 → Agent 调用本工具读最近 N 行日志
 *  - 错误诊断：LogWatcher 检测到异常 → Agent 读取更多上下文做分析
 *
 * 参数：
 *  - path：日志文件路径（默认 logs/latest.log）
 *  - lines：读取行数（默认 100，最大 500）
 *  - filter：可选过滤关键词（只返回包含该词的行）
 *  - level：过滤日志级别（ERROR / WARN / INFO），省略=不过滤
 */
class ReadLogTool(
    /** 日志目录（bukkit: 服务器根目录/logs；velocity: 数据目录/logs）。 */
    private val logDir: File
) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = "read_log"
    override val description = "读取服务器日志文件。可用于：① 主动检查日志是否有报错；② LogWatcher 检测到异常后读取更多上下文做诊断。返回最近 N 行日志（支持按关键词/级别过滤）。"
    override val inputSchema = """{"type":"object","properties":{"path":{"type":"string","description":"日志文件名（默认 latest.log）"},"lines":{"type":"integer","description":"读取行数（默认 100，最大 500）"},"filter":{"type":"string","description":"过滤关键词（只返回包含该词的行）"},"level":{"type":"string","description":"过滤日志级别：ERROR / WARN / INFO","enum":["ERROR","WARN","INFO"]}},"required":[]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val node = runCatching { mapper.readTree(inputJson) }.getOrNull()
        val fileName = node?.get("path")?.asText() ?: "latest.log"
        val lines = node?.get("lines")?.asInt()?.coerceIn(1, 500) ?: 100
        val filter = node?.get("filter")?.asText()
        val level = node?.get("level")?.asText()

        val file = File(logDir, fileName)
        if (!file.exists()) {
            return ToolResult.success(toolCallId, "日志文件不存在：${file.absolutePath}")
        }

        // 读取最后 N 行
        val allLines = file.readLines(Charsets.UTF_8)
        val tail = allLines.takeLast(lines)

        // 过滤
        var filtered = tail
        if (!filter.isNullOrBlank()) {
            filtered = filtered.filter { it.contains(filter, ignoreCase = true) }
        }
        if (!level.isNullOrBlank()) {
            filtered = filtered.filter { it.contains(level, ignoreCase = true) }
        }

        if (filtered.isEmpty()) {
            return ToolResult.success(toolCallId, "最近 ${lines} 行日志中没有匹配的内容。" +
                if (filter != null || level != null) "（过滤条件：${listOfNotNull(filter, level).joinToString(" + ")}）" else "")
        }

        val sb = StringBuilder()
        sb.appendLine("📄 ${file.name} 最近 ${lines} 行" +
            if (filter != null || level != null) "（过滤：${listOfNotNull(filter, level).joinToString(" + ")}）" else "")
        sb.appendLine("共 ${filtered.size} 行匹配：")
        sb.appendLine("```")
        val content = filtered.joinToString("\n")
        if (content.length > 3000) {
            sb.appendLine(content.takeLast(3000) + "\n... (前面内容已截断)")
        } else {
            sb.appendLine(content)
        }
        sb.appendLine("```")
        ToolResult.success(toolCallId, sb.toString().trimEnd())
    }.getOrElse { ToolResult.error(toolCallId, "读取日志失败：${it.message}") }
}
