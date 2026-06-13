package org.windy.windyagent.platform.bukkit.item

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.File
import java.util.zip.ZipFile

/**
 * 解析 `mods/` 下的模组 jar（zip）里的**静态数据**——不执行任何模组代码：
 *  - 名称/翻译：`assets/<mod>/lang/{en_us,zh_cn}.json`（key `item|block.<mod>.<name>` → id `<mod>:<name>`）
 *  - 配方：data/&lt;mod&gt;/recipe(s) 下的 json（result + key/ingredients），处理 shaped/shapeless，其它尽力而为
 *
 * 这样绕开「Velocity/Bukkit 跑不了 NeoForge 代码」的限制——我们只读它的数据文件。
 */
class ModItemParser(private val modsDir: File) {

    private val log = LoggerFactory.getLogger(ModItemParser::class.java)
    private val mapper = ObjectMapper()

    fun parse(): ParseResult {
        val nameEn = HashMap<String, String>()
        val nameZh = HashMap<String, String>()
        val category = HashMap<String, String>()
        val recipes = ArrayList<RecipeRow>()
        val tags = ArrayList<TagEdge>()

        val jars = modsDir.listFiles { f -> f.isFile && f.name.endsWith(".jar") }?.toList() ?: emptyList()
        for (jar in jars) {
            runCatching { parseJar(jar, nameEn, nameZh, category, recipes, tags) }
                .onFailure { log.warn("解析模组 jar 失败 {}: {}", jar.name, it.message) }
        }

        val hasRecipe = recipes.mapTo(HashSet()) { it.output }
        val items = (nameEn.keys + nameZh.keys).distinct().map { id ->
            ModItem(
                id = id,
                mod = id.substringBefore(':', "minecraft"),
                category = category[id] ?: "item",
                tier = null,
                nameEn = nameEn[id].orEmpty(),
                nameZh = nameZh[id].orEmpty(),
                source = if (id in hasRecipe) "craftable" else "raw"
            )
        }
        log.info("模组解析完成 — {} 个 jar，{} 个物品，{} 条配方材料，{} 条标签成员", jars.size, items.size, recipes.size, tags.size)
        return ParseResult(items, recipes, tags)
    }

    private fun parseJar(jar: File, nameEn: MutableMap<String, String>, nameZh: MutableMap<String, String>, category: MutableMap<String, String>, recipes: MutableList<RecipeRow>, tags: MutableList<TagEdge>) {
        ZipFile(jar).use { zip ->
            val es = zip.entries()
            while (es.hasMoreElements()) {
                val e = es.nextElement()
                val n = e.name
                when {
                    n.startsWith("assets/") && n.endsWith("/lang/en_us.json") ->
                        runCatching { parseLang(mapper.readTree(zip.getInputStream(e)), nameEn, category) }
                    n.startsWith("assets/") && n.endsWith("/lang/zh_cn.json") ->
                        runCatching { parseLang(mapper.readTree(zip.getInputStream(e)), nameZh, category) }
                    // 标签定义：data/<ns>/tags/item(s)/<路径>.json → 标签 #<ns>:<路径>
                    n.startsWith("data/") && n.contains("/tags/item") && n.endsWith(".json") ->
                        runCatching { parseTag(mapper.readTree(zip.getInputStream(e)), n, tags) }
                    n.startsWith("data/") && n.contains("/recipe") && n.endsWith(".json") ->
                        runCatching { parseRecipe(mapper.readTree(zip.getInputStream(e)), n, recipes) }
                }
            }
        }
    }

    /** 解析物品标签定义，产出 (#tag, member) 边。member 可能是具体 id 或 #子标签。 */
    private fun parseTag(node: JsonNode, path: String, tags: MutableList<TagEdge>) {
        val m = TAG_PATH.matchEntire(path) ?: return
        val tag = "#${m.groupValues[1]}:${m.groupValues[2]}"   // 如 #c:ingots/inferium
        val values = node["values"] ?: return
        values.forEach { v ->
            val member = when {
                v.isTextual -> v.asText()
                v.isObject -> (v["id"] ?: v["tag"])?.asText()
                else -> null
            }?.takeIf { it.isNotBlank() } ?: return@forEach
            tags += TagEdge(tag, member)
        }
    }

    private fun parseLang(node: JsonNode, into: MutableMap<String, String>, category: MutableMap<String, String>) {
        node.fields().forEach { (key, v) ->
            val m = LANG_KEY.matchEntire(key) ?: return@forEach
            val id = "${m.groupValues[2]}:${m.groupValues[3]}"
            into[id] = v.asText()
            category.putIfAbsent(id, m.groupValues[1]) // item / block
        }
    }

    private fun parseRecipe(node: JsonNode, path: String, recipes: MutableList<RecipeRow>) {
        val result = node["result"] ?: return
        val output = (result["id"] ?: result["item"])?.asText()?.takeIf { it.isNotBlank() } ?: return
        val outCount = result["count"]?.asInt()?.takeIf { it > 0 } ?: 1

        val ing = LinkedHashMap<String, Int>()
        when {
            node.has("key") && node.has("pattern") -> {
                val symCount = HashMap<Char, Int>()
                node["pattern"].forEach { row -> row.asText().forEach { c -> if (c != ' ') symCount[c] = (symCount[c] ?: 0) + 1 } }
                node["key"].fields().forEach { (sym, v) ->
                    val id = ingredientId(v) ?: return@forEach
                    ing[id] = (ing[id] ?: 0) + (symCount[sym.firstOrNull() ?: ' '] ?: 0)
                }
            }
            node.has("ingredients") -> node["ingredients"].forEach { v -> ingredientId(v)?.let { ing[it] = (ing[it] ?: 0) + 1 } }
            node.has("ingredient") -> ingredientId(node["ingredient"])?.let { ing[it] = (ing[it] ?: 0) + 1 }
            else -> return
        }
        if (ing.isEmpty()) return
        ing.forEach { (id, cnt) -> recipes += RecipeRow(output, path, outCount, id, cnt) }
    }

    /** 材料 id：兼容 字符串 / {item|id} / {tag} / 数组(取首)。tag 以 # 前缀返回。 */
    private fun ingredientId(node: JsonNode): String? {
        if (node.isTextual) return node.asText().takeIf { it.isNotBlank() }
        if (node.isArray) return node.firstOrNull()?.let { ingredientId(it) }
        node["item"]?.asText()?.takeIf { it.isNotBlank() }?.let { return it }
        node["id"]?.asText()?.takeIf { it.isNotBlank() }?.let { return it }
        node["tag"]?.asText()?.takeIf { it.isNotBlank() }?.let { return "#$it" }
        return null
    }

    companion object {
        private val LANG_KEY = Regex("^(item|block)\\.([^.]+)\\.(.+)$")
        // data/<ns>/tags/item 或 items/<路径>.json
        private val TAG_PATH = Regex("^data/([^/]+)/tags/items?/(.+)\\.json$")
    }
}
