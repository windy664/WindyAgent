package org.windy.windyagent.platform.bukkit

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.command.AgentCommandRouter
import org.windy.windyagent.knowledge.PlayerQa
import org.windy.windyagent.safety.TrustLevel

/**
 * 游戏内聊天触发：`<trigger> <消息>`（默认 `!ai ...`）。
 *
 * 玩家触发**永远走知识库问答（[PlayerQa]）、不进 Agent、不碰工具**——玩家无法借此踢人/查他人/跑命令。
 * 管理员要用完整 Agent 走 `/ai`（按权限判可信）。命中即取消聊天事件，异步处理。
 */
class BukkitChatListener(
    private val plugin: JavaPlugin,
    private val playerQa: PlayerQa,
    private val platform: BukkitPlatform,
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
                // 玩家聊天 = 不可信来源；先试元命令（approve 等需管理员会被拒）
                val meta = router.dispatch(input, playerName, TrustLevel.UNTRUSTED)
                if (meta != null) { platform.sendResponse(playerName, meta); return@Runnable }
                // 非命令 → 知识库问答（不进 Agent、无工具）
                platform.sendResponse(playerName, playerQa.answer(input))
            }.onFailure {
                plugin.logger.severe("[Chat] 处理出错（$playerName）：${it.message}")
                platform.sendResponse(playerName, "处理出错，请稍后重试。")
            }
        })
    }
}
