package org.windy.windyagent.web.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import org.windy.windyagent.skill.SkillRegistry
import org.windy.windyagent.web.ApiHandler
import org.windy.windyagent.web.DashboardServer
import java.util.concurrent.TimeUnit

class SkillHandler(
    private val server: DashboardServer,
    private val skills: SkillRegistry?,
    private val draftSkill: ((String) -> String)?,
    private val syncSkills: (() -> String)?,
    private val bus: org.windy.windyagent.bus.MessageBus?,
    private val timeoutMs: Long,
    private val connectedServers: () -> Set<String>
) : ApiHandler {

    override fun canHandle(path: String): Boolean = path.startsWith("/api/skills")

    override fun handle(ex: HttpExchange, path: String, query: Map<String, String>, mapper: ObjectMapper) {
        when (path) {
            "/api/skills" -> skillsApi(ex, query, mapper)
            "/api/skills/content" -> {
                val handle = query["handle"]?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"missing handle"}""")
                val s = skills ?: return server.json(ex, 503, """{"error":"skills disabled"}""")
                val c = s.read(handle) ?: return server.json(ex, 404, """{"error":"not found"}""")
                val def = s.all().firstOrNull { it.handle.equals(handle, true) }
                server.json(ex, 200, mapper.createObjectNode().put("handle", handle).put("isScript", c.isScript)
                    .put("md", c.md).put("script", c.script).put("scriptFile", c.scriptFile)
                    .put("targets", def?.targets?.joinToString(", ") ?: "").toString())
            }
            "/api/skills/reload" -> { val s = skills ?: return server.json(ex, 503, """{"error":"skills disabled"}"""); server.json(ex, 200, mapper.createObjectNode().put("count", s.reload()).toString()) }
            "/api/skills/sync" -> { server.json(ex, 200, mapper.createObjectNode().put("result", syncSkills?.invoke() ?: "unavailable").toString()) }
            "/api/skills/run" -> skillRunApi(ex, mapper)
            "/api/skills/draft" -> skillDraftApi(ex, mapper)
            else -> server.json(ex, 404, """{"error":"not found"}""")
        }
    }

    private fun skillsApi(ex: HttpExchange, q: Map<String, String>, mapper: ObjectMapper) {
        val s = skills ?: return server.json(ex, 503, """{"error":"skills disabled"}""")
        when (ex.requestMethod) {
            "GET" -> server.json(ex, 200, skillListJson(mapper))
            "POST" -> {
                val n = runCatching { mapper.readTree(server.body(ex)) }.getOrNull() ?: return server.json(ex, 400, """{"error":"bad json"}""")
                val handle = n["handle"]?.asText()?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"missing handle"}""")
                val isScript = n["isScript"]?.asBoolean() ?: false
                var md = n["md"]?.asText() ?: ""
                if (isScript) md = withTargets(md, n["targets"]?.asText() ?: "")
                val count = s.write(handle, md, n["script"]?.asText() ?: "", isScript)
                if (count < 0) return server.json(ex, 400, """{"error":"技能名非法"}""")
                val pushed = if (isScript) syncSkills?.invoke() else null
                server.json(ex, 200, mapper.createObjectNode().put("ok", true).put("count", count).put("pushed", pushed ?: "").toString())
            }
            "DELETE" -> { val handle = q["handle"]?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"missing handle"}"""); server.json(ex, 200, mapper.createObjectNode().put("ok", s.delete(handle)).toString()) }
            else -> server.json(ex, 405, """{"error":"method not allowed"}""")
        }
    }

    private fun skillListJson(mapper: ObjectMapper): String {
        val arr = mapper.createArrayNode()
        skills?.all()?.forEach { d ->
            val o = arr.addObject()
            o.put("name", d.name).put("description", d.description).put("handle", d.handle).put("type", if (d.isScript) "script" else "text")
                .put("targets", if (d.targets.isEmpty()) "all" else d.targets.joinToString(", "))
            val a = o.putArray("args")
            d.args.forEach { arg -> a.addObject().put("name", arg.name).put("type", arg.type).put("description", arg.description) }
        }
        return arr.toString()
    }

    private fun skillRunApi(ex: HttpExchange, mapper: ObjectMapper) {
        if (ex.requestMethod != "POST") return server.json(ex, 405, """{"error":"use POST"}""")
        val n = runCatching { mapper.readTree(server.body(ex)) }.getOrNull() ?: return server.json(ex, 400, """{"error":"bad json"}""")
        val skill = n["skill"]?.asText()?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"missing skill"}""")
        val def = skills?.get(skill)
        if (def != null && !def.isScript) return server.json(ex, 200, mapper.createObjectNode().put("result", def.textOutput()).toString())
        val srv = n["server"]?.asText()?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"脚本技能需选择目标子服"}""")
        val payload = mapper.createObjectNode().put("skill", skill)
        payload.replace("args", n["args"]?.takeIf { it.isObject } ?: mapper.createObjectNode())
        val b = bus ?: return server.json(ex, 503, """{"error":"cross-server bus not enabled"}""")
        if (srv !in connectedServers()) return server.json(ex, 400, """{"error":"server not connected"}""")
        val reply = runCatching { b.dispatch(srv, "run_skill", payload.toString(), timeoutMs).get(timeoutMs + 1000, TimeUnit.MILLISECONDS) }.getOrNull()
        if (reply == null) server.json(ex, 504, """{"error":"timeout"}""")
        else server.json(ex, if (reply.success) 200 else 502, mapper.createObjectNode().put("result", reply.content).toString())
    }

    private fun skillDraftApi(ex: HttpExchange, mapper: ObjectMapper) {
        val d = draftSkill ?: return server.json(ex, 400, """{"error":"AI draft unavailable"}""")
        if (ex.requestMethod != "POST") return server.json(ex, 405, """{"error":"use POST"}""")
        val text = runCatching { mapper.readTree(server.body(ex))["text"]?.asText() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return server.json(ex, 400, """{"error":"empty text"}""")
        val out = runCatching { d(text) }.getOrElse { return server.json(ex, 502, """{"error":${server.jstr(it.message ?: "draft failed")}}""") }
        val md = out.trim().removePrefix("```markdown").removePrefix("```md").removePrefix("```").removeSuffix("```").trim()
        server.json(ex, 200, mapper.createObjectNode().put("md", md).toString())
    }

    private fun withTargets(md: String, targetsCsv: String): String {
        val items = targetsCsv.split(Regex("[,，]")).map { it.trim() }.filter { it.isNotEmpty() && !it.equals("all", true) && it != "*" }
        val lines = md.replace("\r\n", "\n").split("\n").toMutableList()
        if (lines.firstOrNull()?.trim() != "---") { val tline = if (items.isEmpty()) "" else "targets: [${items.joinToString(", ")}]\n"; return "---\n$tline---\n$md" }
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }.let { if (it < 0) -1 else it + 1 }
        if (end < 0) return md
        var i = 1; while (i < end) { if (lines[i].trim().startsWith("targets:")) { lines.removeAt(i) } else i++ }
        if (items.isNotEmpty()) lines.add(1, "targets: [${items.joinToString(", ")}]")
        return lines.joinToString("\n")
    }
}
