package org.windy.windyagent.platform.velocity

import com.velocitypowered.api.command.RawCommand
import net.kyori.adventure.text.Component
import org.windy.windyagent.command.AgentCommandRouter
import org.windy.windyagent.safety.TrustLevel

/**
 * 顶层审批命令 `/ai-approve|ai-deny|ai-pending` —— **薄适配**，逻辑转交 [AgentCommandRouter]，
 * 与 `/ai approve …` 单一来源。保留顶层命令仅为肌肉记忆。
 * 权限：控制台恒可；玩家需 `windyagent.admin`。
 */
class AgentApprovalCommand(
    private val router: AgentCommandRouter,
    private val action: String
) : RawCommand {

    override fun execute(invocation: RawCommand.Invocation) {
        val src = invocation.source()
        val trust = if (src.hasPermission("windyagent.admin")) TrustLevel.TRUSTED else TrustLevel.UNTRUSTED
        val reply = router.dispatch("$action ${invocation.arguments()}".trim(), "admin", trust)
            ?: "未知命令"
        src.sendMessage(Component.text("[WindyAgent] $reply"))
    }

    override fun hasPermission(invocation: RawCommand.Invocation): Boolean =
        invocation.source().hasPermission("windyagent.admin")
}
