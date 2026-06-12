package org.windy.windyagent.bus.socket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.windy.windyagent.bus.ToolReply
import org.windy.windyagent.bus.ToolRequest
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * SocketBus 线路帧：长度前缀（4 字节 int）+ UTF-8 JSON。
 *
 * 一条连接上复用 4 种帧：
 *   - REGISTER     子服 → 中枢：声明自己的 server 名 + 可选共享密钥
 *   - REGISTER_ACK 中枢 → 子服：注册结果（鉴权/重名校验）
 *   - REQUEST      中枢 → 子服：派发一个动作（内嵌 [ToolRequest]）
 *   - REPLY        子服 → 中枢：动作结果（内嵌 [ToolReply]）
 *   - CATALOG      子服 → 中枢：主动推送能力目录（catalogJson）
 */
data class Frame(
    val type: String = "",
    val server: String? = null,
    val secret: String? = null,
    val ok: Boolean? = null,
    val error: String? = null,
    val request: ToolRequest? = null,
    val reply: ToolReply? = null,
    val catalogJson: String? = null
)

object FrameType {
    const val REGISTER = "REGISTER"
    const val REGISTER_ACK = "REGISTER_ACK"
    const val REQUEST = "REQUEST"
    const val REPLY = "REPLY"
    const val CATALOG = "CATALOG"
}

/** 帧编解码：长度前缀 + JSON。写按输出流加锁，允许多线程并发回包。 */
object FrameCodec {
    private const val MAX_FRAME = 4 * 1024 * 1024 // 4MB 上限，防御异常长度
    private val mapper: ObjectMapper = ObjectMapper().registerKotlinModule()

    fun write(out: DataOutputStream, frame: Frame) {
        val bytes = mapper.writeValueAsBytes(frame)
        synchronized(out) {
            out.writeInt(bytes.size)
            out.write(bytes)
            out.flush()
        }
    }

    fun read(input: DataInputStream): Frame {
        val len = input.readInt()
        if (len <= 0 || len > MAX_FRAME) throw IOException("非法帧长度: $len")
        val bytes = ByteArray(len)
        input.readFully(bytes)
        return mapper.readValue(bytes, Frame::class.java)
    }
}
