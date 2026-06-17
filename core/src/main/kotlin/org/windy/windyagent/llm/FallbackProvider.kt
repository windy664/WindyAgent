package org.windy.windyagent.llm

import org.slf4j.LoggerFactory
import org.windy.windyagent.agent.AgentTool

/**
 * 多 Provider 自动故障转移：主 Provider 挂了（额度用完/过载/超时）自动切下一个，对话不中断。
 *
 * 设计：
 *  - 按优先级排列 Provider 列表（主 → 备用1 → 备用2 …）
 *  - 每次 chat() 先试当前活跃 Provider，失败且错误可恢复时自动切下一个
 *  - 定期（每 5 分钟）尝试切回主 Provider（health check）
 *  - 对话上下文（messages）在 Provider 间天然共享，切换不丢历史
 *
 * 错误分类：
 *  - 可恢复：429（额度/速率限制）、503（过载）、超时、连接错误
 *  - 不可恢复：400（参数错误）、401（鉴权失败）、403（权限不足）
 *  - 不可恢复错误不触发切换（换 Provider 也解决不了）
 */
class FallbackProvider(
    private val providers: List<LLMProvider>
) : LLMProvider {

    private val log = LoggerFactory.getLogger(FallbackProvider::class.java)
    private var activeIndex = 0
    private var lastFailbackCheck = 0L
    private val failbackIntervalMs = 5 * 60 * 1000L  // 5 分钟尝试回切

    init {
        require(providers.isNotEmpty()) { "至少需要一个 LLMProvider" }
    }

    override val name: String
        get() = providers[activeIndex].name + if (activeIndex > 0) " (fallback)" else ""

    /** 当前活跃 Provider 的索引和名称（日志/状态展示用）。 */
    fun activeInfo(): String = "[$activeIndex] ${providers[activeIndex].name}"

    /** 所有 Provider 名称列表。 */
    fun providerNames(): List<String> = providers.map { it.name }

    override fun chat(systemPrompt: String, messages: List<LLMMessage>, tools: List<AgentTool>): LLMResponse {
        // 定期尝试切回主 Provider
        maybeFailback()

        var lastException: Exception? = null
        // 从当前活跃 Provider 开始尝试
        for (attempt in 0 until providers.size) {
            val idx = (activeIndex + attempt) % providers.size
            val provider = providers[idx]
            try {
                val response = provider.chat(systemPrompt, messages, tools)
                // 成功 → 确保 activeIndex 指向这个 Provider
                if (idx != activeIndex) {
                    log.info("LLM 已切回 [{}] {}", idx, provider.name)
                    activeIndex = idx
                }
                return response
            } catch (e: Exception) {
                lastException = e
                if (!isRecoverable(e)) {
                    // 不可恢复错误：不切换，直接抛
                    log.warn("LLM [{}] {} 不可恢复错误：{}", idx, provider.name, e.message)
                    throw e
                }
                // 可恢复错误：切下一个
                log.warn("LLM [{}] {} 可恢复错误：{}，尝试切换", idx, provider.name, e.message)
                val nextIdx = (idx + 1) % providers.size
                if (nextIdx != activeIndex) {
                    activeIndex = nextIdx
                    log.info("LLM 切换到 [{}] {}", nextIdx, providers[nextIdx].name)
                }
            }
        }
        // 所有 Provider 都失败了
        throw LLMException("所有 LLM Provider 均不可用（已尝试 ${providers.size} 个）", lastException)
    }

    /** 定期尝试切回主 Provider。 */
    private fun maybeFailback() {
        if (activeIndex == 0) return  // 已经是主 Provider
        val now = System.currentTimeMillis()
        if (now - lastFailbackCheck < failbackIntervalMs) return
        lastFailbackCheck = now

        // 尝试用主 Provider 发一个轻量请求
        try {
            val primary = providers[0]
            primary.chat("ping", listOf(LLMMessage.User("hi")))
            log.info("主 LLM [0] {} 已恢复，切回", primary.name)
            activeIndex = 0
        } catch (e: Exception) {
            if (isRecoverable(e)) {
                log.debug("主 LLM 仍不可用：{}", e.message)
            } else {
                // 不可恢复错误说明主 Provider 配置有问题，不反复重试
                log.warn("主 LLM 不可恢复错误，停止尝试回切：{}", e.message)
                lastFailbackCheck = now + failbackIntervalMs * 10  // 延长到 50 分钟后再试
            }
        }
    }

    companion object {
        /**
         * 判断 LLM 异常是否可恢复（切下一个 Provider 可能解决）。
         * 不可恢复错误（鉴权/参数）换 Provider 也解决不了，不切。
         */
        fun isRecoverable(e: Exception): Boolean {
            val msg = e.message?.lowercase() ?: ""
            return when {
                // HTTP 状态码
                msg.contains("429") -> true     // 额度/速率限制
                msg.contains("503") -> true     // 服务过载
                msg.contains("502") -> true     // 网关错误
                msg.contains("500") -> true     // 服务端内部错误
                // 超时/连接
                msg.contains("timeout") -> true
                msg.contains("timed out") -> true
                msg.contains("connection") -> true
                msg.contains("connect") -> true
                msg.contains("eofexception") -> true
                // 额度相关关键词
                msg.contains("quota") -> true
                msg.contains("rate limit") -> true
                msg.contains("insufficient") -> true
                msg.contains("exceeded") -> true
                // 不可恢复
                msg.contains("401") -> false    // 鉴权失败
                msg.contains("403") -> false    // 权限不足
                msg.contains("400") -> false    // 参数错误
                msg.contains("invalid") -> false
                msg.contains("unauthorized") -> false
                // 未知错误保守处理：可恢复
                else -> true
            }
        }
    }
}

/** LLM 异常（区分可恢复/不可恢复）。 */
class LLMException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
