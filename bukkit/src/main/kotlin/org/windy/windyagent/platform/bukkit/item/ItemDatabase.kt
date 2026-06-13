package org.windy.windyagent.platform.bukkit.item

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * 嵌入式 SQLite 物品库。**分表抗模组增删**：
 *  - items/recipes：解析所得，刷库时重建；
 *  - overrides：人工锚定值，**持久、刷库绝不触碰**（宝贵）；
 *  - valuations：传播算出，重算时重生成；meta：schema 版本/哈希。
 * 单连接 + 同步访问。
 */
class ItemDatabase(private val dbPath: Path) {

    private val log = LoggerFactory.getLogger(ItemDatabase::class.java)
    private val url = "jdbc:sqlite:${dbPath.toAbsolutePath()}"

    init { runCatching { Class.forName("org.sqlite.JDBC") } }

    @Volatile private var conn: Connection? = null
    @Synchronized private fun c(): Connection {
        conn?.takeIf { !it.isClosed }?.let { return it }
        Files.createDirectories(dbPath.parent)
        return DriverManager.getConnection(url).also {
            conn = it
            it.createStatement().use { st ->
                st.executeUpdate("CREATE TABLE IF NOT EXISTS items(id TEXT PRIMARY KEY, mod TEXT, category TEXT, tier INT, name_en TEXT, name_zh TEXT, source TEXT)")
                st.executeUpdate("CREATE TABLE IF NOT EXISTS recipes(output TEXT, recipe_id TEXT, output_count INT, ingredient TEXT, count INT)")
                st.executeUpdate("CREATE TABLE IF NOT EXISTS overrides(id TEXT PRIMARY KEY, value REAL, note TEXT, set_at INT)")
                st.executeUpdate("CREATE TABLE IF NOT EXISTS valuations(id TEXT PRIMARY KEY, value REAL, confidence TEXT, basis TEXT)")
                st.executeUpdate("CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_items_mod ON items(mod)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_recipes_out ON recipes(output)")
            }
        }
    }

    // ---- 解析数据：刷库时重建（不碰 overrides）----
    @Synchronized
    fun rebuild(items: List<ModItem>, recipes: List<RecipeRow>) {
        val c = c()
        c.createStatement().use { it.executeUpdate("DELETE FROM items"); it.executeUpdate("DELETE FROM recipes") }
        c.autoCommit = false
        try {
            c.prepareStatement("INSERT OR REPLACE INTO items VALUES(?,?,?,?,?,?,?)").use { ps ->
                for (i in items) {
                    ps.setString(1, i.id); ps.setString(2, i.mod); ps.setString(3, i.category)
                    if (i.tier != null) ps.setInt(4, i.tier) else ps.setNull(4, java.sql.Types.INTEGER)
                    ps.setString(5, i.nameEn); ps.setString(6, i.nameZh); ps.setString(7, i.source); ps.addBatch()
                }
                ps.executeBatch()
            }
            c.prepareStatement("INSERT INTO recipes VALUES(?,?,?,?,?)").use { ps ->
                for (r in recipes) { ps.setString(1, r.output); ps.setString(2, r.recipeId); ps.setInt(3, r.outputCount); ps.setString(4, r.ingredient); ps.setInt(5, r.count); ps.addBatch() }
                ps.executeBatch()
            }
            c.commit()
        } catch (e: Exception) { c.rollback(); throw e } finally { c.autoCommit = true }
    }

    @Synchronized fun itemCount() = c().createStatement().use { it.executeQuery("SELECT COUNT(*) FROM items").use { r -> if (r.next()) r.getInt(1) else 0 } }
    @Synchronized fun recipeCount() = c().createStatement().use { it.executeQuery("SELECT COUNT(*) FROM recipes").use { r -> if (r.next()) r.getInt(1) else 0 } }

    @Synchronized
    fun allItemIds(): List<String> = c().createStatement().use { it.executeQuery("SELECT id FROM items").use { rs -> seq(rs) { it.getString(1) } } }

    /** 全部配方一次加载（传播在内存里跑，避免迭代时频繁 SQL）。返回 output -> 各配方(产出数, 材料(id->数量))。 */
    @Synchronized
    fun loadRecipeGraph(): Map<String, List<Pair<Int, Map<String, Int>>>> {
        val byOutRecipe = HashMap<String, HashMap<String, Pair<Int, HashMap<String, Int>>>>()
        c().createStatement().use { st ->
            st.executeQuery("SELECT output, recipe_id, output_count, ingredient, count FROM recipes").use { rs ->
                while (rs.next()) {
                    val out = rs.getString(1); val rid = rs.getString(2); val oc = rs.getInt(3); val ing = rs.getString(4); val cnt = rs.getInt(5)
                    val recipes = byOutRecipe.getOrPut(out) { HashMap() }
                    val (_, ings) = recipes.getOrPut(rid) { oc to HashMap() }
                    ings[ing] = (ings[ing] ?: 0) + cnt
                }
            }
        }
        return byOutRecipe.mapValues { (_, rs) -> rs.values.map { it.first to (it.second as Map<String, Int>) } }
    }

