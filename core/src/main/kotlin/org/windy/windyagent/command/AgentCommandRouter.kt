package org.windy.windyagent.command

import org.windy.windyagent.Messages
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
    private val commands: List<AgentSubcommand> = buildList {
        addAll(listOf(ClearCommand, HistoryCommand, StatusCommand, PendingCommand, ApproveCommand, DenyCommand, MemoryCommand))
        if (valueExecutor != null) add(ValueCommand)
    }
    private val byName: Map<String, AgentSubcommand> = buildMap {
        for (c in commands) {
            put(c.name, c)
            c.aliases.forEach { put(it.lowercase(), c) }
        }
    }

    /** @return 元命令的回复文案；非元命令返回 null（调用方应交给 Agent 对话）。 */
    fun dispatch(input: String, sessionId: String, trust: TrustLevel): String? {
        val trimmed = input.trim()
        val token = trimmed.substringBefore(' ').lowercase().removePrefix("/")
        if (token.isEmpty()) return null
        if (token in HELP_TOKENS) return helpText()

        val cmd = byName[token] ?: return null
        if (cmd.requiresTrusted && trust != TrustLevel.TRUSTED) {
            return Messages.t("router.perm_denied", token)
        }
        val args = trimmed.substringAfter(' ', "").trim()
        val ctx = CommandContext(sessionId, trust, sessions, pending, audit, memory, statusSupplier, valueExecutor)
        return runCatching { cmd.handle(args, ctx) }.getOrElse { Messages.t("router.exec_error", it.message ?: "") }
    }

    private fun helpText(): String {
        val lines = listOf(Messages.t("router.help_show")) + commands.map {
            "${it.name} — ${it.description}" + if (it.requiresTrusted) Messages.t("router.need_admin") else ""
        }
        return Messages.t("router.help_title") + "\n" + lines.joinToString("\n") +
            "\n" + Messages.t("router.natural_hint") + "\n" +
            Messages.t("router.natural_examples") + "\n" +
            Messages.t("router.natural_footer")
    }

    companion object {
        private val HELP_TOKENS = setOf("help", "?", "？", "命令", "菜单", "帮助")
    }
}
