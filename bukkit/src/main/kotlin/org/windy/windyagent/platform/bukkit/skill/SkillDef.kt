package org.windy.windyagent.platform.bukkit.skill

import org.windy.windyagent.bus.CapabilityCommand

/** 一个脚本技能的参数声明。 */
data class SkillArg(val name: String, val type: String, val description: String) {
    /** 映射到 JSON Schema 的类型（LLM 据此填参）。未知类型一律按 string。 */
    fun jsonType(): String = when (type.lowercase()) {
        "int", "integer", "long" -> "integer"
        "double", "float", "number" -> "number"
        "bool", "boolean" -> "boolean"
        else -> "string"
    }
}

/**
 * 一个由服主编写、放在 skills/ 目录的技能。对齐 Anthropic Agent Skills：以 `name` + `description`
 * 暴露给 Agent，**正文/脚本按需加载**。三种形态：
 *  - **纯文字**（[script] == null）：正文是一套操作流程，Agent 读懂后用**现有工具**执行（不写代码）。
 *  - **纯脚本**（[body] == null）：Groovy 脚本，做现有工具做不到的事。
 *  - **脚本+文字**：[body] 说清「何时用/怎么用」（进工具描述供 LLM 选择），[script] 是真正执行的肌肉。
 *
 * 文件形态：扁平 `x.md`(纯文字) / `x.groovy`(纯脚本) / 文件夹含 `SKILL.md`(+脚本)。
 * 信任：能往 skills/ 放文件的即服主 → 视为已审过的确定性扩展，不过 CommandGuard，但每次执行记 audit。
 */
data class SkillDef(
    val name: String,
    val description: String,
    /** 文字正文：纯文字技能的指令；脚本+文字技能的「用法说明」。纯脚本为 null。 */
    val body: String?,
    /** Groovy 源码；纯文字技能为 null。 */
    val script: String?,
    /** 脚本参数声明（仅脚本技能有意义）。 */
    val args: List<SkillArg>,
    /** 展示用相对路径（如 welcome_vip 或 online_count.groovy）。 */
    val source: String,
    /** 文字文件相对路径（SKILL.md 或 x.md）；无文字为 null。 */
    val mdPath: String?,
    /** 脚本文件相对路径；无脚本为 null。 */
    val scriptPath: String?
) {
    val isScript: Boolean get() = script != null
    /** WebUI/删除句柄：统一去后缀（文件夹技能=文件夹名，扁平技能=去 .md/.groovy 的文件名）。 */
    val handle: String get() = source.removeSuffix(".md").removeSuffix(".groovy")

    /** 喂给 LLM 的工具描述：脚本技能把「用法」并进描述（LLM 选择/填参前可见）；纯文字按需返回正文。 */
    fun toolDescription(): String = when {
        !isScript -> "[流程技能] $description（调用本技能获取操作步骤后照做）"
        body.isNullOrBlank() -> "[脚本技能] $description"
        else -> "[脚本技能] $description\n【用法】$body"
    }

    /** 喂给 LLM 的 JSON Schema：脚本技能=参数；纯文字技能=无参（调用即返回正文）。 */
    fun inputSchema(): String {
        if (!isScript) return """{"type":"object","properties":{},"required":[]}"""
        val props = args.joinToString(",") { a ->
            """"${a.name}":{"type":"${a.jsonType()}","description":"${esc(a.description)}"}"""
        }
        val required = args.joinToString(",") { "\"${it.name}\"" }
        return """{"type":"object","properties":{$props},"required":[$required]}"""
    }

    /** 纯文字技能被「调用」时返回的内容 = 正文（操作流程）。 */
    fun textOutput(): String = body?.takeIf { it.isNotBlank() } ?: description

    /**
     * 脚本执行前的参数预校验：所有声明参数视为必填。缺失/类型不符返回说明（供回报 LLM 补参）；通过返回 null。
     */
    fun validate(provided: Map<String, Any?>): String? {
        for (a in args) {
            if (!provided.containsKey(a.name) || provided[a.name] == null)
                return "缺少必填参数「${a.name}」（${a.description}）"
            val v = provided[a.name]
            when (a.jsonType()) {
                "integer", "number" -> if (v !is Number) return "参数「${a.name}」应为数字，实际收到：$v"
                "boolean" -> if (v !is Boolean) return "参数「${a.name}」应为布尔值，实际收到：$v"
            }
        }
        return null
    }

    /** 作为一条能力目录项推给中心（search_capabilities 可搜；描述含类型 + 参数/用法提示）。 */
    fun toCapabilityCommand(): CapabilityCommand {
        val tip = if (isScript) {
            val argHint = if (args.isEmpty()) "无参数" else args.joinToString("，") { "${it.name}(${it.type})：${it.description}" }
            "[脚本技能] $description ｜ 用 run_skill_on_server 调用，参数：$argHint"
        } else {
            "[流程技能] $description ｜ 用 run_skill_on_server 调用以获取操作流程"
        }
        return CapabilityCommand(name = name, aliases = emptyList(), description = tip, source = SOURCE)
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        const val SOURCE = "WindyAgent 技能"
    }
}
