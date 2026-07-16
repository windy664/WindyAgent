package org.windy.windyagent.capability

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.rag.LlmQueryExpander

/**
 * 在**中心本地**的能力目录里检索命令（零总线往返）——取代旧的 per-query 远程自省。
 *
 * 子服启动时已把目录推到中心 [CapabilityRegistry]，这里只读本地，故秒回、不会因反复往返撞迭代。
 * 检索：关键词 + 同义词（[CommandSynonyms]）；命中不足且配了 [expander] 时再 LLM 扩展查询（无 embedding 的语义增强）。
 */
class SearchCapabilitiesTool(
    private val registry: CapabilityRegistry,
    private val expander: LlmQueryExpander? = null,
    private val minHits: Int = 1
) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = "search_capabilities"
    override val description =
        "在已同步的各子服命令目录里检索命令（含用法/来源）。可直接用中文常见词（如「传送」「经济」「领地」会自动扩展到 tp/money/claim 等相关命令）或英文命令关键词。" +
        "一次检索即可，不要反复换词猜；无关键词会给来源概览。「怎么玩/好玩」这类纯主观问题命令表答不了，宜直接问用户想要哪类功能。"
    override val inputSchema = """{"type":"object","properties":{"query":{"type":"string","description":"关键词，按命令名/别名/描述/来源过滤（如 money、tp、ban）；留空给来源概览"},"server":{"type":"string","description":"只查某个子服（可选）"},"limit":{"type":"integer","description":"最多返回多少条，默认 30"}}}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val node = mapper.readTree(inputJson)
        val query = node["query"]?.asText()?.takeIf { it.isNotBlank() }
        val server = node["server"]?.asText()?.takeIf { it.isNotBlank() }
        val limit = (node["limit"]?.asInt()?.takeIf { it > 0 } ?: 30).coerceAtMost(50)

        if (registry.isEmpty()) {
            return ToolResult.success(toolCallId, "尚未收到任何子服的能力目录（子服可能未启动或未连接中枢）。")
        }
        if (query == null) {
            return ToolResult.success(toolCallId, registry.overview(server))
        }

        // 1) 关键词+同义词检索（registry 内部完成）
        var hits = registry.search(query, server, limit)
        var note = ""
        // 2) 命中不足 → LLM 扩展查询再检索（无 embedding 语义增强）
        if (hits.size < minHits && expander != null) {
            val terms = expander.expand(query)
            if (terms.isNotEmpty()) {
                hits = registry.search("$query ${terms.joinToString(" ")}", server, limit)
                note = "（已智能扩展：${terms.joinToString(" ")}）\n"
            }
        }
        if (hits.isEmpty()) {
            return ToolResult.success(toolCallId, "未找到与「$query」相关的命令。换个说法，或不带关键词看来源概览。")
        }
        val body = hits.joinToString("\n") { h ->
            val c = h.command
            val desc = c.description.takeIf { it.isNotBlank() }?.let { " — ${it.take(60)}" }
                ?: c.usage.takeIf { it.isNotBlank() }?.let { " — 用法 ${it.take(60)}" } ?: ""
            "[${h.server}] /${c.name}$desc [${c.source.ifBlank { "原版/模组" }}]"
        }
        val tail = if (hits.size >= limit) "\n（仅列前 ${hits.size} 条，可用更具体的词缩小）" else ""
        val hint = if (hits.any { it.command.description.isBlank() && it.command.usage.isBlank() })
            "\n（部分命令只有名字没有说明，执行前用 describe_command 探查其用法）" else ""
        ToolResult.success(toolCallId, "$note「$query」相关命令（${hits.size} 条）：\n$body$tail$hint")
    }.getOrElse { ToolResult.error(toolCallId, "检索能力目录失败：${it.message}") }
}
