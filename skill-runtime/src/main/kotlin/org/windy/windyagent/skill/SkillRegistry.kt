package org.windy.windyagent.skill

import java.io.File
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 扫描技能库 skills/ 目录，解析三态技能（对齐 Anthropic Agent Skills 的 SKILL.md 形态）：
 *  - 文件夹 `skills/<handle>/SKILL.md`（+ 可选脚本）：脚本+文字 或 纯文字
 *  - 扁平 `skills/<handle>.md`：纯文字技能
 *  - 扁平 `skills/<handle>.groovy`：纯脚本技能（头部 `//` 注释声明元数据）
 *
 * `SKILL.md` 用 YAML frontmatter 声明元数据，`---` 之间：
 * ```
 * ---
 * name: welcome_vip
 * description: 给玩家发 VIP 礼包
 * script: welcome.groovy        # 省略=纯文字技能
 * targets: [survival, lobby]    # 省略=下发到所有在线子服
 * args:
 *   - player: string 玩家名
 *   - coins: int 金币数
 * ---
 * 正文：纯文字技能=操作流程；脚本技能=用法说明
 * ```
 *
 * 本类下沉到 core：中心（Velocity）与 bukkit 子服共用同一套解析/文件管理。脚本的**执行**仍在
 * bukkit 侧（SkillEngine，需 Bukkit API）。[reload] 幂等，能力目录重建时调用做热重载。
 */
class SkillRegistry(private val dir: File, private val maxFileSize: Long = 512 * 1024) : SkillRegistryRef {

    private val log: Logger = LoggerFactory.getLogger(SkillRegistry::class.java)
    @Volatile private var skills: Map<String, SkillDef> = emptyMap()

    fun all(): List<SkillDef> = skills.values.toList()
    override fun get(name: String): SkillDef? = skills[name.lowercase()]
    fun isEmpty(): Boolean = skills.isEmpty()

