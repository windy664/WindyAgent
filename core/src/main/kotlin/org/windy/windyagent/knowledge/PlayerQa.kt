package org.windy.windyagent.knowledge

import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.llm.LLMProvider
import org.windy.windyagent.rag.LlmQueryExpander

/**
 * 玩家问答：给普通玩家(不可信来源)用的**只答疑、不执行**的轻量助手。
 *
 * 与完整 Agent 的区别：**完全不挂工具、不进 ReAct/Plan 循环**——只做"检索知识库 → LLM 据此作答"。
 * 玩家因此无法借 AI 踢人 / 查他人数据 / 跑命令 / 写记忆，安全边界清晰；也省 token（单次调用）。
 * 检索命中不足时用 [expander] 扩词再检索（同主检索的兜底策略）。
 */
class PlayerQa(
    private val llm: LLMProvider,
    private val knowledge: KnowledgeStore,
    private val expander: LlmQueryExpander? = null,
    private val serverDesc: String = "本服务器"
) {
    fun answer(query: String): String {
        var hits = knowledge.search(query, 4)
        if (hits.isEmpty() && expander != null) {
            val terms = expander.expand(query)
            if (terms.isNotEmpty()) hits = knowledge.search("$query ${terms.joinToString(" ")}", 4)
        }
        val ctx = if (hits.isEmpty()) "（知识库暂无相关内容）"
        else hits.joinToString("\n\n") { "【${it.title}】\n${it.content}" }

        val sys = "你是$serverDesc 的玩家答疑助手，只负责答疑。严格遵守：" +
            "① 只依据下面【服务器知识库】回答，用简短友好的中文；" +
            "② 知识库没有的，就回答「这个我不太清楚，可以问问管理员」，绝不编造；" +
            "③ 你只能答疑、不能也不会执行任何操作（踢人/给物品/改服务器/查别人数据都做不到）；玩家让你做这些时，礼貌说明你只是答疑助手。\n\n" +
            "【服务器知识库】\n$ctx"

        return runCatching { llm.chat(sys, listOf(LLMMessage.User(query))).textContent }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "（暂时答不上来，稍后再试）"
    }
}
