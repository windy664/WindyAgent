package org.windy.windyagent.agent

/**
 * 工具装配器：把一组 [ToolContributor] 汇成最终工具列表——过滤不可用的来源、逐来源产出并可选记账。
 *
 * 单一装配口：runner 只需 `extraTools += ToolAssembly.assemble(contributors, log)`，
 * 加工具从"两个 runner 各手写一行"变成"往贡献者清单加一项"。每个来源单独 try 隔离，一个坏了不拖垮全体。
 */
object ToolAssembly {

    /**
     * @param contributors 工具来源清单（顺序即最终工具顺序）。
     * @param log 每个来源产出后的记账回调（如 `plugin.logger::info`）；默认不记。
     */
    fun assemble(contributors: List<ToolContributor>, log: (String) -> Unit = {}): List<AgentTool> {
        val out = ArrayList<AgentTool>()
        for (c in contributors) {
            val available = runCatching { c.isAvailable() }.getOrDefault(false)
            if (!available) continue
            val ts = runCatching { c.tools() }.getOrElse {
                log("[Tools] ${c.name} — 加载失败：${it.message}")
                emptyList()
            }
            if (ts.isNotEmpty()) {
                out += ts
                log("[Tools] ${c.name} — ${ts.size} 个")
            }
        }
        return out
    }
}
