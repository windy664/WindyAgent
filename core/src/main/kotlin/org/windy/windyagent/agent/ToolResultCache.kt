package org.windy.windyagent.agent

import org.windy.windyagent.llm.ToolResult
import java.security.MessageDigest
import java.util.LinkedHashMap

/**
 * 工具结果缓存：相同工具 + 相同参数短时间内不重复调用。
 *
 * LLM 在多轮对话中可能重复请求同一个查询（如连续查同一玩家余额），
 * 缓存可避免浪费 API 调用和延迟。
 *
 * LRU 淘汰 + TTL 过期。
 */
class ToolResultCache(
    private val maxSize: Int = 128,
    /** 缓存有效期（毫秒）。默认 5 分钟。 */
    private val ttlMs: Long = 5 * 60 * 1000
) {
    private data class Entry(val result: ToolResult, val ts: Long)

    // accessOrder=true for LRU
    private val cache = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > maxSize
    }

    /**
     * 查询缓存。命中且未过期返回结果，否则返回 null。
     */
    fun get(toolName: String, inputJson: String): ToolResult? {
        val key = key(toolName, inputJson)
        synchronized(cache) {
            val entry = cache[key] ?: return null
            if (System.currentTimeMillis() - entry.ts > ttlMs) {
                cache.remove(key)
                return null
            }
            return entry.result
        }
    }

    /**
     * 存入缓存。只缓存成功的结果（失败的不缓存，允许重试）。
     */
    fun put(toolName: String, inputJson: String, result: ToolResult) {
        if (result.isError) return
        val key = key(toolName, inputJson)
        synchronized(cache) {
            cache[key] = Entry(result, System.currentTimeMillis())
        }
    }

    fun clear() { synchronized(cache) { cache.clear() } }
    fun size(): Int = synchronized(cache) { cache.size }

    private fun key(toolName: String, inputJson: String): String {
        // 用 hash 做 key，避免大 JSON 字符串占用内存
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(toolName.toByteArray())
        digest.update(inputJson.toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }.take(16)
    }
}
