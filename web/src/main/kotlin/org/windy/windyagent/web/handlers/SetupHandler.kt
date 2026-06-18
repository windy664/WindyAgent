package org.windy.windyagent.web.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import org.windy.windyagent.AgentConfig
import org.windy.windyagent.web.ApiHandler
import org.windy.windyagent.web.DashboardServer

/**
 * 首启向导后端：未配置 LLM 时，前端用它查状态 + 写回 provider/key/model。
 *
 * - GET  /api/setup/state  → { configured, provider, model, apiBaseUrl, fastModel }
 * - POST /api/setup        → 写回 llm 段，返回 { ok:true, restart:true }（需重启代理生效）
 *
 * 仍走 [DashboardServer] 的 token 鉴权（绑 127.0.0.1 + 控制台已打印 token）。
 */
class SetupHandler(
    private val server: DashboardServer,
    private val cfg: AgentConfig
) : ApiHandler {

    override fun canHandle(path: String): Boolean = path == "/api/setup" || path == "/api/setup/state"

    override fun handle(ex: HttpExchange, path: String, query: Map<String, String>, mapper: ObjectMapper) {
        when (path) {
            "/api/setup/state" -> state(ex, mapper)
            "/api/setup" -> if (ex.requestMethod == "POST") save(ex, mapper) else server.json(ex, 405, """{"error":"POST only"}""")
        }
    }

    private fun state(ex: HttpExchange, mapper: ObjectMapper) {
        val node = mapper.createObjectNode()
        node.put("configured", !cfg.needsLlmSetup())
        node.put("provider", cfg.provider())
        node.put("model", cfg.model())
        node.put("apiBaseUrl", cfg.apiBaseUrl())
        node.put("fastModel", cfg.fastModel())
        node.put("ollamaUrl", cfg.ollamaUrl())
        server.json(ex, 200, node.toString())
    }

    private fun save(ex: HttpExchange, mapper: ObjectMapper) {
        val body = runCatching { mapper.readTree(server.body(ex)) }.getOrNull()
            ?: return server.json(ex, 400, """{"error":"bad json"}""")
        val provider = body["provider"]?.asText()?.trim()?.lowercase().orEmpty().ifBlank { "claude" }
        val apiKey = body["apiKey"]?.asText()?.trim().orEmpty()
        val model = body["model"]?.asText()?.trim().orEmpty()
        val apiBaseUrl = body["apiBaseUrl"]?.asText()?.trim().orEmpty()
        val fastModel = body["fastModel"]?.asText()?.trim().orEmpty()

        if (provider !in setOf("claude", "openai", "ollama"))
            return server.json(ex, 400, """{"error":"unknown provider"}""")
        if (provider != "ollama" && apiKey.isBlank())
            return server.json(ex, 400, """{"error":"api-key required for $provider"}""")
        if (model.isBlank())
            return server.json(ex, 400, """{"error":"model required"}""")

        runCatching { cfg.applyLlmSetup(provider, apiBaseUrl, apiKey, model, fastModel) }
            .onFailure { return server.json(ex, 500, """{"error":${server.jstr(it.message ?: "write failed")}}""") }
        server.json(ex, 200, """{"ok":true,"restart":true}""")
    }
}
