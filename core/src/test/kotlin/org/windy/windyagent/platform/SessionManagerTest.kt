package org.windy.windyagent.platform

import org.windy.windyagent.llm.LLMMessage
import org.windy.windyagent.llm.ToolCall
import org.windy.windyagent.llm.ToolResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 守护 [SessionManager.trimHistory]：从头裁剪不得留下孤儿 ToolResults（否则下次请求 400）。 */
class SessionManagerTest {

    private fun asst(text: String?, vararg ids: String) =
        LLMMessage.Assistant(text, ids.map { ToolCall(it, "t", "{}") })
    private fun results(vararg ids: String) =
        LLMMessage.ToolResults(ids.map { ToolResult(it, "ok", false) })

    @Test fun `裁剪后开头孤儿ToolResults被清理`() {
        val sm = SessionManager(maxHistorySize = 2)
        val h = sm.getHistory("s")
        h.addAll(listOf(LLMMessage.User("q"), asst("", "A"), results("A"), asst("done")))
        sm.trimHistory("s")
        val out = sm.getHistory("s")
        // 4→2 从头删 [User, Assistant(tc)]，剩 [ToolResults(A), Assistant(done)]；
        // 开头 ToolResults 是孤儿 → 再清理 → 只剩 Assistant(done)
        assertEquals(1, out.size)
        assertIs<LLMMessage.Assistant>(out.first())
    }

    @Test fun `未超上限不改动`() {
        val sm = SessionManager(maxHistorySize = 20)
        val h = sm.getHistory("s")
        val msgs = listOf(LLMMessage.User("q"), asst("", "A"), results("A"), asst("done"))
        h.addAll(msgs)
        sm.trimHistory("s")
        assertEquals(msgs, sm.getHistory("s"))
    }

    @Test fun `withSessionLock同会话串行`() {
        val sm = SessionManager()
        val order = mutableListOf<Int>()
        val t1 = Thread {
            sm.withSessionLock("s") { Thread.sleep(50); order.add(1) }
        }
        val t2 = Thread {
            Thread.sleep(10) // 确保 t1 先拿锁
            sm.withSessionLock("s") { order.add(2) }
        }
        t1.start(); t2.start(); t1.join(); t2.join()
        // 同会话串行：t1 先完成（1），t2 后（2）
        assertEquals(listOf(1, 2), order)
    }

    @Test fun `withSessionLock不同会话不互相阻塞`() {
        val sm = SessionManager()
        var done = false
        val holder = Thread { sm.withSessionLock("a") { Thread.sleep(200) } }
        holder.start()
        Thread.sleep(20)
        // 不同会话 "b" 应立即可进，不被 "a" 阻塞
        val t = System.currentTimeMillis()
        sm.withSessionLock("b") { done = true }
        val elapsed = System.currentTimeMillis() - t
        assertTrue(done)
        assertTrue(elapsed < 150, "不同会话不应互相阻塞，实际耗时 ${elapsed}ms")
        holder.join()
    }
}
