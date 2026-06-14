package org.windy.windyagent.platform.bukkit

import org.bukkit.Server
import org.bukkit.command.CommandSender
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * 命令输出捕获：用动态代理伪造一个 [CommandSender]，把命令回传给 sender 的 sendMessage 文本收集起来。
 * 用于跑「输出走 sender」的命令（如 NeoForge `/neoforge tps`、`/forge tps`）并拿到结果文本——
 * Bukkit 本身不回传命令输出，只能这样旁路捕获。
 *
 * **能否捕到取决于宿主把不把该命令的输出路由回 Bukkit sender**（混合端各异），故调用方需对空结果兜底。
 * 权限相关方法一律返回 true（让 op 级命令得以执行），其余接口方法返回代理/类型零值。
 */
object CommandCapture {

    fun sender(server: Server, sink: MutableList<String>): CommandSender =
        proxyFor(CommandSender::class.java, server, sink) as CommandSender

    private fun proxyFor(iface: Class<*>, server: Server, sink: MutableList<String>): Any {
        val handler = InvocationHandler { _, method, args ->
            when {
                method.name == "sendMessage" || method.name == "sendRawMessage" -> { args?.forEach { extract(it, sink) }; null }
                method.name == "getServer" -> server
                method.name == "getName" -> "WindyAgent"
                method.name == "isOp" || method.name == "hasPermission" || method.name == "isPermissionSet" -> true
                method.name == "hashCode" -> 0
                method.name == "equals" -> false
                method.name == "toString" -> "WindyAgentCapture"
                // sender.spigot() 等返回另一个接口 → 继续用同一收集器代理，覆盖 spigot().sendMessage(components)
                method.returnType.isInterface -> proxyFor(method.returnType, server, sink)
                else -> zero(method.returnType)
            }
        }
        return Proxy.newProxyInstance(iface.classLoader, arrayOf(iface), handler)
    }

    private val COLOR = Regex("§.")
    private fun extract(arg: Any?, sink: MutableList<String>) {
        when (arg) {
            null -> {}
            is String -> add(arg, sink)
            is Array<*> -> arg.forEach { extract(it, sink) }
            is Iterable<*> -> arg.forEach { extract(it, sink) }
            else -> {
                // 组件对象（BaseComponent / 原版 Component）：尽量取纯文本，取不到退 toString
                val txt = runCatching { arg.javaClass.getMethod("toPlainText").invoke(arg) as? String }.getOrNull()
                    ?: runCatching { arg.javaClass.getMethod("toLegacyText").invoke(arg) as? String }.getOrNull()
                    ?: runCatching { arg.javaClass.getMethod("getString").invoke(arg) as? String }.getOrNull()
                    ?: arg.toString()
                add(txt, sink)
            }
        }
    }

    private fun add(s: String, sink: MutableList<String>) {
        val clean = COLOR.replace(s, "").trim()
        if (clean.isNotEmpty()) sink.add(clean)
    }

    private fun zero(t: Class<*>): Any? = when (t) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Double.TYPE -> 0.0
        java.lang.Float.TYPE -> 0f
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Character.TYPE -> ' '
        else -> null
    }
}
