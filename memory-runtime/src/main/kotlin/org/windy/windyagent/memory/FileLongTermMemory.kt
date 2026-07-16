package org.windy.windyagent.memory

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import org.windy.windyagent.knowledge.KeywordKnowledgeStore
import org.windy.windyagent.knowledge.KnowledgeEntry
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 文件持久的长期记忆：`<dir>/memories.json` 存全量条目，启动载入、变更落盘。
 * recall 复用 [KeywordKnowledgeStore] 的稀疏检索（把记忆映射成 KnowledgeEntry），不重写检索。
 *
 * @param maxEntries 上限，超出按 createdAt 最旧淘汰。
 */
class FileLongTermMemory(
    private val dir: Path,
    private val maxEntries: Int = 500,
    /** 召回最低分：低于此的弱命中视为不相关、不召回（挡垃圾进上下文）。 */
    private val recallMinScore: Int = 2,
    /** 会话历史存储（可选）：recall 时同时搜历史对话原文，与关键词记忆互补。 */
    private val sessionStore: org.windy.windyagent.platform.SessionStore? = null
) : LongTermMemory {

    private val log = LoggerFactory.getLogger(FileLongTermMemory::class.java)
    private val mapper: ObjectMapper = ObjectMapper().registerKotlinModule()
    private val file: Path = dir.resolve("memories.json")

    // id -> entry
    private val entries = ConcurrentHashMap<String, MemoryEntry>()
    // 缓存检索索引，写入时置空惰性重建（issue #8）
    @Volatile private var cachedStore: KeywordKnowledgeStore? = null
    @Volatile private var cachedStoreSize: Int = 0
    // debounce 落盘（issue #17）
    @Volatile private var persistScheduled = false
    private val persistTimer = java.util.Timer("memory-persist", true).apply {
        schedule(object : java.util.TimerTask() { override fun run() { if (persistScheduled) { persistScheduled = false; doPersist() } } }, 5000, 5000)
    }

    init {
        runCatching {
            if (Files.exists(file)) {
                mapper.readValue<List<MemoryEntry>>(Files.readAllBytes(file)).forEach { entries[it.id] = it }
                log.info("长期记忆已载入 — {} 条", entries.size)
            }
        }.onFailure { log.warn("载入长期记忆失败：{}", it.message) }
    }

    override fun remember(scope: String, content: String, tags: List<String>): MemoryEntry {
        val sc = scope.ifBlank { LongTermMemory.GLOBAL }
        val norm = normalize(content)
        // 写入去重：同 scope 已有归一化相等 / 互相包含的记忆 → 复用，不重复攒
        entries.values.firstOrNull { it.scope == sc && isDuplicate(normalize(it.content), norm) }?.let {
            log.info("记忆去重：与 #{} 重复，跳过", it.id)
            return it
        }
        val e = MemoryEntry(UUID.randomUUID().toString().take(8), sc, content.trim(), tags, System.currentTimeMillis())
        entries[e.id] = e
        evictIfNeeded()
        schedulePersist()
        log.info("记下记忆[{}] scope={}：{}", e.id, e.scope, content.take(60))
        return e
    }

    override fun recall(scope: String, query: String, topK: Int, includeAdmin: Boolean): List<MemoryEntry> {
        val scoped = entries.values.filter { it.scope == scope || it.scope == LongTermMemory.GLOBAL || (includeAdmin && it.scope == LongTermMemory.ADMIN) }
        val memoryResults = if (scoped.isNotEmpty()) {
            val store = cachedStore?.takeIf { cachedStoreSize == entries.size }
                ?: KeywordKnowledgeStore(scoped.map { KnowledgeEntry(it.id, it.content, it.content, it.tags) })
                    .also { cachedStore = it; cachedStoreSize = entries.size }
            val hitIds = store.searchScored(query, topK).filter { it.second >= recallMinScore }.map { it.first.id }.toSet()
            scoped.filter { it.id in hitIds }
        } else emptyList()

        // 同时搜会话历史原文（与记忆互补：记忆记"结论"，这里记"原文"）
        val chatResults = sessionStore?.let { ss ->
            ss.search(query, topK, scope).map { (session, content, ts) ->
                MemoryEntry(id = "chat_${ts}", scope = session, content = "[历史对话] $content", tags = listOf("chat"), createdAt = ts)
            }
        } ?: emptyList()

        // 合并：记忆优先，历史对话补充，去重，截断
        val merged = (memoryResults + chatResults).distinctBy { it.content.take(100) }.take(topK)
        return merged
    }

    override fun cleanDuplicates(scope: String): Int {
        val seen = HashSet<String>()
        val dup = entries.values.filter { it.scope == scope }.sortedBy { it.createdAt }
            .filter { !seen.add(normalize(it.content)) }.map { it.id }
        dup.forEach { entries.remove(it) }
        if (dup.isNotEmpty()) schedulePersist()
        return dup.size
    }

    private fun normalize(s: String) = s.trim().lowercase().replace(Regex("\\s+"), "")
    private fun isDuplicate(a: String, b: String) = a == b || (a.length >= 4 && b.length >= 4 && (a.contains(b) || b.contains(a)))

    override fun list(scope: String, includeAdmin: Boolean): List<MemoryEntry> =
        entries.values.filter { it.scope == scope || it.scope == LongTermMemory.GLOBAL || (includeAdmin && it.scope == LongTermMemory.ADMIN) }.sortedBy { it.createdAt }

    override fun forget(id: String): Boolean {
        val removed = entries.remove(id) != null
        if (removed) schedulePersist()
        return removed
    }

    override fun clearScope(scope: String): Int {
        val toRemove = entries.values.filter { it.scope == scope }.map { it.id }
        toRemove.forEach { entries.remove(it) }
        if (toRemove.isNotEmpty()) schedulePersist()
        return toRemove.size
    }

    private fun evictIfNeeded() {
        if (entries.size <= maxEntries) return
        // 排序 + 批量删除，避免逐个 remove 的竞态（issue #11）
        val toEvict = entries.values.sortedBy { it.createdAt }.take(entries.size - maxEntries).map { it.id }
        toEvict.forEach { entries.remove(it) }
    }

    /** 调度延迟落盘（debounce 5s，减少高频写入的 I/O 压力，issue #17）。 */
    private fun schedulePersist() {
        cachedStore = null // 写入时失效缓存
        persistScheduled = true
    }

    @Synchronized
    private fun doPersist() {
        runCatching {
            Files.createDirectories(dir)
            Files.write(file, mapper.writeValueAsBytes(entries.values.sortedBy { it.createdAt }))
        }.onFailure { log.warn("持久化长期记忆失败：{}", it.message) }
    }
}
