package org.windy.windyagent.ui

/**
 * 统一日志风格。
 *
 * 启动横幅：LuckPerms 风格的简约 ASCII 艺术字。
 * 运行日志：TrChat 风格的 `[WindyAgent] 模块 | 消息` 前缀格式。
 *
 * 用 Minecraft 颜色码 § 而非 ANSI（日志常落文件，ANSI 会变乱码）。
 */
object WindyLog {

    const val VERSION = "1.1-SNAPSHOT"

    /** 前缀常量（TrChat 风格） */
    private const val PREFIX = "§8[§3Windy§bAgent§8]"

    /** 给一条消息加统一前缀。TrChat 格式：`[WindyAgent] 模块 | 消息` */
    fun tag(tag: String, msg: String): String = "$PREFIX §b$tag §8| §3$msg"

    /**
     * 启动横幅（LuckPerms 风格的简约 ASCII 艺术字）。
     *
     * LuckPerms 原版：
     * ```
     *         __
     *   |    |__)   LuckPerms v5.5.57
     *   |___ |      Running on Bukkit - Youer
     * ```
     *
     * WindyAgent 版：
     * ```
     *         __                 _
     *   |    |  \   ___  _ _  _| |_
     *   |___ |   \ | .'|| | ||   _|
     *        |___/ |__,||___||_|_|
     * ```
     */
    fun banner(info: List<Pair<String, String>>): String {
        val art = arrayOf(
            "        __                 _",
            "  |    |  \\   ___  _ _  _| |_",
            "  |___ |   \\ | .'|| | ||   _|",
            "       |___/ |__,||___||_|_|",
        )
        val sb = StringBuilder()
        sb.appendLine()
        art.forEach { sb.appendLine(it) }
        sb.appendLine("  §3WindyAgent §bv$VERSION")
        sb.appendLine()
        info.forEach { (k, v) ->
            sb.appendLine("$PREFIX §b$k §8| §3$v")
        }
        return sb.toString()
    }
}
