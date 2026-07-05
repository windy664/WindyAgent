package org.windy.windyagent.knowledge

/** 一条知识：来自 knowledge/ 目录下的一个 .md 文件（可编辑），或内置只读来源。 */
data class KnowledgeEntry(
    val id: String,
    val title: String,
    val content: String,
    val tags: List<String> = emptyList(),
    /** 分组路径（frontmatter folder:），支持多层如 "活动/2026"；空=未分类。前端据此建目录树。 */
    val folder: String = ""
)

/**
 * 只读知识来源：内置文档（静态，打进 jar：插件/模组 wiki）与子服能力目录（动态，手脚上报的命令）
 * 都实现它。其条目并入统一检索 + 目录树，但**标记只读、不可在面板/Obsidian 编辑**（改动只影响 jar/子服）。
 *
 * 函数式：每次调 [entries] 现取，故动态源（如能力目录）天然随子服上下线刷新。
 */
fun interface ReadOnlyKnowledge {
    fun entries(): List<KnowledgeEntry>
}

/**
 * 知识条目的轻量元数据（**不含正文**），给前端建树/关系图/反链用。links=正文双链解析到的目标 id。
 * [readOnly]=true 表示内置只读（前端渲染 🔒、禁编辑/删除/移动），来自 [ReadOnlyKnowledge]。
 */
data class KbMeta(
    val id: String,
    val title: String,
    val folder: String,
    val tags: List<String>,
    val links: List<String>,
    val readOnly: Boolean = false
)
