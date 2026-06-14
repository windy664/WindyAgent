package org.windy.windyagent.behavior

import com.fasterxml.jackson.databind.ObjectMapper

/**
 * 行为分析层：把 [BehaviorDatabase] 里的原始计数聚合成对外 JSON（看板 / 总线消费）。
 * **平台无关、纯确定性**——T0 描述统计、T1 规则分群、单人规则画像都在这算（SQL 聚合 + 阈值规则），
 * 不碰 LLM、不碰任何载体 API。LLM 只在更上层把这些指标「翻成人话洞察」，别让它碰这里的计算。
 *
 * 采集器（bukkit 侧）只管把事件计数写进 db；本类只读 db 出汇总。两者经 db 解耦。
 */
class BehaviorAnalytics(
    private val db: BehaviorDatabase,
    private val churnDays: Int,
    private val activeMinutes: Int,
    private val newbieDays: Int
) {
    private val mapper = ObjectMapper()

    /** T0 面板。 */
    fun statsJson(): String {
        val s = db.stats(System.currentTimeMillis())
        val n = mapper.createObjectNode()
        n.put("totalPlayers", s.totalPlayers)
        n.put("active1d", s.active1d)
        n.put("active7d", s.active7d)
        n.put("newToday", s.newToday)
        n.put("avgPlaytimeMin", s.avgPlaytimeSec / 60)
        n.put("totalDeaths", s.totalDeaths)
        n.put("totalBlocksBroken", s.totalBlocksBroken)
        n.put("totalCrafts", s.totalCrafts)
        n.put("totalAdvancements", s.totalAdvancements)
        val arr = n.putArray("topPlaytime")
        s.topPlaytime.forEach { (name, sec) -> arr.addObject().put("name", name).put("playtimeMin", sec / 60) }
        return n.toString()
    }

    /** 看板重型面板：在线趋势 + 7×24 时段热力 + 行为时间线，一次拉齐。 */
    fun boardJson(): String {
        val now = System.currentTimeMillis()
        val n = mapper.createObjectNode()
        val tr = n.putArray("trend"); db.trendDaily(now, 7).forEach { (d, c) -> tr.addObject().put("day", d).put("peak", c) }
        val hm = n.putArray("heatmap"); db.heatmap().forEach { row -> val a = hm.addArray(); row.forEach { a.add(it) } }
        val fd = n.putArray("feed"); db.feed(20).forEach { r -> fd.addObject().put("ts", r.ts).put("name", r.name).put("type", r.type).put("detail", r.detail) }
        return n.toString()
    }

    /** 接收 Velocity 代理层送来的聊天词频（绕开 Bukkit 聊天事件 bug）。 */
    fun recordChatWords(words: Map<String, Int>) = db.bumpWords("chat", words)

    /** 词云：某来源(cmd/chat) Top-N 高频词。 */
    fun wordsJson(source: String, limit: Int): String {
        val arr = mapper.createArrayNode()
        db.topWords(source.ifBlank { "cmd" }, if (limit > 0) limit else 80).forEach { (w, c) -> arr.addObject().put("word", w).put("count", c) }
        return arr.toString()
    }

    /** T1 分群。 */
    fun segmentsJson(): String {
        val seg = db.segments(System.currentTimeMillis(), churnDays, activeMinutes, newbieDays)
        val n = mapper.createObjectNode()
        seg.forEach { (k, v) -> n.put(k, v) }
        return n.toString()
    }

    /** 单人画像：原始计数 + **解读层**（行为标签 / 主玩法 / 活跃时段 / 派生指标）。 */
    fun playerJson(name: String): String {
        val p = db.player(name) ?: return mapper.createObjectNode().put("error", "未找到玩家「$name」（可能还没产生行为数据）").toString()
        val now = System.currentTimeMillis()
        val hours = p.playtimeSec / 3600.0
        val deathsPerH = if (hours > 0) p.deaths / hours else 0.0
        val blocks = p.blocksPlaced + p.blocksBroken
        val avgSessionMin = if (p.sessions > 0) p.playtimeSec / 60.0 / p.sessions else 0.0
        val recencyDays = (now - p.lastSeen) / 86_400_000.0
        val ageDays = (now - p.firstSeen) / 86_400_000.0

        // 行为标签（规则解读；阈值取常识值，后续可调/可配）
        val tags = ArrayList<String>()
        tags += when { hours < 1 -> "萌新"; hours < 10 -> "常规玩家"; hours < 50 -> "常驻玩家"; else -> "肝帝" }
        if (p.blocksPlaced > 1500) tags += "建筑党"
        if (p.blocksBroken > 3000) tags += "挖矿党"
        if (p.advancements >= 15) tags += "探索者"
        if (p.crafts > 800) tags += "合成狂"
        if (deathsPerH >= 2 && hours >= 0.5) tags += "脆皮"
        if (avgSessionMin > 120) tags += "长时在线"
        if (recencyDays > churnDays) tags += "流失风险" else if (recencyDays <= 1) tags += "近期活跃"
        if (ageDays <= newbieDays) tags += "新加入"

        // 主玩法：各维度归一打分取最高
        val scores = linkedMapOf("建造" to blocks / 60.0, "探索" to p.advancements * 4.0, "生存挂机" to hours * 1.5, "战斗" to p.deaths * 1.0)
        val playstyle = scores.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: "未知"

        // 活跃时段
        val hist = db.playerHourHistogram(p.uuid)
        val periods = intArrayOf((0..5).sumOf { hist[it] }, (6..11).sumOf { hist[it] }, (12..17).sumOf { hist[it] }, (18..23).sumOf { hist[it] })
        val activePeriod = if (periods.all { it == 0 }) "未知"
            else arrayOf("深夜(0-6)", "上午(6-12)", "下午(12-18)", "晚上(18-24)")[periods.indices.maxByOrNull { periods[it] } ?: 0]

        val n = mapper.createObjectNode()
        n.put("name", p.name); n.put("uuid", p.uuid)
        n.put("firstSeen", p.firstSeen); n.put("lastSeen", p.lastSeen)
        n.put("playtimeMin", p.playtimeSec / 60); n.put("sessions", p.sessions)
        n.put("deaths", p.deaths); n.put("commands", p.commands); n.put("chats", p.chats)
        n.put("blocksPlaced", p.blocksPlaced); n.put("blocksBroken", p.blocksBroken)
        n.put("crafts", p.crafts); n.put("advancements", p.advancements)
        // ↓ 画像解读层
        n.put("playstyle", playstyle)
        n.put("activePeriod", activePeriod)
        n.put("deathsPerHour", Math.round(deathsPerH * 100) / 100.0)
        n.put("avgSessionMin", avgSessionMin.toInt())
        n.put("ageDays", ageDays.toInt())
        n.put("recencyDays", recencyDays.toInt())
        n.putPOJO("tags", tags)
        n.putPOJO("activeHours", hist.toList())
        return n.toString()
    }
}
