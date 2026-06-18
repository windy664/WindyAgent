package org.windy.windyagent.platform.bukkit.profile

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.windy.windyagent.profile.ProfileDataRegistry

/**
 * 玩家加入/退出事件 → 分发给所有画像数据源预热/清理缓存。
 *
 * MONITOR 优先级：在其它插件处理完之后再读数据，确保 CMI 已完成自己的初始化。
 */
class PlayerProfileListener(private val registry: ProfileDataRegistry) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(e: PlayerJoinEvent) {
        registry.onJoin(e.player.name)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(e: PlayerQuitEvent) {
        registry.onQuit(e.player.name)
    }
}
