package org.windy.windyagent.safety

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory approval queue for high-risk operations. Restarting drops pending
 * items, which is acceptable because approvals are intentionally short-lived.
 */
class PendingApprovals(
    private val ttlMs: Long = 10 * 60 * 1000,
    private val executionFailureMessage: (String) -> String = { "执行失败：$it" }
) {

    private class Pending(val desc: String, val execute: () -> String, val at: Long)

    data class Item(val id: String, val desc: String, val at: Long)
    /** decision: approved | denied | expired */
    data class Decision(val id: String, val desc: String, val decision: String, val result: String, val at: Long)

    private val map = ConcurrentHashMap<String, Pending>()
    private val seq = AtomicInteger(0)
    private val history = ConcurrentLinkedDeque<Decision>()
    private val maxHistory = 60

    fun ttl(): Long = ttlMs

    /** @return approval id. */
    fun submit(desc: String, execute: () -> String): String {
        purge()
        val id = "A" + seq.incrementAndGet()
        map[id] = Pending(desc, execute, System.currentTimeMillis())
        return id
    }

    /** Approve and execute. Returns null when the id is missing or expired. */
    fun approve(id: String): String? {
        purge()
        val p = map.remove(id) ?: return null
        val result = runCatching { p.execute() }
            .getOrElse { executionFailureMessage(it.message ?: "") }
        record(Decision(id, p.desc, "approved", result, System.currentTimeMillis()))
        return result
    }

    /** Deny. Returns the denied description, or null when the id is missing. */
    fun deny(id: String): String? {
        val p = map.remove(id) ?: return null
        record(Decision(id, p.desc, "denied", "", System.currentTimeMillis()))
        return p.desc
    }

    fun list(): List<String> {
        purge()
        return map.entries.sortedBy { it.value.at }.map { "${it.key}: ${it.value.desc}" }
    }

    fun items(): List<Item> {
        purge()
        return map.entries.sortedBy { it.value.at }.map { Item(it.key, it.value.desc, it.value.at) }
    }

    fun historyItems(): List<Decision> = history.toList()

    private fun record(d: Decision) {
        history.addFirst(d)
        while (history.size > maxHistory) history.pollLast()
    }

    private fun purge() {
        val now = System.currentTimeMillis()
        map.entries.removeIf { (now - it.value.at > ttlMs).also { expired -> if (expired) record(Decision(it.key, it.value.desc, "expired", "", now)) } }
    }
}
