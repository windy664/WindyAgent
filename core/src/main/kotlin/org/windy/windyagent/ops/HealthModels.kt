package org.windy.windyagent.ops

/** 某子服一次健康快照（子服 health_query 回报，中心解析）。tps<0 / online<0 表示该项不可得。 */
data class HealthSnapshot(
    val server: String,
    val reachable: Boolean,
    val tps: Double,
    val online: Int,
    val memUsedMb: Long,
    val memMaxMb: Long,
    // 子服核心类型（子服探测上送）：platform=neoforge-hybrid/forge-hybrid/paper/spigot/craftbukkit
    val platform: String = "",
    val mcVersion: String = "",
    val brand: String = "",
    val modCount: Int = -1,
    val ts: Long = System.currentTimeMillis()
)

/** 告警类型。RECOVERED = 某项异常恢复正常。 */
enum class IncidentKind { OFFLINE, LAG, MEMORY, PLAYER_DROP, RECOVERED }

/** 一条运维告警（哨兵评估翻转时产生）。 */
data class Incident(
    val server: String,
    val kind: IncidentKind,
    val severity: String,      // critical | warn | info
    val detail: String,
    val snapshot: HealthSnapshot?
)
