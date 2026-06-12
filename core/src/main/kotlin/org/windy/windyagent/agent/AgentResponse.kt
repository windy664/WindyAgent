package org.windy.windyagent.agent

data class AgentResponse(val message: String, val success: Boolean, val toolsExecuted: List<String>)
