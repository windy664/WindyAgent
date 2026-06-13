package org.windy.windyagent.valuation

/**
 * 「待 LLM 估值的根」打包：无合成配方、配方溯源算不出价、却被其它配方依赖的源头物
 * （矿物/掉落/原料）。EMC 法的死角，交给 LLM 按世界知识定种子价，再让传播级联出其余。
 * 经总线在子服(产出 roots)↔ 中心(有 LLM)之间传递，故定义在 core 共享。
 */
data class RootInfo(
    val id: String,
    val name: String,   // 中文名优先，回退英文/ id
    val deg: Int        // 被多少条配方作为材料依赖（越高越该优先定价）
)

/** roots + 货币锚点（供 LLM 校准量纲）+ 货币名。anchors 取自子服的 base-values。 */
data class RootsBundle(
    val roots: List<RootInfo> = emptyList(),
    val anchors: Map<String, Double> = emptyMap(),
    val currency: String = "金币"
)
