package org.windy.windyagent.bridge

/**
 * 昕途（XingtuBot）联动器 —— [ImConnector] 的昕途实现，Velocity 与 Bukkit 两端复用。
 *
 * <p><b>惰性隔离（关键）</b>：本类<b>方法体只用平台/通用类型（Any?/String）做「昕途是否在」的探测</b>，
 * 探测为否即早返回，<b>全程不触碰任何昕途类型</b>；确认昕途在之后，才把控制权交给独立的
 * [XingtuBotWiring]——所有昕途类型（XingtuBotHost 等）都收敛在那个类里。
 * 这样 [ImConnectors] 无条件调本类 [tryInstall] 时，昕途未安装也不会加载昕途类、不抛
 * NoClassDefFoundError（与 GuildShelter 的 Bukkit/NeoForge 惰性隔离同理，不用反射）。
 */
object XingtuBotConnector : ImConnector {

    override val platformId: String = "xingtubot-qq"

    override fun tryInstall(env: InstallEnv): Boolean {
        // —— 仅用 Any? 探测昕途是否在，绝不在此触碰昕途类型 ——
        // Velocity 昕途主类 id="xingtubotvelocity"；Bukkit 昕途主类名="XingtuBot"。
        val present = env.lookupPlugin("xingtubotvelocity") != null || env.lookupPlugin("XingtuBot") != null
        if (!present) return false
        // 确认在 → 交给独立 wiring 类触碰昕途类型（此调用发生前不加载任何昕途类）。
        return XingtuBotWiring.wire(env)
    }
}
