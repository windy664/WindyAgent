package org.windy.windyagent.command

import org.windy.windyagent.memory.LongTermMemory
import org.windy.windyagent.platform.SessionManager
import org.windy.windyagent.safety.AuditLog
import org.windy.windyagent.safety.PendingApprovals
import org.windy.windyagent.safety.TrustLevel

/**
 * 载体无关的元命令分发器：触发器把整句先丢进来，命中元命令就处理、返回文案；
 * 未命中返回 null → 调用方把整句当作对话喂给 Agent。
 *
 * 这样未来任何载体（QQ/Web/CLI）只需「parse → dispatch → 输出」即可白嫖全部命令。
 */
class AgentCommandRouter(
    private val sessions: SessionManager,
    private val pending: PendingApprovals,
    private val audit: AuditLog,
    private val memory: LongTermMemory?,
    private val statusSupplier: () -> String,
    private val valueExecutor: ValueExecutor? = null
) {
    private val commands: List<AgentSubcommand> = listOfNotNull(
        ClearCommand, HistoryCommand, StatusCommand,
        PendingCommand, ApproveCommand, DenyCommand, MemoryCommand,
        if (valueExecutor != null) ValueCommand else null
    )
    private val byName: Map<String, AgentSubcommand> = buildMap {
        for (c in commands) {
            put(c.name, c)
            c.aliases.forEach { put(it.lowercase(), c) }
        }
    }

    /** @return 元命令的回复文案；非元命令返回 null（调用方应交给 Agent 对话）。 */
    fun dispatch(input: String, sessionId: String, trust: TrustLevel): String? {
        val trimmed = input.trim()
        val token = trimmed.substringBefore(' ').lowercase()
        if (token.isEmpty()) return null
        if (token in HELP_TOKENS) return helpText()

        val cmd = byName[token] ?: return null
        if (cmd.requiresTrusted && trust != TrustLevel.TRUSTED) {
            return "命令「$token」需要管理员权限（控制台或有 windyagent.admin 的玩家）。"
        }
        val args = trimmed.substringAfter(' ', "").trim()
        val ctx = CommandContext(sessionId, trust, sessions, pending, audit, memory, statusSupplier, valueExecutor)
        return runCatching { cmd.handle(args, ctx) }.getOrElse { "命令执行出错：${it.message}" }
    }

    private fun helpText(): String {
        val lines = listOf("help — 显示本帮助") + commands.map { "${it.name} — ${it.description}" }
        return "WindyAgent 命令（在触发前缀后输入，如 ai help）：\n" + lines.joinToString("\n") +
            "\n其余内容将作为对话交给 AI。"
    }

    companion object {
        private val HELP_TOKENS = setOf("help", "?", "？", "命令", "菜单", "帮助")
    }
}
