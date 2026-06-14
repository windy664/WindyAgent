package org.windy.windyagent.web

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.ops.Incident
import org.windy.windyagent.ops.Notifier
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * WebUI 告警通道：把哨兵告警存进内存环形缓冲（最新在前），供 [DashboardServer] 的 /api/alerts 拉取。
 * 同时实现 [Notifier]，可直接挂进哨兵的 [org.windy.windyagent.ops.CompositeNotifier]。
 * 进程内存态、不落盘（告警是实时态；要历史看控制台日志/将来落库）。
 */
class AlertCenter(private val max: Int = 100) : Notifier {
    private val mapper = ObjectMapper()
    private val buf = ConcurrentLinkedDeque<String>()   // 每条已序列化为 JSON 串

    override fun notify(incident: Incident, advice: String?) {
        val n = mapper.createObjectNode()
        n.put("server", incident.server)
        n.put("kind", incident.kind.name)
        n.put("severity", incident.severity)
        n.put("detail", incident.detail)
        if (!advice.isNullOrBlank()) n.put("advice", advice)
        n.put("ts", System.currentTimeMillis())
        buf.addFirst(n.toString())
        while (buf.size > max) buf.pollLast()
    }

    /** 最新在前的告警 JSON 数组。 */
    fun json(): String = "[" + buf.joinToString(",") + "]"
}
