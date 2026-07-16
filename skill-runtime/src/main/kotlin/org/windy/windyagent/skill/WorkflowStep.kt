package org.windy.windyagent.skill

/**
 * 工作流中的一个执行步骤。一个技能的 `steps` 列表由若干 Step 组成，按序执行。
 *
 * 每步必须指定一种动作（互斥，优先级：tool > script > skill）：
 *  - [tool]   → 调用 Platform 上已注册的 AgentTool（如 run_command_on_server）
 *  - [script] → 内联脚本已废弃；请改用 tool/skill，或拆成 .kether 技能
 *  - [skill]  → 调用另一个技能（递归进入 SkillRegistry → WorkflowEngine 或原路径）
 *
 * 控制流：
 *  - [condition] → 受限布尔表达式，为 false 时跳过本步
 *  - [assign]    → 把本步结果存入上下文变量（后续步骤用 `{var}` 引用）
 *  - [repeat]    → 受限表达式返回次数或变量，对每个元素重复执行本步
 *  - [forEach]   → 上下文中已有列表变量名，对每个元素重复执行本步
 *  - [onFail]    → 失败策略：abort（默认，中止）/ continue（跳过）/ retry:N（重试 N 次）
 *
 * 参数插值：
 *  [args] 的值中 `{varName}` 会在执行前被替换为上下文变量。
 *  特殊变量：`{params.xxx}` → 调用时传入的原始参数。
 */
data class WorkflowStep(
    /** 步骤唯一 id（日志/进度引用用）。 */
    val id: String,
    /** 步骤人类可读名（进度展示用）。 */
    val name: String = id,
    // ── 动作（三选一）──
    /** 调用已有 AgentTool 的 name。 */
    val tool: String? = null,
    /** 内联脚本源码（已废弃；保留用于提示迁移）。 */
    val script: String? = null,
    /** 调用另一个技能的 name。 */
    val skill: String? = null,
    // ── 参数 ──
    /** 传给动作的参数（支持 `{var}` 插值）。 */
    val args: Map<String, String> = emptyMap(),
    // ── 控制流 ──
    /** 受限布尔表达式；为 false/空串时跳过本步。null = 无条件执行。 */
    val condition: String? = null,
    /** 把本步结果存入此变量名（后续 `{assign}` 可引用）。 */
    val assign: String? = null,
    /** 受限表达式返回次数或变量，对每个元素执行本步（当前元素 → `{item}`）。 */
    val repeat: String? = null,
    /** 上下文中已有列表变量名，对每个元素执行本步。 */
    val forEach: String? = null,
    /** 失败策略：abort / continue / retry:N。 */
    val onFail: String = "abort",
    /** 是否可与相邻的并行步骤同时执行。连续的 parallel=true 步骤组成一个并行组。 */
    val parallel: Boolean = false
) {
    /** 解析重试次数（onFail = "retry:3" → 3；其它 → 0）。 */
    val retryCount: Int get() {
        val m = RETRY_RE.matchEntire(onFail.trim()) ?: return 0
        return m.groupValues[1].toIntOrNull() ?: 0
    }

    /** 动作类型枚举（用于引擎分派）。 */
    enum class ActionType { TOOL, SCRIPT, SKILL, NONE }
    val actionType: ActionType get() = when {
        tool != null -> ActionType.TOOL
        script != null -> ActionType.SCRIPT
        skill != null -> ActionType.SKILL
        else -> ActionType.NONE
    }

    companion object {
        private val RETRY_RE = Regex("""retry[:\s]*(\d+)""", RegexOption.IGNORE_CASE)
    }
}


