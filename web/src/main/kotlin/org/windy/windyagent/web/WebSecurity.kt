package org.windy.windyagent.web

import com.sun.net.httpserver.HttpExchange
import java.util.concurrent.ConcurrentHashMap

/**
 * Web 控制台安全策略 —— 借鉴宝塔面板的纵深防御，认证仍用 token。
 *
 * 叠加多层，任一层都不是唯一防线：
 *  1) **安全入口**（可选）：秘密路径前缀，未带即 404（不暴露"这里有后台"）——挡全网扫描器。
 *  2) **token 认证**：仍用 token，但只认请求头 X-Token（不认 ?token= 查询串，避免泄进日志/历史/Referer），
 *     且用**常量时间比较**（防计时侧信道）。
 *  3) **登录失败锁定**：按客户端 IP 计失败次数，超阈值锁定一段时间（防爆破）。
 *  4) 安全响应头 + 收窄 CORS 由 DashboardServer 统一加。
 *
 * 全部可配置（web.security.*），默认给"够安全又不折腾"的值。
 */
class WebSecurity(
    private val token: String,
    /** 安全入口前缀（如 "wa_3f9c"）。非空时，所有 /api 与页面须经 /<entry>/... 访问，否则 404。空=关闭。 */
    private val entry: String,
    private val maxFails: Int,
    private val lockMinutes: Int,
) {
    private data class FailRecord(val count: Int, val firstAt: Long, val lockedUntil: Long)

    private val fails = ConcurrentHashMap<String, FailRecord>()

    val entryEnabled: Boolean get() = entry.isNotBlank()
    /** 安全入口前缀（含首尾斜杠，如 "/wa_3f9c"）；未启用为空串。 */
    val entryPath: String get() = if (entry.isBlank()) "" else "/" + entry.trim('/')

    /** 客户端 IP：优先 X-Forwarded-For 首段（反代场景），否则连接远端地址。 */
    fun clientIp(ex: HttpExchange): String {
        ex.requestHeaders.getFirst("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?.takeIf { it.isNotBlank() }?.let { return it }
        return ex.remoteAddress?.address?.hostAddress ?: "unknown"
    }

    /** 该 IP 是否处于锁定期。 */
    fun isLocked(ip: String): Boolean {
        val r = fails[ip] ?: return false
        return r.lockedUntil > System.currentTimeMillis()
    }

    /** 剩余锁定秒数（供 429 响应提示）。 */
    fun lockRemainingSec(ip: String): Long {
        val r = fails[ip] ?: return 0
        return ((r.lockedUntil - System.currentTimeMillis()).coerceAtLeast(0)) / 1000
    }

    /**
     * 校验 token（仅认 X-Token 头，常量时间比较）。校验失败会累加该 IP 失败次数并可能触发锁定。
     * @return true=通过；false=token 不符（调用方回 401）。token 为空视为未启用鉴权（放行，另有告警）。
     */
    fun checkToken(ex: HttpExchange, ip: String): Boolean {
        if (token.isEmpty()) return true // 未配 token：不鉴权（启动已告警）
        val provided = ex.requestHeaders.getFirst("X-Token")
        if (provided != null && constantTimeEquals(provided, token)) {
            fails.remove(ip) // 成功即清零该 IP 失败记录
            return true
        }
        recordFail(ip)
        return false
    }

    private fun recordFail(ip: String) {
        val now = System.currentTimeMillis()
        fails.compute(ip) { _, old ->
            val base = if (old == null || old.lockedUntil < now && old.count >= maxFails) {
                // 无记录，或上次锁已过期 → 重新计
                FailRecord(0, now, 0)
            } else old
            val cnt = base.count + 1
            val lockedUntil = if (cnt >= maxFails) now + lockMinutes * 60_000L else 0L
            FailRecord(cnt, base.firstAt, lockedUntil)
        }
    }

    /** 常量时间字符串比较：不随首个不同字符提前返回，压制计时侧信道。 */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        val x = a.toByteArray(Charsets.UTF_8)
        val y = b.toByteArray(Charsets.UTF_8)
        var diff = x.size xor y.size
        val n = maxOf(x.size, y.size)
        for (i in 0 until n) {
            val bx = if (i < x.size) x[i].toInt() else 0
            val by = if (i < y.size) y[i].toInt() else 0
            diff = diff or (bx xor by)
        }
        return diff == 0
    }
}
