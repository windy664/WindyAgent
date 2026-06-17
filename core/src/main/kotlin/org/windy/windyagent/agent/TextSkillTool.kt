package org.windy.windyagent.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.safety.AuditLog
import org.windy.windyagent.skill.*

/**
 * 把中心 Agent 的技能包成本地工具。
 *
 * - **纯文字技能**（script=null, steps=null）：调用即返回正文（渐进式披露），Agent 据此用其它工具办事。
 * - **工作流技能**（steps 非空）：走 [WorkflowEngine] 多阶段编排，可调用中心已注册的其它工具。
 *
 * 脚本技能不走这里（由 [RemoteSkillTool] 下发子服执行）。
 */
class TextSkillTool(
    private val def: SkillDef,
    private val audit: AuditLog,
    /** 当前 Platform 注册的所有工具（工作流 step 调用用）。 */
    private val allTools: () -> List<AgentTool> = { emptyList() },
    /** 技能注册表（工作流 step 调用其他 skill 用）。 */
    private val skillRegistry: SkillRegistry? = null
) : AgentTool {

    private val mapper = ObjectMapper()

    init { require(!def.isScript) { "TextSkillTool 不支持脚本技能：${def.name}" } }

    override val name = def.name
    override val description = def.toolDescription()
    override val inputSchema = def.inputSchema()

    override fun execute(toolCallId: String, inputJson: String): ToolResult {
        // 工作流技能：走 WorkflowEngine
        if (def.isWorkflow) {
            return runCatching {
                val argsMap = runCatching {
                    val node = mapper.readTree(inputJson)
                    val map = LinkedHashMap<String, Any?>()
                    node.fields().forEach { (k, v) ->
                        map[k] = when {
                            v.isNull -> null
                            v.isInt || v.isLong -> v.asLong()
                            v.isNumber -> v.asDouble()
                            v.isBoolean -> v.asBoolean()
                            v.isTextual -> v.asText()
                            else -> v.toString()
                        }
                    }
                    map
                }.getOrDefault(emptyMap())

                val engine = WorkflowEngine(
                    toolFinder = { findTool(it) },
                    skillRegistry = skillRegistry,
                    groovyClassLoader = javaClass.classLoader
                )
                val result = engine.execute(def, argsMap)
                audit.record("center", "run_skill", def.name, if (result.success) "WF_OK" else "WF_ERR")
                if (result.success) ToolResult.success(toolCallId, result.message)
                else ToolResult.error(toolCallId, result.message)
            }.getOrElse {
                audit.record("center", "run_skill", def.name, "ERROR", it.message ?: "")
                ToolResult.error(toolCallId, "工作流「${def.name}」执行失败：${it.message}")
            }
        }

        // 纯文字技能：直接返回正文
        audit.record("center", "run_skill", def.name, "TEXT")
        return ToolResult.success(toolCallId, def.textOutput())
    }

    private fun findTool(name: String): AgentToolRef? {
        return allTools().find { it.name == name }?.let { tool ->
            object : AgentToolRef {
                override val name = tool.name
                override fun execute(toolCallId: String, inputJson: String): ToolResultRef {
                    val r = tool.execute(toolCallId, inputJson)
                    return ToolResultRef(r.content, r.isError)
                }
            }
        }
    }
}
