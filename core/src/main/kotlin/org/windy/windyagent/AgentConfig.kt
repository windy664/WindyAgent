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
    /** 元任务（路由分类/查询扩展）用的便宜模型；留空=与主模型相同。 */
    fun fastModel() = getString("llm.fast-model", "")
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

    // 长期记忆（跨会话）
    fun memoryEnabled() = (getNode("memory.enabled") as? Boolean) ?: true
    fun memoryRecallTopK() = (getNode("memory.recall-top-k") as? Number)?.toInt() ?: 3
    fun memoryMaxEntries() = (getNode("memory.max-entries") as? Number)?.toInt() ?: 500
    fun memoryRecallMinScore() = (getNode("memory.recall-min-score") as? Number)?.toInt() ?: 2

    // 模组物品估值
    fun itemValuationEnabled() = (getNode("item-valuation.enabled") as? Boolean) ?: true
    fun itemDefaultBaseValue() = (getNode("item-valuation.default-base-value") as? Number)?.toDouble() ?: 1.0
    fun itemPackDiscount() = (getNode("item-valuation.pack-discount") as? Number)?.toDouble() ?: 0.8
    fun itemCraftOverhead() = (getNode("item-valuation.craft-overhead") as? Number)?.toDouble() ?: 0.1
    fun itemPropagationMaxIter() = (getNode("item-valuation.propagation-max-iter") as? Number)?.toInt() ?: 50
    /** value llm all 一次最多估多少个悬空物（封顶防 token 失控）。 */
    fun itemLlmMaxItems() = (getNode("item-valuation.llm-max-items") as? Number)?.toInt() ?: 600
    /** value llm all 分批时每批多少个（一次 LLM 请求的条数）。 */
    fun itemLlmBatchSize() = (getNode("item-valuation.llm-batch-size") as? Number)?.toInt() ?: 80
    /** LLM 估"无配方根"时用的稀有度档→金币值。LLM 只负责把物品归档（它擅长），精确值靠传播派生。 */
    fun itemRarityTiers(): Map<String, Double> {
        val def = linkedMapOf("common" to 4.0, "uncommon" to 16.0, "rare" to 64.0, "epic" to 256.0, "legendary" to 1024.0)
        val m = getNode("item-valuation.rarity-tiers") as? Map<*, *> ?: return def
        val out = m.entries.mapNotNull { (k, v) ->
            val key = (k as? String)?.lowercase() ?: return@mapNotNull null
            val d = (v as? Number)?.toDouble() ?: return@mapNotNull null
            key to d
        }.toMap()
        return out.ifEmpty { def }
    }
    fun itemCurrencyName() = getString("item-valuation.currency-name", "金币")
    fun itemBaseValues(): Map<String, Double> {
        val m = getNode("item-valuation.base-values") as? Map<*, *> ?: return emptyMap()
        return m.entries.mapNotNull { (k, v) ->
            val key = (k as? String) ?: return@mapNotNull null
            val d = (v as? Number)?.toDouble() ?: return@mapNotNull null
            key to d
        }.toMap()
    }

    // 玩家行为分析（采集在子服，看板在 VC）
    fun behaviorEnabled() = (getNode("behavior.enabled") as? Boolean) ?: true
    fun behaviorFlushIntervalSec() = (getNode("behavior.flush-interval-sec") as? Number)?.toLong() ?: 60L
    fun behaviorRetentionDays() = (getNode("behavior.retention-days") as? Number)?.toInt() ?: 30
    /** 流失判定：超过这么多天未上线算流失风险。 */
    fun behaviorChurnDays() = (getNode("behavior.churn-days") as? Number)?.toInt() ?: 7
    /** 核心玩家门槛：累计在线分钟数。 */
    fun behaviorActiveMinutes() = (getNode("behavior.active-minutes") as? Number)?.toInt() ?: 300
    /** 新玩家窗口：首次上线在这么多天内算新人。 */
    fun behaviorNewbieDays() = (getNode("behavior.newbie-days") as? Number)?.toInt() ?: 3

    // AI 管理控制台（WebUI，仅 Velocity 读取）
    fun webEnabled() = (getNode("web.enabled") as? Boolean) ?: false
    fun webHost() = getString("web.host", "127.0.0.1")
    fun webPort() = (getNode("web.port") as? Number)?.toInt() ?: 8080
    fun webToken() = getString("web.token", "")

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
