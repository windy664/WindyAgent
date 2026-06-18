package org.windy.windyagent.web.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import org.windy.windyagent.Messages
import org.windy.windyagent.ops.ScheduledTask
import org.windy.windyagent.ops.TaskScheduler
import org.windy.windyagent.ops.TaskStep
import org.windy.windyagent.web.ApiHandler
import org.windy.windyagent.web.DashboardServer

class TaskHandler(
    private val server: DashboardServer,
    private val scheduler: TaskScheduler?,
    private val refine: ((String) -> String)?,
    private val compileScript: ((String, String) -> String)?
) : ApiHandler {

    override fun canHandle(path: String): Boolean = path.startsWith("/api/tasks")

    override fun handle(ex: HttpExchange, path: String, query: Map<String, String>, mapper: ObjectMapper) {
        when (path) {
            "/api/tasks" -> tasksApi(ex, query, mapper)
            "/api/tasks/run" -> { val s = scheduler; val id = query["id"]; if (s == null || id.isNullOrBlank()) server.json(ex, 400, """{"error":"bad request"}""") else server.json(ex, 200, mapper.createObjectNode().put("result", s.runNow(id)).toString()) }
            "/api/tasks/toggle" -> { val s = scheduler; val id = query["id"]; if (s == null || id.isNullOrBlank()) server.json(ex, 400, """{"error":"bad request"}""") else { val t = s.toggle(id); server.json(ex, 200, mapper.createObjectNode().put("ok", t != null).put("enabled", t?.enabled ?: false).toString()) } }
            "/api/tasks/refine" -> refineApi(ex, mapper)
            "/api/tasks/compile" -> compileApi(ex, mapper)
            else -> server.json(ex, 404, """{"error":"not found"}""")
        }
    }

    private fun tasksApi(ex: HttpExchange, q: Map<String, String>, mapper: ObjectMapper) {
        val s = scheduler ?: return server.json(ex, 400, """{"error":"scheduler unavailable"}""")
        when (ex.requestMethod) {
            "GET" -> server.json(ex, 200, s.toJson())
            "POST" -> {
                val n = runCatching { mapper.readTree(server.body(ex)) }.getOrNull() ?: return server.json(ex, 400, """{"error":"bad json"}""")
                val t = ScheduledTask(
                    id = n["id"]?.asText() ?: "", name = n["name"]?.asText()?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"missing name"}"""),
                    enabled = n["enabled"]?.asBoolean() ?: true, action = n["action"]?.asText() ?: "broadcast", target = n["target"]?.asText() ?: "",
                    payload = n["payload"]?.asText() ?: "", type = n["type"]?.asText() ?: "interval", intervalMin = n["intervalMin"]?.asInt() ?: 60,
                    time = n["time"]?.asText() ?: "12:00", days = n["days"]?.mapNotNull { it.asInt() } ?: emptyList(),
                    script = n["script"]?.mapNotNull { st -> val a = st["action"]?.asText()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null; TaskStep(a, st["target"]?.asText() ?: "", st["payload"]?.asText() ?: "") } ?: emptyList()
                )
                server.json(ex, 200, mapper.createObjectNode().put("ok", true).put("id", s.upsert(t).id).toString())
            }
            "DELETE" -> { val id = q["id"]?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"missing id"}"""); server.json(ex, 200, mapper.createObjectNode().put("ok", s.delete(id)).toString()) }
            else -> server.json(ex, 405, """{"error":"method not allowed"}""")
        }
    }

    private fun refineApi(ex: HttpExchange, mapper: ObjectMapper) {
        val r = refine ?: return server.json(ex, 400, """{"error":"refine unavailable"}""")
        if (ex.requestMethod != "POST") return server.json(ex, 405, """{"error":"use POST"}""")
        val n = runCatching { mapper.readTree(server.body(ex)) }.getOrNull() ?: return server.json(ex, 400, """{"error":"bad json"}""")
        val text = n["text"]?.asText()?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"empty"}""")
        val out = runCatching { r(text) }.getOrElse { Messages.t("web.error", it.message ?: "") }
        server.json(ex, 200, mapper.createObjectNode().put("text", out).toString())
    }

    private fun compileApi(ex: HttpExchange, mapper: ObjectMapper) {
        val c = compileScript ?: return server.json(ex, 400, """{"error":"compile unavailable"}""")
        if (ex.requestMethod != "POST") return server.json(ex, 405, """{"error":"use POST"}""")
        val n = runCatching { mapper.readTree(server.body(ex)) }.getOrNull() ?: return server.json(ex, 400, """{"error":"bad json"}""")
        val desc = n["description"]?.asText()?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"empty"}""")
        val srv = n["server"]?.asText() ?: ""
        server.respond(ex, 200, "application/json; charset=utf-8", (runCatching { c(desc, srv) }.getOrElse { "[]" }).toByteArray(Charsets.UTF_8))
    }
}
