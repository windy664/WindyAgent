package org.windy.windyagent

import org.windy.windyagent.llm.EmbeddingProvider
import org.windy.windyagent.llm.LLMProvider
import org.windy.windyagent.llm.claude.ClaudeProvider
import org.windy.windyagent.llm.ollama.OllamaProvider
import org.windy.windyagent.llm.openai.OpenAICompatEmbeddingProvider
import org.windy.windyagent.llm.openai.OpenAICompatProvider
import org.windy.windyagent.safety.CommandGuard

/** 命令执行护栏（按 safety.mode + denylist 构建）。 */
fun buildCommandGuard(cfg: AgentConfig): CommandGuard =
    CommandGuard(CommandGuard.mode(cfg.safetyMode()), cfg.commandDenylist())

/**
 * 嵌入提供方（用于能力目录的语义检索 L3）。默认复用 LLM 的 base-url/key，只换 embedding 模型名。
 * 未启用或未配模型则返回 null（检索退回关键词 L2）。
 */
fun buildEmbeddingProvider(cfg: AgentConfig): EmbeddingProvider? {
    if (!cfg.embeddingEnabled() || cfg.embeddingModel().isBlank()) return null
    return OpenAICompatEmbeddingProvider(cfg.embeddingApiBaseUrl(), cfg.embeddingModel(), cfg.embeddingApiKey())
}

fun buildProvider(cfg: AgentConfig): LLMProvider = when (cfg.provider().lowercase()) {
    "ollama" -> OllamaProvider(cfg.ollamaUrl(), cfg.model())
    "openai" -> {
        check(cfg.apiKey().isNotBlank()) { "api-key is required for provider: openai" }
        OpenAICompatProvider(cfg.apiBaseUrl(), cfg.model(), cfg.apiKey())
    }
    else -> { // claude
        check(cfg.apiKey().isNotBlank()) { "api-key is required for provider: claude" }
        val baseUrl = cfg.apiBaseUrl().ifBlank { null }
        ClaudeProvider(cfg.apiKey(), cfg.model(), baseUrl)
    }
}
