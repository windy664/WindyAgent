package org.windy.windyagent.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.skill.SkillRegistry

/**
 * 让 Agent 读取一个技能的完整内容（SKILL.md 正文 + Groovy 脚本）。
 * 用于：① 服主问某个技能的详情；② Agent 更新技能前先读取现有定义。
 */
class ReadSkillTool(
    private val registry: SkillRegistry
) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = "read_skill"
    override val description = "读取一个技能的完整内容（SKILL.md 正文 + Groovy 脚本）。当服主问某个技能详情、或需要更新技能前先读取现有定义时调用。"
    override val inputSchema = """{"type":"object","properties":{"name":{"type":"string","description":"技能名"}},"required":["name"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult {
        val name = runCatching { mapper.readTree(inputJson)["name"]?.asText() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少技能名（name）")

        val def = registry.get(name)
            ?: return ToolResult.error(toolCallId, "技能「$name」不存在")

        val content = registry.read(def.handle)
            ?: return ToolResult.error(toolCallId, "无法读取技能「$name」的内容")

        val sb = StringBuilder()
        sb.append("## 技能「${def.name}」\n")
        sb.append("- 类型：${when { def.isWorkflow -> "工作流(${def.steps!!.size}步)"; def.isScript -> "脚本"; else -> "纯文字" }}\n")
        sb.append("- 权限：${def.permission}\n")
        if (def.tags.isNotEmpty()) sb.append("- 标签：${def.tags.joinToString(", ")}\n")

        sb.append("\n### SKILL.md\n```\n${content.md}\n```\n")

        if (content.script.isNotBlank()) {
            sb.append("\n### Groovy 脚本\n```groovy\n${content.script}\n```\n")
        }

        return ToolResult.success(toolCallId, sb.toString().trimEnd())
    }
}
