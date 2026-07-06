package org.windy.windyagent.platform.velocity

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.web.AlertCenter
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 给 Agent 一把「读今日运营洞察」的手：把散在各子服(行为库)与中心(告警)的数据汇成一份摘要文本，
 * 供 Agent 盘点 / 夜间整理沉淀（提炼 FAQ、归档运营、写记忆）。原始明细仍在 behavior 库，这里只出摘要。
 *
 * ⚠ 数据只能在子服**在线时**经总线现拉。而内置「夜间整理」在 00:00 跑，那时点常常没人在线/子服断开/数据冷清，
 * 现拉会是空的 → Agent 无从沉淀 → 「跑了但没效果」。故这里加**持久化缓存 + 空时回退**：
 *  - 每次拉到有数据的摘要就落盘缓存（[cacheFile]）；
 *  - [startAutoRefresh] 在白天活跃时段定时把活数据刷进缓存；
 *  - 现拉为空时回退到最近一次缓存（未超 [cacheMaxAgeHours]），并注明是快照。
 * 这样 00:00 的整理任务总能拿到「当天最近一次有数据」的摘要。
 */
class OpsInsightTool(
    private val bus: MessageBus,
    private val online: () -> Set<String>,
    private val alerts: AlertCenter?,
    private val timeoutMs: Long,
    /** 缓存落盘位置（null=不持久化，仅进程内）。 */
    private val cacheFile: Path? = null,
    /** 现拉为空时，回退缓存的最大陈旧时长（小时）。 */
    private val cacheMaxAgeHours: Long = 36
) : AgentTool {

    private val mapper = ObjectMapper()
    private val log = LoggerFactory.getLogger(OpsInsightTool::class.java)

    /** 最近一次「有数据」的摘要缓存（进程内），伴随其采集时刻。 */
    @Volatile private var cachedDigest: String? = null
    @Volatile private var cachedAt: Long = 0
    private val refresher = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ops-digest-refresh").apply { isDaemon = true }
    }

    init { loadCache() }

    override val name = "ops_digest"
    override val description =
        "拉取今日运营洞察摘要：各在线子服的玩家统计 / 活跃分群 / 高频聊天词 / 高频命令，以及近期运维告警。盘点服务器状况、整理沉淀时用。"
    override val inputSchema = """{"type":"object","properties":{}}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val (live, hasData) = buildDigest()
        if (hasData) {
            cache(live)
            return@runCatching ToolResult.success(toolCallId, live)
        }
        // 现拉为空：回退到最近一次有数据的缓存
        val snap = freshCache()
        if (snap != null) {
            val age = humanAge(System.currentTimeMillis() - cachedAt)
            ToolResult.success(
                toolCallId,
                "> ⚠ 实时无可用运营数据（无在线子服或数据冷清），以下为最近一次快照（$age 前）。\n\n$snap"
            )
        } else {
            ToolResult.success(toolCallId, live)
        }
    }.getOrElse { ToolResult.error(toolCallId, "拉取运营摘要失败：${it.message}") }

    /**
     * 定时把活数据刷进缓存（白天活跃时段有人在线时刷到东西），供夜间整理回退。
     * [intervalMin] <=0 关闭。首刷延迟一个间隔（避开启动瞬间子服尚未连上）。
     */
    fun startAutoRefresh(intervalMin: Int) {
        if (intervalMin <= 0) return
        refresher.scheduleAtFixedRate({
            runCatching { if (refreshCache()) log.debug("ops_digest 缓存已刷新") }
                .onFailure { log.warn("ops_digest 缓存刷新异常：{}", it.message) }
        }, intervalMin.toLong(), intervalMin.toLong(), TimeUnit.MINUTES)
        log.info("ops_digest 缓存自动刷新已启动（每 {} 分钟）", intervalMin)
    }

    fun stop() = refresher.shutdownNow()

    /** 现拉一次；有数据则更新缓存并返回 true。供定时刷新调用。 */
    fun refreshCache(): Boolean {
        val (digest, hasData) = buildDigest()
        if (hasData) cache(digest)
        return hasData
    }

    /** 构建实时摘要，返回 (文本, 是否含真实数据)。空标题不算数据。 */
    private fun buildDigest(): Pair<String, Boolean> {
        val servers = online()
        val sb = StringBuilder("# 今日运营数据摘要\n")
        var hasData = false
        if (servers.isEmpty()) sb.append("\n（当前无在线子服）\n")
        for (srv in servers.sorted()) {
            sb.append("\n## 子服 ").append(srv).append("\n")
            call(srv, "behavior_stats", "{}")?.let { s ->
                sb.append("- 玩家：总 ${s["totalPlayers"]?.asInt() ?: 0} · 活跃7日 ${s["active7d"]?.asInt() ?: 0} · 今日新增 ${s["newToday"]?.asInt() ?: 0} · 人均时长 ${s["avgPlaytimeMin"]?.asInt() ?: 0} 分\n")
                hasData = true
            }
            call(srv, "behavior_segments", "{}")?.let { g ->
                val parts = g.fields().asSequence().joinToString("、") { (k, v) -> "$k ${v.asInt()}" }
                if (parts.isNotBlank()) { sb.append("- 活跃分群：").append(parts).append("\n"); hasData = true }
            }
            words(srv, "chat")?.let { sb.append("- 高频聊天词：").append(it).append("\n"); hasData = true }
            words(srv, "cmd")?.let { sb.append("- 高频命令：").append(it).append("\n"); hasData = true }
        }
        // 中心侧近期告警
        val al = runCatching { mapper.readTree(alerts?.json() ?: "[]") }.getOrNull()
        if (al != null && al.size() > 0) {
            sb.append("\n## 近期运维告警（最多 10 条）\n")
            al.take(10).forEach { a ->
                sb.append("- [${a["severity"]?.asText() ?: "?"}] ${a["server"]?.asText() ?: ""} ${a["kind"]?.asText() ?: ""}：${a["detail"]?.asText() ?: ""}\n")
            }
            hasData = true
        }
        return sb.toString() to hasData
    }

    private fun call(server: String, action: String, args: String): com.fasterxml.jackson.databind.JsonNode? = runCatching {
        val rep = bus.dispatch(server, action, args, timeoutMs).get(timeoutMs + 1000, TimeUnit.MILLISECONDS)
        if (rep.success) mapper.readTree(rep.content) else null
    }.getOrNull()

    private fun words(server: String, source: String): String? {
        val arr = call(server, "behavior_words", """{"source":"$source","limit":15}""") ?: return null
        if (!arr.isArray || arr.size() == 0) return null
        return arr.take(15).joinToString(" ") { "${it["word"]?.asText() ?: ""}(${it["count"]?.asInt() ?: 0})" }
    }

    // ---- 缓存 ----
    private fun cache(digest: String) {
        cachedDigest = digest
        cachedAt = System.currentTimeMillis()
        val f = cacheFile ?: return
        runCatching {
            Files.createDirectories(f.parent)
            Files.write(f, mapper.writeValueAsBytes(mapOf("ts" to cachedAt, "digest" to digest)))
        }.onFailure { log.warn("ops_digest 缓存落盘失败：{}", it.message) }
    }

    private fun loadCache() {
        val f = cacheFile ?: return
        runCatching {
            if (!Files.exists(f)) return
            val n = mapper.readTree(Files.readAllBytes(f))
            val ts = n["ts"]?.asLong() ?: 0
            val digest = n["digest"]?.asText()
            if (!digest.isNullOrBlank() && ts > 0) { cachedDigest = digest; cachedAt = ts }
        }.onFailure { log.warn("ops_digest 缓存载入失败：{}", it.message) }
    }

    /** 未超陈旧上限的缓存，否则 null。 */
    private fun freshCache(): String? {
        val d = cachedDigest ?: return null
        val ageMs = System.currentTimeMillis() - cachedAt
        return if (ageMs <= cacheMaxAgeHours * 3_600_000L) d else null
    }

    private fun humanAge(ms: Long): String {
        val d = Duration.ofMillis(ms.coerceAtLeast(0))
        val h = d.toHours()
        return if (h >= 1) "${h}小时" else "${d.toMinutes()}分钟"
    }
}
