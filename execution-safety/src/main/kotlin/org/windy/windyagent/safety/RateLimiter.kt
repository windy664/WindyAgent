package org.windy.windyagent.safety

import java.util.concurrent.ConcurrentHashMap

/**
 * Token-bucket rate limiter, keyed by caller id.
 */
class RateLimiter(
    private val bucketSize: Int = 5,
    private val refillRate: Double = 0.1
) {
    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun tryAcquire(key: String): Boolean {
        val bucket = buckets.computeIfAbsent(key) { Bucket(bucketSize.toDouble()) }
        return bucket.tryConsume(refillRate, bucketSize)
    }

    fun remaining(key: String): Double {
        val bucket = buckets[key] ?: return bucketSize.toDouble()
        return bucket.refill(refillRate, bucketSize.toDouble())
    }

    fun clear() = buckets.clear()

    fun cleanup(maxAgeMs: Long = 3600_000) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        buckets.entries.removeIf { it.value.lastAccess < cutoff }
    }

    private class Bucket(initialTokens: Double) {
        @Volatile var tokens: Double = initialTokens
        @Volatile var lastAccess: Long = System.currentTimeMillis()

        fun refill(refillRate: Double, bucketSize: Double): Double {
            val now = System.currentTimeMillis()
            val elapsed = (now - lastAccess) / 1000.0
            tokens = (tokens + elapsed * refillRate).coerceAtMost(bucketSize)
            lastAccess = now
            return tokens
        }

        fun tryConsume(refillRate: Double, bucketSize: Int): Boolean {
            synchronized(this) {
                refill(refillRate, bucketSize.toDouble())
                return if (tokens >= 1.0) {
                    tokens -= 1.0
                    true
                } else {
                    false
                }
            }
        }
    }
}
