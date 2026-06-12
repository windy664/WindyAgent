package org.windy.windyagent.knowledge

/** 一条知识：来自 knowledge/ 目录下的一个 .md 文件。 */
data class KnowledgeEntry(
    val id: String,
    val title: String,
    val content: String,
    val tags: List<String> = emptyList()
)
