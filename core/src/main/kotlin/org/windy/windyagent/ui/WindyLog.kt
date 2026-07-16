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
 * - 不用 ANSI 颜色：日志常落文件，颜色码会变成 `[..m` 乱码。靠结构与对齐出效果。
 */
object WindyLog {

    const val VERSION = "1.1-SNAPSHOT"

    /** 给一条消息加统一的 `[模块]` 前缀。slf4j 占位符 `{}` 原样保留。 */
    fun tag(tag: String, msg: String): String = "[$tag] $msg"

    /**
     * 字符的终端显示宽度：CJK / 全角 / 假名 / 谚文算 2 格，其余 1 格。
     * 用于让中文键值也能按「显示宽度」而非「字符数」对齐（padEnd 只会数字符数 → 中文错位）。
     */
    private fun dispWidth(s: String): Int {
        var w = 0
        for (c in s) {
            val cp = c.code
            w += if (
                cp in 0x1100..0x115F ||   // 谚文字母
                cp in 0x2E80..0x303E ||   // CJK 部首 / 标点
                cp in 0x3041..0x33FF ||   // 假名 / CJK 符号
                cp in 0x3400..0x4DBF ||   // CJK 扩展 A
                cp in 0x4E00..0x9FFF ||   // CJK 统一表意
                cp in 0xA000..0xA4CF ||   // 彝文等
                cp in 0xAC00..0xD7A3 ||   // 谚文音节
                cp in 0xF900..0xFAFF ||   // CJK 兼容
                cp in 0xFE30..0xFE4F ||   // CJK 兼容形式
                cp in 0xFF00..0xFF60 ||   // 全角形式
                cp in 0xFFE0..0xFFE6      // 全角符号
            ) 2 else 1
        }
        return w
    }

    /** 按显示宽度右填充到 [width] 格。 */
    private fun padDisp(s: String, width: Int): String =
        s + " ".repeat((width - dispWidth(s)).coerceAtLeast(0))

    /**
     * "WINDYAGENT" 的字符画大字（figlet ANSI Shadow 风格：█ 块 + ▀ 阴影），两行堆叠：
     * 上 6 行 WINDY、下 6 行 AGENT（整词一行会超 80 列，故拆两行）。各行由脚本生成并逐行校验
     * 居中对齐——手绘极易错位，故固化为常量，勿手改单行长度。
     * 用块绘制字符（█ ▀，Block Elements，等宽终端单宽稳定）；MC 控制台均可正常显示。
     */
    private val ART = arrayOf(
        "██     ██  ██  ███    ██  ██████   ██    ██",
        "██     ██  ██  ████   ██  ██   ██   ██  ██",
        "██  █  ██  ██  ██ ██  ██  ██   ██    ████",
        "██ ███ ██  ██  ██  ██ ██  ██   ██     ██",
        " ███ ███   ██  ██   ████  ██████      ██",
        "  ▀   ▀    ▀▀  ▀▀    ▀▀▀  ▀▀▀▀▀       ▀▀",
        " █████    ██████   ███████  ███    ██  ████████",
        "██   ██  ██        ██       ████   ██     ██",
        "███████  ██   ███  █████    ██ ██  ██     ██",
        "██   ██  ██    ██  ██       ██  ██ ██     ██",
        "██   ██   ██████   ███████  ██   ████     ██",
        "▀▀   ▀▀    ▀▀▀▀▀   ▀▀▀▀▀▀▀  ▀▀    ▀▀▀     ▀▀",
    )

    /**
     * 启动横幅。
     *
     * 上半为标题盒：居中的 "WINDY" 字符画 + AGENT 副标（只放 ASCII，避免 CJK / emoji 在带
     * 右边框的盒内错位——右边框对宽度极敏感）；下半为信息区，采用「左竖线导槽 + 按显示宽度
     * 对齐的键」，无右边框故中文再多也不会错位。
     *
     * @param info 形如 ("角色","中心 Agent · Velocity") 的键值对，逐行打在标题盒下方。
     */
    fun banner(info: List<Pair<String, String>>): String {
        val pad = "  "                       // 整体左缩进
        val subtitle = "AI Agent for Minecraft  ·  v$VERSION"
        // 盒宽按最宽内容自适应（字符画 vs 副标，取大者），避免任一行撑破右边框
        val contentW = maxOf(ART.maxOf { it.length }, subtitle.length)
        val w = contentW + 4
        val inner = w - 2
        val top = "$pad╭" + "─".repeat(w) + "╮"
        val bot = "$pad╰" + "─".repeat(w) + "╯"
        // 盒内居中一行（内容纯 ASCII，用 length 即可）
        fun boxRow(s: String): String {
            val left = ((inner - s.length) / 2).coerceAtLeast(0)
            val right = (inner - s.length - left).coerceAtLeast(0)
            return "$pad│ " + " ".repeat(left) + s + " ".repeat(right) + " │"
        }

        val keyW = info.maxOfOrNull { dispWidth(it.first) } ?: 0

        val sb = StringBuilder()
        sb.append('\n')
        sb.append(top).append('\n')
        sb.append(boxRow("")).append('\n')
        ART.forEach { sb.append(boxRow(it)).append('\n') }
        sb.append(boxRow("")).append('\n')
        sb.append(boxRow(subtitle)).append('\n')
        sb.append(boxRow("")).append('\n')
        sb.append(bot).append('\n')
        if (info.isNotEmpty()) {
            sb.append("$pad│").append('\n')                       // 导槽起点
            info.forEach { (k, v) ->
                sb.append("$pad│  ").append(padDisp(k, keyW)).append("   ").append(v).append('\n')
            }
            sb.append("$pad╵").append('\n')                       // 导槽收尾
        }
        return sb.toString()
    }
}
