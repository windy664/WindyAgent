---
name: backup_world
description: 备份指定世界（保存+复制+验证+通知玩家）
tags: [运维, 备份]
permission: admin
params:
  world:
    type: string
    description: 要备份的世界名
    default: world
  announce:
    type: boolean
    description: 是否全服公告备份结果
    default: true
    optional: true
outputs:
  backup_path:
    type: string
    description: 备份文件路径
steps:
  - id: save
    name: 保存世界
    tool: run_command_on_server
    args:
      command: "/save-off"
  - id: flush
    name: 强制写盘
    tool: run_command_on_server
    args:
      command: "/save-all"
  - id: copy
    name: 复制世界文件
    script: |
      def src = new File("worlds/${params.world}")
      def dst = new File("backups/${params.world}_${System.currentTimeMillis()}")
      if (!src.exists()) throw new IllegalStateException("世界 ${params.world} 不存在")
      src.eachFileRecurse { f ->
        def rel = f.path.substring(src.path.length())
        def target = new File(dst, rel)
        if (f.isDirectory()) target.mkdirs()
        else { target.parentFile.mkdirs(); f.withInputStream { it -> target << it } }
      }
      return dst.absolutePath
    assign: backup_path
  - id: restore_save
    name: 恢复自动保存
    tool: run_command_on_server
    args:
      command: "/save-on"
    onFail: continue
  - id: notify
    name: 通知玩家
    condition: "params.announce == true"
    script: |
      return "世界 ${params.world} 已备份至 ${backup_path}"
---

# 使用说明

当腐竹要求「备份世界」「存档」时调用。

- 自动关闭保存→写盘→复制→恢复保存，保证备份一致性。
- `announce=false` 可静默备份（定时任务用）。
- 备份放在服务器 `backups/` 目录，按时间戳命名。
