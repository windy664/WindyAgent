package org.windy.windyagent.platform

import org.windy.windyagent.llm.LLMMessage
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * 会话管理：按 sessionId 维护对话历史（Agent 多轮上下文）。
 *
 * 线程安全：内部列表用 synchronizedList 包装；淘汰用 synchronized 块保护。
 * LRU 淘汰：超过 maxSessions 时踢最久未活跃的会话。
 */
class SessionManager(
    private val maxHistorySize: Int = 20,
    private val maxSessions: Int = 500
) {
    // LRU map：accessOrder=true，最近访问的排最后
    private val sessions = object : LinkedHashMap<String, MutableList<LLMMessage>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableList<LLMMessage>>?): Boolean =
            size > maxSessions
    }

    fun getHistory(sessionId: String): MutableList<LLMMessage> = synchronized(sessions) {
        sessions.computeIfAbsent(sessionId) { Collections.synchronizedList(mutableListOf()) }
    }

    fun clearSession(sessionId: String) { synchronized(sessions) { sessions.remove(sessionId) } }

    fun trimHistory(sessionId: String) {
        val history = synchronized(sessions) { sessions[sessionId] } ?: return
        synchronized(history) {
            while (history.size > maxHistorySize) history.removeAt(0)
            // 从头删可能把某个 Assistant(tool_calls) 删掉、却留下它的 ToolResults（孤儿）。
            // OpenAI/Anthropic 协议会因"tool 消息无对应 assistant tool_calls"报 400 → 清理开头孤儿。
            while (history.isNotEmpty() && history.first() is LLMMessage.ToolResults) {
                history.removeAt(0)
            }
        }
    }

    /** 当前活跃会话数（监控用）。 */
    fun activeSessions(): Int = synchronized(sessions) { sessions.size }

    // 同会话串行化：同一 sessionId 的多个请求（web 与 QQ 共用 im-<openid>、或用户连发）若并发跑
    // agent.run，会并发读写同一份 history → 破坏 tool_calls/ToolResults 配对(400)/上下文错乱。
    // 用 per-session 锁把同一会话的 agent 调用串行化；不同会话仍并行。
    private val sessionLocks = ConcurrentHashMap<String, ReentrantLock>()

    /** 在会话独占锁下执行 [block]（同 sessionId 串行，不同 sessionId 并行）。 */
    fun <T> withSessionLock(sessionId: String, block: () -> T): T {
        val lock = sessionLocks.computeIfAbsent(sessionId) { ReentrantLock() }
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
