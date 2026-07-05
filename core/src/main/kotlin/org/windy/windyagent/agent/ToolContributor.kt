package org.windy.windyagent.agent

/**
 * 工具贡献者：一个「工具来源」的统一抽象——把一组 [AgentTool] 连同它的可用性判断打包起来。
 *
 * 这是 WindyAgent 加工具的**单一扩展口**，取代零散的 `extraTools += X`：
 *  - [name] 既是日志标识，也**天然充当分类**（域/来源），故无需给每个 AgentTool 加 category 字段；
 *  - [isAvailable] 让"依赖某插件/某开关"的来源在不满足时零开销跳过（沿用 PluginIntegration 的思路）；
 *  - [tools] 惰性产出该来源的工具（仅在 [isAvailable] 为真时被调用）。
 *
 * 现有的 `PluginIntegration`（每插件一组工具）本就是它的一个特例；MCP、技能、平台本地工具都可收编为贡献者。
 * 未来若要"按信任分权 / 按消息只上相关域"，在**贡献者粒度**过滤即可（一个有意义的闸），不必回到 tool 级元数据。
 */
interface ToolContributor {
    /** 来源/域名（如 "knowledge"、"CMI"、"skills"）；作日志标识与天然分类。 */
    val name: String

    /** 该来源当前是否可用（依赖未满足则返回 false，[tools] 不会被调用）。默认恒可用。 */
    fun isAvailable(): Boolean = true

    /** 产出该来源提供的工具。仅在 [isAvailable] 为真时调用。 */
    fun tools(): List<AgentTool>
}

/**
 * 把「名字 + 工具产出闭包」直接包成贡献者的轻量适配器——用于收编现有的零散注册，无需为每组新建一个类。
 * 例：`SimpleToolContributor("knowledge") { listOf(KnowledgeSearchTool(...), KnowledgeWriteTool(...)) }`
 */
class SimpleToolContributor(
    override val name: String,
    private val available: () -> Boolean = { true },
    private val supplier: () -> List<AgentTool>
) : ToolContributor {
    constructor(name: String, supplier: () -> List<AgentTool>) : this(name, { true }, supplier)
    override fun isAvailable(): Boolean = available()
    override fun tools(): List<AgentTool> = supplier()
}
