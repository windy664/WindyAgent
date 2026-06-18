package org.windy.windyagent.web.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import org.windy.windyagent.knowledge.KnowledgeManager
import org.windy.windyagent.web.ApiHandler
import org.windy.windyagent.web.DashboardServer

class KnowledgeHandler(
    private val server: DashboardServer,
    private val kb: KnowledgeManager?,
    private val draft: ((String) -> String)?
) : ApiHandler {

    override fun canHandle(path: String): Boolean = path == "/api/kb" || path == "/api/kb/draft"

    override fun handle(ex: HttpExchange, path: String, query: Map<String, String>, mapper: ObjectMapper) {
        when (path) {
            "/api/kb" -> kbApi(ex, query, mapper)
            "/api/kb/draft" -> draftApi(ex, mapper)
        }
    }

    private fun kbApi(ex: HttpExchange, q: Map<String, String>, mapper: ObjectMapper) {
        val m = kb ?: return server.json(ex, 400, """{"error":"knowledge unavailable"}""")
        when (ex.requestMethod) {
            "GET" -> {
                val arr = mapper.createArrayNode()
                m.list().forEach { e -> arr.addObject().put("id", e.id).put("title", e.title).put("content", e.content).putPOJO("tags", e.tags) }
                server.json(ex, 200, arr.toString())
            }
            "POST" -> {
                val n = runCatching { mapper.readTree(server.body(ex)) }.getOrNull() ?: return server.json(ex, 400, """{"error":"bad json"}""")
                val title = n["title"]?.asText()?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"missing title"}""")
                val content = n["content"]?.asText() ?: ""
                val tags = n["tags"]?.mapNotNull { it.asText().takeIf { t -> t.isNotBlank() } } ?: emptyList()
                val e = m.save(n["id"]?.asText(), title, content, tags)
                server.json(ex, 200, mapper.createObjectNode().put("ok", true).put("id", e.id).toString())
            }
            "DELETE" -> {
                val id = q["id"]?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"missing id"}""")
                server.json(ex, 200, mapper.createObjectNode().put("ok", m.delete(id)).toString())
            }
            else -> server.json(ex, 405, """{"error":"method not allowed"}""")
        }
    }

    private fun draftApi(ex: HttpExchange, mapper: ObjectMapper) {
        val d = draft ?: return server.json(ex, 400, """{"error":"AI draft unavailable"}""")
        if (ex.requestMethod != "POST") return server.json(ex, 405, """{"error":"use POST"}""")
        val text = runCatching { mapper.readTree(server.body(ex))["text"]?.asText() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return server.json(ex, 400, """{"error":"empty text"}""")
        val out = runCatching { d(text) }.getOrElse { return server.json(ex, 502, """{"error":${server.jstr(it.message ?: "draft failed")}}""") }
        val s = out.indexOf('{'); val e = out.lastIndexOf('}')
        server.json(ex, 200, if (s >= 0 && e > s) out.substring(s, e + 1) else """{"title":"","content":${server.jstr(out)},"tags":[]}""")
    }
}
