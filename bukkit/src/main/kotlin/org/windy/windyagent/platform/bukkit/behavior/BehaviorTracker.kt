package org.windy.windyagent.platform.bukkit.behavior

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.windy.windyagent.behavior.BehaviorDatabase
import org.windy.windyagent.behavior.EventRow
import org.windy.windyagent.behavior.ProfileDelta
import org.windy.windyagent.behavior.SessionRow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 行为采集。**防卡架构**：
 *  - 监听器(主线程/异步线程)只对内存里的原子计数器 +1，纳秒级，高并发无压力；
 *  - 一个后台 daemon 单线程每 flushIntervalSec 把增量批量写库（不走 Bukkit 调度器，混合端更稳）；
 *  - 不监听 PlayerMove 等高频无效事件，在线时长靠会话增量累加。
 */
class BehaviorTracker(
    private val plugin: JavaPlugin,
    private val db: BehaviorDatabase,
    private val flushIntervalSec: Long,
    private val retentionDays: Int,
    private val trackChat: Boolean = false
) : Listener {

    private class Acc(@Volatile var name: String) {
        val playtime = AtomicLong(); val sessions = AtomicLong(); val deaths = AtomicLong()
        val commands = AtomicLong(); val chats = AtomicLong(); val placed = AtomicLong()
        val broken = AtomicLong(); val crafts = AtomicLong(); val advs = AtomicLong()
    }

    private val acc = ConcurrentHashMap<UUID, Acc>()
    private val onlineSince = ConcurrentHashMap<UUID, Long>()   // 上次结算在线时长的时间点
    private val joinTs = ConcurrentHashMap<UUID, Long>()
    private val sessionBuf = ConcurrentLinkedQueue<SessionRow>()
    private val eventBuf = ConcurrentLinkedQueue<EventRow>()
    private val exec = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "windyagent-behavior").apply { isDaemon = true } }
    @Volatile private var flushes = 0L
    private val cmdWords = ConcurrentHashMap<String, AtomicLong>()    // 命令名词频（始终采）
    private val chatWords = ConcurrentHashMap<String, AtomicLong>()   // 聊天词频（仅 trackChat 时采）
    // 仅当 trackChat 才注册。用标准的 AsyncPlayerChatEvent——它本就该在异步聊天线程触发，
    // 正好契合本类"监听器只做原子自增"的防卡设计（异步线程上对 ConcurrentHashMap/AtomicLong 自增是安全的）。
    // 旧 Youer(≤26.1.2.75) 错把它在主线程触发才撞 Bukkit 护栏刷错；26.1.2.76 起聊天链改走 server.chatExecutor 已修复。
    private val chatListener = object : Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onChat(e: AsyncPlayerChatEvent) {
            if (chatSeen++ == 0L) plugin.logger.info("[聊天词云] 收到首条聊天事件（${e.player.name}）——本服聊天采集已生效 ✓")
            a(e.player.uniqueId, e.player.name).chats.incrementAndGet()
            tokenizeChat(e.message)
        }
    }
    @Volatile private var chatSeen = 0L

    private fun bumpWord(m: ConcurrentHashMap<String, AtomicLong>, w: String) { m.computeIfAbsent(w) { AtomicLong() }.incrementAndGet() }
    private fun tokenizeChat(text: String) = org.windy.windyagent.text.ChatTokenizer.tokens(text).forEach { bumpWord(chatWords, it) }

    private fun a(id: UUID, name: String): Acc = acc.computeIfAbsent(id) { Acc(name) }.also { it.name = name }

    fun start() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        if (trackChat) {
            plugin.server.pluginManager.registerEvents(chatListener, plugin)
            plugin.logger.info("行为采集：聊天词云已开启（AsyncPlayerChatEvent；旧 Youer≤26.1.2.75 采不到，请升到 26.1.2.76+，看是否出现「收到首条聊天事件」）")
        }
        exec.scheduleAtFixedRate({ runCatching { flush() }.onFailure { plugin.logger.warning("行为 flush 失败：${it.message}") } },
            flushIntervalSec, flushIntervalSec, TimeUnit.SECONDS)
        plugin.logger.info("行为采集已启动（flush 每 ${flushIntervalSec}s，事件保留 ${retentionDays} 天）")
    }

    fun stop() { runCatching { flush() }; exec.shutdown() }

    // ---- 监听器：只自增内存计数 ----
    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(e: PlayerJoinEvent) {
        val id = e.player.uniqueId; val now = System.currentTimeMillis()
        a(id, e.player.name).sessions.incrementAndGet()
        joinTs[id] = now; onlineSince[id] = now
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(e: PlayerQuitEvent) {
        val id = e.player.uniqueId; val now = System.currentTimeMillis()
        val since = onlineSince.remove(id); val jt = joinTs.remove(id)
        if (since != null) a(id, e.player.name).playtime.addAndGet((now - since) / 1000)
        if (jt != null) sessionBuf.add(SessionRow(id.toString(), e.player.name, jt, now, (now - jt) / 1000))
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(e: PlayerDeathEvent) {
        val p = e.entity; a(p.uniqueId, p.name).deaths.incrementAndGet()
        eventBuf.add(EventRow(p.uniqueId.toString(), p.name, "death", System.currentTimeMillis(), ""))
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onCommand(e: PlayerCommandPreprocessEvent) {
        a(e.player.uniqueId, e.player.name).commands.incrementAndGet()
        val cmd = e.message.removePrefix("/").trim().substringBefore(' ').lowercase()
        if (cmd.isNotEmpty()) bumpWord(cmdWords, cmd)   // 命令词云：只记命令名，不记参数
    }

    // 注：聊天采集走上面独立的 chatListener(AsyncPlayerChatEvent)，仅 trackChat 时注册；
    // 这里主监听器不放聊天，避免没开词云时也注册无用监听。

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlace(e: BlockPlaceEvent) { a(e.player.uniqueId, e.player.name).placed.incrementAndGet() }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBreak(e: BlockBreakEvent) { a(e.player.uniqueId, e.player.name).broken.incrementAndGet() }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraft(e: CraftItemEvent) {
        val p = e.whoClicked; a(p.uniqueId, p.name).crafts.incrementAndGet()
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onAdvancement(e: PlayerAdvancementDoneEvent) {
        val key = runCatching { e.advancement.key.toString() }.getOrNull() ?: return
        if (key.startsWith("minecraft:recipes/")) return     // 配方解锁=噪声，丢
        val p = e.player; a(p.uniqueId, p.name).advs.incrementAndGet()
        eventBuf.add(EventRow(p.uniqueId.toString(), p.name, "advancement", System.currentTimeMillis(), key))
    }

    // ---- 后台批量落库 ----
    private fun flush() {
        val now = System.currentTimeMillis()
        // 给仍在线的玩家结算这段在线时长
        for ((id, since) in onlineSince) {
            val d = (now - since) / 1000
            if (d > 0) { acc[id]?.playtime?.addAndGet(d); onlineSince[id] = now }
        }
        // 快照并清零，组装增量
        val deltas = ArrayList<ProfileDelta>()
        for ((id, ac) in acc) {
            val pd = ProfileDelta(id.toString(), ac.name,
                ac.playtime.getAndSet(0), ac.sessions.getAndSet(0).toInt(), ac.deaths.getAndSet(0).toInt(),
                ac.commands.getAndSet(0).toInt(), ac.chats.getAndSet(0).toInt(), ac.placed.getAndSet(0).toInt(),
                ac.broken.getAndSet(0).toInt(), ac.crafts.getAndSet(0).toInt(), ac.advs.getAndSet(0).toInt())
            if (pd.playtimeSec != 0L || pd.sessions != 0 || pd.deaths != 0 || pd.commands != 0 || pd.chats != 0 ||
                pd.blocksPlaced != 0 || pd.blocksBroken != 0 || pd.crafts != 0 || pd.advancements != 0) deltas.add(pd)
            if (!onlineSince.containsKey(id)) acc.remove(id, ac)   // 离线且已清零→回收内存
        }
        db.bumpProfiles(now, deltas)
        db.writeSessions(drain(sessionBuf)); db.writeEvents(drain(eventBuf))
        db.bumpWords("cmd", drainWords(cmdWords)); db.bumpWords("chat", drainWords(chatWords))
        db.recordOnline(now, onlineSince.size)   // 在线数快照（用于趋势图）
        if (deltas.isNotEmpty()) plugin.logger.info("[行为采集] flush：${deltas.size} 名玩家画像更新")
        if (++flushes % 60L == 0L) db.pruneEvents(now - retentionDays * 86_400_000L)
    }

    private fun <T> drain(q: ConcurrentLinkedQueue<T>): List<T> {
        val out = ArrayList<T>(); while (true) { out.add(q.poll() ?: break) }; return out
    }

    private fun drainWords(m: ConcurrentHashMap<String, AtomicLong>): Map<String, Int> {
        val out = HashMap<String, Int>(); for ((w, a) in m) { val d = a.getAndSet(0).toInt(); if (d > 0) out[w] = d }; return out
    }
}

