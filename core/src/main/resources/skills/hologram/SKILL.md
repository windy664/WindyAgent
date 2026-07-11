---
name: hologram
description: 用 DecentHolograms 创建/删除持久悬浮字（全息文字）。放在玩家所在位置或指定坐标，跨重启留存；改内容或移动=先删同名再重建。需目标子服装 DecentHolograms。
script: script.groovy
args:
  - action: string optional default=create 操作：create(创建)/delete(删除)
  - text: string optional 悬浮字内容，多行用竖线 | 分隔，支持 §颜色码与 PAPI 占位符（create 用）
  - id: string optional 全息唯一名；create 省略则自动生成，delete 必填
  - player: string optional 定位参考玩家名（取其所在位置，建在其头顶上方）
  - x: number optional 显式坐标 X（给了 x/y/z 则优先于 player 定位）
  - y: number optional 显式坐标 Y
  - z: number optional 显式坐标 Z
  - world: string optional 显式坐标所在世界名（省略取主世界）
---
# 用法

腐竹说「在某处写个悬浮字 / 立个全息公告牌 / 删掉那个悬浮字」时用本技能。

- **持久**：创建的全息跨重启留存（saveToFile=true），不是临时提示——这是全息的本义。
- **定位**：优先 `x/y/z`(+`world`) 显式坐标；否则给 `player`，建在该玩家所在位置头顶上方。
  ⚠️ **Velocity 中心端拿不到玩家游戏内坐标**，坐标由子服执行时现取——所以务必经 `run_skill_on_server` 发到玩家**所在子服**（内核已自动注入请求者所在子服，一般无需你指定）。
- **审美**：动手前先 `knowledge_search`「全息 审美」拉《全息审美规范》，按规范组织标题/正文层次、§色码与分隔符号，别随手堆颜色。
- **改内容 / 移动**：本技能不单独支持编辑——先 `delete` 同 id，再 `create` 新内容/新位置。
- **查真实命令用法**：若要走原版 `/dh` 命令而非本技能，先 `describe_command` 探 `dh` 的子命令与补全，别凭空猜。
- 这是「脚本+文字」技能：本文件说清何时/怎么用，真正动作在 `script.groovy`。
