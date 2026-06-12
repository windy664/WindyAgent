package org.windy.windyagent.capability

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import org.windy.windyagent.bus.CapabilityCatalog
import org.windy.windyagent.bus.CapabilityCommand
import org.windy.windyagent.llm.EmbeddingProvider
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 中心侧能力目录注册表：收齐各子服推来的目录，供 Agent 在**本地**检索（零总线往返）。
 *
 * 取代旧的 per-query 实时自省。子服目录经总线 [org.windy.windyagent.bus.MessageBus.onCatalog]
 * 推达即 [accept] 入表。standalone/hub 也可直接 [put] 本机目录。
 *
 * 检索两档：L2 关键词（分词 OR + 排序，零基建）；若注入 [embedder] 则 L3 语义向量检索，
 * 向量失败/无结果时自动退回 L2。对外 [search]/概览接口不变。
 */
class CapabilityRegistry(
    private val embedder: EmbeddingProvider? = null,
    /** 持久化目录：非空则收到的目录写盘、启动 [load] 时读回（中心"永久记忆"，重启免重推）。 */
    private val persistDir: Path? = null
) {

    private val log = LoggerFactory.getLogger(CapabilityRegistry::class.java)
    private val mapper: ObjectMapper = ObjectMapper().registerKotlinModule()

    // server -> catalog
    private val catalogs = ConcurrentHashMap<String, CapabilityCatalog>()

    private val vectorIndex = VectorIndex()
    private val embedExec = Executors.newSingleThreadExecutor { r ->
        Thread(r, "windyagent-embed").apply { isDaemon = true }
    }

    /** 收到一条目录 JSON（总线回调用）。 */
    fun accept(catalogJson: String) {
        val catalog = runCatching { mapper.readValue<CapabilityCatalog>(catalogJson) }.getOrElse {
            log.warn("解析能力目录失败：{}", it.message); return
        }
        if (catalog.server.isBlank()) return
        put(catalog)
        log.info("已接收子服「{}」能力目录 — {} 条命令", catalog.server, catalog.commands.size)
    }

    /** 直接放入一个目录（standalone/hub 本机用）。 */
    fun put(catalog: CapabilityCatalog) {
        catalogs[catalog.server] = catalog
        persist(catalog)
        if (embedder != null) reembed(catalog)
    }

    /** 启动时从磁盘载入已持久化的目录（重启免等子服重推）。 */
    fun load() {
        val dir = persistDir ?: return
        if (!Files.isDirectory(dir)) return
        runCatching {
            Files.list(dir).use { stream ->
                stream.filter { it.fileName.toString().endsWith(".json") }.forEach { f ->
                    runCatching {
                        val catalog = mapper.readValue<CapabilityCatalog>(Files.readAllBytes(f))
                        if (catalog.server.isNotBlank()) {
                            catalogs[catalog.server] = catalog
                            if (embedder != null) reembed(catalog)
                            log.info("从磁盘载入子服「{}」能力目录 — {} 条命令", catalog.server, catalog.commands.size)
                        }
                    }
                }
            }
        }.onFailure { log.warn("载入持久化目录失败：{}", it.message) }
    }

    private fun persist(catalog: CapabilityCatalog) {
        val dir = persistDir ?: return
        runCatching {
            Files.createDirectories(dir)
            val safe = catalog.server.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            Files.write(dir.resolve("$safe.json"), mapper.writeValueAsBytes(catalog))
        }.onFailure { log.warn("持久化子服「{}」目录失败：{}", catalog.server, it.message) }
    }

    /** 异步把该子服命令嵌入向量（分批），就绪后语义检索可用；失败退回关键词。 */
    private fun reembed(catalog: CapabilityCatalog) {
        val emb = embedder ?: return
        embedExec.submit {
            runCatching {
                val texts = catalog.commands.map { embedText(it) }
                val vecs = texts.chunked(64).flatMap { emb.embed(it) }
                val items = catalog.commands.zip(vecs).map { (c, v) -> VectorIndex.Item(catalog.server, c, v) }
                vectorIndex.replaceServer(catalog.server, items)
                log.info("已为子服「{}」嵌入 {} 条命令（语义检索就绪）", catalog.server, items.size)
            }.onFailure { log.warn("嵌入子服「{}」目录失败，退回关键词检索：{}", catalog.server, it.message) }
        }
    }

    private fun embedText(c: CapabilityCommand): String =
        "${c.name} ${c.aliases.joinToString(" ")} ${c.description} [${c.source}]"

    fun servers(): Set<String> = catalogs.keys
    fun isEmpty(): Boolean = catalogs.isEmpty()
    fun totalCommands(): Int = catalogs.values.sumOf { it.commands.size }

    /** 一条命中：命令 + 所在子服。 */
    data class Hit(val server: String, val command: CapabilityCommand)

    /**
     * 检索：有 embedder 且向量就绪 → **语义向量检索**（中文 query 也能命中英文命令名）；
     * 否则 / 向量失败 / 无结果 → **L2 分词 OR + 按命中词数排序**（多词查询不再整串落空）。
     */
    fun search(query: String, serverFilter: String?, limit: Int): List<Hit> {
        val emb = embedder
        if (emb != null && !vectorIndex.isEmpty()) {
            val vec = runCatching { emb.embed(listOf(query)).firstOrNull() }.getOrNull()
            if (vec != null) {
                val items = vectorIndex.search(vec, serverFilter, limit)
                if (items.isNotEmpty()) return items.map { Hit(it.server, it.command) }
            }
        }
        return scoredWithServer(query, serverFilter).take(limit).map { Hit(it.server, it.command) }
    }

    /** 统计匹配总数（用于"还有 N 条"提示），不限量。 */
    fun count(query: String, serverFilter: String?): Int = scoredWithServer(query, serverFilter).size

    private data class Scored(val score: Int, val server: String, val command: CapabilityCommand)

    private fun scoredWithServer(query: String, serverFilter: String?): List<Scored> {
        val ql = query.trim().lowercase()
        // 原始分词 + 中英同义词扩展（无 embedding 时让中文也能命中英文命令）
        val tokens = (ql.split(Regex("\\s+")).filter { it.isNotEmpty() } + CommandSynonyms.expand(ql)).distinct()
        if (tokens.isEmpty()) return emptyList()
        val scope = catalogs.values.filter { serverFilter == null || it.server.equals(serverFilter, ignoreCase = true) }
        return scope.flatMap { cat ->
            cat.commands.mapNotNull { c ->
                val hay = "${c.name} ${c.aliases.joinToString(" ")} ${c.description} ${c.source}".lowercase()
                val score = tokens.count { hay.contains(it) }
                if (score == 0) null else Scored(score, cat.server, c)
            }
        }.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.command.name.lowercase() })
    }

    /** 概览：每个子服按来源插件分组计数。serverFilter 非空则只看该子服。 */
    fun overview(serverFilter: String?): String {
        val scope = catalogs.values.filter { serverFilter == null || it.server.equals(serverFilter, ignoreCase = true) }
        if (scope.isEmpty()) return if (serverFilter != null) "未收到子服「$serverFilter」的能力目录（可能未启动或未连接）" else "尚未收到任何子服的能力目录"
        return scope.joinToString("\n\n") { cat ->
            val bySource = cat.commands.groupingBy { it.source.ifBlank { "原版/模组" } }
                .eachCount().entries.sortedByDescending { it.value }
            val top = bySource.take(20).joinToString("\n") { "  - ${it.key}: ${it.value} 条" }
            "【${cat.server}】共 ${cat.commands.size} 条命令，按来源：\n$top"
        }
    }
}
