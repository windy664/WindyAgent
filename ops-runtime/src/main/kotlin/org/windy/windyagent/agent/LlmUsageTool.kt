package org.windy.windyagent.agent

import org.windy.windyagent.llm.LLMUsageTracker
import org.windy.windyagent.llm.ToolResult

/**
 * 让 Agent 查看 LLM 的 token 用量与成本熔断进度（只读）。
 *
 * 基建（[LLMUsageTracker]：token 统计 + 每日预算熔断）原本只暴露给管理员命令 `/ai usage` 与 WebUI，
 * 模型自身看不到、也答不了"今天用了多少 token"。这里包成 AgentTool，补上模型侧的成本可见性/自觉。
 */
class LlmUsageTool(private val tracker: LLMUsageTracker) : AgentTool {

    override val name = "get_llm_usage"
    override val description =
        "查看 AI(LLM) 的 token 用量与成本熔断进度：今日已用/每日预算/剩余、累计调用数与累计 token、平均延迟。" +
        "管理员问“今天用了多少 token、花了多少、还剩多少额度、AI 会不会被限流”时调用。只读。"
    override val inputSchema = """{"type":"object","properties":{},"required":[]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val (usedToday, budget) = tracker.budgetStatus()
        val s = tracker.summary()
        val sb = StringBuilder("📊 LLM 用量：\n")
        if (budget > 0) {
            val remain = (budget - usedToday).coerceAtLeast(0)
            val pct = (usedToday.toDouble() / budget * 100).toInt()
            sb.appendLine("• 今日成本熔断：已用 $usedToday / 预算 $budget tokens（$pct%），剩余 $remain")
        } else {
            sb.appendLine("• 今日已用：$usedToday tokens（未设每日预算，成本熔断关闭）")
        }
        sb.appendLine("• 累计调用：${s.totalCalls} 次")
        sb.appendLine("• 累计 token：输入 ${s.totalInputTokens} + 输出 ${s.totalOutputTokens} = ${s.totalInputTokens + s.totalOutputTokens}")
        if (s.totalCalls > 0) sb.appendLine("• 平均延迟：${s.totalLatencyMs / s.totalCalls} ms/次")
        ToolResult.success(toolCallId, sb.toString().trimEnd())
    }.getOrElse { ToolResult.error(toolCallId, "查询 LLM 用量失败：${it.message}") }
}
