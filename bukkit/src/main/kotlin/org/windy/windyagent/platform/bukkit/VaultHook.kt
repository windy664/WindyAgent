package org.windy.windyagent.platform.bukkit

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit

/**
 * Vault 经济接入（软依赖）。
 *
 * Vault 的 [Economy] 类由 Vault 插件在运行时提供（本插件 compileOnly + softdepend），
 * 故对 Economy 的引用都收拢在此处、且先判存在再触碰，未装 Vault 的服不会触发 NoClassDefFound。
 * 解析结果缓存（economy 提供方在 onEnable 后即稳定）。
 */
object VaultHook {

    @Volatile private var resolved = false
    @Volatile private var economy: Economy? = null

    fun economy(): Economy? {
        if (!resolved) synchronized(this) {
            if (!resolved) {
                economy = runCatching {
                    if (Bukkit.getPluginManager().getPlugin("Vault") == null) null
                    else Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider
                }.getOrNull()
                resolved = true
            }
        }
        return economy
    }
}
