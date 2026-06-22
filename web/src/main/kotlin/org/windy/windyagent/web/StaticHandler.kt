package org.windy.windyagent.web

import com.sun.net.httpserver.HttpExchange
import java.nio.charset.StandardCharsets

/**
 * 静态资源：提供 jar 内打包的 html 单页。
 *  - dashboard-next.html  新版（Vue + Vite 构建产物），默认路由 `/`（别名 `/next`）。
 *  - dashboard.html       旧版（手写单文件），降级到 `/legacy` 作回退兜底。
 *    确认 Vue 版稳定后可删 dashboard.html + serve() + /legacy 路由收尾。
 */
object StaticHandler {
    private val legacy: ByteArray by lazy { load("/dashboard.html") }
    private val next: ByteArray by lazy { load("/dashboard-next.html") }

    private fun load(resource: String): ByteArray =
        StaticHandler::class.java.getResourceAsStream(resource)?.use { it.readBytes() }
            ?: "<h1>$resource missing —— 先在 web-ui/ 跑 npm run build 或执行 gradle 构建</h1>".toByteArray()

    /** 旧版控制台（/legacy 回退）。 */
    fun serve(ex: HttpExchange) = write(ex, legacy)

    /** 新版 Vue 控制台（默认 /）。 */
    fun serveNext(ex: HttpExchange) = write(ex, next)

    private fun write(ex: HttpExchange, body: ByteArray) {
        ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        ex.responseHeaders.add("Cache-Control", "no-cache, no-store, must-revalidate")
        ex.sendResponseHeaders(200, body.size.toLong())
        ex.responseBody.use { it.write(body) }
    }
}
