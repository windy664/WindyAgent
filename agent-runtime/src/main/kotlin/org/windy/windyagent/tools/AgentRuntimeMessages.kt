package org.windy.windyagent.tools

/**
 * Runtime-local message resolver.
 *
 * tools-runtime must not depend on core's Messages object, so platform/core assembly can inject
 * the real i18n resolver while tests and sub-agents keep a safe default.
 */
fun interface AgentRuntimeMessages {
    fun t(key: String, vararg args: Any?): String

    companion object {
        val Default = AgentRuntimeMessages { key, args ->
            when (key) {
                "tools.too_many_calls" -> "工具调用过多（${args.getOrNull(0)} 次），已中止。"
                "tools.stopped" -> "模型停止：${args.getOrNull(0) ?: ""}"
                "tools.max_iter" -> "达到最大迭代次数 ${args.getOrNull(0)}，已中止。"
                else -> key
            }
        }
    }
}
