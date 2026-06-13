package org.windy.windyagent.platform.bukkit.behavior

import com.fasterxml.jackson.databind.ObjectMapper
import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.AgentConfig

/**
 * 行为分析门面：启动采集 + 对外出 JSON（供总线 → VC 看板消费）。跑在子服侧（事件源头）。
 * T0 描述统计 + T1 规则分群 都在这算（SQL 聚合），只把汇总 JSON 过总线。
 */
class BehaviorService(
    private val db: BehaviorDatabase,
    private val tracker: BehaviorTracker,
    private val churnDays: Int,
    private val activeMinutes: Int,
    private val newbieDays: Int
) {
    private val mapper = ObjectMapper()

    fun start() = tracker.start()
    fun stop() = tracker.stop()

    /** T0 面板。 */
    fun statsJson(): String {
        val s = db.stats(System.currentTimeMillis())
        val n = mapper.createObjectNode()
        n.put("totalPlayers", s.totalPlayers)
        n.put("active1d", s.active1d)
        n.put("active7d", s.active7d)
        n.put("newToday", s.newToday)
        n.put("avgPlaytimeMin", s.avgPlaytimeSec / 60)
        n.put("totalDeaths", s.totalDeaths)
        n.put("totalBlocksBroken", s.totalBlocksBroken)
        n.put("totalCrafts", s.totalCrafts)
        n.put("totalAdvancements", s.totalAdvancements)
        val arr = n.putArray("topPlaytime")
        s.topPlaytime.forEach { (name, sec) -> arr.addObject().put("name", name).put("playtimeMin", sec / 60) }
        return n.toString()
    }

    /** T1 分群。 */
    fun segmentsJson(): String {
        val seg = db.segments(System.currentTimeMillis(), churnDays, activeMinutes, newbieDays)
        val n = mapper.createObjectNode()
        seg.forEach { (k, v) -> n.put(k, v) }
        return n.toString()
    }

    /** 单人画像。 */
    fun playerJson(name: String): String {
        val p = db.player(name) ?: return mapper.createObjectNode().put("error", "未找到玩家「$name」（可能还没产生行为数据）").toString()
        val n = mapper.createObjectNode()
        n.put("name", p.name); n.put("uuid", p.uuid)
        n.put("firstSeen", p.firstSeen); n.put("lastSeen", p.lastSeen)
        n.put("playtimeMin", p.playtimeSec / 60); n.put("sessions", p.sessions)
        n.put("deaths", p.deaths); n.put("commands", p.commands); n.put("chats", p.chats)
        n.put("blocksPlaced", p.blocksPlaced); n.put("blocksBroken", p.blocksBroken)
        n.put("crafts", p.crafts); n.put("advancements", p.advancements)
        return n.toString()
    }

    companion object {
        fun build(plugin: JavaPlugin, cfg: AgentConfig): BehaviorService? {
            if (!cfg.behaviorEnabled()) return null
            val db = BehaviorDatabase(plugin.dataFolder.toPath().resolve("behavior.db"))
            val tracker = BehaviorTracker(plugin, db, cfg.behaviorFlushIntervalSec(), cfg.behaviorRetentionDays())
            return BehaviorService(db, tracker, cfg.behaviorChurnDays(), cfg.behaviorActiveMinutes(), cfg.behaviorNewbieDays())
        }
    }
}
