package org.windy.windyagent.tools

/**
 * Assembles tools from ordered contributors. Each contributor is isolated so one
 * broken optional integration does not prevent other tools from loading.
 */
object ToolAssembly {

    fun assemble(contributors: List<ToolContributor>, log: (String) -> Unit = {}): List<AgentTool> {
        val out = ArrayList<AgentTool>()
        for (contributor in contributors) {
            val available = runCatching { contributor.isAvailable() }.getOrDefault(false)
            if (!available) continue
            val tools = runCatching { contributor.tools() }.getOrElse {
                log("[Tools] ${contributor.name} — 加载失败：${it.message}")
                emptyList()
            }
            if (tools.isNotEmpty()) {
                out += tools
                log("[Tools] ${contributor.name} — ${tools.size} 个")
            }
        }
        return out
    }
}
