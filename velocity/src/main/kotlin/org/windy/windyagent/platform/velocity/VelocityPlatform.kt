package org.windy.windyagent.platform.velocity

import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.platform.Platform
import org.windy.windyagent.platform.velocity.tools.BroadcastTool
import org.windy.windyagent.platform.velocity.tools.GetOnlinePlayersTool
import org.windy.windyagent.platform.velocity.tools.GetServerInfoTool
import org.windy.windyagent.platform.velocity.tools.KickPlayerTool

class VelocityPlatform(
    private val server: ProxyServer,
    // 跨服等远端能力包装成的工具（如 RemoteCommandTool）；未启用跨服时为空
    extraTools: List<AgentTool> = emptyList()
) : Platform {
    override val name = "velocity"
    override val tools: List<AgentTool> = listOf(
        BroadcastTool(server),
        GetOnlinePlayersTool(server),
        GetServerInfoTool(server),
        KickPlayerTool(server)
    ) + extraTools
    // 仅声明 Velocity 载体特有的上下文；通用提示见 core 层 SystemPrompt
    override val platformContext = """
        你运行在一个 Velocity 代理端上，它前置了一个或多个后端 Minecraft 子服。
        管理员通过游戏内聊天或服务器控制台（/ai 命令）与你交互。
        需要时，使用提供的工具查询代理端和玩家状态。
    """.trimIndent()

    override fun sendResponse(sessionId: String, message: String) {
        server.getPlayer(sessionId).ifPresent { p ->
            p.sendMessage(Component.text("[WindyAgent] $message"))
        }
    }
}
