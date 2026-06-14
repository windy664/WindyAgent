package org.windy.windyagent.platform.bukkit

import org.bukkit.plugin.java.JavaPlugin

/**
 * 子服「核心类型」探测：纯反射 / 品牌字符串判别，零依赖、探不到就降级。
 * 结果上送中心（搭 health_query），让中心据此区分子服形态、解锁差异化能力
 * （如 NeoForge 混合端才挂"查模组 / 分维度 TPS"之类专属工具）。
 *
 * 探测一次即缓存（服务端形态运行期不变）。
 */
object ServerProfile {

    data class Data(
        val brand: String,       // CraftBukkit / Paper / Spigot …（Bukkit.getName）
        val mcVersion: String,   // 1.21
        val platform: String,    // neoforge-hybrid | forge-hybrid | paper | spigot | craftbukkit
        val modCount: Int,       // 模组数（仅 forge/neoforge 系，否则 0；探不到 -1）
        val hasTps: Boolean      // 是否提供 Server.getTPS()
    )

    @Volatile private var cached: Data? = null

    fun detect(plugin: JavaPlugin): Data = cached ?: compute(plugin).also { cached = it }

    private fun classPresent(cn: String) = runCatching { Class.forName(cn); true }.getOrDefault(false)

    private fun compute(plugin: JavaPlugin): Data {
        val server = plugin.server
        val brand = runCatching { server.name }.getOrDefault("?")
        val bukkitVer = runCatching { server.bukkitVersion }.getOrDefault("")           // 形如 1.21-R0.1-SNAPSHOT
        val mc = bukkitVer.substringBefore("-").ifBlank { runCatching { server.version }.getOrDefault("") }

        val neoforge = classPresent("net.neoforged.fml.ModList") || classPresent("net.neoforged.fml.common.Mod")
        val forge = classPresent("net.minecraftforge.fml.ModList") || classPresent("net.minecraftforge.common.MinecraftForge")
        val paper = classPresent("io.papermc.paper.threadedregions.RegionizedServer") ||
            classPresent("com.destroystokyo.paper.PaperConfig") ||
            classPresent("io.papermc.paper.configuration.Configuration")

        val platform = when {
            neoforge -> "neoforge-hybrid"
            forge -> "forge-hybrid"
            paper -> "paper"
            brand.contains("Spigot", true) -> "spigot"
            else -> "craftbukkit"
        }

        val modCount = when {
            neoforge -> modListSize("net.neoforged.fml.ModList")
            forge -> modListSize("net.minecraftforge.fml.ModList")
            else -> 0
        }

        val hasTps = runCatching { server.javaClass.getMethod("getTPS"); true }.getOrDefault(false)
        return Data(brand, mc, platform, modCount, hasTps)
    }

    /** 反射 ModList.get().getMods().size —— Forge/NeoForge 通用。探不到回 -1。 */
    private fun modListSize(modListClass: String): Int = runCatching {
        val ml = Class.forName(modListClass).getMethod("get").invoke(null)
        val mods = ml.javaClass.getMethod("getMods").invoke(ml) as? List<*>
        mods?.size ?: -1
    }.getOrDefault(-1)
}
