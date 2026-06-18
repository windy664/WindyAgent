package org.windy.windyagent.platform

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * 会话历史持久化 + FTS5 全文搜索。
 *
 * 每次对话结束后异步写入 SQLite，支持按关键词搜索历史对话片段。
 * 供长期记忆召回时同时搜索（与关键词记忆互补：记忆记"结论"，这里记"原文"）。
 *
 * 注意：FTS5 需要 SQLite 3.34+（sqlite-jdbc 3.45 已满足）。
 */
class SessionStore(private val dbPath: Path) {

    private val log = LoggerFactory.getLogger(SessionStore::class.java)
    @Volatile private var conn: Connection? = null

    @Synchronized
    private fun c(): Connection {
        conn?.takeIf { !it.isClosed }?.let { return it }
        Files.createDirectories(dbPath.parent)
        return DriverManager.getConnection("jdbc:sqlite:$dbPath").also {
            conn = it
            it.createStatement().use { st ->
                st.executeUpdate("""CREATE TABLE IF NOT EXISTS chat_history(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session TEXT, role TEXT, content TEXT, ts INTEGER
                )""")
                // FTS5 虚拟表：对 content 做全文索引
                runCatching {
                    st.executeUpdate("CREATE VIRTUAL TABLE IF NOT EXISTS chat_fts USING fts5(content, content=chat_history, content_rowid=id)")
                    // 触发器保持 FTS 索引同步
                    st.executeUpdate("CREATE TRIGGER IF NOT EXISTS chat_ai AFTER INSERT ON chat_history BEGIN INSERT INTO chat_fts(rowid, content) VALUES (new.id, new.content); END")
                    st.executeUpdate("CREATE TRIGGER IF NOT EXISTS chat_ad AFTER DELETE ON chat_history BEGIN INSERT INTO chat_fts(chat_fts, rowid, content) VALUES('delete', old.id, old.content); END")
                }.onFailure { e ->
                    // FTS5 不可用时降级：只用普通表，搜索用 LIKE
                    log.warn("FTS5 不可用（{}），降级为 LIKE 搜索", e.message)
                }
            }
        }
    }

    init { runCatching { Class.forName("org.sqlite.JDBC") } }

    /** 持久化一条对话消息。 */
    @Synchronized
    fun append(session: String, role: String, content: String) {
        runCatching {
            c().prepareStatement("INSERT INTO chat_history(session, role, content, ts) VALUES(?,?,?,?)").use { ps ->
                ps.setString(1, session); ps.setString(2, role); ps.setString(3, content)
                ps.setLong(4, System.currentTimeMillis()); ps.executeUpdate()
            }
        }.onFailure { log.warn("会话写入失败：{}", it.message) }
    }

    /** 批量持久化。 */
    fun appendAll(session: String, messages: List<Pair<String, String>>) {
        val c = c()
        c.autoCommit = false
        try {
            c.prepareStatement("INSERT INTO chat_history(session, role, content, ts) VALUES(?,?,?,?)").use { ps ->
                for ((role, content) in messages) {
                    ps.setString(1, session); ps.setString(2, role); ps.setString(3, content)
                    ps.setLong(4, System.currentTimeMillis()); ps.addBatch()
                }
                ps.executeBatch()
            }
            c.commit()
        } catch (e: Exception) { c.rollback(); log.warn("批量写入失败：{}", e.message) }
        finally { c.autoCommit = true }
    }

    /**
     * 全文搜索历史对话。返回 (session, content, ts) 三元组。
     * FTS5 不可用时降级为 LIKE。
     */
    @Synchronized
    fun search(query: String, topK: Int = 10, sessionId: String? = null): List<Triple<String, String, Long>> {
        val ftsAvailable = runCatching {
            c().createStatement().use { it.executeQuery("SELECT count(*) FROM chat_fts").next() }
        }.getOrDefault(false)

        return if (ftsAvailable) searchFts(query, topK, sessionId) else searchLike(query, topK, sessionId)
    }

    private fun searchFts(query: String, topK: Int, sessionId: String?): List<Triple<String, String, Long>> {
        val ftsQuery = query.split(Regex("\\s+")).joinToString(" OR ") { "\"$it\"" }
        val sql = if (sessionId != null)
            "SELECT h.session, h.content, h.ts FROM chat_fts f JOIN chat_history h ON f.rowid = h.id WHERE chat_fts MATCH ? AND h.session = ? ORDER BY rank LIMIT ?"
        else
            "SELECT h.session, h.content, h.ts FROM chat_fts f JOIN chat_history h ON f.rowid = h.id WHERE chat_fts MATCH ? ORDER BY rank LIMIT ?"
        return runCatching {
            c().prepareStatement(sql).use { ps ->
                ps.setString(1, ftsQuery)
                if (sessionId != null) { ps.setString(2, sessionId); ps.setInt(3, topK) }
                else ps.setInt(2, topK)
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<Triple<String, String, Long>>()
                    while (rs.next()) out += Triple(rs.getString(1), rs.getString(2), rs.getLong(3))
                    out
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun searchLike(query: String, topK: Int, sessionId: String?): List<Triple<String, String, Long>> {
        val like = "%${query.replace("%", "\\%").replace("_", "\\_")}%"
        val sql = if (sessionId != null)
            "SELECT session, content, ts FROM chat_history WHERE content LIKE ? ESCAPE '\\' AND session = ? ORDER BY ts DESC LIMIT ?"
        else
            "SELECT session, content, ts FROM chat_history WHERE content LIKE ? ESCAPE '\\' ORDER BY ts DESC LIMIT ?"
        return runCatching {
            c().prepareStatement(sql).use { ps ->
                ps.setString(1, like)
                if (sessionId != null) { ps.setString(2, sessionId); ps.setInt(3, topK) }
                else ps.setInt(2, topK)
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<Triple<String, String, Long>>()
                    while (rs.next()) out += Triple(rs.getString(1), rs.getString(2), rs.getLong(3))
                    out
                }
            }
        }.getOrDefault(emptyList())
    }

    /** 清理超过保留期的记录。 */
    @Synchronized
    fun prune(before: Long): Int {
        return runCatching {
            c().prepareStatement("DELETE FROM chat_history WHERE ts < ?").use { ps ->
                ps.setLong(1, before); ps.executeUpdate()
            }
        }.getOrDefault(0)
    }

    fun close() { runCatching { conn?.close() } }
}
