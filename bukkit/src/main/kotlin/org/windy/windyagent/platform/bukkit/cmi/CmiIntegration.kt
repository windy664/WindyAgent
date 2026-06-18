package org.windy.windyagent.platform.bukkit.cmi

import com.Zrips.CMI.CMI
import com.fasterxml.jackson.databind.ObjectMapper
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.agent.AgentTool
import org.windy.windyagent.llm.ToolResult
import org.windy.windyagent.platform.bukkit.integration.PluginIntegration
import org.windy.windyagent.profile.ProfileDataRegistry
import org.windy.windyagent.safety.AuditLog

/**
 * CMI 插件集成：经济/传送/封禁/礼包/用户查询。
 *
 * 直接调用 CMI Java API（compileOnly 依赖），结构化返回，比命令拼接更可靠。
 */
class CmiIntegration(
    private val plugin: JavaPlugin,
    private val profileRegistry: ProfileDataRegistry? = null
) : PluginIntegration {

    override val pluginName = "CMI"
    override fun isAvailable(): Boolean = Bukkit.getPluginManager().getPlugin("CMI") != null

    override fun createTools(audit: AuditLog): List<AgentTool> {
        val cmi = CMI.getInstance() ?: return emptyList()
        return listOf(
            BalanceTool(cmi, audit),
            PayTool(cmi, audit),
            HomeTool(cmi, audit),
            WarpTool(cmi, audit),
            UserInfoTool(cmi, audit, profileRegistry),
            BanTool(cmi, audit),
            MuteTool(cmi, audit),
            KitTool(cmi, audit)
        )
    }

    companion object {
        private val mapper = ObjectMapper()

        private fun parsePlayer(json: String): String? =
            runCatching { mapper.readTree(json)["player"]?.asText() }.getOrNull()?.takeIf { it.isNotBlank() }

        private fun parseNode(json: String) = runCatching { mapper.readTree(json) }.getOrNull()

        private fun formatTime(ts: Long): String =
            java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString()
    }

    // ── 经济 ──

    private class BalanceTool(private val cmi: CMI, private val audit: AuditLog) : AgentTool {
        override val name = "cmi_balance"
        override val description = "查询玩家的 CMI 经济余额"
        override val inputSchema = """{"type":"object","properties":{"player":{"type":"string","description":"玩家名"}},"required":["player"]}"""
        override fun execute(toolCallId: String, inputJson: String): ToolResult {
            val p = parsePlayer(inputJson) ?: return ToolResult.error(toolCallId, "缺少 player")
            audit.record("local", name, p, "ALLOW")
            return runCatching {
                val econ = cmi.economyManager.vaultManager.vaultEconomy
                    ?: return ToolResult.error(toolCallId, "CMI 经济未启用")
                val balance = econ.getBalance(Bukkit.getOfflinePlayer(p))
                ToolResult.success(toolCallId, "$p 的余额：${econ.format(balance)}")
            }.getOrElse { ToolResult.error(toolCallId, "查询失败：${it.message}") }
        }
    }

    private class PayTool(private val cmi: CMI, private val audit: AuditLog) : AgentTool {
        override val name = "cmi_pay"
        override val description = "给玩家转账（从服务器账户扣除）"
        override val inputSchema = """{"type":"object","properties":{"player":{"type":"string"},"amount":{"type":"number"}},"required":["player","amount"]}"""
        override fun execute(toolCallId: String, inputJson: String): ToolResult {
            val node = parseNode(inputJson) ?: return ToolResult.error(toolCallId, "参数解析失败")
            val p = node["player"]?.asText() ?: return ToolResult.error(toolCallId, "缺少 player")
            val amount = node["amount"]?.asDouble() ?: return ToolResult.error(toolCallId, "缺少 amount")
            audit.record("local", name, p, "ALLOW", "amount=$amount")
            return runCatching {
                val econ = cmi.economyManager.vaultManager.vaultEconomy
                    ?: return ToolResult.error(toolCallId, "CMI 经济未启用")
                val op = Bukkit.getOfflinePlayer(p)
                val r = econ.depositPlayer(op, amount)
                if (r.transactionSuccess()) ToolResult.success(toolCallId, "已转 ${econ.format(amount)} 给 $p，余额：${econ.format(econ.getBalance(op))}")
                else ToolResult.error(toolCallId, "转账失败：${r.errorMessage}")
            }.getOrElse { ToolResult.error(toolCallId, "转账失败：${it.message}") }
        }
    }

    // ── 传送 ──

    private class HomeTool(private val cmi: CMI, private val audit: AuditLog) : AgentTool {
        override val name = "cmi_home"
        override val description = "查询/传送回家"
        override val inputSchema = """{"type":"object","properties":{"player":{"type":"string"},"home":{"type":"string","description":"家名（省略=列出所有）"}},"required":["player"]}"""
        override fun execute(toolCallId: String, inputJson: String): ToolResult {
            val node = parseNode(inputJson) ?: return ToolResult.error(toolCallId, "参数解析失败")
            val pn = node["player"]?.asText() ?: return ToolResult.error(toolCallId, "缺少 player")
            val home = node["home"]?.asText()
            audit.record("local", name, pn, "ALLOW")
            return runCatching {
                val player = Bukkit.getPlayerExact(pn) ?: return ToolResult.error(toolCallId, "$pn 不在线")
                val max = cmi.homeManager.getMaxHomes(player)
                if (home == null) {
                    val user = cmi.getUser(null, pn, pn) ?: return ToolResult.error(toolCallId, "找不到用户")
                    val homes = user.homes ?: emptyMap<String, Any>()
                    if (homes.isEmpty()) ToolResult.success(toolCallId, "$pn 没有家（上限 $max）")
                    else ToolResult.success(toolCallId, "$pn 的家（${homes.size}/$max）：${homes.keys.joinToString("、")}")
                } else {
                    Bukkit.dispatchCommand(player, "cmi home $home")
                    ToolResult.success(toolCallId, "已传送 $pn 到家「$home」")
                }
            }.getOrElse { ToolResult.error(toolCallId, "操作失败：${it.message}") }
        }
    }

    private class WarpTool(private val cmi: CMI, private val audit: AuditLog) : AgentTool {
        override val name = "cmi_warp"
        override val description = "列出/传送到传送点"
        override val inputSchema = """{"type":"object","properties":{"player":{"type":"string"},"warp":{"type":"string","description":"传送点名（省略=列出所有）"}},"required":[]}"""
        override fun execute(toolCallId: String, inputJson: String): ToolResult {
            val node = parseNode(inputJson)
            val pn = node?.get("player")?.asText()
            val wn = node?.get("warp")?.asText()
            audit.record("local", name, pn ?: "list", "ALLOW")
            return runCatching {
                val warps = cmi.warpManager.warps
                if (wn == null) {
                    if (warps.isEmpty()) ToolResult.success(toolCallId, "没有传送点")
                    else ToolResult.success(toolCallId, "共 ${warps.size} 个传送点：${warps.keys.joinToString("、")}")
                } else {
                    val player = pn?.let { Bukkit.getPlayerExact(it) } ?: return ToolResult.error(toolCallId, "需要在线玩家")
                    Bukkit.dispatchCommand(player, "cmi warp $wn")
                    ToolResult.success(toolCallId, "已传送 ${player.name} 到「$wn」")
                }
            }.getOrElse { ToolResult.error(toolCallId, "操作失败：${it.message}") }
        }
    }

    // ── 用户 ──

    private class UserInfoTool(
        private val cmi: CMI,
        private val audit: AuditLog,
        private val profileRegistry: ProfileDataRegistry? = null
    ) : AgentTool {
        override val name = "cmi_userinfo"
        override val description = "查询玩家详细信息（最后登录/IP/位置/余额）"
        override val inputSchema = """{"type":"object","properties":{"player":{"type":"string"}},"required":["player"]}"""
        override fun execute(toolCallId: String, inputJson: String): ToolResult {
            val pn = parsePlayer(inputJson) ?: return ToolResult.error(toolCallId, "缺少 player")
            audit.record("local", name, pn, "ALLOW")
            return runCatching {
                // 优先读画像缓存（零 CMI 开销），缓存未命中再查 CMI
                val cached = profileRegistry?.snapshot(pn)
                if (cached != null && cached.containsKey("cmi.balance")) {
                    val sb = StringBuilder("📊 $pn 信息（缓存）：\n")
                    cached["cmi.balance"]?.let { sb.appendLine("• 余额：$it") }
                    cached["cmi.homes"]?.let { sb.appendLine("• 家数量：$it") }
                    cached["cmi.lastLogin"]?.let { sb.appendLine("• 最后登录：${formatTime(it.toLongOrNull() ?: 0)}") }
                    cached["cmi.lastLogoff"]?.let { sb.appendLine("• 最后退出：${formatTime(it.toLongOrNull() ?: 0)}") }
                    cached["cmi.lastIp"]?.let { sb.appendLine("• IP：$it") }
                    return@runCatching ToolResult.success(toolCallId, sb.toString().trimEnd())
                }
                // 缓存未命中，回退到实时查询
                val user = cmi.getUser(null, pn, pn) ?: return ToolResult.error(toolCallId, "找不到用户")
                val sb = StringBuilder("📊 $pn 信息：\n")
                sb.appendLine("• 在线：${if (user.isOnline) "是" else "否"}")
                if (user.lastLogin > 0) sb.appendLine("• 最后登录：${formatTime(user.lastLogin)}")
                if (user.lastLogoff > 0) sb.appendLine("• 最后退出：${formatTime(user.lastLogoff)}")
                val ip = user.lastIp
                if (!ip.isNullOrBlank()) sb.appendLine("• IP：$ip")
                cmi.economyManager.vaultManager.vaultEconomy?.let { econ ->
                    sb.appendLine("• 余额：${econ.format(econ.getBalance(Bukkit.getOfflinePlayer(pn)))}")
                }
                val loc = user.location
                if (loc != null) sb.appendLine("• 位置：${loc.world?.name} ${loc.blockX} ${loc.blockY} ${loc.blockZ}")
                ToolResult.success(toolCallId, sb.toString().trimEnd())
            }.getOrElse { ToolResult.error(toolCallId, "查询失败：${it.message}") }
        }
    }

    // ── 封禁/禁言 ──

    private class BanTool(private val cmi: CMI, private val audit: AuditLog) : AgentTool {
        override val name = "cmi_ban"
        override val description = "封禁玩家（支持临时）"
        override val inputSchema = """{"type":"object","properties":{"player":{"type":"string"},"reason":{"type":"string"},"duration":{"type":"string","description":"7d/24h/30m，留空=永久"}},"required":["player"]}"""
        override fun execute(toolCallId: String, inputJson: String): ToolResult {
            val node = parseNode(inputJson) ?: return ToolResult.error(toolCallId, "参数解析失败")
            val p = node["player"]?.asText() ?: return ToolResult.error(toolCallId, "缺少 player")
            val reason = node["reason"]?.asText() ?: "违规"
            val dur = node["duration"]?.asText()
            audit.record("local", name, p, "ALLOW", "reason=$reason")
            return runCatching {
                val cmd = if (dur != null) "cmi tempban $p $dur $reason" else "cmi ban $p $reason"
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
                ToolResult.success(toolCallId, "已封禁 $p（${dur ?: "永久"}），原因：$reason")
            }.getOrElse { ToolResult.error(toolCallId, "封禁失败：${it.message}") }
        }
    }

    private class MuteTool(private val cmi: CMI, private val audit: AuditLog) : AgentTool {
        override val name = "cmi_mute"
        override val description = "禁言玩家（支持临时）"
        override val inputSchema = """{"type":"object","properties":{"player":{"type":"string"},"reason":{"type":"string"},"duration":{"type":"string","description":"1h/30m，留空=永久"}},"required":["player"]}"""
        override fun execute(toolCallId: String, inputJson: String): ToolResult {
            val node = parseNode(inputJson) ?: return ToolResult.error(toolCallId, "参数解析失败")
            val p = node["player"]?.asText() ?: return ToolResult.error(toolCallId, "缺少 player")
            val reason = node["reason"]?.asText() ?: "违规"
            val dur = node["duration"]?.asText()
            audit.record("local", name, p, "ALLOW", "reason=$reason")
            return runCatching {
                val cmd = if (dur != null) "cmi tempmute $p $dur $reason" else "cmi mute $p $reason"
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
                ToolResult.success(toolCallId, "已禁言 $p（${dur ?: "永久"}），原因：$reason")
            }.getOrElse { ToolResult.error(toolCallId, "禁言失败：${it.message}") }
        }
    }

    // ── 礼包 ──

    private class KitTool(private val cmi: CMI, private val audit: AuditLog) : AgentTool {
        override val name = "cmi_kit"
        override val description = "列出/发放礼包"
        override val inputSchema = """{"type":"object","properties":{"player":{"type":"string"},"kit":{"type":"string","description":"礼包名（省略=列出所有）"}},"required":[]}"""
        override fun execute(toolCallId: String, inputJson: String): ToolResult {
            val node = parseNode(inputJson)
            val pn = node?.get("player")?.asText()
            val kn = node?.get("kit")?.asText()
            audit.record("local", name, pn ?: "list", "ALLOW")
            return runCatching {
                val kits = cmi.kitsManager.kitMap
                if (kn == null) {
                    if (kits.isEmpty()) ToolResult.success(toolCallId, "没有礼包")
                    else ToolResult.success(toolCallId, "共 ${kits.size} 个礼包：${kits.keys.joinToString("、")}")
                } else {
                    val player = pn?.let { Bukkit.getPlayerExact(it) } ?: return ToolResult.error(toolCallId, "需要在线玩家")
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "cmi kit $kn $pn")
                    ToolResult.success(toolCallId, "已给 ${player.name} 发放礼包「$kn」")
                }
            }.getOrElse { ToolResult.error(toolCallId, "操作失败：${it.message}") }
        }
    }
}
