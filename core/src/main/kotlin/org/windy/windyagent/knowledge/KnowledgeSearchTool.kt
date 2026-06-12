package org.windy.windyagent.knowledge

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.rag.LlmQueryExpander

/**
 * 让 Agent 检索服务器知识库（商品/定价规则/会员权益/玩法/常见问答等）。
 * 这是「自动商品决策」的信息底座：决策结论应来自知识库检索，而非凭空臆造。
 *
 * 无 embedding 的 RAG：**稀疏优先**（[KeywordKnowledgeStore]），命中不足且配了 [expander] 时
 * 用 LLM 扩展查询词再检索（填补中文↔规范词鸿沟），多数查询零额外 LLM、成本可控。
 */
class KnowledgeSearchTool(
    private val store: KnowledgeStore,
    private val expander: LlmQueryExpander? = null,
    private val minHits: Int = 1
) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = "knowledge_search"
    override val description =
        "检索服务器知识库，获取商品、定价规则、会员权益、玩法、常见问答等服务器特定信息。需要这类信息时务必先查，不要凭空编造。"
    override val inputSchema = """{"type":"object","properties":{"query":{"type":"string","description":"检索关键词或问题"}},"required":["query"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val query = mapper.readTree(inputJson)["query"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少 query 参数")

        // 1) 稀疏优先
        var hits = store.search(query, 5)
        var note = ""

        // 2) 命中不足 → LLM 扩展查询再检索
        if (hits.size < minHits && expander != null) {
            val terms = expander.expand(query)
            if (terms.isNotEmpty()) {
                hits = store.search("$query ${terms.joinToString(" ")}", 5)
                note = "（已智能扩展：${terms.joinToString(" ")}）\n"
            }
        }

        if (hits.isEmpty()) return ToolResult.success(toolCallId, "知识库中未找到与「$query」相关的内容。")

        val text = hits.joinToString("\n\n") { e ->
            val tags = if (e.tags.isEmpty()) "" else "（标签：${e.tags.joinToString("、")}）"
            "## ${e.title}$tags\n${e.content}"
        }
        ToolResult.success(toolCallId, note + text)
    }.getOrElse { ToolResult.error(toolCallId, "检索失败：${it.message}") }
}
