package org.windy.windyagent.platform.bukkit

import com.fasterxml.jackson.databind.ObjectMapper
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.PluginCommand
import org.bukkit.plugin.java.JavaPlugin
import java.lang.management.ManagementFactory
import org.windy.windyagent.bus.CapabilityCommand
import org.windy.windyagent.safety.AuditLog
import org.windy.windyagent.safety.CommandGuard
import org.windy.windyagent.safety.PendingApprovals
import org.windy.windyagent.safety.RequestContext
import org.windy.windyagent.safety.TrustLevel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * 本子服实际操作的统一入口，**封装「跳回主线程执行」这一硬约束**。
 *
 * Bukkit API 非线程安全，必须在主线程调用；而调用方既可能是总线订阅线程（能力提供方），
 * 也可能是 Agent 的异步执行线程（嵌入式）。这里统一用调度器跳主线程并阻塞等结果
 * （若已在主线程则直接执行），让 [BukkitCapabilityHandler] 与各 Bukkit*Tool 共用同一套逻辑。
 *
 * 只调用 1.12 ~ 1.13+ 都稳定的 API（dispatchCommand / broadcastMessage /
 * getOnlinePlayers / getPlayerExact / kickPlayer），保证单 jar 跨版本可用。
 */
class BukkitActions(
    private val plugin: JavaPlugin,
    private val guard: CommandGuard,
    private val audit: AuditLog,
    private val pending: PendingApprovals
) {

    private val mapper = ObjectMapper()
    @Volatile private var mainExec: java.util.concurrent.Executor? = null
    @Volatile private var mainExecResolved = false

    /**
     * 主线程执行器：优先 NMS/NeoForge 的 MinecraftServer（它本身实现 Executor，混合端上可靠），
     * 回退 Bukkit 调度器。Youer 等 Mohist 系混合端的 Bukkit 调度器不可靠，故不直接依赖它。
     */
    private fun mainExecutor(): java.util.concurrent.Executor? {
        if (!mainExecResolved) synchronized(this) {
            if (!mainExecResolved) {
                mainExec = runCatching {
                    val cs = Bukkit.getServer()
                    cs.javaClass.getMethod("getServer").invoke(cs) as? java.util.concurrent.Executor
                }.getOrNull()
                mainExecResolved = true
                plugin.logger.info("[Actions] 主线程执行器：" + if (mainExec != null) "MinecraftServer(NMS)" else "Bukkit 调度器(回退)")
            }
        }
        return mainExec
    }

    private fun <T> onMain(block: () -> T): T = onMain(10, block)

    private fun <T> onMain(timeoutSec: Long, block: () -> T): T {
        if (Bukkit.isPrimaryThread()) return block()
        val future = CompletableFuture<T>()
        val task = Runnable { runCatching { future.complete(block()) }.onFailure { future.completeExceptionally(it) } }
        val exec = mainExecutor()
        if (exec != null) exec.execute(task) else Bukkit.getScheduler().runTask(plugin, task)
        return future.get(timeoutSec, TimeUnit.SECONDS)
    }

    /**
     * 供 skill 引擎复用：在主线程跑一段逻辑，带超时看门狗（防脚本久占主线程时 Agent 线程无限期阻塞）。
     * 注意：超时只解除调用方等待，不强杀已在主线程上执行的脚本（强行 interrupt 主服务器线程有损服状态）。
     */
    fun <T> onMainGuarded(timeoutSec: Long, block: () -> T): T = onMain(timeoutSec, block)

    /** 返回 (是否成功, 文本结果)。执行前过安全护栏（子服侧防御纵深 + 信任分权 + 审批闸）。 */
    fun runCommand(command: String): Pair<Boolean, String> {
        when (val d = guard.check(command, RequestContext.current())) {
            is CommandGuard.Decision.Deny -> {
                audit.record("local", "run_command", command, "DENY", d.reason)
                return false to "命令「$command」被安全策略拦截：${d.reason}"
            }
            is CommandGuard.Decision.NeedsApproval -> {
                val id = pending.submit("本服执行：$command") { executeCommand(command).second }
                audit.record("local", "run_command", command, "NEEDS_APPROVAL", "${d.reason} #$id")
                return false to "⏳ 高危操作「$command」需人工审批，已提交审批单 #$id。请管理员执行 /ai-approve $id 批准。"
            }
            is CommandGuard.Decision.Warn -> audit.record("local", "run_command", command, "WARN", d.reason)
            CommandGuard.Decision.Allow -> audit.record("local", "run_command", command, "ALLOW")
        }
        return executeCommand(command)
    }

    /**
     * 不过 guard 的真实执行。两类调用方已自带把关，故此处不再 gate：
     *  - 本类 [runCommand] 放行/审批通过后；
     *  - provider 模式的 [BukkitCapabilityHandler]——命令来自**已在中心侧 gate 过**的可信总线。
     */
    fun executeCommand(command: String): Pair<Boolean, String> = onMain {
        val ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        ok to if (ok) "已在本服执行指令：$command" else "指令执行返回 false：$command"
    }

    /**
     * 跑一条命令并捕获其输出文本（动态代理 CommandSender 抓 sendMessage）。主线程执行。
     * 用于 NeoForge `/neoforge tps` 这类"输出走 sender"的命令。捕不到则返回空列表，调用方兜底。
     */
    fun runCapture(command: String): List<String> = onMain {
        val sink = java.util.Collections.synchronizedList(ArrayList<String>())
        runCatching { Bukkit.dispatchCommand(CommandCapture.sender(Bukkit.getServer(), sink), command) }
            .onFailure { plugin.logger.fine("[Actions] 命令捕获失败（$command）：${it.message}") }
        ArrayList(sink)
    }

    /** 把命令丢主线程执行、不等结果（输出走控制台日志，配合 [LogCapture] 抓取）。 */
    fun dispatchAsync(command: String) {
        val task = Runnable { runCatching { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command) } }
        val exec = mainExecutor()
        if (exec != null) exec.execute(task) else Bukkit.getScheduler().runTask(plugin, task)
    }

    fun broadcast(message: String): String = onMain {
        Bukkit.broadcastMessage(message)
        "已在本服广播：$message"
    }

    fun onlinePlayers(): String = onMain {
        val players = Bukkit.getOnlinePlayers()
        if (players.isEmpty()) "本服在线 0 人"
        else "本服在线 ${players.size} 人：" + players.joinToString(", ") { it.name }
    }

    /**
     * 服务器详情快照（供 WebUI 点开看）：概况 + 各世界(时间/天气/实体/区块/难度) + 在线玩家。
     * 全在主线程读 Bukkit API；tps/平台/内存由调用方(handler)预先算好传入（那些可在总线线程安全读）。
     */
    fun serverDetail(tps: Double, platform: String, mcVersion: String, modCount: Int, memUsedMb: Long, memMaxMb: Long): String = onMain {
        val m = mapper.createObjectNode()
        m.put("uptimeSec", ManagementFactory.getRuntimeMXBean().uptime / 1000)
        m.put("online", Bukkit.getOnlinePlayers().size)
        m.put("maxPlayers", Bukkit.getMaxPlayers())
        m.put("memUsedMb", memUsedMb); m.put("memMaxMb", memMaxMb)
        m.put("tps", tps); m.put("platform", platform); m.put("mcVersion", mcVersion); m.put("modCount", modCount)
        m.put("pluginCount", runCatching { Bukkit.getPluginManager().plugins.size }.getOrDefault(0))
        m.put("whitelist", runCatching { Bukkit.hasWhitelist() }.getOrDefault(false))
        m.put("onlineMode", runCatching { Bukkit.getOnlineMode() }.getOrDefault(true))
        runCatching { (Bukkit.getServer().javaClass.getMethod("getViewDistance").invoke(Bukkit.getServer()) as? Int) }.getOrNull()?.let { m.put("viewDistance", it) }
        val warr = m.putArray("worlds")
        for (w in Bukkit.getWorlds()) {
            val t = w.time
            val o = warr.addObject()
            o.put("name", w.name); o.put("env", w.environment.name)
            o.put("timeHM", tickToHM(t)); o.put("day", t < 12300L || t > 23850L)
            o.put("weather", if (w.isThundering) "thunder" else if (w.hasStorm()) "rain" else "clear")
            o.put("entities", runCatching { w.entities.size }.getOrDefault(-1))
            o.put("chunks", runCatching { w.loadedChunks.size }.getOrDefault(-1))
            o.put("players", w.players.size)
            o.put("difficulty", runCatching { w.difficulty.name }.getOrDefault(""))
        }
        val parr = m.putArray("players")
        for (p in Bukkit.getOnlinePlayers()) {
            val o = parr.addObject()
            o.put("name", p.name); o.put("world", p.world.name)
            o.put("ping", runCatching { p.javaClass.getMethod("getPing").invoke(p) as? Int }.getOrNull() ?: -1)
            o.put("gamemode", runCatching { p.gameMode.name }.getOrDefault(""))
        }
        m.toString()
    }

    /** MC tick(0–24000) → 游戏内时钟 HH:MM（tick 0 = 6:00）。 */
    private fun tickToHM(t: Long): String {
        val h = (t / 1000.0 + 6.0) % 24.0
        val hh = h.toInt(); val mm = ((h - hh) * 60).toInt()
        return "%02d:%02d".format(hh, mm)
    }

    fun kick(name: String, reason: String): Pair<Boolean, String> {
        // 踢人是破坏性动作：不可信来源（玩家聊天等）禁止，挡注入/滥用
        if (RequestContext.current() == TrustLevel.UNTRUSTED) {
            audit.record("UNTRUSTED", "kick", name, "DENY", "不可信来源不可踢人")
            return false to "踢出「$name」被拒：不可信来源（如玩家聊天）不可执行踢人操作。"
        }
        audit.record("trusted", "kick", name, "ALLOW")
        return onMain {
            val player = Bukkit.getPlayerExact(name)
            if (player == null) false to "玩家「$name」不在本服"
            else {
                player.kickPlayer(reason)
                true to "已踢出「$name」，理由：$reason"
            }
        }
    }

    /** 查询玩家 Vault 余额。返回 (是否查到, 文本)。 */
    fun balance(name: String): Pair<Boolean, String> = onMain {
        val econ = VaultHook.economy()
            ?: return@onMain false to "本服未安装 Vault 或未接入经济插件，无法查询余额"
        @Suppress("DEPRECATION") // 1.12 按名取 OfflinePlayer；对在线/近期玩家可用
        val offline = Bukkit.getOfflinePlayer(name)
        val bal = econ.getBalance(offline)
        true to "玩家「$name」余额：${econ.format(bal)}"
    }

    /**
     * 把本服命令表整理成能力目录条目（供启动时建目录、推回中心）。
     *
     * 两处关键清洗：
     *  - **来源归属**：混合端许多插件命令不是 PluginCommand 实例，靠 cast 会全判成"原版/模组"。
     *    改从 knownCommands 的**命名空间 key**（如 `cmi:home` / `xconomy:money`）取真实来源，准得多。
     *  - **按命令名去重**：命名空间/别名包装是不同对象，`distinct()` 去不掉（会出现 /tp /tpa 重复）。
     */
    fun capabilityCommands(): List<CapabilityCommand> {
        val known = runCatching { knownCommands() }.getOrElse { return emptyList() }

        // 命名空间 key → 命令名的来源（优先非 minecraft 的命名空间）
        val sourceByName = HashMap<String, String>()
        for (key in known.keys) {
            val idx = key.indexOf(':')
            if (idx <= 0) continue
            val ns = key.substring(0, idx)
            val cmd = key.substring(idx + 1)
            val existing = sourceByName[cmd]
            if (existing == null || (existing == "minecraft" && ns != "minecraft")) sourceByName[cmd] = ns
        }

        // 按命令名去重
        val byName = LinkedHashMap<String, Command>()
        for (cmd in known.values) byName.putIfAbsent(cmd.name, cmd)

        return byName.values.map { cmd ->
            val ns = sourceByName[cmd.name]
            val source = when {
                ns != null && ns != "minecraft" && ns != "bukkit" -> ns
                (cmd as? PluginCommand)?.plugin?.name != null -> (cmd as PluginCommand).plugin.name
                else -> "原版/模组"
            }
            CapabilityCommand(
                name = cmd.name,
                aliases = runCatching { cmd.aliases }.getOrNull() ?: emptyList(),
                description = cmd.description ?: "",
                usage = runCatching { cmd.usage }.getOrNull()?.takeIf { it.isNotBlank() } ?: "",
                source = source
            )
        }
    }

    /**
     * 只读探查一条命令的用法：目录里只有命令名/来源，很多插件真正的用法解释藏在自己的 i18n / help 主题里，
     * plugin.yml 的 description 反而是空的。这里先取命令对象的静态 description/usage/aliases，
     * 再跑 Bukkit 内置 `/help <name>`（读 HelpMap，输出走 sender）用 [CommandCapture] 旁路抓回详细主题。
     *
     * 纯读：只碰 `/help`，不 dispatch 目标命令本身，故不过 guard、无副作用。抓不到主题就只回静态元信息。
     */
    fun describeCommand(name: String): String {
        val clean = name.trim().trimStart('/').substringBefore(' ')
        if (clean.isEmpty()) return "请给出要查询的命令名。"
        val known = runCatching { knownCommands() }.getOrNull().orEmpty()
        val cmd = known.values.firstOrNull { it.name.equals(clean, true) }
            ?: known.entries.firstOrNull { it.key.substringAfterLast(':').equals(clean, true) }?.value
            ?: known.values.firstOrNull { c -> runCatching { c.aliases }.getOrNull()?.any { it.equals(clean, true) } == true }

        val sb = StringBuilder()
        var pluginName: String? = null
        if (cmd != null) {
            pluginName = (cmd as? PluginCommand)?.plugin?.name
            sb.append("命令 /").append(cmd.name).append("（来源：").append(pluginName ?: "原版/模组").append("）\n")
            cmd.description?.takeIf { it.isNotBlank() }?.let { sb.append("描述：").append(it).append('\n') }
            cmd.usage?.takeIf { it.isNotBlank() }?.let { sb.append("用法：").append(it).append('\n') }
            runCatching { cmd.aliases }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?.let { sb.append("别名：").append(it.joinToString(", ")).append('\n') }
        } else {
            sb.append("命令表里没有精确匹配「").append(clean).append("」的命令，仅尝试内置帮助。\n")
        }

        // ① Bukkit 内置 /help <topic>：读 HelpMap，插件的详细用法常在这里
        val help = runCapture("help $clean").map { it.trim() }.filter { it.isNotEmpty() }
        if (help.isNotEmpty()) {
            sb.append("── /help ").append(clean).append(" ──\n").append(help.take(40).joinToString("\n"))
            if (help.size > 40) sb.append("\n…（帮助较长，已截断前 40 行）")
            sb.append('\n')
        }

        // ② tab 补全：列子命令/参数候选（纯读，绝不执行命令本体——/help 空但补全全的插件很常见）
        var gotCompletions = false
        if (cmd != null) {
            val comps = tabHints(cmd)
            if (comps.isNotEmpty()) { sb.append("子命令/参数候选：").append(comps.joinToString(", ")).append('\n'); gotCompletions = true }
        }

        val out = sb.toString().trim()
        // 三处都没捞到用法（只剩命令名头部）→ 明确判定"查不到"，让 Agent 转而问用户而非瞎猜参数
        val noUsage = help.isEmpty() && !gotCompletions &&
            (cmd == null || (cmd.description.isNullOrBlank() && cmd.usage.isNullOrBlank()))
        return if (noUsage)
            "$out\n\n⚠️ 未查到「$clean」的可靠用法（描述/帮助/补全/语言文件均无）。不要凭空猜测参数执行；请向用户确认该命令的正确用法，得到答复后可用 knowledge_write 存入知识库。"
        else out.ifEmpty { "未能获取「$clean」的用法信息。" }
    }

    /** 命令的 tab 补全候选（顶层子命令/参数）。纯读、不执行命令本体；主线程跑（补全实现常读 Bukkit 状态）。 */
    private fun tabHints(cmd: Command): List<String> = runCatching {
        onMain {
            val fake = CommandCapture.sender(Bukkit.getServer(), java.util.Collections.synchronizedList(ArrayList<String>()))
            (runCatching { cmd.tabComplete(fake, cmd.name, arrayOf("")) }.getOrNull() ?: emptyList())
                .map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(30)
        }
    }.getOrElse { emptyList() }

    /** 跨版本读取已注册命令表：优先公共方法，回退反射字段（沿类层级找）。 */
    private fun knownCommands(): Map<String, Command> {
        val server = Bukkit.getServer()
        val commandMap = runCatching { server.javaClass.getMethod("getCommandMap").invoke(server) }
            .getOrNull() ?: fieldUp(server, "commandMap") ?: return emptyMap()
        val known = runCatching { commandMap.javaClass.getMethod("getKnownCommands").invoke(commandMap) }
            .getOrNull() ?: fieldUp(commandMap, "knownCommands")
        @Suppress("UNCHECKED_CAST")
        return (known as? Map<String, Command>) ?: emptyMap()
    }

    private fun fieldUp(obj: Any, name: String): Any? {
        var c: Class<*>? = obj.javaClass
        while (c != null) {
            c.declaredFields.firstOrNull { it.name == name }?.let { it.isAccessible = true; return it.get(obj) }
            c = c.superclass
        }
        return null
    }
}
