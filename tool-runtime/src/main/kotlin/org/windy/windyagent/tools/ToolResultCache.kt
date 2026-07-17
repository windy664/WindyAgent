package org.windy.windyagent.tools

import org.windy.windyagent.llm.ToolResult
import java.security.MessageDigest
import java.util.LinkedHashMap

/**
 * Small LRU + TTL cache for successful tool results.
 */
class ToolResultCache(
    private val maxSize: Int = 128,
    private val ttlMs: Long = 5 * 60 * 1000
) {
    private data class Entry(val result: ToolResult, val ts: Long)

    private val cache = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > maxSize
    }

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

    fun put(toolName: String, inputJson: String, result: ToolResult) {
        if (result.isError) return
        val key = key(toolName, inputJson)
        synchronized(cache) {
            cache[key] = Entry(result, System.currentTimeMillis())
        }
    }

    fun clear() {
        synchronized(cache) {
            cache.clear()
        }
    }

    fun size(): Int = synchronized(cache) { cache.size }

    private fun key(toolName: String, inputJson: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(toolName.toByteArray())
        digest.update(inputJson.toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }.take(16)
    }
}
