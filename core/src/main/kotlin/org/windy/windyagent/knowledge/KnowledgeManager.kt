package org.windy.windyagent.knowledge

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * 可读写的知识库（在 [KnowledgeLoader] 之上加增删改 + 热重载）。**本身就是 [KnowledgeStore]**：
 * search/size 委托给当前内存检索库，故 KnowledgeSearchTool 直接拿它当 store 用，编辑后立即可检索。
 * 每条知识仍是 `<dir>/<id>.md`（frontmatter: title/tags + 正文）；编辑就是写/删文件 + reload。
 * 给 WebUI 的知识库管理用。
 */
class KnowledgeManager(private val dir: Path) : KnowledgeStore {

    @Volatile private var entries: List<KnowledgeEntry> = emptyList()
    @Volatile private var store: KnowledgeStore = KeywordKnowledgeStore(emptyList())

    init { reload() }

    override fun search(query: String, topK: Int) = store.search(query, topK)
    override fun size() = store.size()
    fun list(): List<KnowledgeEntry> = entries

    @Synchronized
    fun reload() {
        if (!dir.exists()) runCatching { dir.createDirectories() }
        val files = runCatching { Files.newDirectoryStream(dir, "*.md").use { it.toList() } }.getOrElse { emptyList() }
        entries = files.mapNotNull { runCatching { parse(it) }.getOrNull() }.sortedBy { it.title }
        store = KeywordKnowledgeStore(entries)
    }

    /** 新增或覆盖一条（id 为空=新建，按标题生成 id）。返回落库后的条目。 */
    @Synchronized
    fun save(id: String?, title: String, content: String, tags: List<String>): KnowledgeEntry {
        val fileId = sanitize(id?.takeIf { it.isNotBlank() } ?: slug(title))
        val front = StringBuilder("---\n").append("title: ").append(title.trim()).append("\n")
        if (tags.isNotEmpty()) front.append("tags: ").append(tags.joinToString(", ")).append("\n")
        front.append("---\n")
        dir.resolve("$fileId.md").writeText(front.toString() + content.trim() + "\n")
        reload()
        return entries.firstOrNull { it.id == fileId } ?: KnowledgeEntry(fileId, title, content, tags)
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val ok = runCatching { Files.deleteIfExists(dir.resolve(sanitize(id) + ".md")) }.getOrDefault(false)
        if (ok) reload()
        return ok
    }

    private fun slug(title: String): String =
        title.trim().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifBlank { "kb-" + System.currentTimeMillis() }

    /** 防目录穿越：只留字母/数字/下划线/连字符（CJK 属字母，保留中文标题）。 */
    private fun sanitize(id: String): String =
        id.replace(Regex("[^\\p{L}\\p{N}_-]"), "").ifBlank { "kb-" + System.currentTimeMillis() }

    private fun parse(file: Path): KnowledgeEntry {
        val text = file.readText()
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
        return KnowledgeEntry(file.nameWithoutExtension, title, body, tags)
    }
}
