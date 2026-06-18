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
    /** 工具集动态选择后的子集（由 AgentRouter 填充；null=用 platform.tools 全量）。 */
    var selectedTools: List<AgentTool>? = null
) {
    /** 实际使用的工具列表（优先 selectedTools）。 */
    val effectiveTools: List<AgentTool> get() = selectedTools ?: platform.tools
}
