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

    /**
     * 子服 → 中心 主动推送日志异常（单向广播，与 publishCatalog 同模式）。
     * 默认空实现：不支持的传输无需改动。
     */
    fun publishError(errorJson: String) {}

    /** 中心侧：注册日志异常接收回调。默认空实现。 */
    fun onError(handler: (String) -> Unit) {}

    /**
     * 中心侧：**当前真实在线**（此刻已连接到中枢）的子服名集——权威的"在线"来源。
     * 与能力注册表（持久化目录=曾见过、含离线）区分开：选子服对话 / 校验可派发要用这个，
     * 否则会派到离线子服白等超时（"假在线"）。
     *
     * 默认 `null` = 该传输无法判定在线（如无心跳的 Redis）→ 调用方应退回注册表的"曾见过"集（旧行为，不退化）。
     * 能判定的传输（自建 Socket 中枢 / 进程内）返回非空集（可能为空集=确无子服在线）。
     */
    fun onlineServers(): Set<String>? = null

    override fun close()
}
