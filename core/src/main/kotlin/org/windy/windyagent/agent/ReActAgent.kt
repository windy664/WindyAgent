package org.windy.windyagent.agent

import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.llm.LLMProvider

/**
 * 简单任务：单轮 ReAct 工具循环（边想边调工具，直到给出答复）。
 * 适合问答、单个操作这类一步到位的请求。
 */
class ReActAgent(
    private val llmProvider: LLMProvider,
    private val maxIterations: Int = 10
) : Agent {
    override val name = "react"

    override fun run(context: AgentContext): AgentResponse {
        val messages = context.history.toMutableList()
        messages += LLMMessage.User(userMessageWithMemory(context))

        val response = toolLoop(
            llmProvider,
            context.platform.systemPrompt,
            messages,
            context.effectiveTools,
            maxIterations
        )

        context.syncHistory(messages)
        return response
    }
}
