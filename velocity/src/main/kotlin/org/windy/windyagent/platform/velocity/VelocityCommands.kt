package org.windy.windyagent.platform.velocity

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
import org.windy.windyagent.platform.velocity.VelocityPlatform
import org.windy.windyagent.safety.TrustLevel
import java.util.concurrent.CompletableFuture

/**
 * TabooLib 注解式命令 —— Velocity 平台。
 *
 * `/ai <消息>`：控制台 + 玩家通用。
 * `/ai approve|deny|pending`：审批命令（admin only）。
 *
 * 运行时依赖由 [WindyAgentVelocityPlugin] 启动后注入 [holder]。
 */
@PlatformSide(Platform.VELOCITY)
@CommandHeader("ai", description = "向 WindyAgent 提问或下达指令")
object VelocityCommands {

    var holder: CommandHolder? = null

    @CommandBody(permission = "windyagent.admin", optional = true)
    val approve = subCommand {
        dynamic("审批单号") {
            execute<ProxyCommandSender> { sender, _, arg ->
                handleApproval(sender, "approve", arg)
            }
        }
    }

    @CommandBody(permission = "windyagent.admin", optional = true)
    val deny = subCommand {
        dynamic("审批单号") {
            execute<ProxyCommandSender> { sender, _, arg ->
                handleApproval(sender, "deny", arg)
            }
        }
    }

    @CommandBody(permission = "windyagent.admin", optional = true)
    val pending = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            handleApproval(sender, "pending", "")
        }
    }

    @CommandBody
    val help = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
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
        execute<ProxyCommandSender> { sender, _, arg ->
            handleChat(sender, arg)
        }
    }

    // ── 业务逻辑 ──

    private fun handleChat(sender: ProxyCommandSender, input: String) {
        val h = holder ?: run { sender.sendMessage("§cWindyAgent 尚未就绪"); return }
        if (input.isBlank()) {
            sender.sendMessage(Messages.t("cmd.usage"))
            return
        }

        val sessionId = sender.name
        val requesterServer = ""

        if (h.rateLimiter != null && !h.rateLimiter.tryAcquire(sessionId)) {
            sender.sendMessage(Messages.t("cmd.rate_limited"))
            return
        }

        val trust = if (sender.hasPermission("windyagent.admin")) TrustLevel.TRUSTED else TrustLevel.UNTRUSTED
        sender.sendMessage(Messages.t("cmd.processing"))

        CompletableFuture.runAsync {
            runCatching {
                val meta = h.router.dispatch(input, sessionId, trust)
                if (meta != null) {
                    sender.sendMessage("[WindyAgent] $meta")
                    return@runAsync
                }
                val reply = if (trust == TrustLevel.TRUSTED) {
                    val resp = h.agent.run(AgentContext(sessionId, input, h.platform, h.sessions.getHistory(sessionId), trust, requester = sessionId, requesterServer = requesterServer))
                    h.sessions.trimHistory(sessionId); resp.message
                } else h.playerQa.answer(input)
                sender.sendMessage("[WindyAgent] $reply")
            }.onFailure {
                h.logger.error("[Agent] 会话 {} 执行出错", sessionId, it)
                sender.sendMessage(Messages.t("cmd.error"))
            }
        }
    }

    private fun handleApproval(sender: ProxyCommandSender, action: String, id: String) {
        val h = holder ?: run { sender.sendMessage("§cWindyAgent 尚未就绪"); return }
        val trust = if (sender.hasPermission("windyagent.admin")) TrustLevel.TRUSTED else TrustLevel.UNTRUSTED
        val session = sender.name
        val input = "$action $id".trim()
        val reply = h.router.dispatch(input, session, trust) ?: Messages.t("router.unknown_cmd")
        sender.sendMessage("[WindyAgent] $reply")
    }

    // ── 运行时依赖容器 ──

    class CommandHolder(
        val agent: AgentRouter,
        val playerQa: PlayerQa,
        val platform: VelocityPlatform,
        val sessions: SessionManager,
        val router: AgentCommandRouter,
        val rateLimiter: org.windy.windyagent.safety.RateLimiter?,
        val logger: org.slf4j.Logger
    )
}
