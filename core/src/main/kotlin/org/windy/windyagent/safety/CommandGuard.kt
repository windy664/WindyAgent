package org.windy.windyagent.safety

/**
 * 命令执行护栏：Agent 跑 run_command 前，按策略判断该命令是否放行。
 *
 * acting agent 的核心安全控制——挡住幻觉/误解/提示注入把它诱导去跑 stop/op/ban/kill 等破坏性命令。
 * 黑名单思路（已知危险），服主可在 config 增删；含 @a/@e/@r 的群体选择器无论是否在表内都判高危。
 */
class CommandGuard(
    private val mode: Mode,
    denylist: List<String>
) {
    enum class Mode { ENFORCE, WARN, OFF }

    sealed interface Decision {
        object Allow : Decision
        /** 命中但 WARN 模式：放行，调用方记审计告警。 */
        data class Warn(val reason: String) : Decision
        /** 高危 + 不可信来源：直接拒绝（连审批都不给）。 */
        data class Deny(val reason: String) : Decision
        /** 高危 + 可信来源：需人工审批后才执行。 */
        data class NeedsApproval(val reason: String) : Decision
    }

    private val denied: Set<String> = (denylist.takeIf { it.isNotEmpty() } ?: DEFAULT_DENYLIST)
        .map { it.lowercase() }.toSet()

    fun check(command: String, trust: TrustLevel): Decision {
        if (mode == Mode.OFF) return Decision.Allow

        val base = baseCommand(command)
        val hitDeny = base in denied
        val hitSelector = DANGEROUS_SELECTOR.containsMatchIn(command.lowercase())
        if (!hitDeny && !hitSelector) return Decision.Allow

        val reason = when {
            hitDeny && hitSelector -> "高危命令「$base」+群体选择器"
            hitDeny -> "高危命令「$base」"
            else -> "群体选择器（@a/@e/@r）"
        }
        return when {
            mode == Mode.WARN -> Decision.Warn(reason)
            trust == TrustLevel.UNTRUSTED -> Decision.Deny("$reason（不可信来源，拒绝执行）")
            else -> Decision.NeedsApproval(reason) // 可信来源 + enforce → 走人工审批
        }
    }

    /** 取命令首 token、去前导斜杠与命名空间前缀（minecraft:op → op），小写。 */
    private fun baseCommand(command: String): String {
        val first = command.trim().trimStart('/').substringBefore(' ').lowercase()
        return first.substringAfterLast(':')
    }

    companion object {
        val DEFAULT_DENYLIST = listOf(
            "stop", "restart", "op", "deop", "ban", "ban-ip", "pardon", "pardon-ip",
            "whitelist", "save-off", "save-all", "reload", "rl", "gamerule", "difficulty",
            "kill", "execute", "forceload", "datapack", "perm", "pex", "lp"
        )
        private val DANGEROUS_SELECTOR = Regex("@[aer](?:\\s|$)")

        fun mode(name: String): Mode = when (name.lowercase()) {
            "off" -> Mode.OFF
            "warn" -> Mode.WARN
            else -> Mode.ENFORCE
        }
    }
}