    // ---- overrides：人工锚定，持久 ----
    @Synchronized
    fun setOverride(id: String, value: Double, note: String) =
        c().prepareStatement("INSERT OR REPLACE INTO overrides VALUES(?,?,?,?)").use { ps ->
            ps.setString(1, id); ps.setDouble(2, value); ps.setString(3, note); ps.setLong(4, System.currentTimeMillis()); ps.executeUpdate()
        }

    @Synchronized fun removeOverride(id: String): Boolean = c().prepareStatement("DELETE FROM overrides WHERE id=?").use { ps -> ps.setString(1, id); ps.executeUpdate() > 0 }

    @Synchronized
    fun overrides(): Map<String, Double> = c().createStatement().use { it.executeQuery("SELECT id, value FROM overrides").use { rs ->
        val m = HashMap<String, Double>(); while (rs.next()) m[rs.getString(1)] = rs.getDouble(2); m
    } }

    /** 孤儿 override：id 已不在 items（模组删了）。返回 id->value。 */
    @Synchronized
    fun orphanOverrides(): Map<String, Double> = c().createStatement().use {
        it.executeQuery("SELECT o.id, o.value FROM overrides o LEFT JOIN items i ON o.id=i.id WHERE i.id IS NULL").use { rs ->
            val m = HashMap<String, Double>(); while (rs.next()) m[rs.getString(1)] = rs.getDouble(2); m
        }
    }

    // ---- valuations：传播结果 ----
    @Synchronized
    fun writeValuations(vals: List<Valuation>) {
        val c = c()
        c.createStatement().use { it.executeUpdate("DELETE FROM valuations") }
        c.autoCommit = false
        try {
            c.prepareStatement("INSERT OR REPLACE INTO valuations VALUES(?,?,?,?)").use { ps ->
                for (v in vals) { ps.setString(1, v.id); ps.setDouble(2, v.value); ps.setString(3, v.confidence); ps.setString(4, v.basis); ps.addBatch() }
                ps.executeBatch()
            }
            c.commit()
        } catch (e: Exception) { c.rollback(); throw e } finally { c.autoCommit = true }
    }

    @Synchronized
    fun getValuation(id: String): Valuation? = c().prepareStatement("SELECT * FROM valuations WHERE id=?").use { ps ->
        ps.setString(1, id); ps.executeQuery().use { rs -> if (rs.next()) Valuation(rs.getString("id"), rs.getDouble("value"), rs.getString("confidence"), rs.getString("basis")) else null }
    }

    @Synchronized
    fun topCraftableValuations(limit: Int): List<Pair<String, Double>> = c().prepareStatement(
        "SELECT v.id, v.value FROM valuations v JOIN items i ON v.id=i.id WHERE i.source='craftable' ORDER BY v.value DESC LIMIT ?"
    ).use { ps -> ps.setInt(1, limit); ps.executeQuery().use { rs -> seq(rs) { it.getString(1) to it.getDouble(2) } } }

    // ---- 查询 ----
    @Synchronized
    fun getItem(id: String): ModItem? = c().prepareStatement("SELECT * FROM items WHERE id=?").use { ps ->
        ps.setString(1, id); ps.executeQuery().use { rs -> if (rs.next()) rowToItem(rs) else null }
    }

    @Synchronized
    fun findItems(query: String, limit: Int): List<ModItem> {
        val q = "%${query.trim()}%"
        return c().prepareStatement("SELECT * FROM items WHERE id LIKE ? OR name_zh LIKE ? OR name_en LIKE ? LIMIT ?").use { ps ->
            ps.setString(1, q); ps.setString(2, q); ps.setString(3, q); ps.setInt(4, limit)
            ps.executeQuery().use { rs -> seq(rs) { rowToItem(it) } }
        }
    }

    @Synchronized
    fun recipesFor(output: String): List<RecipeRow> = c().prepareStatement("SELECT * FROM recipes WHERE output=?").use { ps ->
        ps.setString(1, output); ps.executeQuery().use { rs -> seq(rs) { RecipeRow(it.getString("output"), it.getString("recipe_id"), it.getInt("output_count"), it.getString("ingredient"), it.getInt("count")) } }
    }

    @Synchronized fun metaGet(k: String): String? = c().prepareStatement("SELECT v FROM meta WHERE k=?").use { ps -> ps.setString(1, k); ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null } }
    @Synchronized fun metaSet(k: String, v: String) = c().prepareStatement("INSERT OR REPLACE INTO meta VALUES(?,?)").use { ps -> ps.setString(1, k); ps.setString(2, v); ps.executeUpdate(); Unit }

    private fun rowToItem(rs: java.sql.ResultSet) = ModItem(rs.getString("id"), rs.getString("mod"), rs.getString("category"), rs.getObject("tier") as? Int, rs.getString("name_en"), rs.getString("name_zh"), rs.getString("source"))
    private fun <T> seq(rs: java.sql.ResultSet, f: (java.sql.ResultSet) -> T): List<T> = generateSequence { if (rs.next()) f(rs) else null }.toList()
}
