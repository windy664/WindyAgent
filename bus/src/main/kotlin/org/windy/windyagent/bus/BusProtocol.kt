package org.windy.windyagent.bus

/**
 * 跨服总线消息协议（中心 Velocity ↔ 子服 Bukkit）。
 *
 * 频道约定：
 *   - 请求：windyagent:req:<子服名>   中心发，对应子服收
 *   - 回复：windyagent:reply          子服发，中心收（按 requestId 匹配）
 */

/** 中心 → 子服：请求在指定子服上执行一个动作。 */
data class ToolRequest(
    val requestId: String = "",
    val server: String = "",
    val action: String = "",
    /** 动作参数，JSON 字符串（各 action 自定义结构） */
    val argsJson: String = "{}"
)

/** 子服 → 中心：动作执行结果。 */
data class ToolReply(
    val requestId: String = "",
    val success: Boolean = false,
    val content: String = ""
)

object BusChannels {
    fun request(server: String): String = "windyagent:req:$server"
    const val REPLY: String = "windyagent:reply"
    /** 子服 → 中心 推送能力目录的频道 */
    const val CATALOG: String = "windyagent:catalog"
}
