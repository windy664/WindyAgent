package org.windy.windyagent.platform.bukkit

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import taboolib.common.platform.Platform
import taboolib.common.platform.PlatformSide
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.*
import org.windy.windyagent.Messages
import org.windy.windyagent.tools.AgentRouter
import org.windy.windyagent.tools.AgentContext
import org.windy.windyagent.command.AgentCommandRouter
import org.windy.windyagent.knowledge.PlayerQa
import org.windy.windyagent.platform.SessionManager
import org.windy.windyagent.safety.TrustLevel

/**
 * TabooLib 注解式命令 —— 由 Gradle 插件自动注册，无需 plugin.yml 声明。
 *
 * `/ai <消息>`：控制台 + 玩家通用，standalone/hub 模式。
 * `/ai approve|deny|pending`：审批命令（admin only）。
 *
 * 运行时依赖（agent / router / sessions 等）由 [BukkitAgentRunner] 启动后注入 [holder]。
 */
@PlatformSide(Platform.BUKKIT)
@CommandHeader("ai", description = "向 WindyAgent 提问或下达指令")
object WindyCommands {

    // ── 运行时依赖持有者，由 BukkitAgentRunner.start() 填充 ──
    var holder: CommandHolder? = null

    @CommandBody(permission = "windyagent.admin", optional = true)
    val approve = subCommand {
        dynamic("审批单号") {
            execute<CommandSender> { sender, _, arg ->
                handleApproval(sender, "approve", arg)
            }
        }
    }

    @CommandBody(permission = "windyagent.admin", optional = true)
    val deny = subCommand {
        dynamic("审批单号") {
            execute<CommandSender> { sender, _, arg ->
                handleApproval(sender, "deny", arg)
            }
        }
    }

    @CommandBody(permission = "windyagent.admin", optional = true)
    val pending = subCommand {
        execute<CommandSender> { sender, _, _ ->
            handleApproval(sender, "pending", "")
        }
    }

    @CommandBody
    val help = subCommand {
        execute<CommandSender> { sender, _, _ ->
            sender.sendMessage("§6§lWindyAgent 命令帮助")
            sender.sendMessage("§e/ai <消息> §7- 向 WindyAgent 提问或下达指令")
            sender.sendMessage("§e/ai approve <审批单号> §7- 批准待审的高危操作")
            sender.sendMessage("§e/ai deny <审批单号> §7- 驳回待审的高危操作")
            sender.sendMessage("§e/ai pending §7- 列出待审批的高危操作")
            sender.sendMessage("§e/ai help §7- 显示此帮助")
        }
    }

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, arg ->
            handleChat(sender, arg)
        }
    }

    // ── 业务逻辑 ──

    private fun handleChat(sender: CommandSender, input: String) {
        val h = holder ?: run { sender.sendMessage("§cWindyAgent 尚未就绪"); return }
        if (input.isBlank()) {
            sender.sendMessage(Messages.t("cmd.usage"))
            return
        }

        val sessionId = if (sender is Player) sender.name else "console"

        // 速率限制（非管理员也受限）
        if (h.rateLimiter != null && !h.rateLimiter.tryAcquire(sessionId)) {
            sender.sendMessage(Messages.t("cmd.rate_limited"))
            return
        }

        val trust = if (sender.hasPermission("windyagent.admin")) TrustLevel.TRUSTED else TrustLevel.UNTRUSTED
        sender.sendMessage(Messages.t("cmd.processing"))

        Bukkit.getScheduler().runTaskAsynchronously(h.plugin, Runnable {
            runCatching {
                val meta = h.router.dispatch(input, sessionId, trust)
                if (meta != null) { reply(sender, meta); return@Runnable }
                val out = if (trust == TrustLevel.TRUSTED) {
                    val resp = h.agent.run(AgentContext(sessionId, input, h.platform, h.sessions.getHistory(sessionId), trust))
                    h.sessions.trimHistory(sessionId); resp.message
                } else h.playerQa.answer(input)
                reply(sender, out)
            }.onFailure {
                h.plugin.logger.severe("[Command] 会话 $sessionId 执行出错：${it.message}")
                reply(sender, Messages.t("cmd.error"))
            }
        })
    }

    private fun handleApproval(sender: CommandSender, action: String, id: String) {
        val h = holder ?: run { sender.sendMessage("§cWindyAgent 尚未就绪"); return }
        val trust = if (sender.hasPermission("windyagent.admin")) TrustLevel.TRUSTED else TrustLevel.UNTRUSTED
        val session = if (sender is Player) sender.name else "console"
        val input = "$action $id".trim()
        val reply = h.router.dispatch(input, session, trust) ?: Messages.t("router.unknown_cmd")
        sender.sendMessage("[WindyAgent] $reply")
    }

    private fun reply(sender: CommandSender, message: String) {
        val h = holder ?: return
        Bukkit.getScheduler().runTask(h.plugin, Runnable { sender.sendMessage("[WindyAgent] $message") })
    }

    // ── 运行时依赖容器 ──

    class CommandHolder(
        val plugin: JavaPlugin,
        val agent: AgentRouter,
        val playerQa: PlayerQa,
        val platform: BukkitPlatform,
        val sessions: SessionManager,
        val router: AgentCommandRouter,
        val rateLimiter: org.windy.windyagent.safety.RateLimiter?
    )
}
