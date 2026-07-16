package org.windy.windyagent.skill

/** SkillRegistry 的最小引用接口。 */
interface SkillRegistryRef {
    fun get(name: String): SkillDef?
}
