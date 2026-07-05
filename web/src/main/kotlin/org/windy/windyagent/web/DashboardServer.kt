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
    private val token: String,
    private val security: WebSecurity = WebSecurity(token, "", 5, 10)
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
        if (token.isBlank()) log.warn("[Web] token 为空——接口无鉴权，强烈建议设 web.token（尤其 host 非 127.0.0.1 时）")
        // 不在日志中打印 token / 安全入口（日志常被截图/上传，避免泄漏）；均看 windyagent-config.yml
        log.info("[Web] 管理控制台已启动：http://{}:{}{}/ （{} 个 handler，鉴权：{}，安全入口：{}）",
            if (host == "0.0.0.0") "<本机IP>" else host, port, security.entryPath, handlers.size,
            if (token.isBlank()) "关闭" else "开启(token)",
            if (security.entryEnabled) "开启(见配置)" else "关闭")
    }

    fun stop() { server?.stop(0); server = null }

    // ── HTTP 路由 + 鉴权 ──

    private fun handle(ex: HttpExchange) {
        try {
            var path = ex.requestURI.path
            val q = parseQuery(ex.requestURI.query)

            // ① 安全入口（宝塔式）：配了秘密前缀时，所有访问须经 /<entry>/...，否则 404 —— 挡全网扫描。
            //    命中后剥掉前缀，后续按正常路径处理；前端资源用相对路径，故剥离后一致。
            if (security.entryEnabled) {
                val ep = security.entryPath
                if (path == ep) { redirect(ex, "$ep/"); return }
                if (!path.startsWith("$ep/")) { json(ex, 404, """{"error":"not found"}"""); return }
                path = path.removePrefix(ep).ifEmpty { "/" }
            }

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
                val ip = security.clientIp(ex)
                // ② 失败锁定（宝塔式防爆破）：锁定期内直接 429，不再校验 token。
                if (security.isLocked(ip)) {
                    json(ex, 429, """{"error":"too many attempts, locked for ${security.lockRemainingSec(ip)}s"}"""); return
                }
                // ③ token 认证：仅认 X-Token 头 + 常量时间比较（见 WebSecurity）。
                if (!security.checkToken(ex, ip)) {
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

    private fun redirect(ex: HttpExchange, location: String) {
        ex.responseHeaders.add("Location", location)
        ex.sendResponseHeaders(302, -1)
    }

    // ── 工具方法（供 handler 调用）──

    fun json(ex: HttpExchange, code: Int, body: String) = respond(ex, code, "application/json; charset=utf-8", body.toByteArray(StandardCharsets.UTF_8))

    fun respond(ex: HttpExchange, code: Int, contentType: String, body: ByteArray) {
        val h = ex.responseHeaders
        h.add("Content-Type", contentType)
        h.add("Cache-Control", "no-cache, no-store, must-revalidate")
        // 前端与后端同源（同一 DashboardServer），无需开放跨域。
        // 仅回显同源 Origin（等价于"只允许自己"），不再用通配 * —— 避免任意站点跨域打接口。
        ex.requestHeaders.getFirst("Origin")?.let { origin ->
            h.add("Access-Control-Allow-Origin", origin)
            h.add("Vary", "Origin")
        }
        h.add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
        h.add("Access-Control-Allow-Headers", "Content-Type, X-Token")
        // 安全响应头：防点击劫持 / 防 MIME 嗅探 / 收敛 referer（token 曾可能进 referer，双保险）
        h.add("X-Frame-Options", "DENY")
        h.add("X-Content-Type-Options", "nosniff")
        h.add("Referrer-Policy", "no-referrer")
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
