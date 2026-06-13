package org.windy.windyagent.memory

/** 一条长期记忆。scope = 玩家会话 id，或全局 [GLOBAL]。 */
data class MemoryEntry(
    val id: String = "",
    val scope: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0L
)

/**
 * 跨会话长期记忆抽象。当前文件实现（[FileLongTermMemory]）；将来量大/要结构化查询可换 DB/向量，接口不变。
 */
interface LongTermMemory {
    fun remember(scope: String, content: String, tags: List<String> = emptyList()): MemoryEntry
    /** 召回与 query 相关、且属于该 scope 或全局的记忆。 */
    fun recall(scope: String, query: String, topK: Int = 3): List<MemoryEntry>
    fun list(scope: String): List<MemoryEntry>
    fun forget(id: String): Boolean
    fun clearScope(scope: String): Int
    /** 清理本 scope 的精确重复记忆，返回清除条数。 */
    fun cleanDuplicates(scope: String): Int

    companion object {
        const val GLOBAL = "global"
    }
}
