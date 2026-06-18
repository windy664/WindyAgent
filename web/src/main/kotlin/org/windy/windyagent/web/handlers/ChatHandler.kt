package org.windy.windyagent.web.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import org.windy.windyagent.Messages
import org.windy.windyagent.web.ApiHandler
import org.windy.windyagent.web.DashboardServer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * 聊天 API：/api/chat, /api/chat/stream, /api/chat/history
 */
class ChatHandler(
    private val server: DashboardServer,
    private val chat: ((String, String) -> String)?,
    private val dataDir: Path
) : ApiHandler {

    override fun canHandle(path: String): Boolean = path.startsWith("/api/chat")

    override fun handle(ex: HttpExchange, path: String, query: Map<String, String>, mapper: ObjectMapper) {
        when (path) {
            "/api/chat" -> chatApi(ex, mapper)
            "/api/chat/stream" -> chatStreamApi(ex, mapper)
            "/api/chat/history" -> server.json(ex, 200, chatHistory(query["session"]?.takeIf { it.isNotBlank() } ?: "web-console", mapper))
            else -> server.json(ex, 404, """{"error":"not found"}""")
        }
    }

    private fun chatApi(ex: HttpExchange, mapper: ObjectMapper) {
        val c = chat ?: return server.json(ex, 400, """{"error":"chat unavailable"}""")
        if (ex.requestMethod != "POST") return server.json(ex, 405, """{"error":"use POST"}""")
        val n = runCatching { mapper.readTree(server.body(ex)) }.getOrNull() ?: return server.json(ex, 400, """{"error":"bad json"}""")
        val msg = n["message"]?.asText()?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"empty message"}""")
        val sid = n["session"]?.asText()?.takeIf { it.isNotBlank() } ?: "web-console"
        val disp = n["display"]?.asText()?.takeIf { it.isNotBlank() } ?: msg

        if (msg.trim().equals("clear", true) || msg.trim() == "/clear") {
            runCatching { c(sid, msg) }; clearChatLog(sid)
            return server.json(ex, 200, mapper.createObjectNode().put("reply", Messages.t("web.new_chat")).toString())
        }
        val reply = runCatching { c(sid, msg) }.getOrElse { Messages.t("web.error", it.message ?: "") }
        appendChatLog(sid, "u", disp, mapper); appendChatLog(sid, "a", reply, mapper)
        server.json(ex, 200, mapper.createObjectNode().put("reply", reply).toString())
    }

    private fun chatStreamApi(ex: HttpExchange, mapper: ObjectMapper) {
        val c = chat ?: return server.json(ex, 400, """{"error":"chat unavailable"}""")
        if (ex.requestMethod != "POST") return server.json(ex, 405, """{"error":"use POST"}""")
        val n = runCatching { mapper.readTree(server.body(ex)) }.getOrNull() ?: return server.json(ex, 400, """{"error":"bad json"}""")
        val msg = n["message"]?.asText()?.takeIf { it.isNotBlank() } ?: return server.json(ex, 400, """{"error":"empty message"}""")
        val sid = n["session"]?.asText()?.takeIf { it.isNotBlank() } ?: "web-console"

        ex.responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
        ex.responseHeaders.add("Cache-Control", "no-cache")
        ex.responseHeaders.add("Connection", "keep-alive")
        ex.sendResponseHeaders(200, 0)

        val writer = ex.responseBody.bufferedWriter(Charsets.UTF_8)
        try {
            val reply = runCatching { c(sid, msg) }.getOrElse { Messages.t("web.error", it.message ?: "") }
            var i = 0
            while (i < reply.length) {
                val chunk = reply.substring(i, (i + 20).coerceAtMost(reply.length))
                writer.write("data: ${mapper.writeValueAsString(mapper.createObjectNode().put("text", chunk))}\n\n")
                writer.flush(); i += 20
            }
            writer.write("data: [DONE]\n\n"); writer.flush()
            appendChatLog(sid, "u", msg, mapper); appendChatLog(sid, "a", reply, mapper)
        } catch (e: Exception) {
            writer.write("data: ${mapper.writeValueAsString(mapper.createObjectNode().put("error", e.message))}\n\n"); writer.flush()
        } finally { writer.close() }
    }

    // ── 聊天存档 ──

    private val chatLogDir: Path get() = dataDir.resolve("chatlog")
    private fun chatLogFile(session: String): Path = chatLogDir.resolve(session.replace(Regex("[^a-zA-Z0-9_.-]"), "_") + ".jsonl")

    @Synchronized
    private fun appendChatLog(session: String, role: String, text: String, mapper: ObjectMapper) {
        runCatching {
            Files.createDirectories(chatLogDir)
            val line = mapper.createObjectNode().put("role", role).put("text", text).put("ts", System.currentTimeMillis()).toString() + "\n"
            Files.write(chatLogFile(session), line.toByteArray(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        }
    }

    @Synchronized
    private fun clearChatLog(session: String) { runCatching { Files.deleteIfExists(chatLogFile(session)) } }

    private fun chatHistory(session: String, mapper: ObjectMapper): String {
        val f = chatLogFile(session)
        if (!Files.exists(f)) return "[]"
        return runCatching {
            val lines = Files.readAllLines(f, StandardCharsets.UTF_8).filter { it.isNotBlank() }
            "[" + lines.takeLast(200).joinToString(",") + "]"
        }.getOrDefault("[]")
    }
}
