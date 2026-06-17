package org.windy.windyagent.platform.velocity

import com.velocitypowered.api.command.RawCommand
import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import org.slf4j.Logger
import org.windy.windyagent.Messages
import org.windy.windyagent.agent.Agent
import org.windy.windyagent.agent.AgentContext
import org.windy.windyagent.command.AgentCommandRouter
import org.windy.windyagent.knowledge.PlayerQa
import org.windy.windyagent.platform.SessionManager
import org.windy.windyagent.safety.TrustLevel
import java.util.concurrent.CompletableFuture

/**
 * `/ai <message>` 命令 —— 同时支持控制台 (ConsoleCommandSource) 和玩家。
 *
 * 与 [VelocityChatListener] 不同，回复直接发回 [RawCommand.Invocation.source]，
 * 而不是靠 [VelocityPlatform.sendResponse] 按玩家名查在线玩家，
 * 因此控制台（没有对应 Player）也能收到结果。
 */
class AgentCommand(
    private val agent: Agent,
    private val playerQa: PlayerQa,
    private val platform: VelocityPlatform,
    private val sessions: SessionManager,
    private val router: AgentCommandRouter,
    private val logger: Logger
) : RawCommand {

    override fun execute(invocation: RawCommand.Invocation) {
        val source = invocation.source()
        val input = invocation.arguments().trim()
        if (input.isEmpty()) {
            source.sendMessage(Component.text(Messages.t("cmd.usage")))
            return
        }

        val sessionId = if (source is Player) source.username else "console"
        val trust = if (source.hasPermission("windyagent.admin")) TrustLevel.TRUSTED else TrustLevel.UNTRUSTED
        source.sendMessage(Component.text(Messages.t("cmd.processing")))

        CompletableFuture.runAsync {
            runCatching {
                // 先试元命令（help/clear/history/status/approve…）；命中直接回，未命中才走对话
                val meta = router.dispatch(input, sessionId, trust)
                if (meta != null) {
                    source.sendMessage(Component.text("[WindyAgent] $meta"))
                    return@runAsync
                }
                // 可信(管理员/控制台) → 完整 Agent；普通玩家 → 只答疑的知识库问答（不碰工具）
                val reply = if (trust == TrustLevel.TRUSTED) {
                    val response = agent.run(AgentContext(sessionId, input, platform, sessions.getHistory(sessionId), trust))
                    sessions.trimHistory(sessionId); response.message
                } else playerQa.answer(input)
                source.sendMessage(Component.text("[WindyAgent] $reply"))
            }.onFailure {
                logger.error("Agent error for {}", sessionId, it)
                source.sendMessage(Component.text(Messages.t("cmd.error")))
            }
        }
    }

    override fun hasPermission(invocation: RawCommand.Invocation): Boolean = true
}
