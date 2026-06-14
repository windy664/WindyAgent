package org.windy.windyagent.platform.velocity

import com.fasterxml.jackson.databind.ObjectMapper
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerChatEvent
import org.slf4j.LoggerFactory
import org.windy.windyagent.bus.MessageBus
import org.windy.windyagent.text.ChatTokenizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 在 Velocity 代理层捕获玩家聊天做词云 —— 比 Bukkit 侧靠谱：代理在转发到子服前就收到聊天，
 * 与后端是否 Youer/Paper 无关，绕开 Youer 的 AsyncPlayerChatEvent 主线程 bug。
 * 按玩家当前所在子服归集词频，每 60s 经总线 `behavior_chatwords` 送回该子服入库（数据仍归子服）。
 */
class ChatWordCollector(private val bus: MessageBus, private val timeoutMs: Long) {

    private val log = LoggerFactory.getLogger(ChatWordCollector::class.java)
    private val perServer = ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicLong>>()
    private val mapper = ObjectMapper()
    private val exec = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "windyagent-chatwords").apply { isDaemon = true } }
    @Volatile private var seen = 0L

    @Subscribe
    fun onChat(e: PlayerChatEvent) {
        val srv = e.player.currentServer.map { it.serverInfo.name }.orElse("?")
        if (seen++ == 0L) log.info("[聊天词云] 代理层收到首条聊天（{} @ {}）——采集生效", e.player.username, srv)
        val m = perServer.computeIfAbsent(srv) { ConcurrentHashMap() }
        ChatTokenizer.tokens(e.message).forEach { m.computeIfAbsent(it) { AtomicLong() }.incrementAndGet() }
    }

    fun start() {
        exec.scheduleAtFixedRate({ runCatching { flush() } }, 60, 60, TimeUnit.SECONDS)
        log.info("[聊天词云] 代理层聊天采集已启动（玩家需经 Velocity 连入才会被采集）")
    }

    fun stop() { runCatching { flush() }; exec.shutdown() }

    private fun flush() {
        for ((srv, words) in perServer) {
            val drained = HashMap<String, Int>()
            for ((w, a) in words) { val d = a.getAndSet(0).toInt(); if (d > 0) drained[w] = d }
            if (drained.isEmpty()) continue
            val node = mapper.createObjectNode(); val w = node.putObject("words"); drained.forEach { (k, v) -> w.put(k, v) }
            log.info("[聊天词云] {} 个词 → 子服 {}", drained.size, srv)
            runCatching { bus.dispatch(srv, "behavior_chatwords", node.toString(), timeoutMs) }   // 发后即忘，不等回包
        }
    }
}
