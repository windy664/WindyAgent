package org.windy.windyagent.tools

data class AgentResponse(val message: String, val success: Boolean, val toolsExecuted: List<String>)
