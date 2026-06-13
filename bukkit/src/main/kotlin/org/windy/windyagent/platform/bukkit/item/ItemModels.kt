package org.windy.windyagent.platform.bukkit.item

/** 一个物品：id（namespaced）、来源模组、分类、中英文名、获取来源（craftable/raw）。 */
data class ModItem(
    val id: String,
    val mod: String,
    val category: String,   // block / item（粗分）
    val tier: Int?,         // 层级，本轮留空（schema 预留）
    val nameEn: String,
    val nameZh: String,
    val source: String
)

/** 一条估值结果。 */
data class Valuation(val id: String, val value: Double, val confidence: String, val basis: String)


/**
 * 一条配方的一个材料行。`recipeId` 用于把同一产物的多个配方分组
 * （估值时按 recipeId 重建每个配方、取最便宜路径）。
 */
data class RecipeRow(
    val output: String,
    val recipeId: String,
    val outputCount: Int,
    val ingredient: String,
    val count: Int
)

/**
 * 一条标签成员边：`tag`（带 # 前缀，如 #c:ingots/inferium）含成员 `member`
 * （可能是具体物品 id，也可能是另一个 #子标签——引擎按"标签值=成员最小值"递归求解）。
 */
data class TagEdge(val tag: String, val member: String)

data class ParseResult(val items: List<ModItem>, val recipes: List<RecipeRow>, val tags: List<TagEdge>)
