package org.windy.windyagent.skill

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 技能状态持久化：per-skill 的 key-value 存储，跨次执行保留数据。
 *
 * 存储位置：`skills/<name>/state.json`（与 SKILL.md 同目录）。
 * 脚本里通过 `state` 变量读写（Groovy binding），工作流步骤通过 `{state.xxx}` 插值读取。
 *
 * 用途示例：
 *  - 记录上次执行时间/路径：`state.lastBackupPath = "/backups/world_123"`
 *  - 计数器：`state.runCount = (state.runCount ?: 0) + 1`
 *  - 记录配置偏好：`state.lastBlock = "GOLD_BLOCK"`
 *
 * 线程安全：内部用 ConcurrentHashMap；写盘同步（每次 put 后落盘，防丢数据）。
 */
class SkillState(
    /** 技能名（用于确定 state.json 路径）。 */
    private val skillName: String,
    /** 技能库根目录（skills/）。 */
    private val skillsDir: File
) {
    private val log = LoggerFactory.getLogger(SkillState::class.java)
    private val mapper = ObjectMapper().registerKotlinModule()
    private val data = ConcurrentHashMap<String, Any?>()
    private val stateFile: File get() = File(skillsDir, "$skillName/state.json")

    init {
        load()
    }

    /** 读取状态值。脚本里直接用 `state.xxx` 或 `state.get("xxx")`。 */
    @Suppress("UNCHECKED_CAST")
    operator fun get(key: String): Any? = data[key]

    /** 写入状态值（自动落盘）。超出大小限制时拒绝写入并记日志。 */
    operator fun set(key: String, value: Any?) {
        if (value == null) { data.remove(key); persist(); return }
        // 写前检查：序列化后大小不能超限
        val testSize = runCatching { mapper.writeValueAsBytes(data.toMap() + (key to value)).size }.getOrNull()
        if (testSize != null && testSize > MAX_STATE_BYTES) {
            log.warn("技能状态 [{}] 写入被拒：{} 字节超过上限 {}KB（key={}）", skillName, testSize, MAX_STATE_BYTES / 1024, key)
            return
        }
        data[key] = value
        persist()
    }

    /** 获取所有状态（供变量插值用）。 */
    fun all(): Map<String, Any?> = data.toMap()

    /** 清空状态。 */
    fun clear() {
        data.clear()
        persist()
    }

    /** 状态条数。 */
    fun size(): Int = data.size

    private fun load() {
        runCatching {
            if (stateFile.exists()) {
                val map = mapper.readValue(stateFile, Map::class.java) as? Map<String, Any?>
                map?.let { data.putAll(it) }
            }
        }.onFailure { log.warn("加载技能状态失败 [{}]：{}", skillName, it.message) }
    }

    @Synchronized
    private fun persist() {
        runCatching {
            stateFile.parentFile?.mkdirs()
            mapper.writerWithDefaultPrettyPrinter().writeValue(stateFile, data.toMap())
        }.onFailure { log.warn("保存技能状态失败 [{}]：{}", skillName, it.message) }
    }

    companion object {
        /** 从上下文 Map 中取出 SkillState（如果存在）。 */
        fun fromContext(ctx: Map<String, Any?>): SkillState? = ctx["_skillState"] as? SkillState
        /** 状态文件大小上限（1MB）。 */
        private const val MAX_STATE_BYTES = 1024 * 1024
    }
}
