package org.windy.windyagent.llm

/** 文本嵌入接口。返回与输入等长、按输入顺序对齐的向量列表。 */
interface EmbeddingProvider {
    val name: String
    fun embed(texts: List<String>): List<FloatArray>
}
