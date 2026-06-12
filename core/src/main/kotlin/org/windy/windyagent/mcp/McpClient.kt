package org.windy.windyagent.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** 一个 MCP server 的配置。headers 可选（如鉴权头）。 */
data class McpServerConfig(val name: String, val url: String, val headers: Map<String, String> = emptyMap())

/** MCP server 暴露的一个工具定义。 */
data class McpToolDef(val name: String, val description: String, val inputSchema: String)

/**
 * 极简 MCP 客户端：JSON-RPC 2.0 over Streamable HTTP（initialize / tools/list / tools/call）。
 *
 * 复用 core 已有的 okhttp + jackson，**不引入新依赖、不拉 MCP SDK**——
 * 这是「接第三方工具」的标准化通道；与自家跨服总线（[org.windy.windyagent.bus]）正交并存。
 * 响应兼容纯 JSON 与 SSE（text/event-stream）两种形态。
 */
class McpClient(
    private val endpoint: String,
    private val headers: Map<String, String> = emptyMap()
) {
    private val log = LoggerFactory.getLogger(McpClient::class.java)
    private val http = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val mapper = ObjectMapper()
    private val ids = AtomicInteger(0)

    @Volatile private var sessionId: String? = null

    /** 握手：initialize + notifications/initialized，并记录会话 id（若 server 返回）。 */
    fun initialize() {
        val params = mapper.createObjectNode().apply {
            put("protocolVersion", "2025-06-18")
            set<ObjectNode>("capabilities", mapper.createObjectNode())
            set<ObjectNode>("clientInfo", mapper.createObjectNode().put("name", "WindyAgent").put("version", "1.0"))
        }
        post(rpc("initialize", params)) // post 内部会捕获并记录 sessionId
        notify("notifications/initialized")
    }

    fun listTools(): List<McpToolDef> {
        val node = post(rpc("tools/list", null))
        val tools = node["result"]?.get("tools") ?: return emptyList()
        return tools.mapNotNull { t ->
            val name = t["name"]?.asText()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val desc = t["description"]?.asText() ?: ""
            val schema = t["inputSchema"]?.toString() ?: """{"type":"object"}"""
            McpToolDef(name, desc, schema)
        }
    }

    /** 调用一个工具；argumentsJson 是符合该工具 inputSchema 的 JSON 对象字符串。 */
    fun callTool(name: String, argumentsJson: String): String {
        val params = mapper.createObjectNode().apply {
            put("name", name)
            set<ObjectNode>("arguments", runCatching { mapper.readTree(argumentsJson) }.getOrNull() as? ObjectNode
                ?: mapper.createObjectNode())
        }
        val node = post(rpc("tools/call", params))
        node["error"]?.let { return "MCP 调用出错：${it["message"]?.asText() ?: it.toString()}" }
        val content = node["result"]?.get("content")
        if (content == null || !content.isArray) return node["result"]?.toString() ?: "(空结果)"
        val text = buildString {
            content.forEach { c -> if (c["type"]?.asText() == "text") append(c["text"]?.asText().orEmpty()) }
        }
        return text.ifBlank { content.toString() }
    }

    private fun rpc(method: String, params: ObjectNode?): ObjectNode = mapper.createObjectNode().apply {
        put("jsonrpc", "2.0")
        put("id", ids.incrementAndGet())
        put("method", method)
        if (params != null) set<ObjectNode>("params", params)
    }

    private fun notify(method: String) {
        val req = mapper.createObjectNode().put("jsonrpc", "2.0").put("method", method)
        runCatching { post(req) }.onFailure { log.debug("MCP notify {} 失败: {}", method, it.message) }
    }

    private fun post(req: ObjectNode): JsonNode {
        val builder = Request.Builder()
            .url(endpoint)
            .post(mapper.writeValueAsString(req).toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json, text/event-stream")
        headers.forEach { (k, v) -> builder.header(k, v) }
        sessionId?.let { builder.header("Mcp-Session-Id", it) }

        http.newCall(builder.build()).execute().use { resp ->
            resp.header("Mcp-Session-Id")?.let { sessionId = it }
            val body = resp.body?.string().orEmpty()
            return if (body.isBlank()) mapper.createObjectNode() else parseRpc(body)
        }
    }

    /** 兼容纯 JSON 与 SSE：SSE 取最后一条含 result/error/id 的 data 行。 */
    private fun parseRpc(body: String): JsonNode {
        val trimmed = body.trimStart()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return mapper.readTree(trimmed)
        var last: JsonNode? = null
        body.lineSequence().forEach { line ->
            val l = line.trim()
            if (l.startsWith("data:")) {
                runCatching { mapper.readTree(l.removePrefix("data:").trim()) }.getOrNull()
                    ?.let { if (it.has("result") || it.has("error") || it.has("id")) last = it }
            }
        }
        return last ?: mapper.createObjectNode()
    }
}
