package org.windy.windyagent.llm

import org.windy.windyagent.tools.AgentTool

interface LLMProvider {
    val name: String
    fun chat(systemPrompt: String, messages: List<LLMMessage>, tools: List<AgentTool>): LLMResponse
    fun chat(systemPrompt: String, messages: List<LLMMessage>): LLMResponse = chat(systemPrompt, messages, emptyList())
}
