package org.windy.windyagent.safety

/**
 * 请求级上下文（ThreadLocal）：把「本次请求的触发信任级别 + 会话 id」从 Agent 入口传到深层工具，
 * 而**不改 AgentTool 接口**。Agent 单次 run 在单线程内同步执行，故 ThreadLocal 可靠。
 *
 * 入口（AgentRouter.run）进入时 [enter]、finally [clear]；工具内 [current]/[sessionId] 读取。
 * trust 默认 UNTRUSTED（最保守）：未设置时也不会误放高危命令。
 */
object RequestContext {
    private val trust = ThreadLocal<TrustLevel>()
    private val session = ThreadLocal<String>()

    fun enter(level: TrustLevel, sessionId: String) {
        trust.set(level)
        session.set(sessionId)
    }

    fun clear() {
        trust.remove()
        session.remove()
    }

    fun current(): TrustLevel = trust.get() ?: TrustLevel.UNTRUSTED
    fun sessionId(): String = session.get() ?: "unknown"
}
