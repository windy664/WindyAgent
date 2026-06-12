package org.windy.windyagent.agent

/**
 * 统一的 Agent 抽象。
 *
 * 载体（Velocity / 未来 QQ、Web）与 [AgentRouter] 只依赖此接口，
 * 不绑定 [ReActAgent] / [PlanExecuteAgent] 等具体实现，便于路由与替换。
 */
interface Agent {
    /** 实现标识，用于日志与路由决策记录。 */
    val name: String

    fun run(context: AgentContext): AgentResponse
}
