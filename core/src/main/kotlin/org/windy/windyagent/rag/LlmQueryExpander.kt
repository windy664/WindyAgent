package org.windy.windyagent.rag

import org.slf4j.LoggerFactory
import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.llm.LLMProvider

/**
 * 无 embedding 的「语义增强」：用 chat 模型把用户查询扩展成一组检索关键词
 * （中英、同义、口语→规范词），喂给稀疏检索器，以填补「中文意图 ↔ 英文/规范词」的语义鸿沟。
 *
 * 这是「稀疏检索 + LLM」式 RAG 的查询扩展环节，不依赖任何向量模型。
 * 仅在稀疏检索命中不足时调用（成本门控由调用方负责）；失败/空返回空列表，调用方退回原查询、绝不阻断。
 */
class LlmQueryExpander(private val llm: LLMProvider) {

    private val log = LoggerFactory.getLogger(LlmQueryExpander::class.java)

    fun expand(query: String): List<String> {
        val answer = runCatching {
            llm.chat(SYSTEM, listOf(LLMMessage.User("用户查询：$query")))
                .textContent
        }.getOrElse { log.debug("查询扩展失败：{}", it.message); null } ?: return emptyList()

        // 解析逗号/顿号/空白分隔的词；去重、去过长噪声、剔除原样回显
        return answer.split(Regex("[,，、\\s]+"))
            .map { it.trim().trim('"', '\'', '。', '.', '：', ':') }
            .filter { it.isNotEmpty() && it.length <= 20 }
            .distinct()
            .take(8)
    }

    companion object {
        private val SYSTEM = """
            你是检索查询扩展器。用户在搜索一个 Minecraft 服务器的知识库或命令。
            把用户查询扩展成 3~8 个简短检索关键词，覆盖：同义词、口语→规范说法、相关概念，
            并**中英文都给**（如「会员」→ 会员, vip, 特权, member；「传送」→ 传送, tp, teleport, home, warp）。
            只输出这些关键词，用逗号分隔，不要解释、不要编号、不要其它任何文字。
        """.trimIndent()
    }
}
