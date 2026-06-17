package org.windy.windyagent

import org.windy.windyagent.llm.EmbeddingProvider
import org.windy.windyagent.llm.FallbackProvider
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

/**
 * 元任务（路由分类 / RAG 查询扩展）用的便宜模型：复用主 provider 的 base-url/key，只换模型名。
 * 未配 `llm.fast-model` 则返回 null（元任务退回主模型）。
 */
fun buildFastProvider(cfg: AgentConfig): LLMProvider? {
    val m = cfg.fastModel().ifBlank { return null }
    return when (cfg.provider().lowercase()) {
        "ollama" -> OllamaProvider(cfg.ollamaUrl(), m)
        "openai" -> OpenAICompatProvider(cfg.apiBaseUrl(), m, cfg.apiKey())
        else -> ClaudeProvider(cfg.apiKey(), m, cfg.apiBaseUrl().ifBlank { null })
    }
}

/**
 * 构建主 LLM Provider。如果配置了 fallback，自动包装成 [FallbackProvider]。
 */
fun buildProvider(cfg: AgentConfig): LLMProvider {
    val primary = buildSingleProvider(cfg.provider(), cfg.apiBaseUrl(), cfg.model(), cfg.apiKey(), cfg.ollamaUrl())

    if (!cfg.fallbackEnabled()) return primary

    // 构建备用 Provider 列表
    val fallbacks = cfg.fallbackProviders().mapNotNull { pc ->
        val p = (pc["provider"] as? String) ?: return@mapNotNull null
        val url = (pc["api-base-url"] as? String) ?: ""
        val model = (pc["model"] as? String) ?: return@mapNotNull null
        val key = (pc["api-key"] as? String) ?: cfg.apiKey()  // 未填则复用主 key
        runCatching { buildSingleProvider(p, url, model, key, cfg.ollamaUrl()) }.getOrNull()
    }

    if (fallbacks.isEmpty()) return primary

    val all = listOf(primary) + fallbacks
    return FallbackProvider(all)
}

/** 构建单个 Provider（按 provider 名称分发）。 */
private fun buildSingleProvider(provider: String, baseUrl: String, model: String, apiKey: String, ollamaUrl: String): LLMProvider = when (provider.lowercase()) {
    "ollama" -> OllamaProvider(ollamaUrl, model)
    "openai" -> {
        check(apiKey.isNotBlank()) { "api-key is required for provider: openai" }
        OpenAICompatProvider(baseUrl, model, apiKey)
    }
    else -> { // claude
        check(apiKey.isNotBlank()) { "api-key is required for provider: claude" }
        ClaudeProvider(apiKey, model, baseUrl.ifBlank { null })
    }
}
