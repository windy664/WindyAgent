package org.windy.windyagent

import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 配置热重载：监听 config 文件变化，触发回调。
 *
 * 使用 Java WatchService 监听文件系统事件。
 * 组件可注册 listener 接收重载通知，无需重启服务器。
 *
 * 用法：
 * ```
 * val watcher = ConfigWatcher(dataDir)
 * watcher.addListener("safety") { newCfg -> guard.reload(newCfg) }
 * watcher.start()
 * ```
 */
class ConfigWatcher(private val configDir: Path) {

    private val log = LoggerFactory.getLogger(ConfigWatcher::class.java)
    private val listeners = CopyOnWriteArrayList<ConfigListener>()
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "config-watcher").apply { isDaemon = true } }

    @Volatile private var running = false
    @Volatile private var lastModified = 0L

    /** 注册一个配置变更监听器。 */
    fun addListener(name: String, callback: (AgentConfig) -> Unit) {
        listeners.add(ConfigListener(name, callback))
        log.info("已注册配置监听器：{}", name)
    }

    /** 移除监听器。 */
    fun removeListener(name: String) {
        listeners.removeAll { it.name == name }
    }

    fun start() {
        if (running) return
        if (!Files.exists(configDir)) {
            log.warn("配置目录不存在：{}", configDir)
            return
        }
        running = true
        executor.submit { watchLoop() }
        log.info("配置热重载已启动，监听：{}", configDir)
    }

    fun stop() {
        running = false
        executor.shutdown()
    }

    /** 手动触发一次重载（供命令调用）。 */
    fun reload() {
        triggerReload()
    }

    private fun watchLoop() {
        val watchService = FileSystems.getDefault().newWatchService()
        configDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)
        log.info("WatchService 已注册，等待配置变更...")

        while (running) {
            val key: WatchKey = try {
                watchService.poll(2, TimeUnit.SECONDS) ?: continue
            } catch (e: InterruptedException) {
                break
            }

            for (event in key.pollEvents()) {
                val fileName = (event.context() as? Path)?.toString() ?: continue
                if (fileName == "windyagent-config.yml" || fileName.endsWith(".yml")) {
                    // 防抖：文件可能多次触发（编辑器保存时）
                    val now = System.currentTimeMillis()
                    if (now - lastModified < 1000) continue
                    lastModified = now

                    log.info("检测到配置文件变更：{}", fileName)
                    Thread.sleep(500) // 等文件写完
                    triggerReload()
                }
            }
            key.reset()
        }
        watchService.close()
    }

    private fun triggerReload() {
        val newCfg = runCatching { AgentConfig.load(configDir) }.getOrElse {
            log.error("重新加载配置失败：{}", it.message)
            return
        }
        log.info("配置已重载，通知 {} 个监听器", listeners.size)
        for (listener in listeners) {
            runCatching { listener.callback(newCfg) }
                .onFailure { log.warn("监听器「{}」处理重载失败：{}", listener.name, it.message) }
        }
    }

    private data class ConfigListener(val name: String, val callback: (AgentConfig) -> Unit)
}
