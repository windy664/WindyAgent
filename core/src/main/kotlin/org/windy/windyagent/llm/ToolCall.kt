package org.windy.windyagent.llm

data class ToolCall(val id: String, val name: String, val inputJson: String)
