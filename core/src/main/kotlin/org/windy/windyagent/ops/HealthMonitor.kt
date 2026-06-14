package org.windy.windyagent.ops

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 主动运维哨兵：定时巡检各在线子服健康，**边沿触发**告警（状态翻转才报一次，不刷屏），
 * 经 [onIncident] 把告警交给上层（出处置建议 + 通知）。平台无关：探测/在线集/处置都注入。
 *
 *  - [onlineServers]：当前真实在线子服（中心总线视角）。
 *  - [probe]：探一台子服的健康快照，null=无响应（探测失败/超时）。
 *  - [onIncident]：告警回调（上层接 LLM 建议 + Notifier）。
 *
 * 跑在单个 daemon 调度线程；探测在该线程同步阻塞（probe 内部自带超时），子服数量级无压力。
 */
class HealthMonitor(
    private val onlineServers: () -> Set<String>,
    private val probe: (String) -> HealthSnapshot?,
    private val cfg: Config,
    private val onIncident: (Incident) -> Unit
) {
    data class Config(val intervalSec: Long, val tpsMin: Double, val memPct: Int, val playerDrop: Int)

    private val log = LoggerFactory.getLogger(HealthMonitor::class.java)
    private val jsonMapper = ObjectMapper()
    private val exec = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "windyagent-sentinel").apply { isDaemon = true } }
    private val prev = ConcurrentHashMap<String, HealthSnapshot>()
    private val active = ConcurrentHashMap<String, MutableSet<IncidentKind>>()   // 各子服当前活跃告警，用于边沿触发

    fun start() {
        exec.scheduleAtFixedRate({ runCatching { tick() }.onFailure { log.warn("哨兵巡检异常：{}", it.message) } },
            cfg.intervalSec, cfg.intervalSec, TimeUnit.SECONDS)
        log.info("主动运维哨兵已启动（每 {}s 巡检：TPS<{} / 内存>{}% / 掉线 / 在线骤降>{}）",
            cfg.intervalSec, cfg.tpsMin, cfg.memPct, cfg.playerDrop)
    }

    fun stop() = exec.shutdown()

    /** 当前各子服健康 + 状态（供 WebUI 运维总览）。状态：offline>lag>mem>ok。 */
    fun snapshotsJson(): String {
        val arr = jsonMapper.createArrayNode()
        val online = onlineServers()
        val names = (online + prev.keys + active.keys).toSortedSet()
        for (s in names) {
            val snap = prev[s]
            val acts = active[s]?.toSet() ?: emptySet()
            val status = when {
                IncidentKind.OFFLINE in acts || s !in online -> "offline"
                IncidentKind.LAG in acts -> "lag"
                IncidentKind.MEMORY in acts -> "mem"
                else -> "ok"
            }
            val o = arr.addObject()
            o.put("server", s); o.put("status", status); o.put("connected", s in online)
            if (snap != null) {
                if (snap.tps >= 0) o.put("tps", snap.tps)
                if (snap.online >= 0) o.put("players", snap.online)
                o.put("memUsedMb", snap.memUsedMb); o.put("memMaxMb", snap.memMaxMb)
                o.put("memPct", if (snap.memMaxMb > 0) (snap.memUsedMb * 100 / snap.memMaxMb).toInt() else 0)
                if (snap.platform.isNotBlank()) o.put("platform", snap.platform)
                if (snap.mcVersion.isNotBlank()) o.put("mcVersion", snap.mcVersion)
                if (snap.brand.isNotBlank()) o.put("brand", snap.brand)
                o.put("modCount", snap.modCount)
                o.put("ts", snap.ts)
            }
        }
        return arr.toString()
    }

    private fun act(s: String) = active.getOrPut(s) { Collections.synchronizedSet(HashSet()) }
    /** 边沿触发：仅在该告警从无到有时报一次。 */
    private fun raise(s: String, k: IncidentKind, sev: String, detail: String, snap: HealthSnapshot?) {
        if (act(s).add(k)) onIncident(Incident(s, k, sev, detail, snap))
    }
    /** 恢复：该告警从有到无时报一条 RECOVERED。 */
    private fun clear(s: String, k: IncidentKind, snap: HealthSnapshot?) {
        if (act(s).remove(k)) onIncident(Incident(s, IncidentKind.RECOVERED, "info", "$k 已恢复", snap))
    }

    private fun tick() {
        val online = onlineServers()
        // 掉线：曾巡检到、这轮不在在线集
        for (s in prev.keys.toList()) if (s !in online) raise(s, IncidentKind.OFFLINE, "critical", "子服已从总线断开", null)

        for (s in online) {
            val snap = probe(s)
            if (snap == null) { raise(s, IncidentKind.OFFLINE, "critical", "子服无响应（探测超时）", null); continue }
            clear(s, IncidentKind.OFFLINE, snap)

            // TPS（<0 = 该服不提供 TPS，跳过）
            if (snap.tps in 0.0..cfg.tpsMin) raise(s, IncidentKind.LAG, "warn", "TPS=${snap.tps}（阈值 <${cfg.tpsMin}）", snap)
            else if (snap.tps > cfg.tpsMin) clear(s, IncidentKind.LAG, snap)

            // 内存占用
            val pct = if (snap.memMaxMb > 0) (snap.memUsedMb * 100 / snap.memMaxMb).toInt() else 0
            if (pct >= cfg.memPct) raise(s, IncidentKind.MEMORY, "warn", "内存 $pct%（${snap.memUsedMb}/${snap.memMaxMb}MB）", snap)
            else clear(s, IncidentKind.MEMORY, snap)

            // 在线骤降（瞬时事件，不进边沿集，直接报）
            val p = prev[s]
            if (cfg.playerDrop > 0 && p != null && p.online >= 0 && snap.online >= 0 && p.online - snap.online >= cfg.playerDrop)
                onIncident(Incident(s, IncidentKind.PLAYER_DROP, "warn",
                    "在线 ${p.online}→${snap.online}（${cfg.intervalSec}s 内掉 ${p.online - snap.online} 人）", snap))

            prev[s] = snap
        }
    }
}
