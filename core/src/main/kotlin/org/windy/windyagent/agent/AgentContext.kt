package org.windy.windyagent.agent

import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.platform.Platform
import org.windy.windyagent.safety.TrustLevel

class AgentContext(
    val sessionId: String,
    val userMessage: String,
    val platform: Platform,
    val history: MutableList<LLMMessage> = mutableListOf(),
    /** 触发来源信任级别（决定高危命令处置）。默认最保守。 */
    val trust: TrustLevel = TrustLevel.UNTRUSTED,
    /** 本次请求自动召回的长期记忆文本（由 AgentRouter 填充，agent 拼入系统提示）。 */
    var recalled: String = ""
)
