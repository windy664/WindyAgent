package org.windy.windyagent.tools.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.tools.AgentTool
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.safety.AuditLog
import org.windy.windyagent.safety.PendingApprovals
import org.windy.windyagent.safety.RequestContext
import org.windy.windyagent.safety.TrustLevel

/**
 * 中心 Agent 查看/回滚子服上「Agent 改过的配置」的版本历史（子服侧 GitRepoService 支撑）。
 * 与 [RemoteWriteFileTool]/[RemoteDeleteFileTool] 配套：改错了用这里回滚。
 */
class RemoteGitHistoryTool(private val bus: MessageBus, private val timeoutMs: Long) : AgentTool {
    private val mapper = ObjectMapper()
    override val name = "config_history_on_server"
    override val description = "查看指定子服上「Agent 改过的文件」的最近提交历史（每条含提交号/时间/说明）。可用于找回滚点。"
    override val inputSchema = """{"type":"object","properties":{"server":{"type":"string","description":"目标子服名"},"path":{"type":"string","description":"可选：只看某个文件的历史"},"limit":{"type":"integer","description":"返回条数，默认 10"}},"required":[]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        if (RequestContext.current() != TrustLevel.TRUSTED)
            return ToolResult.error(toolCallId, "仅管理员可查看配置版本历史（当前来源不可信）。")
        val node = mapper.readTree(inputJson)
        val server = resolveServer(node) ?: return ToolResult.error(toolCallId, "缺少 server 参数")
        val args = mapper.createObjectNode()
        node["path"]?.asText()?.takeIf { it.isNotBlank() }?.let { args.put("path", it) }
        args.put("limit", node["limit"]?.asInt() ?: 10)
        ToolResult.success(toolCallId, bus.dispatchFile(server, "git_log", args.toString(), timeoutMs))
    }.getOrElse { ToolResult.error(toolCallId, "查看配置历史失败：${it.message}") }
}

/** 回滚子服上某次配置提交（生成反向提交，不改写历史）。有人值守才可，走审批闸。 */
class RemoteRollbackTool(
    private val bus: MessageBus, private val timeoutMs: Long,
    private val audit: AuditLog, private val pending: PendingApprovals
) : AgentTool {
    private val mapper = ObjectMapper()
    override val name = "rollback_config_on_server"
    override val description = "回滚指定子服上某次由 Agent 做的配置改动（用 config_history_on_server 查到提交号后传入）。会生成一个反向提交，不改写历史。需管理员审批。回滚后通常需重载/重启才生效。"
    override val inputSchema = """{"type":"object","properties":{"server":{"type":"string","description":"目标子服名"},"commit":{"type":"string","description":"要回滚的提交号（来自 config_history_on_server）"}},"required":["commit"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        if (RequestContext.current() != TrustLevel.TRUSTED)
            return ToolResult.error(toolCallId, "仅管理员可回滚配置（当前来源不可信）。")
        val node = mapper.readTree(inputJson)
        val server = resolveServer(node) ?: return ToolResult.error(toolCallId, "缺少 server 参数")
        val commit = node["commit"]?.asText()?.takeIf { it.isNotBlank() } ?: return ToolResult.error(toolCallId, "缺少 commit 参数")
        if (RequestContext.unattended()) {
            audit.record(server, "git_revert", commit, "BLOCKED_UNATTENDED")
            return ToolResult.error(toolCallId, "回滚「$commit」在无人值守的定时任务中被拦截。")
        }
        val args = mapper.createObjectNode().put("commit", commit).toString()
        val id = pending.submit("在子服「$server」回滚配置提交：$commit") { bus.dispatchFile(server, "git_revert", args, timeoutMs) }
        audit.record(server, "git_revert", commit, "NEEDS_APPROVAL", "#$id")
        ToolResult.success(toolCallId, "⏳ 回滚提交「$commit」需人工审批，已提交审批单 #$id（/ai-approve $id）。")
    }.getOrElse { ToolResult.error(toolCallId, "回滚配置失败：${it.message}") }
}
