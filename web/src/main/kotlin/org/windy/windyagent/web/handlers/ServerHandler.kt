package org.windy.windyagent.web.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.web.AlertCenter
import org.windy.windyagent.web.ApiHandler
import org.windy.windyagent.web.DashboardServer
import java.util.concurrent.TimeUnit

class ServerHandler(
    private val server: DashboardServer,
    private val bus: MessageBus?,
    private val timeoutMs: Long,
    private val connectedServers: () -> Set<String>,
    private val alerts: AlertCenter?,
    private val health: (() -> String)?
) : ApiHandler {

    override fun canHandle(path: String): Boolean = path in PATHS

    override fun handle(ex: HttpExchange, path: String, query: Map<String, String>, mapper: ObjectMapper) {
        when (path) {
            "/api/servers" -> json(ex, 200, "[" + connectedServers().sorted().joinToString(",") { mapper.writeValueAsString(it) } + "]")
            "/api/health" -> json(ex, 200, runCatching { health?.invoke() }.getOrNull() ?: "[]")
            "/api/alerts" -> json(ex, 200, alerts?.json() ?: "[]")
            "/api/mods" -> proxyText(ex, query, "server_mods", mapper)
            "/api/dimtps" -> proxyText(ex, query, "dimension_tps", mapper)
            "/api/serverdetail" -> proxy(ex, query, "server_detail", mapper)
        }
    }

    private fun json(ex: HttpExchange, code: Int, body: String) {
        server.json(ex, code, body)
    }

    private fun proxy(ex: HttpExchange, q: Map<String, String>, action: String, mapper: ObjectMapper) {
        val b = bus ?: return json(ex, 503, """{"error":"cross-server bus not enabled"}""")
        val s = q["server"]?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"missing server"}""")
        if (s !in connectedServers()) return json(ex, 400, """{"error":"server not connected"}""")
        val reply = runCatching { b.dispatch(s, action, "{}", timeoutMs).get(timeoutMs + 1000, TimeUnit.MILLISECONDS) }.getOrNull()
        when {
            reply == null -> json(ex, 504, """{"error":"timeout"}""")
            !reply.success -> json(ex, 502, """{"error":${mapper.writeValueAsString(reply.content)}}""")
            else -> json(ex, 200, reply.content)
        }
    }

    private fun proxyText(ex: HttpExchange, q: Map<String, String>, action: String, mapper: ObjectMapper) {
        val b = bus ?: return json(ex, 503, """{"error":"cross-server bus not enabled"}""")
        val s = q["server"]?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"missing server"}""")
        if (s !in connectedServers()) return json(ex, 400, """{"error":"server not connected"}""")
        val reply = runCatching { b.dispatch(s, action, "{}", timeoutMs).get(timeoutMs + 1000, TimeUnit.MILLISECONDS) }.getOrNull()
        when {
            reply == null -> json(ex, 504, """{"error":"timeout"}""")
            else -> json(ex, if (reply.success) 200 else 502, mapper.createObjectNode().put("text", reply.content).toString())
        }
    }

    companion object {
        private val PATHS = setOf("/api/servers", "/api/health", "/api/alerts", "/api/mods", "/api/dimtps", "/api/serverdetail")
    }
}
