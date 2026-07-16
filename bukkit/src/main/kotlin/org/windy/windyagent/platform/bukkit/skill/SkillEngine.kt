package org.windy.windyagent.platform.bukkit.skill

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.platform.bukkit.BukkitActions
import org.windy.windyagent.skill.SkillDef
import org.windy.windyagent.skill.SkillState
import taboolib.library.kether.QuestAction
import taboolib.library.kether.QuestActionParser
import taboolib.library.kether.QuestContext
import taboolib.library.kether.QuestReader
import taboolib.library.kether.SimpleQuestLoader
import taboolib.module.kether.KetherShell
import taboolib.module.kether.ScriptContext
import taboolib.module.kether.ScriptOptions
import taboolib.module.kether.ScriptService
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 用 TabooLib Kether 执行 skill 脚本。
 *
 * Kether 是 Bukkit 侧的受控动作 DSL；WindyAgent 只暴露 `wa_*` 动作，避免脚本直接拿到 Bukkit API。
 * 当前内置动作：
 *  - `wa_command "say hello"`：走 [BukkitActions.runCommand]，保留安全护栏/审批/审计。
 *  - `wa_broadcast "message"`：本服广播。
 *  - `wa_tell "player" "message"`：给在线玩家发消息。
 *  - `wa_log "message"`：写插件日志。
 */
