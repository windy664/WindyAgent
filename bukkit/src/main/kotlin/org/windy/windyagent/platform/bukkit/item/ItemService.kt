package org.windy.windyagent.platform.bukkit.item

import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import org.windy.windyagent.AgentConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 物品估值门面：解析 mods/ → SQLite → EMC 式传播估值。跑在子服侧（有 mods/）。
 * build/set 触发的传播在**单线程执行器**上跑（串行、有进度日志），命令即时返回、不阻塞总线。
 */
class ItemService(
    private val modsDir: File,
    private val db: ItemDatabase,
    private val engine: ValuationEngine,
    private val parser: ModItemParser
) {
    private val log = LoggerFactory.getLogger(ItemService::class.java)
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "windyagent-valuation").apply { isDaemon = true } }
    @Volatile private var status = "未构建"

    // ---- value 命令用 ----
    // 估值跑在单线程执行器上（串行、有进度日志）；命令提交后**等待至多 WAIT_MS**：
    // 跑完就回真实结果（不让用户去轮询），真超时（大服）才回「仍在跑」。WAIT_MS < 总线超时，安全。
    fun build(): String = waitOr("全量估值") {
        status = "解析模组中…"; log.info("[物品估值] 开始全量估值：解析模组 → 重建库 → 传播")
        if (!modsDir.isDirectory) { status = "无 mods 目录（${modsDir}）"; return@waitOr }
        val pr = parser.parse()
        db.rebuild(pr.items, pr.recipes, pr.tags)
        status = "传播估值中…"
        engine.propagate()
        status = "完成：${db.itemCount()} 物品 / ${db.recipeCount()} 配方 / 种子 ${engine.lastSeedCount()}（人工锚定 ${db.overrides().size}）\n${engine.lastReport()}"
    }

    fun set(item: String, value: Double, note: String): String {
        val id = resolve(item) ?: return "找不到物品「$item」（先 value build，或给确切 id）。"
        db.setOverride(id, value, note)
        log.info("[物品估值] 人工锚定 {} = {}，重新传播（下游自动跟进）", id, value)
        return "已锚定 ${name(id)}（$id）= $value。" + waitOr("关联重算") {
            status = "重算中（锚定 $id）…"; engine.propagate(); status = "重算完成（锚定 $id）"
        }
    }

    fun unset(item: String): String {
        val id = resolve(item) ?: return "找不到物品「$item」。"
        if (!db.removeOverride(id)) return "「$id」本就没有人工锚定。"
        return "已取消 ${name(id)}（$id）的人工锚定。" + waitOr("关联重算") {
            status = "重算中…"; engine.propagate(); status = "重算完成"
        }
    }

    /** 在执行器上跑 [task]，等待至多 WAIT_MS：完成回真实状态、超时回「仍在跑」、出错回错误。 */
    private fun waitOr(label: String, task: () -> Unit): String {
        val f: Future<*> = exec.submit { runCatching(task).onFailure { status = "失败：${it.message}"; log.warn("[物品估值] {}失败：{}", label, it.message) } }
        return try {
            f.get(WAIT_MS, TimeUnit.MILLISECONDS)
            "$label 完成 — $status"
        } catch (e: TimeoutException) {
            "$label 数据较多仍在跑，过程见子服控制台，稍后 value status 查（当前：$status）"
        } catch (e: Exception) {
            "$label 出错：${e.cause?.message ?: e.message}"
        }
    }

    fun get(item: String): String {
        val id = resolve(item) ?: run {
            val hits = db.findItems(item, 6)
            return if (hits.isEmpty()) "物品库里没找到「$item」。先 value build，或换关键词。"
            else "找到多个，请说具体（或给 id）：\n" + hits.joinToString("\n") { "  ${it.nameZh.ifBlank { it.nameEn }}（${it.id}）" }
        }
        return engine.valuationText(id)
    }

    fun propose(target: Double): String = engine.proposePack(target)

    /** 供 value llm：悬空物 + 货币锚点（交给中心 LLM 定价）。all=true 返回全部退默认物，否则只返回根。 */
    fun roots(all: Boolean = false): org.windy.windyagent.valuation.RootsBundle =
        org.windy.windyagent.valuation.RootsBundle(
            if (all) engine.lastUnresolved() else engine.lastRoots(),
            engine.anchors(), engine.currencyName()
        )

    /** 写入 LLM 估的种子价 → 级联重算。 */
    fun applyLlmSeeds(seeds: Map<String, Double>): String {
        if (seeds.isEmpty()) return "LLM 没给出有效估价，未改动。"
        db.setLlmSeeds(seeds)
        log.info("[物品估值] 写入 {} 个 LLM 种子，重新传播", seeds.size)
        return "LLM 估值已写入 ${seeds.size} 个根。" + waitOr("级联重算") {
            status = "LLM 估值重算中…"; engine.propagate(); status = "LLM 估值重算完成"
        }
    }

    fun orphans(): String {
        val o = db.orphanOverrides()
        return if (o.isEmpty()) "无孤儿锚定（模组删除后残留的人工值）。"
        else "孤儿锚定（对应模组已删，可 value unset 清理）：\n" + o.entries.joinToString("\n") { "  ${it.key} = ${it.value}" }
    }

    fun status(): String = "$status（物品 ${db.itemCount()} / 配方 ${db.recipeCount()} / 估值 ${if (db.itemCount() > 0) "已生成" else "无"}）"

    /** 启动后台预热（首次空库则建）。 */
    fun warmup() {
        if (db.itemCount() == 0) build()
    }

    private fun resolve(item: String): String? {
        val q = item.trim()
        if (q.contains(':') && db.getItem(q) != null) return q
        val hits = db.findItems(q, 6)
        return hits.firstOrNull { it.nameZh == q || it.nameEn.equals(q, true) || it.id == q }?.id
            ?: hits.singleOrNull()?.id
    }

    private fun name(id: String) = db.getItem(id)?.let { it.nameZh.ifBlank { it.nameEn } }?.takeIf { it.isNotBlank() } ?: id

    companion object {
        /** 命令侧等待估值完成的上限；< 总线 timeout-ms(5s)，跑得快就同步回结果、慢则转异步提示。 */
        private const val WAIT_MS = 4000L

        fun build(plugin: JavaPlugin, cfg: AgentConfig): ItemService? {
            if (!cfg.itemValuationEnabled()) return null
            val modsDir = File(plugin.dataFolder.parentFile.parentFile, "mods")
            val db = ItemDatabase(plugin.dataFolder.toPath().resolve("items.db"))
            val engine = ValuationEngine(db, cfg.itemBaseValues(), cfg.itemDefaultBaseValue(), cfg.itemCraftOverhead(), cfg.itemPropagationMaxIter(), cfg.itemCurrencyName(), cfg.itemPackDiscount(), cfg.itemLlmMaxItems())
            return ItemService(modsDir, db, engine, ModItemParser(modsDir))
        }
    }
}
