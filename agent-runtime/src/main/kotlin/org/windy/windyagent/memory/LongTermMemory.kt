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
    /** 召回与 query 相关、且属于该 scope 或全局的记忆；includeAdmin=true 时并含管理方共享域 [ADMIN]。 */
    fun recall(scope: String, query: String, topK: Int = 3, includeAdmin: Boolean = false): List<MemoryEntry>
    fun list(scope: String, includeAdmin: Boolean = false): List<MemoryEntry>
    fun forget(id: String): Boolean
    fun clearScope(scope: String): Int
    /** 清理本 scope 的精确重复记忆，返回清除条数。 */
    fun cleanDuplicates(scope: String): Int

    companion object {
        const val GLOBAL = "global"
        /** 管理方统一记忆域：所有可信(TRUSTED)通道——网页控制台 / VC 控制台 / 管理员 /ai——共享、跨通道互通。 */
        const val ADMIN = "admin"
    }
}
