package org.windy.windyagent.platform

import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.agent.SystemPrompt

interface Platform {
    val name: String
    val tools: List<AgentTool>

    /**
     * 载体特有的系统提示补充（如「你跑在 Velocity 代理上」）。
     * 通用部分见 [SystemPrompt]，无特殊上下文时返回空串即可。
     */
    val platformContext: String
        get() = ""

    /** 人格文件内容（由插件启动时注入）。 */
    var personality: String

    /** 通用基底 + 载体上下文 + 人格，统一由核心层拼接。 */
    val systemPrompt: String
        get() = SystemPrompt.build(platformContext, personality)

    fun sendResponse(sessionId: String, message: String)
}
