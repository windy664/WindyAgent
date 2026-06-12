package org.windy.windyagent.platform.bukkit

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.agent.Agent
import org.windy.windyagent.agent.AgentContext
import org.windy.windyagent.command.AgentCommandRouter
import org.windy.windyagent.platform.SessionManager
import org.windy.windyagent.safety.TrustLevel

/**
 * 游戏内聊天触发：`<trigger> <消息>`（默认 `!ai ...`）。
 *
 * 命中即取消聊天事件，异步跑 Agent，回复经 [BukkitPlatform.sendResponse] 投递。
 */
class BukkitChatListener(
    private val plugin: JavaPlugin,
    private val agent: Agent,
    private val platform: BukkitPlatform,
    private val sessions: SessionManager,
    private val router: AgentCommandRouter,
    trigger: String
) : Listener {

    private val prefix = "$trigger "

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        val message = event.message
        if (!message.startsWith(prefix)) return

        event.isCancelled = true
        val input = message.removePrefix(prefix).trim()
        if (input.isEmpty()) return

        val playerName = event.player.name
        platform.sendResponse(playerName, "正在处理……")

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            runCatching {
                // 玩家聊天 = 不可信来源
                val meta = router.dispatch(input, playerName, TrustLevel.UNTRUSTED)
                if (meta != null) { platform.sendResponse(playerName, meta); return@Runnable }
                val ctx = AgentContext(playerName, input, platform, sessions.getHistory(playerName), TrustLevel.UNTRUSTED)
                val resp = agent.run(ctx)
                sessions.trimHistory(playerName)
                platform.sendResponse(playerName, resp.message)
            }.onFailure {
                plugin.logger.severe("Agent 处理出错（$playerName）：${it.message}")
                platform.sendResponse(playerName, "处理出错，请稍后重试。")
            }
        })
    }
}
