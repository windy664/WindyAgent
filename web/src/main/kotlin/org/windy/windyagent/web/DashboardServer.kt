package org.windy.windyagent.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.slf4j.LoggerFactory
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.knowledge.KnowledgeManager
import org.windy.windyagent.ops.ScheduledTask
import org.windy.windyagent.ops.TaskScheduler
import org.windy.windyagent.safety.PendingApprovals
import org.windy.windyagent.skill.SkillRegistry
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * AI 管理控制台后端 —— **平台无关**：JDK 内置 HttpServer（零依赖），只靠注入的依赖工作，
 * 不碰任何具体载体类，故 Velocity / 未来 Bukkit-hub 都能挂载。
 *  - 聊天(多轮)：[chat]（sessionId,message)->回复，由载体接到 Agent + 会话管理。
 *  - 知识库：[kb] 增删改 + AI 起草 [draft]。
 *  - 行为看板：经 [bus] 把 behavior_* 动作派发到子服。
 * 安全：以 api 前缀的接口必须带 token（query ?token= 或头 X-Token）；默认绑 127.0.0.1。
 */
class DashboardServer(
    private val host: String,
    private val port: Int,
    private val token: String,
    private val bus: MessageBus?,
    private val timeoutMs: Long,
    private val dataDir: Path,
    private val connectedServers: () -> Set<String>,
    private val chat: ((String, String) -> String)? = null,
    private val kb: KnowledgeManager? = null,
    private val draft: ((String) -> String)? = null,
    private val alerts: AlertCenter? = null,
    /** 当前各子服健康快照 JSON（哨兵提供）；null=哨兵未启用，返回空数组。 */
    private val health: (() -> String)? = null,
    private val scheduler: TaskScheduler? = null,
    /** AI 整理：把一句话需求润成清晰任务描述（定时 AI 任务用）。 */
    private val refine: ((String) -> String)? = null,
    private val pending: PendingApprovals? = null,
    /** 脚本编译：把需求描述(desc) + 默认子服(server) → LLM 编译成步骤 JSON 数组字符串。 */
    private val compileScript: ((String, String) -> String)? = null,
    /** 技能起草：把服主一句话需求 → LLM 生成一份「纯文字技能」的 SKILL.md 文本。 */
    private val draftSkill: ((String) -> String)? = null,
    /** 中心权威技能库（本地文件管理：list/get/save/delete/reload + 测试文字技能）。 */
    private val skills: SkillRegistry? = null,
    /** 脚本技能下发：把中心库脚本技能推到命中的在线子服，返回结果摘要。 */
    private val syncSkills: (() -> String)? = null
) {
    private val log = LoggerFactory.getLogger(DashboardServer::class.java)
    private val mapper = ObjectMapper()
    private var server: HttpServer? = null
    private val bundled: ByteArray by lazy {
        javaClass.getResourceAsStream("/dashboard.html")?.use { it.readBytes() } ?: "<h1>dashboard.html missing</h1>".toByteArray()
    }
    /** 前端只认 jar 内打包的那份（换 jar 即更新）；不再读数据目录覆盖文件，避免旧覆盖文件盖掉新 jar。 */
    private fun page(): ByteArray = bundled

    fun start() {
        val s = HttpServer.create(InetSocketAddress(host, port), 0)
        s.executor = Executors.newFixedThreadPool(6) { r -> Thread(r, "windyagent-web").apply { isDaemon = true } }
        s.createContext("/") { ex -> handle(ex) }
        s.start()
        server = s
        if (token.isBlank()) log.warn("管理控制台 token 为空——接口无鉴权，强烈建议设 web.token 并仅绑 127.0.0.1")
        log.info("管理控制台已启动：http://{}:{}/", if (host == "0.0.0.0") "<本机IP>" else host, port)
    }

    fun stop() { server?.stop(0); server = null }

    private fun handle(ex: HttpExchange) {
        try {
            val path = ex.requestURI.path
            val q = parseQuery(ex.requestURI.query)
            when {
                path == "/" || path == "/index.html" -> respond(ex, 200, "text/html; charset=utf-8", page())
                path.startsWith("/api/") -> {
                    if (token.isNotEmpty() && q["token"] != token && ex.requestHeaders.getFirst("X-Token") != token) {
                        json(ex, 401, """{"error":"unauthorized"}"""); return
                    }
                    api(ex, path, q)
                }
                else -> json(ex, 404, """{"error":"not found"}""")
            }
        } catch (e: Exception) {
            runCatching { json(ex, 500, """{"error":${jstr(e.message ?: "error")}}""") }
        } finally { ex.close() }
    }

    private fun body(ex: HttpExchange): String = ex.requestBody.use { it.readBytes().toString(StandardCharsets.UTF_8) }

    private fun api(ex: HttpExchange, path: String, q: Map<String, String>) {
        when (path) {
            "/api/servers" -> json(ex, 200, "[" + connectedServers().sorted().joinToString(",") { jstr(it) } + "]")
            "/api/stats" -> proxy(ex, q, "behavior_stats", "{}")
            "/api/segments" -> proxy(ex, q, "behavior_segments", "{}")
            "/api/player" -> {
                val name = q["name"]?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"missing name"}""")
                proxy(ex, q, "behavior_player", """{"player":${jstr(name)}}""")
            }
            "/api/words" -> {
                val src = q["source"]?.takeIf { it.isNotBlank() } ?: "cmd"
                val lim = q["limit"]?.toIntOrNull() ?: 80
                proxy(ex, q, "behavior_words", """{"source":${jstr(src)},"limit":$lim}""")
            }
            "/api/board" -> proxy(ex, q, "behavior_board", "{}")
            "/api/alerts" -> json(ex, 200, alerts?.json() ?: "[]")
            "/api/health" -> json(ex, 200, runCatching { health?.invoke() }.getOrNull() ?: "[]")
            // Forge/NeoForge 专属（前端仅对该类子服显示入口）：模组清单 / 分维度 TPS（回的是纯文本，包成 {text}）
            "/api/mods" -> proxyText(ex, q, "server_mods", "{}")
            "/api/dimtps" -> proxyText(ex, q, "dimension_tps", "{}")
            "/api/serverdetail" -> proxy(ex, q, "server_detail", "{}")
            "/api/tasks" -> tasksApi(ex, q)
            "/api/tasks/run" -> { val s = scheduler; val id = q["id"]; if (s == null || id.isNullOrBlank()) json(ex, 400, """{"error":"bad request"}""") else json(ex, 200, mapper.createObjectNode().put("result", s.runNow(id)).toString()) }
            "/api/tasks/toggle" -> { val s = scheduler; val id = q["id"]; if (s == null || id.isNullOrBlank()) json(ex, 400, """{"error":"bad request"}""") else { val t = s.toggle(id); json(ex, 200, mapper.createObjectNode().put("ok", t != null).put("enabled", t?.enabled ?: false).toString()) } }
            "/api/tasks/refine" -> refineApi(ex)
            "/api/tasks/compile" -> compileApi(ex)
            "/api/approvals" -> approvalsApi(ex)
            "/api/approvals/approve" -> { val p = pending; val id = q["id"]; if (p == null || id.isNullOrBlank()) json(ex, 400, """{"error":"bad request"}""") else json(ex, 200, mapper.createObjectNode().put("result", p.approve(id) ?: "单号不存在或已过期").toString()) }
            "/api/approvals/deny" -> { val p = pending; val id = q["id"]; if (p == null || id.isNullOrBlank()) json(ex, 400, """{"error":"bad request"}""") else json(ex, 200, mapper.createObjectNode().put("desc", p.deny(id) ?: "单号不存在").toString()) }
            "/api/chat" -> chatApi(ex)
            "/api/chat/history" -> json(ex, 200, chatHistory(q["session"]?.takeIf { it.isNotBlank() } ?: "web-console"))
            "/api/kb" -> kbApi(ex, q)
            "/api/kb/draft" -> draftApi(ex)
            // 技能（Groovy skill）：管理**中心权威库**（本地文件读写 + 热重载 + 下发子服 + 测试运行）
            "/api/skills" -> skillsApi(ex, q)
            "/api/skills/content" -> {
                val handle = q["handle"]?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"missing handle"}""")
                val s = skills ?: return json(ex, 503, """{"error":"skills disabled"}""")
                val c = s.read(handle) ?: return json(ex, 404, """{"error":"not found"}""")
                val def = s.all().firstOrNull { it.handle.equals(handle, true) }
                json(ex, 200, mapper.createObjectNode().put("handle", handle).put("isScript", c.isScript)
                    .put("md", c.md).put("script", c.script).put("scriptFile", c.scriptFile)
                    .put("targets", def?.targets?.joinToString(", ") ?: "").toString())
            }
            "/api/skills/reload" -> {
                val s = skills ?: return json(ex, 503, """{"error":"skills disabled"}""")
                json(ex, 200, mapper.createObjectNode().put("count", s.reload()).toString())
            }
            "/api/skills/sync" -> skillSyncApi(ex)
            "/api/skills/run" -> skillRunApi(ex)
            "/api/skills/draft" -> skillDraftApi(ex)
            else -> json(ex, 404, """{"error":"unknown api"}""")
        }
    }

    // ---- 聊天（多轮，经载体接到 Agent；AI 上下文由载体 SessionManager 维护(限 max-history 轮)）----
    // 另把对话存盘到 chatlog/<会话>.jsonl，供 /api/chat/history 跨刷新跨设备回放（与 AI 上下文独立、可更长）。
    private fun chatApi(ex: HttpExchange) {
        val c = chat ?: return json(ex, 400, """{"error":"chat unavailable"}""")
        if (ex.requestMethod != "POST") return json(ex, 405, """{"error":"use POST"}""")
        val n = runCatching { mapper.readTree(body(ex)) }.getOrNull() ?: return json(ex, 400, """{"error":"bad json"}""")
        val msg = n["message"]?.asText()?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"empty message"}""")
        val sid = n["session"]?.asText()?.takeIf { it.isNotBlank() } ?: "web-console"
        // 持久化展示用原文（前端发来的 display 去掉了"默认子服"前缀）；缺省退回 message。
        val disp = n["display"]?.asText()?.takeIf { it.isNotBlank() } ?: msg
        // "/clear" 类清空指令：清后端会话 + 删存档，不计入历史
        if (msg.trim().equals("clear", true) || msg.trim() == "/clear") {
            runCatching { c(sid, msg) }; clearChatLog(sid)
            return json(ex, 200, mapper.createObjectNode().put("reply", "已开新对话").toString())
        }
        val reply = runCatching { c(sid, msg) }.getOrElse { "出错：${it.message}" }
        appendChatLog(sid, "u", disp); appendChatLog(sid, "a", reply)
        json(ex, 200, mapper.createObjectNode().put("reply", reply).toString())
    }

    // ---- 聊天存档（jsonl，一行一条 {role,text,ts}）----
    private val chatLogDir: Path get() = dataDir.resolve("chatlog")
    private fun chatLogFile(session: String): Path =
        chatLogDir.resolve(session.replace(Regex("[^a-zA-Z0-9_.-]"), "_") + ".jsonl")

    @Synchronized
    private fun appendChatLog(session: String, role: String, text: String) {
        runCatching {
            Files.createDirectories(chatLogDir)
            val line = mapper.createObjectNode().put("role", role).put("text", text).put("ts", System.currentTimeMillis()).toString() + "\n"
            Files.write(chatLogFile(session), line.toByteArray(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
        }.onFailure { log.warn("聊天存档失败：{}", it.message) }
    }

    @Synchronized
    private fun clearChatLog(session: String) { runCatching { Files.deleteIfExists(chatLogFile(session)) } }

    /** 读回某会话最近 N 条，拼成 JSON 数组（每行本就是合法 JSON 对象，直接拼）。 */
    private fun chatHistory(session: String): String {
        val f = chatLogFile(session)
        if (!Files.exists(f)) return "[]"
        return runCatching {
            val lines = Files.readAllLines(f, StandardCharsets.UTF_8).filter { it.isNotBlank() }
            "[" + lines.takeLast(200).joinToString(",") + "]"
        }.getOrDefault("[]")
    }

    // ---- 知识库 CRUD ----
    private fun kbApi(ex: HttpExchange, q: Map<String, String>) {
        val m = kb ?: return json(ex, 400, """{"error":"knowledge unavailable"}""")
        when (ex.requestMethod) {
            "GET" -> {
                val arr = mapper.createArrayNode()
                m.list().forEach { e ->
                    arr.addObject().put("id", e.id).put("title", e.title).put("content", e.content).putPOJO("tags", e.tags)
                }
                json(ex, 200, arr.toString())
            }
            "POST" -> {
                val n = runCatching { mapper.readTree(body(ex)) }.getOrNull() ?: return json(ex, 400, """{"error":"bad json"}""")
                val title = n["title"]?.asText()?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"missing title"}""")
                val content = n["content"]?.asText() ?: ""
                val tags = n["tags"]?.mapNotNull { it.asText().takeIf { t -> t.isNotBlank() } } ?: emptyList()
                val e = m.save(n["id"]?.asText(), title, content, tags)
                json(ex, 200, mapper.createObjectNode().put("ok", true).put("id", e.id).toString())
            }
            "DELETE" -> {
                val id = q["id"]?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"missing id"}""")
                json(ex, 200, mapper.createObjectNode().put("ok", m.delete(id)).toString())
            }
            else -> json(ex, 405, """{"error":"method not allowed"}""")
        }
    }

    // ---- AI 起草：腐竹说人话 → LLM 整理成 {title,content,tags}（人确认后才 POST /api/kb 落库）----
    private fun draftApi(ex: HttpExchange) {
        val d = draft ?: return json(ex, 400, """{"error":"AI draft unavailable"}""")
        if (ex.requestMethod != "POST") return json(ex, 405, """{"error":"use POST"}""")
        val text = runCatching { mapper.readTree(body(ex))["text"]?.asText() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return json(ex, 400, """{"error":"empty text"}""")
        val out = runCatching { d(text) }.getOrElse { return json(ex, 502, """{"error":${jstr(it.message ?: "draft failed")}}""") }
        val s = out.indexOf('{'); val e = out.lastIndexOf('}')
        json(ex, 200, if (s >= 0 && e > s) out.substring(s, e + 1) else """{"title":"","content":${jstr(out)},"tags":[]}""")
    }

    // ---- 技能（Groovy skill）管理：直接操作**中心权威库**本地文件（list / save / delete）----
    private fun skillsApi(ex: HttpExchange, q: Map<String, String>) {
        val s = skills ?: return json(ex, 503, """{"error":"skills disabled"}""")
        when (ex.requestMethod) {
            "GET" -> json(ex, 200, skillListJson())
            "POST" -> {
                val n = runCatching { mapper.readTree(body(ex)) }.getOrNull() ?: return json(ex, 400, """{"error":"bad json"}""")
                val handle = n["handle"]?.asText()?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"missing handle"}""")
                val isScript = n["isScript"]?.asBoolean() ?: false
                var md = n["md"]?.asText() ?: ""
                // 脚本技能：把「目标子服」写进 frontmatter targets（空=全部）。文字技能无目标。
                if (isScript) md = withTargets(md, n["targets"]?.asText() ?: "")
                val count = s.write(handle, md, n["script"]?.asText() ?: "", isScript)
                if (count < 0) return json(ex, 400, """{"error":"技能名非法"}""")
                // 存完即下发：脚本技能推到命中的在线子服执行（文字技能不下发，中心本地执行）
                val pushed = if (isScript) syncSkills?.invoke() else null
                json(ex, 200, mapper.createObjectNode().put("ok", true).put("count", count)
                    .put("pushed", pushed ?: "").toString())
            }
            "DELETE" -> {
                val handle = q["handle"]?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"missing handle"}""")
                json(ex, 200, mapper.createObjectNode().put("ok", s.delete(handle)).toString())
            }
            else -> json(ex, 405, """{"error":"method not allowed"}""")
        }
    }

    /** 中心库技能清单 JSON：每项 name/description/handle/type/targets + 参数声明。 */
    private fun skillListJson(): String {
        val arr = mapper.createArrayNode()
        skills?.all()?.forEach { d ->
            val o = arr.addObject()
            o.put("name", d.name).put("description", d.description)
                .put("handle", d.handle).put("type", if (d.isScript) "script" else "text")
                .put("targets", if (d.targets.isEmpty()) "all" else d.targets.joinToString(", "))
            val a = o.putArray("args")
            d.args.forEach { arg -> a.addObject().put("name", arg.name).put("type", arg.type).put("description", arg.description) }
        }
        return arr.toString()
    }

    /** 把脚本技能下发到所有命中的在线子服（WebUI「立即下发」）。 */
    private fun skillSyncApi(ex: HttpExchange) {
        val sync = syncSkills ?: return json(ex, 503, """{"error":"cross-server sync unavailable"}""")
        json(ex, 200, mapper.createObjectNode().put("result", sync.invoke()).toString())
    }

    /** 在 SKILL.md 的 frontmatter 中设置 `targets:` 行（csv→列表，空/all/* 则删除该行=全部子服）。 */
    private fun withTargets(md: String, targetsCsv: String): String {
        val items = targetsCsv.split(Regex("[,，]")).map { it.trim() }.filter { it.isNotEmpty() && !it.equals("all", true) && it != "*" }
        val lines = md.replace("\r\n", "\n").split("\n").toMutableList()
        if (lines.firstOrNull()?.trim() != "---") {
            // 无 frontmatter：补一段（write 还会补 script 字段）
            val tline = if (items.isEmpty()) "" else "targets: [${items.joinToString(", ")}]\n"
            return "---\n$tline---\n$md"
        }
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }.let { if (it < 0) -1 else it + 1 }
        if (end < 0) return md
        // 删掉已有 targets 行
        var i = 1
        while (i < end) { if (lines[i].trim().startsWith("targets:")) { lines.removeAt(i) } else i++ }
        if (items.isNotEmpty()) lines.add(1, "targets: [${items.joinToString(", ")}]")
        return lines.joinToString("\n")
    }

    /**
     * WebUI 里测试运行一个技能：POST {server, skill, args}。
     * 文字技能=中心直接出正文（无需子服）；脚本技能=派发 run_skill 到选中的在线子服。
     */
    private fun skillRunApi(ex: HttpExchange) {
        if (ex.requestMethod != "POST") return json(ex, 405, """{"error":"use POST"}""")
        val n = runCatching { mapper.readTree(body(ex)) }.getOrNull() ?: return json(ex, 400, """{"error":"bad json"}""")
        val skill = n["skill"]?.asText()?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"missing skill"}""")
        // 文字（流程）技能：中心本地直接返回正文，不需要子服
        val def = skills?.get(skill)
        if (def != null && !def.isScript) return json(ex, 200, mapper.createObjectNode().put("result", def.textOutput()).toString())
        val server = n["server"]?.asText()?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"脚本技能需选择目标子服"}""")
        val payload = mapper.createObjectNode().put("skill", skill)
        payload.replace("args", n["args"]?.takeIf { it.isObject } ?: mapper.createObjectNode())
        // 子服 run_skill 回的是纯文本结果，包成 {result} 给前端
        val b = bus ?: return json(ex, 503, """{"error":"cross-server bus not enabled"}""")
        if (server !in connectedServers()) return json(ex, 400, """{"error":"server not connected"}""")
        val reply = runCatching { b.dispatch(server, "run_skill", payload.toString(), timeoutMs).get(timeoutMs + 1000, TimeUnit.MILLISECONDS) }.getOrNull()
        if (reply == null) json(ex, 504, """{"error":"timeout"}""")
        else json(ex, if (reply.success) 200 else 502, mapper.createObjectNode().put("result", reply.content).toString())
    }

    /** 技能 AI 起草：POST {text} → {md: 生成的 SKILL.md}（纯文字技能，人确认后再保存）。 */
    private fun skillDraftApi(ex: HttpExchange) {
        val d = draftSkill ?: return json(ex, 400, """{"error":"AI draft unavailable"}""")
        if (ex.requestMethod != "POST") return json(ex, 405, """{"error":"use POST"}""")
        val text = runCatching { mapper.readTree(body(ex))["text"]?.asText() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return json(ex, 400, """{"error":"empty text"}""")
        val out = runCatching { d(text) }.getOrElse { return json(ex, 502, """{"error":${jstr(it.message ?: "draft failed")}}""") }
        // 去掉可能的 ```markdown 围栏，只留 SKILL.md 正文
        val md = out.trim().removePrefix("```markdown").removePrefix("```md").removePrefix("```").removeSuffix("```").trim()
        json(ex, 200, mapper.createObjectNode().put("md", md).toString())
    }

    /** 同 proxy，但目标 server 由调用方显式给出（POST 时 server 在请求体里，不在 query）。 */
    private fun dispatchTo(ex: HttpExchange, server: String, action: String, args: String) {
        val b = bus ?: return json(ex, 503, """{"error":"cross-server bus not enabled"}""")
        if (server !in connectedServers()) return json(ex, 400, """{"error":"server not connected"}""")
        val reply = runCatching { b.dispatch(server, action, args, timeoutMs).get(timeoutMs + 1000, TimeUnit.MILLISECONDS) }.getOrNull()
        if (reply == null) json(ex, 504, """{"error":"timeout"}""")
        else if (!reply.success) json(ex, 502, """{"error":${jstr(reply.content)}}""")
        else json(ex, 200, reply.content)
    }

    /** 把请求转成总线动作派发到子服，子服回的就是 JSON，直接透传。 */
    private fun proxy(ex: HttpExchange, q: Map<String, String>, action: String, args: String) {
        val b = bus ?: return json(ex, 503, """{"error":"cross-server bus not enabled"}""")
        val server = q["server"]?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"missing server"}""")
        if (server !in connectedServers()) return json(ex, 400, """{"error":"server not connected"}""")
        val reply = runCatching { b.dispatch(server, action, args, timeoutMs).get(timeoutMs + 1000, TimeUnit.MILLISECONDS) }.getOrNull()
        if (reply == null) json(ex, 504, """{"error":"timeout"}""")
        else if (!reply.success) json(ex, 502, """{"error":${jstr(reply.content)}}""")
        else json(ex, 200, reply.content)
    }

    /** 审批：待审项 + 历史，一次拉齐。 */
    private fun approvalsApi(ex: HttpExchange) {
        val p = pending ?: return json(ex, 200, """{"pending":[],"history":[],"ttlMs":0}""")
        val now = System.currentTimeMillis()
        val root = mapper.createObjectNode()
        val pend = root.putArray("pending")
        p.items().forEach { it0 -> pend.addObject().put("id", it0.id).put("desc", it0.desc).put("at", it0.at).put("remainMs", (p.ttl() - (now - it0.at)).coerceAtLeast(0)) }
        val hist = root.putArray("history")
        p.historyItems().forEach { h -> hist.addObject().put("id", h.id).put("desc", h.desc).put("decision", h.decision).put("result", h.result).put("at", h.at) }
        root.put("ttlMs", p.ttl())
        json(ex, 200, root.toString())
    }

    /** 脚本编译：POST {description, server} → 步骤 JSON 数组（直接返回数组体）。 */
    private fun compileApi(ex: HttpExchange) {
        val c = compileScript ?: return json(ex, 400, """{"error":"compile unavailable"}""")
        if (ex.requestMethod != "POST") return json(ex, 405, """{"error":"use POST"}""")
        val n = runCatching { mapper.readTree(body(ex)) }.getOrNull() ?: return json(ex, 400, """{"error":"bad json"}""")
        val desc = n["description"]?.asText()?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"empty"}""")
        val server = n["server"]?.asText() ?: ""
        val out = runCatching { c(desc, server) }.getOrElse { "[]" }
        respond(ex, 200, "application/json; charset=utf-8", out.toByteArray(StandardCharsets.UTF_8))
    }

    /** AI 整理任务描述：POST {text} → {text: 整理后}。 */
    private fun refineApi(ex: HttpExchange) {
        val r = refine ?: return json(ex, 400, """{"error":"refine unavailable"}""")
        if (ex.requestMethod != "POST") return json(ex, 405, """{"error":"use POST"}""")
        val n = runCatching { mapper.readTree(body(ex)) }.getOrNull() ?: return json(ex, 400, """{"error":"bad json"}""")
        val text = n["text"]?.asText()?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"empty"}""")
        val out = runCatching { r(text) }.getOrElse { "出错：${it.message}" }
        json(ex, 200, mapper.createObjectNode().put("text", out).toString())
    }

    // ---- 定时任务 CRUD（手工解析，web 侧无 jackson-kotlin module）----
    private fun tasksApi(ex: HttpExchange, q: Map<String, String>) {
        val s = scheduler ?: return json(ex, 400, """{"error":"scheduler unavailable"}""")
        when (ex.requestMethod) {
            "GET" -> json(ex, 200, s.toJson())
            "POST" -> {
                val n = runCatching { mapper.readTree(body(ex)) }.getOrNull() ?: return json(ex, 400, """{"error":"bad json"}""")
                val t = ScheduledTask(
                    id = n["id"]?.asText() ?: "",
                    name = n["name"]?.asText()?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"missing name"}"""),
                    enabled = n["enabled"]?.asBoolean() ?: true,
                    action = n["action"]?.asText() ?: "broadcast",
                    target = n["target"]?.asText() ?: "",
                    payload = n["payload"]?.asText() ?: "",
                    type = n["type"]?.asText() ?: "interval",
                    intervalMin = n["intervalMin"]?.asInt() ?: 60,
                    time = n["time"]?.asText() ?: "12:00",
                    days = n["days"]?.mapNotNull { it.asInt() } ?: emptyList(),
                    script = n["script"]?.mapNotNull { st ->
                        val a = st["action"]?.asText()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        org.windy.windyagent.ops.TaskStep(a, st["target"]?.asText() ?: "", st["payload"]?.asText() ?: "")
                    } ?: emptyList()
                )
                val saved = s.upsert(t)
                json(ex, 200, mapper.createObjectNode().put("ok", true).put("id", saved.id).toString())
            }
            "DELETE" -> {
                val id = q["id"]?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"missing id"}""")
                json(ex, 200, mapper.createObjectNode().put("ok", s.delete(id)).toString())
            }
            else -> json(ex, 405, """{"error":"method not allowed"}""")
        }
    }

    /** 同 proxy，但子服回的是纯文本，包成 {"text":...} 供前端读取（避免把文本当 JSON 解析）。 */
    private fun proxyText(ex: HttpExchange, q: Map<String, String>, action: String, args: String) {
        val b = bus ?: return json(ex, 503, """{"error":"cross-server bus not enabled"}""")
        val server = q["server"]?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"missing server"}""")
        if (server !in connectedServers()) return json(ex, 400, """{"error":"server not connected"}""")
        val reply = runCatching { b.dispatch(server, action, args, timeoutMs).get(timeoutMs + 1000, TimeUnit.MILLISECONDS) }.getOrNull()
        if (reply == null) json(ex, 504, """{"error":"timeout"}""")
        else json(ex, if (reply.success) 200 else 502, mapper.createObjectNode().put("text", reply.content).toString())
    }

    private fun json(ex: HttpExchange, code: Int, body: String) = respond(ex, code, "application/json; charset=utf-8", body.toByteArray(StandardCharsets.UTF_8))

    private fun respond(ex: HttpExchange, code: Int, contentType: String, body: ByteArray) {
        ex.responseHeaders.add("Content-Type", contentType)
        // 禁缓存：API 是实时数据本就不该缓存；HTML 不缓存则换 jar / 热改 dataDir/dashboard.html 刷新即生效，
        // 不会再出现"浏览器拿旧页、看不到新功能"的坑。控制台是本机轻量页，不缓存无性能负担。
        ex.responseHeaders.add("Cache-Control", "no-cache, no-store, must-revalidate")
        ex.sendResponseHeaders(code, body.size.toLong())
        ex.responseBody.use { it.write(body) }
    }

    private fun parseQuery(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        return query.split("&").mapNotNull {
            val i = it.indexOf('='); if (i < 0) return@mapNotNull null
            dec(it.substring(0, i)) to dec(it.substring(i + 1))
        }.toMap()
    }

    private fun dec(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun jstr(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ") + "\""
}
