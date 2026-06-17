package org.windy.windyagent.platform.bukkit.skill

import groovy.lang.Binding
import groovy.lang.GroovyShell
import groovy.lang.GroovyClassLoader
import groovy.lang.MissingMethodException
import groovy.lang.MissingPropertyException
import groovy.transform.ThreadInterrupt
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.windy.windyagent.platform.bukkit.BukkitActions
import org.windy.windyagent.skill.SkillDef
import org.windy.windyagent.skill.SkillState

/**
 * 用内嵌 Groovy 执行 skill 脚本。binding 注入：
 *  - `server`  → [org.bukkit.Server]（在线玩家、广播…）
 *  - `plugins` → [org.bukkit.plugin.PluginManager]（取其它插件做 API 互调，如 Vault）
 *  - `actions` → [BukkitActions]（复用受控的命令执行/广播等）
 *  - `args`    → Map，调用方传入的参数
 *  - `log`     → 插件 logger
 *
 * **在主线程执行**（经 [BukkitActions.onMainGuarded]）：Bukkit API 非线程安全。
 * 同时白嫖那道超时看门狗——Agent 侧最多等 [timeoutSec] 秒即收到超时回报。
 *
 * 看门狗的边界要说清：它能让 **Agent 线程**不被无限期阻塞，但**无法强杀已在主线程上
 * 空转的脚本**（强行 interrupt 主服务器线程有损服状态，不做）。[ThreadInterrupt] AST
 * 让脚本里的循环具备「被中断即抛出」的协作式取消能力，但真正的兜底是「技能由服主编写、
 * 已审过」这条信任边界——而非靠引擎硬隔离。
 */
class SkillEngine(
    private val plugin: JavaPlugin,
    private val actions: BukkitActions,
    private val timeoutSec: Long
) {
    private val log = plugin.logger

    // 给脚本里的循环/方法入口注入中断检查（协作式取消）。
    private val compilerConfig = CompilerConfiguration().apply {
        addCompilationCustomizers(ASTTransformationCustomizer(ThreadInterrupt::class.java))
    }

    /** 执行一个脚本技能，返回给 LLM 的文本结果（脚本 return 值；无返回值给默认提示）。 */
    fun run(def: SkillDef, argsMap: Map<String, Any?>, state: SkillState? = null): String {
        val script = def.script ?: return def.textOutput()
        def.validate(argsMap)?.let { throw IllegalArgumentException("参数校验失败：$it") }
        return doRun(def, script, argsMap, state)
    }

    private fun doRun(def: SkillDef, script: String, argsMap: Map<String, Any?>, state: SkillState? = null): String = actions.onMainGuarded(timeoutSec) {
        val binding = Binding().apply {
            setVariable("server", Bukkit.getServer())
            setVariable("plugins", Bukkit.getServer().pluginManager)
            setVariable("actions", actions)
            setVariable("args", argsMap)
            setVariable("log", log)
            // 技能状态（跨次执行持久化）
            state?.let { setVariable("state", it) }
        }
        val shell = GroovyShell(javaClass.classLoader, binding, compilerConfig)
        val result = shell.evaluate(script, "skill_${def.name}.groovy")
        result?.toString() ?: "技能「${def.name}」执行完成（无返回值）"
    }

    // ── 编译检查（不执行任何代码）──

    /**
     * 编译检查：只解析语法 + AST 变换，不执行。返回 null=通过，非 null=错误信息。
     * 用于 AI 生成脚本后保存前验证，100% 安全（不碰任何 Bukkit API）。
     */
    fun compile(script: String, name: String = "check"): String? = runCatching {
        val loader = GroovyClassLoader(javaClass.classLoader, compilerConfig)
        loader.parseClass(script, "check_${name}.groovy")
        loader.clearCache()
        null
    }.getOrElse { "编译错误：${it.message}" }

    // ── Dry-run 模拟（用 mock 对象，不碰真实服务器状态）──

    /**
     * Dry-run：用 mock 对象执行脚本，记录所有"会做的操作"但不真实执行。
     * 返回 [DryRunResult]：成功时包含操作记录，失败时包含异常信息。
     * 用于 AI 生成脚本后让服主预览"这个脚本会做什么"，确认后再保存。
     */
    fun dryRun(script: String, argsMap: Map<String, Any?>, name: String = "dry"): DryRunResult {
        val ops = mutableListOf<String>()
        try {
            val mockServer = MockServer(ops)
            val mockActions = MockActions(ops)
            val binding = Binding().apply {
                setVariable("server", mockServer)
                setVariable("plugins", MockPluginManager(ops))
                setVariable("actions", mockActions)
                setVariable("args", argsMap)
                setVariable("log", log)
            }
            val loader = GroovyClassLoader(javaClass.classLoader, compilerConfig)
            val shell = GroovyShell(loader, binding, compilerConfig)
            shell.evaluate(script, "dry_${name}.groovy")
            return DryRunResult(true, ops, null)
        } catch (e: Exception) {
            ops.add("❌ 异常：${e.javaClass.simpleName}: ${e.message}")
            return DryRunResult(false, ops, e.message)
        }
    }
}

