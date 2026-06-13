package org.windy.windyagent.platform.bukkit.behavior

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
 * 写入只由 [BehaviorTracker] 的后台单线程调用，串行、批量；监听器线程不碰这里。
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
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_profile_last ON profile(last_seen)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sessions_join ON sessions(join_ts)")
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

    /** 清理超过保留期的原始 events（画像/会话不清）。 */
    @Synchronized
    fun pruneEvents(before: Long): Int = c().prepareStatement("DELETE FROM events WHERE ts < ?").use { it.setLong(1, before); it.executeUpdate() }

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
}

data class ProfileDelta(
    val uuid: String, val name: String, var playtimeSec: Long = 0, var sessions: Int = 0, var deaths: Int = 0,
    var commands: Int = 0, var chats: Int = 0, var blocksPlaced: Int = 0, var blocksBroken: Int = 0, var crafts: Int = 0, var advancements: Int = 0
)
data class SessionRow(val uuid: String, val name: String, val joinTs: Long, val quitTs: Long, val durationSec: Long)
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
