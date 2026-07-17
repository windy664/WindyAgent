package org.windy.windyagent.tools

import org.slf4j.LoggerFactory
import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.llm.LLMProvider

/**
 * 自我检查器：Agent 回复前自动检查质量。
 *
 * 检查维度：
 * 1. 完整性 — 是否回答了用户的问题
 * 2. 幻觉 — 是否引用了不存在的工具/玩家/数据
 * 3. 泄露 — 是否暴露了内部系统信息
 * 4. 安全 — 是否建议了危险操作而未警告
 *
 * 用 fastLlm 做轻量检查，只在可疑时才修正。
 */
class SelfChecker(private val llm: LLMProvider) {
    private val log = LoggerFactory.getLogger(SelfChecker::class.java)

    /**
     * 检查回复质量。返回 null=通过，非 null=修正后的回复。
     */
    fun check(userMessage: String, draftReply: String, toolsUsed: List<String>): String? {
        // 快速规则检查（零 LLM 成本）
        ruleCheck(draftReply)?.let { return it }

        // LLM 深度检查（只在回复较长/涉及工具时触发）
        if (draftReply.length < 50 || toolsUsed.isEmpty()) return null
        return llmCheck(userMessage, draftReply, toolsUsed)
    }

    /** 规则检查：内部信息泄露、危险模式。 */
    private fun ruleCheck(reply: String): String? {
        // 检测内部信息泄露
        val leakPatterns = listOf(
            "api-key", "api_key", "apikey", "token", "password", "密码",
            "jdbc:", "redis://", "mongodb://",
            "class ", "Exception", "StackTrace", "at org.windy"
        )
        for (p in leakPatterns) {
            if (reply.contains(p, ignoreCase = true)) {
                log.warn("检测到可能的内部信息泄露：包含 '{}'", p)
                return reply.replace(Regex("(?i)$p[^\n]{0,50}"), "[已过滤]")
            }
        }
        return null
    }

    /** LLM 深度检查：幻觉/完整性/安全。 */
    private fun llmCheck(userMessage: String, draftReply: String, toolsUsed: List<String>): String? {
        val prompt = """
            检查以下 AI 回复的质量问题，如有问题返回修正后的回复，如无问题返回 "OK"。

            用户问题：${userMessage.take(300)}
            AI 回复：${draftReply.take(500)}
            使用的工具：${toolsUsed.joinToString(", ")}

            检查维度：
            1. 是否回答了用户的问题（不要答非所问）
            2. 是否有幻觉（引用了不存在的工具/玩家/数据）
            3. 是否建议了危险操作而未警告
            4. 语言是否与用户一致（用户用英文就回英文）

            只输出修正后的回复，或 "OK"。
        """.trimIndent()

        return runCatching {
            val result = llm.chat("你是回复质量检查器。", listOf(LLMMessage.User(prompt))).textContent?.trim()
            if (result == null || result.equals("OK", ignoreCase = true) || result.length > draftReply.length * 2) null
            else {
                log.info("SelfChecker 修正：原 {} 字 → 修正 {} 字", draftReply.length, result.length)
                result
            }
        }.getOrNull()
    }
}
