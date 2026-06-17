package org.windy.windyagent.agent

import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.skill.SkillRegistry

/**
 * 让 Agent 列出所有已注册技能——服主说"我有哪些技能"时调用。
 * 返回技能名、描述、类型（文字/脚本/工作流）、参数列表。
 */
class ListSkillsTool(
    private val registry: SkillRegistry
) : AgentTool {

    override val name = "list_skills"
    override val description = "列出当前所有已注册的技能（名称、描述、类型、参数）。当服主问「我有哪些技能」「技能列表」时调用。"
    override val inputSchema = """{"type":"object","properties":{},"required":[]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult {
        val skills = registry.all()
        if (skills.isEmpty()) {
            return ToolResult.success(toolCallId, "当前没有任何技能。你可以让我帮你创建一个，比如说「帮我做个技能，给在线玩家发钻石」。")
        }

        val sb = StringBuilder("共 ${skills.size} 个技能：\n\n")
        for (def in skills.sortedBy { it.name }) {
            val type = when {
                def.isWorkflow -> "🔗工作流·${def.steps!!.size}步"
                def.isScript -> "⚙️脚本"
                else -> "📄文字"
            }
            val tags = if (def.tags.isNotEmpty()) " ${def.tags.joinToString(" ") { "#$it" }}" else ""
            sb.append("• **${def.name}** [$type]$tags\n  ${def.description}\n")
            if (def.args.isNotEmpty()) {
                sb.append("  参数：")
                sb.append(def.args.joinToString("；") { a ->
                    val opt = if (!a.required) "（可选，默认${a.default ?: "无"}）" else ""
                    "${a.name}(${a.type})$opt — ${a.description}"
                })
                sb.append("\n")
            }
            if (def.isWorkflow) {
                sb.append("  步骤：${def.steps!!.joinToString(" → ") { it.name }}\n")
            }
            sb.append("\n")
        }
        return ToolResult.success(toolCallId, sb.toString().trimEnd())
    }
}