/** Dry-run 执行结果。 */
data class DryRunResult(
    val success: Boolean,
    /** 脚本"会做的操作"记录（按执行顺序）。 */
    val operations: List<String>,
    val error: String?
) {
    /** 给 Agent/服主看的摘要。 */
    fun summary(): String {
        val sb = StringBuilder()
        sb.appendLine(if (success) "✅ Dry-run 通过" else "❌ Dry-run 失败")
        if (operations.isNotEmpty()) {
            val realOps = operations.filter { !it.startsWith("⚠️") }
            val warnings = operations.filter { it.startsWith("⚠️") }
            if (realOps.isNotEmpty()) {
                sb.appendLine("\n模拟操作（脚本会做的事）：")
                realOps.forEachIndexed { i, op -> sb.appendLine("  ${i + 1}. $op") }
            }
            if (warnings.isNotEmpty()) {
                sb.appendLine("\n⚠️ 以下操作未经模拟验证，可能存在风险（请人工确认）：")
                warnings.forEach { op -> sb.appendLine("  $op") }
            }
        }
        error?.let { sb.appendLine("\n错误：$it") }
        return sb.toString().trimEnd()
    }
}

// ── Mock 对象：记录"会做什么"，不碰真实服务器 ──

/** Mock Server：拦截常见调用，返回安全默认值。 */
private class MockServer(private val ops: MutableList<String>) {
    fun broadcastMessage(msg: String) { ops.add("📢 全服广播：$msg") }
    fun getPlayerExact(name: String): Player? {
        ops.add("🔍 查找玩家：$name → 返回模拟在线玩家")
        return MockPlayer(name, ops)
    }
    fun getOnlinePlayers(): Collection<Player> {
        ops.add("📋 获取在线玩家列表 → 返回 2 个模拟玩家")
        return listOf(MockPlayer("TestPlayer1", ops), MockPlayer("TestPlayer2", ops))
    }
    fun getServicesManager() = MockServiceManager(ops)
    fun getPluginManager() = MockPluginManager(ops)
    // 兜底：任何未拦截的方法调用
    fun methodMissing(name: String, args: Any?): Any? {
        ops.add("⚠️ server.$name(${fmtArgs(args)}) → 未拦截，返回 null")
        return null
    }
    fun propertyMissing(name: String): Any? {
        if (name == "onlinePlayers") return getOnlinePlayers()
        ops.add("⚠️ server.$name → 未拦截，返回 null")
        return null
    }
}

/** Mock Player：拦截常见调用，未拦截的方法由 Proxy 返回安全默认值。 */
private class MockPlayer(private val name: String, private val ops: MutableList<String>) : Player by mockPlayerDelegate(ops) {
    override fun getName(): String = name
    override fun getDisplayName(): String = name
    override fun isOnline(): Boolean = true
    override fun hasPermission(perm: String): Boolean = true
    override fun sendMessage(msg: String) { ops.add("💬 私聊 $name：$msg") }
    override fun toString(): String = "MockPlayer($name)"
}

/** Mock Inventory：拦截 addItem。 */
private class MockInventory(private val playerName: String, private val ops: MutableList<String>) : Inventory by mockInventoryDelegate() {
    override fun addItem(vararg items: ItemStack): HashMap<Int, ItemStack> {
        items.forEach { item ->
            val mat = item.type.name.lowercase().replace("_", " ")
            ops.add("🎒 给 $playerName 添加：${item.amount}x $mat")
        }
        return HashMap()
    }
}

/** Mock Actions：记录命令/广播但不执行。 */
private class MockActions(private val ops: MutableList<String>) {
    fun dispatchCommand(command: String) { ops.add("⚡ 执行命令：$command") }
    fun broadcast(msg: String) { ops.add("📢 广播：$msg") }
    fun methodMissing(name: String, args: Any?): Any? {
        ops.add("⚠️ actions.$name(${fmtArgs(args)}) → 未拦截")
        return null
    }
}

/** Mock PluginManager：getPlugin 返回 null（模拟"未安装"）。 */
private class MockPluginManager(private val ops: MutableList<String>) {
    fun getPlugin(name: String): Any? {
        ops.add("🔌 查询插件：$name → null（模拟未安装）")
        return null
    }
}

/** Mock ServiceManager：getRegistration 返回 null。 */
private class MockServiceManager(private val ops: MutableList<String>) {
    fun getRegistration(clazz: Class<*>): Any? {
        ops.add("🔌 查询服务：${clazz.simpleName} → null（模拟未注册）")
        return null
    }
}

// 兜底代理（Bukkit Player 接口方法太多，未 override 的返回安全默认值）
private fun mockPlayerDelegate(ops: MutableList<String>): Player {
    return java.lang.reflect.Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java)
    ) { _, method, _ ->
        when (method.name) {
            // getInventory 返回 mock（Groovy 的 player.inventory.addItem 走这条路）
            "getInventory" -> MockInventory("player", ops)
            else -> when (method.returnType) {
                String::class.java -> "MockPlayer"
                Boolean::class.javaPrimitiveType, Boolean::class.java -> false
                Int::class.javaPrimitiveType, Int::class.java -> 0
                Long::class.javaPrimitiveType, Long::class.java -> 0L
                else -> null
            }
        }
    } as Player
}

private fun mockInventoryDelegate(): Inventory {
    return java.lang.reflect.Proxy.newProxyInstance(
        Inventory::class.java.classLoader,
        arrayOf(Inventory::class.java)
    ) { _, method, _ ->
        when (method.returnType) {
            String::class.java -> "MockInventory"
            Boolean::class.javaPrimitiveType, Boolean::class.java -> false
            Int::class.javaPrimitiveType, Int::class.java -> 0
            Array<ItemStack>::class.java -> emptyArray<ItemStack>()
            else -> null
        }
    } as Inventory
}

/** 格式化参数列表（用于 mock 日志）。 */
private fun fmtArgs(args: Any?): String = when (args) {
    is Array<*> -> args.joinToString(", ") { it?.toString()?.take(30) ?: "null" }
    is List<*> -> args.joinToString(", ") { it?.toString()?.take(30) ?: "null" }
    else -> args?.toString()?.take(50) ?: "null"
}
