package org.windy.windyagent.skill

import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.slf4j.LoggerFactory

/**
 * 工作流执行引擎：按序执行 [SkillDef.steps]，支持变量插值、条件跳转、循环、skill 间调用。
 *
 * 设计原则：
 *  - **纯 Kotlin、不依赖 Bukkit/Platform**（下沉 core）；工具调用通过 [toolFinder] 回调委派。
 *  - Groovy 仅用于条件评估（[WorkflowStep.condition]）和 repeat 表达式，不替代主逻辑。
 *  - 每步执行后通过 [onProgress] 推送进度（前端实时展示用）。
 *  - 失败策略 [WorkflowStep.onFail] 控制：abort / continue / retry。
 *
 * 变量插值：[WorkflowStep.args] 的值中 `{varName}` 会被替换为上下文变量。
 *  - `{params.xxx}` → 调用时传入的原始参数
 *  - `{stepId}`    → 前序步骤的 assign 结果
 *  - `{item}`      → repeat/forEach 当前迭代元素
 *  - 纯 `{var}`    → 整个值替换为变量（保持原始类型）；混搭 `{a}和{b}` → 字符串拼接
 */
class WorkflowEngine(
    /** 查找 Platform 注册的 AgentTool（name → tool；找不到返回 null）。 */
    private val toolFinder: (String) -> AgentToolRef?,
    /** 技能间调用：按 name 查找 SkillDef。 */
    private val skillRegistry: SkillRegistryRef?,
    /** Groovy 脚本/条件执行的 ClassLoader。 */
    private val groovyClassLoader: ClassLoader? = null,
    /** 进度回调（每步开始/完成时推送）。 */
    private val onProgress: ((StepProgress) -> Unit)? = null
) {
    private val log = LoggerFactory.getLogger(WorkflowEngine::class.java)

    /**
     * 执行一个工作流技能。返回 [WorkflowResult]。
     * @param def 技能定义（[SkillDef.steps] 须非空）
     * @param params 调用方传入的原始参数
     */
    fun execute(def: SkillDef, params: Map<String, Any?>): WorkflowResult {
        val steps = def.steps ?: return WorkflowResult(false, "技能「${def.name}」无工作流步骤", emptyMap())
        // 初始上下文：params 扁平 + params.xxx 别名
        val ctx = LinkedHashMap<String, Any?>()
        params.forEach { (k, v) -> ctx[k] = v }
        ctx["params"] = params

        val executed = mutableListOf<String>()
        for (step in steps) {
            val result = executeStep(step, ctx, def.name)
            executed.add(step.id)
            if (!result.success) {
                return WorkflowResult(false, "步骤「${step.name}」失败：${result.message}", ctx, executed)
            }
            // assign：把结果存入上下文
            if (step.assign != null && result.value != null) {
                ctx[step.assign] = result.value
            }
        }
        return WorkflowResult(true, "工作流「${def.name}」执行完成", ctx, executed)
    }

    // ── 单步执行 ──

    private fun executeStep(step: WorkflowStep, ctx: MutableMap<String, Any?>, skillName: String): StepResult {
        // 1. 条件检查
        if (!evalCondition(step.condition, ctx)) {
            onProgress?.invoke(StepProgress(step.id, step.name, StepStatus.SKIPPED, "条件不满足"))
            return StepResult(true, "跳过", null)
        }

        // 2. 循环展开
        val loopItems = resolveLoop(step, ctx)
        if (loopItems != null) {
            return executeLoop(step, loopItems, ctx, skillName)
        }

        // 3. 单次执行（含重试）
        return executeOnce(step, ctx, skillName)
    }

    private fun executeOnce(step: WorkflowStep, ctx: Map<String, Any?>, skillName: String): StepResult {
        val maxAttempts = 1 + step.retryCount
        var lastError: String? = null
        for (attempt in 1..maxAttempts) {
            if (attempt > 1) {
                onProgress?.invoke(StepProgress(step.id, step.name, StepStatus.RETRYING, "重试 $attempt/$maxAttempts"))
            }
            onProgress?.invoke(StepProgress(step.id, step.name, StepStatus.RUNNING, null))
            val r = runCatching { dispatch(step, ctx) }
            if (r.isSuccess) {
                onProgress?.invoke(StepProgress(step.id, step.name, StepStatus.DONE, null))
                return StepResult(true, "完成", r.getOrNull())
            }
            lastError = r.exceptionOrNull()?.message ?: "未知错误"
            log.warn("步骤「{}」第 {} 次执行失败：{}", step.id, attempt, lastError)
        }
        // 所有重试用尽
        onProgress?.invoke(StepProgress(step.id, step.name, StepStatus.FAILED, lastError))
        return when (step.onFail.lowercase().substringBefore(":")) {
            "continue" -> {
                log.info("步骤「{}」失败但 onFail=continue，继续执行", step.id)
                StepResult(true, "失败但继续", null)
            }
            else -> StepResult(false, lastError ?: "执行失败", null)
        }
    }

    private fun executeLoop(step: WorkflowStep, items: List<Any?>, ctx: MutableMap<String, Any?>, skillName: String): StepResult {
        val results = mutableListOf<Any?>()
        for ((i, item) in items.withIndex()) {
            ctx["item"] = item
            ctx["index"] = i
            val r = executeOnce(step, ctx, skillName)
            if (!r.success) return r
            results.add(r.value)
        }
        return StepResult(true, "循环 ${items.size} 次完成", results)
    }

    // ── 动作分派 ──

    private fun dispatch(step: WorkflowStep, ctx: Map<String, Any?>): Any? {
        val args = interpolateArgs(step.args, ctx)
        return when (step.actionType) {
            WorkflowStep.ActionType.TOOL -> dispatchTool(step.tool!!, args)
            WorkflowStep.ActionType.SCRIPT -> dispatchScript(step.script!!, args, ctx)
            WorkflowStep.ActionType.SKILL -> dispatchSkill(step.skill!!, args)
            WorkflowStep.ActionType.NONE -> null
        }
    }

    private fun dispatchTool(toolName: String, args: Map<String, Any?>): String? {
        val tool = toolFinder(toolName)
            ?: throw IllegalStateException("工具「$toolName」未注册")
        val inputJson = MAPPER.writeValueAsString(args)
        val result = tool.execute("wf_${System.nanoTime()}", inputJson)
        if (result.isError) throw IllegalStateException(result.content)
        return result.content
    }

    private fun dispatchScript(script: String, args: Map<String, Any?>, ctx: Map<String, Any?>): Any? {
        val binding = Binding()
        // 注入上下文变量
        ctx.forEach { (k, v) -> binding.setVariable(k, v) }
        args.forEach { (k, v) -> binding.setVariable(k, v) }
        val cl = groovyClassLoader ?: javaClass.classLoader
        val shell = GroovyShell(cl, binding)
        return shell.evaluate(script, "wf_step.groovy")
    }

    private fun dispatchSkill(skillName: String, args: Map<String, Any?>): String? {
        val ref = skillRegistry ?: throw IllegalStateException("技能注册表未接入，无法调用「$skillName」")
        val def = ref.get(skillName)
            ?: throw IllegalStateException("技能「$skillName」不存在")
        return if (def.isWorkflow) {
            // 递归执行工作流
            execute(def, args).let { if (it.success) it.message else throw IllegalStateException(it.message) }
        } else if (def.isScript) {
            // 脚本技能：由调用方自行通过 SkillEngine 执行（这里返回提示）
            "[脚本技能「${def.name}」需在 Bukkit 子服执行，请使用 run_skill_on_server]"
        } else {
            // 纯文字技能：返回正文
            def.textOutput()
        }
    }

    // ── 辅助 ──

    /** 解析循环源：repeat 表达式 或 forEach 变量名。null = 非循环步。 */
    private fun resolveLoop(step: WorkflowStep, ctx: Map<String, Any?>): List<Any?>? {
        if (step.repeat != null) {
            val result = evalExpression(step.repeat, ctx)
            return when (result) {
                is Iterable<*> -> result.toList()
                is Array<*> -> result.toList()
                is Int -> (0 until result).toList()
                is Long -> (0 until result).toList()
                else -> if (result != null) listOf(result) else emptyList()
            }
        }
        if (step.forEach != null) {
            val v = ctx[step.forEach]
            return when (v) {
                is Iterable<*> -> v.toList()
                is Array<*> -> v.toList()
                else -> if (v != null) listOf(v) else emptyList()
            }
        }
        return null
    }

    /** 评估条件表达式；null 或空串 → 无条件通过。 */
    private fun evalCondition(expr: String?, ctx: Map<String, Any?>): Boolean {
        if (expr.isNullOrBlank()) return true
        val result = evalExpression(expr, ctx)
        return when (result) {
            is Boolean -> result
            is Number -> result.toDouble() != 0.0
            is String -> result.isNotBlank()
            null -> false
            else -> true
        }
    }

    /** 用 GroovyShell 评估一个表达式（条件 / repeat），注入上下文变量。 */
    private fun evalExpression(expr: String, ctx: Map<String, Any?>): Any? {
        val binding = Binding()
        ctx.forEach { (k, v) -> binding.setVariable(k, v) }
        val cl = groovyClassLoader ?: javaClass.classLoader
        return GroovyShell(cl, binding).evaluate(expr, "wf_eval.groovy")
    }

    /**
     * 参数插值：把 `{var}` 替换为上下文值。
     * - 整个值是 `{var}` 且变量存在 → 保持原始类型（数字/布尔/对象）
     * - 混搭或部分匹配 → 字符串拼接
     */
    private fun interpolateArgs(args: Map<String, String>, ctx: Map<String, Any?>): Map<String, Any?> {
        return args.mapValues { (_, v) -> interpolate(v, ctx) }
    }

    private fun interpolate(template: String, ctx: Map<String, Any?>): Any? {
        // 整个值是单个变量引用 → 保持类型
        val single = SINGLE_VAR.matchEntire(template)
        if (single != null) {
            val key = single.groupValues[1]
            return resolveVar(key, ctx)
        }
        // 混搭 → 字符串替换
        return VAR_RE.replace(template) { m ->
            val key = m.groupValues[1]
            resolveVar(key, ctx)?.toString() ?: m.value
        }
    }

    /** 解析变量路径：支持 `params.xxx` 嵌套访问。 */
    private fun resolveVar(key: String, ctx: Map<String, Any?>): Any? {
        val parts = key.split(".")
        var current: Any? = ctx
        for (part in parts) {
            current = when (current) {
                is Map<*, *> -> current[part]
                else -> return null
            }
        }
        return current
    }

    companion object {
        private val SINGLE_VAR = Regex("""^\{(\w+(?:\.\w+)*)}$""")
        private val VAR_RE = Regex("""\{(\w+(?:\.\w+)*)}""")
        private val MAPPER = com.fasterxml.jackson.databind.ObjectMapper()
    }
}

