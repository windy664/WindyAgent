package org.windy.windyagent

/**
 * 载体无关的 i18n 消息系统。core 模块不依赖任何平台 API。
 *
 * 启动时调用 [init] 设置语言，后续用 [t] 获取翻译。
 */
object Messages {

    private var lang = "zh_cn"

    fun init(language: String) {
        lang = language.lowercase().replace("-", "_")
    }

    /** 按 key 取翻译；缺 key 回退 zh_cn；再缺返回 key 本身。 */
    fun t(key: String): String = LANGS[lang]?.get(key) ?: LANGS["zh_cn"]?.get(key) ?: key

    /** 带 %1 %2 占位符的格式化翻译。 */
    fun t(key: String, vararg args: Any): String {
        var s = t(key)
        args.forEachIndexed { i, v -> s = s.replace("%${i + 1}", v.toString()) }
        return s
    }

    private val LANGS = mapOf(
        "zh_cn" to mapOf(
            // BukkitCommand / AgentCommand
            "cmd.usage" to "[WindyAgent] 用法：/ai <消息>",
            "cmd.processing" to "[WindyAgent] 正在处理……",
            "cmd.error" to "[WindyAgent] 处理出错，请稍后重试。",
            // AgentCommandRouter
            "router.perm_denied" to "命令「%1」需要管理员权限（控制台或有 windyagent.admin 的玩家）。",
            "router.exec_error" to "命令执行出错：%1",
            "router.unknown_cmd" to "未知命令",
            "router.help_title" to "WindyAgent 确定性命令（在触发前缀后输入，如 ai help）：",
            "router.help_show" to "help — 显示本帮助",
            "router.need_admin" to "  [需管理员]",
            "router.natural_hint" to "\n其余直接用自然语言对它说，它会自己调工具执行，例如：",
            "router.natural_examples" to "  广播 今晚8点开活动 ｜ 把 Steve 踢了，理由刷屏 ｜ 在 earth 给 Alice 查余额\n  在 earth 执行 time set day ｜ earth 上终极精华值多少 ｜ 组个 36 金币的礼包",
            "router.natural_footer" to "（运维操作走自然语言；高危命令会自动转人工审批，见上面 pending/approve）",
            // ClearCommand
            "cmd.clear.desc" to "清空当前会话的对话上下文，重新开始",
            "cmd.clear.done" to "已清空当前会话上下文，我们重新开始。",
            // HistoryCommand
            "cmd.history.desc" to "查看当前会话最近的对话",
            "cmd.history.empty" to "当前会话还没有对话记录。",
            "cmd.history.header" to "最近对话：",
            // StatusCommand
            "cmd.status.desc" to "查看 Agent 运行状态",
            // PendingCommand
            "cmd.pending.desc" to "列出待人工审批的高危操作",
            "cmd.pending.empty" to "当前无待审批操作。",
            "cmd.pending.header" to "待审批：",
            // ApproveCommand
            "cmd.approve.desc" to "批准并执行待审的高危操作：approve <单号>",
            "cmd.approve.usage" to "用法：approve <审批单号>",
            "cmd.approve.not_found" to "审批单 #%1 不存在或已过期。",
            "cmd.approve.done" to "已批准 #%1：%2",
            // DenyCommand
            "cmd.deny.desc" to "驳回待审的高危操作：deny <单号>",
            "cmd.deny.usage" to "用法：deny <审批单号>",
            "cmd.deny.not_found" to "审批单 #%1 不存在。",
            "cmd.deny.done" to "已驳回 #%1。",
            // ValueCommand
            "cmd.value.desc" to "物品估值（EMC 式种子+全图传播）。VC 上需带子服名，如 value get <子服> <物品>",
            "cmd.value.disabled" to "本节点未启用物品估值。",
            "cmd.value.unknown_sub" to "未知子命令「%1」。\n%2",
            "cmd.value.perm_denied" to "value %1 需要管理员权限（控制台或有 windyagent.admin 的玩家）。",
            "cmd.value.error" to "估值命令出错：%1",
            "cmd.value.usage" to """物品估值用法（VC 上每条都带 <子服>）：
  value build <子服>            — 一键全量解析+传播估值（异步，看子服控制台）
  value llm <子服>              — LLM 给"无配方的根"(矿/掉落/原料)定种子价→重算级联（省 token，admin）
  value llm <子服> all          — LLM 给**全部**溯源够不着的物品估价（含机器/祭坛造的成品，分批，token 多）
  value get <子服> <物品>       — 查某物估值（值/置信度/合成路径）
  value set <子服> <物品> <价> [备注] — 人工锚定，关联下游自动重算
  value unset <子服> <物品>     — 取消人工锚定，重算
  value orphans <子服>          — 列模组删除后残留的孤儿锚定
  value status <子服>           — 查构建/传播进度
  value servers                — 列出已连接子服
（build/set/unset 需管理员；get/status/servers 只读放开）""",
            // MemoryCommand
            "cmd.memory.desc" to "查看/管理长期记忆：memory [forget <id> | clear | clean]",
            "cmd.memory.disabled" to "长期记忆未启用。",
            "cmd.memory.forget_usage" to "用法：memory forget <记忆编号>",
            "cmd.memory.forgot" to "已删除记忆 #%1。",
            "cmd.memory.not_found" to "记忆 #%1 不存在。",
            "cmd.memory.cleared" to "已清空你的 %1 条长期记忆（全局记忆不受影响）。",
            "cmd.memory.cleaned" to "已清理 %1 条重复记忆。",
            "cmd.memory.empty" to "暂无长期记忆。说点稳定偏好我帮你记，或用 remember。",
            "cmd.memory.header" to "长期记忆（你的 + 管理方 + 全服）：",
            "cmd.memory.scope_global" to "[全服]",
            "cmd.memory.scope_admin" to "[管理]",
            "cmd.memory.usage" to "用法：memory（列出） | memory forget <id> | memory clear",
            // PendingApprovals
            "approval.exec_failed" to "执行失败：%1",
            // BehaviorAnalytics
            "tag.newbie" to "萌新",
            "tag.normal" to "常规玩家",
            "tag.regular" to "常驻玩家",
            "tag.hardcore" to "肝帝",
            "tag.churn_risk" to "流失风险",
            "tag.active_recent" to "近期活跃",
            "tag.builder" to "建筑党",
            "tag.miner" to "挖矿党",
            "tag.explorer" to "探索者",
            "tag.crafter" to "合成狂",
            "tag.squishy" to "脆皮",
            "tag.long_session" to "长时在线",
            "tag.new_join" to "新加入",
            "tag.unknown" to "未知",
            "behavior.build" to "建造",
            "behavior.explore" to "探索",
            "behavior.afk" to "生存挂机",
            "behavior.combat" to "战斗",
            "period.late_night" to "深夜(0-6)",
            "period.morning" to "上午(6-12)",
            "period.afternoon" to "下午(12-18)",
            "period.evening" to "晚上(18-24)",
            // BehaviorDatabase segments
            "seg.new_player" to "新玩家",
            "seg.core" to "核心(在线≥%1分)",
            "seg.active" to "活跃(近%1天)",
            "seg.churn_risk" to "流失风险(>%1天未见)",
            // DashboardServer
            "web.new_chat" to "已开新对话",
            "web.error" to "出错：%1",
            "behavior.player_not_found" to "未找到玩家「%1」（可能还没产生行为数据）",
            // AgentLoop
            "agent.stopped" to "已停止：%1",
            "agent.max_iter" to "已达到最大迭代次数（%1），任务未完成",
            // /usage
            "cmd.usage.desc" to "查看 LLM 用量统计",
            "cmd.usage.header" to "LLM 用量统计：",
            "cmd.usage.calls" to "总调用",
            "cmd.usage.input" to "输入 token",
            "cmd.usage.output" to "输出 token",
            "cmd.usage.latency" to "总延迟",
            "cmd.usage.daily" to "近 %1 天每日用量：",
            "cmd.usage.day" to "日",
            "cmd.usage.enabled" to "用量追踪已启用",
            "cmd.usage.disabled" to "用量追踪未启用",
            "cmd.rate_limited" to "[WindyAgent] 请求太频繁，请稍后再试。",
            // /compress
            "cmd.compress.desc" to "手动压缩当前会话上下文",
            "cmd.compress.done" to "上下文已压缩：%1 条 → %2 条",
            "cmd.compress.noop" to "当前上下文无需压缩（%1 条）",
            "cmd.compress.disabled" to "上下文压缩未启用",
            // /profile
            "cmd.profile.desc" to "查看当前用户画像",
            "cmd.profile.empty" to "暂无用户画像数据",
            // /memory scope
            "cmd.memory.scope_player" to "[玩家]",
        ),
        "en" to mapOf(
            "cmd.usage" to "[WindyAgent] Usage: /ai <message>",
            "cmd.processing" to "[WindyAgent] Processing…",
            "cmd.error" to "[WindyAgent] Error, please try again later.",
            "router.perm_denied" to "Command「%1」requires admin (console or player with windyagent.admin).",
            "router.exec_error" to "Command error: %1",
            "router.unknown_cmd" to "Unknown command",
            "router.help_title" to "WindyAgent commands (type after trigger prefix, e.g. ai help):",
            "router.help_show" to "help — Show this help",
            "router.need_admin" to "  [Admin]",
            "router.natural_hint" to "\nOr just talk naturally, the agent will use tools automatically, e.g.:",
            "router.natural_examples" to "  broadcast event tonight 8pm | kick Steve for spamming | check Alice balance on earth\n  run time set day on earth | how much is Ultimate Essence on earth | make a 36-coin bundle",
            "router.natural_footer" to "(Ops via natural language; high-risk commands auto-route to approval, see pending/approve)",
            "cmd.clear.desc" to "Clear session context and start fresh",
            "cmd.clear.done" to "Session context cleared. Let's start fresh.",
            "cmd.history.desc" to "View recent conversation in this session",
            "cmd.history.empty" to "No conversation history in this session.",
            "cmd.history.header" to "Recent conversation:",
            "cmd.status.desc" to "View Agent status",
            "cmd.pending.desc" to "List pending high-risk operations awaiting approval",
            "cmd.pending.empty" to "No pending approvals.",
            "cmd.pending.header" to "Pending:",
            "cmd.approve.desc" to "Approve and execute a pending operation: approve <id>",
            "cmd.approve.usage" to "Usage: approve <approval-id>",
            "cmd.approve.not_found" to "Approval #%1 not found or expired.",
            "cmd.approve.done" to "Approved #%1: %2",
            "cmd.deny.desc" to "Deny a pending operation: deny <id>",
            "cmd.deny.usage" to "Usage: deny <approval-id>",
            "cmd.deny.not_found" to "Approval #%1 not found.",
            "cmd.deny.done" to "Denied #%1.",
            "cmd.value.desc" to "Item valuation (EMC-style seed + propagation). On VC include server name, e.g. value get <server> <item>",
            "cmd.value.disabled" to "Item valuation not enabled on this node.",
            "cmd.value.unknown_sub" to "Unknown subcommand「%1」。\n%2",
            "cmd.value.perm_denied" to "value %1 requires admin (console or player with windyagent.admin).",
            "cmd.value.error" to "Valuation error: %1",
            "cmd.value.usage" to """Item valuation usage (on VC include <server>):
  value build <server>           — Full parse + propagation (async, check server console)
  value llm <server>             — LLM price "unrecipeable roots" → cascade (save tokens, admin)
  value llm <server> all         — LLM price ALL unreachable items (batched, more tokens)
  value get <server> <item>      — Query valuation (value/confidence/craft path)
  value set <server> <item> <price> [note] — Manual anchor, auto-recalculates downstream
  value unset <server> <item>    — Remove manual anchor, recalculate
  value orphans <server>         — List orphaned anchors from removed mods
  value status <server>          — Build/propagation progress
  value servers                  — List connected servers
(build/set/unset need admin; get/status/servers are read-only)""",
            "cmd.memory.desc" to "View/manage long-term memory: memory [forget <id> | clear | clean]",
            "cmd.memory.disabled" to "Long-term memory not enabled.",
            "cmd.memory.forget_usage" to "Usage: memory forget <memory-id>",
            "cmd.memory.forgot" to "Deleted memory #%1.",
            "cmd.memory.not_found" to "Memory #%1 not found.",
            "cmd.memory.cleared" to "Cleared %1 of your long-term memories (global memories unaffected).",
            "cmd.memory.cleaned" to "Cleaned %1 duplicate memories.",
            "cmd.memory.empty" to "No long-term memories. Tell me a stable preference and I'll remember it, or use remember.",
            "cmd.memory.header" to "Long-term memories (yours + admin + global):",
            "cmd.memory.scope_global" to "[Global]",
            "cmd.memory.scope_admin" to "[Admin]",
            "cmd.memory.usage" to "Usage: memory (list) | memory forget <id> | memory clear",
            "approval.exec_failed" to "Execution failed: %1",
            "tag.newbie" to "Newbie",
            "tag.normal" to "Casual",
            "tag.regular" to "Regular",
            "tag.hardcore" to "Hardcore",
            "tag.churn_risk" to "Churn Risk",
            "tag.active_recent" to "Recently Active",
            "tag.builder" to "Builder",
            "tag.miner" to "Miner",
            "tag.explorer" to "Explorer",
            "tag.crafter" to "Crafter",
            "tag.squishy" to "Squishy",
            "tag.long_session" to "Long Session",
            "tag.new_join" to "New Arrival",
            "tag.unknown" to "Unknown",
            "behavior.build" to "Build",
            "behavior.explore" to "Explore",
            "behavior.afk" to "AFK",
            "behavior.combat" to "Combat",
            "period.late_night" to "Late Night(0-6)",
            "period.morning" to "Morning(6-12)",
            "period.afternoon" to "Afternoon(12-18)",
            "period.evening" to "Evening(18-24)",
            "seg.new_player" to "New Players",
            "seg.core" to "Core(≥%1min online)",
            "seg.active" to "Active(last %1d)",
            "seg.churn_risk" to "Churn Risk(>%1d offline)",
            "web.new_chat" to "New conversation started",
            "web.error" to "Error: %1",
            "behavior.player_not_found" to "Player「%1」not found (may not have behavior data yet)",
            "agent.stopped" to "Stopped: %1",
            "agent.max_iter" to "Max iterations reached (%1), task incomplete",
            "cmd.usage.desc" to "View LLM usage statistics",
            "cmd.usage.header" to "LLM Usage Statistics:",
            "cmd.usage.calls" to "Total Calls",
            "cmd.usage.input" to "Input Tokens",
            "cmd.usage.output" to "Output Tokens",
            "cmd.usage.latency" to "Total Latency",
            "cmd.usage.daily" to "Last %1 days daily usage:",
            "cmd.usage.day" to "Day",
            "cmd.usage.enabled" to "Usage tracking enabled",
            "cmd.usage.disabled" to "Usage tracking disabled",
            "cmd.rate_limited" to "[WindyAgent] Too many requests, please try again later.",
            "cmd.compress.desc" to "Manually compress session context",
            "cmd.compress.done" to "Context compressed: %1 → %2 messages",
            "cmd.compress.noop" to "No compression needed (%1 messages)",
            "cmd.compress.disabled" to "Context compression disabled",
            "cmd.profile.desc" to "View current user profile",
            "cmd.profile.empty" to "No user profile data yet",
            "cmd.memory.scope_player" to "[Player]",
        )
    )
}
