package org.windy.windyagent.platform.velocity

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.command.ValueExecutor
import org.windy.windyagent.safety.TrustLevel
import java.util.concurrent.TimeUnit

/**
 * 中心（Velocity）侧 value 执行后端：`value <子命令> <子服> [参数]` → 校验子服已连 →
 * 总线派发 `value_<子命令>` 到子服 → 回文本。DB/引擎都在子服，这里只过命令与结果。
 * 长操作（build/set）子服内异步执行、即时回「已开始」，故不会撞总线超时。
 */
class RemoteValueExecutor(
    private val bus: MessageBus,
    private val timeoutMs: Long,
    private val connectedServers: () -> Set<String>
) : ValueExecutor {
    private val mapper = ObjectMapper()

    override fun execute(sub: String, rest: String, trust: TrustLevel): String {
        if (sub == "servers") {
            val s = connectedServers()
            return if (s.isEmpty()) "当前没有已连接的子服（子服需启用 cross-server 并推送能力目录）。"
            else "已连接子服：\n" + s.sorted().joinToString("\n") { "  $it" }
        }
        val parts = rest.split(Regex("\\s+"), limit = 2)
        val server = parts.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return "用法：value $sub <子服> ${argHint(sub)}"
        val connected = connectedServers()
        if (server !in connected) {
            return "子服「$server」未连接。已连：${if (connected.isEmpty()) "（无）" else connected.sorted().joinToString("、")}"
        }
        val subArgs = parts.getOrNull(1)?.trim().orEmpty()
        val argsJson = buildArgs(sub, subArgs) ?: return "用法：value $sub <子服> ${argHint(sub)}"
        return dispatchText(server, "value_$sub", argsJson)
    }

    /** @return argsJson；参数不足返回 null（让上层打印用法）。 */
    private fun buildArgs(sub: String, a: String): String? = when (sub) {
        "build", "status", "orphans" -> "{}"
        "get", "unset" -> {
            if (a.isBlank()) null else mapper.createObjectNode().put("item", a).toString()
        }
        "set" -> {
            // <物品> <价> [备注]
            val t = a.split(Regex("\\s+"), limit = 3)
            val item = t.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
            val value = t.getOrNull(1)?.toDoubleOrNull() ?: return null
            mapper.createObjectNode().put("item", item).put("value", value).put("note", t.getOrNull(2) ?: "").toString()
        }
        else -> "{}"
    }

    private fun argHint(sub: String) = when (sub) {
        "get", "unset" -> "<物品>"
        "set" -> "<物品> <价> [备注]"
        else -> ""
    }

    private fun dispatchText(server: String, action: String, args: String): String = runCatching {
        val reply = bus.dispatch(server, action, args, timeoutMs).get(timeoutMs + 1000, TimeUnit.MILLISECONDS)
        if (reply.success) reply.content else "子服未成功：${reply.content}"
    }.getOrElse { "派发到「$server」失败：${it.message}" }
}
