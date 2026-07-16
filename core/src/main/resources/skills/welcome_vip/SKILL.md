---
name: welcome_vip
description: 给指定在线玩家发 VIP 欢迎礼包（5 钻石）并全服欢迎；玩家开通会员时使用
script: script.kether
args:
  - player: string 目标玩家名（须在线）
---
# 用法

当腐竹说「给某人开 VIP / 发会员礼包」时调用本技能。

- 脚本只通过 WindyAgent 暴露的 Kether 动作执行：发 5 个钻石、全服欢迎、写日志。
- 如果还要发金币，优先使用已接入的经济工具；不同服务器经济命令差异较大，不在默认脚本里硬编码。
- 这是「脚本+文字」技能：本文件说清何时/怎么用，真正动作在 `script.kether`。