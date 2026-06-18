package org.windy.windyagent.command

import org.windy.windyagent.agent.ContextCompressor
import org.windy.windyagent.agent.UserProfileManager
import org.windy.windyagent.llm.LLMUsageTracker
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
    val memory: LongTermMemory?,
    val status: () -> String,
    val valueExecutor: ValueExecutor? = null,
    /** 用量追踪器（未启用为 null）。 */
    val usageTracker: LLMUsageTracker? = null,
    /** 上下文压缩器（未启用为 null）。 */
    val compressor: ContextCompressor? = null,
    /** 用户画像管理器（未启用为 null）。 */
    val profileManager: UserProfileManager? = null
)
