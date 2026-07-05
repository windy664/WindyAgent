package org.windy.windyagent.agent

import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.llm.ToolCall
import org.windy.windyagent.llm.ToolResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

/**
 * 守护 [sanitizeToolPairing] 与 [stepLabel] —— 前者是"满屏 null / 400 tool_call 无响应"那类 bug 的最后防线，
 * 后者是过程展示里 skill 名的提取。这两个是纯函数，最该有测试。
 */
class AgentLoopTest {

    private fun user(t: String) = LLMMessage.User(t)
    private fun asst(text: String?, vararg ids: String) =
        LLMMessage.Assistant(text, ids.map { ToolCall(it, "tool_$it", "{}") })
    private fun results(vararg ids: String) =
        LLMMessage.ToolResults(ids.map { ToolResult(it, "ok", false) })

    @Test fun `正常配对原样保留`() {
        val msgs = listOf(user("hi"), asst("", "A"), results("A"), asst("done"))
        val out = sanitizeToolPairing(msgs)
        assertEquals(msgs, out)
    }

    @Test fun `孤儿tool_calls有文本时降级为纯文本assistant`() {
        // Assistant 带 toolCalls 但后面没有 ToolResults → 去掉 toolCalls，保留文本
        val out = sanitizeToolPairing(listOf(user("hi"), asst("我在想", "A"), user("再说")))
        assertEquals(3, out.size)
        val a = out[1]
        assertIs<LLMMessage.Assistant>(a)
        assertTrue(a.toolCalls.isEmpty(), "孤儿 toolCalls 应被剥离")
        assertEquals("我在想", a.content)
    }

    @Test fun `孤儿tool_calls无文本时整条丢弃`() {
        val out = sanitizeToolPairing(listOf(user("hi"), asst(null, "A")))
        assertEquals(listOf(user("hi")), out)
    }

    @Test fun `开头孤儿ToolResults被丢弃`() {
        // 历史被从头裁剪后可能留下没有对应 Assistant(tool_calls) 的 ToolResults
        val out = sanitizeToolPairing(listOf(results("A"), asst("continue")))
        assertEquals(listOf(asst("continue")), out)
    }

    @Test fun `ToolResults未覆盖全部tool_call_id视为孤儿`() {
        // Assistant 调了 A、B 两个工具，但 ToolResults 只回了 A → 不算配对
        val out = sanitizeToolPairing(listOf(asst("x", "A", "B"), results("A")))
        // Assistant 降级为纯文本，ToolResults(A) 成孤儿被丢
        assertEquals(1, out.size)
        val a = out[0]
        assertIs<LLMMessage.Assistant>(a)
        assertTrue(a.toolCalls.isEmpty())
    }

    @Test fun `stepLabel从run_skill_on_server提取skill名`() {
        assertEquals("skill:farm_boost",
            stepLabel("run_skill_on_server", """{"skill":"farm_boost","server":"s1"}"""))
        assertEquals("skill:x", stepLabel("run_skill", """{"skill":"x"}"""))
    }

    @Test fun `stepLabel普通工具原样返回`() {
        assertEquals("kick_player", stepLabel("kick_player", """{"player":"Steve"}"""))
        assertEquals("broadcast", stepLabel("broadcast", """{"message":"hi"}"""))
    }

    @Test fun `stepLabel无skill字段或坏json回退工具名`() {
        assertEquals("run_skill_on_server", stepLabel("run_skill_on_server", """{"server":"s1"}"""))
        assertEquals("run_skill_on_server", stepLabel("run_skill_on_server", "not-json"))
    }
}
