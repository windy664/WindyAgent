package org.windy.windyagent.knowledge

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.tools.AgentTool
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.safety.RequestContext
import org.windy.windyagent.safety.TrustLevel

/**
 * 让 Agent **写入/更新**知识库条目（FAQ、运营记录、规则约定等）。
 * 与 [KnowledgeSearchTool]（只读）配对，是夜间"整理数据→沉淀知识"的落地手。
 *
 * **信任门槛**：仅 TRUSTED（管理方 / 定时任务）可写，挡普通玩家污染。
 * 传相同 id（或同标题生成的 id）即覆盖更新，便于"滚动运营日志"这类条目反复追加。
 */
class KnowledgeWriteTool(private val manager: KnowledgeManager) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = "knowledge_write"
    override val description =
        "新增或更新一条服务器知识库条目（FAQ / 运营记录 / 规则约定等）。知识库是一个 Obsidian vault：用 folder 归入分类子目录（如 规则、活动/2026），正文可用 [[另一条标题]] 建立双链互相引用。仅写确有长期复用价值的内容，别记一次性琐碎。传已存在的 id 则覆盖更新。"
    override val inputSchema = """{"type":"object","properties":{"id":{"type":"string","description":"条目 id（vault 相对路径，如 规则/pvp规则）；留空=新建，填已存在 id=覆盖更新"},"title":{"type":"string","description":"标题"},"content":{"type":"string","description":"正文（Markdown，可用 [[标题]] 双链）"},"tags":{"type":"array","items":{"type":"string"},"description":"可选标签"},"folder":{"type":"string","description":"可选分类子目录，支持多层如 活动/2026；留空=根目录"}},"required":["title","content"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        if (RequestContext.current() != TrustLevel.TRUSTED)
            return ToolResult.error(toolCallId, "仅管理员可写入知识库（当前来源不可信，已忽略）。")
        val node = mapper.readTree(inputJson)
        val title = node["title"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少 title 参数")
        val content = node["content"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少 content 参数")
        val tags = node["tags"]?.mapNotNull { it.asText()?.takeIf { t -> t.isNotBlank() } } ?: emptyList()
        val folder = node["folder"]?.asText()?.takeIf { it.isNotBlank() } ?: ""
        val e = manager.save(node["id"]?.asText()?.takeIf { it.isNotBlank() }, title, content, tags, folder)
        ToolResult.success(toolCallId, "已写入知识库（#${e.id}）：${e.title}")
    }.getOrElse { ToolResult.error(toolCallId, "写入知识库失败：${it.message}") }
}
