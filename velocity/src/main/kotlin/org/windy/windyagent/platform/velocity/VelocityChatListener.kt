package org.windy.windyagent.platform.velocity

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerChatEvent
import org.slf4j.Logger
import org.windy.windyagent.command.AgentCommandRouter
import org.windy.windyagent.knowledge.PlayerQa
import org.windy.windyagent.safety.TrustLevel
import java.util.concurrent.CompletableFuture

/**
 * 游戏内 `!ai <消息>`：玩家触发，**永远走知识库问答（[PlayerQa]）、不进 Agent、不碰工具**。
 * 玩家无法借此踢人/查他人数据/跑命令。管理员要用完整 Agent 请走 `/ai`（按权限判可信）。
 */
class VelocityChatListener(
    private val playerQa: PlayerQa,
    private val platform: VelocityPlatform,
    private val router: AgentCommandRouter,
    private val logger: Logger,
    trigger: String
) {
    private val triggerPrefix = "$trigger "

    @Subscribe
    fun onPlayerChat(event: PlayerChatEvent) {
        val message = event.message
        if (!message.startsWith(triggerPrefix)) return

        event.result = PlayerChatEvent.ChatResult.denied()
        val userInput = message.removePrefix(triggerPrefix).trim()
        if (userInput.isEmpty()) return

        val playerName = event.player.username
        platform.sendResponse(playerName, "正在处理……")

        CompletableFuture.runAsync {
            runCatching {
                // 玩家聊天 = 不可信来源；先试元命令（approve 等需管理员会被拒）
                val meta = router.dispatch(userInput, playerName, TrustLevel.UNTRUSTED)
                if (meta != null) {
                    platform.sendResponse(playerName, meta)
                    return@runAsync
                }
                // 非命令 → 知识库问答（不进 Agent、无工具）
                platform.sendResponse(playerName, playerQa.answer(userInput))
            }.onFailure {
                logger.error("Agent error for player {}", playerName, it)
                platform.sendResponse(playerName, "处理出错，请稍后重试。")
            }
        }
    }
}
