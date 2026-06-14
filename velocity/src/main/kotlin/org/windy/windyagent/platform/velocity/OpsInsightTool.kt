package org.windy.windyagent.platform.velocity

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.web.AlertCenter
import java.util.concurrent.TimeUnit

/**
 * 给 Agent 一把「读今日运营洞察」的手：把散在各子服(行为库)与中心(告警)的数据汇成一份摘要文本，
 * 供 Agent 盘点 / 夜间整理沉淀（提炼 FAQ、归档运营、写记忆）。原始明细仍在 behavior 库，这里只出摘要。
 */
class OpsInsightTool(
    private val bus: MessageBus,
    private val online: () -> Set<String>,
    private val alerts: AlertCenter?,
    private val timeoutMs: Long
) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = "ops_digest"
    override val description =
        "拉取今日运营洞察摘要：各在线子服的玩家统计 / 活跃分群 / 高频聊天词 / 高频命令，以及近期运维告警。盘点服务器状况、整理沉淀时用。"
    override val inputSchema = """{"type":"object","properties":{}}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val servers = online()
        val sb = StringBuilder("# 今日运营数据摘要\n")
        if (servers.isEmpty()) sb.append("\n（当前无在线子服）\n")
        for (srv in servers.sorted()) {
            sb.append("\n## 子服 ").append(srv).append("\n")
            call(srv, "behavior_stats", "{}")?.let { s ->
                sb.append("- 玩家：总 ${s["totalPlayers"]?.asInt() ?: 0} · 活跃7日 ${s["active7d"]?.asInt() ?: 0} · 今日新增 ${s["newToday"]?.asInt() ?: 0} · 人均时长 ${s["avgPlaytimeMin"]?.asInt() ?: 0} 分\n")
            }
            call(srv, "behavior_segments", "{}")?.let { g ->
                val parts = g.fields().asSequence().joinToString("、") { (k, v) -> "$k ${v.asInt()}" }
                if (parts.isNotBlank()) sb.append("- 活跃分群：").append(parts).append("\n")
            }
            words(srv, "chat")?.let { sb.append("- 高频聊天词：").append(it).append("\n") }
            words(srv, "cmd")?.let { sb.append("- 高频命令：").append(it).append("\n") }
        }
        // 中心侧近期告警
        val al = runCatching { mapper.readTree(alerts?.json() ?: "[]") }.getOrNull()
        if (al != null && al.size() > 0) {
            sb.append("\n## 近期运维告警（最多 10 条）\n")
            al.take(10).forEach { a ->
                sb.append("- [${a["severity"]?.asText() ?: "?"}] ${a["server"]?.asText() ?: ""} ${a["kind"]?.asText() ?: ""}：${a["detail"]?.asText() ?: ""}\n")
            }
        }
        ToolResult.success(toolCallId, sb.toString())
    }.getOrElse { ToolResult.error(toolCallId, "拉取运营摘要失败：${it.message}") }

    private fun call(server: String, action: String, args: String): com.fasterxml.jackson.databind.JsonNode? = runCatching {
        val rep = bus.dispatch(server, action, args, timeoutMs).get(timeoutMs + 1000, TimeUnit.MILLISECONDS)
        if (rep.success) mapper.readTree(rep.content) else null
    }.getOrNull()

    private fun words(server: String, source: String): String? {
        val arr = call(server, "behavior_words", """{"source":"$source","limit":15}""") ?: return null
        if (!arr.isArray || arr.size() == 0) return null
        return arr.take(15).joinToString(" ") { "${it["word"]?.asText() ?: ""}(${it["count"]?.asInt() ?: 0})" }
    }
}
