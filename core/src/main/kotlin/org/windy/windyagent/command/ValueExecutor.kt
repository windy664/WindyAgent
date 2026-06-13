package org.windy.windyagent.command

import org.windy.windyagent.safety.TrustLevel

/**
 * `value` 命令的执行后端（载体相关）。
 *  - Velocity 上是 [远端转发]：`value <子命令> <子服> [参数]` → 总线派发到子服；
 *  - Bukkit 上是 [本地直跑]：`value <子命令> [参数]` → 直接调本服估值引擎。
 * 命令解析（拆子命令）在 ValueCommand 里做一次；server 名/参数的进一步解析由各实现负责，
 * 因为「是否需要子服名」正是两种载体的唯一区别。
 */
interface ValueExecutor {
    /** @param sub build/get/set/unset/orphans/status/servers；@param rest 子命令后的剩余原始参数。 */
    fun execute(sub: String, rest: String, trust: TrustLevel): String

    /** 写类子命令需管理员。 */
    fun requiresTrusted(sub: String): Boolean = sub in WRITE_SUBS

    companion object {
        val WRITE_SUBS = setOf("build", "set", "unset", "llm")
        val ALL_SUBS = listOf("build", "llm", "get", "set", "unset", "orphans", "status", "servers")
    }
}
