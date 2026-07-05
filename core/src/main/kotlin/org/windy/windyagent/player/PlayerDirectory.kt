package org.windy.windyagent.player

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 玩家管理「聚合中枢」—— 可扩展的玩家信息与操作注册表。
 *
 * <p>设计目标（呼应「后续接入更多插件集成信息」）：玩家管理面板不写死字段，而是<b>聚合多来源</b>：
 *   - <b>基础行</b>：由平台（Velocity 代理 / Bukkit 服）提供在线玩家的基础信息（名字/子服/延迟…）；
 *   - <b>扩展列</b>：各插件注册 [PlayerInfoContributor] 贡献自己的字段（如经济余额、领地数、封禁状态…）；
 *   - <b>扩展操作</b>：各插件注册 [PlayerAction] 贡献自己的操作（如封禁、改余额、加白名单…）。
 *
 * <p>面板经 /api/players 拿到「基础行 + 所有贡献列 + 所有操作」的聚合结果，自动渲染。
 * 新增集成 = 注册一个 Contributor / Action，<b>后端 handler 与前端面板均无需改动</b>
 * （与 IM 联动的 ImConnector、GuildShelter 的 provider 同一套可扩展哲学）。
 */
object PlayerDirectory {

    /** 一列扩展字段的元信息。 */
    data class Column(val key: String, val label: String)

    /** 一个玩家操作（如踢人/封禁）。danger=破坏性（前端二次确认）。 */
    interface PlayerAction {
        val id: String
        val label: String
        val danger: Boolean
        /** 执行。@return 给操作者看的结果文案。 */
        fun run(player: String, args: Map<String, String>): String
    }

    /** 一个字段贡献者（如经济插件贡献「余额」列）。 */
    interface PlayerInfoContributor {
        val id: String
        /** 本贡献者提供的列（表头）。 */
        fun columns(): List<Column>
        /** 为给定在线玩家批量取字段：playerName -> (columnKey -> value)。 */
        fun fieldsFor(players: List<String>): Map<String, Map<String, String>>
    }

    /** 基础行：至少含 name；可含 server/ping 等平台已知字段。由平台侧提供。 */
    @Volatile
    private var baseSupplier: (() -> List<Map<String, Any?>>)? = null

    private val contributors = CopyOnWriteArrayList<PlayerInfoContributor>()
    private val actions = CopyOnWriteArrayList<PlayerAction>()

    /** 平台侧注册「在线玩家基础行」供给器。 */
    fun setBaseSupplier(supplier: () -> List<Map<String, Any?>>) { baseSupplier = supplier }

    /** 注册字段贡献者（幂等：同 id 覆盖）。 */
    fun registerContributor(c: PlayerInfoContributor) {
        contributors.removeIf { it.id == c.id }
        contributors.add(c)
    }

    /** 注册操作（幂等：同 id 覆盖）。 */
    fun registerAction(a: PlayerAction) {
        actions.removeIf { it.id == a.id }
        actions.add(a)
    }

    /** 所有贡献列（供前端建表头）。 */
    fun columns(): List<Column> = contributors.flatMap { it.columns() }

    /** 所有操作的元信息（供前端建按钮）。 */
    fun actions(): List<PlayerAction> = actions.toList()

    /** 聚合快照：基础行 + 各贡献者字段合并。 */
    fun snapshot(): List<Map<String, Any?>> {
        val base = runCatching { baseSupplier?.invoke() }.getOrNull() ?: emptyList()
        if (base.isEmpty()) return emptyList()
        val names = base.mapNotNull { it["name"] as? String }
        // 预取各贡献者字段（批量，避免逐玩家 N 次调用）
        val enrichments = contributors.map { c -> runCatching { c.fieldsFor(names) }.getOrElse { emptyMap() } }
        return base.map { row ->
            val name = row["name"] as? String
            val merged = LinkedHashMap<String, Any?>(row)
            if (name != null) for (e in enrichments) e[name]?.let { merged.putAll(it) }
            merged
        }
    }

    /** 执行某操作。未知 id 返回提示。 */
    fun runAction(id: String, player: String, args: Map<String, String>): String =
        actions.firstOrNull { it.id == id }?.let { runCatching { it.run(player, args) }.getOrElse { e -> "操作失败：${e.message}" } }
            ?: "未知操作：$id"
}
