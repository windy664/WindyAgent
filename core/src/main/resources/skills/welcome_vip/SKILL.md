---
name: welcome_vip
description: 给指定在线玩家发 VIP 礼包（5 钻石，装了 Vault 则再发金币）并全服欢迎；玩家开通会员时使用
script: script.groovy
args:
  - player: string 目标玩家名（须在线）
  - coins: int 发放金币数（无 Vault 时忽略）
---
# 用法

当腐竹说「给某人开 VIP / 发会员礼包」时调用本技能。

- 发放前确认玩家在线（脚本会校验，不在线直接返回提示）。
- 金额异常（如 coins > 10000）先跟腐竹确认再发。
- 这是「脚本+文字」技能：本文件说清何时/怎么用，真正的发放动作在 `script.groovy`。
