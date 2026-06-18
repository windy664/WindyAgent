package org.windy.windyagent.agent

import org.windy.windyagent.safety.TrustLevel

/**
 * 工具集分组：把 Agent 工具按用途归类，按场景动态加载。
 *
 * 减少无关工具对 LLM 的干扰（工具定义也占上下文），提升工具选择准确率。
 * `alwaysOn=true` 的工具集始终加载（如核心运维/安全相关）。
 */
data class Toolset(
    val name: String,
    val description: String,
    val tools: List<AgentTool>,
    val alwaysOn: Boolean = false
)

/**
 * 工具集选择器：按请求上下文决定加载哪些 Toolset。
 */
object ToolsetSelector {
    /**
     * 按用户消息和信任级别选择激活的工具集。
     * 策略：alwaysOn 全选 + 按关键词匹配其余。
     */
    fun select(message: String, trust: TrustLevel, all: List<Toolset>): List<AgentTool> {
        val always = all.filter { it.alwaysOn }.flatMap { it.tools }
        val optional = all.filter { !it.alwaysOn }
        if (optional.isEmpty()) return always

        val text = message.lowercase()
        val matched = optional.filter { ts ->
            KEYWORDS[ts.name]?.any { text.contains(it) } == true
        }.flatMap { it.tools }

        // 如果没匹配到任何可选工具集，返回全部（保守：不缺工具）
        return if (matched.isEmpty()) all.flatMap { it.tools } else (always + matched).distinctBy { it.name }
    }

    private val KEYWORDS = mapOf(
        "economy" to listOf("余额", "金币", "钱", "转账", "充值", "礼包", "估值", "物品", "balance", "money", "pay", "value", "item"),
        "knowledge" to listOf("知识", "查", "搜", "问答", "政策", "规则", "knowledge", "search", "wiki"),
        "admin" to listOf("踢", "封禁", "ban", "kick", "op", "权限", "permission"),
        "ops" to listOf("广播", "执行", "命令", "重启", "备份", "broadcast", "run", "command", "restart", "backup"),
        "memory" to listOf("记住", "记忆", "忘", "remember", "forget", "memory")
    )
}
