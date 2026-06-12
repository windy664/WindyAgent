package org.windy.windyagent.platform.bukkit

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.agent.Agent
import org.windy.windyagent.agent.AgentContext
import org.windy.windyagent.command.AgentCommandRouter
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
    private val platform: BukkitPlatform,
    private val sessions: SessionManager,
    private val router: AgentCommandRouter
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val input = args.joinToString(" ").trim()
        if (input.isEmpty()) {
            sender.sendMessage("[WindyAgent] 用法：/ai <消息>")
            return true
        }
        val sessionId = if (sender is Player) sender.name else "console"
        // 控制台或有权限玩家 = 可信（高危走审批）；其余不可信（高危直接拒）
        val trust = if (sender.hasPermission("windyagent.admin")) TrustLevel.TRUSTED else TrustLevel.UNTRUSTED
        sender.sendMessage("[WindyAgent] 正在处理……")

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            runCatching {
                val meta = router.dispatch(input, sessionId, trust)
                if (meta != null) { reply(sender, meta); return@Runnable }
                val ctx = AgentContext(sessionId, input, platform, sessions.getHistory(sessionId), trust)
                val resp = agent.run(ctx)
                sessions.trimHistory(sessionId)
                reply(sender, resp.message)
            }.onFailure {
                plugin.logger.severe("Agent 处理出错（$sessionId）：${it.message}")
                reply(sender, "处理出错，请稍后重试。")
            }
        })
        return true
    }

    private fun reply(sender: CommandSender, message: String) {
        Bukkit.getScheduler().runTask(plugin, Runnable { sender.sendMessage("[WindyAgent] $message") })
    }
}
