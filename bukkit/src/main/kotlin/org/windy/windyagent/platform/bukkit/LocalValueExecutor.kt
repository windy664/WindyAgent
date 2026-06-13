package org.windy.windyagent.platform.bukkit

import org.windy.windyagent.command.ValueExecutor
import org.windy.windyagent.platform.bukkit.item.ItemService
import org.windy.windyagent.safety.TrustLevel

/**
 * 子服（Bukkit）本地 value 执行后端：无子服名，`value <子命令> [参数]` 直接调本服 [ItemService]/引擎。
 * 与中心的 [总线远端动作] 共用同一个 ItemService 实例 → 命令/工具/总线三个前门、同一来源。
 */
class LocalValueExecutor(private val items: ItemService) : ValueExecutor {
    override fun execute(sub: String, rest: String, trust: TrustLevel): String = when (sub) {
        "build" -> items.build()
        "status" -> items.status()
        "orphans" -> items.orphans()
        "get" -> if (rest.isBlank()) "用法：value get <物品>" else items.get(rest)
        "unset" -> if (rest.isBlank()) "用法：value unset <物品>" else items.unset(rest)
        "set" -> {
            val t = rest.split(Regex("\\s+"), limit = 3)
            val item = t.getOrNull(0)?.takeIf { it.isNotBlank() }
            val value = t.getOrNull(1)?.toDoubleOrNull()
            if (item == null || value == null) "用法：value set <物品> <价> [备注]"
            else items.set(item, value, t.getOrNull(2) ?: "")
        }
        "servers" -> "本服为子服节点，servers 是中心（Velocity）命令。"
        else -> "未知子命令「$sub」。"
    }
}
