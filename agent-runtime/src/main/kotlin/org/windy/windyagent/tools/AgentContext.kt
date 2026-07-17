package org.windy.windyagent.tools

import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.platform.Platform
import org.windy.windyagent.safety.TrustLevel

class AgentContext(
    val sessionId: String,
    var userMessage: String,
    val platform: Platform,
    val history: MutableList<LLMMessage> = mutableListOf(),
    val trust: TrustLevel = TrustLevel.UNTRUSTED,
    val unattended: Boolean = false,
    /**
     * 本次请求者的可读标识（玩家名 / 控制台 / QQ 号等）。空则回退到 [sessionId]。
     * 由载体入口填，供内核在提示里点名"谁在问"，实现"ID 不用报"。
     */
    val requester: String = "",
    /**
     * 请求者当前所在子服（玩家 /ai 时由 Velocity 入口按 currentServer 填；控制台/QQ/定时=空）。
     * 内核据此在提示里告知默认目标子服、并经 [org.windy.windyagent.safety.RequestContext]
     * 供远端工具（run_command_on_server / run_skill_on_server）在用户未言明 server 时兜底，实现"子服不用报"。
     */
    val requesterServer: String = "",
    /** 本次请求自动召回的长期记忆 + 画像文本（由 AgentRouter 填充）。 */
    var recalled: String = "",
    /**
     * 本次请求的「执行过程」回调（每个工具完成时触发）：(工具名, 是否成功, 耗时ms)。
     * 供流式对话把 tools 的工具调用过程实时推给前端展示（仿 Hermes 的 streaming tool output）。
     * null=不关心过程（默认）。per-request，与全局统计用的 onToolCall 分离。
     */
    val onStep: ((String, Boolean, Long) -> Unit)? = null
) {
    /** 实际使用的工具列表。 */
    val effectiveTools: List<AgentTool> get() = platform.tools
}
