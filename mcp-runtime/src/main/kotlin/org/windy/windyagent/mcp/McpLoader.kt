package org.windy.windyagent.mcp

import org.slf4j.LoggerFactory
import org.windy.windyagent.tools.AgentTool

/**
 * 按配置接入 MCP server：逐个握手、拉 tools/list、把每个工具包成 [McpToolAdapter]。
 * 单个 server 失败只记日志并跳过，不影响其它 server 与本地工具。
 */
object McpLoader {
    private val log = LoggerFactory.getLogger(McpLoader::class.java)

    fun load(servers: List<McpServerConfig>): List<AgentTool> {
        if (servers.isEmpty()) return emptyList()
        val tools = mutableListOf<AgentTool>()
        for (s in servers) {
            runCatching {
                val client = McpClient(s.url, s.headers)
                client.initialize()
                val defs = client.listTools()
                defs.forEach { tools += McpToolAdapter(client, it) }
                log.info("MCP server '{}' 已接入 — {} 个工具", s.name, defs.size)
            }.onFailure { log.warn("MCP server '{}' 接入失败，已跳过：{}", s.name, it.message) }
        }
        return tools
    }
}
