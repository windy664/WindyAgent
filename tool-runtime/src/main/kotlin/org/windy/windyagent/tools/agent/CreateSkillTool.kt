package org.windy.windyagent.tools.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.tools.AgentTool
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.safety.AuditLog
import org.windy.windyagent.skill.SkillRegistry

/**
 * 让 Agent **对话式创建/更新技能**——服主说人话，AI 生成 SKILL.md + Kether 脚本，本工具负责落盘。
 *
 * 两种操作：
 *  - `create_skill`：创建新技能（已存在则拒绝，除非 overwrite=true）
 *  - `update_skill`：更新已有技能内容
 *
 * Agent（LLM）负责理解服主意图并生成完整的 SKILL.md 正文（含 frontmatter）和可选的 Kether 脚本。
 * 本工具只做文件 I/O + 热重载，不参与内容生成。
 *
 * 设计意图：服主不需要会写 Kether 或 SKILL.md 语法——只要说"帮我做个技能，每天给在线玩家发钻石"，
 * AI 就能生成完整定义并保存，服主后续说"改成10个"AI 就更新。
 */
class CreateSkillTool(
    private val registry: SkillRegistry,
    private val audit: AuditLog,
    private val isUpdate: Boolean = false
) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = if (isUpdate) "update_skill" else "create_skill"
    override val description = if (isUpdate) {
        "更新一个已有技能的内容。传入技能名和新的 SKILL.md 正文（含 frontmatter），可选传入 Kether 脚本。"
    } else {
        "创建一个新技能。传入技能名和 SKILL.md 正文（含 frontmatter：name/description/args/steps 等），" +
        "可选传入 Kether 脚本。保存后立即生效（热重载）。" +
        "已存在同名技能时会拒绝（除非设 overwrite=true）。"
    }
    override val inputSchema = if (isUpdate) """{"type":"object","properties":{"name":{"type":"string","description":"技能名（须已存在）"},"md":{"type":"string","description":"完整的 SKILL.md 内容（含 --- frontmatter --- 和正文）"},"script":{"type":"string","description":"Kether 脚本内容（脚本技能才需要；纯文字/工作流技能省略）"}},"required":["name","md"]}"""
    else """{"type":"object","properties":{"name":{"type":"string","description":"技能名（英文字母/数字/下划线，如 daily_diamond）"},"md":{"type":"string","description":"完整的 SKILL.md 内容（含 --- frontmatter --- 和正文）"},"script":{"type":"string","description":"Kether 脚本内容（脚本技能才需要；纯文字/工作流技能省略）"},"overwrite":{"type":"boolean","description":"已存在同名技能时是否覆盖（默认 false）"}},"required":["name","md"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val node = mapper.readTree(inputJson)
        val name = node["name"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少技能名（name）")
        val md = node["md"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少 SKILL.md 内容（md）")
        val script = node["script"]?.asText()
        val hasScript = !script.isNullOrBlank()
        val overwrite = node["overwrite"]?.asBoolean() ?: false

        // 检查是否存在
        val exists = registry.get(name) != null

        if (isUpdate) {
            if (!exists) return ToolResult.error(toolCallId, "技能「$name」不存在，无法更新（请用 create_skill 创建）")
        } else {
            if (exists && !overwrite) return ToolResult.error(toolCallId, "技能「$name」已存在。设 overwrite=true 覆盖，或换个名字。")
        }

        // 标记来源为 AI 生成（如果 md 中未指定 origin）
        val markedMd = if (!md.contains("origin:") && !isUpdate) {
            // 在 frontmatter 的 description 行后插入 origin: ai_generated
            md.replace(Regex("(description:.*)"), "$1\norigin: ai_generated")
        } else md

        // 落盘
        val count = registry.write(name, markedMd, script ?: "", hasScript)
        if (count < 0) return ToolResult.error(toolCallId, "技能名「$name」不合法（只允许字母/数字/下划线）")

        audit.record("center", if (isUpdate) "update_skill" else "create_skill", name, "ALLOW")

        val action = if (isUpdate) "更新" else if (exists) "覆盖" else "创建"
        val type = when {
            hasScript -> "脚本技能"
            md.contains("steps:") -> "工作流技能"
            else -> "纯文字技能"
        }
        ToolResult.success(toolCallId, "已${action}技能「$name」（$type），当前共 $count 个技能。")
    }.getOrElse {
        audit.record("center", if (isUpdate) "update_skill" else "create_skill", node_name(inputJson), "ERROR", it.message ?: "")
        ToolResult.error(toolCallId, "${if (isUpdate) "更新" else "创建"}技能失败：${it.message}")
    }

    private fun node_name(json: String): String = runCatching {
        mapper.readTree(json)["name"]?.asText() ?: "?"
    }.getOrDefault("?")
}

