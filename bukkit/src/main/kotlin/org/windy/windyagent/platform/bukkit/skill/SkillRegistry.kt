package org.windy.windyagent.platform.bukkit.skill

import java.io.File
import java.util.logging.Logger

/**
 * 扫描 skills/ 目录，解析三态技能（对齐 Anthropic Agent Skills 的 SKILL.md 形态）：
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
 * args:
 *   - player: string 玩家名
 *   - coins: int 金币数
 * ---
 * 正文：纯文字技能=操作流程；脚本技能=用法说明
 * ```
 * [reload] 幂等，能力目录重建时调用做热重载。WebUI 经总线以 handle 远程增删改。
 */
class SkillRegistry(private val dir: File, private val log: Logger) {

    @Volatile private var skills: Map<String, SkillDef> = emptyMap()

    fun all(): List<SkillDef> = skills.values.toList()
    fun get(name: String): SkillDef? = skills[name.lowercase()]
    fun isEmpty(): Boolean = skills.isEmpty()

    /** 重新扫描目录。返回加载到的技能数；目录不存在则创建并返回 0。 */
    fun reload(): Int {
        if (!dir.exists()) dir.mkdirs()
        val found = LinkedHashMap<String, SkillDef>()
        dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
            runCatching {
                when {
                    f.isDirectory -> File(f, "SKILL.md").takeIf { it.isFile }?.let { parseFolder(f, it) }
                    f.name.equals("SKILL.md", true) -> null  // 顶层裸 SKILL.md 跳过
                    f.name.endsWith(".md", true) -> parseFlatText(f)
                    f.name.endsWith(".groovy", true) -> parseFlatScript(f)
                    else -> null
                }
            }.onSuccess { def -> if (def != null) found[def.name.lowercase()] = def }
                .onFailure { log.warning("解析技能 ${f.name} 失败：${it.message}") }
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
            scriptPath = if (scriptText != null) "$handle/${File(scriptFile).name}" else null
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
            scriptPath = null
        )
    }

    /** 扁平纯脚本技能：x.groovy，头部 `//` 注释声明 name/description/arg。 */
    private fun parseFlatScript(f: File): SkillDef {
        val text = f.readText()
        var name = f.nameWithoutExtension
        var desc = ""
        val args = mutableListOf<SkillArg>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (!line.startsWith("//")) break
            val b = line.removePrefix("//").trim()
            when {
                b.startsWith("name:", true) -> name = b.substringAfter(':').trim().ifBlank { name }
                b.startsWith("description:", true) -> desc = b.substringAfter(':').trim()
                b.startsWith("arg:", true) -> {
                    val parts = b.substringAfter(':').trim().split(Regex("\\s+"), limit = 3)
                    if (parts.size >= 2) args += SkillArg(parts[0], parts[1], parts.getOrElse(2) { "" })
                }
            }
        }
        return SkillDef(name, desc.ifBlank { name }, null, text, args, f.name, null, f.name)
    }

    /** 取 `---` 之间的 frontmatter（解析成扁平 map，args 原样留字符串待 [parseYamlArgs]），其余为正文。 */
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

    /** 解析 args 块：每行 `- 名: 类型 说明`（或 `名: 类型 说明`）。 */
    private fun parseYamlArgs(block: String?): List<SkillArg> {
        if (block.isNullOrBlank()) return emptyList()
        return block.split("\n").mapNotNull { raw ->
            val line = raw.trim().removePrefix("-").trim()
            if (line.isEmpty() || !line.contains(":")) return@mapNotNull null
            val pname = line.substringBefore(':').trim()
            val rest = line.substringAfter(':').trim().split(Regex("\\s+"), limit = 2)
            if (pname.isEmpty() || rest.isEmpty()) null
            else SkillArg(pname, rest[0], rest.getOrElse(1) { "" })
        }
    }

    // ---------- WebUI 远程文件管理（经总线下发，以 handle 为句柄）----------

    /** 读一个技能的两部分内容（md 正文 + 脚本）。扁平技能无 SKILL.md 时按当前元数据合成一份，供 WebUI 编辑。 */
    fun read(handle: String): SkillContent? {
        val def = all().firstOrNull { it.handle.equals(handle, true) } ?: return null
        val mdFile = def.mdPath?.let { File(dir, it).takeIf { f -> f.isFile }?.readText() }
        val md = mdFile ?: synthMd(def)
        val sc = def.scriptPath?.let { File(dir, it).takeIf { f -> f.isFile }?.readText() } ?: def.script ?: ""
        return SkillContent(def.isScript, md, sc, def.scriptPath?.substringAfterLast('/'))
    }

    /** 为没有 SKILL.md 的扁平技能合成 frontmatter（保住 name/description/args，编辑保存后落成文件夹格式）。 */
    private fun synthMd(def: SkillDef): String {
        val sb = StringBuilder("---\n")
        sb.append("name: ${def.name}\n")
        sb.append("description: ${def.description}\n")
        if (def.isScript) {
            sb.append("script: script.groovy\n")
            if (def.args.isNotEmpty()) {
                sb.append("args:\n")
                def.args.forEach { sb.append("  - ${it.name}: ${it.type} ${it.description}\n") }
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
    data class SkillContent(val isScript: Boolean, val md: String, val script: String, val scriptFile: String?)
}
