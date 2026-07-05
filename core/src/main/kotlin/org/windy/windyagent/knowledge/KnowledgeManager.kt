package org.windy.windyagent.knowledge

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * 可读写的知识库，**本身就是一个 Obsidian vault**：`<dataDir>/knowledge/` 下的每个 `.md` = 一条知识，
 * **子目录 = 分类**（真·磁盘目录，服主可直接用 Obsidian「打开文件夹作为库」编辑，双向同步）。
 *
 * 递归加载：`folder` 取自文件相对 vault 根的父目录（如 `规则/pvp`），`id` = 去扩展名的 vault 相对路径
 * （如 `规则/pvp规则`）——因此不同子目录下的同名文件不会 id 冲突，move/rename 也有稳定锚点。
 *
 * **本身就是 [KnowledgeStore]**：search/size 委托给当前内存检索库，故 KnowledgeSearchTool 直接拿它当 store 用，
 * 编辑后立即可检索。首启（vault 不存在）会释放一套预置知识库框架（[seedFramework]）。给 WebUI + Agent 写库共用。
 */
class KnowledgeManager(private val dir: Path) : KnowledgeStore {

    @Volatile private var entries: List<KnowledgeEntry> = emptyList()
    @Volatile private var store: KnowledgeStore = KeywordKnowledgeStore(emptyList())

    init { reload() }

    override fun search(query: String, topK: Int) = store.search(query, topK)
    override fun size() = store.size()
    fun list(): List<KnowledgeEntry> = entries

    /** 按 id 取单条（含正文），供懒加载详情/编辑用；找不到返回 null。 */
    fun get(id: String): KnowledgeEntry? = entries.firstOrNull { it.id == id }

    /**
     * 轻量元数据列表（**不含正文**）：给前端建目录树 / 关系图 / 反向链接用。
     * `links` = 该条正文里 `[[双链]]` 解析到的目标 id（去重、去自引用），这样图/反链无需下发全文。
     */
    fun metadata(): List<KbMeta> {
        val nameToId = HashMap<String, String>()
        for (e in entries) {
            nameToId.putIfAbsent(e.title.lowercase().trim(), e.id)
            nameToId.putIfAbsent(e.id.substringAfterLast('/').lowercase(), e.id)
        }
        return entries.map { e ->
            val links = WIKILINK.findAll(e.content)
                .mapNotNull { nameToId[it.groupValues[1].trim().lowercase()] }
                .filter { it != e.id }
                .distinct()
                .toList()
            KbMeta(e.id, e.title, e.folder, e.tags, links)
        }
    }

