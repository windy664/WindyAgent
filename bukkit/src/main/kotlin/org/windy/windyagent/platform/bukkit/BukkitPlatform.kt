package org.windy.windyagent.platform.bukkit

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.platform.Platform

/**
 * Bukkit/Paper 载体（嵌入式 Agent 用）。与 VelocityPlatform 对等：
 * 提供本服本地工具 + 载体上下文 + 回复投递。
 *
 * @param extraTools hub 模式下追加的远端工具（如 RemoteCommandTool 派发到其它子服）。
 */
class BukkitPlatform(
    private val plugin: JavaPlugin,
    actions: BukkitActions,
    extraTools: List<AgentTool> = emptyList()
) : Platform {

    override val name = "bukkit"

    override val tools: List<AgentTool> = listOf(
        BukkitBroadcastTool(actions),
        BukkitOnlinePlayersTool(actions),
        BukkitKickTool(actions),
        BukkitRunCommandTool(actions),
        BukkitBalanceTool(actions)
    ) + extraTools

    override val platformContext =
        "你直接跑在一台 Paper/Spigot 服务器上，可对本服玩家与控制台执行操作。"

    /** 按玩家名投递（跳主线程确保线程安全）；控制台无对应 Player 时由命令入口自行回显。 */
    override fun sendResponse(sessionId: String, message: String) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.getPlayerExact(sessionId)?.sendMessage("[WindyAgent] $message")
        })
    }
}
