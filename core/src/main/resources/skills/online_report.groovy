// name: online_report
// description: 统计当前在线玩家数与名单，按所在世界分组；想快速了解服务器在线情况时用
// 这是「纯脚本」技能的扁平写法：单个 .groovy 文件，头部 // 注释声明元数据，无需 SKILL.md，无参数。
// 注入变量：server(Server) / plugins(PluginManager) / actions / args(Map) / log
def players = server.onlinePlayers
if (players.isEmpty()) return "当前无玩家在线"
def byWorld = players.groupBy { it.world.name }
def sb = new StringBuilder("在线 ${players.size} 人：\n")
byWorld.each { world, list ->
    sb.append("· ${world}（${list.size}）：").append(list*.name.join(", ")).append("\n")
}
return sb.toString().trim()
