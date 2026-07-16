package org.windy.windyagent.skill

import java.io.File
import org.slf4j.LoggerFactory

/**
 * 默认技能释放：首次启动（技能库为空）时，把打进 jar 的默认技能（`resources/skills/`）按
 * `skills/_manifest.txt` 清单复制到中心库目录，行为对齐配置文件的首启释放（[org.windy.windyagent.AgentConfig.load]）。
 *
 * 为什么用清单：jar 内无法可靠地「列目录」，故用一份显式清单驱动复制。资源只在 core 模块放一份，
 * 随 core 被 shade 进 Velocity / bukkit 各自的 fat jar，运行时经 core 的类加载器即可读到。
 */
object SkillDefaults {

    private val log = LoggerFactory.getLogger(SkillDefaults::class.java)
    private const val MANIFEST = "/skills/_manifest.txt"

    /** 目标 [dir] 不存在或为空时，按清单释放默认技能。返回释放的文件数（已存在/无清单则 0）。 */
    fun releaseIfEmpty(dir: File): Int {
        if (hasAnySkill(dir)) return 0
        val entries = readManifest()
        if (entries.isEmpty()) return 0
        if (!dir.exists()) dir.mkdirs()
        var n = 0
        for (rel in entries) {
            val res = "/skills/$rel"
            val ins = SkillDefaults::class.java.getResourceAsStream(res)
            if (ins == null) { log.warn("默认技能资源缺失：$res"); continue }
            runCatching {
                val target = File(dir, rel)
                target.parentFile?.mkdirs()
                ins.use { input -> target.outputStream().use { input.copyTo(it) } }
                n++
            }.onFailure { log.warn("释放默认技能 $rel 失败：${it.message}") }
        }
        if (n > 0) log.info("已释放 $n 个默认技能到 ${dir.absolutePath}")
        return n
    }

    /** 目录里是否已有任何技能文件（.md/.kether 或子目录），有则不覆盖。 */
    private fun hasAnySkill(dir: File): Boolean {
        val files = dir.listFiles() ?: return false
        return files.any { it.isDirectory || it.name.endsWith(".md", true) || it.name.endsWith(".kether", true) }
    }

    private fun readManifest(): List<String> {
        val ins = SkillDefaults::class.java.getResourceAsStream(MANIFEST) ?: return emptyList()
        return ins.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        }
    }
}

