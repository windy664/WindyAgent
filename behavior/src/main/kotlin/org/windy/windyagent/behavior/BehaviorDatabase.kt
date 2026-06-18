package org.windy.windyagent.behavior

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * 玩家行为库（嵌入式 SQLite）。**分表**：
 *  - profile：每位玩家的滚动画像（计数器，增量更新，长存）
 *  - sessions：每次会话（登入登出，算时长/留存）
 *  - events：低频事件原始记录（死亡、成就），可按保留期清理
 * 写入只由采集器（如 bukkit 侧 BehaviorTracker）的后台单线程调用，串行、批量；监听器线程不碰这里。
 * 本类**平台无关**：只用 JDBC + java.time，不碰任何载体（Bukkit/Velocity）API。
 */
class BehaviorDatabase(private val dbPath: Path) {

    private val log = LoggerFactory.getLogger(BehaviorDatabase::class.java)
    private val url = "jdbc:sqlite:${dbPath.toAbsolutePath()}"

    init { runCatching { Class.forName("org.sqlite.JDBC") } }

    @Volatile private var conn: Connection? = null
    @Synchronized private fun c(): Connection {
        conn?.takeIf { !it.isClosed }?.let { return it }
        Files.createDirectories(dbPath.parent)
        return DriverManager.getConnection(url).also {
            conn = it
            it.createStatement().use { st ->
                st.executeUpdate("""CREATE TABLE IF NOT EXISTS profile(
                    uuid TEXT PRIMARY KEY, name TEXT, first_seen INT, last_seen INT, playtime_sec INT DEFAULT 0,
                    session_count INT DEFAULT 0, death_count INT DEFAULT 0, cmd_count INT DEFAULT 0, chat_count INT DEFAULT 0,
                    blocks_placed INT DEFAULT 0, blocks_broken INT DEFAULT 0, craft_count INT DEFAULT 0, adv_count INT DEFAULT 0)""")
                st.executeUpdate("CREATE TABLE IF NOT EXISTS sessions(uuid TEXT, name TEXT, join_ts INT, quit_ts INT, duration_sec INT)")
                st.executeUpdate("CREATE TABLE IF NOT EXISTS events(uuid TEXT, name TEXT, type TEXT, ts INT, detail TEXT)")
                st.executeUpdate("CREATE TABLE IF NOT EXISTS word_freq(source TEXT, word TEXT, count INT, PRIMARY KEY(source,word))")
                st.executeUpdate("CREATE TABLE IF NOT EXISTS online_snap(ts INT, cnt INT)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_profile_last ON profile(last_seen)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sessions_join ON sessions(join_ts)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_events_ts ON events(ts)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_word_count ON word_freq(source, count DESC)")
            }
        }
    }

