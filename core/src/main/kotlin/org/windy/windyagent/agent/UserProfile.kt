package org.windy.windyagent.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.llm.LLMProvider
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * 用户画像：跨会话持续理解用户偏好/风格/需求模式。
 *
 * 每个 session/player 维护一份画像 JSON，每次对话后异步增量更新。
 * 下次对话时拼入 userMessage，让 Agent 更懂用户。
 *
 * 数据模型扁平化（避免复杂嵌套），可被 LLM 逐步丰富。
 */
data class UserProfile(
    val sessionId: String,
    var playstyle: String = "",
    var communicationStyle: String = "",
    var frequentCommands: MutableMap<String, Int> = mutableMapOf(),
    var preferences: MutableMap<String, String> = mutableMapOf(),
    var recentTopics: MutableList<String> = mutableListOf(),
    var lastUpdated: Long = 0
) {
    /** 序列化为人类可读文本（拼入 userMessage 用）。 */
    fun toText(): String {
        val parts = mutableListOf<String>()
        if (playstyle.isNotBlank()) parts += "玩法偏好：$playstyle"
        if (communicationStyle.isNotBlank()) parts += "沟通风格：$communicationStyle"
        val topCmds = frequentCommands.entries.sortedByDescending { it.value }.take(5)
        if (topCmds.isNotEmpty()) parts += "常用命令：${topCmds.joinToString(", ") { "${it.key}(${it.value}次)" }}"
        if (preferences.isNotEmpty()) parts += "偏好：" + preferences.entries.joinToString(", ") { "${it.key}=${it.value}" }
        if (recentTopics.isNotEmpty()) parts += "近期关注：${recentTopics.takeLast(5).joinToString("、")}"
        return if (parts.isEmpty()) "" else "[用户画像]\n" + parts.joinToString("\n")
    }
}

/**
 * 用户画像管理器：加载/保存/增量更新。
 */
class UserProfileManager(private val dir: Path) {
    private val log = LoggerFactory.getLogger(UserProfileManager::class.java)
    private val mapper = ObjectMapper().registerKotlinModule()
    private val cache = ConcurrentHashMap<String, UserProfile>()

    fun get(sessionId: String): UserProfile {
        return cache.computeIfAbsent(sessionId) { load(it) }
    }

    fun save(profile: UserProfile) {
        profile.lastUpdated = System.currentTimeMillis()
        cache[profile.sessionId] = profile
        runCatching {
            Files.createDirectories(dir)
            val file = dir.resolve("${sanitize(profile.sessionId)}.json")
            Files.write(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(profile).toByteArray(Charsets.UTF_8))
        }.onFailure { log.warn("画像保存失败：{}", it.message) }
    }

    /** 增量更新：用 LLM 从对话中提取关键信息合并到画像。 */
    fun updateFromConversation(sessionId: String, userMessage: String, assistantReply: String, llm: LLMProvider) {
        val profile = get(sessionId)
        val updatePrompt = """
            从以下对话片段中提取用户特征，输出 JSON（只填有明确证据的字段，无证据留空字符串）：
            {"playstyle":"玩法偏好(如生存/建造/红石/交易)","style":"沟通风格(如简洁/详细/随意)","commands":["常用命令1","常用命令2"],"topics":["关注话题1","关注话题2"],"prefs":{"key":"value"}}

            对话：
            用户：${userMessage.take(500)}
            助手：${assistantReply.take(500)}
        """.trimIndent()

        runCatching<Unit> {
            val resp = llm.chat("你是用户画像提取器，只输出 JSON。", listOf(LLMMessage.User(updatePrompt)))
            val text = resp.textContent?.trim() ?: return
            val start = text.indexOf('{'); val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return
            val node = mapper.readTree(text.substring(start, end + 1))
            node["playstyle"]?.asText()?.takeIf { it.isNotBlank() }?.let { profile.playstyle = it }
            node["style"]?.asText()?.takeIf { it.isNotBlank() }?.let { profile.communicationStyle = it }
            node["commands"]?.forEach { cmd ->
                cmd.asText()?.takeIf { it.isNotBlank() }?.let {
                    profile.frequentCommands[it] = (profile.frequentCommands[it] ?: 0) + 1
                }
            }
            node["topics"]?.forEach { t ->
                t.asText()?.takeIf { it.isNotBlank() }?.let {
                    if (it !in profile.recentTopics) profile.recentTopics.add(it)
                    if (profile.recentTopics.size > 20) profile.recentTopics.removeFirst()
                }
            }
            node["prefs"]?.fields()?.forEach { (k, v) ->
                v.asText()?.takeIf { it.isNotBlank() }?.let { profile.preferences[k] = it }
            }
            save(profile)
        }.onFailure { log.debug("画像更新失败（可忽略）：{}", it.message) }
    }

    private fun load(sessionId: String): UserProfile {
        val file = dir.resolve("${sanitize(sessionId)}.json")
        if (!Files.exists(file)) return UserProfile(sessionId)
        return runCatching {
            mapper.readValue(String(Files.readAllBytes(file), Charsets.UTF_8), UserProfile::class.java)
        }.getOrElse {
            log.warn("画像加载失败：{}", it.message)
            UserProfile(sessionId)
        }
    }

    private fun sanitize(s: String) = s.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
}
