package org.windy.windyagent.llm

import org.slf4j.LoggerFactory
import org.windy.windyagent.tools.AgentTool
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
class LlmBudgetExceededException(val usedTokens: Long, val budget: Long) :
    RuntimeException("已达今日 LLM 用量上限（$usedTokens/$budget tokens），为控制成本已暂停 AI 调用，次日自动恢复或调高 llm.budget-daily-tokens。")

class LLMUsageTracker private constructor(
    private val delegate: LLMProvider,
    private val dbPath: Path,
    private val dailyTokenBudget: Long = 0
) : LLMProvider, org.windy.windyagent.tools.StreamingProvider {

    override val name: String get() = delegate.name

    // 成本熔断(#3)：内存累计今日 token，跨天重置；达 dailyTokenBudget 后新请求抛异常。
    @Volatile private var budgetDay: String = java.time.LocalDate.now().toString()
    private val budgetTokens = java.util.concurrent.atomic.AtomicLong(0)
    private fun bumpBudget(inTok: Int, outTok: Int) { rollBudgetDay(); budgetTokens.addAndGet((inTok + outTok).toLong()) }
    private fun rollBudgetDay() { val d = java.time.LocalDate.now().toString(); if (d != budgetDay) synchronized(this) { if (d != budgetDay) { budgetDay = d; budgetTokens.set(0) } } }
    private fun checkBudget() { if (dailyTokenBudget <= 0) return; rollBudgetDay(); val u = budgetTokens.get(); if (u >= dailyTokenBudget) throw LlmBudgetExceededException(u, dailyTokenBudget) }
    /** 今日成本熔断进度：(已用tokens, 每日预算；预算<=0=未设限)。供 get_llm_usage 工具与管理端只读。 */
    fun budgetStatus(): Pair<Long, Long> { rollBudgetDay(); return budgetTokens.get() to dailyTokenBudget }

    /** 透传底层的流式能力并统计 token；底层不支持流式则回退一次性 emit。 */
    override fun chatStream(systemPrompt: String, messages: List<LLMMessage>, tools: List<AgentTool>): ChatStream =
        trackedStream(delegate, systemPrompt, messages, tools)

    /** 透传任意 target 的流式能力并统计 token（target 不支持则回退一次性 chat）。两条路径都计入成本，
     *  使流式对话同样统计（SSE 通常不带 usage，用与 chat() 同款 estimate 兜底）。 */
    private fun trackedStream(target: LLMProvider, systemPrompt: String, messages: List<LLMMessage>, tools: List<AgentTool>): ChatStream {
        checkBudget()
        val start = System.currentTimeMillis()
        val out = ChatStream()
        val sp = target as? org.windy.windyagent.tools.StreamingProvider
        if (sp != null) {
            val src = sp.chatStream(systemPrompt, messages, tools)
            Thread({
                val sb = StringBuilder()
                var realIn = -1
                var realOut = -1
                while (true) {
                    val chunk = src.read() ?: break
                    when (chunk) {
                        is StreamChunk.Text -> sb.append(chunk.text)
                        is StreamChunk.Usage -> { realIn = chunk.inputTokens; realOut = chunk.outputTokens }
                        else -> {}
                    }
                    // Usage 帧是内部统计信号，不往下游前端透传
                    if (chunk !is StreamChunk.Usage) out.emit(chunk)
                    if (chunk is StreamChunk.Done || chunk is StreamChunk.Error) {
                        recordStream(target.name, systemPrompt, messages, sb.toString(), start, realIn, realOut); break
                    }
                }
            }, "usage-stream").apply { isDaemon = true }.start()
        } else {
            Thread({
                runCatching { target.chat(systemPrompt, messages, tools).textContent ?: "" }
                    .onSuccess {
                        recordStream(target.name, systemPrompt, messages, it, start)
                        out.emit(StreamChunk.Text(it)); out.emit(StreamChunk.Done)
                    }
                    .onFailure { out.emit(StreamChunk.Error(it.message ?: "chat failed")) }
            }, "usage-stream-fallback").apply { isDaemon = true }.start()
        }
        return out
    }

    /** 记录一次流式调用的用量。realIn/realOut>=0 时用 provider 回报的精确值，否则用 estimate 兜底。 */
    private fun recordStream(model: String, systemPrompt: String, messages: List<LLMMessage>, output: String, startMs: Long, realIn: Int = -1, realOut: Int = -1) {
        val latency = System.currentTimeMillis() - startMs
        val inTok = if (realIn >= 0) realIn else estimateTokens(systemPrompt, messages)
        val outTok = if (realOut >= 0) realOut else estimateTokens(output)
        queue.offer(UsageRecord(System.currentTimeMillis(), "", model, inTok, outTok, latency))
        bumpBudget(inTok, outTok)
    }

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

    override fun chat(systemPrompt: String, messages: List<LLMMessage>, tools: List<AgentTool>): LLMResponse =
        trackedChat(delegate, "", systemPrompt, messages, tools)

    /** 带 session 标记的 chat（供 AgentRouter 传入 sessionId）。 */
    fun chatWithSession(sessionId: String, systemPrompt: String, messages: List<LLMMessage>, tools: List<AgentTool>): LLMResponse =
        trackedChat(delegate, sessionId, systemPrompt, messages, tools)

    /** 统计任意 target 的一次 chat。 */
    private fun trackedChat(target: LLMProvider, session: String, systemPrompt: String, messages: List<LLMMessage>, tools: List<AgentTool>): LLMResponse {
        checkBudget()
        val start = System.currentTimeMillis()
        val response = target.chat(systemPrompt, messages, tools)
        val latency = System.currentTimeMillis() - start
        // 估算 token（provider 未返回时用字符数/4 近似）
        val inTok = if (response.inputTokens > 0) response.inputTokens else estimateTokens(systemPrompt, messages)
        val outTok = if (response.outputTokens > 0) response.outputTokens else estimateTokens(response.textContent ?: "")
        queue.offer(UsageRecord(System.currentTimeMillis(), session, target.name, inTok, outTok, latency))
        bumpBudget(inTok, outTok)
        return response
    }

    /** 用同一统计后端包装第二个 provider（如 fast-model）：用量并入同一 usage.db，靠 model 字段区分。
     *  共享同一 queue/flusher/连接，避免多连接写同库的 SQLite 锁问题。 */
    fun track(other: LLMProvider): LLMProvider = SecondaryTracked(other)

    private inner class SecondaryTracked(private val d: LLMProvider) : LLMProvider, org.windy.windyagent.tools.StreamingProvider {
        override val name: String get() = d.name
        override fun chat(systemPrompt: String, messages: List<LLMMessage>, tools: List<AgentTool>): LLMResponse =
            trackedChat(d, "", systemPrompt, messages, tools)
        override fun chatStream(systemPrompt: String, messages: List<LLMMessage>, tools: List<AgentTool>): ChatStream =
            trackedStream(d, systemPrompt, messages, tools)
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
        }.onFailure {
            // 写失败：把已出队的这批放回队列，下次 flush 重试，避免成本被少算（数据已 poll 出，不放回就永久丢）
            log.warn("用量数据写入失败，将重试：{}", it.message)
            batch.forEach { r -> queue.offer(r) }
        }
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

        fun wrap(provider: LLMProvider, dataDir: Path, dailyTokenBudget: Long = 0): LLMUsageTracker {
            val tracker = LLMUsageTracker(provider, dataDir.resolve("usage.db"), dailyTokenBudget)
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
