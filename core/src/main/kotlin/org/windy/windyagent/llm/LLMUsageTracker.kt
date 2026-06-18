package org.windy.windyagent.llm

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.windy.windyagent.agent.AgentTool
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * LLM 用量追踪装饰器：包装任意 [LLMProvider]，拦截每次 chat() 统计 token/延迟。
 *
 * 存 SQLite（`usage.db`），表：`usage(ts, session, model, input_tokens, output_tokens, latency_ms)`。
 * 写入异步化（批量 flush），不阻塞 Agent 主路径。
 *
 * 使用方式：在插件启动时用 `LLMUsageTracker.wrap(realProvider, dataDir)` 包装即可。
 */
class LLMUsageTracker private constructor(
    private val delegate: LLMProvider,
    private val dbPath: Path
) : LLMProvider {

    override val name: String get() = delegate.name

    private val log = LoggerFactory.getLogger(LLMUsageTracker::class.java)
    private val queue = java.util.concurrent.ConcurrentLinkedQueue<UsageRecord>()
    private val flusher = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "usage-flush").apply { isDaemon = true } }

    @Volatile private var conn: Connection? = null

    @Synchronized
    private fun c(): Connection {
        conn?.takeIf { !it.isClosed }?.let { return it }
        Files.createDirectories(dbPath.parent)
        return DriverManager.getConnection("jdbc:sqlite:$dbPath").also {
            conn = it
            it.createStatement().use { st ->
                st.executeUpdate("""CREATE TABLE IF NOT EXISTS usage(
                    ts INTEGER, session TEXT, model TEXT,
                    input_tokens INTEGER, output_tokens INTEGER, latency_ms INTEGER
                )""")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_usage_ts ON usage(ts)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_usage_session ON usage(session)")
            }
        }
    }

    init {
        runCatching { Class.forName("org.sqlite.JDBC") }
        flusher.scheduleAtFixedRate({ flush() }, 5, 30, TimeUnit.SECONDS)
    }

    override fun chat(systemPrompt: String, messages: List<LLMMessage>, tools: List<AgentTool>): LLMResponse {
        val start = System.currentTimeMillis()
        val response = delegate.chat(systemPrompt, messages, tools)
        val latency = System.currentTimeMillis() - start
        // 估算 token（provider 未返回时用字符数/4 近似）
        val inTok = if (response.inputTokens > 0) response.inputTokens else estimateTokens(systemPrompt, messages)
        val outTok = if (response.outputTokens > 0) response.outputTokens else estimateTokens(response.textContent ?: "")
        queue.offer(UsageRecord(System.currentTimeMillis(), "", delegate.name, inTok, outTok, latency))
        return response
    }

    /** 带 session 标记的 chat（供 AgentRouter 传入 sessionId）。 */
    fun chatWithSession(sessionId: String, systemPrompt: String, messages: List<LLMMessage>, tools: List<AgentTool>): LLMResponse {
        val start = System.currentTimeMillis()
        val response = delegate.chat(systemPrompt, messages, tools)
        val latency = System.currentTimeMillis() - start
        val inTok = if (response.inputTokens > 0) response.inputTokens else estimateTokens(systemPrompt, messages)
        val outTok = if (response.outputTokens > 0) response.outputTokens else estimateTokens(response.textContent ?: "")
        queue.offer(UsageRecord(System.currentTimeMillis(), sessionId, delegate.name, inTok, outTok, latency))
        return response
    }

    private fun flush() {
        val batch = mutableListOf<UsageRecord>()
        while (true) { val r = queue.poll() ?: break; batch.add(r) }
        if (batch.isEmpty()) return
        runCatching {
            val c = c()
            c.prepareStatement("INSERT INTO usage VALUES(?,?,?,?,?,?)").use { ps ->
                for (r in batch) {
                    ps.setLong(1, r.ts); ps.setString(2, r.session); ps.setString(3, r.model)
                    ps.setInt(4, r.inputTokens); ps.setInt(5, r.outputTokens); ps.setLong(6, r.latencyMs)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }.onFailure { log.warn("用量数据写入失败：{}", it.message) }
    }

    /** 查询用量统计：按天聚合。 */
    fun queryDaily(days: Int = 7): List<DailyUsage> {
        flush() // 先刷队列
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        return runCatching {
            c().prepareStatement("""
                SELECT ts/86400000*86400000 AS day,
                       SUM(input_tokens) AS in_tok, SUM(output_tokens) AS out_tok,
                       COUNT(*) AS calls, SUM(latency_ms) AS total_ms
                FROM usage WHERE ts > ? GROUP BY day ORDER BY day
            """).use { ps ->
                ps.setLong(1, cutoff)
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<DailyUsage>()
                    while (rs.next()) out += DailyUsage(
                        rs.getLong("day"), rs.getLong("in_tok"), rs.getLong("out_tok"),
                        rs.getInt("calls"), rs.getLong("total_ms")
                    )
                    out
                }
            }
        }.getOrDefault(emptyList())
    }

    /** 总用量摘要。 */
    fun summary(): UsageSummary {
        flush()
        return runCatching {
            c().createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) c, COALESCE(SUM(input_tokens),0) i, COALESCE(SUM(output_tokens),0) o, COALESCE(SUM(latency_ms),0) l FROM usage").use { rs ->
                    rs.next(); UsageSummary(rs.getLong("c"), rs.getLong("i"), rs.getLong("o"), rs.getLong("l"))
                }
            }
        }.getOrDefault(UsageSummary(0, 0, 0, 0))
    }

    fun close() { flush(); flusher.shutdown(); runCatching { conn?.close() } }

    companion object {
        private data class UsageRecord(val ts: Long, val session: String, val model: String, val inputTokens: Int, val outputTokens: Int, val latencyMs: Long)

        fun wrap(provider: LLMProvider, dataDir: Path): LLMUsageTracker {
            val tracker = LLMUsageTracker(provider, dataDir.resolve("usage.db"))
            return tracker
        }

        /** 粗估 token 数：英文≈4字符/token，中文≈1.5字符/token。 */
        private fun estimateTokens(vararg texts: String): Int {
            val total = texts.sumOf { it.length }
            val cjk = texts.sumOf { t -> t.count { it.code in 0x4e00..0x9fff } }
            return ((total - cjk) / 4 + cjk / 1.5).toInt().coerceAtLeast(1)
        }
        private fun estimateTokens(systemPrompt: String, messages: List<LLMMessage>): Int {
            val parts = mutableListOf(systemPrompt)
            for (m in messages) when (m) {
                is LLMMessage.User -> parts += m.content
                is LLMMessage.Assistant -> m.content?.let { parts += it }
                is LLMMessage.ToolResults -> m.results.forEach { parts += it.content }
            }
            return estimateTokens(*parts.toTypedArray())
        }
    }

    data class DailyUsage(val day: Long, val inputTokens: Long, val outputTokens: Long, val calls: Int, val totalLatencyMs: Long)
    data class UsageSummary(val totalCalls: Long, val totalInputTokens: Long, val totalOutputTokens: Long, val totalLatencyMs: Long)
}
