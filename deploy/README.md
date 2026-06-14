# 落地清单 —— Velocity + Bukkit / socket / 无 Redis

调试这套「Velocity 问问题 → Bukkit 取 Vault 经济数据」的拓扑，按下面三步走。

## 1. 放 jar
- `velocity/build/libs/windyagent-1.0-SNAPSHOT.jar` → `<Velocity>/plugins/`
- `bukkit/build/libs/windyagent-bukkit-1.0-SNAPSHOT.jar` → `<Bukkit子服>/plugins/`
- 子服需另装 **Vault** + 一个经济插件（如 EssentialsX/CMI），否则查余额返回「未安装 Vault」。

## 2. 放配置（首启动会自动生成默认模板，用下面这两份覆盖即可）
- `deploy/velocity/windyagent-config.yml` → `<Velocity>/plugins/windyagent/windyagent-config.yml`
  - **填 `llm.api-key`**（你的 mimo key）。这份是中枢，监听 socket 25599。
- `deploy/bukkit/windyagent-config.yml` → `<Bukkit子服>/plugins/WindyAgent/windyagent-config.yml`
  - **改 `deployment.server-name`** 为本子服名（`!ai` 里就用这个名字指代它）。
  - provider 模式不跑 Agent，无需填 key。

> 同机调试：socket.host 两端都用 `127.0.0.1`。
> 跨机器：Velocity 端改 `0.0.0.0` 监听；Bukkit 端 `host` 填 Velocity 机器 IP。
> 要鉴权：两端 `socket.secret` 填同一串。

## 3. 启动顺序与验证
1. 先起 Velocity（中枢监听），再起 Bukkit 子服（dial-home 连入）。
2. 看日志：Velocity「SocketHubBus 监听 …」；Bukkit「已连接中枢 … 注册为「<server-name>」」。
3. 在 Velocity 控制台或游戏内：`!ai 查一下 <玩家名> 在 <server-name> 的余额`
   - 期望：Agent 调 `get_player_balance_on_server` → 总线下发 → 子服 Vault 查询 → 回包余额。

## 安全
- 真实 api-key 只存在于上面这两个**数据目录**里，**不进 jar、不入库**（仓库模板已清空）。
- 之前那把 `tp-...` 曾随旧 jar 分发过，**建议去 mimo 控制台轮换**后再用新 key。
