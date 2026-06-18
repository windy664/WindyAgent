package org.windy.windyagent.platform.velocity

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.command.ValueExecutor
import org.windy.windyagent.llm.LLMProvider
import org.windy.windyagent.safety.TrustLevel
import org.windy.windyagent.valuation.LlmRootPricer
import org.windy.windyagent.valuation.RootInfo
import org.windy.windyagent.valuation.RootsBundle
import java.util.concurrent.TimeUnit

/**
 * 中心（Velocity）侧 value 执行后端：`value <子命令> <子服> [参数]` → 校验子服已连 →
 * 总线派发 `value_<子命令>` 到子服 → 回文本。DB/引擎都在子服，这里只过命令与结果。
 * 长操作（build/set）子服内异步执行、即时回「已开始」，故不会撞总线超时。
 * value llm 特殊：子服无 LLM，故中心编排——取根→本机 LLM 定价→回写子服。
 */
class RemoteValueExecutor(
    private val bus: MessageBus,
    private val timeoutMs: Long,
    private val llm: LLMProvider,
    private val batchSize: Int,
    private val rarityTiers: Map<String, Double>,
    private val connectedServers: () -> Set<String>
) : ValueExecutor {
    private val mapper = ObjectMapper()
    private val log = LoggerFactory.getLogger(RemoteValueExecutor::class.java)

    override fun execute(sub: String, rest: String, trust: TrustLevel): String {
        if (sub == "servers") {
            val s = connectedServers()
            return if (s.isEmpty()) "当前没有已连接的子服（子服需启用 cross-server 并推送能力目录）。"
            else "已连接子服：\n" + s.sorted().joinToString("\n") { "  $it" }
        }
        val parts = rest.split(Regex("\\s+"), limit = 2)
        val server = parts.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return "用法：value $sub <子服> ${argHint(sub)}"
        val connected = connectedServers()
        if (server !in connected) {
            return "子服「$server」未连接。已连：${if (connected.isEmpty()) "（无）" else connected.sorted().joinToString("、")}"
        }
        if (sub == "llm") return runLlm(server, parts.getOrNull(1)?.trim().equals("all", true))
        val subArgs = parts.getOrNull(1)?.trim().orEmpty()
        val argsJson = buildArgs(sub, subArgs) ?: return "用法：value $sub <子服> ${argHint(sub)}"
        return dispatchText(server, "value_$sub", argsJson)
    }

    /** 中心编排 LLM 估值：① 子服回根 → ② 本机 LLM 定价 → ③ 回写子服级联重算。 */
    private fun runLlm(server: String, all: Boolean): String {
        val scope = if (all) "全部悬空物" else "悬空的根"
        log.info("[Value{}] 子服 {} —— 步骤1/3 取{}…", if (all) " all" else "", server, scope)
        val argsJson = if (all) mapper.createObjectNode().put("all", true).toString() else "{}"
        val rootsJson = dispatchText(server, "value_roots", argsJson)
        val bundle = runCatching { parseBundle(rootsJson) }
            .getOrElse { return "取清单失败（子服回复：${rootsJson.take(120)}）" }
        if (bundle.roots.isEmpty()) {
            log.info("[Value] 子服 {} 无{}，无需估值", server, scope)
            return "「$server」没有需要 LLM 估值的物品（都已解析，或先 value build $server）。"
        }
        log.info("[Value] 子服 {} —— 步骤2/3 取到 {} 个{}，调用 LLM 估价…", server, bundle.roots.size, scope)
        val pricer = LlmRootPricer(llm, rarityTiers)
        val seeds = if (all) pricer.priceBatched(bundle, batchSize) else pricer.price(bundle)
        if (seeds.isEmpty()) {
            log.warn("[Value] 子服 {} —— LLM 未给出有效估价", server)
            return "LLM 没给出有效估价（${bundle.roots.size} 个），未改动。可重试或改用 value set。"
        }
        log.info("[Value] 子服 {} —— 步骤3/3 LLM 给 {} 个估价，下发子服级联重算…", server, seeds.size)
        val seedNode = mapper.createObjectNode()
        val s = seedNode.putObject("seeds")
        seeds.forEach { (k, v) -> s.put(k, v) }
        val applied = dispatchText(server, "value_seed", seedNode.toString())
        log.info("[Value] 子服 {} —— 完成", server)
        return "LLM 给 ${seeds.size}/${bundle.roots.size} 个物品定价并下发「$server」。\n$applied"
    }

    /** 手动解析子服回的 RootsBundle JSON（plain jackson，不依赖 kotlin module）。 */
    private fun parseBundle(json: String): RootsBundle {
        val node = mapper.readTree(json)
        val roots = node["roots"]?.mapNotNull { r ->
            val id = r["id"]?.asText()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            RootInfo(id, r["name"]?.asText() ?: id, r["deg"]?.asInt() ?: 0)
        } ?: emptyList()
        val anchors = HashMap<String, Double>()
        node["anchors"]?.fields()?.forEach { (k, v) -> if (v.isNumber) anchors[k] = v.asDouble() }
        return RootsBundle(roots, anchors, node["currency"]?.asText() ?: "金币")
    }

    /** @return argsJson；参数不足返回 null（让上层打印用法）。 */
    private fun buildArgs(sub: String, a: String): String? = when (sub) {
        "build", "status", "orphans" -> "{}"
        "get", "unset" -> {
            if (a.isBlank()) null else mapper.createObjectNode().put("item", a).toString()
        }
        "set" -> {
            // <物品> <价> [备注]
            val t = a.split(Regex("\\s+"), limit = 3)
            val item = t.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
            val value = t.getOrNull(1)?.toDoubleOrNull() ?: return null
            mapper.createObjectNode().put("item", item).put("value", value).put("note", t.getOrNull(2) ?: "").toString()
        }
        else -> "{}"
    }

    private fun argHint(sub: String) = when (sub) {
        "get", "unset" -> "<物品>"
        "set" -> "<物品> <价> [备注]"
        else -> ""
    }

    private fun dispatchText(server: String, action: String, args: String): String = runCatching {
        val reply = bus.dispatch(server, action, args, timeoutMs).get(timeoutMs + 1000, TimeUnit.MILLISECONDS)
        if (reply.success) reply.content else "子服未成功：${reply.content}"
    }.getOrElse { "派发到「$server」失败：${it.message}" }
}
