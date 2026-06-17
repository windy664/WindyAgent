---
name: cmi_player_lookup
description: 综合查询一个 CMI 玩家的完整信息（余额/最后登录/家/封禁记录/礼包领取情况）
origin: builtin
tags: [CMI, 玩家, 查询]
params:
  player:
    type: string
    description: 玩家名
steps:
  - id: userinfo
    name: 查询基本信息
    tool: cmi_userinfo
    args:
      player: "{player}"
  - id: homes
    name: 查询家列表
    tool: cmi_home
    args:
      player: "{player}"
  - id: balance
    name: 查询余额
    tool: cmi_balance
    args:
      player: "{player}"
    onFail: continue
---

# 使用说明

当管理员要求「查一下某玩家的信息」「看看某人的资料」时调用。

会一次性查询：基本信息（最后登录/IP/位置）、家列表、余额。
