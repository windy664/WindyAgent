package org.windy.windyagent.agent

import org.slf4j.LoggerFactory
import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.llm.LLMProvider

/**
 * 复杂多步任务：先让 LLM 产出一份计划，再带着计划跑工具循环逐步执行。
 *
 * 取舍：不为每个步骤单独开一轮 LLM（成本翻倍、上下文割裂），而是
 * 「规划一次 + 连续执行」——计划作为执行循环的额外纪律注入系统提示，
 * 模型按清单顺序推进、用工具落地，最后统一汇报。
 *
 * 规划阶段**不提供工具**：避免模型在"想清楚要做什么"之前就误触发带副作用的操作。
 */
class PlanExecuteAgent(
    private val llmProvider: LLMProvider,
    private val maxIterations: Int = 16,
    private val failureDetector: FailureDetector? = null,
    private val toolResultCache: ToolResultCache? = null,
    private val selfChecker: SelfChecker? = null,
    private val trajectoryRecorder: TrajectoryRecorder? = null,
    private val onToolCall: ((String, Long, Boolean) -> Unit)? = null
) : Agent {
    override val name = "plan-execute"
    private val logger = LoggerFactory.getLogger(PlanExecuteAgent::class.java)

    override fun run(context: AgentContext): AgentResponse {
        val plan = makePlan(context)
        logger.info("Plan for session {}:\n{}", context.sessionId, plan)

        val systemPrompt = buildString {
            append(context.platform.systemPrompt)
            append("\n\n").append(EXECUTE_GUIDE)
            append("\n\n当前任务计划：\n").append(plan)
        }

        val messages = context.history.toMutableList()
        messages += LLMMessage.User(userMessageWithMemory(context))

        val response = toolLoop(
            llmProvider, systemPrompt, messages, context.effectiveTools, maxIterations,
            failureDetector, toolResultCache, selfChecker, trajectoryRecorder,
            context.sessionId, context.userMessage, onToolCall, context.onStep
        )

        context.syncHistory(messages)
        return response
    }

    /** 规划阶段：纯文本步骤清单，不提供工具。失败或空白时退化为「直接完成」。 */
    private fun makePlan(context: AgentContext): String {
        val planMessages = listOf<LLMMessage>(
            LLMMessage.User("$PLAN_PROMPT\n\n用户请求：${context.userMessage}")
        )
        val text = runCatching {
            llmProvider.chat(context.platform.systemPrompt, planMessages).textContent
        }.getOrNull()
        return text?.trim().orEmpty().ifBlank { "1. 直接完成用户的请求" }
    }

    companion object {
        private val PLAN_PROMPT = """
            请把下面的用户请求拆解成一份简短、可执行的步骤清单（每行一步，用数字编号）。
            只输出步骤本身，不要执行、不要解释、不要调用任何工具。
            如果请求其实很简单，给出一两步即可。
        """.trimIndent()

        private val EXECUTE_GUIDE = """
            你正在按既定计划执行一个多步任务，请依照下面的「当前任务计划」推进：
            - 按顺序完成每一步，需要时调用相应工具。
            - 一步完成后再进行下一步，不要跳步、不要提前收尾。
            - 全部步骤完成后，用简洁的简体中文向用户汇报最终结果。
        """.trimIndent()
    }
}
