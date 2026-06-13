package org.windy.windyagent.command

import org.windy.windyagent.memory.LongTermMemory
import org.windy.windyagent.platform.SessionManager
import org.windy.windyagent.safety.AuditLog
import org.windy.windyagent.safety.PendingApprovals
import org.windy.windyagent.safety.TrustLevel

/**
 * 载体无关的 Agent 元命令（clear/history/status/approve…）。
 * 逻辑只在 core 实现一遍，任何载体（Velocity/Bukkit/未来 QQ/Web/CLI）经 [AgentCommandRouter] 复用。
 */
interface AgentSubcommand {
    val name: String
    val aliases: List<String> get() = emptyList()
    val description: String
    /** true = 仅可信来源（控制台/管理员）可用。 */
    val requiresTrusted: Boolean get() = false
    fun handle(args: String, ctx: CommandContext): String
}

/** 命令执行上下文：本次请求的会话/信任 + 共享服务。 */
class CommandContext(
    val sessionId: String,
    val trust: TrustLevel,
    val sessions: SessionManager,
    val pending: PendingApprovals,
    val audit: AuditLog,
    /** 长期记忆（未启用为 null）。 */
    val memory: LongTermMemory?,
    /** 载体提供的状态文案（provider/工具/安全等，各载体自拼）。 */
    val status: () -> String,
    /** 物品估值执行后端（未启用为 null）。 */
    val valueExecutor: ValueExecutor? = null
)
