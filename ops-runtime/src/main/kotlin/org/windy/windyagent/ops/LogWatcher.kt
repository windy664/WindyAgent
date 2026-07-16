package org.windy.windyagent.ops

import org.slf4j.LoggerFactory
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 日志文件监控器：daemon 线程定期扫描服务器日志文件，检测 ERROR / Exception 等异常模式，
 * 检测到后调用 [onError] 回调（携带错误行 + 前后上下文），供 Agent 分析诊断。
 *
 * 设计：
 *  - 跟踪文件读取位置（[lastOffset]），只读新增内容，不重复扫描。
 *  - 去重：同一错误模式 [dedupWindowMs] 内不重复报（防刷屏）。
 *  - 平台无关：只读文件，不依赖 Bukkit/Velocity API。
 *  - 日志路径由调用方注入（bukkit=服务器根目录/logs/latest.log，velocity=同理）。
 */
class LogWatcher(
    /** 日志文件路径（可多个，如 bukkit 的 latest.log + 历史日志）。 */
    private val logFiles: List<File>,
    /** 检测到错误时的回调（错误行 + 上下文）。 */
    private val onError: (LogError) -> Unit,
    /** 扫描间隔（秒）。 */
    private val intervalSec: Long = 30,
    /** 同一错误模式的去重窗口（毫秒）。 */
    private val dedupWindowMs: Long = 5 * 60 * 1000
) {
    private val log = LoggerFactory.getLogger(LogWatcher::class.java)
    private val exec = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "windyagent-logwatcher").apply { isDaemon = true } }
    /** 每个文件的上次读取位置。 */
    private val lastOffset = ConcurrentHashMap<File, Long>()
    /** 错误去重：错误模式 → 上次报错时间。 */
    private val dedup = ConcurrentHashMap<String, Long>()
    /** 洪泛防护：每分钟最大推送条数（超出丢弃 + 告警一次）。 */
    private val rateLimiter = RateLimiter(maxPerMinute = 30)

    fun start() {
        // 初始化：从文件末尾开始（不扫描历史）
        for (f in logFiles) {
            if (f.exists()) lastOffset[f] = f.length()
        }
        exec.scheduleAtFixedRate(
            { runCatching { scan() }.onFailure { log.warn("日志扫描异常：{}", it.message) } },
            intervalSec, intervalSec, TimeUnit.SECONDS
        )
        log.info("日志监控已启动 — {} 个文件，每 {}s 扫描", logFiles.size, intervalSec)
    }

    fun stop() = exec.shutdown()

    private fun scan() {
        for (f in logFiles) {
            if (!f.exists()) continue
            val offset = lastOffset[f] ?: 0L
            val len = f.length()
            if (len <= offset) continue // 文件没增长（可能被轮转）

            // 读取新增内容
            val newLines = readLines(f, offset)
            lastOffset[f] = len

            // 逐行检测错误模式
            for ((i, line) in newLines.withIndex()) {
                val errorMatch = matchError(line) ?: continue
                val key = errorMatch.pattern
                val now = System.currentTimeMillis()
                // 去重
                val last = dedup[key]
                if (last != null && now - last < dedupWindowMs) continue
                dedup[key] = now

                // 收集上下文（错误行前 5 行 + 后 5 行）
                val start = (i - 5).coerceAtLeast(0)
                val end = (i + 5).coerceAtMost(newLines.size - 1)
                val context = newLines.subList(start, end + 1)

                val error = LogError(
                    file = f.name,
                    lineNum = offset.toInt() + i + 1,
                    errorLine = line,
                    context = context,
                    pattern = errorMatch.pattern,
                    severity = errorMatch.severity,
                    ts = now
                )
                log.info("检测到日志异常：[{}] {}", error.severity, error.pattern)
                // 洪泛防护：每分钟最多推 N 条
                if (rateLimiter.tryAcquire()) {
                    runCatching { onError(error) }.onFailure { log.warn("日志错误回调失败：{}", it.message) }
                }
            }
        }
    }

    /** 从指定偏移量读取文件新增行。 */
    private fun readLines(f: File, offset: Long): List<String> {
        return runCatching {
            RandomAccessFile(f, "r").use { raf ->
                raf.seek(offset)
                val lines = mutableListOf<String>()
                var line = raf.readLine()
                while (line != null) {
                    lines.add(String(line.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8))
                    line = raf.readLine()
                }
                lines
            }
        }.getOrDefault(emptyList())
    }

    /** 匹配错误模式。返回 null = 不是错误行。 */
    private fun matchError(line: String): ErrorMatch? {
        val trimmed = line.trim()
        for ((pattern, severity) in ERROR_PATTERNS) {
            if (trimmed.contains(pattern, ignoreCase = true)) {
                return ErrorMatch(pattern, severity)
            }
        }
        return null
    }

    private data class ErrorMatch(val pattern: String, val severity: String)

    /** 简单滑动窗口限流器：每分钟最多 maxPerMinute 次。 */
    private class RateLimiter(private val maxPerMinute: Int) {
        private val window = java.util.concurrent.ConcurrentLinkedDeque<Long>()
        fun tryAcquire(): Boolean {
            val now = System.currentTimeMillis()
            while (window.peekFirst()?.let { now - it > 60_000 } == true) window.pollFirst()
            if (window.size >= maxPerMinute) return false
            window.addLast(now)
            return true
        }
    }

    companion object {
        /** 错误模式 → 严重级别（按优先级排列，先匹配的优先）。 */
        private val ERROR_PATTERNS = listOf(
            "OutOfMemoryError" to "critical",
            "StackOverflowError" to "critical",
            "java.lang.Error" to "critical",
            "Exception" to "error",
            "ERROR" to "error",
            "FAILED" to "error",
            "FATAL" to "critical",
            "WARN" to "warn"
        )
    }
}

/** 一条检测到的日志错误。 */
data class LogError(
    val file: String,
    val lineNum: Int,
    val errorLine: String,
    /** 错误行前后的上下文（前 5 行 + 后 5 行）。 */
    val context: List<String>,
    /** 匹配到的错误模式（如 "Exception"、"OutOfMemoryError"）。 */
    val pattern: String,
    val severity: String,
    val ts: Long
) {
    /** 给 Agent 看的摘要（截断过长内容）。 */
    fun summary(maxContextLen: Int = 2000): String {
        val sb = StringBuilder()
        sb.appendLine("⚠️ 日志异常 [$severity] ($file:$lineNum)")
        sb.appendLine("模式：$pattern")
        sb.appendLine("--- 日志上下文 ---")
        val ctxStr = context.joinToString("\n")
        if (ctxStr.length > maxContextLen) {
            sb.appendLine(ctxStr.take(maxContextLen) + "\n... (截断)")
        } else {
            sb.appendLine(ctxStr)
        }
        sb.appendLine("--- 请分析根因并提供解决方案 ---")
        return sb.toString()
    }
}
