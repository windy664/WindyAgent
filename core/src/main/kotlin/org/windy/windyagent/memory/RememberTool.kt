package org.windy.windyagent.memory

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.safety.RequestContext

/**
 * 让 Agent 把「值得长期记住的事」写入长期记忆（跨会话、重启后仍在）。
 * scope 默认 = 当前用户（[RequestContext.sessionId]）；显式传 "global" 记为全服记忆。
 */
class RememberTool(private val memory: LongTermMemory) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = "remember"
    override val description =
        "把关于当前玩家的稳定偏好、或重要的服务器约定记入长期记忆（跨会话保留）。仅记值得长期保留的信息，不要记琐碎/一次性内容。"
    override val inputSchema = """{"type":"object","properties":{"content":{"type":"string","description":"要记住的内容，一句话讲清"},"scope":{"type":"string","description":"作用域：留空=当前玩家；填 global=全服通用记忆"},"tags":{"type":"array","items":{"type":"string"},"description":"可选标签"}},"required":["content"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val node = mapper.readTree(inputJson)
        val content = node["content"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少 content 参数")
        val scope = node["scope"]?.asText()?.takeIf { it.isNotBlank() } ?: RequestContext.sessionId()
        val tags = node["tags"]?.mapNotNull { it.asText()?.takeIf { t -> t.isNotBlank() } } ?: emptyList()
        val e = memory.remember(scope, content, tags)
        ToolResult.success(toolCallId, "已记住（#${e.id}，作用域：${e.scope}）：$content")
    }.getOrElse { ToolResult.error(toolCallId, "记忆写入失败：${it.message}") }
}
