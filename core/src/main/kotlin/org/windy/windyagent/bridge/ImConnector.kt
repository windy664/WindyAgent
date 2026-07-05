package org.windy.windyagent.bridge

import org.slf4j.Logger

/**
 * 一个 IM 平台联动器。每种 IM（昕途QQ / 未来飞书 / 钉钉 / 企业微信…）实现一个。
 *
 * 职责：检测自己的宿主插件是否在<b>同进程</b>运行；在则把「外部超管消息 → 运维 Agent」
 * 接线并返回 true；不在则返回 false（跳过）。
 *
 * <p><b>平台无关</b>：本接口刻意<b>不含</b> Velocity/Bukkit 任何平台类型（无 ProxyServer / Server），
 * 因此放在平台无关的 core 模块，Velocity 与 Bukkit 两端共用同一套联动实现。
 * 「怎么在当前平台定位宿主插件」由各平台入口经 [InstallEnv.lookupPlugin] 喂进来：
 *   - Velocity：`proxy.getPluginManager().getPlugin(id).flatMap{it.instance}.orElse(null)`
 *   - Bukkit：`server.getServicesManager().load(XingtuBotHost::class.java)` 或 `pluginManager.getPlugin(id)`
 *
 * <p><b>会话函数</b>：[InstallEnv.chat] 是平台无关的 `(sessionId, message) -> reply`——即
 * 「把一条消息喂给 Agent 并拿回复」。各平台适配器只把自己的消息模型翻译到该函数，Agent
 * 内部（工具/会话/审批）全复用。
 *
 * <p><b>惰性隔离铁律</b>：实现类<b>不得</b>在字段/父类型/构造器签名里引用宿主插件的类型
 * （如昕途 MessageHandler），只能在<b>方法体内</b>、且确认宿主存在之后才触碰。参见 XingtuBotConnector。
 */
interface ImConnector {

    /** 平台标识（日志用），如 "xingtubot-qq"。 */
    val platformId: String

    /**
     * 尝试装载联动。
     *
     * @return true=宿主在且已接线；false=宿主未安装或未就绪（跳过，不算错误）。
     */
    fun tryInstall(env: InstallEnv): Boolean
}

/**
 * 联动装载环境 —— 平台无关的入参袋。两端主类各自填好平台差异，Connector 只消费。
 *
 * <p>为什么给两个探针：不同宿主暴露自己的方式不同，且同一宿主在两平台也可能不同。
 * 例如昕途：Velocity 靠「插件实例 as HostProvider」，Bukkit 靠「ServicesManager 里的 service」。
 * Connector 两条都试，命中即用；两端主类只需按自己平台实现这两个探针一次，对所有 Connector 通用。
 *
 * @param chat          会话函数 `(sessionId, message) -> reply`（复用主类的 Agent 调用链）。
 * @param logger        日志（slf4j，core 与两端载体均可用）。
 * @param lookupPlugin  按插件 id 取「插件主类实例」，返回 null=未安装：
 *                      Velocity `{ id -> proxy.pluginManager.getPlugin(id).flatMap{it.instance}.orElse(null) }`；
 *                      Bukkit   `{ id -> server.pluginManager.getPlugin(id) }`。
 * @param lookupService 按类型取「服务总线里注册的服务实例」，返回 null=未注册（Bukkit ServicesManager）：
 *                      Bukkit   `{ t -> server.servicesManager.load(t) }`；
 *                      Velocity 无此机制 → 传 `{ _ -> null }`。
 */
class InstallEnv(
    val chat: (String, String) -> String,
    val logger: Logger,
    val lookupPlugin: (String) -> Any?,
    val lookupService: (Class<*>) -> Any?,
)
