package org.windy.windyagent.platform.bukkit

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.windy.windyagent.command.AgentCommandRouter
import org.windy.windyagent.safety.TrustLevel

/**
 * 顶层审批命令 `/ai-approve|ai-deny|ai-pending` —— **薄适配**，逻辑转交 [AgentCommandRouter]，
 * 与 `/ai approve …` 单一来源。一个执行器按命令名分派。
 */
class BukkitApprovalCommand(private val router: AgentCommandRouter) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val action = command.name.lowercase().removePrefix("ai-") // ai-approve → approve
        val trust = if (sender.hasPermission("windyagent.admin")) TrustLevel.TRUSTED else TrustLevel.UNTRUSTED
        val session = if (sender is Player) sender.name else "console"
        val reply = router.dispatch("$action ${args.joinToString(" ")}".trim(), session, trust) ?: "未知命令"
        sender.sendMessage("[WindyAgent] $reply")
        return true
    }
}
