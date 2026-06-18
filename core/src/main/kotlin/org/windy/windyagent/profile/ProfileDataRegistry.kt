package org.windy.windyagent.profile

/**
 * 画像数据注册表：收集所有 [ProfileDataSource]，提供合并快照。
 *
 *  - 各模块启动时 [register] 自己的 Source（CMI、LuckPerms…）。
 *  - AI 查画像时调 [snapshot] 合并所有可用 Source 的数据。
 *  - 玩家加入/退出事件调 [onJoin]/[onQuit] 分发给所有 Source。
 */
class ProfileDataRegistry {

    private val sources = mutableListOf<ProfileDataSource>()

    /** 注册一个数据源（幂等：同名 Source 不重复注册）。 */
    fun register(source: ProfileDataSource) {
        if (sources.any { it.name == source.name }) return
        sources.add(source)
    }

    /** 获取某个数据源（按名称查找，供工具层直接读缓存用）。 */
    fun source(name: String): ProfileDataSource? = sources.firstOrNull { it.name == name }

    /** 所有已注册且可用的 Source。 */
    fun available(): List<ProfileDataSource> = sources.filter { it.isAvailable() }

    /**
     * 合并所有可用 Source 的数据快照。
     * 后注册的 Source 覆盖同名 key（后注册优先级更高）。
     */
    fun snapshot(player: String): Map<String, String> {
        val merged = LinkedHashMap<String, String>()
        for (src in available()) {
            runCatching { merged.putAll(src.snapshot(player)) }
        }
        return merged
    }

    /** 玩家加入事件——分发给所有 Source。 */
    fun onJoin(player: String) {
        for (src in available()) {
            runCatching { src.onJoin(player) }
        }
    }

    /** 玩家退出事件——分发给所有 Source。 */
    fun onQuit(player: String) {
        for (src in available()) {
            runCatching { src.onQuit(player) }
        }
    }
}
