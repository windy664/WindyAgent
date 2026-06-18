package org.windy.windyagent.safety

import java.util.concurrent.ConcurrentHashMap

/**
 * 速率限制器：令牌桶算法，防止单玩家滥用 AI 请求。
 *
 * 每个玩家有独立的令牌桶，按 [refillRate] 每秒补充令牌，
 * 每次请求消耗一个令牌。桶空时拒绝请求。
 *
 * 线程安全，无锁（ConcurrentHashMap + AtomicDouble）。
 */
class RateLimiter(
    /** 桶容量（最大突发请求数）。 */
    private val bucketSize: Int = 5,
    /** 每秒补充多少令牌（如 0.1 = 每 10 秒一个）。 */
    private val refillRate: Double = 0.1
) {
    private val buckets = ConcurrentHashMap<String, Bucket>()

    /**
     * 尝试消费一个令牌。
     * @return 允许=true，限流=false。
     */
    fun tryAcquire(key: String): Boolean {
        val bucket = buckets.computeIfAbsent(key) { Bucket(bucketSize.toDouble()) }
        return bucket.tryConsume(refillRate, bucketSize)
    }

    /** 查询某 key 剩余令牌（监控用）。 */
    fun remaining(key: String): Double {
        val bucket = buckets[key] ?: return bucketSize.toDouble()
        return bucket.refill(refillRate, bucketSize.toDouble())
    }

    /** 清除所有桶（测试用）。 */
    fun clear() = buckets.clear()

    /** 清除不活跃的桶（定期调用，防内存泄漏）。 */
    fun cleanup(maxAgeMs: Long = 3600_000) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        buckets.entries.removeIf { it.value.lastAccess < cutoff }
    }

    private class Bucket(initialTokens: Double) {
        @Volatile var tokens: Double = initialTokens
        @Volatile var lastAccess: Long = System.currentTimeMillis()

        /** 补充令牌到桶容量上限。 */
        fun refill(refillRate: Double, bucketSize: Double): Double {
            val now = System.currentTimeMillis()
            val elapsed = (now - lastAccess) / 1000.0
            tokens = (tokens + elapsed * refillRate).coerceAtMost(bucketSize.toDouble())
            lastAccess = now
            return tokens
        }

        /** 尝试消耗一个令牌。 */
        fun tryConsume(refillRate: Double, bucketSize: Int): Boolean {
            synchronized(this) {
                refill(refillRate, bucketSize.toDouble())
                return if (tokens >= 1.0) {
                    tokens -= 1.0; true
                } else false
            }
        }
    }
}
