package org.windy.windyagent.llm.openai

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.windy.windyagent.agent.AgentTool
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
) : LLMProvider {

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
            if (!resp.isSuccessful) {
                val body = runCatching { resp.body?.string() }.getOrNull() ?: ""
                throw LLMException("LLM HTTP ${resp.code}: ${body.take(200)}")
            }
            return parseResponse(resp.body!!.string())
        }
    }

    private fun parseResponse(json: String): LLMResponse {
        val node = mapper.readTree(json)
        val choice = node["choices"][0]
        val message = choice["message"]

        val content: String? = message["content"]?.asText()?.takeIf { it.isNotBlank() }
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
        return LLMResponse(content, toolCalls, stopReason)
    }
}
