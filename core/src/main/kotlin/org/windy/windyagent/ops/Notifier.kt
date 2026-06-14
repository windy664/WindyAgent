package org.windy.windyagent.ops

import org.slf4j.LoggerFactory

/** 告警出口抽象：把一条告警（可带 LLM 处置建议）送到某个通道。WebUI / 控制台 / 将来 QQ·Webhook 各实现一个。 */
interface Notifier {
    fun notify(incident: Incident, advice: String?)
}

/** 多通道广播：任一通道异常不影响其它。 */
class CompositeNotifier(private val channels: List<Notifier>) : Notifier {
    override fun notify(incident: Incident, advice: String?) =
        channels.forEach { runCatching { it.notify(incident, advice) } }
}

/** 控制台通道：直接打日志（零配置基线，任何宿主都有）。 */
class LogNotifier : Notifier {
    private val log = LoggerFactory.getLogger("WindyAgent-Sentinel")
    override fun notify(incident: Incident, advice: String?) {
        val head = "[哨兵][${incident.severity}] ${incident.server} ${incident.kind}：${incident.detail}"
        if (advice.isNullOrBlank()) log.warn(head) else log.warn("{}\n  └ 处置建议：{}", head, advice)
    }
}
