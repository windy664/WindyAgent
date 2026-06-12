package org.windy.windyagent.safety

/**
 * 触发来源的信任级别，决定高危命令的处置：
 * - TRUSTED（控制台 / 有 windyagent.admin 权限的玩家）：高危走人工审批闸。
 * - UNTRUSTED（玩家聊天 / 无权限玩家）：高危直接拒绝，连审批都不给——挡提示注入越权。
 */
enum class TrustLevel { TRUSTED, UNTRUSTED }
