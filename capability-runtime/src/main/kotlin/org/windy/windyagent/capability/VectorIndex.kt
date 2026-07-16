package org.windy.windyagent.capability

import org.windy.windyagent.bus.CapabilityCommand
import java.util.concurrent.ConcurrentHashMap

/**
 * 极简内存向量索引（余弦相似度），按 server 分桶以便整服替换 + 过滤。
 * 命令目录规模（几百×几服）用线性扫描足够，不引 Qdrant；量真大了再换。
 */
class VectorIndex {

    data class Item(val server: String, val command: CapabilityCommand, val vec: FloatArray)

    private val byServer = ConcurrentHashMap<String, List<Item>>()

    fun replaceServer(server: String, items: List<Item>) {
        byServer[server] = items
    }

    fun isEmpty(): Boolean = byServer.values.all { it.isEmpty() }

    fun search(query: FloatArray, serverFilter: String?, topK: Int): List<Item> {
        val scope = byServer.filterKeys { serverFilter == null || it.equals(serverFilter, ignoreCase = true) }
            .values.flatten()
        if (scope.isEmpty()) return emptyList()
        return scope.map { it to cosine(query, it.vec) }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        val n = minOf(a.size, b.size)
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in 0 until n) {
            dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]
        }
        if (na == 0.0 || nb == 0.0) return 0.0
        return dot / (Math.sqrt(na) * Math.sqrt(nb))
    }
}
