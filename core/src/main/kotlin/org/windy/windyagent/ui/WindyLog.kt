package org.windy.windyagent.ui

/**
 * 统一日志风格 + 启动横幅。
 *
 * 纯字符串构造，不依赖任何日志框架（slf4j / java.util.logging），
 * 由 Velocity 与 Bukkit 两端各自的 logger 直接吐出，保证两端观感一致。
 *
 * 约定：
 * - 每条日志带 `[模块]` 前缀（用 [tag]），日志级别交给宿主控制台前缀（[INFO]/[WARN]…）。
 * - 启动只打一个 [banner]，其余按域分 tag。
 */
object WindyLog {

    const val VERSION = "1.0-SNAPSHOT"

    /** 给一条消息加统一的 `[模块]` 前缀。slf4j 占位符 `{}` 原样保留。 */
    fun tag(tag: String, msg: String): String = "[$tag] $msg"

    /**
     * 启动横幅。盒内只放纯 ASCII（避免 CJK 宽度错位），盒下的信息行可含中文。
     *
     * @param info 形如 ("角色","中心 Agent · Velocity") 的键值对，逐行打在盒子下方。
     */
    fun banner(info: List<Pair<String, String>>): String {
        val w = 52
        val top = "╔" + "═".repeat(w) + "╗"
        val bot = "╚" + "═".repeat(w) + "╝"
        fun row(s: String) = "║ " + s.padEnd(w - 1) + "║"

        val keyW = (info.maxOfOrNull { it.first.length } ?: 0)
        val sb = StringBuilder()
        sb.append('\n')
        sb.append(top).append('\n')
        sb.append(row("")).append('\n')
        sb.append(row("W I N D Y   A G E N T")).append('\n')
        sb.append(row("AI Agent for Minecraft  ·  v$VERSION")).append('\n')
        sb.append(row("")).append('\n')
        sb.append(bot).append('\n')
        info.forEach { (k, v) -> sb.append("  ").append(k.padEnd(keyW)).append("   ").append(v).append('\n') }
        return sb.toString()
    }
}
