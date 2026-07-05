package org.windy.windyagent.web.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.web.ApiHandler
import org.windy.windyagent.web.DashboardServer
import java.util.concurrent.TimeUnit

/**
 * 行为看板 API：/api/stats · /api/segments · /api/words · /api/player
 *
 * 行为数据归<b>子服</b>（各子服自己的 behavior.db）。本 handler 经跨服总线把请求代理到目标子服
 * （action: behavior_board / behavior_segments / behavior_words / behavior_player），子服的
 * BukkitCapabilityHandler 已实现这些 action，返回现成分析 JSON。
 *
 * <p>前端字段：/api/stats 期望 {kpis,trend,behavior,segments,topCommands,topPlaytime}，
 * 对应子服的 boardJson（action behavior_board），而非 statsJson。
 *
 * <p>需启用跨服总线；未连子服/超时/未启用行为分析时优雅降级（前端已容错为空）。
 */
class BoardHandler(
    private val server: DashboardServer,
    private val bus: MessageBus?,
    private val timeoutMs: Long,
    private val connectedServers: () -> Set<String>,
) : ApiHandler {

    override fun canHandle(path: String): Boolean = path in PATHS

    override fun handle(ex: HttpExchange, path: String, query: Map<String, String>, mapper: ObjectMapper) {
        when (path) {
            "/api/stats" -> proxy(ex, query, "behavior_board", mapper)
            "/api/segments" -> proxy(ex, query, "behavior_segments", mapper)
            "/api/words" -> {
                // 透传 source/limit 给子服
                val payload = mapper.createObjectNode()
                query["source"]?.let { payload.put("source", it) }
                query["limit"]?.toIntOrNull()?.let { payload.put("limit", it) }
                proxy(ex, query, "behavior_words", mapper, payload.toString())
            }
            "/api/player" -> {
                val payload = mapper.createObjectNode()
                query["name"]?.let { payload.put("name", it) }
                proxy(ex, query, "behavior_player", mapper, payload.toString())
            }
            else -> server.json(ex, 404, """{"error":"not found"}""")
        }
    }

    private fun proxy(ex: HttpExchange, q: Map<String, String>, action: String, mapper: ObjectMapper, payload: String = "{}") {
        val b = bus ?: return server.json(ex, 503, """{"error":"cross-server bus not enabled"}""")
        val s = q["server"]?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"missing server"}""")
        if (s !in connectedServers()) return server.json(ex, 400, """{"error":"server not connected"}""")
        val reply = runCatching { b.dispatch(s, action, payload, timeoutMs).get(timeoutMs + 1000, TimeUnit.MILLISECONDS) }.getOrNull()
        when {
            reply == null -> server.json(ex, 504, """{"error":"timeout"}""")
            !reply.success -> server.json(ex, 502, """{"error":${mapper.writeValueAsString(reply.content)}}""")
            else -> server.json(ex, 200, reply.content)
        }
    }

    companion object {
        private val PATHS = setOf("/api/stats", "/api/segments", "/api/words", "/api/player")
    }
}
