package org.windy.windyagent.ops

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 一条定时任务。到点把 [action]（broadcast 广播 / command 执行命令）下发到 [target]（子服名，或 * = 全部已连）。
 * 调度二选一：interval（每 N 分钟）/ daily（每天 HH:MM，可限定 [days] 周几；空=每天）。
 */
/** 脚本里的一步：确定性动作。action: broadcast | command。 */
data class TaskStep(val action: String = "broadcast", val target: String = "", val payload: String = "")

data class ScheduledTask(
    val id: String = "",
    val name: String = "",
    val enabled: Boolean = true,
    val action: String = "broadcast",   // broadcast | command | agent(实时) | script(LLM 编译的固定步骤)
    val target: String = "",            // 子服名 或 "*"=全部已连
    val payload: String = "",           // 广播文案 / 命令 / (script)需求描述 / (agent)指令
    /** action=script 时：LLM 编译出的待执行步骤序列（到点确定性执行，不再调 LLM/Agent）。 */
    val script: List<TaskStep> = emptyList(),
    val type: String = "interval",      // interval | daily
    val intervalMin: Int = 60,
    val time: String = "12:00",         // daily 用，HH:MM
    val days: List<Int> = emptyList(),  // daily 用，1..7=周一..周日；空=每天
    val lastRun: Long = 0,
    val lastResult: String = "",
    var nextRun: Long = 0
)

/**
 * 平台无关定时任务调度器：任务存盘（`tasks.json`），单 daemon 线程每 30s 巡检到点任务，
 * 经注入的 [exec] 执行（载体把它接到总线：广播/命令下发到子服）。CRUD 即时落盘并重算下次触发。
 */
class TaskScheduler(
    private val file: Path,
    private val exec: (ScheduledTask) -> String
) {
    private val log = LoggerFactory.getLogger(TaskScheduler::class.java)
    private val mapper: ObjectMapper = ObjectMapper().registerKotlinModule()
    private val tasks = ConcurrentHashMap<String, ScheduledTask>()
    private val ticker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "windyagent-scheduler").apply { isDaemon = true } }

    init {
        runCatching {
            if (Files.exists(file)) mapper.readValue<List<ScheduledTask>>(Files.readAllBytes(file)).forEach { tasks[it.id] = it }
        }.onFailure { log.warn("载入定时任务失败：{}", it.message) }
    }

    fun start() {
        // 启动时为缺下次触发的任务补算
        val now = System.currentTimeMillis()
        tasks.values.filter { it.nextRun <= 0 }.forEach { tasks[it.id] = it.copy(nextRun = computeNext(it, now)) }
        ticker.scheduleAtFixedRate({ runCatching { tick() }.onFailure { log.warn("定时任务巡检异常：{}", it.message) } }, 10, 30, TimeUnit.SECONDS)
        log.info("定时任务调度器已启动 — {} 个任务", tasks.size)
    }

    fun stop() = ticker.shutdown()

    @Synchronized
    private fun tick() {
        val now = System.currentTimeMillis()
        var changed = false
        for (t in tasks.values) {
            if (!t.enabled || t.nextRun <= 0 || now < t.nextRun) continue
            val result = runCatching { exec(t) }.getOrElse { "执行异常：${it.message}" }
            log.info("定时任务「{}」已触发 → {}", t.name, result.take(80))
            tasks[t.id] = t.copy(lastRun = now, lastResult = result.take(200), nextRun = computeNext(t, now))
            changed = true
        }
        if (changed) persist()
    }

    /** 下次触发时刻。interval：from + N 分钟；daily：from 之后最近的 HH:MM（落在允许周几）。 */
    private fun computeNext(t: ScheduledTask, from: Long): Long = when (t.type) {
        "daily" -> nextDaily(t.time, t.days, from)
        else -> from + t.intervalMin.coerceAtLeast(1) * 60_000L
    }

    private fun nextDaily(time: String, days: List<Int>, from: Long): Long {
        val parts = time.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 12
        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val zone = ZoneId.systemDefault()
        val base = Instant.ofEpochMilli(from).atZone(zone).withSecond(0).withNano(0)
        var cand = base.withHour(h).withMinute(m)
        if (!cand.isAfter(base)) cand = cand.plusDays(1)
        if (days.isNotEmpty()) { var g = 0; while (cand.dayOfWeek.value !in days && g++ < 8) cand = cand.plusDays(1) }
        return cand.toInstant().toEpochMilli()
    }

    // ---- CRUD（即时落盘）----
    fun list(): List<ScheduledTask> = tasks.values.sortedBy { it.nextRun.let { n -> if (n <= 0) Long.MAX_VALUE else n } }

    fun upsert(t: ScheduledTask): ScheduledTask {
        val id = t.id.ifBlank { UUID.randomUUID().toString().take(8) }
        val saved = t.copy(id = id, nextRun = computeNext(t, System.currentTimeMillis()))
        tasks[id] = saved; persist(); return saved
    }

    fun delete(id: String): Boolean { val r = tasks.remove(id) != null; if (r) persist(); return r }

    fun toggle(id: String): ScheduledTask? {
        val t = tasks[id] ?: return null
        val nt = t.copy(enabled = !t.enabled, nextRun = computeNext(t, System.currentTimeMillis()))
        tasks[id] = nt; persist(); return nt
    }

    /** 立即手动触发一次（不改 nextRun 的周期）。 */
    fun runNow(id: String): String {
        val t = tasks[id] ?: return "任务不存在"
        val result = runCatching { exec(t) }.getOrElse { "执行异常：${it.message}" }
        tasks[id] = t.copy(lastRun = System.currentTimeMillis(), lastResult = result.take(200)); persist()
        return result
    }

    fun toJson(): String = mapper.writeValueAsString(list())

    @Synchronized
    private fun persist() {
        runCatching {
            Files.createDirectories(file.parent)
            Files.write(file, mapper.writeValueAsBytes(tasks.values.sortedBy { it.id }))
        }.onFailure { log.warn("定时任务落盘失败：{}", it.message) }
    }
}
