package org.windy.windyagent.agent

import org.slf4j.LoggerFactory
import org.windy.windyagent.llm.ToolResult

/**
 * 失败模式检测器：检测 Agent 是否在重复调同一个工具/陷入循环。
 *
 * 检测策略：
 * 1. 连续 N 次相同工具 + 相同参数 → 循环
 * 2. 连续 N 次工具调用失败 → 反复失败
 * 3. 某工具累计调用超过阈值 → 可能失控
 *
 * 检测到后返回 [Verdict]，调用方据此决定中止/换策略/继续。
 */
class FailureDetector(
    /** 连续相同调用次数阈值。 */
    private val repeatThreshold: Int = 3,
    /** 连续失败次数阈值。 */
    private val failThreshold: Int = 4,
    /** 单工具累计调用上限。 */
    private val toolCallLimit: Int = 15
) {
    private val log = LoggerFactory.getLogger(FailureDetector::class.java)

    private data class Call(val toolName: String, val inputHash: Int, val success: Boolean)

    private val calls = mutableListOf<Call>()
    private val toolCounts = mutableMapOf<String, Int>()

    /** 记录一次工具调用结果。返回检测结论。 */
    fun record(toolName: String, inputJson: String, result: ToolResult): Verdict {
        val hash = inputJson.hashCode()
        val call = Call(toolName, hash, !result.isError)
        calls.add(call)
        toolCounts[toolName] = (toolCounts[toolName] ?: 0) + 1

        // 检测 1：连续相同调用
        if (calls.size >= repeatThreshold) {
            val recent = calls.takeLast(repeatThreshold)
            if (recent.all { it.toolName == toolName && it.inputHash == hash }) {
                log.warn("检测到循环：连续 {} 次相同调用 {} (hash={})", repeatThreshold, toolName, hash)
                return Verdict.LOOP
            }
        }

        // 检测 2：连续失败
        if (calls.size >= failThreshold) {
            val recent = calls.takeLast(failThreshold)
            if (recent.all { !it.success }) {
                log.warn("检测到反复失败：连续 {} 次工具调用失败", failThreshold)
                return Verdict.REPEATED_FAILURE
            }
        }

        // 检测 3：单工具累计超限
        if ((toolCounts[toolName] ?: 0) > toolCallLimit) {
            log.warn("工具 {} 累计调用超过 {} 次", toolName, toolCallLimit)
            return Verdict.TOO_MANY_CALLS
        }

        return Verdict.OK
    }

    fun reset() { calls.clear(); toolCounts.clear() }
    fun callCount(): Int = calls.size

    enum class Verdict {
        /** 正常。 */
        OK,
        /** 检测到循环（连续相同调用）。 */
        LOOP,
        /** 反复失败。 */
        REPEATED_FAILURE,
        /** 单工具累计调用过多。 */
        TOO_MANY_CALLS
    }
}
