package org.windy.windyagent.skill

import org.windy.windyagent.bus.CapabilityCommand

/** 作为一条能力目录项推给中心检索（search_capabilities 可搜；描述含类型 + 参数/用法提示）。 */
fun SkillDef.toCapabilityCommand(): CapabilityCommand {
    val argHint = if (args.isEmpty()) "无参数" else args.joinToString("，") { "${it.name}(${it.type})：${it.description}" }
    val workflowSteps = steps.orEmpty()
    val tip = when {
        workflowSteps.isNotEmpty() -> {
            val stepNames = workflowSteps.joinToString("→") { it.name }
            "[工作流技能·${workflowSteps.size}步] $description ｜ 步骤：$stepNames ｜ 参数：$argHint"
        }
        isScript -> "[脚本技能] $description ｜ 用 run_skill_on_server 调用，参数：$argHint"
        else -> "[流程技能] $description ｜ 直接调用同名工具以获取操作流程"
    }
    return CapabilityCommand(name = name, aliases = emptyList(), description = tip, source = SkillDef.SOURCE)
}