    /** 重新扫描目录。返回加载到的技能数；目录不存在则创建并返回 0。 */
    fun reload(): Int {
        if (!dir.exists()) dir.mkdirs()
        val found = LinkedHashMap<String, SkillDef>()
        dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
            runCatching {
                when {
                    f.isDirectory -> File(f, "SKILL.md").takeIf { it.isFile && it.length() <= maxFileSize }?.let { parseFolder(f, it) }
                    f.name.equals("SKILL.md", true) -> null  // 顶层裸 SKILL.md 跳过
                    f.name.endsWith(".md", true) -> if (f.length() <= maxFileSize) parseFlatText(f) else null
                    f.name.endsWith(".groovy", true) -> if (f.length() <= maxFileSize) parseFlatScript(f) else null
                    else -> null
                }
            }.onSuccess { def -> if (def != null) found[def.name.lowercase()] = def }
                .onFailure { log.warn("解析技能 ${f.name} 失败：${it.message}") }
        }
        skills = found
        return found.size
    }

    // ---------- 解析 ----------

    /** 文件夹技能：SKILL.md（frontmatter + 正文）+ 可选脚本。 */
    private fun parseFolder(folder: File, md: File): SkillDef {
        val handle = folder.name
        val (meta, body) = splitFrontmatter(md.readText())
        val name = meta["name"]?.ifBlank { null } ?: handle
        val scriptFile = meta["script"]?.ifBlank { null }
        val scriptText = scriptFile?.let { File(folder, File(it).name).takeIf { sf -> sf.isFile }?.readText() }
        return SkillDef(
            name = name,
            description = meta["description"]?.ifBlank { null } ?: name,
            body = body.ifBlank { null },
            script = scriptText,
            args = parseYamlArgs(meta["args"]),
            source = handle,
            mdPath = "$handle/SKILL.md",
            scriptPath = if (scriptText != null) "$handle/${File(scriptFile).name}" else null,
            targets = parseTargets(meta["targets"]),
            tags = parseTags(meta["tags"]),
            permission = meta["permission"]?.ifBlank { null } ?: "trusted",
            outputs = parseOutputs(meta["outputs"]),
            steps = parseSteps(meta["steps"]),
            origin = meta["origin"]?.ifBlank { null } ?: "manual"
        )
    }

    /** 扁平纯文字技能：x.md（frontmatter + 正文，无脚本）。 */
    private fun parseFlatText(f: File): SkillDef {
        val handle = f.nameWithoutExtension
        val (meta, body) = splitFrontmatter(f.readText())
        val name = meta["name"]?.ifBlank { null } ?: handle
        return SkillDef(
            name = name,
            description = meta["description"]?.ifBlank { null } ?: name,
            body = body.ifBlank { null } ?: name,
            script = null,
            args = emptyList(),
            source = f.name,
            mdPath = f.name,
            scriptPath = null,
            tags = parseTags(meta["tags"]),
            permission = meta["permission"]?.ifBlank { null } ?: "trusted",
            outputs = parseOutputs(meta["outputs"]),
            steps = parseSteps(meta["steps"]),
            origin = meta["origin"]?.ifBlank { null } ?: "manual"
        )
    }

    /** 扁平纯脚本技能：x.groovy，头部 `//` 注释声明 name/description/arg/target。 */
    private fun parseFlatScript(f: File): SkillDef {
        val text = f.readText()
        var name = f.nameWithoutExtension
        var desc = ""
        val args = mutableListOf<SkillArg>()
        var targets = emptyList<String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (!line.startsWith("//")) break
            val b = line.removePrefix("//").trim()
            when {
                b.startsWith("name:", true) -> name = b.substringAfter(':').trim().ifBlank { name }
                b.startsWith("description:", true) -> desc = b.substringAfter(':').trim()
                b.startsWith("target:", true) -> targets = parseTargets(b.substringAfter(':').trim())
                b.startsWith("arg:", true) -> {
                    val parts = b.substringAfter(':').trim().split(Regex("\\s+"), limit = 3)
                    if (parts.size >= 2) args += SkillArg(parts[0], parts[1], parts.getOrElse(2) { "" })
                }
            }
        }
        return SkillDef(name, desc.ifBlank { name }, null, text, args, f.name, null, f.name, targets)
    }

    /** 取 `---` 之间的 frontmatter（解析成扁平 map，args/targets 原样留字符串待后续解析），其余为正文。 */
    private fun splitFrontmatter(text: String): Pair<Map<String, String>, String> {
        val lines = text.replace("\r\n", "\n").split("\n")
        if (lines.firstOrNull()?.trim() != "---") return emptyMap<String, String>() to text.trim()
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (end < 0) return emptyMap<String, String>() to text.trim()
        val fmLines = lines.subList(1, end + 1)
        val body = lines.subList(end + 2, lines.size).joinToString("\n").trim()

        val meta = LinkedHashMap<String, String>()
        var i = 0
        while (i < fmLines.size) {
            val raw = fmLines[i]
            val line = raw.trim()
            if (line.isEmpty() || !line.contains(":")) { i++; continue }
            val key = line.substringBefore(':').trim()
            val v = line.substringAfter(':').trim()
            if (v.isEmpty()) {
                // 块标量（如 args:）：收集后续缩进行
                val sb = StringBuilder()
                var j = i + 1
                while (j < fmLines.size && (fmLines[j].startsWith(" ") || fmLines[j].startsWith("\t")) && fmLines[j].isNotBlank()) {
                    sb.append(fmLines[j].trim()).append("\n"); j++
                }
                meta[key] = sb.toString().trim()
                i = j
            } else { meta[key] = v; i++ }
        }
        return meta to body
    }

    /**
     * 解析 args 块，支持增强格式：
     * `- 名: 类型 说明` — 基础（必填）
     * `- 名: 类型 optional 说明` — 可选
     * `- 名: 类型 optional default=值 说明` — 可选+默认值
     * `- 名: type enum=[a,b,c] 说明` — 枚举约束
     */
    private fun parseYamlArgs(block: String?): List<SkillArg> {
        if (block.isNullOrBlank()) return emptyList()
        return block.split("\n").mapNotNull { raw ->
            val line = raw.trim().removePrefix("-").trim()
            if (line.isEmpty() || !line.contains(":")) return@mapNotNull null
            val pname = line.substringBefore(':').trim()
            val rest = line.substringAfter(':').trim()
            if (pname.isEmpty()) return@mapNotNull null
            parseEnhancedArg(pname, rest)
        }
    }

    /** 解析单行增强参数声明。 */
    private fun parseEnhancedArg(name: String, spec: String): SkillArg {
        val tokens = spec.split(Regex("\\s+"))
        val type = tokens.getOrElse(0) { "string" }
        var required = true
        var default: Any? = null
        var enumValues = emptyList<String>()
        val descParts = mutableListOf<String>()
        var i = 1
        while (i < tokens.size) {
            val t = tokens[i]
            when {
                t.equals("optional", true) -> { required = false; i++ }
                t.startsWith("default=", true) -> {
                    required = false
                    default = t.substringAfter("=", "").ifBlank { null }
                    i++
                }
                t.startsWith("enum=", true) -> {
                    val raw = t.substringAfter("=", "").removeSurrounding("[", "]")
                    enumValues = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    i++
                }
                t.startsWith("enum:[") || t.startsWith("enum=[") -> {
                    // enum=[a,b,c] 跨 token 处理（方括号内可能有逗号）
                    val sb = StringBuilder(t.substringAfter("[").removePrefix("enum:").removePrefix("enum="))
                    while (i + 1 < tokens.size && !tokens[i + 1].contains("]")) {
                        i++; sb.append(" ").append(tokens[i])
                    }
                    if (i + 1 < tokens.size) { i++; sb.append(" ").append(tokens[i].substringBefore("]")) }
                    enumValues = sb.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    i++
                }
                else -> { descParts.add(t); i++ }
            }
        }
        return SkillArg(name, type, descParts.joinToString(" "), required, default, enumValues)
    }

    /**
     * 解析 targets：支持行内 `[a, b]`、逗号分隔 `a, b`、或多行 `- a` 列表块。空/`all`/`*` → 空列表（=全部）。
     */
    private fun parseTargets(block: String?): List<String> {
        if (block.isNullOrBlank()) return emptyList()
        val cleaned = block.trim().removeSurrounding("[", "]")
        val items = cleaned.split(Regex("[,\\n]"))
            .map { it.trim().removePrefix("-").trim().trim('"', '\'') }
            .filter { it.isNotEmpty() }
        if (items.size == 1 && (items[0].equals("all", true) || items[0] == "*")) return emptyList()
        return items.filterNot { it.equals("all", true) || it == "*" }
    }

    /** 解析 tags：与 targets 同格式（行内 `[a, b]` 或逗号分隔）。 */
    private fun parseTags(block: String?): List<String> = parseTargets(block) // 复用格式

    /** 解析 outputs 块：每行 `- name: type 说明`（或 `name: type 说明`）。 */
    private fun parseOutputs(block: String?): Map<String, String> {
        if (block.isNullOrBlank()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        block.split("\n").forEach { raw ->
            val line = raw.trim().removePrefix("-").trim()
            if (line.isEmpty() || !line.contains(":")) return@forEach
            val oname = line.substringBefore(':').trim()
            val otype = line.substringAfter(':').trim()
            if (oname.isNotEmpty()) map[oname] = otype.ifEmpty { "string" }
        }
        return map
    }

    /**
     * 解析 steps 块：轻量 YAML-like 解析器。
     *
     * 格式：
     * ```
     * - id: clear
     *   name: 清理区域
     *   tool: run_command_on_server
     *   args:
     *     server: "{server}"
     *     command: "/fill ..."
     * - id: floor
     *   ...
     * ```
     *
     * 逻辑：以 `- id:` 为步骤分隔，每步的 key: value 逐行解析；
     * `args:` 下的缩进行作为参数映射收集。
     */
    private fun parseSteps(block: String?): List<WorkflowStep>? {
        if (block.isNullOrBlank()) return null
        val lines = block.replace("\r\n", "\n").split("\n")
        val steps = mutableListOf<WorkflowStep>()
        var current: MutableMap<String, Any?>? = null
        var currentArgs: LinkedHashMap<String, String>? = null
        var currentScript: StringBuilder? = null

        fun flushStep() {
            current?.let { m ->
                // 先把积攒的 args/script 写回
                currentArgs?.let { m["args"] = it }
                currentScript?.let { m["script"] = it.toString().trimEnd() }
                steps.add(buildStep(m))
            }
            current = null; currentArgs = null; currentScript = null
        }

        for (raw in lines) {
            val line = raw.trimEnd()
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // 新步骤开始
            if (trimmed.startsWith("- id:") || trimmed.startsWith("-id:")) {
                flushStep()
                current = LinkedHashMap()
                current!!["id"] = trimmed.substringAfter(":").trim().removeSurrounding("\"")
                continue
            }
            if (current == null) continue

            // args 子块
            if (trimmed == "args:") {
                currentArgs = LinkedHashMap()
                currentScript = null
                continue
            }
            if (currentArgs != null && (raw.startsWith("      ") || raw.startsWith("\t\t") || raw.startsWith("    "))) {
                // 缩进行 = args 的 key: value
                if (trimmed.contains(":")) {
                    val k = trimmed.substringBefore(":").trim()
                    val v = trimmed.substringAfter(":").trim().removeSurrounding("\"")
                    if (k.isNotEmpty()) currentArgs!![k] = v
                }
                continue
            } else if (currentArgs != null) {
                // 退出 args 块
                current!!["args"] = currentArgs!!
                currentArgs = null
            }

            // script 子块（多行脚本，用 | 开头）
            if (trimmed == "script: |" || trimmed == "script:|") {
                currentScript = StringBuilder()
                continue
            }
            if (currentScript != null && (raw.startsWith("      ") || raw.startsWith("\t\t") || raw.startsWith("    "))) {
                if (currentScript!!.isNotEmpty()) currentScript!!.append("\n")
                currentScript!!.append(trimmed)
                continue
            } else if (currentScript != null) {
                current!!["script"] = currentScript!!.toString().trimEnd()
                currentScript = null
            }

            // 普通 key: value
            if (trimmed.contains(":")) {
                val k = trimmed.substringBefore(":").trim()
                val v = trimmed.substringAfter(":").trim().removeSurrounding("\"")
                if (k.isNotEmpty()) current!![k] = v
            }
        }
        flushStep()
        return steps.ifEmpty { null }
    }

    /** 从解析好的 map 构建一个 WorkflowStep。 */
    private fun buildStep(m: Map<String, Any?>): WorkflowStep {
        val argsMap = when (val a = m["args"]) {
            is Map<*, *> -> a.entries.associate { (k, v) -> k.toString() to v.toString() }
            else -> emptyMap()
        }
        return WorkflowStep(
            id = m["id"]?.toString() ?: "step_${System.nanoTime()}",
            name = m["name"]?.toString() ?: m["id"]?.toString() ?: "未命名步骤",
            tool = m["tool"]?.toString(),
            script = m["script"]?.toString(),
            skill = m["skill"]?.toString(),
            args = argsMap,
            condition = m["condition"]?.toString(),
            assign = m["assign"]?.toString(),
            repeat = m["repeat"]?.toString(),
            forEach = m["forEach"]?.toString() ?: m["foreach"]?.toString(),
            onFail = m["onFail"]?.toString() ?: m["on_fail"]?.toString() ?: "abort",
            parallel = m["parallel"]?.toString()?.toBoolean() ?: false
        )
    }

    // ---------- 文件管理（WebUI / 下发以 handle 为句柄）----------

    /** 读一个技能的两部分内容（md 正文 + 脚本）。扁平技能无 SKILL.md 时按当前元数据合成一份，供 WebUI 编辑。 */
    fun read(handle: String): SkillContent? {
        val def = all().firstOrNull { it.handle.equals(handle, true) } ?: return null
        val mdFile = def.mdPath?.let { File(dir, it).takeIf { f -> f.isFile && f.length() <= maxFileSize }?.readText() }
        val md = mdFile ?: synthMd(def)
        val sc = def.scriptPath?.let { File(dir, it).takeIf { f -> f.isFile }?.readText() } ?: def.script ?: ""
        return SkillContent(def.isScript, md, sc, def.scriptPath?.substringAfterLast('/'), def.isWorkflow)
    }

    /** 为没有 SKILL.md 的扁平技能合成 frontmatter（保住所有元数据，编辑保存后落成文件夹格式）。 */
    private fun synthMd(def: SkillDef): String {
        val sb = StringBuilder("---\n")
        sb.append("name: ${def.name}\n")
        sb.append("description: ${def.description}\n")
        if (def.origin != "manual") sb.append("origin: ${def.origin}\n")
        if (def.tags.isNotEmpty()) sb.append("tags: [${def.tags.joinToString(", ")}]\n")
        if (def.permission != "trusted") sb.append("permission: ${def.permission}\n")
        if (def.isScript) {
            sb.append("script: script.groovy\n")
            if (def.targets.isNotEmpty()) sb.append("targets: [${def.targets.joinToString(", ")}]\n")
        }
        if (def.args.isNotEmpty()) {
            sb.append("args:\n")
            def.args.forEach { a ->
                val opt = if (!a.required) " optional" else ""
                val defVal = if (a.default != null) " default=${a.default}" else ""
                val enum = if (a.enumValues.isNotEmpty()) " enum=[${a.enumValues.joinToString(",")}]" else ""
                sb.append("  - ${a.name}: ${a.type}$opt$defVal$enum ${a.description}\n")
            }
        }
        if (def.outputs.isNotEmpty()) {
            sb.append("outputs:\n")
            def.outputs.forEach { (k, v) -> sb.append("  - $k: $v\n") }
        }
        if (def.isWorkflow) {
            sb.append("steps:\n")
            def.steps!!.forEach { step ->
                sb.append("  - id: ${step.id}\n")
                if (step.name != step.id) sb.append("    name: ${step.name}\n")
                step.tool?.let { sb.append("    tool: $it\n") }
                step.skill?.let { sb.append("    skill: $it\n") }
                step.script?.let { sb.append("    script: |\n      ${it.replace("\n", "\n      ")}\n") }
                if (step.args.isNotEmpty()) {
                    sb.append("    args:\n")
                    step.args.forEach { (k, v) -> sb.append("      $k: \"$v\"\n") }
                }
                step.condition?.let { sb.append("    condition: $it\n") }
                step.assign?.let { sb.append("    assign: $it\n") }
                step.repeat?.let { sb.append("    repeat: $it\n") }
                step.forEach?.let { sb.append("    forEach: $it\n") }
                if (step.onFail != "abort") sb.append("    onFail: ${step.onFail}\n")
                if (step.parallel) sb.append("    parallel: true\n")
            }
        }
        sb.append("---\n")
        def.body?.takeIf { it.isNotBlank() }?.let { sb.append(it).append("\n") }
        return sb.toString()
    }

    /**
     * 写一个技能（统一落成文件夹格式 `<handle>/SKILL.md` + 可选 `<handle>/script.groovy`）并热重载。
     * @param hasScript true=脚本技能（写 script.groovy 并在 frontmatter 标 script）；false=纯文字（只写 SKILL.md）。
     * @return 重载后的技能总数；handle 非法返回 -1。
     */
    fun write(handle: String, md: String, script: String, hasScript: Boolean): Int {
        val safe = safeHandle(handle) ?: return -1
        val folder = File(dir, safe)
        // 同名扁平文件先清掉，避免与文件夹双份加载
        File(dir, "$safe.md").takeIf { it.isFile }?.delete()
        File(dir, "$safe.groovy").takeIf { it.isFile }?.delete()
        folder.mkdirs()
        val mdText = if (hasScript && !md.contains("script:")) ensureScriptField(md) else md
        File(folder, "SKILL.md").writeText(mdText)
        val scriptFile = File(folder, "script.groovy")
        if (hasScript) scriptFile.writeText(script) else scriptFile.takeIf { it.isFile }?.delete()
        return reload()
    }

    /** 删除一个技能（文件夹或扁平文件）并热重载。 */
    fun delete(handle: String): Boolean {
        val safe = safeHandle(handle) ?: return false
        var ok = false
        File(dir, safe).takeIf { it.isDirectory }?.let { ok = it.deleteRecursively() || ok }
        File(dir, "$safe.md").takeIf { it.isFile }?.let { ok = it.delete() || ok }
        File(dir, "$safe.groovy").takeIf { it.isFile }?.let { ok = it.delete() || ok }
        reload()
        return ok
    }

    /** frontmatter 没声明 script 时补一行（写文件夹脚本技能用）。 */
    private fun ensureScriptField(md: String): String {
        val lines = md.replace("\r\n", "\n").split("\n").toMutableList()
        if (lines.firstOrNull()?.trim() == "---") {
            val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
            if (end >= 0) { lines.add(end + 1, "script: script.groovy"); return lines.joinToString("\n") }
        }
        // 无 frontmatter：补一段
        return "---\nscript: script.groovy\n---\n$md"
    }

    /** handle 限定在 skills/ 内：剥离路径分量、去后缀、挡 `..`。 */
    private fun safeHandle(handle: String): String? {
        val base = File(handle.trim()).name
            .removeSuffix(".md").removeSuffix(".groovy")
            .takeIf { it.isNotBlank() && it != "." && it != ".." } ?: return null
        return base
    }

    /** WebUI 读取技能内容的 DTO。 */
    data class SkillContent(
        val isScript: Boolean,
        val md: String,
        val script: String,
        val scriptFile: String?,
        /** 是否为工作流技能。 */
        val isWorkflow: Boolean = false
    )
}
