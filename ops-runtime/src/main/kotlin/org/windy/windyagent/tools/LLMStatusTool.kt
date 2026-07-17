package org.windy.windyagent.tools

import org.windy.windyagent.llm.FallbackProvider
import org.windy.windyagent.llm.LLMProvider
import org.windy.windyagent.llm.ToolResult

/**
 * 让 Agent 查看当前 LLM Provider 状态（哪个在用、有几个备用、是否在降级模式）。
 * 管理员问"用的什么模型""LLM 有没有问题"时调用。
 */
class LLMStatusTool(
    private val provider: LLMProvider
) : AgentTool {

    override val name = "llm_status"
    override val description = "查看当前 LLM Provider 状态（正在用哪个模型、是否有备用、是否在降级模式）"
    override val inputSchema = """{"type":"object","properties":{},"required":[]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult {
        val sb = StringBuilder("🤖 LLM 状态：\n")

        if (provider is FallbackProvider) {
            sb.appendLine("• 模式：故障转移（已启用）")
            sb.appendLine("• 当前活跃：${provider.activeInfo()}")
            sb.appendLine("• Provider 列表：")
            provider.providerNames().forEachIndexed { i, name ->
                val marker = if (i == 0) "（主）" else "（备用$i）"
                sb.appendLine("  [$i] $name $marker")
            }
        } else {
            sb.appendLine("• 模式：单 Provider（无备用）")
            sb.appendLine("• 当前：${provider.name}")
            sb.appendLine("• 提示：可配置 llm.fallback 启用故障转移")
        }

        return ToolResult.success(toolCallId, sb.toString().trimEnd())
    }
}
