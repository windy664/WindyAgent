package org.windy.windyagent.platform.velocity

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerChatEvent
import org.slf4j.Logger
import org.windy.windyagent.agent.Agent
import org.windy.windyagent.agent.AgentContext
import org.windy.windyagent.platform.SessionManager
import java.util.concurrent.CompletableFuture

class VelocityChatListener(
    private val agent: Agent,
    private val platform: VelocityPlatform,
    private val sessions: SessionManager,
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
                val context = AgentContext(playerName, userInput, platform, sessions.getHistory(playerName))
                val response = agent.run(context)
                sessions.trimHistory(playerName)
                platform.sendResponse(playerName, response.message)
            }.onFailure {
                logger.error("Agent error for player {}", playerName, it)
                platform.sendResponse(playerName, "处理出错，请稍后重试。")
            }
        }
    }
}
