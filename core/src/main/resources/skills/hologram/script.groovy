// hologram 技能：用 DecentHolograms 创建/删除持久悬浮字（脚本+文字技能的「肌肉」）。
// 注入变量：server(Server) / plugins(PluginManager) / actions / args(Map) / log
// 在主线程执行、带超时看门狗；return 的字符串回报给 Agent。
// 软依赖 DecentHolograms：未装则报错提示、不崩。DHAPI 全静态方法经反射调用（避免编译期硬绑，
// 照搬 welcome_vip 的 Vault 运行时解析范式；DHAPI 常量位于 eu.decentsoftware.holograms.api）。
import org.bukkit.Location

if (plugins.getPlugin("DecentHolograms") == null)
    return "本服未安装 DecentHolograms，无法操作全息。请先安装该插件。"

def dhClass = Class.forName("eu.decentsoftware.holograms.api.DHAPI")
def action = ((args.action ?: "create") as String).trim().toLowerCase()

// ── 删除 ──
if (action == "delete") {
    def id = (args.id as String)?.trim()
    if (!id) return "删除全息需要 id 参数（全息唯一名）"
    if (dhClass.getMethod("getHologram", String).invoke(null, id) == null)
        return "没有名为「${id}」的全息，无需删除"
    dhClass.getMethod("removeHologram", String).invoke(null, id)
    return "已删除全息「${id}」"
}

// ── 创建 ──（默认 action）
// 文本行：竖线 | 分隔、逐行 trim、去空行；全空则给一行占位
List<String> lines = args.text ? (args.text as String).split("\\|").collect { it.trim() }.findAll { it } : []
if (lines.isEmpty()) lines = ["§f悬浮字"]

// 定位：显式坐标优先，否则取参考玩家所在位置（坐标只有子服有，Velocity 拿不到）
Location loc
if (args.x != null && args.y != null && args.z != null) {
    def worldName = (args.world as String)?.trim()
    def w = worldName ? server.getWorld(worldName) : server.worlds[0]
    if (w == null) return "找不到世界「${worldName}」，无法创建全息"
    loc = new Location(w, (args.x as double), (args.y as double), (args.z as double))
} else {
    def refName = (args.player as String)?.trim()
    if (!refName) return "创建全息需要定位：给 player（参考玩家名，取其所在位置）或 x/y/z(+world) 坐标"
    def p = server.getPlayerExact(refName)
    if (p == null) return "玩家 ${refName} 不在线，无法按其位置创建（可改用 x/y/z 坐标）"
    loc = p.location.clone().add(0, 2.3, 0)   // 抬到头顶上方，避免埋进脚下方块
}

// id：未给则按时间戳自动生成；已存在则拒绝覆盖（让调用方显式先 delete）
def id = (args.id as String)?.trim() ?: "wa_holo_${System.currentTimeMillis()}"
if (dhClass.getMethod("getHologram", String).invoke(null, id) != null)
    return "已存在名为「${id}」的全息；换个 id，或先用 action=delete 删掉再建"

// 创建（持久 saveToFile=true）：优先 4 参重载，老版本回退 3 参
try {
    dhClass.getMethod("createHologram", String, Location, Boolean.TYPE, List).invoke(null, id, loc, true, lines)
} catch (NoSuchMethodException ignored) {
    dhClass.getMethod("createHologram", String, Location, List).invoke(null, id, loc, lines)
}
return "已创建持久全息「${id}」（${lines.size} 行）@ ${loc.world.name} ${loc.blockX},${loc.blockY},${loc.blockZ}"
