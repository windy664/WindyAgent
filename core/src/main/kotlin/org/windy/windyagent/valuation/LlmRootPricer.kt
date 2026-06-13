package org.windy.windyagent.valuation

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.llm.LLMProvider

/**
 * 用 LLM 给「配方溯源够不着的物品」估种子价（EMC 法的补充，模仿 item-alchemy 那种按稀有度手工标定的思路）。
 *
 * 关键设计：**LLM 只做"归入哪个稀有度档"(它擅长的分类)，不报精确数字(它不擅长)**；档→金币值由配置 [tiers] 决定，
 * 精确价靠后续传播派生。这样既一致、可解释、可人工调档值，又省 token。
 *  - [price]：只估"根"，一次请求。
 *  - [priceBatched]：估全部悬空物，分批多次请求。
 * 失败/解析不出 → 跳过该批，绝不阻断。
 */
class LlmRootPricer(private val llm: LLMProvider, private val tiers: Map<String, Double>) {

    private val log = LoggerFactory.getLogger(LlmRootPricer::class.java)
    private val mapper = ObjectMapper()

    fun price(bundle: RootsBundle): Map<String, Double> {
        if (bundle.roots.isEmpty()) return emptyMap()
        return callLlm(bundle.roots, anchorStr(bundle), bundle.currency)
    }

    fun priceBatched(bundle: RootsBundle, batchSize: Int = 80): Map<String, Double> {
        if (bundle.roots.isEmpty()) return emptyMap()
        val anchor = anchorStr(bundle)
        val chunks = bundle.roots.chunked(batchSize)
        val out = HashMap<String, Double>()
        chunks.forEachIndexed { i, chunk ->
            log.info("[物品估值] LLM 估值批次 {}/{}（{} 条）…", i + 1, chunks.size, chunk.size)
            out.putAll(callLlm(chunk, anchor, bundle.currency))
        }
        return out
    }

    private fun anchorStr(bundle: RootsBundle): String = bundle.anchors.entries.take(8)
        .joinToString("、") { "${it.key.substringAfter(':')}=${it.value.toInt()}" }
        .ifBlank { "铁锭=10、钻石=320" }

    /** 档说明，如 "common(≈4) / uncommon(≈16) / rare(≈64) / epic(≈256) / legendary(≈1024)"。 */
    private fun tierMenu(): String = tiers.entries.joinToString(" / ") { "${it.key}(≈${it.value.toInt()})" }

    private fun callLlm(roots: List<RootInfo>, anchorStr: String, currency: String): Map<String, Double> {
        if (roots.isEmpty()) return emptyMap()
        val tierMenu = tierMenu()
        val sys = """
            你给 Minecraft 服务器经济估值。下面这批物品无法用合成配方推算价值
            （矿物/掉落/原料，或用机器、祭坛、铸造等特殊方式制造）。
            请把每个物品**按稀有度/获取难度归入一档**（参照同类物的获取成本，越难得越高档）。
            可选档（本服货币 $currency，括号是大致价位，仅供你判断档位）：$tierMenu。
            价位锚点参考：$anchorStr。
            只输出一个 JSON 对象：键=物品 id（与输入完全一致），值=档名（必须是上面之一）。
            不要解释、不要代码块标记、不要多余文字。
        """.trimIndent()
        val body = roots.joinToString("\n") { "${it.id} ${it.name} (被${it.deg}配方依赖)" }

        log.info("[物品估值] 正在调用 LLM（{}）给 {} 个物品分档估价…", llm.name, roots.size)
        val t0 = System.currentTimeMillis()
        val ans = runCatching { llm.chat(sys, listOf(LLMMessage.User(body))).textContent }
            .getOrElse { log.warn("[物品估值] LLM 调用失败：{}", it.message); null } ?: return emptyMap()
        log.info("[物品估值] LLM 返回（耗时 {}ms），解析中…", System.currentTimeMillis() - t0)

        val s = ans.indexOf('{'); val e = ans.lastIndexOf('}')
        if (s < 0 || e <= s) { log.warn("[物品估值] LLM 未返回 JSON：{}", ans.take(120)); return emptyMap() }
        val node = runCatching { mapper.readTree(ans.substring(s, e + 1)) }.getOrElse { return emptyMap() }

        val ids = roots.mapTo(HashSet()) { it.id }
        val out = HashMap<String, Double>()
        node.fields().forEach { (k, v) ->
            if (k !in ids) return@forEach
            // 优先按档名映射；模型若直接给了数字也兜底接受
            val tierVal = tiers[v.asText().trim().lowercase()]
            val value = tierVal ?: (if (v.isNumber && v.asDouble() > 0) v.asDouble() else null)
            if (value != null && value > 0) out[k] = value
        }
        log.info("[物品估值] LLM 为 {} 个中的 {} 个归档定价", roots.size, out.size)
        return out
    }
}