class SkillEngine(
    private val plugin: JavaPlugin,
    private val actions: BukkitActions,
    private val timeoutSec: Long
) {
    private val log = plugin.logger
    private val namespaces = listOf("windyagent")

    init {
        registerActions(plugin, actions)
    }

    fun run(def: SkillDef, argsMap: Map<String, Any?>, state: SkillState? = null): String {
        val script = def.script ?: return def.textOutput()
        if (!def.scriptLanguage.equals("kether", true)) {
            throw IllegalArgumentException("脚本语言「${def.scriptLanguage}」已不支持，请迁移为 kether")
        }
        def.validate(argsMap)?.let { throw IllegalArgumentException("参数校验失败：$it") }
        return doRun(def.name, script, argsMap, state, dryRun = false).result
    }

    fun compile(script: String, name: String = "check"): String? = runCatching {
        SimpleQuestLoader().load<ScriptContext>(
            ScriptService,
            "windyagent_$name",
            script.toByteArray(StandardCharsets.UTF_8),
            namespaces
        )
        null
    }.getOrElse { "Kether 解析错误：${it.message}" }

    fun dryRun(script: String, argsMap: Map<String, Any?>, name: String = "dry"): DryRunResult {
        return runCatching {
            compile(script, name)?.let { return DryRunResult(false, listOf("❌ $it"), it) }
            val result = doRun(name, script, argsMap, null, dryRun = true)
            DryRunResult(true, result.operations, null)
        }.getOrElse {
            DryRunResult(false, listOf("❌ 异常：${it.javaClass.simpleName}: ${it.message}"), it.message)
        }
    }

    private data class RunResult(val result: String, val operations: List<String>)

    private fun doRun(
        name: String,
        script: String,
        argsMap: Map<String, Any?>,
        state: SkillState?,
        dryRun: Boolean
    ): RunResult {
        val operations = java.util.Collections.synchronizedList(mutableListOf<String>())
        val options = ScriptOptions().apply {
            namespace = namespaces
            sandbox = true
            detailError = true
            this.context = { ctx: ScriptContext ->
                ctx.id = "windyagent_$name"
                ctx.set("wa_args", argsMap)
                ctx.set("wa_dry_run", dryRun)
                ctx.set("wa_operations", operations)
                argsMap.forEach { (key, value) -> ctx.set(key, value) }
                state?.let { ctx.set("state", it) }
                kotlin.Unit
            }
        }
        val value = KetherShell.eval(script, options).get(timeoutSec, TimeUnit.SECONDS)
        val message = value?.toString() ?: "技能「$name」执行完成（无返回值）"
        return RunResult(message, ArrayList(operations))
    }

    private companion object {
        private val registered = AtomicBoolean(false)

        fun registerActions(plugin: JavaPlugin, actions: BukkitActions) {
            if (!registered.compareAndSet(false, true)) return
            val registry = ScriptService.registry
            register(registry, "wa_command", commandParser(plugin, actions))
            register(registry, "wa_broadcast", broadcastParser(plugin, actions))
            register(registry, "wa_tell", tellParser(plugin, actions))
            register(registry, "wa_log", logParser(plugin))
        }

        private fun register(registry: taboolib.library.kether.QuestRegistry, name: String, parser: QuestActionParser) {
            runCatching { registry.registerAction(name, "windyagent", parser) }
        }

        private fun commandParser(plugin: JavaPlugin, actions: BukkitActions): QuestActionParser = QuestActionParser.of { reader ->
            val command = readRemaining(reader)
            action<String> { frame ->
                val resolved = interpolate(command, frame)
                if (isDryRun(frame)) {
                    record(frame, "⚡ 执行命令：$resolved")
                    "dry-run: command $resolved"
                } else {
                    actions.runCommand(resolved).second
                }
            }
        }

        private fun broadcastParser(plugin: JavaPlugin, actions: BukkitActions): QuestActionParser = QuestActionParser.of { reader ->
            val message = readRemaining(reader)
            action<String> { frame ->
                val resolved = interpolate(message, frame)
                if (isDryRun(frame)) {
                    record(frame, "📢 广播：$resolved")
                    "dry-run: broadcast"
                } else {
                    actions.broadcast(resolved)
                }
            }
        }

        private fun tellParser(plugin: JavaPlugin, actions: BukkitActions): QuestActionParser = QuestActionParser.of { reader ->
            val player = readToken(reader)
            val message = readRemaining(reader)
            action<String> { frame ->
                val targetName = interpolate(player, frame)
                val resolved = interpolate(message, frame)
                if (isDryRun(frame)) {
                    record(frame, "💬 私聊 $targetName：$resolved")
                    "dry-run: tell $targetName"
                } else {
                    actions.onMainGuarded(5) {
                        val target = Bukkit.getPlayerExact(targetName)
                            ?: return@onMainGuarded "玩家「$targetName」不在线"
                        target.sendMessage(resolved)
                        "已向 $targetName 发送消息：$resolved"
                    }
                }
            }
        }

        private fun logParser(plugin: JavaPlugin): QuestActionParser = QuestActionParser.of { reader ->
            val message = readRemaining(reader)
            action<String> { frame ->
                val resolved = interpolate(message, frame)
                if (isDryRun(frame)) record(frame, "📝 记录日志：$resolved") else plugin.logger.info("[KetherSkill] $resolved")
                resolved
            }
        }

        private fun <T> action(block: (QuestContext.Frame) -> T): QuestAction<T> = object : QuestAction<T>() {
            override fun process(frame: QuestContext.Frame): CompletableFuture<T> {
                return CompletableFuture.completedFuture(block(frame))
            }
        }

        private fun isDryRun(frame: QuestContext.Frame): Boolean {
            val ctx = frame.context() as? ScriptContext ?: return false
            return ctx.get("wa_dry_run", false) == true
        }

        @Suppress("UNCHECKED_CAST")
        private fun record(frame: QuestContext.Frame, text: String) {
            val ctx = frame.context() as? ScriptContext ?: return
            val ops = ctx.get("wa_operations", null) as? MutableList<String> ?: return
            ops.add(text)
        }


        private fun interpolate(text: String, frame: QuestContext.Frame): String {
            val ctx = frame.context() as? ScriptContext ?: return text
            return Regex("""\{([A-Za-z0-9_.-]+)}""").replace(text) { match ->
                val key = match.groupValues[1]
                resolveVar(ctx, key)?.toString() ?: match.value
            }
        }

        private fun resolveVar(ctx: ScriptContext, key: String): Any? {
            val parts = key.split(".")
            var current: Any? = ctx.get(parts.first(), null)
            for (part in parts.drop(1)) {
                current = when (current) {
                    is Map<*, *> -> current[part]
                    else -> return null
                }
            }
            return current
        }
        private fun readRemaining(reader: QuestReader): String {
            val parts = mutableListOf<String>()
            while (reader.hasNext()) parts += readToken(reader)
            return parts.joinToString(" ").trim()
        }

        private fun readToken(reader: QuestReader): String {
            return reader.nextToken().trim().trim('"', '\'')
        }
    }
}

/** Dry-run 执行结果。 */
data class DryRunResult(
    val success: Boolean,
    val operations: List<String>,
    val error: String?
) {
    fun summary(): String {
        val sb = StringBuilder()
        sb.appendLine(if (success) "✅ Kether Dry-run 通过" else "❌ Kether Dry-run 失败")
        if (operations.isNotEmpty()) {
            sb.appendLine("\n模拟操作（脚本会做的事）：")
            operations.forEachIndexed { i, op -> sb.appendLine("  ${i + 1}. $op") }
        }
        error?.let { sb.appendLine("\n错误：$it") }
        return sb.toString().trimEnd()
    }
}


