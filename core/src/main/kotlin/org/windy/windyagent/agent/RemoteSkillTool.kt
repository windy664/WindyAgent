package org.windy.windyagent.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.safety.AuditLog
import org.windy.windyagent.safety.RequestContext
import java.util.concurrent.TimeUnit

/**
 * 让中心 Agent 在指定子服上调用一个**服主编写的技能（skill）**——经 [MessageBus] 下发，
 * 子服侧用内嵌 Kether 执行（见 bukkit 的 SkillEngine）。
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
            ?: RequestContext.requesterServer().takeIf { it.isNotBlank() }  // 未言明 → 兜底请求者所在子服
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
/**
 * Velocity/中心端保存 Kether 技能前，把脚本发送到目标 Bukkit 子服验证。
 * Velocity 不加载 TabooLib，也不执行 Kether；真正 compile/dry-run 都在目标子服完成。
 */
class RemoteValidateSkillTool(
    private val bus: MessageBus,
    private val timeoutMs: Long
) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = "validate_skill_on_server"
    override val description =
        "在指定 Bukkit 子服上验证 Kether 脚本。mode=compile 只解析语法；mode=dryRun 使用子服 Kether 引擎沙箱模拟，只记录 wa_* 动作而不执行。Velocity 中心端保存脚本技能前应先调用。"
    override val inputSchema = """{"type":"object","properties":{"server":{"type":"string","description":"目标子服名（须与已注册子服一致）"},"script":{"type":"string","description":"Kether 脚本源码"},"args":{"type":"object","description":"dryRun 模拟参数，可省略"},"mode":{"type":"string","description":"验证模式：compile 或 dryRun","enum":["compile","dryRun"]},"name":{"type":"string","description":"临时脚本名，便于错误定位；可省略"}},"required":["server","script","mode"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val node = mapper.readTree(inputJson)
        val server = node["server"]?.asText()?.takeIf { it.isNotBlank() }
            ?: RequestContext.requesterServer().takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少 server 参数")
        val script = node["script"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少 script 参数")
        val mode = node["mode"]?.asText()?.takeIf { it.equals("compile", true) || it.equals("dryRun", true) }
            ?: return ToolResult.error(toolCallId, "mode 只能是 compile 或 dryRun")

        val payload = mapper.createObjectNode()
        payload.put("script", script)
        payload.put("mode", mode)
        node["name"]?.asText()?.takeIf { it.isNotBlank() }?.let { payload.put("name", it) }
        payload.replace("args", node["args"]?.takeIf { it.isObject } ?: mapper.createObjectNode())

        val reply = bus.dispatch(server, "skill_validate", payload.toString(), timeoutMs)
            .get(timeoutMs + 1000, TimeUnit.MILLISECONDS)
        if (!reply.success) return ToolResult.error(toolCallId, "子服「$server」验证失败：${reply.content}")

        val result = mapper.readTree(reply.content)
        val success = result["success"]?.asBoolean() == true
        val error = result["error"]?.takeIf { !it.isNull }?.asText()
        val operations = result["operations"]?.takeIf { it.isArray }?.map { it.asText() }.orEmpty()
        val summary = buildString {
            append(if (success) "✅ 子服「$server」Kether ${mode} 通过" else "❌ 子服「$server」Kether ${mode} 未通过")
            if (operations.isNotEmpty()) {
                append("\n\n模拟操作（不会真实执行）：")
                operations.forEachIndexed { index, op -> append("\n  ${index + 1}. $op") }
            }
            if (!error.isNullOrBlank()) append("\n\n错误：$error")
        }
        if (success) ToolResult.success(toolCallId, summary) else ToolResult.error(toolCallId, summary)
    }.getOrElse { ToolResult.error(toolCallId, "调用子服验证失败：${it.message}") }
}
