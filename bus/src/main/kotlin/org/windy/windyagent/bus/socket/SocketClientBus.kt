package org.windy.windyagent.bus.socket

import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.bus.ToolReply
import org.windy.windyagent.bus.ToolRequest
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.logging.Logger

/**
 * 子服侧总线：主动连接中枢 [SocketHubBus]，注册本服名，接收并执行中枢派发的动作。
 *
 * 与 [org.windy.windyagent.bus.RedisBus] 的子服用法对等：[listen] 注册 handler 后，
 * 独立守护线程负责连接、注册、读请求；断线 2s 重连。
 * handler 执行下放到线程池，读循环不被阻塞——多个并发请求可并行处理（回包写按流加锁，安全）。
 *
 * 按 Java 8 编译。
 */
class SocketClientBus(
    private val hubHost: String,
    private val port: Int,
    private val secret: String?
) : MessageBus {

    private val log = Logger.getLogger("WindyAgent-Bus")

    @Volatile private var running = true
    @Volatile private var serverName: String? = null
    @Volatile private var handler: ((ToolRequest) -> ToolReply)? = null

    // 当前活动连接的输出流 + 最近一次目录：用于主动推目录、并在重连后自动重推
    @Volatile private var activeOut: DataOutputStream? = null
    @Volatile private var lastCatalogJson: String? = null

    private val workers = Executors.newCachedThreadPool { r ->
        Thread(r, "windyagent-socketclient-worker").apply { isDaemon = true }
    }

    override fun listen(server: String, handler: (ToolRequest) -> ToolReply) {
        this.serverName = server
        this.handler = handler
        Thread({ connectLoop() }, "windyagent-socketclient").apply { isDaemon = true }.start()
    }

    private fun connectLoop() {
        while (running) {
            try {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(hubHost, port), 5000)
                    val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                    val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

                    FrameCodec.write(out, Frame(type = FrameType.REGISTER, server = serverName, secret = secret))
                    val ack = FrameCodec.read(input)
                    if (ack.type != FrameType.REGISTER_ACK || ack.ok != true) {
                        log.warning("中枢拒绝注册（${ack.error}），5s 后重试")
                        runCatching { Thread.sleep(5000) }
                        return@use
                    }
                    log.info("已连接中枢 $hubHost:$port，注册为「$serverName」")
                    activeOut = out
                    // 重连自愈：有缓存目录则立即重推（中枢可能刚重启、丢了目录）
                    lastCatalogJson?.let {
                        runCatching { FrameCodec.write(out, Frame(type = FrameType.CATALOG, server = serverName, catalogJson = it)) }
                        log.info("连上后已重推能力目录")
                    }

                    while (running) {
                        val frame = FrameCodec.read(input)
                        if (frame.type == FrameType.REQUEST && frame.request != null) {
                            val req = frame.request
                            workers.submit {
                                val reply = runCatching { handler?.invoke(req) ?: ToolReply(req.requestId, false, "无 handler") }
                                    .getOrElse { ToolReply(req.requestId, false, "执行异常：${it.message}") }
                                runCatching { FrameCodec.write(out, Frame(type = FrameType.REPLY, reply = reply)) }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (running) {
                    log.warning("与中枢连接中断，2s 后重连：${e.message}")
                    runCatching { Thread.sleep(2000) }
                }
            } finally {
                activeOut = null
            }
        }
    }

    /** 子服侧：推送能力目录到中枢；缓存以便重连后自动重推。当前未连上则仅缓存，待连上再发。 */
    override fun publishCatalog(catalogJson: String) {
        lastCatalogJson = catalogJson
        val out = activeOut
        if (out != null) {
            runCatching { FrameCodec.write(out, Frame(type = FrameType.CATALOG, server = serverName, catalogJson = catalogJson)) }
            log.info("能力目录已推送中枢")
        } else {
            log.info("尚未连上中枢，能力目录已缓存，待连上自动重推")
        }
    }

    /** 子服侧：推送日志异常到中枢（fire-and-forget，不缓存）。 */
    override fun publishError(errorJson: String) {
        val out = activeOut
        if (out != null) {
            runCatching { FrameCodec.write(out, Frame(type = FrameType.ERROR, server = serverName, errorJson = errorJson)) }
        }
        // 未连上则丢弃（日志异常是即时告警，不值得缓存重推）
    }

    /** 子服侧不发起 dispatch。 */
    override fun dispatch(server: String, action: String, argsJson: String, timeoutMs: Long): CompletableFuture<ToolReply> =
        CompletableFuture.completedFuture(ToolReply("", false, "SocketClientBus 是子服侧，不支持 dispatch"))

    /** 子服侧无独立回复监听（回包随读循环即时发出）。 */
    override fun startReplyListener() { /* no-op */ }

    override fun close() {
        running = false
        runCatching { workers.shutdownNow() }
    }
}
