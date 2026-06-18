package org.windy.windyagent.agent

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * 人格文件加载：读 `dataDir/personality.md`（可选），存在则追加到系统提示尾部。
 * 服主可在文件里自定义 Agent 的语气/风格/称呼/专长等，改完下次对话生效。
 */
object PersonalityLoader {
    private val log = LoggerFactory.getLogger(PersonalityLoader::class.java)

    fun load(dataDir: Path, fileName: String): String {
        val file = dataDir.resolve(fileName)
        if (!Files.exists(file)) return ""
        return runCatching {
            val text = String(Files.readAllBytes(file), Charsets.UTF_8).trim()
            if (text.isNotBlank()) {
                log.info("已加载人格文件：{}（{} 字符）", fileName, text.length)
                text
            } else ""
        }.getOrElse {
            log.warn("读取人格文件失败：{}", it.message)
            ""
        }
    }
}
