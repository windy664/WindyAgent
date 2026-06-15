package org.windy.windyagent.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.safety.AuditLog
import java.util.concurrent.TimeUnit

/**
 * 让中心 Agent 在指定子服上调用一个**服主编写的技能（skill）**——经 [MessageBus] 下发，
 * 子服侧用内嵌 GroovyShell 执行（见 bukkit 的 SkillEngine）。
 *
 * 技能是人工编写、已审过的确定性扩展（文件系统即信任边界），故**不过 CommandGuard、不挂审批**，
 * 无人值守也可调；但每次执行记 [audit]。子服侧有主线程跳转 + 超时看门狗兜底。
 * 有哪些技能可先用 search_capabilities 查（来源标注「WindyAgent 技能」的条目，描述含参数清单）。
 */
class RemoteSkillTool(
    private val bus: MessageBus,
    private val timeoutMs: Long,
    private val audit: AuditLog
) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = "run_skill_on_server"
    override val description =
        "在指定子服上调用一个服主编写的技能（skill，扩展能力）。技能名与参数可先用 search_capabilities 查（来源为「WindyAgent 技能」的条目）。"
    override val inputSchema = """{"type":"object","properties":{"server":{"type":"string","description":"目标子服名（须与已注册子服一致）"},"skill":{"type":"string","description":"技能名"},"args":{"type":"object","description":"技能参数对象，按该技能声明的参数填；无参数可省略"}},"required":["server","skill"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val node = mapper.readTree(inputJson)
        val server = node["server"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少 server 参数")
        val skill = node["skill"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少 skill 参数")

        val payload = mapper.createObjectNode()
        payload.put("skill", skill)
        payload.replace("args", node["args"]?.takeIf { it.isObject } ?: mapper.createObjectNode())

        audit.record(server, "run_skill", skill, "ALLOW")
        val reply = bus.dispatch(server, "run_skill", payload.toString(), timeoutMs)
            .get(timeoutMs + 1000, TimeUnit.MILLISECONDS)
        if (reply.success) ToolResult.success(toolCallId, reply.content)
        else ToolResult.error(toolCallId, "技能「$skill」执行未成功：${reply.content}")
    }.getOrElse { ToolResult.error(toolCallId, "调用子服技能失败：${it.message}") }
}
