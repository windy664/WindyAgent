package org.windy.windyagent.web.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import org.windy.windyagent.player.PlayerDirectory
import org.windy.windyagent.web.ApiHandler
import org.windy.windyagent.web.DashboardServer

/**
 * 玩家管理 API：
 *   GET  /api/players        → { columns:[{key,label}], actions:[{id,label,danger}], rows:[{name,server,...扩展字段}] }
 *   POST /api/players/action → 体 {id, player, args?} → { result }
 *
 * 数据全部来自可扩展的 [PlayerDirectory]：基础行 + 各插件贡献的列/操作。本 handler 不写死任何字段，
 * 新增集成（经济/领地/封禁…）只需注册 Contributor/Action，本类与前端面板均无需改动。
 */
class PlayerHandler(private val server: DashboardServer) : ApiHandler {

    override fun canHandle(path: String): Boolean = path.startsWith("/api/players")

    override fun handle(ex: HttpExchange, path: String, query: Map<String, String>, mapper: ObjectMapper) {
        when (path) {
            "/api/players" -> list(ex, mapper)
            "/api/players/action" -> action(ex, mapper)
            else -> server.json(ex, 404, """{"error":"not found"}""")
        }
    }

    private fun list(ex: HttpExchange, mapper: ObjectMapper) {
        val root = mapper.createObjectNode()
        val cols = root.putArray("columns")
        for (c in PlayerDirectory.columns()) cols.add(mapper.createObjectNode().put("key", c.key).put("label", c.label))
        val acts = root.putArray("actions")
        for (a in PlayerDirectory.actions()) acts.add(mapper.createObjectNode().put("id", a.id).put("label", a.label).put("danger", a.danger))
        val rows = root.putArray("rows")
        for (r in PlayerDirectory.snapshot()) {
            val o = mapper.createObjectNode()
            for ((k, v) in r) when (v) {
                null -> o.putNull(k)
                is Number -> o.put(k, v.toDouble())
                is Boolean -> o.put(k, v)
                else -> o.put(k, v.toString())
            }
            rows.add(o)
        }
        server.json(ex, 200, root.toString())
    }

    private fun action(ex: HttpExchange, mapper: ObjectMapper) {
        if (ex.requestMethod != "POST") return server.json(ex, 405, """{"error":"use POST"}""")
        val n = runCatching { mapper.readTree(server.body(ex)) }.getOrNull()
            ?: return server.json(ex, 400, """{"error":"bad json"}""")
        val id = n["id"]?.asText()?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"missing id"}""")
        val player = n["player"]?.asText()?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"missing player"}""")
        val args = LinkedHashMap<String, String>()
        n["args"]?.fields()?.forEach { (k, v) -> args[k] = v.asText() }
        val result = PlayerDirectory.runAction(id, player, args)
        server.json(ex, 200, mapper.createObjectNode().put("result", result).toString())
    }
}
