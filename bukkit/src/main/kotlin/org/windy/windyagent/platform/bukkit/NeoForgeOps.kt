package org.windy.windyagent.platform.bukkit

import org.bukkit.plugin.java.JavaPlugin

/**
 * Forge / NeoForge 混合端的**专属能力**（按子服核心类型门控：非 forge/neoforge 直接拒）。
 *  - 模组清单：反射 `ModList.get().getMods()`。
 *  - 分维度 TPS：旁路捕获 `/neoforge tps`（NeoForge）/ `/forge tps`（旧 Forge）的输出。
 *
 * 命令捕获能否成功取决于宿主是否把该命令输出路由回 Bukkit sender（混合端各异），故对空结果有提示兜底。
 */
object NeoForgeOps {

    private fun classPresent(cn: String) = runCatching { Class.forName(cn); true }.getOrDefault(false)
    private fun modListClass(): String? = when {
        classPresent("net.neoforged.fml.ModList") -> "net.neoforged.fml.ModList"
        classPresent("net.minecraftforge.fml.ModList") -> "net.minecraftforge.fml.ModList"
        else -> null
    }

    /** 模组清单（id / 名称 / 版本）。 */
    fun modList(plugin: JavaPlugin): String {
        val cls = modListClass() ?: return "本服不是 Forge/NeoForge，没有模组清单。"
        return runCatching {
            val ml = Class.forName(cls).getMethod("get").invoke(null)
            val mods = ml.javaClass.getMethod("getMods").invoke(ml) as List<*>
            val lines = mods.filterNotNull().map { mi ->
                val id = str(mi, "getModId")
                val name = str(mi, "getDisplayName").ifBlank { id }
                val ver = str(mi, "getVersion")
                "- $name（$id）v$ver"
            }
            "本服共 ${lines.size} 个模组：\n" + lines.joinToString("\n")
        }.getOrElse { "读取模组清单失败：${it.message}" }
    }

    /**
     * 分维度 TPS：NeoForge/Forge 的 tps 命令**输出走服务器日志**（不回命令 sender），故用 [LogCapture] 抓日志。
     * 异步派发命令到主线程 + 开一个短日志捕获窗口，过滤出含 TPS 的行。
     */
    fun dimensionTps(plugin: JavaPlugin, actions: BukkitActions): String {
        val cmd = when {
            classPresent("net.neoforged.fml.ModList") -> "neoforge tps"
            classPresent("net.minecraftforge.fml.ModList") -> "forge tps"
            else -> return "本服不是 Forge/NeoForge，无分维度 TPS。"
        }
        val lines = LogCapture.capture(600) { actions.dispatchAsync(cmd) }
            .filter { it.contains("TPS") || it.contains("ms/tick") }
            .distinct()
        return if (lines.isEmpty())
            "未捕获到 `/$cmd` 输出（日志系统可能不便挂载捕获）。整体 TPS 见健康卡片。"
        else "【/$cmd】\n" + lines.joinToString("\n")
    }

    private fun str(obj: Any, method: String): String =
        runCatching { obj.javaClass.getMethod(method).invoke(obj)?.toString() }.getOrNull().orEmpty()
}
