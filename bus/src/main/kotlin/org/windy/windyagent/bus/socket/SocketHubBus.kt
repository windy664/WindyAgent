package org.windy.windyagent.bus.socket

import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.bus.ToolReply
import org.windy.windyagent.bus.ToolRequest
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * 中枢侧总线（无 Redis 的跨进程传输）：自建 TCP 服务，子服 dial-home 连进来。
 *
 * 星型拓扑：中枢（Velocity 代理 / 某台 Bukkit）开 [ServerSocket]，各子服用
 * [SocketClientBus] 主动连接并 REGISTER 自己的名字；中枢按名字把 [dispatch] 的
 * 动作写到对应连接，按 requestId 关联回包——与 [org.windy.windyagent.bus.RedisBus]
 * 的 pending future + 超时机制一致，对上层 [MessageBus] 语义无差别。
 *
 * 并发：连接读线程只做「收 REPLY → 完成 future」的轻量活；请求多路复用在持久连接上，
 * 写按连接加锁（见 FrameCodec）。子服数量级的 thread-per-connection 足够，无需 Netty。
 *
 * 按 Java 8 编译（与 bus 模块一致）。
 */
class SocketHubBus(
    private val bindHost: String,
    private val port: Int,
    private val secret: String?
) : MessageBus {

    private val log = Logger.getLogger("WindyAgent-Bus")

    @Volatile private var running = true
    @Volatile private var serverSocket: ServerSocket? = null

    private val connections = ConcurrentHashMap<String, Conn>()
    private val pending = ConcurrentHashMap<String, CompletableFuture<ToolReply>>()
    @Volatile private var catalogHandler: ((String) -> Unit)? = null
    private val timeoutExec = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "windyagent-sockethub-timeout").apply { isDaemon = true }
    }

    private class Conn(val socket: Socket, val out: DataOutputStream, val input: DataInputStream) {
        @Volatile var server: String? = null
    }

    /** 中枢侧：起 accept 循环开始接收子服连接。幂等。 */
    @Synchronized
    override fun startReplyListener() {
        if (serverSocket != null) return
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(bindHost, port))
        serverSocket = ss
        Thread({ acceptLoop(ss) }, "windyagent-sockethub-accept").apply { isDaemon = true }.start()
        log.info("SocketHubBus 监听 $bindHost:$port")
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (running) {
            val socket = try {
                ss.accept()
            } catch (e: Exception) {
                if (running) log.warning("SocketHubBus accept 中断: ${e.message}")
                break
            }
            runCatching {
                socket.tcpNoDelay = true
                val conn = Conn(
                    socket,
                    DataOutputStream(BufferedOutputStream(socket.getOutputStream())),
                    DataInputStream(BufferedInputStream(socket.getInputStream()))
                )
                Thread({ handleConn(conn) }, "windyagent-sockethub-conn").apply { isDaemon = true }.start()
            }.onFailure { runCatching { socket.close() } }
        }
    }

    private fun handleConn(conn: Conn) {
        try {
            val first = FrameCodec.read(conn.input)
            if (first.type != FrameType.REGISTER || first.server.isNullOrBlank()) {
                FrameCodec.write(conn.out, Frame(type = FrameType.REGISTER_ACK, ok = false, error = "首帧必须是 REGISTER"))
                return
            }
            if (!secret.isNullOrBlank() && first.secret != secret) {
                FrameCodec.write(conn.out, Frame(type = FrameType.REGISTER_ACK, ok = false, error = "共享密钥不匹配"))
                log.warning("子服「${first.server}」鉴权失败，拒绝连接")
                return
            }
            conn.server = first.server
            connections[first.server!!] = conn
            FrameCodec.write(conn.out, Frame(type = FrameType.REGISTER_ACK, ok = true))
            log.info("子服「${first.server}」已连接中枢")

            while (running) {
                val frame = FrameCodec.read(conn.input)
                when {
                    frame.type == FrameType.REPLY && frame.reply != null ->
                        pending.remove(frame.reply.requestId)?.complete(frame.reply)
                    frame.type == FrameType.CATALOG && frame.catalogJson != null ->
                        runCatching { catalogHandler?.invoke(frame.catalogJson) }
                            .onFailure { log.warning("处理子服「${conn.server}」目录失败: ${it.message}") }
                }
            }
        } catch (e: Exception) {
            if (running) log.fine("子服「${conn.server}」连接断开: ${e.message}")
        } finally {
            conn.server?.let { connections.remove(it, conn) }
            runCatching { conn.socket.close() }
        }
    }

    override fun dispatch(server: String, action: String, argsJson: String, timeoutMs: Long): CompletableFuture<ToolReply> {
        val id = UUID.randomUUID().toString()
        val future = CompletableFuture<ToolReply>()

        val conn = connections[server]
        if (conn == null) {
            future.complete(ToolReply(id, false, "子服「$server」未连接到中枢"))
            return future
        }

        pending[id] = future
        timeoutExec.schedule({
            pending.remove(id)?.complete(ToolReply(id, false, "子服「$server」响应超时（${timeoutMs}ms）"))
        }, timeoutMs, TimeUnit.MILLISECONDS)

        runCatching {
            FrameCodec.write(conn.out, Frame(type = FrameType.REQUEST, request = ToolRequest(id, server, action, argsJson)))
        }.onFailure {
            pending.remove(id)?.complete(ToolReply(id, false, "下发子服请求失败：${it.message}"))
        }
        return future
    }

    /** 中枢自身能力走本地工具、不经总线，故不支持 listen。 */
    override fun listen(server: String, handler: (ToolRequest) -> ToolReply) {
        throw UnsupportedOperationException("SocketHubBus 是中枢侧；子服侧请用 SocketClientBus")
    }

    /** 中枢侧：注册目录接收回调（各子服 CATALOG 帧到达时触发）。 */
    override fun onCatalog(handler: (String) -> Unit) {
        catalogHandler = handler
    }

    /** 当前真实在线 = 此刻持有活动连接的子服（REGISTER 时入、断开即移除）。权威在线来源。 */
    override fun onlineServers(): Set<String> = connections.keys.toSet()

    override fun close() {
        running = false
        runCatching { serverSocket?.close() }
        connections.values.forEach { runCatching { it.socket.close() } }
        connections.clear()
        runCatching { timeoutExec.shutdownNow() }
    }
}
