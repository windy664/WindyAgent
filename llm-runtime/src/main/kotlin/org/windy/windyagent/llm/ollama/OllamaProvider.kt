package org.windy.windyagent.llm.ollama

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.windy.windyagent.tools.AgentTool
import org.windy.windyagent.llm.*
import java.util.concurrent.TimeUnit

class OllamaProvider(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "llama3.2"
) : LLMProvider {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()
    private val mapper = ObjectMapper()

    override val name = "ollama"

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
            .url("$baseUrl/v1/chat/completions")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            val bodyBytes = resp.body?.bytes() ?: ByteArray(0)
            if (bodyBytes.size > 8 * 1024 * 1024) throw LLMException("Ollama 响应超过 8MB")
            val bodyStr = String(bodyBytes, Charsets.UTF_8)
            if (!resp.isSuccessful) throw LLMException("Ollama HTTP ${resp.code}: ${bodyStr.take(200)}")
            return parseResponse(bodyStr)
        }
    }

    private fun parseResponse(json: String): LLMResponse {
        val node = mapper.readTree(json)
        val choice = node["choices"][0]
        val message = choice["message"]

        var content: String? = message["content"]?.asText()?.takeIf { it.isNotBlank() }
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
}
