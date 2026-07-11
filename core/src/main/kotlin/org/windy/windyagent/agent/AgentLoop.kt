package org.windy.windyagent.agent

import org.slf4j.LoggerFactory
import org.windy.windyagent.Messages
import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.llm.LLMProvider
import org.windy.windyagent.llm.LLMResponse
import org.windy.windyagent.llm.ToolResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

private val loopLogger = LoggerFactory.getLogger("org.windy.windyagent.agent.ToolLoop")
/** 工具并行执行线程池（守护线程，不阻塞 JVM 退出）。 */
private val toolPool = Executors.newCachedThreadPool { r -> Thread(r, "windyagent-tool").apply { isDaemon = true } }

/**
 * 通用 ReAct 工具循环：反复调用 LLM，遇到 TOOL_USE 就执行工具并把结果回灌，
 * 直到模型 END_TURN 或达到 [maxIterations] 上限。
 *
 * 工具调用并行执行：同一轮多个 tool call 用线程池并发，缩短总等待时间。
 * 结果按原始顺序回灌（LLM 期望顺序一致）。
 *
 * 被 [ReActAgent] 与 [PlanExecuteAgent] 共用——前者直接跑一轮，后者带着计划跑。
 * [messages] 会被原地追加，便于调用方据此同步会话历史。
 *
 * 可选增强组件（均为 null 时退化为原始行为）：
 * - [failureDetector]：检测循环/反复失败/累计超限 → 提前中止
 * - [toolResultCache]：相同工具+参数短时间内不重复调用
 * - [selfChecker]：回复前检查幻觉/泄露/完整性
 * - [trajectoryRecorder]：记录交互轨迹（可导出 SFT 训练数据）
 */
internal fun toolLoop(
    llmProvider: LLMProvider,
    systemPrompt: String,
    messages: MutableList<LLMMessage>,
    tools: List<AgentTool>,
    maxIterations: Int,
    failureDetector: FailureDetector? = null,
    toolResultCache: ToolResultCache? = null,
    selfChecker: SelfChecker? = null,
    trajectoryRecorder: TrajectoryRecorder? = null,
    sessionId: String = "",
    userMessage: String = "",
    onToolCall: ((String, Long, Boolean) -> Unit)? = null,
    onStep: ((String, Boolean, Long) -> Unit)? = null
): AgentResponse {
    val executedTools = mutableListOf<String>()
    // 每次 toolLoop 调用重置检测器（跨请求不累积）
    failureDetector?.reset()
    val trajectory = trajectoryRecorder?.start(sessionId, userMessage)

    for (i in 0 until maxIterations) {
        loopLogger.debug("tool loop iteration {}, messages: {}", i + 1, messages.size)

        // 发给 LLM 前做 tool_calls/ToolResults 配对清洗：OpenAI 协议要求带 tool_calls 的 assistant
        // 消息后必须紧跟覆盖每个 tool_call_id 的 tool 结果，否则 400。历史若因裁剪/并发/异常损坏
        // 出现"孤儿 tool_calls 或孤儿 ToolResults"，这里在最后一道关口剔除，保证请求始终合法。
        val response = llmProvider.chat(systemPrompt, sanitizeToolPairing(messages), tools)

        when (response.stopReason) {
            LLMResponse.StopReason.END_TURN -> {
                var text = response.textContent ?: ""
                // SelfChecker：回复前检查质量
                if (selfChecker != null && executedTools.isNotEmpty()) {
                    val corrected = selfChecker.check(userMessage, text, executedTools)
                    if (corrected != null) text = corrected
                }
                messages += LLMMessage.Assistant(text)
                trajectory?.finish(text, true)
                return AgentResponse(text, true, executedTools)
            }
            LLMResponse.StopReason.TOOL_USE -> {
                messages += LLMMessage.Assistant(response.textContent, response.toolCalls)
                // 并行执行所有 tool call（保持顺序回灌）
                val calls = response.toolCalls
                val ctxSnapshot = org.windy.windyagent.safety.RequestContext.snapshot() // 跨线程传递信任级别
                val futures = calls.map { tc ->
                    loopLogger.info("Tool call: {} args={}", tc.name, tc.inputJson.take(200))
                    executedTools += tc.name
                    CompletableFuture.supplyAsync({
                        org.windy.windyagent.safety.RequestContext.restore(ctxSnapshot) // 恢复本次请求信任级别，否则并行线程默认 UNTRUSTED
                        try {
                        // ToolResultCache：命中则直接返回
                        val cached = toolResultCache?.get(tc.name, tc.inputJson)
                        if (cached != null) {
                            loopLogger.info("Tool cache hit: {}", tc.name)
                            return@supplyAsync cached
                        }
                        val startMs = System.currentTimeMillis()
                        val r = tools.find { it.name == tc.name }
                            ?.execute(tc.id, tc.inputJson)
                            ?: ToolResult.error(tc.id, "Tool not found: ${tc.name}")
                        val latencyMs = System.currentTimeMillis() - startMs
                        loopLogger.info("Tool result: {} -> {}{}", tc.name, if (r.isError) "[ERROR] " else "", r.content.take(300))
                        // ToolResultCache：缓存成功结果
                        toolResultCache?.put(tc.name, tc.inputJson, r)
                        // FailureDetector：记录并检测
                        if (failureDetector != null) {
                            val verdict = failureDetector.record(tc.name, tc.inputJson, r)
                            if (verdict != FailureDetector.Verdict.OK) {
                                loopLogger.warn("FailureDetector: {}", verdict)
                            }
                        }
                        // TrajectoryRecorder：记录工具调用
                        trajectory?.recordToolCall(tc.name, tc.inputJson, r, latencyMs)
                        // SystemHealth 回调（全局统计）
                        onToolCall?.invoke(tc.name, latencyMs, !r.isError)
                        // 过程回调（本次请求，供流式对话实时展示工具调用过程）。并行线程内调用，
                        // 实现方（如 SSE 写帧）需自负线程安全。
                        onStep?.invoke(stepLabel(tc.name, tc.inputJson), !r.isError, latencyMs)
                        r
                        } finally { org.windy.windyagent.safety.RequestContext.clear() }
                    }, toolPool)
                }
                // 等全部完成，按原始顺序收集结果
                val results = futures.map { it.join() }
                messages += LLMMessage.ToolResults(results)

                // FailureDetector：检测是否需要提前中止
                if (failureDetector != null) {
                    val lastVerdict = failureDetector.callCount().let {
                        // 重新检查最后一个 verdict（已在并行块中 record 过）
                        // 这里用简化逻辑：检查连续失败
                        val recentCalls = executedTools.takeLast(4)
                        if (recentCalls.size >= 4 && recentCalls.toSet().size == 1) FailureDetector.Verdict.LOOP
                        else FailureDetector.Verdict.OK
                    }
                    // 如果并行块中已检测到问题，提前终止
                    val fdState = failureDetector.let { fd ->
                        // 用 callCount 做粗粒度检查
                        if (fd.callCount() > 15) FailureDetector.Verdict.TOO_MANY_CALLS
                        else null
                    }
                    if (fdState == FailureDetector.Verdict.TOO_MANY_CALLS) {
                        val msg = Messages.t("agent.too_many_calls", failureDetector.callCount())
                        trajectory?.finish(msg, false)
                        return AgentResponse(msg, false, executedTools)
                    }
                }
            }
            else -> {
                val msg = Messages.t("agent.stopped", response.stopReason ?: "")
                trajectory?.finish(msg, false)
                return AgentResponse(msg, false, executedTools)
            }
        }
    }

    val msg = Messages.t("agent.max_iter", maxIterations)
    trajectory?.finish(msg, false)
    return AgentResponse(msg, false, executedTools)
}

