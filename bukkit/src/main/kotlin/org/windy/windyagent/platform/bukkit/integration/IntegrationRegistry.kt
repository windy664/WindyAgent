package org.windy.windyagent.platform.bukkit.integration

import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.agent.ToolAssembly
import org.windy.windyagent.agent.ToolContributor
import org.windy.windyagent.platform.bukkit.cmi.CmiIntegration
import org.windy.windyagent.safety.AuditLog

/**
 * 插件集成注册表：自动发现已安装插件，加载对应集成，返回所有插件工具。
 *
 * 每个 [PluginIntegration] 本质就是一个 [ToolContributor]（插件即来源/分类），故这里直接把它们
 * 适配成贡献者交给 [ToolAssembly] 统一装配——与核心工具走同一套装配口。
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

    /** 把插件集成适配成通用工具贡献者（插件名=来源名，可用性=插件是否安装）。 */
    private fun PluginIntegration.asContributor(audit: AuditLog): ToolContributor {
        val integ = this
        return object : ToolContributor {
            override val name = integ.pluginName
            override fun isAvailable() = integ.isAvailable()
            override fun tools() = integ.createTools(audit)
        }
    }

    /** 已注册集成作为贡献者清单（供 runner 并入统一装配，或单独 [discoverAndRegister]）。 */
    fun contributors(plugin: JavaPlugin, audit: AuditLog): List<ToolContributor> =
        allIntegrations(plugin).map { it.asContributor(audit) }

    /**
     * 扫描所有已注册集成，返回已安装插件的工具列表。未安装的插件自动跳过（零开销）。
     */
    fun discoverAndRegister(plugin: JavaPlugin, audit: AuditLog): List<AgentTool> =
        ToolAssembly.assemble(contributors(plugin, audit)) { plugin.logger.info("[Integration] $it") }
}
