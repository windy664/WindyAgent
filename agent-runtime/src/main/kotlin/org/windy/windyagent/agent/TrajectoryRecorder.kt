package org.windy.windyagent.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 训练数据轨迹记录器：记录每次 Agent 交互的完整轨迹。
 *
 * 轨迹格式（JSONL，每行一条）：
 * ```
 * {"session":"player1","ts":1234,"user":"查询余额","steps":[
 *   {"type":"tool_call","tool":"get_balance","input":"...","output":"...","latency_ms":120},
 *   {"type":"tool_call","tool":"knowledge_search","input":"...","output":"...","latency_ms":80}
 * ],"response":"你的余额是 1000 金币","success":true,"tools_used":["get_balance","knowledge_search"]}
 * ```
 *
 * 可导出为 OpenAI/SFT 微调格式。异步写入，不阻塞 Agent 主路径。
 */
class TrajectoryRecorder(private val dir: Path) {

    private val log = LoggerFactory.getLogger(TrajectoryRecorder::class.java)
    private val mapper = ObjectMapper()
    private val queue = ConcurrentLinkedQueue<ObjectNode>()
    private val flusher = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "trajectory-flush").apply { isDaemon = true } }

    init {
        Files.createDirectories(dir)
        flusher.scheduleAtFixedRate({ flush() }, 5, 30, TimeUnit.SECONDS)
    }

    /** 开始一条新轨迹。返回 TrajectoryBuilder 用于记录步骤。 */
    fun start(session: String, userMessage: String): TrajectoryBuilder {
        return TrajectoryBuilder(session, userMessage)
    }

    inner class TrajectoryBuilder(private val session: String, private val userMessage: String) {
        private val steps = mutableListOf<ObjectNode>()
        private val toolsUsed = mutableListOf<String>()
        private val startTs = System.currentTimeMillis()

        fun recordToolCall(toolName: String, inputJson: String, result: org.windy.windyagent.llm.ToolResult, latencyMs: Long) {
            toolsUsed += toolName
            val step = mapper.createObjectNode()
                .put("type", "tool_call")
                .put("tool", toolName)
                .put("input", inputJson.take(500))
                .put("output", result.content.take(1000))
                .put("is_error", result.isError)
                .put("latency_ms", latencyMs)
            steps.add(step)
        }

        fun finish(response: String, success: Boolean) {
            val totalMs = System.currentTimeMillis() - startTs
            val trajectory = mapper.createObjectNode()
                .put("session", session)
                .put("ts", startTs)
                .put("user", userMessage.take(1000))
                .put("response", response.take(2000))
                .put("success", success)
                .put("total_ms", totalMs)
                .put("step_count", steps.size)
            val stepsArr = trajectory.putArray("steps")
            steps.forEach { stepsArr.add(it) }
            val toolsArr = trajectory.putArray("tools_used")
            toolsUsed.distinct().forEach { toolsArr.add(it) }
            queue.offer(trajectory)
        }
    }

    private fun flush() {
        val batch = mutableListOf<ObjectNode>()
        while (true) { val t = queue.poll() ?: break; batch.add(t) }
        if (batch.isEmpty()) return
        runCatching {
            val file = dir.resolve("trajectories.jsonl")
            val lines = batch.map { mapper.writeValueAsString(it) + "\n" }
            Files.write(file, lines.joinToString("").toByteArray(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
        }.onFailure { log.warn("轨迹写入失败：{}", it.message) }
    }

    /** 导出为 OpenAI chat 微调格式。 */
    fun exportSft(outputFile: Path) {
        flush()
        val file = dir.resolve("trajectories.jsonl")
        if (!Files.exists(file)) return
        runCatching {
            val lines = Files.readAllLines(file)
            val sft = mapper.createArrayNode()
            for (line in lines) {
                val t = runCatching { mapper.readTree(line) }.getOrNull() ?: continue
                val messages = mapper.createArrayNode()
                messages.addObject().put("role", "user").put("content", t["user"].asText())
                val assistant = messages.addObject().put("role", "assistant").put("content", t["response"].asText())
                val toolCalls = mapper.createArrayNode()
                t["steps"]?.forEach { step ->
                    if (step["type"].asText() == "tool_call") {
                        toolCalls.addObject()
                            .put("tool", step["tool"].asText())
                            .put("input", step["input"].asText())
                    }
                }
                if (toolCalls.size() > 0) assistant.set<ArrayNode>("tool_calls", toolCalls)
                sft.addObject().set<ArrayNode>("messages", messages)
            }
            Files.write(outputFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(sft))
            log.info("已导出 {} 条训练数据到 {}", sft.size(), outputFile)
        }.onFailure { log.warn("导出失败：{}", it.message) }
    }

    fun close() { flush(); flusher.shutdown() }
}