/** 把循环后的完整消息列表写回会话历史。 */
private val stepLabelMapper = com.fasterxml.jackson.databind.ObjectMapper()

/** 过程标签：run_skill_on_server/run_skill 从 inputJson 提取 skill 名 → "skill:<名>"(前端高亮🧪)；其余原样。 */
internal fun stepLabel(toolName: String, inputJson: String): String {
    if (toolName == "run_skill_on_server" || toolName == "run_skill") {
        val skill = runCatching { stepLabelMapper.readTree(inputJson)?.get("skill")?.asText() }.getOrNull()
        if (!skill.isNullOrBlank()) return "skill:$skill"
    }
    return toolName
}

internal fun AgentContext.syncHistory(messages: List<LLMMessage>) {
    history.clear()
    history.addAll(messages)
}

/**
 * 清洗消息序列，保证 tool_calls 与 ToolResults 严格配对（OpenAI/Anthropic 协议合法）：
 *  - 带 toolCalls 的 Assistant，其后必须紧跟一条覆盖全部 tool_call_id 的 ToolResults——
 *    否则该 assistant 的 toolCalls 是「孤儿」，去掉 toolCalls 只保留文本（无文本则整条丢弃）。
 *  - 未被合法 Assistant(toolCalls) 领起的 ToolResults 是「孤儿」，丢弃。
 * 不改动入参，返回清洗后的新列表。正常历史（本就成对）原样返回。
 */
internal fun sanitizeToolPairing(messages: List<LLMMessage>): List<LLMMessage> {
    val out = ArrayList<LLMMessage>(messages.size)
    var i = 0
    while (i < messages.size) {
        val m = messages[i]
        when {
            m is LLMMessage.Assistant && m.toolCalls.isNotEmpty() -> {
                val next = messages.getOrNull(i + 1)
                val paired = next is LLMMessage.ToolResults &&
                    next.results.map { it.toolCallId }.toSet()
                        .containsAll(m.toolCalls.map { it.id }.toSet())
                if (paired) {
                    out += m; out += next as LLMMessage.ToolResults; i += 2
                } else {
                    if (!m.content.isNullOrBlank()) out += LLMMessage.Assistant(m.content)
                    i += 1
                }
            }
            m is LLMMessage.ToolResults -> i += 1 // 孤儿 ToolResults（配对的已在上面被 i+=2 跳过）
            else -> { out += m; i += 1 }
        }
    }
    return out
}

/**
 * 把召回的长期记忆拼进**当次 user 消息**（而非系统提示）——这样系统提示 + 工具定义保持稳定、
 * 可被 provider 前缀缓存命中（ReAct 多轮/跨请求复用），记忆与新问只落在非缓存尾部。
 */
internal fun userMessageWithMemory(context: AgentContext): String = buildString {
    // 请求者 + 所在子服前言（内核自动注入，玩家无需自报 ID/子服）。
    val who = context.requester.ifBlank { context.sessionId }
    if (who.isNotBlank() && who != "unknown") {
        append("[本次请求者] ").append(who)
        if (context.requesterServer.isNotBlank()) {
            append("（当前所在子服：").append(context.requesterServer).append("）")
        }
        append('\n')
        if (context.requesterServer.isNotBlank()) {
            append("涉及需指定目标子服的操作（run_command_on_server / run_skill_on_server 等），")
            append("若用户未言明是哪个子服，默认就是请求者所在的这个子服；不要反问。\n")
        }
        append('\n')
    }
    if (context.recalled.isNotBlank()) {
        append("[关于当前用户的已知记忆，酌情参考]\n").append(context.recalled).append("\n\n")
    }
    append(context.userMessage)
}
