package org.windy.windyagent.bus

/**
 * 能力目录：子服把自己「真实已装的命令」整理成目录，启动时/插件变更时主动推回中心。
 *
 * 取代旧的 per-query 实时自省（每次提问都跨进程现扫）。中心收齐各子服目录后建本地索引，
 * Agent 在中心本地检索、零往返。DTO 放 bus 模块，与 [ToolRequest]/[ToolReply] 同级、两端共用。
 */
data class CapabilityCommand(
    val name: String = "",
    val aliases: List<String> = emptyList(),
    val description: String = "",
    /** 来源插件名；原版/模组命令为 "原版/模组" */
    val source: String = ""
)

data class CapabilityCatalog(
    val server: String = "",
    val commands: List<CapabilityCommand> = emptyList(),
    val builtAt: Long = 0L
)
