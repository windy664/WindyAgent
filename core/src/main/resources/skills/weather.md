---
name: weather
description: 天气查询说明。当前默认 Kether 技能不再在 Bukkit 主线程里发起外部 HTTP；玩家或腐竹问现实城市天气时，用普通问答或后续接入的外部查询工具回答。
args:
  - city: string optional default=北京 城市名，例如 北京/Shanghai/London
---
# 使用说明

WindyAgent 已移除旧任意脚本，默认 Kether 技能不再直接访问外部 HTTP API，避免在 Bukkit 主线程卡服。

如果管理员询问现实天气：

- 有外部天气/联网查询工具时，优先调用对应工具。
- 没有工具时，明确说明当前服务端未接入天气查询能力，不要编造实时天气。
- 如果要恢复自动查询，建议新增一个中心端 HTTP 工具，而不是在 Bukkit Kether 脚本里联网。