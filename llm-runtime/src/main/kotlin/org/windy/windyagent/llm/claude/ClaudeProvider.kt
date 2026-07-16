package org.windy.windyagent.llm.claude

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.llm.*

class ClaudeProvider @JvmOverloads constructor(
    apiKey: String,
    private val model: String = "claude-opus-4-8",
    baseUrl: String? = null
) : LLMProvider {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .apply { if (!baseUrl.isNullOrBlank()) baseUrl(baseUrl) }
        .build()

    private val mapper = ObjectMapper()

    override val name = "claude"

    override fun chat(systemPrompt: String, messages: List<LLMMessage>, tools: List<AgentTool>): LLMResponse {
        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(8096L)
            .system(systemPrompt)

        for (msg in messages) {
            when (msg) {
                is LLMMessage.User -> builder.addUserMessage(msg.content)

                is LLMMessage.Assistant -> {
                    if (msg.toolCalls.isEmpty()) {
                        builder.addAssistantMessage(msg.content ?: "")
                    } else {
                        val blocks = mutableListOf<ContentBlockParam>()
                        val content = msg.content
                        if (!content.isNullOrBlank()) {
                            blocks += ContentBlockParam.ofText(TextBlockParam.builder().text(content).build())
                        }
                        for (tc in msg.toolCalls) {
                            @Suppress("UNCHECKED_CAST")
                            val inputMap = mapper.readValue(tc.inputJson, Map::class.java) as Map<String, Any>
                            blocks += ContentBlockParam.ofToolUse(
                                ToolUseBlockParam.builder()
                                    .id(tc.id)
                                    .name(tc.name)
                                    .input(JsonValue.from(inputMap))
                                    .build()
                            )
                        }
                        builder.addMessage(
                            MessageParam.builder()
                                .role(MessageParam.Role.ASSISTANT)
                                .contentOfBlockParams(blocks)
                                .build()
                        )
                    }
                }

                is LLMMessage.ToolResults -> {
                    val blocks = msg.results.map { r ->
                        ContentBlockParam.ofToolResult(
                            ToolResultBlockParam.builder()
                                .toolUseId(r.toolCallId)
                                .content(r.content)
                                .build()
                        )
                    }
                    builder.addMessage(
                        MessageParam.builder()
                            .role(MessageParam.Role.USER)
                            .contentOfBlockParams(blocks)
                            .build()
                    )
                }
            }
        }

        for (tool in tools) builder.addTool(buildSdkTool(tool))

        return try {
            parseResponse(client.messages().create(builder.build()))
        } catch (e: Exception) {
            // Anthropic SDK 的异常包装成 LLMException，保留原始信息供 FallbackProvider 分类
            throw LLMException("Claude API 错误: ${e.message}", e)
        }
    }

    private fun buildSdkTool(tool: AgentTool): Tool {
        val schema = mapper.readTree(tool.inputSchema)
        val propsBuilder = Tool.InputSchema.Properties.builder()
        schema.get("properties")?.fields()?.forEach { (key, node) ->
            @Suppress("UNCHECKED_CAST")
            propsBuilder.putAdditionalProperty(key, JsonValue.from(mapper.convertValue(node, Map::class.java)))
        }
        val schemaBuilder = Tool.InputSchema.builder().properties(propsBuilder.build())
        schema.get("required")?.takeIf { it.isArray }?.map { it.asText() }?.let { schemaBuilder.required(it) }
        return Tool.builder()
            .name(tool.name)
            .description(tool.description)
            .inputSchema(schemaBuilder.build())
            .build()
    }

    private fun parseResponse(response: Message): LLMResponse {
        var textContent: String? = null
        val toolCalls = mutableListOf<ToolCall>()

        for (block in response.content()) {
            block.text().ifPresent { textContent = it.text() }
            block.toolUse().ifPresent { tu ->
                toolCalls += ToolCall(tu.id(), tu.name(), tu._input().toString())
            }
        }

        val stopReason = when (response.stopReason().toString()) {
            "tool_use" -> LLMResponse.StopReason.TOOL_USE
            "max_tokens" -> LLMResponse.StopReason.MAX_TOKENS
            else -> LLMResponse.StopReason.END_TURN
        }
        val usage = runCatching { response.usage() }.getOrNull()
        val inTok = usage?.let { runCatching { it.inputTokens().toInt() }.getOrNull() } ?: -1
        val outTok = usage?.let { runCatching { it.outputTokens().toInt() }.getOrNull() } ?: -1
        return LLMResponse(textContent, toolCalls, stopReason, inTok, outTok)
    }
}
