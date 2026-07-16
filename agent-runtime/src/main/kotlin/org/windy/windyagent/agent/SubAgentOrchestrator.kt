package org.windy.windyagent.agent

import org.slf4j.LoggerFactory
import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.llm.LLMProvider
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * 子 Agent 并行编排器：复杂任务拆成多个独立子任务，并行执行后汇总。
 *
 * 流程：
 * 1. LLM 把用户请求拆成 N 个独立子任务（JSON 数组）
 * 2. 每个子任务用独立的 ReAct Agent 并行执行（隔离上下文）
 * 3. 收集所有结果，LLM 汇总为最终回复
 *
 * 适合：批量操作（"查所有子服状态"）、多维度查询（"查余额+查在线+查最近死亡"）。
 */
class SubAgentOrchestrator(
    private val llm: LLMProvider,
    private val tools: () -> List<AgentTool>,
    private val systemPrompt: String,
    private val messagesResolver: AgentRuntimeMessages = AgentRuntimeMessages.Default
) {
    private val log = LoggerFactory.getLogger(SubAgentOrchestrator::class.java)
    private val pool = Executors.newFixedThreadPool(4) { r -> Thread(r, "sub-agent").apply { isDaemon = true } }

    data class SubTask(val id: String, val description: String, val tools: List<String>)
    data class SubResult(val id: String, val description: String, val result: String, val success: Boolean)

    /**
     * 判断是否应该并行化。返回 null=不并行（单 Agent 更好），非 null=子任务列表。
     */
    fun plan(userMessage: String): List<SubTask>? {
        if (userMessage.length < 30) return null // 太短不值得拆

        val prompt = """
            判断以下用户请求是否可以拆成多个独立的子任务并行执行。
            如果可以，输出 JSON 数组，每项 {"id":"1","desc":"子任务描述","tools":["tool1"]}.
            如果不适合并行（有依赖关系或太简单），输出 "NO".

            用户请求：$userMessage
        """.trimIndent()

        return runCatching {
            val result = llm.chat("你是任务分解器，只输出 JSON 或 NO。", listOf(LLMMessage.User(prompt))).textContent?.trim()
            if (result == null || result.equals("NO", ignoreCase = true)) return null
            val arr = com.fasterxml.jackson.databind.ObjectMapper().readTree(result)
            if (!arr.isArray || arr.size() < 2) return null
            arr.mapIndexed { i, node ->
                SubTask(
                    id = node["id"]?.asText() ?: "${i + 1}",
                    description = node["desc"]?.asText() ?: "",
                    tools = node["tools"]?.map { it.asText() } ?: emptyList()
                )
            }
        }.getOrNull()
    }

    /**
     * 并行执行子任务，汇总结果。
     */
    fun execute(subTasks: List<SubTask>): String {
        log.info("并行执行 {} 个子任务", subTasks.size)
        val allTools = tools()

        val futures = subTasks.map { task ->
            CompletableFuture.supplyAsync({
                runCatching {
                    val agent = ReActAgent(llm, maxIterations = 5, messagesResolver = messagesResolver)
                    val ctx = AgentContext(
                        sessionId = "sub-${task.id}",
                        userMessage = task.description,
                        platform = object : org.windy.windyagent.platform.Platform {
                            override val name = "sub-agent"
                            override val tools = allTools.filter { task.tools.isEmpty() || it.name in task.tools }
                            override var personality = ""
                            override fun sendResponse(sessionId: String, message: String) {}
                        }
                    )
                    val response = agent.run(ctx)
                    SubResult(task.id, task.description, response.message, response.success)
                }.getOrElse {
                    SubResult(task.id, task.description, "子任务执行失败：${it.message}", false)
                }
            }, pool)
        }

        val results = futures.map { it.get() }

        // 汇总
        return summarize(results)
    }

    private fun summarize(results: List<SubResult>): String {
        val prompt = """
            汇总以下子任务结果为一段简洁的最终回复：
            ${results.joinToString("\n") { "[子任务 ${it.id}] ${it.description} → ${if (it.success) "成功" else "失败"}：${it.result.take(300)}" }}

            要求：
            1. 合并所有成功结果，失败的单独指出
            2. 去除重复信息
            3. 用简洁的中文回复
        """.trimIndent()

        return runCatching {
            llm.chat("你是结果汇总器。", listOf(LLMMessage.User(prompt))).textContent?.trim()
        }.getOrNull() ?: results.joinToString("\n") { "${it.description}: ${it.result}" }
    }
}
