package org.windy.windyagent.ops

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import org.windy.windyagent.llm.LLMUsageTracker
import org.windy.windyagent.platform.SessionManager
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * 系统健康数据聚合：收集 LLM 延迟趋势、工具调用热力图、错误率等，
 * 供 Dashboard /api/system 端点消费。
 */
class SystemHealth(
    private val usageTracker: LLMUsageTracker?,
    private val sessions: SessionManager?
) {
    private val mapper = ObjectMapper()

    // 工具调用统计（滑动窗口，最近 1000 条）
    data class ToolCallRecord(val tool: String, val latencyMs: Long, val success: Boolean, val ts: Long)
    private val recentCalls = ConcurrentLinkedDeque<ToolCallRecord>()
    private val maxRecent = 1000

    // 错误统计
    private val recentErrors = ConcurrentLinkedDeque<Pair<Long, String>>()

    fun recordToolCall(tool: String, latencyMs: Long, success: Boolean) {
        recentCalls.addLast(ToolCallRecord(tool, latencyMs, success, System.currentTimeMillis()))
        while (recentCalls.size > maxRecent) recentCalls.pollFirst()
        if (!success) {
            recentErrors.addLast(System.currentTimeMillis() to tool)
            while (recentErrors.size > 500) recentErrors.pollFirst()
        }
    }

    /** 生成系统健康 JSON。 */
    fun toJson(): String {
        val root = mapper.createObjectNode()

        // JVM 信息
        val rt = ManagementFactory.getRuntimeMXBean()
        val mem = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        root.put("uptimeMs", rt.uptime)
        root.put("heapUsedMb", mem.used / 1024 / 1024)
        root.put("heapMaxMb", mem.max / 1024 / 1024)
        root.put("activeSessions", sessions?.activeSessions() ?: 0)

        // LLM 用量摘要
        usageTracker?.let { tracker ->
            val summary = tracker.summary()
            val llm = root.putObject("llm")
            llm.put("totalCalls", summary.totalCalls)
            llm.put("totalInputTokens", summary.totalInputTokens)
            llm.put("totalOutputTokens", summary.totalOutputTokens)
            llm.put("avgLatencyMs", if (summary.totalCalls > 0) summary.totalLatencyMs / summary.totalCalls else 0)
            // 最近 7 天每日趋势
            val daily = tracker.queryDaily(7)
            val dailyArr = llm.putArray("daily")
            for (d in daily) {
                dailyArr.addObject()
                    .put("day", d.day)
                    .put("inputTokens", d.inputTokens)
                    .put("outputTokens", d.outputTokens)
                    .put("calls", d.calls)
                    .put("avgLatencyMs", if (d.calls > 0) d.totalLatencyMs / d.calls else 0)
            }
        }

        // 工具调用热力图（按工具名聚合）
        val toolStats = root.putObject("tools")
        val byTool = recentCalls.groupBy { it.tool }
        for ((tool, calls) in byTool) {
            val obj = toolStats.putObject(tool)
            obj.put("calls", calls.size)
            obj.put("avgLatencyMs", calls.map { it.latencyMs }.average().toLong())
            obj.put("errorRate", calls.count { !it.success }.toDouble() / calls.size.coerceAtLeast(1))
            obj.put("lastCall", calls.maxOfOrNull { it.ts } ?: 0)
        }

        // 错误率趋势（最近 24 小时，每小时一个桶）
        val now = System.currentTimeMillis()
        val hourMs = 3600_000L
        val errorBuckets = mapper.createArrayNode()
        for (i in 0..23) errorBuckets.addObject()
        for ((ts, _) in recentErrors) {
            val hour = ((now - ts) / hourMs).toInt().coerceIn(0, 23)
            val bucket = errorBuckets[23 - hour] as com.fasterxml.jackson.databind.node.ObjectNode
            bucket.put("errors", (bucket["errors"]?.asInt() ?: 0) + 1)
        }
        root.set<ArrayNode>("errorTrend", errorBuckets)

        return mapper.writeValueAsString(root)
    }
}