    /** 批量增量更新画像（flush 调用）。deltas: 各计数增量；playtimeSec 累加；首见则建行。 */
    @Synchronized
    fun bumpProfiles(now: Long, deltas: Collection<ProfileDelta>) {
        if (deltas.isEmpty()) return
        val c = c()
        c.autoCommit = false
        try {
            val sql = """INSERT INTO profile(uuid,name,first_seen,last_seen,playtime_sec,session_count,death_count,cmd_count,chat_count,blocks_placed,blocks_broken,craft_count,adv_count)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, last_seen=excluded.last_seen,
                    playtime_sec=playtime_sec+excluded.playtime_sec, session_count=session_count+excluded.session_count,
                    death_count=death_count+excluded.death_count, cmd_count=cmd_count+excluded.cmd_count, chat_count=chat_count+excluded.chat_count,
                    blocks_placed=blocks_placed+excluded.blocks_placed, blocks_broken=blocks_broken+excluded.blocks_broken,
                    craft_count=craft_count+excluded.craft_count, adv_count=adv_count+excluded.adv_count"""
            c.prepareStatement(sql).use { ps ->
                for (d in deltas) {
                    ps.setString(1, d.uuid); ps.setString(2, d.name); ps.setLong(3, now); ps.setLong(4, now)
                    ps.setLong(5, d.playtimeSec); ps.setInt(6, d.sessions); ps.setInt(7, d.deaths); ps.setInt(8, d.commands)
                    ps.setInt(9, d.chats); ps.setInt(10, d.blocksPlaced); ps.setInt(11, d.blocksBroken); ps.setInt(12, d.crafts); ps.setInt(13, d.advancements)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            c.commit()
        } catch (e: Exception) { c.rollback(); log.warn("行为画像写入失败：{}", e.message) } finally { c.autoCommit = true }
    }

    @Synchronized
    fun writeSessions(rows: Collection<SessionRow>) {
        if (rows.isEmpty()) return
        val c = c(); c.autoCommit = false
        try {
            c.prepareStatement("INSERT INTO sessions VALUES(?,?,?,?,?)").use { ps ->
                for (r in rows) { ps.setString(1, r.uuid); ps.setString(2, r.name); ps.setLong(3, r.joinTs); ps.setLong(4, r.quitTs); ps.setLong(5, r.durationSec); ps.addBatch() }
                ps.executeBatch()
            }
            c.commit()
        } catch (e: Exception) { c.rollback() } finally { c.autoCommit = true }
    }

    @Synchronized
    fun writeEvents(rows: Collection<EventRow>) {
        if (rows.isEmpty()) return
        val c = c(); c.autoCommit = false
        try {
            c.prepareStatement("INSERT INTO events VALUES(?,?,?,?,?)").use { ps ->
                for (r in rows) { ps.setString(1, r.uuid); ps.setString(2, r.name); ps.setString(3, r.type); ps.setLong(4, r.ts); ps.setString(5, r.detail); ps.addBatch() }
                ps.executeBatch()
            }
            c.commit()
        } catch (e: Exception) { c.rollback() } finally { c.autoCommit = true }
    }

    /** 词频累加（source: cmd/chat）。upsert += 。 */
    @Synchronized
    fun bumpWords(source: String, words: Map<String, Int>) {
        if (words.isEmpty()) return
        val c = c(); c.autoCommit = false
        try {
            c.prepareStatement("INSERT INTO word_freq(source,word,count) VALUES(?,?,?) ON CONFLICT(source,word) DO UPDATE SET count=count+excluded.count").use { ps ->
                for ((w, n) in words) { ps.setString(1, source); ps.setString(2, w); ps.setInt(3, n); ps.addBatch() }
                ps.executeBatch()
            }
            c.commit()
        } catch (e: Exception) { c.rollback() } finally { c.autoCommit = true }
    }

    /** 某来源 Top-N 高频词（词云用）。 */
    @Synchronized
    fun topWords(source: String, limit: Int): List<Pair<String, Int>> =
        c().prepareStatement("SELECT word,count FROM word_freq WHERE source=? ORDER BY count DESC LIMIT ?").use { ps ->
            ps.setString(1, source); ps.setInt(2, limit)
            ps.executeQuery().use { rs -> val out = ArrayList<Pair<String, Int>>(); while (rs.next()) out.add(rs.getString(1) to rs.getInt(2)); out }
        }

    // ---- 看板：在线趋势 / 时段热力 / 行为时间线 ----
    @Synchronized
    fun recordOnline(ts: Long, cnt: Int) = c().prepareStatement("INSERT INTO online_snap VALUES(?,?)").use { it.setLong(1, ts); it.setInt(2, cnt); it.executeUpdate(); Unit }

    /** 近 days 天每日在线峰值，返回 (MM-dd, peak)。 */
    @Synchronized
    fun trendDaily(now: Long, days: Int): List<Pair<String, Int>> {
        val peak = LinkedHashMap<String, Int>()
        c().prepareStatement("SELECT ts,cnt FROM online_snap WHERE ts>? ORDER BY ts").use { ps ->
            ps.setLong(1, now - days * 86_400_000L)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val d = java.time.Instant.ofEpochMilli(rs.getLong(1)).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString().substring(5)
                    peak[d] = maxOf(peak[d] ?: 0, rs.getInt(2))
                }
            }
        }
        return peak.entries.map { it.key to it.value }
    }

