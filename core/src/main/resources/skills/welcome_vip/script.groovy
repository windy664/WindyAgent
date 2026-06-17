// welcome_vip 的执行脚本（脚本+文字技能的「肌肉」）。
// 注入变量：server(Server) / plugins(PluginManager) / actions / args(Map) / log
// 在主线程执行、带超时看门狗；return 的字符串回报给 Agent。
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

def p = server.getPlayerExact(args.player as String)
if (p == null) return "玩家 ${args.player} 不在线，未发放"

// 经济：软依赖 Vault。用 Class.forName 运行时解析，避免无 Vault 时脚本编译期就报找不到类。
def paid = 0
if (plugins.getPlugin("Vault") != null) {
    def econClass = Class.forName("net.milkbowl.vault.economy.Economy")
    def rsp = server.servicesManager.getRegistration(econClass)
    if (rsp != null) {
        rsp.provider.depositPlayer(p, (args.coins ?: 0L) as double)  // Groovy 动态派发，无需编译期类型
        paid = (args.coins ?: 0L) as int
    }
}

p.inventory.addItem(new ItemStack(Material.DIAMOND, 5))
server.broadcastMessage("§e欢迎 VIP §b${p.name}§e！")
return "已给 ${p.name} 发放 5 钻石" + (paid > 0 ? " + ${paid} 金币" : "（未发金币：无 Vault 经济）")
