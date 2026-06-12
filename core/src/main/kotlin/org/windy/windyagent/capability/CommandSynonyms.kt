package org.windy.windyagent.capability

/**
 * 中英「语义-lite」同义词表：把常见中文/口语词扩展到相关英文命令关键词，
 * 让关键词检索在**没有 embedding 接口**时也能用中文命中英文命令（如「传送」→ /tp /home /warp）。
 *
 * Minecraft 命令是有界领域，一张表覆盖绝大多数运营/玩法查询；结构化、零基建、可审计、易扩展。
 * 匹配方式：query 若**包含** key（子串），就把其英文关键词并入检索词。
 */
object CommandSynonyms {

    val MAP: Map<String, List<String>> = mapOf(
        "传送" to listOf("tp", "teleport", "tpa", "tpaccept", "tphere", "back"),
        "瞬移" to listOf("tp", "teleport", "tpa"),
        "家" to listOf("home", "sethome", "delhome", "homes"),
        "回家" to listOf("home"),
        "传送点" to listOf("warp", "setwarp", "warps"),
        "地标" to listOf("warp", "warps"),
        "出生点" to listOf("spawn", "setspawn"),
        "主城" to listOf("spawn", "hub", "lobby"),
        "经济" to listOf("money", "balance", "bal", "eco", "economy", "pay", "baltop"),
        "钱" to listOf("money", "balance", "bal", "pay"),
        "金币" to listOf("money", "balance", "eco"),
        "余额" to listOf("balance", "bal", "money"),
        "转账" to listOf("pay", "send"),
        "商店" to listOf("shop", "buy", "sell", "market", "ah", "auction"),
        "拍卖" to listOf("auction", "ah", "market"),
        "领地" to listOf("claim", "region", "land", "plot", "rg", "res", "residence", "lands"),
        "圈地" to listOf("claim", "land"),
        "地皮" to listOf("plot", "plotme"),
        "权限" to listOf("perm", "permission", "lp", "luckperms", "pex", "group"),
        "称号" to listOf("tag", "prefix", "nick", "nickname"),
        "昵称" to listOf("nick", "nickname"),
        "封禁" to listOf("ban", "ipban", "tempban", "banlist"),
        "封号" to listOf("ban", "ipban"),
        "禁言" to listOf("mute", "tempmute"),
        "踢人" to listOf("kick"),
        "踢出" to listOf("kick"),
        "警告" to listOf("warn", "warning"),
        "飞行" to listOf("fly"),
        "飞" to listOf("fly"),
        "速度" to listOf("speed", "walkspeed", "flyspeed"),
        "模式" to listOf("gamemode", "gm", "creative", "survival", "spectator"),
        "创造" to listOf("gamemode", "gmc", "creative"),
        "生存" to listOf("gamemode", "gms", "survival"),
        "给予" to listOf("give", "item", "kit"),
        "物品" to listOf("give", "item", "i"),
        "礼包" to listOf("kit", "kits"),
        "公告" to listOf("broadcast", "bc", "say", "announce", "alert"),
        "广播" to listOf("broadcast", "bc", "say"),
        "天气" to listOf("weather", "rain", "sun"),
        "时间" to listOf("time", "day", "night"),
        "治疗" to listOf("heal", "feed"),
        "修复" to listOf("repair", "fix"),
        "附魔" to listOf("enchant"),
        "任务" to listOf("quest", "mission", "task"),
        "投票" to listOf("vote", "votes"),
        "签到" to listOf("sign", "daily", "checkin"),
        "传送请求" to listOf("tpa", "tpaccept", "tpdeny"),
        "帮助" to listOf("help", "?"),
        "管理" to listOf("admin", "manage", "op"),
        "重载" to listOf("reload", "rl")
    )

    /** 把查询里命中的中文/口语词扩展为英文关键词（去重）。 */
    fun expand(queryLower: String): List<String> =
        MAP.asSequence().filter { queryLower.contains(it.key) }.flatMap { it.value.asSequence() }.distinct().toList()
}
