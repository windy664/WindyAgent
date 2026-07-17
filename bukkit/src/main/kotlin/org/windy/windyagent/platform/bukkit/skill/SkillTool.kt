package org.windy.windyagent.platform.bukkit.skill

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.tools.AgentTool
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.safety.AuditLog
import org.windy.windyagent.skill.*
import java.io.File

/**
 * 把一个 [SkillDef] 包成本地 [AgentTool]，供**嵌入式 Agent（standalone/hub）**直接挂载——
 * 脚本与 Agent 同 JVM，无总线往返。
 *
 * 技能由服主编写（文件系统即信任边界），不过 CommandGuard；但每次执行记 [audit]
 * （ALLOW / ERROR），与命令路径同一本审计账。
 *
 * 工作流技能（[SkillDef.isWorkflow]）走 [WorkflowEngine] 多阶段编排；
 * 脚本技能走 [SkillEngine] 单次 Kether 执行；纯文字直接返回正文。
 */
class SkillTool(
    private val def: SkillDef,
    private val engine: SkillEngine,
    private val audit: AuditLog,
    /** 当前 Platform 注册的所有工具（工作流 step 调用用）。 */
    private val allTools: () -> List<AgentTool> = { emptyList() },
    /** 技能注册表（工作流 step 调用其他 skill 用）。 */
    private val skillRegistry: SkillRegistry? = null,
    /** 技能库根目录（用于 SkillState 持久化）。 */
    private val skillsDir: File? = null
) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = def.name
    override val description = def.toolDescription()
    override val inputSchema = def.inputSchema()

    override fun execute(toolCallId: String, inputJson: String): ToolResult {
        // 纯文字技能：调用即返回操作流程
        if (!def.isScript && !def.isWorkflow) {
            audit.record("local", "run_skill", def.name, "TEXT")
            return ToolResult.success(toolCallId, def.textOutput())
        }

        val argsMap = SkillArgs.toMap(runCatching { mapper.readTree(inputJson) }.getOrNull())
        // 技能状态（跨次执行持久化）
        val state = skillsDir?.let { SkillState(def.name, it) }

        // 工作流技能：走 WorkflowEngine
        if (def.isWorkflow) {
            return runCatching {
                val workflowEngine = buildWorkflowEngine(state)
                val result = workflowEngine.execute(def, argsMap)
                audit.record("local", "run_skill", def.name, if (result.success) "ALLOW" else "ERROR")
                if (result.success) ToolResult.success(toolCallId, result.message)
                else ToolResult.error(toolCallId, result.message)
            }.getOrElse {
                audit.record("local", "run_skill", def.name, "ERROR", it.message ?: "")
                ToolResult.error(toolCallId, "工作流「${def.name}」执行失败：${it.message}")
            }
        }

        // 脚本技能：走 SkillEngine
        return runCatching {
            audit.record("local", "run_skill", def.name, "ALLOW")
            ToolResult.success(toolCallId, engine.run(def, argsMap, state))
        }.getOrElse {
            audit.record("local", "run_skill", def.name, "ERROR", it.message ?: "")
            ToolResult.error(toolCallId, "技能「${def.name}」执行失败：${it.message}")
        }
    }

    private fun buildWorkflowEngine(state: SkillState? = null): WorkflowEngine {
        val tools = allTools()
        val toolMap = tools.associateBy { it.name }
        return WorkflowEngine(
            toolFinder = { name ->
                toolMap[name]?.let { tool ->
                    object : AgentToolRef {
                        override val name = tool.name
                        override fun execute(toolCallId: String, inputJson: String): ToolResultRef {
                            val r = tool.execute(toolCallId, inputJson)
                            return ToolResultRef(r.content, r.isError)
                        }
                    }
                }
            },
            skillRegistry = skillRegistry,            skillState = state
        )
    }
}


