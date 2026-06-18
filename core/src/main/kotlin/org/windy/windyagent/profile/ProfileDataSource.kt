package org.windy.windyagent.profile

/**
 * 画像数据源抽象：每个插件（CMI、LuckPerms、Essentials…）实现此接口，
 * 提供玩家的结构化数据快照。
 *
 * 设计原则：
 *  - 各 Source 自管缓存，[snapshot] 必须轻量（读缓存，不查远端）。
 *  - [onJoin]/[onQuit] 由 Bukkit 事件层统一分发，Source 据此预热/清理。
 *  - key 带命名空间前缀（如 "cmi.balance"），避免跨 Source 冲突。
 *  - 平台无关：不依赖 Bukkit API，可在 core 模块定义。
 */
interface ProfileDataSource {

    /** 数据源名称（如 "cmi"、"luckperms"）。 */
    val name: String

    /** 对应插件是否已安装可用。 */
    fun isAvailable(): Boolean

    /** 玩家当前数据快照（从缓存读取，key 如 "cmi.balance"、"cmi.homes"）。 */
    fun snapshot(player: String): Map<String, String>

    /** 玩家加入时调用——预热缓存。 */
    fun onJoin(player: String) {}

    /** 玩家退出时调用——清理缓存。 */
    fun onQuit(player: String) {}
}
