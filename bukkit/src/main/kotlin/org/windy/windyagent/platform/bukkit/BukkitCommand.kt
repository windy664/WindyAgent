package org.windy.windyagent.platform.bukkit

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.Messages
import org.windy.windyagent.agent.Agent
import org.windy.windyagent.agent.AgentContext
import org.windy.windyagent.command.AgentCommandRouter
import org.windy.windyagent.knowledge.PlayerQa
import org.windy.windyagent.platform.SessionManager
import org.windy.windyagent.safety.TrustLevel

/**
 * `/ai <消息>` 命令（控制台 + 玩家通用）。
 *
 * LLM 调用是阻塞网络操作，绝不能在主线程跑：用 runTaskAsynchronously 异步执行，
 * 工具内部再经 [BukkitActions] 跳回主线程；回复也跳回主线程发给 sender。
 */
class BukkitCommand(
    private val plugin: JavaPlugin,
    private val agent: Agent,
    private val playerQa: PlayerQa,
    private val platform: BukkitPlatform,
    private val sessions: SessionManager,
    private val router: AgentCommandRouter,
    private val rateLimiter: org.windy.windyagent.safety.RateLimiter? = null
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val input = args.joinToString(" ").trim()
        if (input.isEmpty()) {
            sender.sendMessage(Messages.t("cmd.usage"))
            return true
        }
        val sessionId = if (sender is Player) sender.name else "console"

        // 速率限制（非管理员也受限）
        if (rateLimiter != null && !rateLimiter.tryAcquire(sessionId)) {
            sender.sendMessage(Messages.t("cmd.rate_limited"))
            return true
        }

        val trust = if (sender.hasPermission("windyagent.admin")) TrustLevel.TRUSTED else TrustLevel.UNTRUSTED
        sender.sendMessage(Messages.t("cmd.processing"))

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            runCatching {
                val meta = router.dispatch(input, sessionId, trust)
                if (meta != null) { reply(sender, meta); return@Runnable }
                val out = if (trust == TrustLevel.TRUSTED) {
                    val resp = agent.run(AgentContext(sessionId, input, platform, sessions.getHistory(sessionId), trust))
                    sessions.trimHistory(sessionId); resp.message
                } else playerQa.answer(input)
                reply(sender, out)
            }.onFailure {
                plugin.logger.severe("Agent error ($sessionId): ${it.message}")
                reply(sender, Messages.t("cmd.error"))
            }
        })
        return true
    }

    private fun reply(sender: CommandSender, message: String) {
        Bukkit.getScheduler().runTask(plugin, Runnable { sender.sendMessage("[WindyAgent] $message") })
    }
}
