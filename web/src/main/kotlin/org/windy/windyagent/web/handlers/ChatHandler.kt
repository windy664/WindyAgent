package org.windy.windyagent.web.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import org.windy.windyagent.Messages
import org.windy.windyagent.web.ApiHandler
import org.windy.windyagent.web.ChatArchive
import org.windy.windyagent.web.DashboardServer

/**
 * 聊天 API：/api/chat, /api/chat/stream, /api/chat/history
 *
 * @param archive 共享聊天存档（与 IM 联动共用同一实例，实现 web↔QQ 记录互通）。
 * @param streamChat 可选的「裸 LLM 真流式」：(session, message, onChunk) -> 完整回复。已弃用（不带工具会瞎编）。
 * @param processChat 可选的「带工具 Agent + 过程流式」：(session, message, onStep(tool,ok,ms)) -> 完整回复。
 *   非 null 时 /api/chat/stream 优先走它：agent 执行中把每个工具调用作为 step 帧实时推给前端（仿 Hermes），
 *   agent 完成后最终回复走打字机切片。这是既能真调工具、又能展示过程的方案。
 */
class ChatHandler(
    private val server: DashboardServer,
    private val chat: ((String, String) -> String)?,
    private val archive: ChatArchive,
    private val streamChat: ((String, String, (String) -> Unit) -> String)? = null,
    private val processChat: ((String, String, (String, Boolean, Long) -> Unit) -> String)? = null
) : ApiHandler {

    override fun canHandle(path: String): Boolean = path.startsWith("/api/chat")

    override fun handle(ex: HttpExchange, path: String, query: Map<String, String>, mapper: ObjectMapper) {
        when (path) {
            "/api/chat" -> chatApi(ex, mapper)
            "/api/chat/stream" -> chatStreamApi(ex, mapper)
            "/api/chat/history" -> server.json(ex, 200, archive.history(query["session"]?.takeIf { it.isNotBlank() } ?: "web-console"))
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
            runCatching { c(sid, msg) }; archive.clear(sid)
            return server.json(ex, 200, mapper.createObjectNode().put("reply", Messages.t("web.new_chat")).toString())
        }
        val reply = runCatching { c(sid, msg) }.getOrElse { Messages.t("web.error", it.message ?: "") }
        archive.append(sid, "u", disp); archive.append(sid, "a", reply)
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
        val lock = Any() // SSE 写帧加锁：过程回调可能在工具的并行线程触发
        fun send(payload: String) = synchronized(lock) { writer.write("data: $payload\n\n"); writer.flush() }
        fun sendText(chunk: String) = send(mapper.createObjectNode().put("text", chunk).toString())
        // 最终回复打字机切片（按 code point 切，避免劈开 emoji 代理对显示 "??"）
        fun typewriter(reply: String) {
            val cps = reply.codePoints().toArray()
            var i = 0
            while (i < cps.size) {
                val end = (i + 2).coerceAtMost(cps.size)
                sendText(String(cps, i, end - i)); i = end
                runCatching { Thread.sleep(22) } // 约 90 字/秒
            }
        }
        try {
            when {
                processChat != null -> {
                    // 带工具 Agent + 过程流式：执行中把每个工具调用作为 step 帧实时推给前端，完成后打字机出正文。
                    val reply = runCatching {
                        processChat!!.invoke(sid, msg) { tool, ok, ms ->
                            val step = mapper.createObjectNode().put("tool", tool).put("ok", ok).put("ms", ms)
                            send("""{"step":$step}""")
                        }
                    }.getOrElse { Messages.t("web.error", it.message ?: "") }
                    typewriter(reply)
                    send("[DONE]")
                    archive.append(sid, "u", msg); archive.append(sid, "a", reply)
                }
                streamChat != null -> {
                    val fullReply = streamChat!!.invoke(sid, msg) { chunk -> sendText(chunk) }
                    send("[DONE]")
                    archive.append(sid, "u", msg); archive.append(sid, "a", fullReply)
                }
                else -> {
                    // 兜底：无过程，带工具 Agent 跑完打字机切片
                    val reply = runCatching { c(sid, msg) }.getOrElse { Messages.t("web.error", it.message ?: "") }
                    typewriter(reply)
                    send("[DONE]")
                    archive.append(sid, "u", msg); archive.append(sid, "a", reply)
                }
            }
        } catch (e: Exception) {
            send(mapper.createObjectNode().put("error", e.message).toString())
        } finally { writer.close() }
    }
}