// ── 引擎依赖的最小接口（避免循环依赖）──

/** AgentTool 的最小引用接口（WorkflowEngine 不直接依赖 agent 包）。 */
interface AgentToolRef {
    val name: String
    fun execute(toolCallId: String, inputJson: String): ToolResultRef
}

/** ToolResult 的最小引用接口。 */
data class ToolResultRef(val content: String, val isError: Boolean)

/** SkillRegistry 的最小引用接口。 */
interface SkillRegistryRef {
    fun get(name: String): SkillDef?
}

// ── 执行结果模型 ──

/** 工作流整体执行结果。 */
data class WorkflowResult(
    val success: Boolean,
    val message: String,
    /** 执行结束时的完整上下文（含所有 assign 变量）。 */
    val context: Map<String, Any?> = emptyMap(),
    /** 已执行的步骤 id 列表。 */
    val executedSteps: List<String> = emptyList()
)

/** 单步执行结果。 */
internal data class StepResult(
    val success: Boolean,
    val message: String?,
    val value: Any?
)

/** 步骤进度推送（前端实时展示用）。 */
data class StepProgress(
    val stepId: String,
    val stepName: String,
    val status: StepStatus,
    val detail: String?
)

enum class StepStatus { RUNNING, DONE, FAILED, SKIPPED, RETRYING }
