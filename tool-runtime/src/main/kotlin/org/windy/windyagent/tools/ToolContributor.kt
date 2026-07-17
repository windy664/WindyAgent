package org.windy.windyagent.tools

/**
 * A named source of tools. The name is used for logging and as a natural
 * grouping boundary, while [isAvailable] lets optional integrations disappear
 * cleanly when their dependency is absent or disabled.
 */
interface ToolContributor {
    val name: String

    fun isAvailable(): Boolean = true

    fun tools(): List<AgentTool>
}

class SimpleToolContributor(
    override val name: String,
    private val available: () -> Boolean = { true },
    private val supplier: () -> List<AgentTool>
) : ToolContributor {
    constructor(name: String, supplier: () -> List<AgentTool>) : this(name, { true }, supplier)
    override fun isAvailable(): Boolean = available()
    override fun tools(): List<AgentTool> = supplier()
}
