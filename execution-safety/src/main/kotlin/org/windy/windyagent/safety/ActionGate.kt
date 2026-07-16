package org.windy.windyagent.safety

/**
 * 具名破坏性动作（非任意命令）的信任闸，如 kick。
 * 不可信来源一律拒绝并审计；可信来源放行并审计。
 */
object ActionGate {
    /** @return 被拒时的提示文案；放行时 null。 */
    fun guardTrusted(action: String, target: String, audit: AuditLog): String? =
        if (RequestContext.current() == TrustLevel.UNTRUSTED) {
            audit.record("UNTRUSTED", action, target, "DENY", "不可信来源")
            "操作（$action $target）来自不可信来源（如玩家聊天），已被安全策略拒绝。"
        } else {
            audit.record("trusted", action, target, "ALLOW")
            null
        }
}
