package org.windy.windyagent.platform.bukkit

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * 日志输出捕获：在一小段窗口内，给 log4j 根 logger 临时挂一个代理 Appender，收集这期间打到日志的消息。
 * 用于抓「输出走服务器日志而非命令 sender」的命令（NeoForge `/neoforge tps` 实测就是这种）。
 *
 * 全程反射 + 动态代理，不编译期依赖 log4j-core（宿主运行时提供）；任何一步失败则返回空，调用方兜底。
 * 窗口内会捕获到**所有**日志，调用方按内容过滤（如只留含 "TPS" 的行）。
 */
object LogCapture {

    /** 挂临时 appender → 跑 [trigger]（通常异步派发命令到主线程）→ 等 [windowMs] → 摘 appender，返回这期间的日志行。 */
    fun capture(windowMs: Long, trigger: () -> Unit): List<String> {
        val sink = java.util.Collections.synchronizedList(ArrayList<String>())
        val detach = runCatching { attach(sink) }.getOrNull()
        try {
            trigger()
            Thread.sleep(windowMs)
        } catch (_: InterruptedException) {
        } finally {
            runCatching { detach?.invoke() }
        }
        return ArrayList(sink)
    }

    /** 给根 LoggerConfig 加代理 Appender，返回"摘除"闭包。 */
    private fun attach(sink: MutableList<String>): (() -> Unit) {
        val lm = Class.forName("org.apache.logging.log4j.LogManager")
        val ctx = lm.getMethod("getContext", java.lang.Boolean.TYPE).invoke(null, false)        // core LoggerContext
        val config = ctx.javaClass.getMethod("getConfiguration").invoke(ctx)
        val rootLogger = config.javaClass.getMethod("getRootLogger").invoke(config)             // LoggerConfig
        val appenderIface = Class.forName("org.apache.logging.log4j.core.Appender")
        val filterClass = Class.forName("org.apache.logging.log4j.core.Filter")
        val levelClass = Class.forName("org.apache.logging.log4j.Level")
        val stateClass = Class.forName("org.apache.logging.log4j.core.LifeCycle\$State")
        val started = stateClass.enumConstants.firstOrNull { it.toString() == "STARTED" }
        val name = "windyagent-capture"

        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "append" -> {
                    runCatching {
                        val ev = args!![0]
                        val msg = ev.javaClass.getMethod("getMessage").invoke(ev)
                        (msg.javaClass.getMethod("getFormattedMessage").invoke(msg) as? String)?.let { sink.add(it) }
                    }; null
                }
                "getName" -> name
                "isStarted" -> true
                "isStopped" -> false
                "getState" -> started
                "ignoreExceptions" -> true
                "getLayout", "getHandler" -> null
                "toString" -> name
                "hashCode" -> name.hashCode()
                "equals" -> false
                else -> zero(method.returnType)
            }
        }
        val appender = Proxy.newProxyInstance(appenderIface.classLoader, arrayOf(appenderIface), handler)
        rootLogger.javaClass.getMethod("addAppender", appenderIface, levelClass, filterClass).invoke(rootLogger, appender, null, null)
        return { runCatching { rootLogger.javaClass.getMethod("removeAppender", String::class.java).invoke(rootLogger, name) } }
    }

    private fun zero(t: Class<*>): Any? = when (t) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        else -> null
    }
}
