package org.windy.windyagent.command

import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.safety.TrustLevel

/** 内置元命令集合（载体无关）。 */

object ClearCommand : AgentSubcommand {
    override val name = "clear"
    override val aliases = listOf("reset", "new", "清空", "重置")
    override val description = "清空当前会话的对话上下文，重新开始"
    override fun handle(args: String, ctx: CommandContext): String {
        ctx.sessions.clearSession(ctx.sessionId)
        return "已清空当前会话上下文，我们重新开始。"
    }
}

object HistoryCommand : AgentSubcommand {
    override val name = "history"
    override val aliases = listOf("历史")
    override val description = "查看当前会话最近的对话"
    override fun handle(args: String, ctx: CommandContext): String {
        val h = ctx.sessions.getHistory(ctx.sessionId)
        if (h.isEmpty()) return "当前会话还没有对话记录。"
        val sb = StringBuilder("最近对话：")
        h.takeLast(8).forEach { m ->
            when (m) {
                is LLMMessage.User -> sb.append("\n[你] ").append(m.content.take(100))
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
    override val description = "查看 Agent 运行状态"
    override fun handle(args: String, ctx: CommandContext): String = ctx.status()
}

object PendingCommand : AgentSubcommand {
    override val name = "pending"
    override val aliases = listOf("待审")
    override val description = "列出待人工审批的高危操作"
    override val requiresTrusted = true
    override fun handle(args: String, ctx: CommandContext): String {
        val list = ctx.pending.list()
        return if (list.isEmpty()) "当前无待审批操作。" else "待审批：\n" + list.joinToString("\n")
    }
}

object ApproveCommand : AgentSubcommand {
    override val name = "approve"
    override val aliases = listOf("批准")
    override val description = "批准并执行待审的高危操作：approve <单号>"
    override val requiresTrusted = true
    override fun handle(args: String, ctx: CommandContext): String {
        val id = args.substringBefore(' ').trim()
        if (id.isEmpty()) return "用法：approve <审批单号>"
        val r = ctx.pending.approve(id)
        ctx.audit.record("admin", "approve", id, if (r == null) "NOT_FOUND" else "APPROVED", r ?: "")
        return if (r == null) "审批单 #$id 不存在或已过期。" else "已批准 #$id：$r"
    }
}

object DenyCommand : AgentSubcommand {
    override val name = "deny"
    override val aliases = listOf("驳回")
    override val description = "驳回待审的高危操作：deny <单号>"
    override val requiresTrusted = true
    override fun handle(args: String, ctx: CommandContext): String {
        val id = args.substringBefore(' ').trim()
        if (id.isEmpty()) return "用法：deny <审批单号>"
        val d = ctx.pending.deny(id)
        ctx.audit.record("admin", "deny", id, if (d == null) "NOT_FOUND" else "DENIED_BY_ADMIN", d ?: "")
        return if (d == null) "审批单 #$id 不存在。" else "已驳回 #$id。"
    }
}

object ValueCommand : AgentSubcommand {
    override val name = "value"
    override val aliases = listOf("估值", "价值")
    override val description = "物品估值（EMC 式种子+全图传播）。VC 上需带子服名，如 value get <子服> <物品>"
    // 写类子命令逐条鉴权（见下），命令本身不整体设限，好让 get/status 放开
    override val requiresTrusted = false
    override fun handle(args: String, ctx: CommandContext): String {
        val exec = ctx.valueExecutor ?: return "本节点未启用物品估值。"
        val parts = args.trim().split(Regex("\\s+"), limit = 2)
        val sub = parts.firstOrNull()?.lowercase().orEmpty()
        if (sub.isEmpty()) return usage()
        if (sub !in ValueExecutor.ALL_SUBS) return "未知子命令「$sub」。\n" + usage()
        if (exec.requiresTrusted(sub) && ctx.trust != TrustLevel.TRUSTED) {
            return "value $sub 需要管理员权限（控制台或有 windyagent.admin 的玩家）。"
        }
        val rest = parts.getOrNull(1)?.trim().orEmpty()
        return runCatching { exec.execute(sub, rest, ctx.trust) }.getOrElse { "估值命令出错：${it.message}" }
    }

    private fun usage() = """物品估值用法（VC 上每条都带 <子服>）：
  value build <子服>            — 一键全量解析+传播估值（异步，看子服控制台）
  value llm <子服>              — LLM 给"无配方的根"(矿/掉落/原料)定种子价→重算级联（省 token，admin）
  value llm <子服> all          — LLM 给**全部**溯源够不着的物品估价（含机器/祭坛造的成品，分批，token 多）
  value get <子服> <物品>       — 查某物估值（值/置信度/合成路径）
  value set <子服> <物品> <价> [备注] — 人工锚定，关联下游自动重算
  value unset <子服> <物品>     — 取消人工锚定，重算
  value orphans <子服>          — 列模组删除后残留的孤儿锚定
  value status <子服>           — 查构建/传播进度
  value servers                — 列出已连接子服
（build/set/unset 需管理员；get/status/servers 只读放开）"""
}

object MemoryCommand : AgentSubcommand {
    override val name = "memory"
    override val aliases = listOf("记忆")
    override val description = "查看/管理长期记忆：memory [forget <id> | clear | clean]"
    override fun handle(args: String, ctx: CommandContext): String {
        val mem = ctx.memory ?: return "长期记忆未启用。"
        val parts = args.trim().split(Regex("\\s+"), limit = 2)
        return when (parts.firstOrNull()?.lowercase()) {
            "forget", "删除" -> {
                val id = parts.getOrNull(1)?.trim().orEmpty()
                if (id.isEmpty()) "用法：memory forget <记忆编号>"
                else if (mem.forget(id)) "已删除记忆 #$id。" else "记忆 #$id 不存在。"
            }
            "clear", "清空" -> {
                val n = mem.clearScope(ctx.sessionId)
                "已清空你的 $n 条长期记忆（全局记忆不受影响）。"
            }
            "clean", "清理" -> {
                val n = mem.cleanDuplicates(ctx.sessionId)
                "已清理 $n 条重复记忆。"
            }
            null, "" -> {
                val list = mem.list(ctx.sessionId, ctx.trust == TrustLevel.TRUSTED)
                if (list.isEmpty()) "暂无长期记忆。说点稳定偏好我帮你记，或用 remember。"
                else "长期记忆（你的 + 管理方 + 全服）：\n" + list.joinToString("\n") {
                    val mark = when (it.scope) { "global" -> "[全服]"; "admin" -> "[管理]"; else -> "" }
                    "#${it.id}$mark ${it.content}"
                }
            }
            else -> "用法：memory（列出） | memory forget <id> | memory clear"
        }
    }
}
