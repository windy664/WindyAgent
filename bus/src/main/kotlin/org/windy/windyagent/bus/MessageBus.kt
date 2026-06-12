package org.windy.windyagent.bus

import java.util.concurrent.CompletableFuture

/**
 * 跨服总线抽象（中心 Velocity ↔ 子服）。
 *
 * 把传输实现（Redis pub/sub、进程内、将来的自建 Socket 中枢…）藏在接口后，
 * 中心侧的 [org.windy.windyagent.bus] 使用方与 RemoteCommandTool 只依赖此接口，
 * 靠配置 `cross-server.transport` 切换实现，零改动——
 * 与 AgentTool / McpToolAdapter「抽象掉实现、可替换」的取向一致。
 *
 * 约束：本模块按 Java 8 编译（兼容 Spigot 1.12 老服），实现不得用 Java 9+ API。
 */
interface MessageBus : AutoCloseable {

    /** 中心侧：开始接收子服回复（按 requestId 完成对应 future）。可幂等、可为 no-op。 */
    fun startReplyListener()

    /** 中心侧：向指定子服派发动作，返回 future（到点未回则以超时结果完成）。 */
    fun dispatch(server: String, action: String, argsJson: String, timeoutMs: Long): CompletableFuture<ToolReply>

    /** 子服侧：监听本服请求并由 handler 执行，结果自动回中心。 */
    fun listen(server: String, handler: (ToolRequest) -> ToolReply)

    /**
     * 子服 → 中心 主动推送能力目录（不走请求/响应，是单向广播）。
     * 默认空实现：不支持的传输（或不需要的一侧）无需改动。
     */
    fun publishCatalog(catalogJson: String) {}

    /** 中心侧：注册目录接收回调（收到任一子服推来的目录 JSON 即触发）。默认空实现。 */
    fun onCatalog(handler: (String) -> Unit) {}

    override fun close()
}
