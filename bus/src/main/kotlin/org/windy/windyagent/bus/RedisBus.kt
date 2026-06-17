package org.windy.windyagent.bus

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * 基于 Redis pub/sub 的跨服请求/响应总线。
 *
 * 中心侧用法：[startReplyListener] 一次，然后 [dispatch] 派发动作（返回带超时的 future）。
 * 子服侧用法：[listen] 监听本服请求频道，由 handler 执行并自动回包。
 *
 * 不依赖 slf4j（Spigot 1.12 无 slf4j 实现），内部用 JUL 记录告警。
 */
class RedisBus(
    host: String,
    port: Int,
    password: String?
) : MessageBus {

    private val log = Logger.getLogger("WindyAgent-Bus")
    private val mapper: ObjectMapper = ObjectMapper().registerKotlinModule()

    private val pool: JedisPool = if (password.isNullOrBlank()) {
        JedisPool(JedisPoolConfig(), host, port, 2000)
    } else {
        JedisPool(JedisPoolConfig(), host, port, 2000, password)
    }

    @Volatile private var running = true

    // ---- 中心侧：待回复的请求 ----
    private val pending = ConcurrentHashMap<String, CompletableFuture<ToolReply>>()
    private val timeoutExec = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "windyagent-bus-timeout").apply { isDaemon = true }
    }
    @Volatile private var replyListenerStarted = false

    /** 中心侧：订阅回复频道，按 requestId 完成对应 future。幂等。 */
    @Synchronized
    override fun startReplyListener() {
        if (replyListenerStarted) return
        replyListenerStarted = true
        startSubscriber(BusChannels.REPLY) { message ->
            val reply = runCatching { mapper.readValue<ToolReply>(message) }.getOrNull() ?: return@startSubscriber
            pending.remove(reply.requestId)?.complete(reply)
        }
    }

    /** 中心侧：向指定子服派发动作，返回 future（到点未回则以超时结果完成）。 */
    override fun dispatch(server: String, action: String, argsJson: String, timeoutMs: Long): CompletableFuture<ToolReply> {
        val id = UUID.randomUUID().toString()
        val future = CompletableFuture<ToolReply>()
        pending[id] = future
        timeoutExec.schedule({
            pending.remove(id)?.complete(ToolReply(id, false, "子服「$server」响应超时（${timeoutMs}ms）"))
        }, timeoutMs, TimeUnit.MILLISECONDS)
        val req = ToolRequest(id, server, action, argsJson)
        publish(BusChannels.request(server), runCatching { mapper.writeValueAsString(req) }.getOrElse {
            pending.remove(id)?.complete(ToolReply(id, false, "请求序列化失败：${it.message}"))
            return future
        })
        return future
    }

    /** 子服侧：监听本服请求频道，交给 handler 执行并把结果发回回复频道。 */
    override fun listen(server: String, handler: (ToolRequest) -> ToolReply) {
        startSubscriber(BusChannels.request(server)) { message ->
            val req = runCatching { mapper.readValue<ToolRequest>(message) }.getOrNull() ?: return@startSubscriber
            val reply = runCatching { handler(req) }
                .getOrElse { ToolReply(req.requestId, false, "执行异常：${it.message}") }
            publish(BusChannels.REPLY, runCatching { mapper.writeValueAsString(reply) }.getOrElse { return@startSubscriber })
        }
    }

    /** 子服侧：把目录发到 catalog 频道（pub/sub 不持久，中心需先订阅；中心重启后子服下次推送才补上）。 */
    override fun publishCatalog(catalogJson: String) {
        publish(BusChannels.CATALOG, catalogJson)
    }

    /** 中心侧：订阅 catalog 频道，收到任一子服目录即回调。 */
    override fun onCatalog(handler: (String) -> Unit) {
        startSubscriber(BusChannels.CATALOG) { message -> runCatching { handler(message) } }
    }

    /** 子服侧：把日志异常发到 error 频道。 */
    override fun publishError(errorJson: String) {
        publish(BusChannels.ERROR, errorJson)
    }

    /** 中心侧：订阅 error 频道，收到任一子服异常即回调。 */
    override fun onError(handler: (String) -> Unit) {
        startSubscriber(BusChannels.ERROR) { message -> runCatching { handler(message) } }
    }

    private fun publish(channel: String, message: String) {
        runCatching { pool.resource.use { it.publish(channel, message) } }
            .onFailure { log.warning("Redis publish 失败($channel): ${it.message}") }
    }

    /** 在独立守护线程上阻塞订阅，断线 2s 后自动重连。 */
    private fun startSubscriber(channel: String, onMessage: (String) -> Unit) {
        val thread = Thread({
            while (running) {
                try {
                    pool.resource.use { jedis ->
                        jedis.subscribe(object : JedisPubSub() {
                            override fun onMessage(ch: String, msg: String) = onMessage(msg)
                        }, channel)
                    }
                } catch (e: Exception) {
                    if (running) {
                        log.warning("Redis 订阅中断($channel)，2s 后重连: ${e.message}")
                        runCatching { Thread.sleep(2000) }
                    }
                }
            }
        }, "windyagent-bus-sub-$channel")
        thread.isDaemon = true
        thread.start()
    }

    override fun close() {
        running = false
        runCatching { timeoutExec.shutdownNow() }
        runCatching { pool.close() }
    }
}
