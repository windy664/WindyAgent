package org.windy.windyagent.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange

/**
 * Dashboard API handler 接口。每个业务域实现一个。
 *
 * [DashboardServer] 路由时遍历所有已注册 handler，找到第一个 [canHandle] 返回 true 的来处理。
 * 新增 API 域只需：实现本接口 + 在插件启动时 `server.register(XxxHandler(...))`。
 */
interface ApiHandler {
    /** 是否能处理该路径（如 path.startsWith("/api/chat"))。 */
    fun canHandle(path: String): Boolean

    /** 处理请求。已通过鉴权。可用 server.json() / server.body() 等工具方法。 */
    fun handle(ex: HttpExchange, path: String, query: Map<String, String>, mapper: ObjectMapper)
}
