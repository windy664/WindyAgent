package org.windy.windyagent.knowledge

import org.slf4j.LoggerFactory

/**
 * 内置**只读**参考库：官方文档这类事实性资料（CMI、以后 Jobs / 领地 / MC 机制……），
 * 打进 jar 当资源，启动时加载进内存，**不落进服主的 vault**。与 [KnowledgeManager]（可写 vault）
 * 分层：这里是「别人的事实」，那边是「你服务器的知识」。
 *
 * 检索联合：[KnowledgeManager] 把本库条目并进它的统一检索索引，Agent 的 `knowledge_search`
 * 两边一起搜、无感。前端可另开只读节点展示（[packs]）；要改某篇 → 复制成 vault 副本覆盖（override，后续做）。
 *
 * 两个资源根（全 ASCII 目录名，避开 jar 内中文资源名编码坑）：
 * ```
 * /knowledge-builtin/   # 第一方内容（使用手册等），入 git、随插件版本更新
 * /knowledge-ref/       # 爬取的官方文档（CMI/DH），不入 git（DMCA），本地构建时打进 jar
 * ```
 * 每个根下：
 * ```
 * <root>/_packs.txt            # id|显示名|简介，每行一个 pack
 * <root>/<pack>/_manifest.txt  # 资源文件名|虚拟路径(=分类/条目id，可中文)
 * <root>/<pack>/<file>.md      # 带 frontmatter(title/tags/source) 的正文
 * ```
 * 换版本/加 pack 只需替换资源，无需改代码（新 pack 记得进对应根的 `_packs.txt`）。
 * pack id 需全局唯一（跨根也不重复）。
 */
class ReferenceLibrary private constructor(
    val entries: List<KnowledgeEntry>,
    val packs: List<PackInfo>
) {
    /** 一个内置文档包的元信息（id=资源目录名，count=实际加载条数）。 */
    data class PackInfo(val id: String, val name: String, val desc: String, val count: Int)

    fun size(): Int = entries.size
    fun get(id: String): KnowledgeEntry? = entries.firstOrNull { it.id == id }

    companion object {
        private val log = LoggerFactory.getLogger(ReferenceLibrary::class.java)
        /** 第一方内容根（使用手册等，入 git、**永远加载**、不受 packs 白名单约束）。 */
        private const val BUILTIN = "/knowledge-builtin"
        /** 爬取文档根（CMI/DH，gitignore、受 packs 白名单约束）。 */
        private const val SCRAPED = "/knowledge-ref"
        val EMPTY = ReferenceLibrary(emptyList(), emptyList())

        /**
         * 加载内置参考库（合并两根）。[enabledPacks] 只约束**爬取文档**（[SCRAPED]）：
         * null/空 = 全部；否则只留命中 id。第一方手册（[BUILTIN]）**始终全量加载**，不受此约束——
         * 保证 AI 永远拿得到插件自述，也让老用户换 jar 自动更新手册。
         */
        fun load(enabledPacks: Set<String>? = null): ReferenceLibrary {
            val entries = ArrayList<KnowledgeEntry>()
            val packs = ArrayList<PackInfo>()
            // 根 -> 是否受 packs 白名单约束
            for ((root, filtered) in listOf(BUILTIN to false, SCRAPED to true)) {
                for (line in readLines("$root/_packs.txt")) {
                    val parts = line.split('|')
                    val id = parts.getOrNull(0)?.trim().orEmpty()
                    if (id.isEmpty()) continue
                    if (filtered && !enabledPacks.isNullOrEmpty() && id !in enabledPacks) continue
                    if (packs.any { it.id == id }) { log.warn("参考库 pack id 重复，跳过：$id"); continue }
                    val name = parts.getOrNull(1)?.trim().orEmpty().ifEmpty { id }
                    val desc = parts.getOrNull(2)?.trim().orEmpty()
                    val loaded = loadPack(root, id)
                    entries += loaded
                    packs += PackInfo(id, name, desc, loaded.size)
                }
            }
            if (entries.isNotEmpty()) log.info("已加载内置参考库：${packs.joinToString("、") { "${it.name}(${it.count})" }}，共 ${entries.size} 条")
            return ReferenceLibrary(entries, packs)
        }

        private fun loadPack(root: String, pack: String): List<KnowledgeEntry> {
            val out = ArrayList<KnowledgeEntry>()
            for (line in readLines("$root/$pack/_manifest.txt")) {
                val i = line.indexOf('|')
                val res = (if (i < 0) line else line.substring(0, i)).trim()
                val virtual = (if (i < 0) line else line.substring(i + 1)).trim().removeSuffix(".md")
                if (res.isEmpty() || virtual.isEmpty()) continue
                val text = readResource("$root/$pack/$res") ?: run { log.warn("参考库资源缺失：$root/$pack/$res"); continue }
                val (title, tags, body) = parseFrontmatter(text, virtual.substringAfterLast('/'))
                val folder = virtual.substringBeforeLast('/', "").let { if (it == virtual) "" else it }
                out += KnowledgeEntry(virtual, title, body, tags, folder)
            }
            return out
        }

        /** 解析 frontmatter：取 title/tags，其余为正文；无 frontmatter 时标题回落 [fallbackTitle]。 */
        private fun parseFrontmatter(text: String, fallbackTitle: String): Triple<String, List<String>, String> {
            var title = fallbackTitle
            var tags = emptyList<String>()
            var body = text
            if (text.startsWith("---")) {
                val end = text.indexOf("\n---", 3)
                if (end > 0) {
                    val front = text.substring(3, end)
                    body = text.substring(end + 4).trim()
                    for (l in front.lines()) {
                        val c = l.indexOf(':'); if (c < 0) continue
                        val k = l.substring(0, c).trim().lowercase(); val v = l.substring(c + 1).trim()
                        when (k) {
                            "title" -> if (v.isNotBlank()) title = v
                            "tags" -> tags = v.split(',', '，').map { it.trim() }.filter { it.isNotBlank() }
                        }
                    }
                }
            }
            return Triple(title, tags, body)
        }

        private fun readResource(path: String): String? =
            ReferenceLibrary::class.java.getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) }

        private fun readLines(path: String): List<String> =
            readResource(path)?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") } ?: emptyList()
    }
}
