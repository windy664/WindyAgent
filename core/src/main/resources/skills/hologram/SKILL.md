---
name: hologram
description: 创建/删除持久悬浮字的操作流程。需目标子服安装 DecentHolograms；执行前先查询本服 dh 命令用法。
tags: [建筑, 展示]
permission: trusted
---
# 用法

腐竹说「在某处写个悬浮字 / 立个全息公告牌 / 删掉那个悬浮字」时用本技能。

- 先用 `search_capabilities` / `describe_command` 查询本服 `dh` 或 `decentholograms` 的真实命令格式，不要猜参数。
- Velocity 中心端拿不到玩家游戏内坐标；需要按玩家当前位置创建时，应让腐竹确认目标子服和参考玩家。
- 改内容或移动时，通常先删除同名全息，再按新内容/坐标创建。
- 默认已移除旧反射脚本；如后续要自动化，可新增 Bukkit 专用 Kether 动作或封装 DecentHolograms 工具。