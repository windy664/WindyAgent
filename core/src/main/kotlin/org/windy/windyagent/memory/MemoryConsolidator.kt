package org.windy.windyagent.memory

import org.slf4j.LoggerFactory
import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.llm.LLMProvider
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 记忆整合器：定期用 LLM 合并/去重/精炼长期记忆。
 *
 * 长期记忆只增不删会越来越臃肿（重复、过时、矛盾）。
 * 整合策略：
 * 1. 按 scope 分组，每组找语义重复的记忆对
 * 2. 用 LLM 合并重复对为一条精炼记忆
 * 3. 删除被合并的旧记忆
 *
 * 定期执行（默认每天凌晨），不在热路径上。
 */
class MemoryConsolidator(
    private val memory: FileLongTermMemory,
    private val llm: LLMProvider
) {
    private val log = LoggerFactory.getLogger(MemoryConsolidator::class.java)
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "memory-consolidate").apply { isDaemon = true } }

    fun start(intervalHours: Long = 24) {
        scheduler.scheduleAtFixedRate({ runCatching { consolidate() }.onFailure { log.warn("记忆整合异常：{}", it.message) } },
            intervalHours, intervalHours, TimeUnit.HOURS)
        log.info("记忆整合器已启动（每 {} 小时）", intervalHours)
    }

    fun stop() = scheduler.shutdown()

    /** 手动触发一次整合。返回合并了多少条。 */
    fun consolidate(): Int {
        val all = memory.list("", includeAdmin = true)
        if (all.size < 5) return 0 // 太少不值得整合

        // 按 scope 分组
        val byScope = all.groupBy { it.scope }
        var merged = 0

        for ((scope, entries) in byScope) {
            if (entries.size < 3) continue
            // 找语义重复对（简单策略：内容前 50 字符相同 or 互相包含）
            val duplicates = findDuplicates(entries)
            if (duplicates.isEmpty()) continue

            for ((a, b) in duplicates) {
                val mergedContent = mergeMemories(a.content, b.content) ?: continue
                // 保留较新的那条，更新内容，删除另一条
                val keep = if (a.createdAt >= b.createdAt) a else b
                val remove = if (keep === a) b else a
                memory.forget(remove.id)
                memory.forget(keep.id)
                memory.remember(scope, mergedContent, (a.tags + b.tags).distinct())
                merged++
                log.info("合并记忆 #{} + #{} → 精炼版", a.id, b.id)
            }
        }
        if (merged > 0) log.info("记忆整合完成：合并 {} 对", merged)
        return merged
    }

    private fun findDuplicates(entries: List<MemoryEntry>): List<Pair<MemoryEntry, MemoryEntry>> {
        val result = mutableListOf<Pair<MemoryEntry, MemoryEntry>>()
        val seen = mutableSetOf<String>()
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                val a = entries[i]; val b = entries[j]
                val key = setOf(a.id, b.id).joinToString(":")
                if (key in seen) continue
                if (isDuplicate(a.content, b.content)) {
                    result.add(a to b)
                    seen.add(key)
                }
            }
        }
        return result
    }

    private fun isDuplicate(a: String, b: String): Boolean {
        val na = a.trim().lowercase().replace(Regex("\\s+"), "")
        val nb = b.trim().lowercase().replace(Regex("\\s+"), "")
        if (na == nb) return true
        if (na.length >= 8 && nb.length >= 8 && (na.contains(nb.take(50)) || nb.contains(na.take(50)))) return true
        return false
    }

    private fun mergeMemories(a: String, b: String): String? {
        return runCatching {
            val prompt = """
                把以下两条记忆合并为一条精炼记忆，保留所有关键信息，去除重复和矛盾。
                直接输出合并后的记忆内容，不要加前缀。

                记忆 A：$a
                记忆 B：$b
            """.trimIndent()
            llm.chat("你是记忆整合器，只输出合并后的文本。", listOf(LLMMessage.User(prompt)))
                .textContent?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
