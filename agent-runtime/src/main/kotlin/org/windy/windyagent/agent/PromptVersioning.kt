package org.windy.windyagent.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors

/**
 * Prompt 模板版本化：SystemPrompt 存文件，支持版本历史。
 *
 * 机制：
 * - `dataDir/prompts/system.md` — 当前生效的系统提示
 * - `dataDir/prompts/history/` — 历史版本（带时间戳）
 * - 启动时：文件存在则读取，不存在则用默认 BASE
 * - 修改后自动保存 + 归档旧版本
 */
class PromptVersioning(private val dir: Path) {

    private val log = LoggerFactory.getLogger(PromptVersioning::class.java)
    private val mapper = ObjectMapper().registerKotlinModule()
    private val historyDir = dir.resolve("history")
    private val currentFile = dir.resolve("system.md")
    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    @Volatile private var currentVersion: String = ""
    @Volatile private var currentContent: String = ""

    /** 加载当前 prompt。不存在返回空串（调用方用默认 BASE）。 */
    fun load(): String {
        if (!Files.exists(currentFile)) return ""
        return runCatching {
            currentContent = String(Files.readAllBytes(currentFile), Charsets.UTF_8).trim()
            currentVersion = "v${Files.getLastModifiedTime(currentFile).toMillis()}"
            log.info("已加载自定义 prompt（{} 字符，版本 {}）", currentContent.length, currentVersion)
            currentContent
        }.getOrElse {
            log.warn("加载 prompt 失败：{}", it.message)
            ""
        }
    }

    /** 保存新版本（自动归档旧版本）。 */
    fun save(content: String) {
        runCatching<Unit> {
            Files.createDirectories(dir)
            Files.createDirectories(historyDir)
            // 归档旧版本
            if (Files.exists(currentFile)) {
                val ts = LocalDateTime.now().format(fmt)
                val archive = historyDir.resolve("system_${ts}.md")
                Files.copy(currentFile, archive)
                log.info("旧 prompt 已归档：{}", archive.fileName)
                // 只保留最近 20 个版本
                cleanupHistory()
            }
            Files.write(currentFile, content.toByteArray(Charsets.UTF_8))
            currentContent = content
            currentVersion = "v${System.currentTimeMillis()}"
            log.info("Prompt 已保存（{} 字符）", content.length)
        }.onFailure { log.warn("保存 prompt 失败：{}", it.message) }
    }

    /** 列出历史版本。 */
    fun listHistory(): List<VersionInfo> {
        if (!Files.exists(historyDir)) return emptyList()
        return runCatching<List<VersionInfo>> {
            Files.list(historyDir).use { stream ->
                stream.filter { it.fileName.toString().endsWith(".md") }
                    .sorted()
                    .map<VersionInfo> { f ->
                        VersionInfo(
                            f.fileName.toString(),
                            Files.getLastModifiedTime(f).toMillis(),
                            String(Files.readAllBytes(f), Charsets.UTF_8).length
                        )
                    }
                    .collect(Collectors.toList())
                    .reversed()
            }
        }.getOrDefault(emptyList())
    }

    /** 恢复到指定历史版本。 */
    fun restore(fileName: String): Boolean {
        val file = historyDir.resolve(fileName)
        if (!Files.exists(file)) return false
        save(String(Files.readAllBytes(file), Charsets.UTF_8))
        return true
    }

    private fun cleanupHistory() {
        runCatching {
            Files.list(historyDir).use { stream ->
                val files: List<Path> = stream.filter { it.fileName.toString().endsWith(".md") }
                    .sorted()
                    .collect(Collectors.toList())
                if (files.size > 20) {
                    files.subList(0, files.size - 20).forEach { Files.deleteIfExists(it) }
                }
            }
        }
    }

    data class VersionInfo(val fileName: String, val timestamp: Long, val size: Int)
}
