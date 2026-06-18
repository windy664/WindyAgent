package org.windy.windyagent.knowledge

/**
 * 关键词检索实现（结构化优先的 RAG 起步版）。
 *
 * 对查询切词后，按词在 标题/标签/正文 中的出现次数加权打分。
 * 中文无空格，故除英文/数字 token 外，额外对连续中文段取相邻二元组，
 * 让「春节礼包」这类查询也能命中。无外部依赖、结果确定、可审计。
 *
 * 性能：构建时预计算 lowercase 字符串 + 提取 terms，避免每次搜索重复分配。
 */
class KeywordKnowledgeStore(entries: List<KnowledgeEntry>) : KnowledgeStore {

    // 预计算：每条 entry 的 lowercase 字段（构建一次，搜索多次）
    private data class Prepared(val entry: KnowledgeEntry, val titleLc: String, val tagsLc: String, val contentLc: String)
    private val prepared = entries.map { e ->
        Prepared(e, e.title.lowercase(), e.tags.joinToString(" ").lowercase(), e.content.lowercase())
    }

    override fun size(): Int = prepared.size

    override fun search(query: String, topK: Int): List<KnowledgeEntry> =
        searchScored(query, topK).map { it.first }

    /** 同 [search]，但带分数（供调用方按阈值过滤弱命中，如长期记忆召回门控）。 */
    fun searchScored(query: String, topK: Int): List<Pair<KnowledgeEntry, Int>> {
        val terms = extractTerms(query)
        if (terms.isEmpty()) return emptyList()
        return prepared
            .map { it.entry to score(it, terms) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(topK)
    }

    private fun score(p: Prepared, terms: List<String>): Int {
        var s = 0
        for (t in terms) {
            s += count(p.titleLc, t) * 3
            s += count(p.tagsLc, t) * 2
            s += count(p.contentLc, t)
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
