package org.windy.windyagent.agent

import org.slf4j.LoggerFactory
import org.windy.windyagent.Messages
import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.llm.LLMProvider
import org.windy.windyagent.llm.LLMResponse
import org.windy.windyagent.llm.ToolResult

private val loopLogger = LoggerFactory.getLogger("org.windy.windyagent.agent.ToolLoop")

/**
 * 通用 ReAct 工具循环：反复调用 LLM，遇到 TOOL_USE 就执行工具并把结果回灌，
 * 直到模型 END_TURN 或达到 [maxIterations] 上限。
 *
 * 被 [ReActAgent] 与 [PlanExecuteAgent] 共用——前者直接跑一轮，后者带着计划跑。
 * [messages] 会被原地追加，便于调用方据此同步会话历史。
 */
internal fun toolLoop(
    llmProvider: LLMProvider,
    systemPrompt: String,
    messages: MutableList<LLMMessage>,
    tools: List<AgentTool>,
    maxIterations: Int
): AgentResponse {
    val executedTools = mutableListOf<String>()

    for (i in 0 until maxIterations) {
        loopLogger.debug("tool loop iteration {}, messages: {}", i + 1, messages.size)

        val response = llmProvider.chat(systemPrompt, messages, tools)

        when (response.stopReason) {
            LLMResponse.StopReason.END_TURN -> {
                val text = response.textContent ?: ""
                messages += LLMMessage.Assistant(text)
                return AgentResponse(text, true, executedTools)
            }
            LLMResponse.StopReason.TOOL_USE -> {
                messages += LLMMessage.Assistant(response.textContent, response.toolCalls)
                val results = response.toolCalls.map { tc ->
                    loopLogger.info("Tool call: {} args={}", tc.name, tc.inputJson.take(200))
                    executedTools += tc.name
                    val r = tools.find { it.name == tc.name }
                        ?.execute(tc.id, tc.inputJson)
                        ?: ToolResult.error(tc.id, "Tool not found: ${tc.name}")
                    loopLogger.info("Tool result: {} -> {}{}", tc.name, if (r.isError) "[ERROR] " else "", r.content.take(300))
                    r
                }
                messages += LLMMessage.ToolResults(results)
            }
            else -> return AgentResponse(Messages.t("agent.stopped", response.stopReason ?: ""), false, executedTools)
        }
    }

    return AgentResponse(Messages.t("agent.max_iter", maxIterations), false, executedTools)
}

/** 把循环后的完整消息列表写回会话历史。 */
internal fun AgentContext.syncHistory(messages: List<LLMMessage>) {
    history.clear()
    history.addAll(messages)
}

/**
 * 把召回的长期记忆拼进**当次 user 消息**（而非系统提示）——这样系统提示 + 工具定义保持稳定、
 * 可被 provider 前缀缓存命中（ReAct 多轮/跨请求复用），记忆与新问只落在非缓存尾部。
 */
internal fun userMessageWithMemory(context: AgentContext): String =
    if (context.recalled.isBlank()) context.userMessage
    else "[关于当前用户的已知记忆，酌情参考]\n${context.recalled}\n\n${context.userMessage}"
