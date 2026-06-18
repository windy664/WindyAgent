package org.windy.windyagent.web.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import org.windy.windyagent.Messages
import org.windy.windyagent.safety.PendingApprovals
import org.windy.windyagent.web.ApiHandler
import org.windy.windyagent.web.DashboardServer

class ApprovalHandler(
    private val server: DashboardServer,
    private val pending: PendingApprovals?
) : ApiHandler {

    override fun canHandle(path: String): Boolean = path.startsWith("/api/approvals")

    override fun handle(ex: HttpExchange, path: String, query: Map<String, String>, mapper: ObjectMapper) {
        when (path) {
            "/api/approvals" -> approvalsApi(ex, mapper)
            "/api/approvals/approve" -> { val p = pending; val id = query["id"]; if (p == null || id.isNullOrBlank()) server.json(ex, 400, """{"error":"bad request"}""") else server.json(ex, 200, mapper.createObjectNode().put("result", p.approve(id) ?: Messages.t("cmd.approve.not_found", id)).toString()) }
            "/api/approvals/deny" -> { val p = pending; val id = query["id"]; if (p == null || id.isNullOrBlank()) server.json(ex, 400, """{"error":"bad request"}""") else server.json(ex, 200, mapper.createObjectNode().put("desc", p.deny(id) ?: Messages.t("cmd.deny.not_found", id)).toString()) }
            else -> server.json(ex, 404, """{"error":"not found"}""")
        }
    }

    private fun approvalsApi(ex: HttpExchange, mapper: ObjectMapper) {
        val p = pending ?: return server.json(ex, 200, """{"pending":[],"history":[],"ttlMs":0}""")
        val now = System.currentTimeMillis()
        val root = mapper.createObjectNode()
        val pend = root.putArray("pending")
        p.items().forEach { pend.addObject().put("id", it.id).put("desc", it.desc).put("at", it.at).put("remainMs", (p.ttl() - (now - it.at)).coerceAtLeast(0)) }
        val hist = root.putArray("history")
        p.historyItems().forEach { h -> hist.addObject().put("id", h.id).put("desc", h.desc).put("decision", h.decision).put("result", h.result).put("at", h.at) }
        root.put("ttlMs", p.ttl())
        server.json(ex, 200, root.toString())
    }
}
