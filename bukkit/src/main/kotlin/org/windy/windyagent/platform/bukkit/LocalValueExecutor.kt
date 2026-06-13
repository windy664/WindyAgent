package org.windy.windyagent.platform.bukkit

import org.windy.windyagent.command.ValueExecutor
import org.windy.windyagent.llm.LLMProvider
import org.windy.windyagent.platform.bukkit.item.ItemService
import org.windy.windyagent.safety.TrustLevel
import org.windy.windyagent.valuation.LlmRootPricer

/**
 * 子服（Bukkit）本地 value 执行后端：无子服名，`value <子命令> [参数]` 直接调本服 [ItemService]/引擎。
 * 与中心的 [总线远端动作] 共用同一个 ItemService 实例 → 命令/工具/总线三个前门、同一来源。
 * standalone/hub 形态本机带 LLM，故 value llm 可本地直跑。
 */
class LocalValueExecutor(
    private val items: ItemService,
    private val llm: LLMProvider?,
    private val batchSize: Int = 80,
    private val rarityTiers: Map<String, Double> = emptyMap()
) : ValueExecutor {
    override fun execute(sub: String, rest: String, trust: TrustLevel): String = when (sub) {
        "build" -> items.build()
        "llm" -> runLlm(rest.trim().equals("all", true))
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

    private fun runLlm(all: Boolean): String {
        val provider = llm ?: return "本服未配置 LLM，无法自动估值（可改用 value set 人工锚定）。"
        val bundle = items.roots(all)
        if (bundle.roots.isEmpty()) return "没有需要 LLM 估值的物品（都已解析，或先 value build）。"
        val pricer = LlmRootPricer(provider, rarityTiers)
        val seeds = if (all) pricer.priceBatched(bundle, batchSize) else pricer.price(bundle)
        return items.applyLlmSeeds(seeds)
    }
}
