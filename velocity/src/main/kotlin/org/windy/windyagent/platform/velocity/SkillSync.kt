package org.windy.windyagent.platform.velocity

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.skill.SkillRegistry
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 把**中心权威技能库**里的脚本技能下发到子服执行（脚本需 Bukkit API，只能在子服跑）。
 *
 * 下发=经总线 `skill_save` 动作把技能文件写到子服 `skills/` 目录（子服侧 [reload] + 重建能力目录，
 * 见 BukkitCapabilityHandler.onSkillsChanged → CapabilitySync.rebuildSoon）。每个脚本技能据其
 * `targets` 决定下发到哪些子服（空=全部在线子服）。文字技能不下发（中心本地执行）。
 *
 * 触发：① 子服上线（中心收到其能力目录 [onServerAnnounced]）；② WebUI「立即下发」[syncAll]。
 * 在后台线程跑，避免阻塞总线回调/HTTP 线程。
 */
class SkillSync(
    private val bus: MessageBus,
    private val registry: SkillRegistry,
    private val timeoutMs: Long
) {
    private val log = LoggerFactory.getLogger(SkillSync::class.java)
    private val mapper = ObjectMapper()
    private val exec = Executors.newSingleThreadExecutor { r ->
        Thread(r, "windyagent-skillsync").apply { isDaemon = true }
    }

    /** 子服宣告能力目录（=上线/重连）时触发：把命中该服的脚本技能下发过去。异步。 */
    fun onServerAnnounced(catalogJson: String) {
        val server = runCatching { mapper.readTree(catalogJson)["server"]?.asText() }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: return
        exec.submit { runCatching { syncTo(server) }.onFailure { log.warn("[SkillSync] 向子服「{}」下发技能失败：{}", server, it.message) } }
    }

    /** 把命中 [server] 的脚本技能逐个下发。返回下发条数。 */
    fun syncTo(server: String): Int {
        val scripts = registry.all().filter { it.isScript && it.appliesTo(server) }
        if (scripts.isEmpty()) return 0
        var n = 0
        for (def in scripts) {
            val content = registry.read(def.handle) ?: continue
            val payload = mapper.createObjectNode()
                .put("handle", def.handle)
                .put("md", content.md)
                .put("script", content.script)
                .put("isScript", true)
                .toString()
            val ok = runCatching {
                bus.dispatch(server, "skill_save", payload, timeoutMs).get(timeoutMs + 1000, TimeUnit.MILLISECONDS).success
            }.getOrDefault(false)
            if (ok) n++ else log.warn("[SkillSync] 下发技能「{}」到子服「{}」未成功", def.name, server)
        }
        if (n > 0) log.info("[SkillSync] 已向子服「{}」下发 {} 个脚本技能", server, n)
        return n
    }

    /** WebUI「立即下发」：把脚本技能同步到所有命中的在线子服。返回每服结果摘要。 */
    fun syncAll(onlineServers: Set<String>): String {
        if (onlineServers.isEmpty()) return "无在线子服"
        return onlineServers.joinToString("；") { srv -> "$srv:${runCatching { syncTo(srv) }.getOrDefault(-1)}" }
    }
}
