---
name: setup_spawn
description: 设置服务器出生点区域（清障+铺设地面+设重生点+公告）
tags: [运维, 建筑]
permission: admin
params:
  world:
    type: string
    description: 目标世界名
    default: world
  radius:
    type: integer
    description: 出生区域半径（方块）
    default: 16
  block:
    type: string
    description: 地面方块材质
    default: STONE
    enum: [STONE, GRASS_BLOCK, GLASS, GOLD_BLOCK, QUARTZ_BLOCK]
outputs:
  summary:
    type: string
    description: 设置结果摘要
steps:
  - id: clear
    name: 清理区域
    tool: run_command_on_server
    args:
      command: "/execute in {world} run fill 0 63 0 {radius} 80 {radius} air"
    onFail: continue
  - id: floor
    name: 铺设地面
    tool: run_command_on_server
    args:
      command: "/execute in {world} run fill 0 62 0 {radius} 62 {radius} {block}"
  - id: spawn
    name: 设置重生点
    tool: run_command_on_server
    args:
      command: "/setworldspawn 0 63 0 {world}"
  - id: announce
    name: 全服公告
    script: |
      return "出生点已设置：世界={params.world}，半径={params.radius}，材质={params.block}"
---

# 使用说明

当腐竹要求「设置出生点」「建出生平台」「重建 spawn」时调用。

- 先确认世界存在（`world` 参数）。
- `radius` 过大（>64）先确认，避免清掉玩家建筑。
- 这是工作流技能：引擎按顺序执行清障→铺地→设重生点→公告四个步骤。
