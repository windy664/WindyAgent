package org.windy.windyagent.tools.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.tools.AgentTool
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.safety.AuditLog
import org.windy.windyagent.safety.PendingApprovals
import org.windy.windyagent.safety.RequestContext
import org.windy.windyagent.safety.TrustLevel
import java.util.concurrent.TimeUnit

/**
 * 让中心 Agent 在子服上**读/改/删文件**（改配置、装插件的落地手），经 [org.windy.windyagent.bus.MessageBus] 下发到子服执行。
 * 子服侧由 ServerFileService 保证作用域/保护名单，GitRepoService 每次写删自动提交（可回滚）。
 *
 * 门控与命令同源：读=仅 TRUSTED；写=TRUSTED（允许无人值守的定时任务，配置类改动是主要用途）；
 * 删=TRUSTED 且**有人值守才可**（无人值守直接拦，删文件是最不可逆的动作），有人值守走审批闸。
 * 每步记 [audit]。这几类工具共享 [dispatchFile]。
 */
internal fun resolveServer(node: com.fasterxml.jackson.databind.JsonNode): String? =
    node["server"]?.asText()?.takeIf { it.isNotBlank() }
        ?: RequestContext.requesterServer().takeIf { it.isNotBlank() }

internal fun MessageBus.dispatchFile(server: String, action: String, argsJson: String, timeoutMs: Long): String {
    val reply = dispatch(server, action, argsJson, timeoutMs).get(timeoutMs + 1000, TimeUnit.MILLISECONDS)
    return if (reply.success) reply.content else "执行未成功：${reply.content}"
}

/** 读取子服上的文件内容。 */
class RemoteReadFileTool(private val bus: MessageBus, private val timeoutMs: Long, private val audit: AuditLog) :
    AgentTool {
    private val mapper = ObjectMapper()
    override val name = "read_file_on_server"
    override val description = "读取指定子服上某个文件的文本内容（用于查看插件配置等）。路径相对服务器根目录，须在允许作用域内（如 plugins/、config/）。"
    override val inputSchema = """{"type":"object","properties":{"server":{"type":"string","description":"目标子服名"},"path":{"type":"string","description":"相对服务器根目录的文件路径，如 plugins/Foo/config.yml"}},"required":["path"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        if (RequestContext.current() != TrustLevel.TRUSTED)
            return ToolResult.error(toolCallId, "仅管理员可读取服务器文件（当前来源不可信）。")
        val node = mapper.readTree(inputJson)
        val server = resolveServer(node) ?: return ToolResult.error(toolCallId, "缺少 server 参数")
        val path = node["path"]?.asText()?.takeIf { it.isNotBlank() } ?: return ToolResult.error(toolCallId, "缺少 path 参数")
        audit.record(server, "file_read", path, "ALLOW")
        val args = mapper.createObjectNode().put("path", path).toString()
        ToolResult.success(toolCallId, bus.dispatchFile(server, "fs_read", args, timeoutMs))
    }.getOrElse { ToolResult.error(toolCallId, "读取子服文件失败：${it.message}") }
}

/** 列出子服上某目录的文件。 */
class RemoteListDirTool(private val bus: MessageBus, private val timeoutMs: Long, private val audit: AuditLog) :
    AgentTool {
    private val mapper = ObjectMapper()
    override val name = "list_dir_on_server"
    override val description = "列出指定子服上某目录下的文件与子目录。路径留空=列出允许作用域的各根目录（如 plugins/、config/）。"
    override val inputSchema = """{"type":"object","properties":{"server":{"type":"string","description":"目标子服名"},"path":{"type":"string","description":"相对服务器根目录的目录路径；留空=列出各允许根"}},"required":[]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        if (RequestContext.current() != TrustLevel.TRUSTED)
            return ToolResult.error(toolCallId, "仅管理员可浏览服务器文件（当前来源不可信）。")
        val node = mapper.readTree(inputJson)
        val server = resolveServer(node) ?: return ToolResult.error(toolCallId, "缺少 server 参数")
        val path = node["path"]?.asText() ?: ""
        val args = mapper.createObjectNode().put("path", path).toString()
        ToolResult.success(toolCallId, bus.dispatchFile(server, "fs_list", args, timeoutMs))
    }.getOrElse { ToolResult.error(toolCallId, "列目录失败：${it.message}") }
}

