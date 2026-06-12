package org.windy.windyagent

import org.windy.windyagent.mcp.McpServerConfig
import org.yaml.snakeyaml.Yaml
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

class AgentConfig private constructor(private val root: Map<String, Any>) {

    companion object {
        fun load(configDir: Path): AgentConfig {
            val file = configDir.resolve("windyagent-config.yml")
            if (!file.exists()) {
                configDir.createDirectories()
                (AgentConfig::class.java.classLoader.getResourceAsStream("windyagent-config.yml")
                    ?: throw FileNotFoundException("windyagent-config.yml not found in JAR"))
                    .use { Files.copy(it, file) }
            }
            @Suppress("UNCHECKED_CAST")
            val root = file.inputStream().use { Yaml().load<Map<String, Any>>(it) }
            return AgentConfig(root)
        }
    }

    fun provider() = getString("llm.provider", "claude")
    fun apiKey() = getString("llm.api-key", "")
    fun apiBaseUrl() = getString("llm.api-base-url", "")
    fun model() = getString("llm.model", "claude-opus-4-8")
    fun ollamaUrl() = getString("llm.ollama-url", "http://localhost:11434")
    fun trigger() = getString("agent.trigger", "!ai")
    fun maxHistory() = (getNode("agent.max-history") as? Number)?.toInt() ?: 20

    // 部署形态（仅 Bukkit 读取；Velocity 固定为中心 Agent，忽略本段）
    /** provider（纯能力提供方）/ standalone（嵌入式 Agent）/ hub（嵌入式 Agent + 总线中枢）。 */
    fun mode() = getString("deployment.mode", "provider").lowercase()
    /** 本节点在总线上的名字（provider 必填，须与中枢侧期望一致）。 */
    fun serverName() = getString("deployment.server-name", "")

    // 跨服总线（Velocity ↔ Bukkit 子服）
    fun crossServerEnabled() = (getNode("cross-server.enabled") as? Boolean) ?: false
    /** 传输实现：redis（生产）/ socket（无 Redis 的自建 TCP 中枢）/ inprocess（单实例测试）。 */
    fun crossServerTransport() = getString("cross-server.transport", "redis").lowercase()
    fun remoteTimeoutMs() = (getNode("cross-server.timeout-ms") as? Number)?.toLong() ?: 5000L

    // transport: redis
    fun redisHost() = getString("cross-server.redis.host", "127.0.0.1")
    fun redisPort() = (getNode("cross-server.redis.port") as? Number)?.toInt() ?: 6379
    fun redisPassword() = getString("cross-server.redis.password", "")

    // transport: socket（中枢 bind / 子服连接的同一组 host:port + 共享密钥）
    fun socketHost() = getString("cross-server.socket.host", "0.0.0.0")
    fun socketPort() = (getNode("cross-server.socket.port") as? Number)?.toInt() ?: 25599
    fun socketSecret() = getString("cross-server.socket.secret", "")

    // 安全护栏：命令执行策略
    /** enforce（命中即拒）/ warn（放行但审计告警）/ off（不拦）。 */
    fun safetyMode() = getString("safety.mode", "enforce")
    /** 高危命令黑名单（空则用内置默认表）。 */
    fun commandDenylist(): List<String> =
        (getNode("safety.command-denylist") as? List<*>)?.mapNotNull { (it as? String)?.takeIf { s -> s.isNotBlank() } } ?: emptyList()

    // RAG 查询扩展（无 embedding 的语义增强）：稀疏命中不足时用 LLM 扩展查询词
    fun ragQueryExpansion() = (getNode("rag.query-expansion") as? Boolean) ?: true
    fun ragMinHits() = (getNode("rag.min-hits") as? Number)?.toInt() ?: 1

    // 嵌入/语义检索（L3 RAG）：把能力目录嵌入向量，search_capabilities 走语义检索
    fun embeddingEnabled() = (getNode("embedding.enabled") as? Boolean) ?: false
    fun embeddingModel() = getString("embedding.model", "")
    /** 默认复用 llm 的 base-url / key（留空即用 llm 的）。 */
    fun embeddingApiBaseUrl() = getString("embedding.api-base-url", "").ifBlank { apiBaseUrl() }
    fun embeddingApiKey() = getString("embedding.api-key", "").ifBlank { apiKey() }

    // MCP 工具接入（可选）：外部 MCP server 列表
    fun mcpServers(): List<McpServerConfig> {
        val list = getNode("mcp.servers") as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val url = (m["url"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = (m["name"] as? String)?.takeIf { it.isNotBlank() } ?: url
            @Suppress("UNCHECKED_CAST")
            val headers = (m["headers"] as? Map<String, Any?>)?.mapValues { it.value.toString() } ?: emptyMap()
            McpServerConfig(name, url, headers)
        }
    }

    private fun getString(path: String, default: String): String {
        val v = getNode(path)
        return if (v is String && v.isNotBlank()) v else default
    }

    @Suppress("UNCHECKED_CAST")
    private fun getNode(path: String): Any? {
        var cur: Any? = root
        for (key in path.split(".")) {
            cur = (cur as? Map<String, Any>)?.get(key) ?: return null
        }
        return cur
    }
}
