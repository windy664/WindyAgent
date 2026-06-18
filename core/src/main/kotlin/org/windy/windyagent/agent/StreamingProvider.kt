package org.windy.windyagent.agent

import org.windy.windyagent.llm.ChatStream
import org.windy.windyagent.llm.LLMMessage

/**
 * 流式输出接口：LLMProvider 可选实现此接口，支持边生成边推送。
 *
 * 实现方式：返回 [ChatStream]，调用方逐块读取（阻塞式队列）。
 * 与前端的 SSE/WebSocket 对接时，每读一块就推一条事件。
 */
interface StreamingProvider {
    fun chatStream(systemPrompt: String, messages: List<LLMMessage>, tools: List<AgentTool>): ChatStream
}
