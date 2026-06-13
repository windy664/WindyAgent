package org.windy.windyagent.platform.velocity.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.slf4j.LoggerFactory
import org.windy.windyagent.bus.MessageBus
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * AI 管理控制台后端：JDK 内置 HttpServer（零依赖），跑在 velocity 进程内（要拉总线/agent/知识库）。
 * v1 先做行为看板：静态单页 + /api 路由 JSON。chat / kb 端点下一轮加（API 已按 JSON+token 设计，将来前端可拆独立项目）。
 * 安全：/api 路由 必须带 token（query ?token= 或头 X-Token）；默认绑 127.0.0.1。
 */
class DashboardServer(
    private val host: String,
    private val port: Int,
    private val token: String,
    private val bus: MessageBus,
    private val timeoutMs: Long,
    private val connectedServers: () -> Set<String>
) {
    private val log = LoggerFactory.getLogger(DashboardServer::class.java)
    private var server: HttpServer? = null
    private val page: ByteArray by lazy {
        javaClass.getResourceAsStream("/dashboard.html")?.use { it.readBytes() } ?: "<h1>dashboard.html missing</h1>".toByteArray()
    }

    fun start() {
        val s = HttpServer.create(InetSocketAddress(host, port), 0)
        s.executor = Executors.newFixedThreadPool(4) { r -> Thread(r, "windyagent-web").apply { isDaemon = true } }
        s.createContext("/") { ex -> handle(ex) }
        s.start()
        server = s
        if (token.isBlank()) log.warn("管理控制台 token 为空——/api 无鉴权，强烈建议设 web.token 并仅绑 127.0.0.1")
        log.info("管理控制台已启动：http://{}:{}/", if (host == "0.0.0.0") "<本机IP>" else host, port)
    }

    fun stop() { server?.stop(0); server = null }

    private fun handle(ex: HttpExchange) {
        try {
            val path = ex.requestURI.path
            val q = parseQuery(ex.requestURI.query)
            when {
                path == "/" || path == "/index.html" -> respond(ex, 200, "text/html; charset=utf-8", page)
                path.startsWith("/api/") -> {
                    if (token.isNotEmpty() && q["token"] != token && ex.requestHeaders.getFirst("X-Token") != token) {
                        json(ex, 401, """{"error":"unauthorized"}"""); return
                    }
                    api(ex, path, q)
                }
                else -> json(ex, 404, """{"error":"not found"}""")
            }
        } catch (e: Exception) {
            runCatching { json(ex, 500, """{"error":${jstr(e.message ?: "error")}}""") }
        } finally { ex.close() }
    }

    private fun api(ex: HttpExchange, path: String, q: Map<String, String>) {
        when (path) {
            "/api/servers" -> json(ex, 200, "[" + connectedServers().sorted().joinToString(",") { jstr(it) } + "]")
            "/api/stats" -> proxy(ex, q, "behavior_stats", "{}")
            "/api/segments" -> proxy(ex, q, "behavior_segments", "{}")
            "/api/player" -> {
                val name = q["name"]?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"missing name"}""")
                proxy(ex, q, "behavior_player", """{"player":${jstr(name)}}""")
            }
            else -> json(ex, 404, """{"error":"unknown api"}""")
        }
    }

    /** 把请求转成总线动作派发到子服，子服回的就是 JSON，直接透传。 */
    private fun proxy(ex: HttpExchange, q: Map<String, String>, action: String, args: String) {
        val server = q["server"]?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"missing server"}""")
        if (server !in connectedServers()) return json(ex, 400, """{"error":"server not connected"}""")
        val reply = runCatching { bus.dispatch(server, action, args, timeoutMs).get(timeoutMs + 1000, TimeUnit.MILLISECONDS) }.getOrNull()
        if (reply == null) json(ex, 504, """{"error":"timeout"}""")
        else if (!reply.success) json(ex, 502, """{"error":${jstr(reply.content)}}""")
        else json(ex, 200, reply.content)
    }

    private fun json(ex: HttpExchange, code: Int, body: String) = respond(ex, code, "application/json; charset=utf-8", body.toByteArray(StandardCharsets.UTF_8))

    private fun respond(ex: HttpExchange, code: Int, contentType: String, body: ByteArray) {
        ex.responseHeaders.add("Content-Type", contentType)
        ex.sendResponseHeaders(code, body.size.toLong())
        ex.responseBody.use { it.write(body) }
    }

    private fun parseQuery(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        return query.split("&").mapNotNull {
            val i = it.indexOf('='); if (i < 0) return@mapNotNull null
            dec(it.substring(0, i)) to dec(it.substring(i + 1))
        }.toMap()
    }

    private fun dec(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    /** 最小 JSON 字符串转义（够用于服务器名/玩家名/错误文本）。 */
    private fun jstr(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ") + "\""
}
