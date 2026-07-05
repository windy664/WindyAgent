package org.windy.windyagent.web.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import org.windy.windyagent.bridge.ImThreadRegistry
import org.windy.windyagent.web.ApiHandler
import org.windy.windyagent.web.DashboardServer

/**
 * IM 联动 API：/api/im/threads —— 返回各 IM 平台（QQ 等）已登记的「固定对话」列表。
 *
 * web 前端据此把这些对话置顶固定显示（点进去用其 session，与 web 对话无缝衔接）。
 * 数据来自 [ImThreadRegistry]（IM 超管首次发消息时懒注册）；无 IM 联动则返回空数组。
 */
class ImHandler(private val server: DashboardServer) : ApiHandler {

    override fun canHandle(path: String): Boolean = path.startsWith("/api/im/")

    override fun handle(ex: HttpExchange, path: String, query: Map<String, String>, mapper: ObjectMapper) {
        when (path) {
            "/api/im/threads" -> {
                val arr = mapper.createArrayNode()
                for (t in ImThreadRegistry.snapshot()) {
                    arr.add(mapper.createObjectNode()
                        .put("session", t.session)
                        .put("platform", t.platform)
                        .put("title", t.title)
                        .put("updatedAt", t.updatedAt))
                }
                server.json(ex, 200, arr.toString())
            }
            else -> server.json(ex, 404, """{"error":"not found"}""")
        }
    }
}
