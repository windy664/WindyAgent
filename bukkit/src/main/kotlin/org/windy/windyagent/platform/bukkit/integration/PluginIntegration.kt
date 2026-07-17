package org.windy.windyagent.platform.bukkit.integration

import org.windy.windyagent.tools.AgentTool
import org.windy.windyagent.safety.AuditLog

/**
 * 插件集成抽象：每个受支持的插件实现此接口，提供自己的 AgentTool 列表。
 *
 * 架构设计：
 *  - [IntegrationRegistry] 自动发现已安装插件，加载对应集成
 *  - 每个插件一个文件夹（cmi/、essentials/、luckperms/…），互不依赖
 *  - 工具走反射调用插件 API，不硬依赖编译期
 *  - 无插件时零开销（不注册任何工具）
 *
 * 扩展新插件：实现此接口 → 在 [IntegrationRegistry] 注册即可。
 */
interface PluginIntegration {

    /** 插件名（与 plugin.yml 中的 name 一致，如 "CMI"、"Essentials"）。 */
    val pluginName: String

    /** 插件是否已安装且可用。 */
    fun isAvailable(): Boolean

    /**
     * 返回该插件提供的 AgentTool 列表。
     * 调用时机：确认 [isAvailable] 为 true 后才调用。
     */
    fun createTools(audit: AuditLog): List<AgentTool>
}
