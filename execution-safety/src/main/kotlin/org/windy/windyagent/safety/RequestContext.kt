package org.windy.windyagent.safety

/**
 * Request-scoped context. Tools may execute on worker threads, so callers can
 * pass [Snapshot] across threads and restore it before running the tool body.
 */
object RequestContext {
    private val trust = ThreadLocal<TrustLevel>()
    private val session = ThreadLocal<String>()
    private val unattendedTL = ThreadLocal<Boolean>()
    private val serverTL = ThreadLocal<String>()

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

    fun snapshot(): Snapshot = Snapshot(current(), sessionId(), unattended(), requesterServer())

    fun restore(s: Snapshot) {
        trust.set(s.trust)
        session.set(s.session)
        unattendedTL.set(s.unattended)
        serverTL.set(s.server)
    }

    fun current(): TrustLevel = trust.get() ?: TrustLevel.UNTRUSTED
    fun sessionId(): String = session.get() ?: "unknown"
    fun unattended(): Boolean = unattendedTL.get() ?: false
    fun requesterServer(): String = serverTL.get() ?: ""
}
