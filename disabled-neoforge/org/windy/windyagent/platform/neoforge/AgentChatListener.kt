package org.windy.windyagent.platform.neoforge

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.ServerChatEvent
import org.slf4j.LoggerFactory
import org.windy.windyagent.agent.AgentContext
import org.windy.windyagent.agent.ReActAgent
import org.windy.windyagent.platform.SessionManager
import java.util.concurrent.CompletableFuture

class AgentChatListener(
    private val agent: ReActAgent,
    private val platform: NeoForgePlatform,
    private val sessions: SessionManager,
    trigger: String
) {
    private val logger = LoggerFactory.getLogger(AgentChatListener::class.java)
    private val triggerPrefix = "$trigger "

    @SubscribeEvent
    fun onServerChat(event: ServerChatEvent) {
        val message = event.message.string
        if (!message.startsWith(triggerPrefix)) return

        event.isCanceled = true
        val userInput = message.removePrefix(triggerPrefix).trim()
        if (userInput.isEmpty()) return

        val playerName = event.player.name.string
        platform.sendResponse(playerName, "Processing...")

        CompletableFuture.runAsync {
            runCatching {
                val context = AgentContext(playerName, userInput, platform, sessions.getHistory(playerName))
                val response = agent.run(context)
                sessions.trimHistory(playerName)
                platform.sendResponse(playerName, response.message)
            }.onFailure {
                logger.error("Agent error for player {}", playerName, it)
                platform.sendResponse(playerName, "An error occurred. Please try again.")
            }
        }
    }
}
