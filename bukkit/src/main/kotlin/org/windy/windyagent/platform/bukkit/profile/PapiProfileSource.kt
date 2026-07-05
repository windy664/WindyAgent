package org.windy.windyagent.platform.bukkit.profile

import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.profile.ProfileDataSource
import java.util.concurrent.ConcurrentHashMap

/**
 * PlaceholderAPI 画像数据源。取代原先逐插件写的 CMI 集成：PAPI 本就是给各插件统一暴露玩家变量用的，
 * 画像直接读占位符即可，无需为每个插件写 compileOnly 集成。
 *
 * **半自动发现**：PAPI 只暴露「装了哪些 expansion(前缀)」（[PlaceholderAPI.getRegisteredIdentifiers]），
 * 给不出「每个 expansion 有哪些完整占位符」（参数由各插件内部自由解析，PAPI 不维护目录）。故我们内置一份
 * [KNOWN_BY_EXPANSION] 语法库（按 expansion 分组的常用玩家变量），启动时只纳入**已装 expansion**对应的那几组
 * ——装了 LuckPerms 就自动长出权限组维度，没装就没有。服主零配置即用。
 *
 * config 的 `profiles.papi-placeholders`（[configPlaceholders]）是**额外/覆盖**：想加语法库没覆盖的冷门维度
 * （领地数、投票数…）就在 config 写「显示名: 占位符」，默认可留空全交给半自动。
 *
 * 缓存策略：加入预拉（[onJoin]，此时在线，能取需在线上下文的占位符）→ 在线读缓存 → 退出清（[onQuit]）。
 * key 带 "papi." 前缀 + 中文显示名（如 "papi.余额"），避免跨 Source 冲突、AI 读着直观。
 */
class PapiProfileSource(
    private val plugin: JavaPlugin,
    /** config 手配的「显示名 → 占位符」：额外维度 + 覆盖，最高优先级。默认空 = 全交给半自动发现。 */
    private val configPlaceholders: Map<String, String>
) : ProfileDataSource {

    override val name = "papi"

    private val cache = ConcurrentHashMap<String, Map<String, String>>()

    /** 惰性算出的「有效占位符清单」（半自动发现 + config 叠加），首次解析时定一次并缓存。 */
    @Volatile
    private var effective: Map<String, String>? = null

    override fun isAvailable(): Boolean = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null

    override fun onJoin(player: String) {
        runCatching { fetch(player) }
            .onSuccess { cache[player] = it }
            .onFailure { plugin.logger.warning("[PapiProfile] 预拉 $player 失败：${it.message}") }
    }

    override fun onQuit(player: String) {
        cache.remove(player)
    }

    override fun snapshot(player: String): Map<String, String> {
        cache[player]?.let { return it }
        if (!isAvailable()) return emptyMap()
        return runCatching { fetch(player).also { cache[player] = it } }.getOrDefault(emptyMap())
    }

    /**
     * 有效占位符清单 = 【半自动】已装 expansion 命中语法库的组 + 【config】手配（覆盖同名）。
     * 惰性算一次缓存：getRegisteredIdentifiers 运行时基本稳定；ecloud 运行时下载的极少数场景重启即可刷新。
     */
    private fun effectivePlaceholders(): Map<String, String> {
        effective?.let { return it }
        val registered = runCatching {
            PlaceholderAPI.getRegisteredIdentifiers().map { it.lowercase() }.toSet()
        }.getOrDefault(emptySet())

        val out = LinkedHashMap<String, String>()
        // 半自动：只纳入已装 expansion 对应的那几组占位符
        for ((expId, group) in KNOWN_BY_EXPANSION) {
            if (expId in registered) out.putAll(group)
        }
        // config 手配叠加（额外维度 + 覆盖同显示名）；对应 expansion 没装则取值时被 isMeaningful 过滤掉，无害
        out.putAll(configPlaceholders)

        plugin.logger.info("[PapiProfile] 画像启用 ${out.size} 个维度（检测到 expansion: " +
            "${registered.sorted().joinToString().ifEmpty { "无" }}）")
        effective = out
        return out
    }

    /** 逐个解析有效清单里的占位符，过滤掉未被解析（原样返回）或空/无效的项。 */
    private fun fetch(player: String): Map<String, String> {
        if (!isAvailable()) return emptyMap()
        // 在线玩家也是 OfflinePlayer；PAPI 对在线者会自动用其 Player 上下文，故统一走 OfflinePlayer 重载。
        val online = Bukkit.getPlayerExact(player)
        @Suppress("DEPRECATION") // 按名取 OfflinePlayer：1.12 兼容，与项目其它处一致
        val target = online ?: Bukkit.getOfflinePlayer(player)
        val out = LinkedHashMap<String, String>()
        for ((label, expr) in effectivePlaceholders()) {
            val value = runCatching { PlaceholderAPI.setPlaceholders(target, expr) }
                .getOrNull()?.trim().orEmpty()
            if (isMeaningful(value, expr)) out["papi.$label"] = value
        }
        return out
    }

    /**
     * 值是否有意义（值得进画像）：
     *  - 非空；
     *  - 不等于原占位符表达式（相等 = 没有 expansion 认领它，PAPI 原样返回）；
     *  - 不是 null/N/A/无 之类占位符「无值」约定。
     */
    private fun isMeaningful(value: String, expr: String): Boolean {
        if (value.isEmpty() || value == expr) return false
        val lower = value.lowercase()
        return lower != "null" && lower != "n/a" && lower != "无"
    }

    companion object {
        /**
         * 内置语法库：`expansion id（小写） → (显示名 → 占位符)`。按已装 expansion 半自动纳入。
         *
         * 只收「玩家维度、相对稳定、非 UI 格式化」的常用变量——不收 server 级(%server_tps%)、瞬时(%player_ping%)、
         * 关系型(%rel_*%)。expansion id 即各插件 PlaceholderExpansion.getIdentifier() 的值（PAPI 内部按小写存）。
         * 某维度取不到值（版本占位符语法不同）会被过滤，不影响其它维度；要补冷门维度用 config papi-placeholders。
         */
        val KNOWN_BY_EXPANSION: Map<String, Map<String, String>> = linkedMapOf(
            // Vault（/papi ecloud download Vault）：经济
            "vault" to linkedMapOf(
                "余额" to "%vault_eco_balance%"
            ),
            // LuckPerms（/papi ecloud download LuckPerms）：权限组 / 前缀
            "luckperms" to linkedMapOf(
                "权限组" to "%luckperms_primary_group_name%",
                "称号前缀" to "%luckperms_prefix%"
            ),
            // Statistic（/papi ecloud download Statistic）：原版统计
            "statistic" to linkedMapOf(
                "总在线时长" to "%statistic_time_played%",
                "死亡次数" to "%statistic_deaths%",
                "击杀数" to "%statistic_player_kills%"
            ),
            // Player（/papi ecloud download Player）：原版玩家状态
            "player" to linkedMapOf(
                "所在世界" to "%player_world%",
                "等级" to "%player_level%",
                "血量" to "%player_health%"
            ),
            // CMI：登录时间 / IP（供画像判断地区、多账号）
            "cmi" to linkedMapOf(
                "最后登录" to "%cmi_user_lastlogin_format%",
                "IP" to "%cmi_user_ip%"
            )
        )
    }
}