    /** 7×24 时段热力（行=周一..周日，列=0..23 时），数据来自近 30 天会话登入时刻。 */
    @Synchronized
    fun heatmap(): Array<IntArray> {
        val m = Array(7) { IntArray(24) }
        val cutoff = System.currentTimeMillis() - 30L * 86_400_000L
        c().prepareStatement("SELECT join_ts FROM sessions WHERE join_ts > ?").use { ps ->
            ps.setLong(1, cutoff)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val z = java.time.Instant.ofEpochMilli(rs.getLong(1)).atZone(java.time.ZoneId.systemDefault())
                    m[z.dayOfWeek.value - 1][z.hour]++
                }
            }
        }
        return m
    }

    /** 近期事件时间线：合并 死亡/成就 + 登入/登出，按时间倒序。 */
    @Synchronized
    fun feed(limit: Int): List<FeedRow> {
        val sql = "SELECT ts,name,type,detail FROM (" +
            "SELECT ts,name,type,detail FROM events " +
            "UNION ALL SELECT join_ts ts,name,'join' type,'' detail FROM sessions " +
            "UNION ALL SELECT quit_ts ts,name,'quit' type,'' detail FROM sessions) ORDER BY ts DESC LIMIT ?"
        return c().prepareStatement(sql).use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs ->
                val out = ArrayList<FeedRow>()
                while (rs.next()) out.add(FeedRow(rs.getLong("ts"), rs.getString("name") ?: "?", rs.getString("type") ?: "", rs.getString("detail") ?: ""))
                out
            }
        }
    }

    /** 清理超过保留期的原始 events + 过期 sessions + 在线快照（画像/词频不清）。 */
    @Synchronized
    fun pruneEvents(before: Long): Int {
        c().prepareStatement("DELETE FROM online_snap WHERE ts < ?").use { it.setLong(1, before); it.executeUpdate() }
        c().prepareStatement("DELETE FROM sessions WHERE join_ts < ?").use { it.setLong(1, before); it.executeUpdate() }
        return c().prepareStatement("DELETE FROM events WHERE ts < ?").use { it.setLong(1, before); it.executeUpdate() }
    }

    // ---- T0 查询 ----
    @Synchronized
    fun stats(now: Long): Stats = c().createStatement().use { st ->
        fun q1(sql: String): Long = st.executeQuery(sql).use { if (it.next()) it.getLong(1) else 0L }
        val day = 86_400_000L
        val total = q1("SELECT COUNT(*) FROM profile")
        val a1 = q1("SELECT COUNT(*) FROM profile WHERE last_seen > ${now - day}")
        val a7 = q1("SELECT COUNT(*) FROM profile WHERE last_seen > ${now - 7 * day}")
        val newToday = q1("SELECT COUNT(*) FROM profile WHERE first_seen > ${now - day}")
        val avgPlay = q1("SELECT IFNULL(AVG(playtime_sec),0) FROM profile")
        val deaths = q1("SELECT IFNULL(SUM(death_count),0) FROM profile")
        val broken = q1("SELECT IFNULL(SUM(blocks_broken),0) FROM profile")
        val crafts = q1("SELECT IFNULL(SUM(craft_count),0) FROM profile")
        val advs = q1("SELECT IFNULL(SUM(adv_count),0) FROM profile")
        val top = ArrayList<Pair<String, Long>>()
        st.executeQuery("SELECT name, playtime_sec FROM profile ORDER BY playtime_sec DESC LIMIT 5").use { rs ->
            while (rs.next()) top += (rs.getString(1) ?: "?") to rs.getLong(2)
        }
        Stats(total, a1, a7, newToday, avgPlay, deaths, broken, crafts, advs, top)
    }

    /** T1 规则分群计数。 */
    @Synchronized
    fun segments(now: Long, churnDays: Int, activeMinutes: Int, newbieDays: Int): Map<String, Long> {
        val day = 86_400_000L
        val activeSec = activeMinutes * 60L
        return c().createStatement().use { st ->
            fun q1(sql: String): Long = st.executeQuery(sql).use { if (it.next()) it.getLong(1) else 0L }
            linkedMapOf(
                "新玩家" to q1("SELECT COUNT(*) FROM profile WHERE first_seen > ${now - newbieDays * day}"),
                "核心(在线≥${activeMinutes}分)" to q1("SELECT COUNT(*) FROM profile WHERE playtime_sec >= $activeSec AND last_seen > ${now - churnDays * day}"),
                "活跃(近${churnDays}天)" to q1("SELECT COUNT(*) FROM profile WHERE last_seen > ${now - churnDays * day} AND playtime_sec < $activeSec AND first_seen <= ${now - newbieDays * day}"),
                "流失风险(>${churnDays}天未见)" to q1("SELECT COUNT(*) FROM profile WHERE last_seen <= ${now - churnDays * day}")
            )
        }
    }

    /** 某玩家各会话登入时刻的 24 小时直方图（用本机时区）——用于"活跃时段"画像。 */
    @Synchronized
    fun playerHourHistogram(uuid: String): IntArray {
        val h = IntArray(24)
        c().prepareStatement("SELECT join_ts FROM sessions WHERE uuid=?").use { ps ->
            ps.setString(1, uuid)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val hr = java.time.Instant.ofEpochMilli(rs.getLong(1)).atZone(java.time.ZoneId.systemDefault()).hour
                    if (hr in 0..23) h[hr]++
                }
            }
        }
        return h
    }

    @Synchronized
    fun player(name: String): Profile? = c().prepareStatement("SELECT * FROM profile WHERE name=? COLLATE NOCASE ORDER BY last_seen DESC LIMIT 1").use { ps ->
        ps.setString(1, name)
        ps.executeQuery().use { rs ->
            if (!rs.next()) null else Profile(
                rs.getString("uuid"), rs.getString("name"), rs.getLong("first_seen"), rs.getLong("last_seen"),
                rs.getLong("playtime_sec"), rs.getInt("session_count"), rs.getInt("death_count"), rs.getInt("cmd_count"),
                rs.getInt("chat_count"), rs.getInt("blocks_placed"), rs.getInt("blocks_broken"), rs.getInt("craft_count"), rs.getInt("adv_count")
            )
        }
    }

    /** 获取所有玩家画像（生命周期分析用）。 */
    @Synchronized
    fun allProfiles(): List<Profile> = c().prepareStatement("SELECT * FROM profile").use { ps ->
        ps.executeQuery().use { rs ->
            val out = mutableListOf<Profile>()
            while (rs.next()) out += Profile(
                rs.getString("uuid"), rs.getString("name"), rs.getLong("first_seen"), rs.getLong("last_seen"),
                rs.getLong("playtime_sec"), rs.getInt("session_count"), rs.getInt("death_count"), rs.getInt("cmd_count"),
                rs.getInt("chat_count"), rs.getInt("blocks_placed"), rs.getInt("blocks_broken"), rs.getInt("craft_count"), rs.getInt("adv_count")
            )
            out
        }
    }
}

data class ProfileDelta(
    val uuid: String, val name: String, var playtimeSec: Long = 0, var sessions: Int = 0, var deaths: Int = 0,
    var commands: Int = 0, var chats: Int = 0, var blocksPlaced: Int = 0, var blocksBroken: Int = 0, var crafts: Int = 0, var advancements: Int = 0
)
data class SessionRow(val uuid: String, val name: String, val joinTs: Long, val quitTs: Long, val durationSec: Long)
data class FeedRow(val ts: Long, val name: String, val type: String, val detail: String)
data class EventRow(val uuid: String, val name: String, val type: String, val ts: Long, val detail: String)
data class Stats(
    val totalPlayers: Long, val active1d: Long, val active7d: Long, val newToday: Long, val avgPlaytimeSec: Long,
    val totalDeaths: Long, val totalBlocksBroken: Long, val totalCrafts: Long, val totalAdvancements: Long,
    val topPlaytime: List<Pair<String, Long>>
)
data class Profile(
    val uuid: String, val name: String, val firstSeen: Long, val lastSeen: Long, val playtimeSec: Long,
    val sessions: Int, val deaths: Int, val commands: Int, val chats: Int, val blocksPlaced: Int, val blocksBroken: Int, val crafts: Int, val advancements: Int
)
