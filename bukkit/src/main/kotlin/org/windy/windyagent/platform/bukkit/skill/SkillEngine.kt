package org.windy.windyagent.platform.bukkit.skill

import groovy.lang.Binding
import groovy.lang.GroovyShell
import groovy.transform.ThreadInterrupt
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.windy.windyagent.platform.bukkit.BukkitActions
import org.windy.windyagent.skill.SkillDef

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
    fun run(def: SkillDef, argsMap: Map<String, Any?>): String {
        // 纯文字技能不该走到这（应在调用侧直接返回正文）；防御性处理。
        val script = def.script ?: return def.textOutput()
        // 第②步参数预校验：缺参/类型不符在跳主线程前就拦下，回报 LLM 补参，不进 GroovyShell。
        def.validate(argsMap)?.let { throw IllegalArgumentException("参数校验失败：$it") }
        return doRun(def, script, argsMap)
    }

    private fun doRun(def: SkillDef, script: String, argsMap: Map<String, Any?>): String = actions.onMainGuarded(timeoutSec) {
        val binding = Binding().apply {
            setVariable("server", Bukkit.getServer())
            setVariable("plugins", Bukkit.getServer().pluginManager)
            setVariable("actions", actions)
            setVariable("args", argsMap)
            setVariable("log", log)
        }
        // 用插件类加载器：脚本可见 Bukkit API + 本插件 + 经依赖加载链可达的其它插件类。
        val shell = GroovyShell(javaClass.classLoader, binding, compilerConfig)
        val result = shell.evaluate(script, "skill_${def.name}.groovy")
        result?.toString() ?: "技能「${def.name}」执行完成（无返回值）"
    }
}
