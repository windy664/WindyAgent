package org.windy.windyagent.bridge

import org.windy.xingtubot.common.binding.BindingRepository
import org.windy.windyagent.player.PlayerDirectory

/**
 * 昕途绑定信息 → 玩家管理面板的字段贡献者：给每个在线玩家补一列「绑定QQ」。
 *
 * 这是 [PlayerDirectory] 可扩展框架的<b>第一个真实 contributor</b>——昕途装了且拿得到
 * 绑定仓库时才注册（见 [XingtuBotWiring]）。
 *
 * <p><b>性能</b>：昕途 [BindingRepository.findByPlayer] 每次都裸查 DB（无缓存），逐个在线玩家查
 * 就是 N+1 次查询——面板一刷新，N 个在线玩家 = N 次 SELECT（MySQL 尤其是 N 次网络往返），会卡。
 * 故本类<b>批量 + 缓存</b>：一次 [BindingRepository.all] 拉全表建 player→qq 映射，缓存 [ttlMs]；
 * 面板轮询刷新期内 0 次 DB 查询，缓存过期才重拉一次。绑定表通常几百~几千条，一次 all() 可接受。
 *
 * <p>惰性隔离：本类引用昕途类型（BindingRepository），只由 XingtuBotWiring 在确认昕途在之后创建。
 */
internal class XingtuBotPlayerContributor(
    private val bindings: BindingRepository,
    private val ttlMs: Long = 60_000L,
) : PlayerDirectory.PlayerInfoContributor {

    override val id: String = "xingtubot-qq"

    @Volatile private var cache: Map<String, String> = emptyMap() // player -> qq
    @Volatile private var cachedAt: Long = 0L

    override fun columns(): List<PlayerDirectory.Column> =
        listOf(PlayerDirectory.Column("qq", "绑定QQ"))

    override fun fieldsFor(players: List<String>): Map<String, Map<String, String>> {
        val map = snapshot()
        val out = HashMap<String, Map<String, String>>()
        for (name in players) {
            val qq = map[name] ?: continue
            out[name] = mapOf("qq" to qq)
        }
        return out
    }

    /** player→qq 全量映射，带 TTL 缓存。过期时一次 all() 重建；查询失败则沿用旧缓存。 */
    private fun snapshot(): Map<String, String> {
        val now = System.currentTimeMillis()
        val c = cache
        if (c.isNotEmpty() && now - cachedAt < ttlMs) return c
        val fresh = runCatching {
            bindings.all().asSequence()
                .mapNotNull { e ->
                    val q = e.qq?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    e.player to q
                }
                .toMap()
        }.getOrElse { return c } // 失败保留旧缓存，不清空
        cache = fresh
        cachedAt = now
        return fresh
    }
}
