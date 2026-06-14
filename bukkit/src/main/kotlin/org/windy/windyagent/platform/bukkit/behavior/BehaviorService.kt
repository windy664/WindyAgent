package org.windy.windyagent.platform.bukkit.behavior

import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.AgentConfig
import org.windy.windyagent.behavior.BehaviorAnalytics
import org.windy.windyagent.behavior.BehaviorDatabase

/**
 * bukkit 侧的行为子系统**装配门面**：把平台无关的数据平台（[BehaviorDatabase] + [BehaviorAnalytics]，
 * 在 :behavior 模块）与本服特有的采集器（[BehaviorTracker]，吃 Bukkit 事件）接到一起，并把分析 JSON
 * 透传给总线/看板消费方（[org.windy.windyagent.platform.bukkit.BukkitCapabilityHandler]）。
 *
 * 分层：采集器只往 db 写计数；analytics 只读 db 出汇总；本类只做生命周期 + 转发，**不含分析逻辑**。
 */
class BehaviorService(
    private val tracker: BehaviorTracker,
    private val analytics: BehaviorAnalytics
) {
    fun start() = tracker.start()
    fun stop() = tracker.stop()

    // —— 以下全是对 analytics（:behavior 模块）的转发，bukkit 这边不重复分析逻辑 ——
    fun statsJson(): String = analytics.statsJson()
    fun boardJson(): String = analytics.boardJson()
    fun segmentsJson(): String = analytics.segmentsJson()
    fun playerJson(name: String): String = analytics.playerJson(name)
    fun wordsJson(source: String, limit: Int): String = analytics.wordsJson(source, limit)
    /** 接收 Velocity 代理层送来的聊天词频（绕开 Bukkit 聊天事件 bug）。 */
    fun recordChatWords(words: Map<String, Int>) = analytics.recordChatWords(words)

    companion object {
        fun build(plugin: JavaPlugin, cfg: AgentConfig): BehaviorService? {
            if (!cfg.behaviorEnabled()) return null
            val db = BehaviorDatabase(plugin.dataFolder.toPath().resolve("behavior.db"))
            val tracker = BehaviorTracker(plugin, db, cfg.behaviorFlushIntervalSec(), cfg.behaviorRetentionDays(), cfg.behaviorTrackChat())
            val analytics = BehaviorAnalytics(db, cfg.behaviorChurnDays(), cfg.behaviorActiveMinutes(), cfg.behaviorNewbieDays())
            return BehaviorService(tracker, analytics)
        }
    }
}
