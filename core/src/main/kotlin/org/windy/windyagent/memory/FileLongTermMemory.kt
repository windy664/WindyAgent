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
    private val recallMinScore: Int = 2
) : LongTermMemory {

    private val log = LoggerFactory.getLogger(FileLongTermMemory::class.java)
    private val mapper: ObjectMapper = ObjectMapper().registerKotlinModule()
    private val file: Path = dir.resolve("memories.json")

    // id -> entry
    private val entries = ConcurrentHashMap<String, MemoryEntry>()

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
        persist()
        log.info("记下记忆[{}] scope={}：{}", e.id, e.scope, content.take(60))
        return e
    }

    override fun recall(scope: String, query: String, topK: Int, includeAdmin: Boolean): List<MemoryEntry> {
        val scoped = entries.values.filter { it.scope == scope || it.scope == LongTermMemory.GLOBAL || (includeAdmin && it.scope == LongTermMemory.ADMIN) }
        if (scoped.isEmpty()) return emptyList()
        val store = KeywordKnowledgeStore(scoped.map { KnowledgeEntry(it.id, it.content, it.content, it.tags) })
        // 仅保留分数达阈值的命中，过滤弱相关垃圾
        val hitIds = store.searchScored(query, topK).filter { it.second >= recallMinScore }.map { it.first.id }.toSet()
        return scoped.filter { it.id in hitIds }
    }

    override fun cleanDuplicates(scope: String): Int {
        val seen = HashSet<String>()
        val dup = entries.values.filter { it.scope == scope }.sortedBy { it.createdAt }
            .filter { !seen.add(normalize(it.content)) }.map { it.id }
        dup.forEach { entries.remove(it) }
        if (dup.isNotEmpty()) persist()
        return dup.size
    }

    private fun normalize(s: String) = s.trim().lowercase().replace(Regex("\\s+"), "")
    private fun isDuplicate(a: String, b: String) = a == b || (a.length >= 4 && b.length >= 4 && (a.contains(b) || b.contains(a)))

    override fun list(scope: String, includeAdmin: Boolean): List<MemoryEntry> =
        entries.values.filter { it.scope == scope || it.scope == LongTermMemory.GLOBAL || (includeAdmin && it.scope == LongTermMemory.ADMIN) }.sortedBy { it.createdAt }

    override fun forget(id: String): Boolean {
        val removed = entries.remove(id) != null
        if (removed) persist()
        return removed
    }

    override fun clearScope(scope: String): Int {
        val toRemove = entries.values.filter { it.scope == scope }.map { it.id }
        toRemove.forEach { entries.remove(it) }
        if (toRemove.isNotEmpty()) persist()
        return toRemove.size
    }

    private fun evictIfNeeded() {
        if (entries.size <= maxEntries) return
        entries.values.sortedBy { it.createdAt }.take(entries.size - maxEntries).forEach { entries.remove(it.id) }
    }

    @Synchronized
    private fun persist() {
        runCatching {
            Files.createDirectories(dir)
            Files.write(file, mapper.writeValueAsBytes(entries.values.sortedBy { it.createdAt }))
        }.onFailure { log.warn("持久化长期记忆失败：{}", it.message) }
    }
}
