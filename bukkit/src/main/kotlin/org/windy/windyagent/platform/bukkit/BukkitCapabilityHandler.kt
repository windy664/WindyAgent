package org.windy.windyagent.platform.bukkit

import com.fasterxml.jackson.databind.ObjectMapper
import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.bus.ToolReply
import org.windy.windyagent.bus.ToolRequest
import org.windy.windyagent.platform.bukkit.behavior.BehaviorService
import org.windy.windyagent.platform.bukkit.item.ItemService

/**
 * 能力提供方（provider 模式）：把中心 Agent 经总线下发的动作映射为本服操作。
 *
 * 实际操作与主线程跳转统一委托给 [BukkitActions]，与嵌入式 Agent 的本地工具共用同一套逻辑。
 * 收/发都打日志：在子服控制台即可看到「中心让我干什么、结果如何」，便于联调。
 */
class BukkitCapabilityHandler(
    private val plugin: JavaPlugin,
    private val actions: BukkitActions,
    private val items: ItemService?,
    private val behavior: BehaviorService? = null
) {

    private val mapper = ObjectMapper()

    fun handle(req: ToolRequest): ToolReply {
        plugin.logger.info("← 收到中心指令：action=${req.action} args=${req.argsJson.take(200)}")
        val reply = runCatching { dispatch(req) }
            .getOrElse { ToolReply(req.requestId, false, "执行异常：${it.message}") }
        plugin.logger.info("→ 回复中心：${if (reply.success) "OK" else "FAIL"} ${reply.content.take(200)}")
        return reply
    }

    private fun dispatch(req: ToolRequest): ToolReply {
        val args = mapper.readTree(req.argsJson)
        return when (req.action) {
            "run_command" -> {
                val cmd = args["command"]?.asText()?.takeIf { it.isNotBlank() }
                    ?: return fail(req, "缺少 command 参数")
                // provider：命令来自已在中心侧 gate 过的可信总线，直接执行（不再以 UNTRUSTED 重判）
                val (ok, msg) = actions.executeCommand(cmd)
                ToolReply(req.requestId, ok, msg)
            }
            "broadcast" -> {
                val msg = args["message"]?.asText()?.takeIf { it.isNotBlank() }
                    ?: return fail(req, "缺少 message 参数")
                ToolReply(req.requestId, true, actions.broadcast(msg))
            }
            "get_online_players" -> ToolReply(req.requestId, true, actions.onlinePlayers())
            "health_query" -> ToolReply(req.requestId, true, healthSnapshot())
            "server_detail" -> {
                val p = ServerProfile.detect(plugin)
                val rt = Runtime.getRuntime()
                ToolReply(req.requestId, true, actions.serverDetail(currentTps(), p.platform, p.mcVersion, p.modCount,
                    (rt.totalMemory() - rt.freeMemory()) / 1_048_576, rt.maxMemory() / 1_048_576))
            }
            // Forge/NeoForge 专属（按子服核心类型门控）：非 forge/neoforge 由 NeoForgeOps 内部直接拒
            "server_mods" -> ToolReply(req.requestId, true, NeoForgeOps.modList(plugin))
            "dimension_tps" -> ToolReply(req.requestId, true, NeoForgeOps.dimensionTps(plugin, actions))
            "refresh_items", "value_build" -> ToolReply(req.requestId, true, items?.build() ?: "本服未启用物品估值")
            "appraise_item", "value_get" -> {
                val q = args["item"]?.asText()?.takeIf { it.isNotBlank() } ?: return fail(req, "缺少 item 参数")
                ToolReply(req.requestId, true, items?.get(q) ?: "本服未启用物品估值")
            }
            "propose_pack" -> {
                val target = args["target_value"]?.asDouble()?.takeIf { it > 0 } ?: return fail(req, "缺少 target_value 参数")
                ToolReply(req.requestId, true, items?.propose(target) ?: "本服未启用物品估值")
            }
            "value_set" -> {
                val item = args["item"]?.asText()?.takeIf { it.isNotBlank() } ?: return fail(req, "缺少 item 参数")
                val v = args["value"]?.asDouble() ?: return fail(req, "缺少 value 参数")
                ToolReply(req.requestId, true, items?.set(item, v, args["note"]?.asText() ?: "") ?: "本服未启用物品估值")
            }
            "value_unset" -> ToolReply(req.requestId, true, items?.unset(args["item"]?.asText() ?: "") ?: "本服未启用物品估值")
            "value_roots" -> items?.let { ToolReply(req.requestId, true, mapper.writeValueAsString(it.roots(args["all"]?.asBoolean() ?: false))) } ?: fail(req, "本服未启用物品估值")
            "value_seed" -> {
                val seeds = HashMap<String, Double>()
                args["seeds"]?.fields()?.forEach { (k, v) -> if (v.isNumber) seeds[k] = v.asDouble() }
                ToolReply(req.requestId, true, items?.applyLlmSeeds(seeds) ?: "本服未启用物品估值")
            }
            "value_status" -> ToolReply(req.requestId, true, items?.status() ?: "本服未启用物品估值")
            "behavior_stats" -> ToolReply(req.requestId, true, behavior?.statsJson() ?: "本服未启用行为分析")
            "behavior_segments" -> ToolReply(req.requestId, true, behavior?.segmentsJson() ?: "本服未启用行为分析")
            "behavior_words" -> {
                val src = args["source"]?.asText()?.takeIf { it.isNotBlank() } ?: "cmd"
                ToolReply(req.requestId, true, behavior?.wordsJson(src, args["limit"]?.asInt() ?: 80) ?: "本服未启用行为分析")
            }
            "behavior_board" -> ToolReply(req.requestId, true, behavior?.boardJson() ?: "本服未启用行为分析")
            "behavior_chatwords" -> {
                val words = HashMap<String, Int>()
                args["words"]?.fields()?.forEach { (k, v) -> if (v.isNumber) words[k] = v.asInt() }
                behavior?.recordChatWords(words)
                ToolReply(req.requestId, true, "ok")
            }
            "behavior_player" -> {
                val name = args["player"]?.asText()?.takeIf { it.isNotBlank() } ?: return fail(req, "缺少 player 参数")
                ToolReply(req.requestId, true, behavior?.playerJson(name) ?: "本服未启用行为分析")
            }
            "value_orphans" -> ToolReply(req.requestId, true, items?.orphans() ?: "本服未启用物品估值")
            "get_balance" -> {
                val name = args["player"]?.asText()?.takeIf { it.isNotBlank() }
                    ?: return fail(req, "缺少 player 参数")
                val (ok, msg) = actions.balance(name)
                ToolReply(req.requestId, ok, msg)
            }
            "kick_player" -> {
                val name = args["player"]?.asText()?.takeIf { it.isNotBlank() }
                    ?: return fail(req, "缺少 player 参数")
                val reason = args["reason"]?.asText()?.takeIf { it.isNotBlank() } ?: "你已被管理员踢出"
                val (ok, msg) = actions.kick(name, reason)
                ToolReply(req.requestId, ok, msg)
            }
            else -> fail(req, "未知动作：${req.action}")
        }
    }

    /** 本服健康快照（供中心哨兵巡检）：TPS（Paper/Youer 反射取，远古服无则 -1）+ 在线 + JVM 内存。 */
    private fun healthSnapshot(): String {
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
        val maxMb = rt.maxMemory() / 1_048_576
        val online = runCatching { plugin.server.onlinePlayers.size }.getOrDefault(-1)
        val tps = currentTps()
        val p = ServerProfile.detect(plugin)
        return mapper.createObjectNode()
            .put("tps", Math.round(tps * 100) / 100.0)
            .put("online", online)
            .put("memUsedMb", usedMb)
            .put("memMaxMb", maxMb)
            .put("brand", p.brand)
            .put("mcVersion", p.mcVersion)
            .put("platform", p.platform)
            .put("modCount", p.modCount)
            .toString()
    }

    /** 整体 TPS：先试 Bukkit getTPS()（Paper 系有）；Youer 等无此 API → 退 NMS 平均 tick 时长推算。 */
    private fun currentTps(): Double = runCatching {
        val arr = plugin.server.javaClass.getMethod("getTPS").invoke(plugin.server) as DoubleArray
        (arr.firstOrNull() ?: -1.0).coerceAtMost(20.0)
    }.getOrDefault(-1.0).let { if (it >= 0) it else nmsTps() }

    /** Bukkit getTPS() 不可用时，从 NMS MinecraftServer 的平均 tick 时长推算整体 TPS；推不出回 -1。 */
    private fun nmsTps(): Double {
        val nms = runCatching { val cs = plugin.server; cs.javaClass.getMethod("getServer").invoke(cs) }.getOrNull() ?: return -1.0
        val msptNanos = nmsMsptNanos(nms) ?: return -1.0
        val msptMs = msptNanos / 1_000_000.0
        if (msptMs <= 0) return -1.0
        return Math.round((1000.0 / msptMs).coerceAtMost(20.0) * 100) / 100.0
    }

    /** 平均 tick 耗时（纳秒）：方法 getAverageTickTimeNanos()(纳秒) / getAverageTickTime()(毫秒)；回退字段 tickTimesNanos / tickTimes。 */
    private fun nmsMsptNanos(nms: Any): Double? {
        runCatching { (nms.javaClass.getMethod("getAverageTickTimeNanos").invoke(nms) as Number).toDouble() }.getOrNull()?.let { if (it > 0) return it }
        runCatching { (nms.javaClass.getMethod("getAverageTickTime").invoke(nms) as Number).toDouble() }.getOrNull()?.let { if (it > 0) return it * 1_000_000.0 }
        fieldAvg(nms, "tickTimesNanos")?.let { if (it > 0) return it }
        fieldAvg(nms, "tickTimes")?.let { if (it > 0) return it * 1_000_000.0 }
        return null
    }

    /** 沿类层级找 long[] 字段并取非零平均。 */
    private fun fieldAvg(obj: Any, name: String): Double? {
        var c: Class<*>? = obj.javaClass
        while (c != null) {
            c.declaredFields.firstOrNull { it.name == name }?.let { f ->
                return runCatching {
                    f.isAccessible = true
                    val a = f.get(obj) as? LongArray ?: return null
                    a.filter { it > 0 }.let { nz -> if (nz.isEmpty()) null else nz.average() }
                }.getOrNull()
            }
            c = c.superclass
        }
        return null
    }

    private fun fail(req: ToolRequest, msg: String) = ToolReply(req.requestId, false, msg)
}
