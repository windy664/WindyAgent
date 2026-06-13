package org.windy.windyagent.platform.bukkit

import com.fasterxml.jackson.databind.ObjectMapper
import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.bus.ToolReply
import org.windy.windyagent.bus.ToolRequest
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
    private val items: ItemService?
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

    private fun fail(req: ToolRequest, msg: String) = ToolReply(req.requestId, false, msg)
}
