---
name: backup_world
description: 保存指定世界并提醒执行外部备份；默认不再用脚本直接复制服务端文件
tags: [运维, 备份]
permission: admin
args:
  - world: string optional default=world 要备份的世界名
  - announce: bool optional default=true 是否全服公告备份结果
outputs:
  - summary: string 备份流程结果
steps:
  - id: save_off
    name: 暂停自动保存
    tool: run_command_on_server
    args:
      command: "save-off"
  - id: flush
    name: 强制写盘
    tool: run_command_on_server
    args:
      command: "save-all"
  - id: save_on
    name: 恢复自动保存
    tool: run_command_on_server
    args:
      command: "save-on"
    onFail: continue
  - id: notify
    name: 通知玩家
    condition: "announce == true"
    tool: run_command_on_server
    args:
      command: "say 世界 {world} 已完成 save-all；请确认外部备份任务已复制存档。"
---

# 使用说明

当腐竹要求「备份世界」「存档」时调用。

- 本技能负责安全地 `save-off` → `save-all` → `save-on`，让外部备份工具拿到一致存档。
- 默认不再在 Bukkit 进程内用脚本递归复制世界目录，避免卡主线程和越权文件操作。
- 如果需要真正复制文件，建议接入专用文件/备份工具后再扩展工作流步骤。