/** 写入/覆盖子服上的文件（写后子服侧自动 git 提交，可回滚）。 */
class RemoteWriteFileTool(private val bus: MessageBus, private val timeoutMs: Long, private val audit: AuditLog) :
    AgentTool {
    private val mapper = ObjectMapper()
    override val name = "write_file_on_server"
    override val description = "写入或覆盖指定子服上的一个文本文件（如修改插件配置）。写入后子服会自动用 git 提交本次改动，改错可用 rollback_config_on_server 回滚。路径须在允许作用域内、非受保护路径。改动通常需重载插件或重启服务器才生效。"
    override val inputSchema = """{"type":"object","properties":{"server":{"type":"string","description":"目标子服名"},"path":{"type":"string","description":"相对服务器根目录的文件路径"},"content":{"type":"string","description":"文件完整新内容（会覆盖原文件）"},"reason":{"type":"string","description":"改动原因（写进 git 提交信息，便于溯源）"}},"required":["path","content"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        if (RequestContext.current() != TrustLevel.TRUSTED)
            return ToolResult.error(toolCallId, "仅管理员可修改服务器文件（当前来源不可信）。")
        val node = mapper.readTree(inputJson)
        val server = resolveServer(node) ?: return ToolResult.error(toolCallId, "缺少 server 参数")
        val path = node["path"]?.asText()?.takeIf { it.isNotBlank() } ?: return ToolResult.error(toolCallId, "缺少 path 参数")
        val content = node["content"]?.asText() ?: return ToolResult.error(toolCallId, "缺少 content 参数")
        val reason = node["reason"]?.asText()?.takeIf { it.isNotBlank() } ?: "Agent 修改"
        audit.record(server, "file_write", path, "ALLOW", reason)
        val args = mapper.createObjectNode().put("path", path).put("content", content).put("reason", reason).toString()
        ToolResult.success(toolCallId, bus.dispatchFile(server, "fs_write", args, timeoutMs))
    }.getOrElse { ToolResult.error(toolCallId, "写入子服文件失败：${it.message}") }
}

/** 删除子服上的文件（有人值守才可，走审批闸；删前子服侧已 git 跟踪，可回滚）。 */
class RemoteDeleteFileTool(
    private val bus: MessageBus, private val timeoutMs: Long,
    private val audit: AuditLog, private val pending: PendingApprovals
) : AgentTool {
    private val mapper = ObjectMapper()
    override val name = "delete_file_on_server"
    override val description = "删除指定子服上的一个文件或空目录。这是不可逆高危动作：无人值守的定时任务中会被拒绝；有人值守时需管理员审批。"
    override val inputSchema = """{"type":"object","properties":{"server":{"type":"string","description":"目标子服名"},"path":{"type":"string","description":"相对服务器根目录的文件路径"}},"required":["path"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        if (RequestContext.current() != TrustLevel.TRUSTED)
            return ToolResult.error(toolCallId, "仅管理员可删除服务器文件（当前来源不可信）。")
        val node = mapper.readTree(inputJson)
        val server = resolveServer(node) ?: return ToolResult.error(toolCallId, "缺少 server 参数")
        val path = node["path"]?.asText()?.takeIf { it.isNotBlank() } ?: return ToolResult.error(toolCallId, "缺少 path 参数")
        if (RequestContext.unattended()) {
            audit.record(server, "file_delete", path, "BLOCKED_UNATTENDED")
            return ToolResult.error(toolCallId, "删除文件「$path」在无人值守的定时任务中被拦截（仅记录，未执行）。")
        }
        val args = mapper.createObjectNode().put("path", path).toString()
        val id = pending.submit("在子服「$server」删除文件：$path") { bus.dispatchFile(server, "fs_delete", args, timeoutMs) }
        audit.record(server, "file_delete", path, "NEEDS_APPROVAL", "#$id")
        ToolResult.success(toolCallId, "⏳ 删除文件「$path」需人工审批，已提交审批单 #$id（/ai-approve $id，10 分钟内有效）。")
    }.getOrElse { ToolResult.error(toolCallId, "删除子服文件失败：${it.message}") }
}
