package org.windy.windyagent.command

import org.windy.windyagent.Messages
import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.safety.TrustLevel

/** 内置元命令集合（载体无关）。 */

object ClearCommand : AgentSubcommand {
    override val name = "clear"
    override val aliases = listOf("reset", "new", "清空", "重置")
    override val description get() = Messages.t("cmd.clear.desc")
    override fun handle(args: String, ctx: CommandContext): String {
        ctx.sessions.clearSession(ctx.sessionId)
        return Messages.t("cmd.clear.done")
    }
}

object HistoryCommand : AgentSubcommand {
    override val name = "history"
    override val aliases = listOf("历史")
    override val description get() = Messages.t("cmd.history.desc")
    override fun handle(args: String, ctx: CommandContext): String {
        val h = ctx.sessions.getHistory(ctx.sessionId)
        if (h.isEmpty()) return Messages.t("cmd.history.empty")
        val sb = StringBuilder(Messages.t("cmd.history.header"))
        h.takeLast(8).forEach { m ->
            when (m) {
                is LLMMessage.User -> sb.append("\n[you] ").append(m.content.take(100))
                is LLMMessage.Assistant -> m.content?.takeIf { it.isNotBlank() }?.let { sb.append("\n[AI] ").append(it.take(100)) }
                else -> {}
            }
        }
        return sb.toString()
    }
}

object StatusCommand : AgentSubcommand {
    override val name = "status"
    override val aliases = listOf("状态")
    override val description get() = Messages.t("cmd.status.desc")
    override fun handle(args: String, ctx: CommandContext): String = ctx.status()
}

object PendingCommand : AgentSubcommand {
    override val name = "pending"
    override val aliases = listOf("待审")
    override val description get() = Messages.t("cmd.pending.desc")
    override val requiresTrusted = true
    override fun handle(args: String, ctx: CommandContext): String {
        val list = ctx.pending.list()
        return if (list.isEmpty()) Messages.t("cmd.pending.empty")
        else Messages.t("cmd.pending.header") + "\n" + list.joinToString("\n")
    }
}

object ApproveCommand : AgentSubcommand {
    override val name = "approve"
    override val aliases = listOf("批准")
    override val description get() = Messages.t("cmd.approve.desc")
    override val requiresTrusted = true
    override fun handle(args: String, ctx: CommandContext): String {
        val id = args.substringBefore(' ').trim()
        if (id.isEmpty()) return Messages.t("cmd.approve.usage")
        val r = ctx.pending.approve(id)
        ctx.audit.record("admin", "approve", id, if (r == null) "NOT_FOUND" else "APPROVED", r ?: "")
        return if (r == null) Messages.t("cmd.approve.not_found", id)
        else Messages.t("cmd.approve.done", id, r)
    }
}

object DenyCommand : AgentSubcommand {
    override val name = "deny"
    override val aliases = listOf("驳回")
    override val description get() = Messages.t("cmd.deny.desc")
    override val requiresTrusted = true
    override fun handle(args: String, ctx: CommandContext): String {
        val id = args.substringBefore(' ').trim()
        if (id.isEmpty()) return Messages.t("cmd.deny.usage")
        val d = ctx.pending.deny(id)
        ctx.audit.record("admin", "deny", id, if (d == null) "NOT_FOUND" else "DENIED_BY_ADMIN", d ?: "")
        return if (d == null) Messages.t("cmd.deny.not_found", id)
        else Messages.t("cmd.deny.done", id)
    }
}

object ValueCommand : AgentSubcommand {
    override val name = "value"
    override val aliases = listOf("估值", "价值")
    override val description get() = Messages.t("cmd.value.desc")
    override val requiresTrusted = false
    override fun handle(args: String, ctx: CommandContext): String {
        val exec = ctx.valueExecutor ?: return Messages.t("cmd.value.disabled")
        val parts = args.trim().split(Regex("\\s+"), limit = 2)
        val sub = parts.firstOrNull()?.lowercase().orEmpty()
        if (sub.isEmpty()) return Messages.t("cmd.value.usage")
        if (sub !in ValueExecutor.ALL_SUBS) return Messages.t("cmd.value.unknown_sub", sub, Messages.t("cmd.value.usage"))
        if (exec.requiresTrusted(sub) && ctx.trust != TrustLevel.TRUSTED) {
            return Messages.t("cmd.value.perm_denied", sub)
        }
        val rest = parts.getOrNull(1)?.trim().orEmpty()
        return runCatching { exec.execute(sub, rest, ctx.trust) }.getOrElse { Messages.t("cmd.value.error", it.message ?: "") }
    }
}

object MemoryCommand : AgentSubcommand {
    override val name = "memory"
    override val aliases = listOf("记忆")
    override val description get() = Messages.t("cmd.memory.desc")
    override fun handle(args: String, ctx: CommandContext): String {
        val mem = ctx.memory ?: return Messages.t("cmd.memory.disabled")
        val parts = args.trim().split(Regex("\\s+"), limit = 2)
        return when (parts.firstOrNull()?.lowercase()) {
            "forget", "删除" -> {
                val id = parts.getOrNull(1)?.trim().orEmpty()
                if (id.isEmpty()) Messages.t("cmd.memory.forget_usage")
                else if (mem.forget(id)) Messages.t("cmd.memory.forgot", id) else Messages.t("cmd.memory.not_found", id)
            }
            "clear", "清空" -> {
                val n = mem.clearScope(ctx.sessionId)
                Messages.t("cmd.memory.cleared", n)
            }
            "clean", "清理" -> {
                val n = mem.cleanDuplicates(ctx.sessionId)
                Messages.t("cmd.memory.cleaned", n)
            }
            null, "" -> {
                val list = mem.list(ctx.sessionId, ctx.trust == TrustLevel.TRUSTED)
                if (list.isEmpty()) Messages.t("cmd.memory.empty")
                else Messages.t("cmd.memory.header") + "\n" + list.joinToString("\n") {
                    val mark = when (it.scope) {
                        "global" -> Messages.t("cmd.memory.scope_global")
                        "admin" -> Messages.t("cmd.memory.scope_admin")
                        else -> ""
                    }
                    "#${it.id}$mark ${it.content}"
                }
            }
            else -> Messages.t("cmd.memory.usage")
        }
    }
}
