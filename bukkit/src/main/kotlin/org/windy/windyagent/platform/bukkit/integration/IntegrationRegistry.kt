package org.windy.windyagent.platform.bukkit.integration

import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.platform.bukkit.cmi.CmiIntegration
import org.windy.windyagent.safety.AuditLog

/**
 * 插件集成注册表：自动发现已安装插件，加载对应集成，返回所有插件工具。
 *
 * 新增插件支持只需两步：
 *  1. 创建 `integration/<插件名>/<Plugin>Integration.kt` 实现 [PluginIntegration]
 *  2. 在下方 [allIntegrations] 列表中注册
 */
object IntegrationRegistry {

    /** 所有已注册的插件集成（新增插件在这里加一行）。 */
    private fun allIntegrations(plugin: JavaPlugin): List<PluginIntegration> = listOf(
        CmiIntegration(plugin)
        // EssentialsIntegration(plugin),   // TODO
        // LuckPermsIntegration(plugin),    // TODO
        // WorldGuardIntegration(plugin),   // TODO
    )

    /**
     * 扫描所有已注册集成，返回已安装插件的工具列表。
     * 未安装的插件自动跳过（零开销）。
     */
    fun discoverAndRegister(plugin: JavaPlugin, audit: AuditLog): List<AgentTool> {
        val tools = mutableListOf<AgentTool>()
        for (integration in allIntegrations(plugin)) {
            if (!integration.isAvailable()) continue
            runCatching {
                val pluginTools = integration.createTools(audit)
                tools.addAll(pluginTools)
                plugin.logger.info("[Integration] ${integration.pluginName} — ${pluginTools.size} 个工具")
            }.onFailure {
                plugin.logger.warning("[Integration] ${integration.pluginName} 加载失败：${it.message}")
            }
        }
        return tools
    }
}