    @Synchronized
    fun reload() {
        if (!dir.exists()) {
            runCatching {
                dir.createDirectories()
                seedFramework()
            }
        }
        // 递归收集 vault 内全部 .md（子目录 = 分类）
        val files = runCatching {
            Files.walk(dir).use { s ->
                s.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".md", ignoreCase = true) }
                    .iterator().asSequence().toList()
            }
        }.getOrElse { emptyList() }
        entries = files.mapNotNull { runCatching { parse(it) }.getOrNull() }.sortedBy { it.title }
        store = KeywordKnowledgeStore(entries)
    }

    /**
     * 新增或覆盖一条。`id` 为空=新建；`folder` 为分类子目录（支持 `活动/2026` 多层，空=根）。
     * 文件落在 `<vault>/<folder>/<slug(title)>.md`；若传入的旧 id 与新路径不同（改了分类/标题）则删除旧文件。
     */
    @Synchronized
    fun save(id: String?, title: String, content: String, tags: List<String>, folder: String = ""): KnowledgeEntry {
        val base = sanitizeSeg(slug(title))
        val folderRel = sanitizeFolder(folder)
        val rel = if (folderRel.isEmpty()) base else "$folderRel/$base"
        val file = dir.resolve("$rel.md")
        runCatching { file.parent?.createDirectories() }

        val front = StringBuilder("---\n").append("title: ").append(title.trim()).append("\n")
        if (tags.isNotEmpty()) front.append("tags: ").append(tags.joinToString(", ")).append("\n")
        front.append("---\n")
        file.writeText(front.toString() + content.trim() + "\n")

        // 移动/改名：旧文件与新路径不同则删掉旧的，并清理因此空掉的目录
        val oldRel = id?.takeIf { it.isNotBlank() }?.let { sanitizePath(it) }
        if (oldRel != null && oldRel != rel) {
            val old = dir.resolve("$oldRel.md")
            runCatching { Files.deleteIfExists(old); pruneEmptyDirs(old.parent) }
        }
        reload()
        return entries.firstOrNull { it.id == rel } ?: KnowledgeEntry(rel, title, content, tags, folderRel)
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val file = dir.resolve(sanitizePath(id) + ".md")
        val ok = runCatching { Files.deleteIfExists(file) }.getOrDefault(false)
        if (ok) { runCatching { pruneEmptyDirs(file.parent) }; reload() }
        return ok
    }

    /**
     * 把某条移动到新分类 [newFolder]（文件名不变），**不改正文**——纯磁盘 move，供前端"移动到/文件夹重命名"用，
     * 免去回传正文的开销与误清空风险。返回移动后的条目；源不存在返回 null。
     */
    @Synchronized
    fun move(id: String, newFolder: String): KnowledgeEntry? {
        val srcRel = sanitizePath(id)
        val src = dir.resolve("$srcRel.md")
        if (!src.exists()) return null
        val base = srcRel.substringAfterLast('/')
        val folderRel = sanitizeFolder(newFolder)
        val rel = if (folderRel.isEmpty()) base else "$folderRel/$base"
        val dst = dir.resolve("$rel.md")
        if (dst != src) {
            runCatching {
                dst.parent?.createDirectories()
                Files.move(src, dst)
                pruneEmptyDirs(src.parent)
            }
            reload()
        }
        return entries.firstOrNull { it.id == rel }
    }

    private fun slug(title: String): String =
        title.trim().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifBlank { "kb-" + System.currentTimeMillis() }

    /** 防目录穿越的单段清洗：只留字母/数字/下划线/连字符（CJK 属字母，保留中文）。 */
    private fun sanitizeSeg(seg: String): String =
        seg.replace(Regex("[^\\p{L}\\p{N}_-]"), "").ifBlank { "kb-" + System.currentTimeMillis() }

    /** 分类目录清洗：按 `/`(或 `\`) 分段，逐段清洗、丢弃空段与 `.`/`..`，用 `/` 重接（保留空格与 CJK）。 */
    private fun sanitizeFolder(folder: String): String =
        folder.split('/', '\\')
            .map { it.trim().replace(Regex("[^\\p{L}\\p{N}_ -]"), "").trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .joinToString("/")

    /** vault 相对路径清洗（分类段 + 末段文件名），同样防穿越。 */
    private fun sanitizePath(rel: String): String {
        val segs = rel.split('/', '\\').map { it.trim() }.filter { it.isNotEmpty() && it != "." && it != ".." }
        if (segs.isEmpty()) return "kb-" + System.currentTimeMillis()
        val folder = segs.dropLast(1).joinToString("/") { it.replace(Regex("[^\\p{L}\\p{N}_ -]"), "") }.trim('/')
        val name = sanitizeSeg(segs.last())
        return if (folder.isEmpty()) name else "$folder/$name"
    }

    /** 从 [from] 起向上删除空目录，止于 vault 根（不删根本身）。 */
    private fun pruneEmptyDirs(from: Path?) {
        var p = from
        while (p != null && p.startsWith(dir) && p != dir) {
            val empty = runCatching { Files.newDirectoryStream(p).use { !it.iterator().hasNext() } }.getOrDefault(false)
            if (!empty) break
            if (!runCatching { Files.delete(p); true }.getOrDefault(false)) break
            p = p.parent
        }
    }

    private fun parse(file: Path): KnowledgeEntry {
        val text = file.readText()
        val rel = dir.relativize(file).toString().replace('\\', '/')
        val id = rel.removeSuffix(".md")
        val folder = id.substringBeforeLast('/', "").let { if (it == id) "" else it }
        var title = file.nameWithoutExtension
        var tags = emptyList<String>()
        var body = text
        if (text.startsWith("---")) {
            val end = text.indexOf("\n---", 3)
            if (end > 0) {
                val front = text.substring(3, end)
                body = text.substring(end + 4).trim()
                for (line in front.lines()) {
                    val i = line.indexOf(':'); if (i < 0) continue
                    val key = line.substring(0, i).trim().lowercase(); val value = line.substring(i + 1).trim()
                    when (key) {
                        "title" -> if (value.isNotBlank()) title = value
                        "tags" -> tags = value.split(',', '，').map { it.trim() }.filter { it.isNotBlank() }
                    }
                }
            }
        }
        return KnowledgeEntry(id, title, body, tags, folder)
    }

    companion object {
        /** `[[目标]]` / `[[目标|别名]]` 双链，取 group1=目标。 */
        private val WIKILINK = Regex("""\[\[([^\]|\n]+?)(?:\|[^\]\n]+?)?]]""")
    }

    /** 首启释放的预置知识库框架：既有 WindyAgent 自述手册，也有留给服主填的运营模板骨架。 */
    private fun seedFramework() {
        for ((rel, content) in KnowledgeFramework.FILES) {
            val f = dir.resolve(rel)
            runCatching { f.parent?.createDirectories(); f.writeText(content.trimIndent().trim() + "\n") }
        }
    }
}
