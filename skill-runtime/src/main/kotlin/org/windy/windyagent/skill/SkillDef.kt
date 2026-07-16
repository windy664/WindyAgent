package org.windy.windyagent.skill


/** 一个技能的参数声明（对齐 JSON Schema，支持 optional / default / enum）。 */
data class SkillArg(
    val name: String,
    val type: String,
    val description: String,
    /** 是否必填；false 时 LLM 可省略，引擎自动填 [default]。 */
    val required: Boolean = true,
    /** 非必填参数的默认值（[required]=false 时生效）。类型须与 [jsonType] 匹配。 */
    val default: Any? = null,
    /** 枚举约束：非空时 LLM 只能从中选值。 */
    val enumValues: List<String> = emptyList()
) {
    /** 映射到 JSON Schema 的类型（LLM 据此填参）。未知类型一律按 string。 */
    fun jsonType(): String = when (type.lowercase()) {
        "int", "integer", "long" -> "integer"
        "double", "float", "number" -> "number"
        "bool", "boolean" -> "boolean"
        else -> "string"
    }
}

/**
 * 一个由服主编写、放在中心技能库 `skills/` 目录的技能。对齐 Anthropic Agent Skills：以 `name` + `description`
 * 暴露给 Agent，**正文/脚本按需加载**。三种形态：
 *  - **纯文字**（[script] == null）：正文是一套操作流程，Agent 读懂后用**现有工具**执行（不写代码）。中心直接执行。
 *  - **纯脚本**（[body] == null）：Groovy 脚本，做现有工具做不到的事。**只能在 bukkit 子服执行**（需 Bukkit API）。
 *  - **脚本+文字**：[body] 说清「何时用/怎么用」（进工具描述供 LLM 选择），[script] 是真正执行的肌肉。
 *
 * 文件形态：扁平 `x.md`(纯文字) / `x.groovy`(纯脚本) / 文件夹含 `SKILL.md`(+脚本)。
 * [targets]：脚本技能的下发目标子服名；空 = 下发到所有在线子服（all）。文字技能忽略此字段（中心本地执行）。
 * 信任：能往中心库放文件的即服主 → 视为已审过的确定性扩展，不过 CommandGuard，但每次执行记 audit。
 */
