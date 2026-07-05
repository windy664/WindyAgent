package org.windy.windyagent.web

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * 聊天存档 —— 「给人看」的对话记录（每会话一个 jsonl，{role,text,ts} 逐行追加）。
 *
 * 从 ChatHandler 私有实现抽出并<b>共享</b>：Web 控制台（ChatHandler）与 IM 联动（QQ 等）
 * 持有<b>同一实例</b>、按<b>同一 session id</b> 读写，从而 web 界面能看到 QQ 聊过的内容、
 * 反之亦然 —— 这是「web 与 QQ 对话无缝衔接」的存储支点（另一半是 Agent 侧同 session 的
 * 上下文连续，由 SessionManager/sessionStore 保证）。
 *
 * 线程安全：append/clear 加锁，容多来源（web 请求线程 + IM 处理线程）并发写。
 */
class ChatArchive(private val dataDir: Path) {

    private val mapper = ObjectMapper()
    private val dir: Path get() = dataDir.resolve("chatlog")
    private fun fileOf(session: String): Path =
        dir.resolve(session.replace(Regex("[^a-zA-Z0-9_.-]"), "_") + ".jsonl")

    /** 追加一条。role: "u"=用户 / "a"=助手。 */
    @Synchronized
    fun append(session: String, role: String, text: String) {
        runCatching {
            Files.createDirectories(dir)
            val line = mapper.createObjectNode()
                .put("role", role).put("text", text).put("ts", System.currentTimeMillis())
                .toString() + "\n"
            Files.write(fileOf(session), line.toByteArray(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        }
    }

    /** 清空某会话记录。 */
    @Synchronized
    fun clear(session: String) {
        runCatching { Files.deleteIfExists(fileOf(session)) }
    }

    /** 取某会话历史，返回 JSON 数组串（最近 200 条），供 /api/chat/history。 */
    fun history(session: String): String {
        val f = fileOf(session)
        if (!Files.exists(f)) return "[]"
        return runCatching {
            val lines = Files.readAllLines(f, StandardCharsets.UTF_8).filter { it.isNotBlank() }
            "[" + lines.takeLast(200).joinToString(",") + "]"
        }.getOrDefault("[]")
    }
}
