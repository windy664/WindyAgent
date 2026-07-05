package org.windy.windyagent.knowledge

/** 一条知识：来自 knowledge/ 目录下的一个 .md 文件。 */
data class KnowledgeEntry(
    val id: String,
    val title: String,
    val content: String,
    val tags: List<String> = emptyList(),
    /** 分组路径（frontmatter folder:），支持多层如 "活动/2026"；空=未分类。前端据此建目录树。 */
    val folder: String = ""
)

/** 知识条目的轻量元数据（**不含正文**），给前端建树/关系图/反链用。links=正文双链解析到的目标 id。 */
data class KbMeta(
    val id: String,
    val title: String,
    val folder: String,
    val tags: List<String>,
    val links: List<String>
)
