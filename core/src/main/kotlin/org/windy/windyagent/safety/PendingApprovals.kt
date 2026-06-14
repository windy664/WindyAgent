package org.windy.windyagent.safety

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * 人工审批闸：高危操作不自动执行，先入待审队列，管理员 /ai-approve 或 WebUI 批准才真正执行。
 *
 * 进程内内存队列（重启丢弃待审项，可接受——高危操作短时效）；带过期。
 * `execute` 是「绕过 guard 的真实执行」闭包，由提交方提供（远端=总线派发 / 本地=主线程执行）。
 * approve/deny 是唯一收口，故在此记**审批历史**（游戏内/控制台/网页批的都收得到）。
 */
class PendingApprovals(private val ttlMs: Long = 10 * 60 * 1000) {

    private class Pending(val desc: String, val execute: () -> String, val at: Long)

    /** 待审项（对外）。 */
    data class Item(val id: String, val desc: String, val at: Long)
    /** 一条审批历史。decision: approved | denied | expired。 */
    data class Decision(val id: String, val desc: String, val decision: String, val result: String, val at: Long)

    private val map = ConcurrentHashMap<String, Pending>()
    private val seq = AtomicInteger(0)
    private val history = ConcurrentLinkedDeque<Decision>()
    private val maxHistory = 60

    fun ttl(): Long = ttlMs

    /** @return 审批单号。 */
    fun submit(desc: String, execute: () -> String): String {
        purge()
        val id = "A" + seq.incrementAndGet()
        map[id] = Pending(desc, execute, System.currentTimeMillis())
        return id
    }

    /** 批准并执行；返回执行结果文本，单号不存在/已过期返回 null。 */
    fun approve(id: String): String? {
        purge()
        val p = map.remove(id) ?: return null
        val result = runCatching { p.execute() }.getOrElse { "执行失败：${it.message}" }
        record(Decision(id, p.desc, "approved", result, System.currentTimeMillis()))
        return result
    }

    /** 驳回；返回被驳回项的描述，单号不存在返回 null。 */
    fun deny(id: String): String? {
        val p = map.remove(id) ?: return null
        record(Decision(id, p.desc, "denied", "", System.currentTimeMillis()))
        return p.desc
    }

    fun list(): List<String> {
        purge()
        return map.entries.sortedBy { it.value.at }.map { "${it.key}: ${it.value.desc}" }
    }

    /** 结构化待审列表（WebUI 用）。 */
    fun items(): List<Item> {
        purge()
        return map.entries.sortedBy { it.value.at }.map { Item(it.key, it.value.desc, it.value.at) }
    }

    /** 审批历史（最新在前）。 */
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
