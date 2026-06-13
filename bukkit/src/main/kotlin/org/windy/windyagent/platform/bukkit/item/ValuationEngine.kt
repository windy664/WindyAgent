package org.windy.windyagent.platform.bukkit.item

import org.slf4j.LoggerFactory
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
    private val packDiscount: Double
) {
    private val log = LoggerFactory.getLogger(ValuationEngine::class.java)
    private val INF = Double.MAX_VALUE / 4

    /** 全图传播，写回 valuations。build/set 都调它（build 先重建解析表，set 只改 overrides）。 */
    @Synchronized
    fun propagate() {
        val graph = db.loadRecipeGraph()           // output -> [(产出数, 材料map)]
        val items = db.allItemIds().toHashSet()
        val overrideIds = db.overrides().keys
        val seeds = HashMap<String, Double>().apply { putAll(baseValues); putAll(db.overrides()) } // override 覆盖 base
        log.info("[物品估值] 开始传播 — 物品 {}，配方产物 {}，种子 {}", items.size, graph.size, seeds.size)

        val allIds = HashSet(items).apply { addAll(graph.keys); graph.values.forEach { recs -> recs.forEach { addAll(it.second.keys) } } }
        val value = HashMap<String, Double>(allIds.size)
        for (id in allIds) value[id] = seeds[id] ?: INF

        var round = 0
        while (round < maxIter) {
            round++
            var changed = 0
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

        val conf = computeConfidence(graph, value, seeds)
        val vals = items.map { id ->
            val basis = when {
                id in overrideIds -> "override"
                id in baseValues -> "base"
                (value[id] ?: INF) < INF -> "derived"
                else -> "unknown"
            }
            val v = (value[id] ?: INF).let { if (it >= INF) defaultBase else it }
            val confidence = if (basis == "unknown" || conf[id] == false) "low" else "high"
            Valuation(id, v, confidence, basis)
        }
        db.writeValuations(vals)
        db.metaSet("last_build", System.currentTimeMillis().toString())
        log.info("[物品估值] 传播完成 — {} 物品写入数据库", vals.size)
    }

    /** 置信度二次传播：种子=高；未知(INF)=低；派生=其最便宜配方的材料全高才高。 */
    private fun computeConfidence(graph: Map<String, List<Pair<Int, Map<String, Int>>>>, value: Map<String, Double>, seeds: Map<String, Double>): Map<String, Boolean> {
        val conf = HashMap<String, Boolean>()
        for (id in value.keys) conf[id] = id in seeds
        var round = 0
        while (round < maxIter) {
            round++; var changed = false
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
        return "${nameOf(id)}（$id）\n估值：${fmt(v.value)} $currency（置信度：${if (v.confidence == "high") "高" else "低，建议人工锚定"}，依据：${basisLabel(v.basis)}）\n${breakdown(id)}"
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
    private fun basisLabel(b: String) = when (b) { "override" -> "人工锚定"; "base" -> "基准价"; "derived" -> "合成推导"; else -> "未知(默认值)" }
    private fun fmt(d: Double) = if (d >= 100) "%.0f".format(d) else "%.1f".format(d)
}
