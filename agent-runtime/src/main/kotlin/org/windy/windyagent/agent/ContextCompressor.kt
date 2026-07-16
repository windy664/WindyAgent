package org.windy.windyagent.agent

import org.slf4j.LoggerFactory
import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.llm.LLMProvider

/**
 * 上下文压缩器：历史消息超过阈值时，用 LLM 把旧消息摘要成一条替换。
 *
 * 策略：保留最近 [keepRecent] 条原样消息 + 把更早的消息用 fastLlm 压缩成
 * 一条 `[至此的对话摘要]` 消息。释放上下文窗口的同时保留关键信息。
 *
 * 由 [AgentRouter.run] 在 recall 之后、选择 Agent 之前调用。
 */
class ContextCompressor(
    private val llm: LLMProvider,
    private val threshold: Int = 16,
    private val keepRecent: Int = 6
) {
    private val log = LoggerFactory.getLogger(ContextCompressor::class.java)

    /**
     * 检查并压缩历史消息。返回压缩后的列表（可能不变）。
     */
    fun compress(history: MutableList<LLMMessage>): MutableList<LLMMessage> {
        if (history.size <= threshold) return history

        val splitPoint = (history.size - keepRecent).coerceAtLeast(1)
        val oldMessages = history.subList(0, splitPoint)
        val recentMessages = history.subList(splitPoint, history.size)

        // 把旧消息拼成文本供 LLM 摘要
        val transcript = oldMessages.mapNotNull { m ->
            when (m) {
                is LLMMessage.User -> "[用户] ${m.content}"
                is LLMMessage.Assistant -> "[助手] ${m.content ?: ""}${if (m.toolCalls.isNotEmpty()) " [调用了 ${m.toolCalls.size} 个工具]" else ""}"
                is LLMMessage.ToolResults -> "[工具结果] ${m.results.size} 条"
            }
        }.joinToString("\n")

        if (transcript.length < 200) return history // 太短不值得压缩

        val summary = runCatching {
            llm.chat(
                SUMMARIZE_PROMPT,
                listOf(LLMMessage.User("请压缩以下对话记录：\n\n${transcript.take(6000)}"))
            ).textContent?.trim()
        }.getOrNull()

        if (summary.isNullOrBlank()) {
            log.warn("上下文压缩失败，保留原样")
            return history
        }

        log.info("上下文压缩：{} 条消息 → 摘要 + {} 条最近消息", history.size, recentMessages.size)
        val compressed = mutableListOf<LLMMessage>()
        compressed += LLMMessage.User("[至此的对话摘要]\n$summary")
        compressed.addAll(recentMessages)
        return compressed
    }

    companion object {
        private val SUMMARIZE_PROMPT = """
            你是对话摘要器。把以下多轮对话压缩成一段简洁的摘要（中文），保留：
            1. 用户的核心意图和关键请求
            2. 已完成的操作和结果
            3. 未完成的事项或待确认的问题
            4. 关键的上下文信息（玩家名、服务器名、数值等）
            不要遗漏重要细节，但去掉闲聊和重复。直接输出摘要，不要加前缀。
        """.trimIndent()
    }
}
