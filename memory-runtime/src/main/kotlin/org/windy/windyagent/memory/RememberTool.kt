package org.windy.windyagent.memory

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.safety.RequestContext
import org.windy.windyagent.safety.TrustLevel

/**
 * 让 Agent 把「值得长期记住的事」写入长期记忆（跨会话、重启后仍在）。
 *
 * **信任门槛**：仅 TRUSTED 来源（管理方：网页控制台 / VC 控制台 / 有 windyagent.admin 的玩家）可写——
 * 挡住普通玩家 !ai 污染"有效记忆"。默认写入管理方共享域 [LongTermMemory.ADMIN]，跨所有可信通道互通；
 * 可显式传 "global" 记为全服记忆，或指定其它 scope（如某玩家名）。
 */
class RememberTool(private val memory: LongTermMemory) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = "remember"
    override val description =
        "把管理方的稳定偏好、或重要的服务器约定记入长期记忆（跨会话、跨管理通道共享）。仅记值得长期保留的信息，不要记琐碎/一次性内容。"
    override val inputSchema = """{"type":"object","properties":{"content":{"type":"string","description":"要记住的内容，一句话讲清"},"scope":{"type":"string","description":"作用域：留空=管理方共享(admin，各管理通道互通)；填 global=全服通用；或填某玩家名只记给该玩家"},"tags":{"type":"array","items":{"type":"string"},"description":"可选标签"}},"required":["content"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        // 信任门槛：不可信来源（普通玩家）一律不许写，保证记忆只来自管理方
        if (RequestContext.current() != TrustLevel.TRUSTED)
            return ToolResult.error(toolCallId, "仅管理员可写入长期记忆（当前来源不可信，已忽略）。")
        val node = mapper.readTree(inputJson)
        val content = node["content"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少 content 参数")
        // 默认写管理方共享域；显式 scope 优先（global / 玩家名 等）
        val scope = node["scope"]?.asText()?.takeIf { it.isNotBlank() } ?: LongTermMemory.ADMIN
        val tags = node["tags"]?.mapNotNull { it.asText()?.takeIf { t -> t.isNotBlank() } } ?: emptyList()
        val e = memory.remember(scope, content, tags)
        ToolResult.success(toolCallId, "已记住（#${e.id}，作用域：${e.scope}）：$content")
    }.getOrElse { ToolResult.error(toolCallId, "记忆写入失败：${it.message}") }
}
