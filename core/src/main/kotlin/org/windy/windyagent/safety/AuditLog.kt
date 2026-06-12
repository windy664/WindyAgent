package org.windy.windyagent.safety

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 操作审计：每条命令执行（含被拒）落盘 + 控制台，便于追溯"AI 干过什么、谁触发、是否被拦"。
 * 线程安全 append；写盘失败不影响主流程（仅控制台）。
 */
class AuditLog(private val file: Path?) {

    private val log = LoggerFactory.getLogger("WindyAgent-Audit")
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** @param decision ALLOW / DENY / WARN */
    @Synchronized
    fun record(session: String, action: String, command: String, decision: String, reason: String = "") {
        val line = "${LocalDateTime.now().format(fmt)} | session=$session | $action | cmd=$command | $decision${if (reason.isNotBlank()) " ($reason)" else ""}"
        log.info(line)
        val f = file ?: return
        runCatching {
            Files.write(
                f, (line + System.lineSeparator()).toByteArray(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND
            )
        }
    }
}
