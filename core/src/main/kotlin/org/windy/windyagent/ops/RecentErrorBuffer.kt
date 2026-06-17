package org.windy.windyagent.ops

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * 中心侧日志异常缓冲区：存储各子服经总线推来的日志异常，供 Agent 读取分析。
 *
 * 环形缓冲（最多 [maxSize] 条），自动淘汰旧条目。
 * 线程安全：ConcurrentLinkedDeque，读写无锁。
 */
class RecentErrorBuffer(
    private val maxSize: Int = 100,
    /** 持久化文件路径（null = 不持久化，纯内存）。 */
    private val persistFile: File? = null
) {

    private val log = LoggerFactory.getLogger(RecentErrorBuffer::class.java)
    private val buffer = ConcurrentLinkedDeque<ErrorEntry>()
    private val mapper = ObjectMapper().registerKotlinModule()

    init { load() }

    /** 接收一条日志异常（由总线回调调用）。 */
    fun add(entry: ErrorEntry) {
        buffer.addFirst(entry)
        while (buffer.size > maxSize) buffer.pollLast()
        log.info("收到日志异常：[{}] {} — {}", entry.server, entry.severity, entry.pattern)
        persist()
    }

    /** 从 JSON 反序列化并添加。 */
    fun addFromJson(json: String) {
        runCatching {
            val entry = mapper.readValue(json, ErrorEntry::class.java)
            add(entry)
        }.onFailure { log.warn("解析日志异常 JSON 失败：{}", it.message) }
    }

    /** 获取最近 N 条异常。 */
    fun recent(n: Int = 20): List<ErrorEntry> = buffer.toList().take(n)

    /** 按子服过滤。 */
    fun byServer(server: String, n: Int = 20): List<ErrorEntry> =
        buffer.filter { it.server.equals(server, true) }.take(n)

    /** 按严重级别过滤。 */
    fun bySeverity(severity: String, n: Int = 20): List<ErrorEntry> =
        buffer.filter { it.severity.equals(severity, true) }.take(n)

    /** 清空。 */
    fun clear() = buffer.clear()

    fun size(): Int = buffer.size

    private fun load() {
        val f = persistFile ?: return
        runCatching {
            if (f.exists()) {
                val list = mapper.readValue(f, Array<ErrorEntry>::class.java)
                list?.let { buffer.addAll(it) }
            }
        }.onFailure { log.warn("加载日志异常缓冲失败：{}", it.message) }
    }

    @Synchronized
    private fun persist() {
        val f = persistFile ?: return
        runCatching {
            f.parentFile?.mkdirs()
            mapper.writerWithDefaultPrettyPrinter().writeValue(f, buffer.toList())
        }.onFailure { log.warn("保存日志异常缓冲失败：{}", it.message) }
    }

    /** 给 Agent 看的摘要。 */
    fun summary(n: Int = 20): String {
        val entries = recent(n)
        if (entries.isEmpty()) return "最近没有日志异常。"
        val sb = StringBuilder("最近 ${entries.size} 条日志异常：\n\n")
        for (e in entries) {
            sb.appendLine("• [${e.severity}] ${e.server} — ${e.pattern}")
            sb.appendLine("  ${e.errorLine.take(120)}")
            sb.appendLine("  (${e.file}:${e.lineNum}, ${formatTime(e.ts)})")
            sb.appendLine()
        }
        return sb.toString().trimEnd()
    }

    private fun formatTime(ts: Long): String =
        java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime().toString()
}

/** 一条日志异常（跨进程传输用，与 LogError 对齐但可序列化）。 */
data class ErrorEntry(
    val server: String = "",
    val file: String = "",
    val lineNum: Int = 0,
    val errorLine: String = "",
    val context: List<String> = emptyList(),
    val pattern: String = "",
    val severity: String = "error",
    val ts: Long = 0
)
