package org.windy.windyagent.safety

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Operation audit trail. Failures to write the optional file are intentionally
 * non-fatal; the main flow should not break because audit storage is unavailable.
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
