package org.windy.windyagent.knowledge

/**
 * 知识库检索抽象（RAG 的 Retriever）。
 *
 * 当前实现是关键词检索（[KeywordKnowledgeStore]）——结构化优先、零外部依赖、可审计。
 * 将来知识量大/非结构化时，换成向量检索实现即可；[KnowledgeSearchTool] 与 Agent 核心无需改动。
 */
interface KnowledgeStore {
    /** 返回与 query 最相关的至多 topK 条知识（按相关度降序）。 */
    fun search(query: String, topK: Int = 5): List<KnowledgeEntry>

    /** 当前已加载的知识条数。 */
    fun size(): Int
}
