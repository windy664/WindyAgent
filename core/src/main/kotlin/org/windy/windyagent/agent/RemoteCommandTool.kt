package org.windy.windyagent.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.safety.AuditLog
import org.windy.windyagent.safety.CommandGuard
import org.windy.windyagent.safety.PendingApprovals
import org.windy.windyagent.safety.RequestContext
import java.util.concurrent.TimeUnit

/**
 * 让 Agent 在指定后端子服上执行一条控制台指令（经 [MessageBus] 下发到子服执行）。
 *
 * 远端能力包装成本地 AgentTool，传输无关。执行前过安全护栏（[guard]）：
 * 高危 + 不可信来源直接拒；高危 + 可信来源入 [pending] 审批闸；其余放行。每步记 [audit]。
 */
class RemoteCommandTool(
    private val bus: MessageBus,
    private val timeoutMs: Long,
    private val guard: CommandGuard,
    private val audit: AuditLog,
    private val pending: PendingApprovals
) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = "run_command_on_server"
    override val description =
        "在指定的后端子服上执行一条控制台指令（如给物品 give、传送 tp 等）。可先查询可用子服名再下发。"
    override val inputSchema = """{"type":"object","properties":{"server":{"type":"string","description":"目标子服名（须与已注册子服一致）"},"command":{"type":"string","description":"在该子服控制台执行的指令，不含前导斜杠"}},"required":["server","command"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val node = mapper.readTree(inputJson)
        val server = node["server"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少 server 参数")
        val command = node["command"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少 command 参数")

        when (val d = guard.check(command, RequestContext.current())) {
            is CommandGuard.Decision.Deny -> {
                audit.record(server, "run_command", command, "DENY", d.reason)
                return ToolResult.error(toolCallId, "命令「$command」被安全策略拦截：${d.reason}")
            }
            is CommandGuard.Decision.NeedsApproval -> {
                val id = pending.submit("在子服「$server」执行：$command") { dispatchRaw(server, command) }
                audit.record(server, "run_command", command, "NEEDS_APPROVAL", "${d.reason} #$id")
                return ToolResult.success(
                    toolCallId,
                    "⏳ 高危操作「$command」需人工审批，已提交审批单 #$id。请管理员执行 /ai-approve $id 批准（10 分钟内有效）。"
                )
            }
            is CommandGuard.Decision.Warn -> audit.record(server, "run_command", command, "WARN", d.reason)
            CommandGuard.Decision.Allow -> audit.record(server, "run_command", command, "ALLOW")
        }

        ToolResult.success(toolCallId, dispatchRaw(server, command))
    }.getOrElse { ToolResult.error(toolCallId, "下发子服指令失败：${it.message}") }

    /** 绕过 guard 的真实下发（已放行 / 已审批时调用）。 */
    private fun dispatchRaw(server: String, command: String): String {
        val argsJson = mapper.createObjectNode().put("command", command).toString()
        val reply = bus.dispatch(server, "run_command", argsJson, timeoutMs)
            .get(timeoutMs + 1000, TimeUnit.MILLISECONDS)
        return if (reply.success) reply.content else "执行未成功：${reply.content}"
    }
}
