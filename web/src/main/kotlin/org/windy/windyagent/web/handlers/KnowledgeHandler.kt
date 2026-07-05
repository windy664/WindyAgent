package org.windy.windyagent.web.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import org.windy.windyagent.knowledge.KnowledgeManager
import org.windy.windyagent.web.ApiHandler
import org.windy.windyagent.web.DashboardServer

class KnowledgeHandler(
    private val server: DashboardServer,
    private val kb: KnowledgeManager?,
    private val draft: ((String) -> String)?,
    /** AI 正文编辑：(instruction, text) -> 改写后的正文。供 /api/kb/ai 用。 */
    private val aiEdit: ((String, String) -> String)? = null,
    /** 插件命令目录 JSON（实时来自能力注册表）。供 /api/kb/capabilities 只读展示。 */
    private val capabilities: (() -> String)? = null
) : ApiHandler {

    override fun canHandle(path: String): Boolean = path == "/api/kb" ||
        path == "/api/kb/entry" || path == "/api/kb/search" || path == "/api/kb/capabilities" ||
        path == "/api/kb/move" || path == "/api/kb/draft" || path == "/api/kb/ai"

    override fun handle(ex: HttpExchange, path: String, query: Map<String, String>, mapper: ObjectMapper) {
        when (path) {
            "/api/kb" -> kbApi(ex, query, mapper)
            "/api/kb/entry" -> entryApi(ex, query, mapper)
            "/api/kb/search" -> searchApi(ex, query, mapper)
            "/api/kb/capabilities" -> capsApi(ex)
            "/api/kb/move" -> moveApi(ex, mapper)
            "/api/kb/draft" -> draftApi(ex, mapper)
            "/api/kb/ai" -> aiApi(ex, mapper)
        }
    }

    /** 移动条目到新分类（不改正文）。 */
    private fun moveApi(ex: HttpExchange, mapper: ObjectMapper) {
        val m = kb ?: return server.json(ex, 400, """{"error":"knowledge unavailable"}""")
        if (ex.requestMethod != "POST") return server.json(ex, 405, """{"error":"use POST"}""")
        val n = runCatching { mapper.readTree(server.body(ex)) }.getOrNull()
            ?: return server.json(ex, 400, """{"error":"bad json"}""")
        val id = n["id"]?.asText()?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"missing id"}""")
        val folder = n["folder"]?.asText() ?: ""
        val e = m.move(id, folder) ?: return server.json(ex, 404, """{"error":"not found"}""")
        server.json(ex, 200, mapper.createObjectNode().put("ok", true).put("id", e.id).toString())
    }

    /** 单条（含正文），懒加载详情/编辑用。 */
    private fun entryApi(ex: HttpExchange, q: Map<String, String>, mapper: ObjectMapper) {
        val m = kb ?: return server.json(ex, 400, """{"error":"knowledge unavailable"}""")
        val id = q["id"]?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"missing id"}""")
        val e = m.get(id) ?: return server.json(ex, 404, """{"error":"not found"}""")
        server.json(
            ex, 200,
            mapper.createObjectNode().put("id", e.id).put("title", e.title).put("content", e.content)
                .put("folder", e.folder).putPOJO("tags", e.tags).toString()
        )
    }

    /** 全文检索（走知识库稀疏检索），只回命中条目的元数据，不含正文。 */
    private fun searchApi(ex: HttpExchange, q: Map<String, String>, mapper: ObjectMapper) {
        val m = kb ?: return server.json(ex, 400, """{"error":"knowledge unavailable"}""")
        val query = q["q"]?.takeIf { it.isNotBlank() } ?: return server.json(ex, 200, "[]")
        val arr = mapper.createArrayNode()
        m.search(query, 30).forEach { e ->
            arr.addObject().put("id", e.id).put("title", e.title).put("folder", e.folder).putPOJO("tags", e.tags)
        }
        server.json(ex, 200, arr.toString())
    }

    /** 插件命令目录（只读，实时来自能力注册表）。 */
    private fun capsApi(ex: HttpExchange) {
        val fn = capabilities ?: return server.json(ex, 200, "[]")
        server.json(ex, 200, runCatching { fn() }.getOrDefault("[]"))
    }

    /** 预置动作 → 基础指令；custom（无 action）时仅用调用方 instruction。 */
    private fun baseInstruction(action: String): String = when (action) {
        "polish" -> "把正文润色改写得更清晰、通顺、专业，保持原意与信息完整，不要遗漏要点。"
        "continue" -> "顺着正文自然地继续写下去、补全后续内容，只输出【新增的续写部分】，不要重复已有正文。"
        "summarize" -> "把正文提炼成简洁的要点/摘要（用列表或短段落）。"
        "translate" -> "翻译正文：若正文主要是中文则译成英文，否则译成简体中文；只输出译文。"
        else -> ""
    }

    private fun aiApi(ex: HttpExchange, mapper: ObjectMapper) {
        val fn = aiEdit ?: return server.json(ex, 400, """{"error":"AI edit unavailable"}""")
        if (ex.requestMethod != "POST") return server.json(ex, 405, """{"error":"use POST"}""")
        val n = runCatching { mapper.readTree(server.body(ex)) }.getOrNull()
            ?: return server.json(ex, 400, """{"error":"bad json"}""")
        val text = n["text"]?.asText() ?: ""
        val action = n["action"]?.asText()?.trim() ?: ""
        val instruction = n["instruction"]?.asText()?.trim() ?: ""
        val finalInstr = listOf(baseInstruction(action), instruction).filter { it.isNotBlank() }
            .joinToString("。额外要求：")
        if (finalInstr.isBlank()) return server.json(ex, 400, """{"error":"missing action or instruction"}""")
        val out = runCatching { fn(finalInstr, text) }
            .getOrElse { return server.json(ex, 502, """{"error":${server.jstr(it.message ?: "ai edit failed")}}""") }
        server.json(ex, 200, mapper.createObjectNode().put("result", stripFence(out.trim())).toString())
    }

    /** 去掉模型可能多包的 ``` 代码块围栏。 */
    private fun stripFence(s: String): String {
        var t = s.trim()
        if (t.startsWith("```")) {
            t = t.substringAfter('\n', "")
            if (t.endsWith("```")) t = t.substringBeforeLast("```")
        }
        return t.trim()
    }

    private fun kbApi(ex: HttpExchange, q: Map<String, String>, mapper: ObjectMapper) {
        val m = kb ?: return server.json(ex, 400, """{"error":"knowledge unavailable"}""")
        when (ex.requestMethod) {
            "GET" -> {
                // 只回元数据（不含正文），正文点开走 /api/kb/entry；links 供关系图/反链
                val arr = mapper.createArrayNode()
                m.metadata().forEach { e ->
                    arr.addObject().put("id", e.id).put("title", e.title).put("folder", e.folder)
                        .putPOJO("tags", e.tags).putPOJO("links", e.links)
                }
                server.json(ex, 200, arr.toString())
            }
            "POST" -> {
                val n = runCatching { mapper.readTree(server.body(ex)) }.getOrNull() ?: return server.json(ex, 400, """{"error":"bad json"}""")
                val title = n["title"]?.asText()?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"missing title"}""")
                val content = n["content"]?.asText() ?: ""
                val tags = n["tags"]?.mapNotNull { it.asText().takeIf { t -> t.isNotBlank() } } ?: emptyList()
                val folder = n["folder"]?.asText() ?: ""
                val e = m.save(n["id"]?.asText(), title, content, tags, folder)
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
