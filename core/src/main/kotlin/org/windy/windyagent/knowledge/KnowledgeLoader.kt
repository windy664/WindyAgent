package org.windy.windyagent.knowledge

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * 从 `<dataDir>/knowledge/` 下的 `.md` 文件加载知识，每个文件 = 一条知识。
 *
 * 文件格式（frontmatter 可选）：
 * ```
 * ---
 * title: 标题
 * tags: 标签1, 标签2
 * ---
 * 正文……
 * ```
 * 首次运行若目录不存在，会创建并写入一份示例，方便服主照着填。
 */
object KnowledgeLoader {

    fun load(dataDir: Path): KnowledgeStore {
        val dir = dataDir.resolve("knowledge")
        if (!dir.exists()) {
            runCatching {
                dir.createDirectories()
                dir.resolve("example.md").writeText(SAMPLE)
            }
        }
        val files = runCatching {
            Files.newDirectoryStream(dir, "*.md").use { it.toList() }
        }.getOrElse { emptyList() }
        val entries = files.mapNotNull { runCatching { parse(it) }.getOrNull() }
        return KeywordKnowledgeStore(entries)
    }

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
                    val i = line.indexOf(':')
                    if (i < 0) continue
                    val key = line.substring(0, i).trim().lowercase()
                    val value = line.substring(i + 1).trim()
                    when (key) {
                        "title" -> if (value.isNotBlank()) title = value
                        "tags" -> tags = value.split(',', '，').map { it.trim() }.filter { it.isNotBlank() }
                    }
                }
            }
        }
        return KnowledgeEntry(file.nameWithoutExtension, title, body, tags)
    }

    private val SAMPLE = """
        ---
        title: 示例知识条目
        tags: 示例, 用法
        ---
        把服务器相关的知识写成这样的 .md 文件放进 knowledge/ 目录，每个文件一条。
        Agent 会在需要时用 knowledge_search 检索它们，例如商品定价规则、会员权益、
        玩法说明、常见问答等。删掉本示例文件即可。
    """.trimIndent()
}
