package org.windy.windyagent.web.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import org.windy.windyagent.llm.LLMUsageTracker
import org.windy.windyagent.ops.SystemHealth
import org.windy.windyagent.web.ApiHandler
import org.windy.windyagent.web.DashboardServer

class UsageHandler(
    private val server: DashboardServer,
    private val usageTracker: LLMUsageTracker?,
    private val systemHealth: SystemHealth?
) : ApiHandler {

    override fun canHandle(path: String): Boolean = path == "/api/usage" || path == "/api/system"

    override fun handle(ex: HttpExchange, path: String, query: Map<String, String>, mapper: ObjectMapper) {
        when (path) {
            "/api/usage" -> usageApi(ex, query, mapper)
            "/api/system" -> server.json(ex, 200, systemHealth?.toJson() ?: """{"error":"system health unavailable"}""")
        }
    }

    private fun usageApi(ex: HttpExchange, q: Map<String, String>, mapper: ObjectMapper) {
        val tracker = usageTracker ?: return server.json(ex, 503, """{"error":"usage tracking disabled"}""")
        val days = q["days"]?.toIntOrNull()?.coerceIn(1, 30) ?: 7
        val summary = tracker.summary()
        val daily = tracker.queryDaily(days)
        val root = mapper.createObjectNode()
        root.put("totalCalls", summary.totalCalls).put("totalInputTokens", summary.totalInputTokens)
            .put("totalOutputTokens", summary.totalOutputTokens).put("totalLatencyMs", summary.totalLatencyMs)
        val arr = root.putArray("daily")
        for (d in daily) {
            arr.addObject().put("day", d.day).put("inputTokens", d.inputTokens)
                .put("outputTokens", d.outputTokens).put("calls", d.calls).put("totalLatencyMs", d.totalLatencyMs)
        }
        server.json(ex, 200, root.toString())
    }
}
