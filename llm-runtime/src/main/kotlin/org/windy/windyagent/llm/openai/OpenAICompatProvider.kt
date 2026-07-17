package org.windy.windyagent.llm.openai

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.windy.windyagent.tools.AgentTool
import org.windy.windyagent.tools.StreamingProvider
import org.windy.windyagent.llm.*
import java.util.concurrent.TimeUnit

/**
 * 任意 OpenAI 兼容协议（mimo、讯飞、智谱等）。
 * baseUrl 传到 /v1 为止，例如 https://token-plan-cn.xiaomimimo.com/v1
 */
class OpenAICompatProvider(
    baseUrl: String,
    private val model: String,
    private val apiKey: String
) : LLMProvider, StreamingProvider {

    private val chatUrl = baseUrl.trimEnd('/') + "/chat/completions"
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()
    private val mapper = ObjectMapper()

    override val name = "openai-compat($model)"

    override fun chat(systemPrompt: String, messages: List<LLMMessage>, tools: List<AgentTool>): LLMResponse {
        val allMessages = mutableListOf<Map<String, Any>>()
        allMessages += mapOf("role" to "system", "content" to systemPrompt)

        for (msg in messages) {
            when (msg) {
                is LLMMessage.User -> allMessages += mapOf("role" to "user", "content" to msg.content)
                is LLMMessage.Assistant -> {
                    if (msg.toolCalls.isEmpty()) {
                        allMessages += mapOf("role" to "assistant", "content" to (msg.content ?: ""))
                    } else {
                        allMessages += mapOf(
                            "role" to "assistant",
                            "content" to (msg.content ?: ""),
                            "tool_calls" to msg.toolCalls.map { tc ->
                                mapOf("id" to tc.id, "type" to "function",
                                    "function" to mapOf("name" to tc.name, "arguments" to tc.inputJson))
                            }
                        )
                    }
                }
                is LLMMessage.ToolResults -> msg.results.forEach { r ->
                    allMessages += mapOf("role" to "tool", "tool_call_id" to r.toolCallId, "content" to r.content)
                }
            }
        }

        val body = mutableMapOf<String, Any>("model" to model, "messages" to allMessages, "stream" to false)
        if (tools.isNotEmpty()) {
            body["tools"] = tools.map { t ->
                mapOf("type" to "function",
                    "function" to mapOf("name" to t.name, "description" to t.description,
                        "parameters" to mapper.readValue(t.inputSchema, Map::class.java)))
            }
        }

        val json = mapper.writeValueAsString(body)
        val request = Request.Builder()
            .url(chatUrl)
            .header("Authorization", "Bearer $apiKey")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            val bodyBytes = resp.body?.bytes() ?: ByteArray(0)
            if (bodyBytes.size > 8 * 1024 * 1024) throw LLMException("LLM 响应超过 8MB")
            val bodyStr = String(bodyBytes, Charsets.UTF_8)
            if (!resp.isSuccessful) {
                throw LLMException("LLM HTTP ${resp.code}: ${bodyStr.take(200)}")
            }
            return parseResponse(bodyStr)
        }
    }

    private fun parseResponse(json: String): LLMResponse {
        val node = mapper.readTree(json)
        val choice = node["choices"][0]
        val message = choice["message"]

        // isTextual 防 NullNode.asText()=="null"（见 chatStream 处同因注释）
        val content: String? = message["content"]?.takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }
        val finishReason = choice["finish_reason"]?.asText() ?: "stop"

        val toolCalls = mutableListOf<ToolCall>()
        message["tool_calls"]?.takeIf { !it.isNull }?.forEach { tc ->
            val fn = tc["function"]
            toolCalls += ToolCall(tc["id"].asText(), fn["name"].asText(), fn["arguments"].asText())
        }

        val stopReason = when (finishReason) {
            "tool_calls" -> LLMResponse.StopReason.TOOL_USE
            "length" -> LLMResponse.StopReason.MAX_TOKENS
            else -> LLMResponse.StopReason.END_TURN
        }
        val usage = node["usage"]
        val inTok = usage?.get("prompt_tokens")?.asInt(-1) ?: -1
        val outTok = usage?.get("completion_tokens")?.asInt(-1) ?: -1
        return LLMResponse(content, toolCalls, stopReason, inTok, outTok)
    }

    // ── 流式输出 ──

    override fun chatStream(systemPrompt: String, messages: List<LLMMessage>, tools: List<AgentTool>): ChatStream {
        val stream = ChatStream()
        val allMessages = buildMessages(systemPrompt, messages)
        val body = mutableMapOf<String, Any>("model" to model, "messages" to allMessages, "stream" to true,
            // 让 API 在流末尾回报真实 token 用量（OpenAI 兼容协议），统计走精确值而非估算
            "stream_options" to mapOf("include_usage" to true))
        if (tools.isNotEmpty()) body["tools"] = buildToolDefs(tools)

        val json = mapper.writeValueAsString(body)
        val request = Request.Builder()
            .url(chatUrl)
            .header("Authorization", "Bearer $apiKey")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        // 异步执行 SSE 读取
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                stream.emit(StreamChunk.Error(e.message ?: "request failed"))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        stream.emit(StreamChunk.Error("HTTP ${resp.code}"))
                        return
                    }
                    val reader = resp.body?.source() ?: run {
                        stream.emit(StreamChunk.Error("empty body"))
                        return
                    }
                    try {
                        while (!reader.exhausted()) {
                            val line = reader.readUtf8Line() ?: break
                            if (!line.startsWith("data: ")) continue
                            val data = line.removePrefix("data: ").trim()
                            if (data == "[DONE]") break
                            val chunk = runCatching { mapper.readTree(data) }.getOrNull() ?: continue
                            // 末尾 usage 帧：choices 为空数组、带 usage 字段。先于 delta 处理。
                            chunk["usage"]?.takeIf { !it.isNull }?.let { u ->
                                val inTok = u["prompt_tokens"]?.asInt(-1) ?: -1
                                val outTok = u["completion_tokens"]?.asInt(-1) ?: -1
                                if (inTok >= 0 || outTok >= 0) stream.emit(StreamChunk.Usage(inTok, outTok))
                            }
                            val delta = chunk["choices"]?.get(0)?.get("delta") ?: continue
                            // 只放行真正的文本节点：带思维链的模型(如 mimo)思考阶段 delta 是
                            // {"content":null,"reasoning_content":"..."}，Jackson 对 null 字段返回
                            // NullNode，其 asText() 是字符串 "null"，会被当正文发出 → 满屏 "null"。
                            delta["content"]?.takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }?.let {
                                stream.emit(StreamChunk.Text(it))
                            }
                            delta["tool_calls"]?.forEach { tc ->
                                val fn = tc["function"]
                                if (tc["index"]?.asInt() == 0 && fn?.get("name")?.asText() != null) {
                                    stream.emit(StreamChunk.ToolCallStart(tc["id"]?.asText() ?: "", fn["name"].asText()))
                                }
                                fn?.get("arguments")?.asText()?.takeIf { it.isNotBlank() }?.let {
                                    stream.emit(StreamChunk.ToolCallDelta(tc["id"]?.asText() ?: "", it))
                                }
                            }
                        }
                        stream.emit(StreamChunk.Done)
                    } catch (e: Exception) {
                        stream.emit(StreamChunk.Error(e.message ?: "stream read error"))
                    }
                }
            }
        })
        return stream
    }

    private fun buildMessages(systemPrompt: String, messages: List<LLMMessage>): List<Map<String, Any>> {
        val all = mutableListOf<Map<String, Any>>()
        all += mapOf("role" to "system", "content" to systemPrompt)
        for (msg in messages) when (msg) {
            is LLMMessage.User -> all += mapOf("role" to "user", "content" to msg.content)
            is LLMMessage.Assistant -> {
                if (msg.toolCalls.isEmpty()) all += mapOf("role" to "assistant", "content" to (msg.content ?: ""))
                else all += mapOf("role" to "assistant", "content" to (msg.content ?: ""),
                    "tool_calls" to msg.toolCalls.map { tc ->
                        mapOf("id" to tc.id, "type" to "function", "function" to mapOf("name" to tc.name, "arguments" to tc.inputJson))
                    })
            }
            is LLMMessage.ToolResults -> msg.results.forEach { r ->
                all += mapOf("role" to "tool", "tool_call_id" to r.toolCallId, "content" to r.content)
            }
        }
        return all
    }

    private fun buildToolDefs(tools: List<AgentTool>): List<Map<String, Any>> {
        return tools.map { t ->
            mapOf("type" to "function", "function" to mapOf("name" to t.name, "description" to t.description,
                "parameters" to mapper.readValue(t.inputSchema, Map::class.java)))
        }
    }
}
