package org.windy.windyagent.platform.bukkit.profile

import org.windy.windyagent.platform.bukkit.behavior.BehaviorService
import org.windy.windyagent.profile.ProfileDataSource

/**
 * 把 :behavior 模块的**行为画像**（主玩法 / 活跃时段 / 行为标签 / 在线时长…）作为一个数据源贡献进
 * [org.windy.windyagent.profile.ProfileDataRegistry]，让 Agent 的聚合画像工具（PlayerProfileTool /
 * 跨服 RemotePlayerProfileTool）**一次拿到「属性画像（PAPI 等外部插件）+ 行为画像」**，
 * 不再两个"画像"各说各话（原来 behavior 与 profile 完全隔离）。
 *
 * 纯适配器：解读逻辑全在 [org.windy.windyagent.behavior.BehaviorAnalytics.playerSummary]（behavior 模块，
 * 阈值单一来源），本类只做「命名空间前缀 + ProfileDataSource 接口适配」。key 带 "behavior." 前缀
 * （如 "behavior.主玩法"），对齐 [PapiProfileSource] 的 "papi." 约定，避免跨 Source 冲突、AI 读着直观。
 *
 * snapshot 走 behavior 自己的本地 SQLite（非远端），玩家画像查询是管理员触发的低频操作，可接受。
 */
class BehaviorProfileSource(private val behavior: BehaviorService) : ProfileDataSource {

    override val name = "behavior"

    override fun isAvailable(): Boolean = true

    override fun snapshot(player: String): Map<String, String> =
        behavior.playerSummary(player)?.mapKeys { "behavior.${it.key}" } ?: emptyMap()
}
