package org.windy.windyagent.knowledge

/**
 * 关键词检索实现（结构化优先的 RAG 起步版）。
 *
 * 对查询切词后，按词在 标题/标签/正文 中的出现次数加权打分。
 * 中文无空格，故除英文/数字 token 外，额外对连续中文段取相邻二元组，
 * 让「春节礼包」这类查询也能命中。无外部依赖、结果确定、可审计。
 */
class KeywordKnowledgeStore(private val entries: List<KnowledgeEntry>) : KnowledgeStore {

    override fun size(): Int = entries.size

    override fun search(query: String, topK: Int): List<KnowledgeEntry> =
        searchScored(query, topK).map { it.first }

    /** 同 [search]，但带分数（供调用方按阈值过滤弱命中，如长期记忆召回门控）。 */
    fun searchScored(query: String, topK: Int): List<Pair<KnowledgeEntry, Int>> {
        val terms = extractTerms(query)
        if (terms.isEmpty()) return emptyList()
        return entries
            .map { it to score(it, terms) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(topK)
    }

    private fun score(e: KnowledgeEntry, terms: List<String>): Int {
        val title = e.title.lowercase()
        val tags = e.tags.joinToString(" ").lowercase()
        val content = e.content.lowercase()
        var s = 0
        for (t in terms) {
            s += count(title, t) * 3
            s += count(tags, t) * 2
            s += count(content, t)
        }
        return s
    }

    private fun count(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var idx = haystack.indexOf(needle)
        var c = 0
        while (idx >= 0) {
            c++
            idx = haystack.indexOf(needle, idx + needle.length)
        }
        return c
    }

    companion object {
        /** 切词：英文/数字按字母数字段提取(长度≥2)；中文段提取相邻二元组(单字段保留单字)。 */
        fun extractTerms(query: String): List<String> {
            val q = query.lowercase()
            val terms = linkedSetOf<String>()
            Regex("[a-z0-9]+").findAll(q).forEach { if (it.value.length >= 2) terms += it.value }
            Regex("[\\u4e00-\\u9fff]+").findAll(q).forEach { m ->
                val s = m.value
                if (s.length == 1) {
                    terms += s
                } else {
                    for (i in 0 until s.length - 1) terms += s.substring(i, i + 2)
                }
            }
            return terms.toList()
        }
    }
}
