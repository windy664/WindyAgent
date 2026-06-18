package org.windy.windyagent.web

import com.sun.net.httpserver.HttpExchange
import java.nio.charset.StandardCharsets

/**
 * 静态资源：只提供 jar 内的 dashboard.html。
 */
object StaticHandler {
    private val bundled: ByteArray by lazy {
        StaticHandler::class.java.getResourceAsStream("/dashboard.html")?.use { it.readBytes() }
            ?: "<h1>dashboard.html missing</h1>".toByteArray()
    }

    fun serve(ex: HttpExchange) {
        ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        ex.responseHeaders.add("Cache-Control", "no-cache, no-store, must-revalidate")
        ex.sendResponseHeaders(200, bundled.size.toLong())
        ex.responseBody.use { it.write(bundled) }
    }
}
