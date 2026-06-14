package org.windy.windyagent.text

/**
 * 聊天/文本粗分词（词云用）：拉丁/数字整词 + 中文双字组 + 停用词过滤。
 * 无分词库的廉价办法——英文/命令名准，中文偏碎。Velocity(代理层捕获聊天) 与 Bukkit(单机兜底) 共用。
 */
object ChatTokenizer {
    private val LATIN = Regex("[a-zA-Z0-9_]{2,}")
    private val CJK = Regex("[\\u4e00-\\u9fa5]+")
    private val STOP = setOf(
        "the", "and", "you", "for", "that", "this", "with", "are", "was", "but", "not", "your",
        "什么", "怎么", "可以", "没有", "知道", "这个", "那个", "一个", "我们", "你们", "他们", "现在", "就是", "不是", "这样"
    )

    fun tokens(text: String): List<String> {
        val out = ArrayList<String>()
        LATIN.findAll(text.lowercase()).forEach { if (it.value !in STOP) out.add(it.value) }
        CJK.findAll(text).forEach { m -> val s = m.value; for (i in 0 until s.length - 1) { val bg = s.substring(i, i + 2); if (bg !in STOP) out.add(bg) } }
        return out
    }
}
