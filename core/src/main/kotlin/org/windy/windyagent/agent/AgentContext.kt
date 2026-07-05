package org.windy.windyagent.agent

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
    /** 本次请求自动召回的长期记忆 + 画像文本（由 AgentRouter 填充）。 */
    var recalled: String = "",
    /**
     * 本次请求的「执行过程」回调（每个工具完成时触发）：(工具名, 是否成功, 耗时ms)。
     * 供流式对话把 agent 的工具调用过程实时推给前端展示（仿 Hermes 的 streaming tool output）。
     * null=不关心过程（默认）。per-request，与全局统计用的 onToolCall 分离。
     */
    val onStep: ((String, Boolean, Long) -> Unit)? = null
) {
    /** 实际使用的工具列表。 */
    val effectiveTools: List<AgentTool> get() = platform.tools
}
