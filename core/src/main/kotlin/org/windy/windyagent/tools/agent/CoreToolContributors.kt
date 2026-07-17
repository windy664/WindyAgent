package org.windy.windyagent.tools.agent

import org.windy.windyagent.tools.SimpleToolContributor
import org.windy.windyagent.tools.ToolContributor
import org.windy.windyagent.tools.LlmUsageTool
import org.windy.windyagent.knowledge.KnowledgeManager
import org.windy.windyagent.knowledge.KnowledgeSearchTool
import org.windy.windyagent.knowledge.KnowledgeWriteTool
import org.windy.windyagent.llm.LLMUsageTracker
import org.windy.windyagent.mcp.McpLoader
import org.windy.windyagent.mcp.McpServerConfig
import org.windy.windyagent.memory.LongTermMemory
import org.windy.windyagent.memory.RememberTool
import org.windy.windyagent.rag.LlmQueryExpander

/**
 * 平台无关的**核心工具贡献者**：knowledge / usage / memory / mcp —— 这几组在 Velocity 中心与 Bukkit 嵌入式
 * 两个 runner 里构造方式完全一致，过去各手写一遍 `extraTools += X`（加一个核心工具要改两处）。
 *
 * 收敛到这里后，两个 runner 都 `extraTools += ToolAssembly.assemble(CoreToolContributors.of(...))`，
 * 加/改核心工具只改一处；平台特有工具（本地 vs 远程、技能、能力目录等）仍由各 runner 自行装配。
 *
 * 依赖为 null / 空时对应贡献者 [org.windy.windyagent.tools.ToolContributor.isAvailable] 为 false，装配时零开销跳过——等价于原来的 `?.let`。
 */
object CoreToolContributors {

    fun of(
        knowledge: KnowledgeManager,
        expander: LlmQueryExpander?,
        ragMinHits: Int,
        usageTracker: LLMUsageTracker?,
        memory: LongTermMemory?,
        mcpServers: List<McpServerConfig>
    ): List<ToolContributor> = listOf(
        // 知识库检索 + 写入（沉淀）
        SimpleToolContributor("knowledge") {
            listOf(KnowledgeSearchTool(knowledge, expander, ragMinHits), KnowledgeWriteTool(knowledge))
        },
        // LLM token 用量 / 预算（成本可见性）
        SimpleToolContributor("usage", available = { usageTracker != null }) {
            listOf(LlmUsageTool(usageTracker!!))
        },
        // 长期记忆写入
        SimpleToolContributor("memory", available = { memory != null }) {
            listOf(RememberTool(memory!!))
        },
        // 外部 MCP server 暴露的工具（可选）
        SimpleToolContributor("mcp", available = { mcpServers.isNotEmpty() }) {
            McpLoader.load(mcpServers)
        }
    )
}
