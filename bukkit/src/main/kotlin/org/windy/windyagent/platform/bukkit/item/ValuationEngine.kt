package org.windy.windyagent.platform.bukkit.item

import org.slf4j.LoggerFactory
import org.windy.windyagent.valuation.RootInfo
import kotlin.math.abs

/**
 * EMC 式估值：种子（人工 override + 配置 base）+ 全图传播（Bellman-Ford 松弛取最便宜路径）。
 * 结果写 valuations 表；appraise/get 读它，不再每次重算。人工 set 后重跑 propagate → 下游自动连锁。
 */
class ValuationEngine(
    private val db: ItemDatabase,
    private val baseValues: Map<String, Double>,
    private val defaultBase: Double,
    private val overhead: Double,
    private val maxIter: Int,
    private val currency: String,
    private val packDiscount: Double,
    private val maxLlmItems: Int = 600
) {
    private val log = LoggerFactory.getLogger(ValuationEngine::class.java)
    private val INF = Double.MAX_VALUE / 4
    @Volatile private var lastSeeds = 0
    @Volatile private var lastReport = ""
    @Volatile private var lastRoots: List<RootInfo> = emptyList()
    @Volatile private var lastUnresolved: List<RootInfo> = emptyList()

    fun lastSeedCount() = lastSeeds
    /** 上次传播后"无配方、被依赖却悬空的根"（value llm 默认用，token 省）。 */
    fun lastRoots() = lastRoots
    /** 上次传播后**全部**退默认值的物品（含机器/祭坛造的成品；value llm all 用，覆盖全）。 */
    fun lastUnresolved() = lastUnresolved
    fun anchors(): Map<String, Double> = baseValues
    fun currencyName() = currency

    /** 全图传播，写回 valuations。build/set 都调它（build 先重建解析表，set 只改 overrides）。 */
    @Synchronized
    fun propagate() {
        val graph = db.loadRecipeGraph()           // output -> [(产出数, 材料map)]
        val tagMembers = db.loadTagMembers()        // #tag -> [成员id 或 #子标签]
        val items = db.allItemIds().toHashSet()
        val overrides = db.overrides()
        val overrideIds = overrides.keys
        val llmSeeds = db.llmSeeds()
        val llmSeedIds = llmSeeds.keys
        // 优先级：人工 override > 配置 base > LLM 估值（后 put 覆盖先 put）
        val seeds = HashMap<String, Double>().apply { putAll(llmSeeds); putAll(baseValues); putAll(overrides) }
        lastSeeds = seeds.size
        log.info("[物品估值] 开始传播 — 物品 {}，配方产物 {}，标签 {}，种子 {}（基准 {} + 人工锚定 {} + LLM {}）", items.size, graph.size, tagMembers.size, seeds.size, baseValues.size, overrides.size, llmSeeds.size)
        if (seeds.isEmpty()) {
            log.warn("[物品估值] ⚠ 种子为 0 —— 估值无价值源，所有物品将退默认值（${defaultBase}）。请在配置 item-valuation.base-values 填基材价，或用 value set 人工锚定后重建。")
        }

        val allIds = HashSet(items).apply {
            addAll(graph.keys); graph.values.forEach { recs -> recs.forEach { addAll(it.second.keys) } }
            addAll(tagMembers.keys); tagMembers.values.forEach { addAll(it) }
        }
        val value = HashMap<String, Double>(allIds.size)
        for (id in allIds) value[id] = seeds[id] ?: INF

        var round = 0
        while (round < maxIter) {
            round++
            var changed = 0
            // 先松弛标签：标签值 = 成员（具体物品/子标签）最小值，不加工序开销
            for ((tag, members) in tagMembers) {
                if (tag in seeds) continue
                var best = value[tag] ?: INF
                for (mem in members) { val mv = value[mem] ?: INF; if (mv < best) best = mv }
                if (best < (value[tag] ?: INF)) { value[tag] = best; changed++ }
            }
            // 再松弛配方
            for ((out, recs) in graph) {
                if (out in seeds) continue
                var best = value[out] ?: INF
                for ((outCount, ings) in recs) {
                    var sum = 0.0; var ok = true
                    for ((ing, cnt) in ings) {
                        val iv = value[ing] ?: INF
                        if (iv >= INF) { ok = false; break }
                        sum += iv * cnt
                    }
                    if (ok) { val unit = sum / outCount.coerceAtLeast(1) + overhead; if (unit < best) best = unit }
                }
                if (best < (value[out] ?: INF)) { value[out] = best; changed++ }
            }
            log.info("[物品估值] 传播第 {} 轮，更新 {} 个", round, changed)
            if (changed == 0) break
        }

        val unresolvedTags = tagMembers.keys.count { (value[it] ?: INF) >= INF }
        if (unresolvedTags > 0) log.info("[物品估值] 仍有 {} 个标签无解（多为 NeoForge/原版提供、jar 内无定义），可在 base-values 用 #c:... 补种子", unresolvedTags)

        val conf = computeConfidence(graph, tagMembers, value, seeds)
        val vals = items.map { id ->
            val basis = when {
                id in overrideIds -> "override"
                id in baseValues -> "base"
                id in llmSeedIds -> "llm"
                (value[id] ?: INF) < INF -> "derived"
                else -> "unknown"
            }
            val v = (value[id] ?: INF).let { if (it >= INF) defaultBase else it }
            val confidence = when {
                basis == "unknown" || conf[id] == false -> "low"
                basis == "llm" -> "medium"     // LLM 估的是猜测，标中等
                else -> "high"
            }
            Valuation(id, v, confidence, basis)
        }
        db.writeValuations(vals)
        db.metaSet("last_build", System.currentTimeMillis().toString())

        // 悬空的根：无配方推导、被≥1 配方依赖。既用于文字引导，也供 value llm 拿去给 LLM 定价。
        val inDegree = HashMap<String, Int>()
        for ((_, recs) in graph) for ((_, ings) in recs) for (ing in ings.keys) inDegree[ing] = (inDegree[ing] ?: 0) + 1
        val unresolved = items.filter { (value[it] ?: INF) >= INF }
        val resolved = items.size - unresolved.size
        val sortedUnresolved = unresolved.sortedByDescending { inDegree[it] ?: 0 }
        val rootList = sortedUnresolved.filter { (inDegree[it] ?: 0) > 0 }
            .map { RootInfo(it, nameOf(it), inDegree[it] ?: 0) }
        lastRoots = rootList.take(80)
        // 全部悬空物（含成品），供 value llm all 分批兜底；上限可配（防 token 失控）
        lastUnresolved = sortedUnresolved.take(maxLlmItems).map { RootInfo(it, nameOf(it), inDegree[it] ?: 0) }
        val hints = rootList.take(6)
        lastReport = "已解析估值 $resolved / 退默认值 ${unresolved.size}" +
            if (hints.isNotEmpty()) "。这些「根」被大量配方依赖却无合成路径，建议 value llm 自动估价或 value set 人工锚定：\n" +
                hints.joinToString("\n") { "  ${it.name}（${it.id}）— 被 ${it.deg} 处配方引用" } else "。"
        log.info("[物品估值] 传播完成 — {} 物品写入数据库；{}", vals.size, lastReport.replace("\n", " "))
    }

    fun lastReport() = lastReport

    /** 置信度二次传播：种子=高；未知(INF)=低；派生=其最便宜配方的材料全高才高。 */
    private fun computeConfidence(graph: Map<String, List<Pair<Int, Map<String, Int>>>>, tagMembers: Map<String, List<String>>, value: Map<String, Double>, seeds: Map<String, Double>): Map<String, Boolean> {
        val conf = HashMap<String, Boolean>()
        for (id in value.keys) conf[id] = id in seeds
        var round = 0
        while (round < maxIter) {
            round++; var changed = false
            // 标签：取到最小值的那个成员若可信，则标签可信
            for ((tag, members) in tagMembers) {
                if (tag in seeds || conf[tag] == true) continue
                val tv = value[tag] ?: INF; if (tv >= INF) continue
                if (members.any { conf[it] == true && (value[it] ?: INF) <= tv + 1e-9 }) { conf[tag] = true; changed = true }
            }
            for ((out, recs) in graph) {
                if (out in seeds || conf[out] == true) continue
                if ((value[out] ?: INF) >= INF) continue
                val target = value[out]!!
                for ((outCount, ings) in recs) {
                    var sum = 0.0; var ok = true; var allConf = true
                    for ((ing, cnt) in ings) {
                        val iv = value[ing] ?: INF; if (iv >= INF) { ok = false; break }
                        sum += iv * cnt; if (conf[ing] != true) allConf = false
                    }
                    if (ok && allConf && abs(sum / outCount.coerceAtLeast(1) + overhead - target) < 1e-6) { conf[out] = true; changed = true; break }
                }
            }
            if (!changed) break
        }
        return conf
    }

    fun valuationText(id: String): String {
        val item = db.getItem(id) ?: return "物品库里没有「$id」。先 value build 建库。"
        val v = db.getValuation(id) ?: return "${nameOf(id)}（$id）尚无估值，先 value build。"
        val confLabel = when (v.confidence) { "high" -> "高"; "medium" -> "中（LLM 估，建议核对）"; else -> "低，建议人工锚定" }
        return "${nameOf(id)}（$id）\n估值：${fmt(v.value)} $currency（置信度：$confLabel，依据：${basisLabel(v.basis)}）\n${breakdown(id)}"
    }

    fun proposePack(target: Double): String {
        val cands = db.topCraftableValuations(800).filter { it.second in 1.0..target }
        if (cands.isEmpty()) return "没合适物品组礼包（先 value build）。"
        val basket = ArrayList<Pair<String, Double>>(); var total = 0.0
        for (c in cands.sortedByDescending { it.second }) {
            if (total + c.second <= target) { basket += c; total += c.second }
            if (total >= target * 0.9 || basket.size >= 8) break
        }
        if (basket.isEmpty()) basket += cands.first().also { total = it.second }
        val price = total * packDiscount
        return "礼包提案（目标 ${fmt(target)} $currency）：\n" +
            basket.joinToString("\n") { "  ${nameOf(it.first)} ×1 — 估值 ${fmt(it.second)}" } +
            "\n合计估值：${fmt(total)} $currency\n建议售价：${fmt(price)} $currency（${(packDiscount * 100).toInt()}% 折）\n（建议，落商店需管理员确认）"
    }

    private fun breakdown(id: String): String {
        val recipes = db.recipesFor(id).groupBy { it.recipeId }
        if (recipes.isEmpty()) return "（无配方，按种子/基准价计）"
        val best = recipes.values.minByOrNull { rows -> val o = rows.first().outputCount.coerceAtLeast(1); rows.sumOf { (db.getValuation(it.ingredient)?.value ?: 0.0) * it.count } / o } ?: return ""
        val o = best.first().outputCount.coerceAtLeast(1)
        return "合成路径（一次产出 $o）：\n" + best.joinToString("\n") { r -> "  ${r.count}× ${nameOf(r.ingredient)}（${fmt(db.getValuation(r.ingredient)?.value ?: 0.0)}）" }
    }

    private fun nameOf(id: String): String =
        if (id.startsWith("#")) "标签 $id" else db.getItem(id)?.let { it.nameZh.ifBlank { it.nameEn } }?.takeIf { it.isNotBlank() } ?: id
    private fun basisLabel(b: String) = when (b) { "override" -> "人工锚定"; "base" -> "基准价"; "llm" -> "LLM 估值"; "derived" -> "合成推导"; else -> "未知(默认值)" }
    private fun fmt(d: Double) = if (d >= 100) "%.0f".format(d) else "%.1f".format(d)
}
