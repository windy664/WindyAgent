package org.windy.windyagent.bridge

import org.windy.xingtubot.common.module.XingtuBotHost
import org.windy.xingtubot.common.module.XingtuBotHostProvider

/**
 * 昕途联动的<b>接线实现</b> —— 所有对昕途类型的引用都收敛在本类。
 *
 * 只被 [XingtuBotConnector] 在「确认昕途已安装」后调用，故本类被加载时昕途类必然可用，
 * 不会 NoClassDefFoundError。把这层单独成类，是为了让 [XingtuBotConnector] 的探测逻辑
 * 保持零昕途依赖（惰性隔离的支点）。
 */
internal object XingtuBotWiring {

    /** 定位昕途 host 并注册观察者。@return 成功接线返回 true。 */
    fun wire(env: InstallEnv): Boolean {
        // 两平台暴露 host 的机制不同，都试：
        //  · Velocity：主类实例(id "xingtubotvelocity") 实现 XingtuBotHostProvider → .host
        //  · Bukkit：昕途把 XingtuBotHost 注册进 ServicesManager → lookupService 直接拿到 host
        val viaProvider = (env.lookupPlugin("xingtubotvelocity") as? XingtuBotHostProvider)?.host
        val viaService = if (viaProvider == null) env.lookupService(XingtuBotHost::class.java) as? XingtuBotHost else null
        val host: XingtuBotHost = viaProvider ?: viaService
            ?: run {
                env.logger.warn("[XingtuBot] 检测到插件但取不到宿主接口（版本不匹配？），联动未启用")
                return false
            }

        // ── 启动诊断（QQ 没反应时对照这几行定位）──
        // 注：新版昕途 host.permission() 返回的 PermissionChecker 仅有 isAdmin(openid)，
        // 不再暴露 hasAnyAdmin()（该能力随 auth 拆分归入独立的 xt-auth 插件）。故这里不再预判
        // 「是否配了超管」，只提示排查路径——真正的鉴权仍由 XingtuBotAgentHandler 逐条 isAdmin 判定。
        env.logger.info("[XingtuBot] 诊断：宿主来源={} · isBrain(大脑)={}",
            if (viaProvider != null) "Velocity/HostProvider" else "Bukkit/ServicesManager",
            host.isBrain())
        env.logger.info("[XingtuBot] 提示：若 QQ 没反应，先确认昕途 config.yml 的 admin-openids 填了你的 openid" +
            "（首次可先给机器人发消息，昕途日志会打印你的 openid）——非超管的消息会被忽略。")

        // QQ 消息 observer 只在「大脑」节点有意义：昕途手脚模式(slave)不连 QQ、不 dispatch 消息，
        // 在手脚上注册 observer 是永不触发的死接。故只在 isBrain 时注册，手脚上显式跳过并说明，避免静默空接。
        if (host.isBrain()) {
            host.registry().registerObserver(XingtuBotAgentHandler(
                env.chat, host.permission(), env.logger,
                // 惰性问主链"这条会被某具体命令认领吗"：超管发昕途命令(登录/天气/mcmod…)时 Agent 不抢答，交主链处理。
                // 复用昕途真 matches() 判定，不猜前缀 → 白名单「登录」这类只 matches 不声明 triggers 的也覆盖。
                handledByCommand = { text, event -> host.registry().isHandledByCommand(text, event) }))
            env.logger.info("[XingtuBot] ✅ QQ 联动 observer 已注册：超管私聊 / @机器人 → 运维 Agent")
        } else {
            env.logger.warn("[XingtuBot] ⚠ 当前为昕途手脚(slave)节点，不连 QQ、不注册 observer；" +
                "QQ 联动必须在【大脑】节点(跑 bot 的那个：Velocity 代理 或 独立 Bukkit)启用。这正是你 QQ 没反应的可能原因。")
        }

        return true
    }
}
