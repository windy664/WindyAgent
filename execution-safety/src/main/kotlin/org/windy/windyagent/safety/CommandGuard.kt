package org.windy.windyagent.safety

/**
 * Agent command execution guard.
 *
 * It blocks or routes dangerous commands to approval before run_command executes
 * them, reducing damage from hallucination, misunderstanding, or prompt injection.
 */
class CommandGuard(
    private val mode: Mode,
    denylist: List<String>
) {
    enum class Mode { ENFORCE, WARN, OFF }

    sealed interface Decision {
        object Allow : Decision
        /** WARN mode: allow, but caller should audit the warning. */
        data class Warn(val reason: String) : Decision
        /** High risk + untrusted source: deny immediately. */
        data class Deny(val reason: String) : Decision
        /** High risk + trusted source: require human approval. */
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
            hitDeny && hitSelector -> "高危命令「$base」+ 群体选择器"
            hitDeny -> "高危命令「$base」"
            else -> "群体选择器（@a/@e/@r）"
        }
        return when {
            mode == Mode.WARN -> Decision.Warn(reason)
            trust == TrustLevel.UNTRUSTED -> Decision.Deny("$reason（不可信来源，拒绝执行）")
            else -> Decision.NeedsApproval(reason)
        }
    }

    /** First token, without leading slash or namespace prefix, lowercased. */
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
