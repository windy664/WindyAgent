package org.windy.windyagent.platform.bukkit.skill

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.safety.AuditLog

/**
 * 把一个 [SkillDef] 包成本地 [AgentTool]，供**嵌入式 Agent（standalone/hub）**直接挂载——
 * 脚本与 Agent 同 JVM，无总线往返。
 *
 * 技能由服主编写（文件系统即信任边界），不过 CommandGuard；但每次执行记 [audit]
 * （ALLOW / ERROR），与命令路径同一本审计账。
 */
class SkillTool(
    private val def: SkillDef,
    private val engine: SkillEngine,
    private val audit: AuditLog
) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = def.name
    override val description = def.toolDescription()
    override val inputSchema = def.inputSchema()

    override fun execute(toolCallId: String, inputJson: String): ToolResult {
        // 纯文字技能：调用即返回操作流程（不执行任何代码），由 Agent 据此用其它工具办事
        if (!def.isScript) {
            audit.record("local", "run_skill", def.name, "TEXT")
            return ToolResult.success(toolCallId, def.textOutput())
        }
        return runCatching {
            val argsMap = SkillArgs.toMap(runCatching { mapper.readTree(inputJson) }.getOrNull())
            audit.record("local", "run_skill", def.name, "ALLOW")
            ToolResult.success(toolCallId, engine.run(def, argsMap))
        }.getOrElse {
            audit.record("local", "run_skill", def.name, "ERROR", it.message ?: "")
            ToolResult.error(toolCallId, "技能「${def.name}」执行失败：${it.message}")
        }
    }
}
