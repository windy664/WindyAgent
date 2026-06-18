package org.windy.windyagent.behavior

import org.slf4j.LoggerFactory

/**
 * 玩家生命周期管理：跟踪玩家旅程阶段，自动触发对应策略。
 *
 * 阶段划分（基于行为数据，非主观判断）：
 *  - NEWBIE    首次上线 ≤ newbieDays 天，且在线时长很短
 *  - ACTIVE    近期有稳定活跃（近 churnDays 天内上线，且达一定时长）
 *  - ENGAGED   高度活跃（在线时长超 activeMinutes，常驻玩家）
 *  - DORMANT   曾经活跃但超过 churnDays 天未上线（沉睡）
 *  - CHURNED   超过 churnDays*2 天未上线（流失）
 *  - RETURNED  从沉睡/流失状态重新上线（回归）
 *
 * 每个阶段可绑定自动策略（Agent 可读取并执行）。
 */
class PlayerLifecycle(
    private val db: BehaviorDatabase,
    private val churnDays: Int = 7,
    private val activeMinutes: Int = 300,
    private val newbieDays: Int = 3
) {
    private val log = LoggerFactory.getLogger(PlayerLifecycle::class.java)

    /** 玩家生命周期阶段。 */
    enum class Stage {
        NEWBIE, ACTIVE, ENGAGED, DORMANT, CHURNED, RETURNED
    }

    /** 一条生命周期记录。 */
    data class LifecycleInfo(
        val name: String,
        val uuid: String,
        val stage: Stage,
        val playtimeHours: Double,
        val daysSinceLastSeen: Double,
        val sessions: Int,
        /** 该阶段建议的自动策略。 */
        val suggestedActions: List<String>
    )

    /**
     * 查询单个玩家的生命周期阶段。
     */
    fun getPlayerStage(name: String): LifecycleInfo? {
        val p = db.player(name) ?: return null
        return classify(p)
    }

    /**
     * 批量查询所有玩家的生命周期分布。
     */
    fun getStageDistribution(): Map<Stage, List<LifecycleInfo>> {
        // 从数据库获取所有玩家（复用 stats 里的查询方式）
        val profiles = db.allProfiles()
        return profiles.map { classify(it) }
            .groupBy { it.stage }
    }

    /**
     * 获取某阶段的玩家列表（供 Agent 查询 "哪些新人玩家" / "哪些人快流失了"）。
     */
    fun getPlayersByStage(stage: Stage): List<LifecycleInfo> {
        return db.allProfiles().map { classify(it) }.filter { it.stage == stage }
    }

    /**
     * 获取需要关注的玩家（沉睡 + 流失 + 回归），供 Agent 主动运维。
     */
    fun getAttentionNeeded(): List<LifecycleInfo> {
        return db.allProfiles().map { classify(it) }
            .filter { it.stage in setOf(Stage.DORMANT, Stage.CHURNED, Stage.RETURNED) }
            .sortedBy { it.daysSinceLastSeen }
    }

    private fun classify(p: Profile): LifecycleInfo {
        val now = System.currentTimeMillis()
        val hours = p.playtimeSec / 3600.0
        val daysSinceFirst = (now - p.firstSeen) / 86_400_000.0
        val daysSinceLast = (now - p.lastSeen) / 86_400_000.0
        val minutesPlayed = p.playtimeSec / 60.0

        val stage = when {
            // 回归：曾经沉睡/流失，但最近又上线了
            daysSinceLast <= 1 && daysSinceFirst > churnDays && minutesPlayed < activeMinutes -> Stage.RETURNED
            // 新人
            daysSinceFirst <= newbieDays -> Stage.NEWBIE
            // 流失
            daysSinceLast > churnDays * 2 -> Stage.CHURNED
            // 沉睡
            daysSinceLast > churnDays -> Stage.DORMANT
            // 高度活跃
            minutesPlayed >= activeMinutes -> Stage.ENGAGED
            // 活跃
            daysSinceLast <= churnDays -> Stage.ACTIVE
            else -> Stage.ACTIVE
        }

        val actions = ACTIONS[stage] ?: emptyList()
        return LifecycleInfo(p.name, p.uuid, stage, hours, daysSinceLast, p.sessions, actions)
    }

    companion object {
        /** 每个阶段的建议自动策略（Agent 可读取并执行）。 */
        val ACTIONS = mapOf(
            Stage.NEWBIE to listOf(
                "发送欢迎消息，介绍服务器规则和常用命令",
                "赠送新手礼包（如基础工具+食物）",
                "推荐适合新人的区域或活动"
            ),
            Stage.ACTIVE to listOf(
                "定期推送服务器活动信息",
                "推荐进阶玩法和社区活动"
            ),
            Stage.ENGAGED to listOf(
                "邀请参与管理或社区建设",
                "推送高级内容和稀有活动",
                "记录偏好到长期记忆"
            ),
            Stage.DORMANT to listOf(
                "发送召回消息（好久不见，服务器有新内容）",
                "推送近期更新和活动预告",
                "赠送回归礼包"
            ),
            Stage.CHURNED to listOf(
                "发送强力召回（专属优惠/限时活动）",
                "了解流失原因（如有反馈渠道）",
                "标记为重点关注"
            ),
            Stage.RETURNED to listOf(
                "发送欢迎回归消息",
                "赠送回归礼包",
                "介绍近期更新和变化",
                "安排老玩家带新"
            )
        )
    }
}
