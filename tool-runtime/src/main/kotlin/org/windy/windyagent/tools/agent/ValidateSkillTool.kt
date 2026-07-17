package org.windy.windyagent.tools.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.windy.windyagent.tools.AgentTool
import org.windy.windyagent.llm.ToolResult
import kotlin.collections.forEach

/**
 * 让 Agent 在保存技能前验证 Kether 脚本——**编译检查 + dry-run 模拟**。
 *
 * 两步验证：
 *  1. 编译检查（compile）：只检查语法，不执行任何代码，100% 安全。
 *  2. Dry-run（dryRun）：用 Kether dry-run 上下文执行脚本，记录"会做什么操作"但不执行真实服务器动作。
 *
 * 典型流程（AI 自动调用，服主无感）：
 *  AI: [生成 Kether 脚本]
 *      → validate_skill(script=..., mode="compile")  语法检查
 *      → validate_skill(script=..., args=..., mode="dryRun")  模拟执行
 *      → 显示 dry-run 结果给服主："脚本会执行以下操作，确认后保存"
 *      → create_skill(...)  落盘
 *
 * 如果编译失败或 dry-run 异常，AI 自动修复脚本并重试。
 */
class ValidateSkillTool(
    /** 编译检查函数：返回 null=通过，非 null=错误信息。 */
    private val compileCheck: (String) -> String?,
    /** Dry-run 函数：返回结果摘要。 */
    private val dryRun: (String, Map<String, Any?>) -> DryRunSummary
) : AgentTool {

    private val mapper = ObjectMapper()

    override val name = "validate_skill"
    override val description = "验证 Kether 脚本的正确性。mode=compile 只检查语法（安全），mode=dryRun 用 Kether dry-run 上下文模拟执行并记录「会做什么」（不碰真实服务器）。在 create_skill 之前调用，确保脚本无误。"
    override val inputSchema = """{"type":"object","properties":{"script":{"type":"string","description":"Kether 脚本源码"},"args":{"type":"object","description":"模拟参数（dryRun 用；可省略）"},"mode":{"type":"string","description":"验证模式：compile（只编译）或 dryRun（编译+模拟执行）","enum":["compile","dryRun"]}},"required":["script","mode"]}"""

    override fun execute(toolCallId: String, inputJson: String): ToolResult = runCatching {
        val node = mapper.readTree(inputJson)
        val script = node["script"]?.asText()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error(toolCallId, "缺少脚本内容（script）")
        val mode = node["mode"]?.asText() ?: "compile"

        // ① 编译检查（两种模式都做）
        val compileResult = compileCheck(script)
        if (compileResult != null) {
            return ToolResult.success(toolCallId, "❌ 编译失败：$compileResult\n\n请修复语法错误后重试。")
        }

        if (mode == "compile") {
            return ToolResult.success(toolCallId, "✅ 编译通过，语法无误。")
        }

        // ② Dry-run 模拟
        val argsMap = mutableMapOf<String, Any?>()
        node["args"]?.takeIf { it.isObject }?.fields()?.forEach { (k, v) ->
            argsMap[k] = when {
                v.isNull -> null
                v.isTextual -> v.asText()
                v.isInt || v.isLong -> v.asLong()
                v.isNumber -> v.asDouble()
                v.isBoolean -> v.asBoolean()
                else -> v.toString()
            }
        }

        val result = dryRun(script, argsMap)
        return ToolResult.success(toolCallId, result.summary())
    }.getOrElse { ToolResult.error(toolCallId, "验证失败：${it.message}") }
}

/** Dry-run 结果摘要（跨模块传递用，不依赖 bukkit 的 DryRunResult）。 */
data class DryRunSummary(
    val success: Boolean,
    val operations: List<String>,
    val error: String?
) {
    fun summary(): String {
        val sb = StringBuilder()
        sb.appendLine(if (success) "✅ Dry-run 通过 — 脚本逻辑正常" else "❌ Dry-run 失败")
        if (operations.isNotEmpty()) {
            sb.appendLine("\n模拟操作（脚本会做的事，按执行顺序）：")
            operations.forEachIndexed { i, op -> sb.appendLine("  ${i + 1}. $op") }
        }
        error?.let { sb.appendLine("\n错误：$it") }
        if (success) sb.appendLine("\n以上操作不会真正执行。确认无误后告诉我，我帮你保存。")
        else sb.appendLine("\n请修复错误后重试，或者告诉我你想实现什么，我帮你改。")
        return sb.toString().trimEnd()
    }
}



