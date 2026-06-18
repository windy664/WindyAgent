package org.windy.windyagent.platform.bukkit.cmi

import com.Zrips.CMI.CMI
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.profile.ProfileDataSource
import java.util.concurrent.ConcurrentHashMap

/**
 * CMI 画像数据源：玩家加入时拉一次 CMI 数据缓存，之后读缓存不查 CMI。
 *
 * 缓存策略：
 *  - 加入 → 立即预拉（[onJoin]）。
 *  - 在线期间 → [snapshot] 读缓存，零 CMI 开销。
 *  - 退出 → 清缓存（[onQuit]）。
 *
 * key 带 "cmi." 前缀：balance / homes / lastLogin / lastLogoff / lastIp。
 */
class CmiProfileSource(private val plugin: JavaPlugin) : ProfileDataSource {

    override val name = "cmi"

    private val cache = ConcurrentHashMap<String, Map<String, String>>()

    override fun isAvailable(): Boolean = Bukkit.getPluginManager().getPlugin("CMI") != null

    override fun onJoin(player: String) {
        runCatching { fetchFromCmi(player) }
            .onSuccess { cache[player] = it }
            .onFailure { plugin.logger.warning("[CmiProfile] 预拉 $player 失败：${it.message}") }
    }

    override fun onQuit(player: String) {
        cache.remove(player)
    }

    override fun snapshot(player: String): Map<String, String> {
        // 缓存命中直接返回
        cache[player]?.let { return it }
        // 缓存未命中（可能刚重启、缓存还没预热）→ 实时拉一次并缓存
        if (!isAvailable()) return emptyMap()
        return runCatching {
            fetchFromCmi(player).also { cache[player] = it }
        }.getOrDefault(emptyMap())
    }

    /** 从 CMI 拉取玩家数据，返回带 "cmi." 前缀的 map。 */
    private fun fetchFromCmi(player: String): Map<String, String> {
        val cmi = CMI.getInstance() ?: return emptyMap()
        val user = cmi.getUser(null, player, player) ?: return emptyMap()
        val data = LinkedHashMap<String, String>()

        // 经济
        cmi.economyManager.vaultManager.vaultEconomy?.let { econ ->
            val balance = econ.getBalance(Bukkit.getOfflinePlayer(player))
            data["cmi.balance"] = econ.format(balance)
            data["cmi.balance_raw"] = balance.toString()
        }
        // 家
        user.homes?.let { data["cmi.homes"] = it.size.toString() }
        // 时间
        if (user.lastLogin > 0) data["cmi.lastLogin"] = user.lastLogin.toString()
        if (user.lastLogoff > 0) data["cmi.lastLogoff"] = user.lastLogoff.toString()
        // IP（供画像判断地区/多账号）
        val ip = user.lastIp
        if (!ip.isNullOrBlank()) data["cmi.lastIp"] = ip

        return data
    }
}
