package org.windy.windyagent.platform

import org.windy.windyagent.llm.LLMMessage
import java.util.concurrent.ConcurrentHashMap

class SessionManager(private val maxHistorySize: Int = 20) {
    private val sessions = ConcurrentHashMap<String, MutableList<LLMMessage>>()

    fun getHistory(sessionId: String): MutableList<LLMMessage> =
        sessions.computeIfAbsent(sessionId) { mutableListOf() }

    fun clearSession(sessionId: String) { sessions.remove(sessionId) }

    fun trimHistory(sessionId: String) {
        val history = sessions[sessionId] ?: return
        while (history.size > maxHistorySize) history.removeFirst()
    }
}
