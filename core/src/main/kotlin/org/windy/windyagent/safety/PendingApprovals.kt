package org.windy.windyagent.safety

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 人工审批闸：高危操作不自动执行，先入待审队列，管理员 /ai-approve 批准才真正执行。
 *
 * 进程内内存队列（重启丢弃待审项，可接受——高危操作短时效）；带过期。
 * `execute` 是「绕过 guard 的真实执行」闭包，由提交方提供（远端=总线派发 / 本地=主线程执行）。
 */
class PendingApprovals(private val ttlMs: Long = 10 * 60 * 1000) {

    private class Pending(val desc: String, val execute: () -> String, val at: Long)

    private val map = ConcurrentHashMap<String, Pending>()
    private val seq = AtomicInteger(0)

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
        return runCatching { p.execute() }.getOrElse { "执行失败：${it.message}" }
    }

    /** 驳回；返回被驳回项的描述，单号不存在返回 null。 */
    fun deny(id: String): String? = map.remove(id)?.desc

    fun list(): List<String> {
        purge()
        return map.entries.sortedBy { it.value.at }.map { "${it.key}: ${it.value.desc}" }
    }

    private fun purge() {
        val now = System.currentTimeMillis()
        map.entries.removeIf { now - it.value.at > ttlMs }
    }
}