data class SkillDef(
    val name: String,
    val description: String,
    /** 文字正文：纯文字技能的指令；脚本+文字技能的「用法说明」。纯脚本为 null。 */
    val body: String?,
    /** Groovy 源码；纯文字技能为 null。 */
    val script: String?,
    /** 参数声明。 */
    val args: List<SkillArg>,
    /** 展示用相对路径（如 welcome_vip 或 online_count.groovy）。 */
    val source: String,
    /** 文字文件相对路径（SKILL.md 或 x.md）；无文字为 null。 */
    val mdPath: String?,
    /** 脚本文件相对路径；无脚本为 null。 */
    val scriptPath: String?,
    /** 脚本技能下发目标子服名列表；空 = 全部在线子服（all）。 */
    val targets: List<String> = emptyList(),
    // ── 工作流增强 ──
    /** 标签（分类/检索用）。 */
    val tags: List<String> = emptyList(),
    /** 权限要求：admin / trusted / public（默认 trusted）。 */
    val permission: String = "trusted",
    /** 输出声明（name → type 描述），告知 LLM 执行后能得到什么。 */
    val outputs: Map<String, String> = emptyMap(),
    /** 工作流步骤；null = 旧模式（纯文字/纯脚本）。非空时走 WorkflowEngine。 */
    val steps: List<WorkflowStep>? = null,
    /** 技能来源：manual（服主手写，默认）/ ai_generated（AI 生成）。 */
    val origin: String = "manual"
) {
    val isScript: Boolean get() = script != null
    /** 是否为工作流技能（有多阶段步骤）。 */
    val isWorkflow: Boolean get() = !steps.isNullOrEmpty()
    /** WebUI/删除句柄：统一去后缀（文件夹技能=文件夹名，扁平技能=去 .md/.groovy 的文件名）。 */
    val handle: String get() = source.removeSuffix(".md").removeSuffix(".groovy")

    /** 该脚本技能是否应下发到指定子服（targets 空=全部）。 */
    fun appliesTo(server: String): Boolean = targets.isEmpty() || targets.any { it.equals(server, true) }

    /** 喂给 LLM 的工具描述：标注类型 + 标签 + 工作流步骤数。 */
    fun toolDescription(): String {
        val tag = when {
            isWorkflow -> "[工作流技能·${steps!!.size}步]"
            isScript -> "[脚本技能]"
            else -> "[流程技能]（调用本技能获取操作步骤后照做）"
        }
        val tagStr = if (tags.isNotEmpty()) " ${tags.joinToString(" ") { "#$it" }}" else ""
        val bodyStr = if (!body.isNullOrBlank()) "\n【用法】$body" else ""
        val outStr = if (outputs.isNotEmpty()) {
            val os = outputs.entries.joinToString("，") { "${it.key}(${it.value})" }
            "\n【输出】$os"
        } else ""
        return "$tag $tagStr$description$bodyStr$outStr"
    }

    /** 喂给 LLM 的 JSON Schema：支持 optional / enum / default。纯文字技能=无参。 */
    fun inputSchema(): String {
        if (!isScript && !isWorkflow) return """{"type":"object","properties":{},"required":[]}"""
        val props = args.joinToString(",") { a ->
            val sb = StringBuilder()
            sb.append(""""${a.name}":{"type":"${a.jsonType()}","description":"${esc(a.description)}"""")
            if (a.enumValues.isNotEmpty()) {
                val ev = a.enumValues.joinToString(",") { "\"${esc(it)}\"" }
                sb.append(""","enum":[$ev]""")
            }
            if (!a.required && a.default != null) {
                sb.append(""","default":${jsonLiteral(a.default)}""")
            }
            sb.append("}")
            sb.toString()
        }
        val required = args.filter { it.required }.joinToString(",") { "\"${it.name}\"" }
        return """{"type":"object","properties":{$props},"required":[$required]}"""
    }

    /** 纯文字技能被「调用」时返回的内容 = 正文（操作流程）。 */
    fun textOutput(): String = body?.takeIf { it.isNotBlank() } ?: description

    /**
     * 参数预校验 + 默认值填充。返回填好的 Map 或错误说明。
     * - 必填参数缺失 → 返回 Left(错误说明)
     * - 非必填参数缺失 → 自动填 [SkillArg.default]
     * - 类型不符 → 返回 Left
     * - enum 约束 → 校验值是否在范围内
     * 通过 → Right(补全后的参数 Map)
     */
    fun validateAndFill(provided: Map<String, Any?>): Pair<Map<String, Any?>?, String?> {
        val filled = LinkedHashMap(provided)
        for (a in args) {
            val v = filled[a.name]
            if (v == null) {
                if (a.required) return null to "缺少必填参数「${a.name}」（${a.description}）"
                if (a.default != null) filled[a.name] = a.default
                continue
            }
            when (a.jsonType()) {
                "integer", "number" -> if (v !is Number) return null to "参数「${a.name}」应为数字，实际收到：$v"
                "boolean" -> if (v !is Boolean) return null to "参数「${a.name}」应为布尔值，实际收到：$v"
            }
            if (a.enumValues.isNotEmpty() && v.toString() !in a.enumValues)
                return null to "参数「${a.name}」的值「$v」不在允许范围：${a.enumValues.joinToString(" / ")}"
        }
        return filled to null
    }

    /** 兼容旧调用：只校验不填默认值。通过返回 null。 */
    fun validate(provided: Map<String, Any?>): String? {
        for (a in args) {
            if (!provided.containsKey(a.name) || provided[a.name] == null) {
                if (a.required) return "缺少必填参数「${a.name}」（${a.description}）"
                continue
            }
            val v = provided[a.name]
            when (a.jsonType()) {
                "integer", "number" -> if (v !is Number) return "参数「${a.name}」应为数字，实际收到：$v"
                "boolean" -> if (v !is Boolean) return "参数「${a.name}」应为布尔值，实际收到：$v"
            }
            if (a.enumValues.isNotEmpty() && v.toString() !in a.enumValues)
                return "参数「${a.name}」的值「$v」不在允许范围：${a.enumValues.joinToString(" / ")}"
        }
        return null
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    /** 把 Kotlin 值序列化为 JSON 字面量（string/number/boolean/list）。 */
    private fun jsonLiteral(v: Any?): String = when (v) {
        null -> "null"
        is String -> "\"${esc(v)}\""
        is Number, is Boolean -> v.toString()
        is List<*> -> "[${v.joinToString(",") { jsonLiteral(it) }}]"
        else -> "\"${esc(v.toString())}\""
    }

    companion object {
        const val SOURCE = "WindyAgent 技能"
    }
}
