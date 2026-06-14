package org.windy.windyagent.bus

import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 进程内总线：[dispatch] 直接调用本 JVM 内经 [listen] 注册的 handler，并完成 future。
 *
 * 用途：**无 Redis 的单实例测试**——在一个 Velocity 实例里注册 stub 子服 handler，
 * 即可跑通「Agent → RemoteCommandTool → 总线 → 子服执行 → 回包」整条中心侧链路，
 * 不需要 Redis、不需要第二个服务端进程。
 *
 * 注意：它**不跨进程**，子服 handler 必须在同一 JVM 注册，因此仅适合开发/测试，
 * 生产请用 [RedisBus]（或将来的自建 Socket 中枢）。
 *
 * 并发：handler 在独立线程池异步执行、各请求互不阻塞，行为贴近真实总线的非阻塞语义。
 * 按 Java 8 编译，超时用调度线程手动实现（不用 Java 9+ 的 orTimeout）。
 */
class InProcessBus : MessageBus {

    private val handlers = ConcurrentHashMap<String, (ToolRequest) -> ToolReply>()

    private val workers = Executors.newCachedThreadPool { r ->
        Thread(r, "windyagent-inproc-worker").apply { isDaemon = true }
    }
    private val timeoutExec = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "windyagent-inproc-timeout").apply { isDaemon = true }
    }

    @Volatile private var catalogHandler: ((String) -> Unit)? = null

    /** 进程内回复直接完成 future，无需独立回复监听。 */
    override fun startReplyListener() { /* no-op */ }

    override fun onCatalog(handler: (String) -> Unit) { catalogHandler = handler }

    /** 进程内：直接同步回调中心已注册的目录处理器。 */
    override fun publishCatalog(catalogJson: String) {
        runCatching { catalogHandler?.invoke(catalogJson) }
    }

    override fun listen(server: String, handler: (ToolRequest) -> ToolReply) {
        handlers[server] = handler
    }

    /** 进程内：已注册 handler 的子服即"在线"（同 JVM，注册即可达）。 */
    override fun onlineServers(): Set<String> = handlers.keys.toSet()

    override fun dispatch(server: String, action: String, argsJson: String, timeoutMs: Long): CompletableFuture<ToolReply> {
        val id = UUID.randomUUID().toString()
        val future = CompletableFuture<ToolReply>()

        val handler = handlers[server]
        if (handler == null) {
            future.complete(ToolReply(id, false, "子服「$server」未注册（InProcessBus 仅识别同 JVM 注册的子服）"))
            return future
        }

        // 到点未回则以超时完成；complete 幂等，正常先回则此处 no-op。
        timeoutExec.schedule({
            future.complete(ToolReply(id, false, "子服「$server」响应超时（${timeoutMs}ms）"))
        }, timeoutMs, TimeUnit.MILLISECONDS)

        workers.submit {
            val reply = runCatching { handler(ToolRequest(id, server, action, argsJson)) }
                .getOrElse { ToolReply(id, false, "执行异常：${it.message}") }
            future.complete(reply.copy(requestId = id))
        }

        return future
    }

    override fun close() {
        runCatching { workers.shutdownNow() }
        runCatching { timeoutExec.shutdownNow() }
    }
}
