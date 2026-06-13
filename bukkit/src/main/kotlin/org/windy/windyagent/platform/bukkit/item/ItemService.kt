package org.windy.windyagent.platform.bukkit.item

import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import org.windy.windyagent.AgentConfig
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Executors

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
    fun build(): String {
        exec.submit {
            runCatching {
                status = "解析模组中…"
                if (!modsDir.isDirectory) { status = "无 mods 目录"; return@submit }
                val pr = parser.parse()
                db.rebuild(pr.items, pr.recipes)
                status = "传播估值中…"
                engine.propagate()
                status = "完成：${db.itemCount()} 物品 / ${db.recipeCount()} 配方"
            }.onFailure { status = "失败：${it.message}"; log.warn("全量估值失败：{}", it.message) }
        }
        return "已开始全量估值（解析+传播），过程见子服控制台，稍后用 value status 查。"
    }

    fun set(item: String, value: Double, note: String): String {
        val id = resolve(item) ?: return "找不到物品「$item」（先 value build，或给确切 id）。"
        db.setOverride(id, value, note)
        exec.submit { runCatching { status = "重算中（锚定 $id）…"; engine.propagate(); status = "重算完成" }.onFailure { log.warn("重算失败：{}", it.message) } }
        return "已锚定 ${name(id)}（$id）= ${value}，正在重算关联价值（下游自动跟进）…"
    }

    fun unset(item: String): String {
        val id = resolve(item) ?: return "找不到物品「$item」。"
        if (!db.removeOverride(id)) return "「$id」本就没有人工锚定。"
        exec.submit { runCatching { engine.propagate() } }
        return "已取消 ${name(id)}（$id）的人工锚定，正在重算…"
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
        fun build(plugin: JavaPlugin, cfg: AgentConfig): ItemService? {
            if (!cfg.itemValuationEnabled()) return null
            val modsDir = File(plugin.dataFolder.parentFile.parentFile, "mods")
            val db = ItemDatabase(plugin.dataFolder.toPath().resolve("items.db"))
            val engine = ValuationEngine(db, cfg.itemBaseValues(), cfg.itemDefaultBaseValue(), cfg.itemCraftOverhead(), cfg.itemPropagationMaxIter(), cfg.itemCurrencyName(), cfg.itemPackDiscount())
            return ItemService(modsDir, db, engine, ModItemParser(modsDir))
        }
    }
}
