package org.windy.windyagent.platform.bukkit.skill

import com.fasterxml.jackson.databind.JsonNode

/** 把一个 JSON 对象节点摊成 Kether 参数 用的 Map（数字/布尔/字符串按值类型转）。 */
object SkillArgs {
    fun toMap(node: JsonNode?): Map<String, Any?> {
        if (node == null || !node.isObject) return emptyMap()
        val map = LinkedHashMap<String, Any?>()
        node.fields().forEach { (k, v) ->
            map[k] = when {
                v.isNull -> null
                v.isInt || v.isLong -> v.asLong()
                v.isNumber -> v.asDouble()
                v.isBoolean -> v.asBoolean()
                v.isTextual -> v.asText()
                else -> v.toString()
            }
        }
        return map
    }
}

