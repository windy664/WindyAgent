package org.windy.windyagent.bridge

import java.util.concurrent.ConcurrentHashMap

/**
 * IM 联动「固定对话」注册表 —— IM 超管在各平台的对话，登记为 web 控制台可见的固定线程。
 *
 * <p>目的：让「QQ 超管的对话」和「web 后台的对话」用<b>同一个 session id</b>，从而无缝衔接
 * ——超管在 QQ 说的话，web 打开对应固定对话即可看到并接着聊（Agent 上下文、聊天记录皆共享）。
 *
 * <p>登记时机：<b>懒注册</b>。超管首次通过某 IM 平台发消息时登记（因 PermissionService 只判
 * isAdmin、不暴露超管名单，无法预知有哪些超管）。web 前端拉 [snapshot] 把这些对话置顶固定显示。
 *
 * <p>可扩展：未来飞书/钉钉/企业微信的 Connector 同样往这里登记自己的固定对话，web 无需改动。
 */
object ImThreadRegistry {

    /**
     * 一个 IM 固定对话。
     * @param session   与 web 共用的会话 id（如 `im-<openid>`）—— 无缝衔接的关键。
     * @param platform  平台标识（如 "QQ"），前端用于图标/分组。
     * @param title     显示名（如超管昵称）。
     * @param updatedAt 最近活跃时间（排序用）。
     */
    data class ImThread(
        val session: String,
        val platform: String,
        val title: String,
        val updatedAt: Long,
    )

    private val threads = ConcurrentHashMap<String, ImThread>()

    /** 登记/更新一个固定对话（幂等，按 session 覆盖并刷新活跃时间）。 */
    fun register(session: String, platform: String, title: String) {
        threads[session] = ImThread(session, platform, title, System.currentTimeMillis())
    }

    /** 当前所有固定对话，按最近活跃降序。供 web 的 /api/im/threads。 */
    fun snapshot(): List<ImThread> = threads.values.sortedByDescending { it.updatedAt }
}
