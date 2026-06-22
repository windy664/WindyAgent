package org.windy.windyagent.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * AI 管理控制台后端 —— HTTP 路由 + 鉴权骨架。
 *
 * 职责单一：只管 HTTP 生命周期、token 鉴权、路由分发。
 * 各业务域由 [ApiHandler] 实现类处理，通过 [register] 注册。
 *
 * 设计原则：
 * - 新增 API 域 = 新建一个 ApiHandler 实现 + 在插件启动时 register，不改本类
 * - 静态资源（dashboard.html）由 [StaticHandler] 提供
 * - CORS / 缓存 / 错误处理在本类统一处理
 */
class DashboardServer(
    private val host: String,
    private val port: Int,
    private val token: String
) {
    private val log = LoggerFactory.getLogger(DashboardServer::class.java)
    private val mapper = ObjectMapper()
    private var server: HttpServer? = null
    private val handlers = mutableListOf<ApiHandler>()

    /** 注册一个业务域 handler。启动前调用。 */
    fun register(handler: ApiHandler) { handlers.add(handler) }

    fun start() {
        val s = HttpServer.create(InetSocketAddress(host, port), 0)
        s.executor = Executors.newFixedThreadPool(6) { r -> Thread(r, "windyagent-web").apply { isDaemon = true } }
        s.createContext("/") { ex -> handle(ex) }
        s.start()
        server = s
        if (token.isBlank()) log.warn("[Web] token 为空——接口无鉴权，强烈建议设 web.token 并仅绑 127.0.0.1")
        log.info("[Web] 管理控制台已启动：http://{}:{}/ （{} 个 handler，token={}）",
            if (host == "0.0.0.0") "<本机IP>" else host, port, handlers.size,
            if (token.isBlank()) "无" else token)
    }

    fun stop() { server?.stop(0); server = null }

    // ── HTTP 路由 + 鉴权 ──

    private fun handle(ex: HttpExchange) {
        try {
            val path = ex.requestURI.path
            val q = parseQuery(ex.requestURI.query)

            // 静态资源：/ 默认 Vue 新版（/next 保留为别名）
            if (path == "/" || path == "/index.html" || path == "/next" || path == "/next.html") {
                StaticHandler.serveNext(ex); return
            }
            // 旧版控制台降级到 /legacy 兜底（功能已全部迁到 Vue，留作回退）
            if (path == "/legacy" || path == "/legacy.html") {
                StaticHandler.serve(ex); return
            }

            // API 鉴权
            if (path.startsWith("/api/")) {
                if (token.isNotEmpty() && q["token"] != token && ex.requestHeaders.getFirst("X-Token") != token) {
                    json(ex, 401, """{"error":"unauthorized"}"""); return
                }
                // 路由分发：找到第一个能处理的 handler
                for (h in handlers) {
                    if (h.canHandle(path)) {
                        runCatching { h.handle(ex, path, q, mapper) }
                            .onFailure { json(ex, 500, """{"error":${mapper.writeValueAsString(it.message ?: "error")}}""") }
                        return
                    }
                }
                json(ex, 404, """{"error":"unknown api"}""")
                return
            }

            json(ex, 404, """{"error":"not found"}""")
        } catch (e: Exception) {
            runCatching { json(ex, 500, """{"error":${mapper.writeValueAsString(e.message ?: "error")}}""") }
        } finally { ex.close() }
    }

    // ── 工具方法（供 handler 调用）──

    fun json(ex: HttpExchange, code: Int, body: String) = respond(ex, code, "application/json; charset=utf-8", body.toByteArray(StandardCharsets.UTF_8))

    fun respond(ex: HttpExchange, code: Int, contentType: String, body: ByteArray) {
        ex.responseHeaders.add("Content-Type", contentType)
        ex.responseHeaders.add("Cache-Control", "no-cache, no-store, must-revalidate")
        ex.responseHeaders.add("Access-Control-Allow-Origin", "*")
        ex.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
        ex.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type, X-Token")
        if (ex.requestMethod == "OPTIONS") { ex.sendResponseHeaders(204, -1); ex.close(); return }
        ex.sendResponseHeaders(code, body.size.toLong())
        ex.responseBody.use { it.write(body) }
    }

    fun body(ex: HttpExchange): String {
        val max = 512 * 1024
        return ex.requestBody.use { stream ->
            val buf = ByteArray(max + 1); var total = 0
            while (total < buf.size) { val n = stream.read(buf, total, buf.size - total); if (n < 0) break; total += n }
            if (total > max) throw IllegalArgumentException("Request body too large (>512KB)")
            String(buf, 0, total, StandardCharsets.UTF_8)
        }
    }

    fun jstr(s: String): String = mapper.writeValueAsString(s)

    fun parseQuery(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        return query.split("&").mapNotNull {
            val i = it.indexOf('='); if (i < 0) return@mapNotNull null
            dec(it.substring(0, i)) to dec(it.substring(i + 1))
        }.toMap()
    }

    private fun dec(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
