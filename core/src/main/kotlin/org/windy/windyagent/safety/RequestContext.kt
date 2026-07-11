package org.windy.windyagent.safety

/**
 * 请求级上下文（ThreadLocal）：把「本次请求的触发信任级别 + 会话 id」从 Agent 入口传到深层工具，
 * 而**不改 AgentTool 接口**。
 *
 * 入口（AgentRouter.run）进入时 [enter]、finally [clear]；工具内 [current]/[sessionId] 读取。
 * trust 默认 UNTRUSTED（最保守）：未设置时也不会误放高危命令。
 *
 * ⚠ 跨线程传播：工具现在在并行线程池执行（AgentLoop），ThreadLocal 不会自动传到工具线程 →
 * 工具线程读到默认 UNTRUSTED，导致「可信来源(超管/控制台)的踢人等高危操作被误判为不可信而拦截」。
 * 故提供 [snapshot]/[restore]：主线程取快照，工具线程 restore 恢复本次请求的信任级别。
 */
object RequestContext {
    private val trust = ThreadLocal<TrustLevel>()
    private val session = ThreadLocal<String>()
    private val unattendedTL = ThreadLocal<Boolean>()
    /** 请求者当前所在子服（玩家 /ai 时由入口填；控制台/QQ/定时=空）。供工具默认目标子服用。 */
    private val serverTL = ThreadLocal<String>()

    /** 请求上下文快照（可跨线程传递，值类型不可变）。 */
    data class Snapshot(val trust: TrustLevel, val session: String, val unattended: Boolean, val server: String)

    fun enter(level: TrustLevel, sessionId: String, unattended: Boolean = false, requesterServer: String = "") {
        trust.set(level)
        session.set(sessionId)
        unattendedTL.set(unattended)
        serverTL.set(requesterServer)
    }

    fun clear() {
        trust.remove()
        session.remove()
        unattendedTL.remove()
        serverTL.remove()
    }

    /** 取当前线程的上下文快照，传给工具线程。 */
    fun snapshot(): Snapshot = Snapshot(current(), sessionId(), unattended(), requesterServer())

    /** 在当前（工具）线程恢复快照——使并行工具也拿到本次请求的信任级别。用完须 [clear]。 */
    fun restore(s: Snapshot) {
        trust.set(s.trust)
        session.set(s.session)
        unattendedTL.set(s.unattended)
        serverTL.set(s.server)
    }

    fun current(): TrustLevel = trust.get() ?: TrustLevel.UNTRUSTED
    fun sessionId(): String = session.get() ?: "unknown"
    /** 无人值守：定时 Agent 任务执行中，高危命令应直接拦截而非入审批闸。 */
    fun unattended(): Boolean = unattendedTL.get() ?: false
    /** 请求者当前所在子服；未知=空串。 */
    fun requesterServer(): String = serverTL.get() ?: ""
}
