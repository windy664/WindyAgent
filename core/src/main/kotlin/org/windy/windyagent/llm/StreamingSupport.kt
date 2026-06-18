package org.windy.windyagent.llm

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 流式聊天会话。[chunks] 是阻塞队列，调用方逐块读取。
 * 收到 [StreamChunk.Done] 或 [StreamChunk.Error] 时流结束。
 */
class ChatStream {
    private val queue: BlockingQueue<StreamChunk> = LinkedBlockingQueue()

    /** 供 LLMProvider 写入。 */
    fun emit(chunk: StreamChunk) { queue.offer(chunk) }

    /** 供调用方读取（阻塞）。timeoutMs=0 表示无限等待。 */
    fun read(timeoutMs: Long = 0): StreamChunk? =
        if (timeoutMs > 0) queue.poll(timeoutMs, TimeUnit.MILLISECONDS) else queue.take()

    /** 收集所有文本块为完整响应。 */
    fun collectText(): String {
        val sb = StringBuilder()
        while (true) {
            val chunk = read() ?: break
            when (chunk) {
                is StreamChunk.Text -> sb.append(chunk.text)
                is StreamChunk.Done -> break
                is StreamChunk.Error -> break
                is StreamChunk.ToolCallStart -> {}
                is StreamChunk.ToolCallDelta -> {}
            }
        }
        return sb.toString()
    }
}

sealed class StreamChunk {
    /** 文本增量。 */
    data class Text(val text: String) : StreamChunk()
    /** 工具调用开始。 */
    data class ToolCallStart(val id: String, val name: String) : StreamChunk()
    /** 工具调用参数增量。 */
    data class ToolCallDelta(val id: String, val delta: String) : StreamChunk()
    /** 流正常结束。 */
    object Done : StreamChunk()
    /** 流异常结束。 */
    data class Error(val message: String) : StreamChunk()
}
