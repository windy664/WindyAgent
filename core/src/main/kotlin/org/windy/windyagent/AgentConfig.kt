package org.windy.windyagent

import org.windy.windyagent.mcp.McpServerConfig
import org.yaml.snakeyaml.Yaml
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

class AgentConfig private constructor(
    private val root: MutableMap<String, Any>,
    private val configFile: Path
) {

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
            val root = file.inputStream().use { Yaml().load<MutableMap<String, Any>>(it) }
            return AgentConfig(root, file)
        }

        private const val TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
    }

    fun language() = getString("language", "zh_cn")
    fun usageEnabled() = (getNode("usage.enabled") as? Boolean) ?: true

    /** 今日 LLM token 预算上限(输入+输出)。0=不限。超限暂停 AI 调用防烧钱，次日自动恢复。(#3) */
    fun llmBudgetDailyTokens() = (getNode("llm.budget-daily-tokens") as? Number)?.toLong() ?: 0L
    fun personalityFile() = getString("personality", "personality.md")
    fun compressionEnabled() = (getNode("context.compress.enabled") as? Boolean) ?: true
    fun compressionThreshold() = (getNode("context.compress.threshold") as? Number)?.toInt() ?: 16
    fun compressionKeepRecent() = (getNode("context.compress.keep-recent") as? Number)?.toInt() ?: 6
    fun profilesEnabled() = (getNode("profiles.enabled") as? Boolean) ?: true

    /**
     * 玩家画像的 PAPI 占位符——config `profiles.papi-placeholders` 里的**额外/覆盖**项：`显示名 → 占位符`。
     * 常用维度由 PapiProfileSource 按已装 expansion **半自动发现**（无需在此配）；这里只填语法库没覆盖的
     * 冷门维度（如领地数、投票数）或想覆盖默认显示名的项。默认没配 → 返回空 map，全交给半自动。
     */
    @Suppress("UNCHECKED_CAST")
    fun papiProfilePlaceholders(): Map<String, String> {
        val node = getNode("profiles.papi-placeholders") as? Map<String, Any> ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        for ((k, v) in node) {
            val expr = v?.toString()?.trim().orEmpty()
            if (k.isNotBlank() && expr.isNotEmpty()) out[k] = expr
        }
        return out
    }
    /** 同一会话两次画像更新的最小间隔（秒）；<=0 表示每条都更。画像更新是后台 LLM 调用，节流可省成本、降负载。 */
    fun profileUpdateMinIntervalSec() = (getNode("profiles.update-min-interval-sec") as? Number)?.toInt() ?: 60
    fun sessionStoreEnabled() = (getNode("session-store.enabled") as? Boolean) ?: true
    fun rateLimitEnabled() = (getNode("rate-limit.enabled") as? Boolean) ?: true

    // 工具增强组件
    /** 工具结果缓存：相同工具+参数短时间内不重复调用。 */
    fun toolCacheEnabled() = (getNode("tool-cache.enabled") as? Boolean) ?: true
    fun toolCacheTtlSeconds() = (getNode("tool-cache.ttl-seconds") as? Number)?.toLong() ?: 300L
    fun toolCacheMaxSize() = (getNode("tool-cache.max-size") as? Number)?.toInt() ?: 128
    /** 失败模式检测：循环/反复失败/累计超限自动中止。 */
    fun failureDetectEnabled() = (getNode("failure-detect.enabled") as? Boolean) ?: true
    /** 自我检查：回复前检查幻觉/泄露/完整性。 */
    fun selfCheckEnabled() = (getNode("self-check.enabled") as? Boolean) ?: true
    /** 轨迹记录：记录交互轨迹，可导出 SFT 训练数据。 */
    fun trajectoryEnabled() = (getNode("trajectory.enabled") as? Boolean) ?: true
    /** 记忆整合：定期 LLM 合并去重长期记忆。 */
    fun memoryConsolidateEnabled() = (getNode("memory-consolidate.enabled") as? Boolean) ?: true
    fun memoryConsolidateIntervalHours() = (getNode("memory-consolidate.interval-hours") as? Number)?.toLong() ?: 24L
    /** 运营洞察缓存：白天定时把活数据刷进缓存（分钟），供 00:00 夜间整理在无人在线时回退。0=关。 */
    fun opsDigestRefreshMin() = (getNode("ops-digest.refresh-min") as? Number)?.toInt() ?: 180
    /** ops_digest 现拉为空时，回退最近缓存的最大陈旧时长（小时）。 */
    fun opsDigestCacheMaxAgeHours() = (getNode("ops-digest.cache-max-age-hours") as? Number)?.toLong() ?: 36L
    /** 成本路由：按复杂度自动选便宜/贵模型（需配 fast-model）。 */
    fun costRouterEnabled() = (getNode("cost-router.enabled") as? Boolean) ?: false
    /** 子任务并行：复杂请求自动拆分子任务并行执行。 */
    fun subAgentEnabled() = (getNode("sub-agent.enabled") as? Boolean) ?: false
    /** Prompt 版本化：system prompt 存文件+历史归档。 */
    fun promptVersioningEnabled() = (getNode("prompt-versioning.enabled") as? Boolean) ?: true
    fun rateLimitBucketSize() = (getNode("rate-limit.bucket-size") as? Number)?.toInt() ?: 5
    fun rateLimitRefillRate() = (getNode("rate-limit.refill-rate") as? Number)?.toDouble() ?: 0.1
    fun provider() = getString("llm.provider", "claude")
    fun apiKey() = getString("llm.api-key", "")
    fun apiBaseUrl() = getString("llm.api-base-url", "")
    fun model() = getString("llm.model", "claude-opus-4-8")
    fun ollamaUrl() = getString("llm.ollama-url", "http://localhost:11434")
    /** 元任务（路由分类/查询扩展）用的便宜模型；留空=与主模型相同。 */
    fun fastModel() = getString("llm.fast-model", "")

    // 故障转移：备用 Provider 列表（主 Provider 挂了自动切换）
    /** 是否启用故障转移。 */
    fun fallbackEnabled() = (getNode("llm.fallback.enabled") as? Boolean) ?: false
    /** 备用 Provider 列表（格式同主 llm 配置）。 */
    @Suppress("UNCHECKED_CAST")
    fun fallbackProviders(): List<Map<String, Any>> {
        val node = getNode("llm.fallback.providers") ?: return emptyList()
        return node as? List<Map<String, Any>> ?: emptyList()
    }
    fun trigger() = getString("agent.trigger", "!ai")
    fun maxHistory() = (getNode("agent.max-history") as? Number)?.toInt() ?: 20

    // 部署形态（仅 Bukkit 读取；Velocity 固定为中心 Agent，忽略本段）
    /** provider / standalone / hub，由服主显式指定（不再自动判定）。默认 standalone（单台 Paper 本机跑 Agent）。 */
    fun mode() = getString("deployment.mode", "standalone").lowercase()
    /** 本节点在总线上的名字（provider 必填，须与中枢侧期望一致）。 */
    fun serverName() = getString("deployment.server-name", "")

    /** 取总线注册名；为空则用 [default] 写回 deployment.server-name 并返回（provider 未命名时自动命名用）。 */
    fun ensureServerName(default: String): String {
        val cur = serverName()
        if (cur.isNotBlank()) return cur
        @Suppress("UNCHECKED_CAST")
        (root["deployment"] as? MutableMap<String, Any>)?.put("server-name", default)
        runCatching { patchScalar("deployment", "server-name", default) }
        return default
    }

    // 跨服总线（Velocity ↔ Bukkit 子服）
    fun crossServerEnabled() = (getNode("cross-server.enabled") as? Boolean) ?: false
    /** 传输实现：redis（生产）/ socket（无 Redis 的自建 TCP 中枢）/ inprocess（单实例测试）。 */
    fun crossServerTransport() = getString("cross-server.transport", "socket").lowercase()
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
    /** 是否采集聊天内容做词云：仅在能正常触发 AsyncPlayerChatEvent 的服开（Youer 等混合端有 bug，默认关）。命令词云不受此开关影响。 */
    fun behaviorTrackChat() = (getNode("behavior.track-chat") as? Boolean) ?: false

    // 主动运维哨兵（仅中心 Velocity 读取）：定时巡检子服健康，异常时出处置建议 + 通知
    fun sentinelEnabled() = (getNode("sentinel.enabled") as? Boolean) ?: false
    fun sentinelIntervalSec() = (getNode("sentinel.interval-sec") as? Number)?.toLong() ?: 30L
    /** TPS 低于此判为卡顿（Paper/Youer 有 TPS，远古 Spigot 无则跳过此项）。 */
    fun sentinelTpsMin() = (getNode("sentinel.tps-min") as? Number)?.toDouble() ?: 15.0
    /** JVM 内存占用百分比超此判为内存吃紧。 */
    fun sentinelMemPct() = (getNode("sentinel.mem-pct") as? Number)?.toInt() ?: 90
    /** 一个巡检周期内在线人数掉这么多判为骤降（疑似崩服/被打）；0=关此项。 */
    fun sentinelPlayerDrop() = (getNode("sentinel.player-drop") as? Number)?.toInt() ?: 5
    /** 告警时是否调 LLM 给处置建议（false=只报原始告警，省 token）。 */
    fun sentinelAdvise() = (getNode("sentinel.advise") as? Boolean) ?: true

    // AI 管理控制台（WebUI，仅 Velocity 读取）
    fun webEnabled() = (getNode("web.enabled") as? Boolean) ?: true
    fun webHost() = getString("web.host", "0.0.0.0")
    fun webPort() = (getNode("web.port") as? Number)?.toInt() ?: 25580
    fun webToken() = getString("web.token", "")

    // —— Web 安全（借鉴宝塔，认证仍用 token）——
    /** 安全入口秘密前缀：非空时所有访问须经 /<entry>/，否则 404（挡全网扫描）。空=关闭。 */
    fun webSecurityEntry() = getString("web.security.entry", "")
    /** 同一 IP 连续失败多少次后锁定（防 token 爆破）。 */
    fun webSecurityMaxFails() = (getNode("web.security.max-fails") as? Number)?.toInt() ?: 5
    /** 锁定时长（分钟）。 */
    fun webSecurityLockMinutes() = (getNode("web.security.lock-minutes") as? Number)?.toInt() ?: 10

    /**
     * 取控制台 token；若为空则生成一个随机串、写回配置文件并返回。
     * 保证默认开启 web 时不会裸奔无鉴权——首启自动落一个 token 到 windyagent-config.yml。
     */
    fun ensureWebToken(): String {
        val cur = webToken()
        if (cur.isNotBlank()) return cur
        val token = buildString { repeat(32) { append(TOKEN_CHARS[kotlin.random.Random.nextInt(TOKEN_CHARS.length)]) } }
        // 内存里同步更新，使本次启动 webToken() 立即返回新值
        @Suppress("UNCHECKED_CAST")
        (root["web"] as? MutableMap<String, Any>)?.put("token", token)
        runCatching { patchScalar("web", "token", token) }
        return token
    }

    /**
     * 定点替换 `section:` 下某个标量键的值，保留行尾注释与文件其余内容（不整体重写 YAML，免得吃掉注释）。
     * 键不存在时插入到本段末尾（沿用段内缩进，默认 2 空格）。
     */
    private fun patchScalar(section: String, key: String, value: String) {
        if (!configFile.exists()) return
        val esc = value.replace("\\", "\\\\").replace("\"", "\\\"")
        val lines = configFile.readLines().toMutableList()
        val sectionRe = Regex("^$section:\\s*(#.*)?$")
        val keyRe = Regex("^(\\s+)$key:\\s*(\"[^\"]*\"|\\S*)?\\s*(#.*)?$")
        var sectionAt = -1
        var lastBodyAt = -1
        var indent = "  "
        for (i in lines.indices) {
            val line = lines[i]
            if (sectionAt < 0) { if (sectionRe.matches(line)) sectionAt = i; continue }
            // 段内所有合法行都带缩进；出现顶格非空行（下一段的键或注释）= 段结束
            if (line.isNotBlank() && !line.first().isWhitespace()) break
            val m = keyRe.matchEntire(line)
            if (m != null) {
                val ind = m.groupValues[1]; val comment = m.groupValues[3]
                lines[i] = "$ind$key: \"$esc\"" + if (comment.isNotEmpty()) "  $comment" else ""
                configFile.writeText(lines.joinToString(System.lineSeparator()) + System.lineSeparator())
                return
            }
            if (line.isNotBlank()) { lastBodyAt = i; line.takeWhile { it.isWhitespace() }.let { if (it.isNotEmpty()) indent = it } }
        }
        if (sectionAt < 0) return   // 没这个段，放弃（不擅自造段）
        val insertAt = (if (lastBodyAt >= 0) lastBodyAt else sectionAt) + 1
        lines.add(insertAt, "$indent$key: \"$esc\"")
        configFile.writeText(lines.joinToString(System.lineSeparator()) + System.lineSeparator())
    }

    /** 是否还没配好可用的 LLM（claude/openai 需 api-key；ollama 无需）。供首启向导判断。 */
    fun needsLlmSetup(): Boolean = provider().lowercase() != "ollama" && apiKey().isBlank()

    /**
     * 首启向导写回 LLM 配置：定点改写 windyagent-config.yml 的 llm 段并同步内存。
     * 写完需重启代理走完整初始化（不做热生效）。
     */
    fun applyLlmSetup(provider: String, apiBaseUrl: String, apiKey: String, model: String, fastModel: String) {
        val pairs = linkedMapOf(
            "provider" to provider, "api-base-url" to apiBaseUrl,
            "api-key" to apiKey, "model" to model, "fast-model" to fastModel
        )
        @Suppress("UNCHECKED_CAST")
        val llm = (root["llm"] as? MutableMap<String, Any>)
        pairs.forEach { (k, v) -> patchScalar("llm", k, v); llm?.put(k, v) }
    }

    // 安全护栏：命令执行策略
    /** enforce（命中即拒）/ warn（放行但审计告警）/ off（不拦）。 */
    fun safetyMode() = getString("safety.mode", "enforce")
    /** 高危命令黑名单（空则用内置默认表）。 */
    fun commandDenylist(): List<String> =
        (getNode("safety.command-denylist") as? List<*>)?.mapNotNull { (it as? String)?.takeIf { s -> s.isNotBlank() } } ?: emptyList()

    // RAG 查询扩展（无 embedding 的语义增强）：稀疏命中不足时用 LLM 扩展查询词
    fun ragQueryExpansion() = (getNode("rag.query-expansion") as? Boolean) ?: true
    fun ragMinHits() = (getNode("rag.min-hits") as? Number)?.toInt() ?: 1

    // 内置只读参考库（官方文档 pack：CMI 等）——并进知识库检索，但不落进服主的 vault
    fun knowledgeReferenceEnabled() = (getNode("knowledge.reference.enabled") as? Boolean) ?: true
    /** 启用的 pack id 白名单；空/未配 = 全部打进 jar 的 pack。 */
    fun knowledgeReferencePacks(): Set<String> =
        (getNode("knowledge.reference.packs") as? List<*>)
            ?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }?.toSet() ?: emptySet()

    // 嵌入/语义检索（L3 RAG）：把能力目录嵌入向量，search_capabilities 走语义检索
    fun embeddingEnabled() = (getNode("embedding.enabled") as? Boolean) ?: false
    fun embeddingModel() = getString("embedding.model", "")
    /** 默认复用 llm 的 base-url / key（留空即用 llm 的）。 */
    fun embeddingApiBaseUrl() = getString("embedding.api-base-url", "").ifBlank { apiBaseUrl() }
    fun embeddingApiKey() = getString("embedding.api-key", "").ifBlank { apiKey() }

    // Groovy 技能（服主编写的扩展能力，放插件数据目录的 skills/ 下，启动/插件变更时热重载）
    fun skillsEnabled() = (getNode("skills.enabled") as? Boolean) ?: true
    fun skillsDir() = getString("skills.dir", "skills")
    /** 技能执行的主线程超时看门狗（秒）：脚本久占主线程时解除 Agent 等待。 */
    fun skillTimeoutSec() = (getNode("skills.timeout-sec") as? Number)?.toLong() ?: 5L

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
