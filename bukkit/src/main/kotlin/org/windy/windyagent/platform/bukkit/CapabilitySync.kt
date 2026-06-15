package org.windy.windyagent.platform.bukkit

import com.fasterxml.jackson.databind.ObjectMapper
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.bus.CapabilityCatalog
import org.windy.windyagent.bus.CapabilityCommand
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 子服侧能力目录同步：**启动后建一次目录、插件变更去抖重建**，通过 [deliver] 下发
 * （provider 模式 = 经总线推回中心；standalone/hub = 放进本机注册表）。
 *
 * 取代旧的「中心每次提问现扫子服」——目录在子服本地建好、主动送达，中心零往返检索。
 *
 * **不用 Bukkit 调度器**：实测在 NeoForge 混合端（Youer）上 runTaskLaterAsynchronously 不触发，
 * 改用独立 ScheduledExecutorService（守护线程），脱离 tick/调度器，混合端也必跑。
 * 读 CommandMap 无需主线程，故后台线程安全。配合总线侧「连上后重推缓存目录」自愈早期未连场景。
 */
class CapabilitySync(
    private val plugin: JavaPlugin,
    private val actions: BukkitActions,
    private val serverName: String,
    private val deliver: (CapabilityCatalog) -> Unit,
    /** 技能目录补充项（默认空）：每次重建目录时调用，可在内部热重载 skills/ 目录，
     *  返回的条目并入命令目录一并推给中心，让 search_capabilities 能搜到技能。 */
    private val skillCatalog: () -> List<CapabilityCommand> = { emptyList() }
) : Listener {

    private val exec = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "windyagent-catalog").apply { isDaemon = true }
    }
    @Volatile private var scheduled = false

    fun start() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        schedule(5) // 启动后 ~5s 建初版（等插件加载完）
    }

    /** 外部触发重建目录（如中心下发技能后，让新技能尽快进目录被 search_capabilities 搜到）。 */
    fun rebuildSoon(delaySec: Long = 3) = schedule(delaySec)

    @EventHandler fun onPluginEnable(e: PluginEnableEvent) = schedule(3)
    @EventHandler fun onPluginDisable(e: PluginDisableEvent) = schedule(3)

    /** 去抖：已排程则跳过，让其执行时取最新状态。 */
    private fun schedule(delaySec: Long) {
        if (scheduled) return
        scheduled = true
        exec.schedule({
            scheduled = false
            runCatching {
                val commands = actions.capabilityCommands() + skillCatalog()
                val sig = signature(commands)
                // 变更门控：签名与上次已提交一致就不推（中心靠持久记忆存活）
                if (sig == readSig()) {
                    plugin.logger.info("能力目录无变更（${commands.size} 条），跳过下发")
                } else {
                    val catalog = CapabilityCatalog(serverName, commands, System.currentTimeMillis())
                    plugin.logger.info("能力目录变更 → ${commands.size} 条命令（server=$serverName），下发…")
                    deliver(catalog)
                    writeSig(sig)
                }
            }.onFailure { plugin.logger.warning("生成能力目录失败：${it.message}") }
        }, delaySec, TimeUnit.SECONDS)
    }

    /** 命令集合的稳定签名（排除 builtAt，仅看内容），用于检测插件增删改。 */
    private fun signature(commands: List<CapabilityCommand>): String {
        val body = commands.sortedBy { it.name }
            .joinToString("\n") { "${it.name}|${it.aliases.sorted().joinToString(",")}|${it.description}|${it.source}" }
        return Integer.toHexString(body.hashCode()) + ":" + commands.size
    }

    private val sigFile: File get() = File(plugin.dataFolder, "catalog.sig")
    private fun readSig(): String? = runCatching { if (sigFile.exists()) sigFile.readText().trim() else null }.getOrNull()
    private fun writeSig(sig: String) {
        runCatching { plugin.dataFolder.mkdirs(); sigFile.writeText(sig) }
            .onFailure { plugin.logger.warning("写入 catalog.sig 失败：${it.message}") }
    }

    companion object {
        private val mapper = ObjectMapper()
        /** 序列化目录为 JSON（经总线推送用）。 */
        fun toJson(catalog: CapabilityCatalog): String = mapper.writeValueAsString(catalog)
    }
}
