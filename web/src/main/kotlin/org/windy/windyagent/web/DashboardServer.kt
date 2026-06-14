package org.windy.windyagent.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.slf4j.LoggerFactory
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.knowledge.KnowledgeManager
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
    private val draft: ((String) -> String)? = null
) {
    private val log = LoggerFactory.getLogger(DashboardServer::class.java)
    private val mapper = ObjectMapper()
    private var server: HttpServer? = null
    private val bundled: ByteArray by lazy {
        javaClass.getResourceAsStream("/dashboard.html")?.use { it.readBytes() } ?: "<h1>dashboard.html missing</h1>".toByteArray()
    }
    /** 优先读数据目录下的 dashboard.html（可热改、刷新即生效），没有才用 jar 内打包的那份。 */
    private fun page(): ByteArray {
        val override = dataDir.resolve("dashboard.html")
        if (Files.exists(override)) return runCatching { Files.readAllBytes(override) }.getOrDefault(bundled)
        return bundled
    }

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
            "/api/chat" -> chatApi(ex)
            "/api/kb" -> kbApi(ex, q)
            "/api/kb/draft" -> draftApi(ex)
            else -> json(ex, 404, """{"error":"unknown api"}""")
        }
    }

    // ---- 聊天（多轮，经载体接到 Agent；会话历史由载体的 SessionManager 维护）----
    private fun chatApi(ex: HttpExchange) {
        val c = chat ?: return json(ex, 400, """{"error":"chat unavailable"}""")
        if (ex.requestMethod != "POST") return json(ex, 405, """{"error":"use POST"}""")
        val n = runCatching { mapper.readTree(body(ex)) }.getOrNull() ?: return json(ex, 400, """{"error":"bad json"}""")
        val msg = n["message"]?.asText()?.takeIf { it.isNotBlank() } ?: return json(ex, 400, """{"error":"empty message"}""")
        val sid = n["session"]?.asText()?.takeIf { it.isNotBlank() } ?: "web-console"
        val reply = runCatching { c(sid, msg) }.getOrElse { "出错：${it.message}" }
        json(ex, 200, mapper.createObjectNode().put("reply", reply).toString())
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

    private fun json(ex: HttpExchange, code: Int, body: String) = respond(ex, code, "application/json; charset=utf-8", body.toByteArray(StandardCharsets.UTF_8))

    private fun respond(ex: HttpExchange, code: Int, contentType: String, body: ByteArray) {
        ex.responseHeaders.add("Content-Type", contentType)
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
