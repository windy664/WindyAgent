package org.windy.windyagent.platform

import org.windy.windyagent.llm.LLMMessage
import java.util.Collections
import java.util.LinkedHashMap

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
        }
    }

    /** 当前活跃会话数（监控用）。 */
    fun activeSessions(): Int = synchronized(sessions) { sessions.size }
}
