package org.windy.windyagent.bridge

import org.slf4j.Logger
import org.windy.xingtubot.common.event.BotMessageContext
import org.windy.xingtubot.common.handler.BotMessageHandler
import org.windy.xingtubot.common.handler.PermissionChecker

/**
 * 昕途消息 → 运维 Agent 的观察者（昕途 [BotMessageHandler] 适配器）。
 *
 * 作为观察者注册（HandlerRegistry.registerObserver）：与昕途自身命令/群服互联并行，不互相阻断。
 * 只由 [XingtuBotWiring] 在确认昕途已安装后创建，故其对昕途类型的引用运行时安全。
 *
 * 触发条件（严格，命中才响应，其余一律不理）：
 *  - 发送者是昕途<b>超管</b>（复用昕途 [PermissionChecker]，即 config 的 admin-openids）；
 *  - 且「私聊」或「群内 @机器人」——群内非 @ 不响应，避免刷屏；
 *  - 且这条消息<b>不会被昕途某个具体命令认领</b>：交给昕途主链的 `isHandledByCommand` 判定（它直接复用
 *    各 handler 真正的 matches()，是唯一可靠信号——登录/绑定/天气/id 等全覆盖，且自动排除群服互联等 catch-all
 *    兜底器）。否则超管发昕途命令时会被 Agent 抢答（两边并行都回）。
 *
 * 命令识别用 [handledByCommand]（惰性调昕途 `HandlerRegistry.isHandledByCommand`）。之所以不再用
 * getManagedPrefixes 的前缀表：那只看 usage/triggers 声明，会漏掉靠 matches() 精确匹配却没声明前缀的 handler
 * （如白名单「登录」——正是本类曾抢答它的根因）。惰性 = 兼容我们 observer 注册后昕途才注册的命令。
 *
 * 每个超管一个独立会话（`im-<openid>`）——与 web 控制台共用同一 session id，实现 web↔QQ 无缝衔接：
 * 首次消息时把该会话登记进 [ImThreadRegistry]，web 前端即把它置顶为一个固定对话，点进去可看到
 * QQ 聊过的内容并接着聊（Agent 上下文由 SessionManager、聊天记录由 ChatArchive 共享）。
 * 会话函数 [chat] 平台无关，故本 handler 只做「昕途消息模型 → chat」的翻译。
 */
class XingtuBotAgentHandler(
    private val chat: (String, String) -> String,
    private val permission: PermissionChecker,
    private val logger: Logger,
    /** 惰性问昕途主链「这条会被某具体命令认领吗」；返回 false/抛错都当「无命令」放行给 Agent。 */
    private val handledByCommand: (String, BotMessageContext) -> Boolean = { _, _ -> false },
) : BotMessageHandler {

    override fun matches(message: String?, event: BotMessageContext): Boolean {
        if (message == null || message.trim().isEmpty()) return false
        // 跳过群成员增减等非聊天事件
        val et = event.eventType
        if (et != null && et.contains("MEMBER")) return false

        // ── 诊断日志：只对「定向消息」（私聊 or 群@）打点，避免群闲聊刷屏。
        //    QQ 没反应时对照：能看到这行 = 消息已到达本联动；看不到 = 消息没进昕途分发(见下方排查清单)。
        val directed = !event.isGroupMessage || event.isGroupAtMessage
        if (directed) {
            val admin = runCatching { permission.isAdmin(event.senderId) }.getOrDefault(false)
            val isCmd = admin && isOtherCommand(message.trim(), event)
            logger.info("[XingtuBot] 收到定向消息：openid={} 超管={} 私聊={} 群@={} et={} → {}",
                event.senderId, admin, !event.isGroupMessage, event.isGroupAtMessage, et,
                when {
                    !admin -> "忽略(此 openid 不在昕途 admin-openids)"
                    isCmd -> "忽略(昕途某命令会认领，交主链处理)"
                    else -> "受理"
                })
        }

        // 只认超管
        if (!permission.isAdmin(event.senderId)) return false
        // 只认「私聊」或「群内 @机器人」；群内非 @ 一律不响应
        if (event.isGroupMessage && !event.isGroupAtMessage) return false
        // 不抢别的命令：斜杠命令 + 昕途主链任何会认领它的具体命令，交给它们处理，Agent 不响应
        if (isOtherCommand(message.trim(), event)) return false
        return true
    }

    /**
     * 这条消息是否是「别的命令」，不该转给 Agent：
     *  1. 斜杠/叹号打头（`/mcp`、`!xxx` 等平台或插件命令，从来不是给 Agent 的自然语言）；
     *  2. 昕途主链某个具体命令 handler 会 matches 认领它（登录/绑定/天气/id…）——由昕途 isHandledByCommand
     *     用真 matches() 判定，不靠猜前缀，故白名单「登录」这类只 matches 不声明 triggers 的也覆盖。
     * 判定失败（NoSuchMethod/版本不符等）只降级为「不按命令拦」，绝不因此吞掉超管的正常提问。
     */
    private fun isOtherCommand(text: String, event: BotMessageContext): Boolean {
        if (text.isEmpty()) return false
        val c = text[0]
        if (c == '/' || c == '!' || c == '！') return true
        return runCatching { handledByCommand(text, event) }.getOrDefault(false)
    }

    override fun handle(message: String, event: BotMessageContext) {
        // 与 web 共用的 session id：im-<openid>。首次即登记固定对话（懒注册），web 前端据此置顶。
        val sid = "im-" + event.senderId
        logger.info("[XingtuBot] ▶ 受理超管消息 sid={} : {}", sid, message.trim().take(60))
        val title = event.username?.takeIf { it.isNotBlank() }?.let { "QQ · $it" } ?: "QQ 超管"
        ImThreadRegistry.register(sid, "QQ", title)
        val reply = runCatching { chat(sid, message.trim()) }
            .getOrElse { e ->
                logger.warn("[XingtuBot] Agent 处理失败：${e.message}")
                "⚠️ 处理失败：${e.message}"
            }
        // 用 markdown 回复：agent 回复常含 **加粗**/列表等，昕途 reply() 会转义纯文本，replyMarkdown 才渲染。
        event.replyMarkdown(reply, null)
    }

    override fun name(): String = "windyagent-agent"

    // 观察者不参与主链优先级排序，给一个稳定值即可
    override fun priority(): Int = 90

    override fun usage(): String = "私聊或@我 + 管理指令（如：查下现在多少人在线）"

    override fun description(): String = "超管直连运维 Agent（可查在线、执行命令、看日志）"

    override fun category(): String = "🛰️ 运维管理"
}
