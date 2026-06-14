# WindyAgent

面向 Minecraft 服务器运营的自主 AI Agent。一个可替换 LLM 的「服务器大脑」：管理员说人话或不说话，它都能查信息、跑运维、主动巡检告警、按计划执行任务，并把高危操作交回人工审批。

Kotlin / Gradle 多模块，约 **7800 行、110 个源文件、6 个模块**；一个 jar 同时是 Velocity 插件和 Bukkit 插件。LLM 默认走 OpenAI 兼容接口（实测小米 mimo），可换 Claude / Ollama。

---

## 一句话定位

不是「又一个聊天机器人插件」。它的核心取向是 **「能确定化的全部确定化，LLM 只在真正需要『理解』和『判断』的接缝处出场」**——所以它高频操作零 token、可复现、可审计，又保留了自然语言交互和主动决策的能力。

## 设计理念（也是这个项目最想证明的东西）

| 这步任务的特征 | 交给谁 |
|---|---|
| 可枚举 / 可计算 / 要复现 / 高频 | **确定性代码**（命令路由、图传播、规则分群） |
| 开放语言 / 要世界知识 / 模糊意图 | **LLM** |
| 介于之间（打标、归档、抽取） | **LLM 出标签 + 代码出数值** |

举例：给上千模组物品估值，没让 LLM 逐个报价（实测 14 分钟还算不准），而是用 EMC 式「种子值 + 全图传播」的确定性图算法（秒级、可复现），LLM 只负责把无配方根物**归档到稀有度档位**（它擅长分类，不擅长十层合成树的乘法累加）。

## 架构

中心-提供方拓扑，**一个通用 jar 三种形态**（`deployment.mode` 切换）：

```
                   ┌─────────────────────────────┐
   管理员 ──!ai──▶ │  Velocity 中心（Agent 大脑）  │
   网页控制台 ───▶ │  ReAct / Plan-Execute 路由   │
                   │  安全闸 · 记忆 · 哨兵 · 调度   │
                   └──────────────┬──────────────┘
                          自建 Socket 中枢 / Redis 总线
              ┌───────────────────┼───────────────────┐
        ┌─────▼─────┐       ┌─────▼─────┐       ┌─────▼─────┐
        │ 子服 earth │       │ 子服 lobby │  ...  │  子服 N   │
        │ provider  │       │ provider  │       │ provider  │
        │ 执行+采集  │       │ 执行+采集  │       │ 执行+采集  │
        └───────────┘       └───────────┘       └───────────┘
```

- **provider**：子服不跑 Agent，连中枢执行下发动作 + 采集本地数据（VC 群组）。
- **standalone**：单服自带嵌入式 Agent，无总线（单 Paper/Spigot 服）。
- **hub**：自带 Agent 且当总线中枢（无 Velocity 的多服群组）。

> **通用 jar 怎么做到的**：`:velocity` 的 shadowJar 把 `:bukkit` 一并打进同一个 fat jar，里面同时有 `velocity-plugin.json`（指向 Velocity 主类）和 `plugin.yml`（指向 Bukkit 主类）。丢进 Velocity 或现代 Bukkit(Paper/Mohist/Youer) 的 `plugins/` 各自识别加载，另一平台的主类休眠不触发。

## 真正解决过的工程问题

这些是项目里花时间最多、也最能说明问题的点：

**1. 跨进程总线抽象，无 Redis 也能跑**
`MessageBus` 接口后藏三种实现：自建 **Socket 中枢**（星型拓扑、子服 dial-home、thread-per-connection、按 requestId 关联回包）、Redis pub/sub、进程内（测试）。上层只依赖接口，配置切换零改动。

**2. 能力自发现，即插即用**
子服启动把本机命令表（实测一台 439 条）整理成能力目录，经总线推给中心注册表。中心**没有硬编码的子服列表**——谁连进来谁的能力就可被 Agent 检索调用。注册表持久化，重启免重推。

**3. 没有 embedding 也能做语义检索**
mimo 无嵌入模型，于是检索是「关键词稀疏（拉丁词 + 中文相邻二元组 + 字段加权）→ 命中弱时 LLM 扩词再搜」的兜底阶梯。向量 + 余弦（`VectorIndex`）的坑挖好留着，配任一 OpenAI 兼容 embedding 即点亮——**架构缝留着，不为暂时用不上的能力提前增依赖**。

**4. 混合端（Youer / NeoForge）的一堆反射硬骨头**
- 混合端 Bukkit 调度器不可靠 → 优先反射拿 NMS `MinecraftServer`（它本身是 Executor）做主线程跳转。
- `getTPS()` 是 Paper 扩展，Youer 没有 → 退 NMS 平均 tick 耗时反射算 TPS。
- `/neoforge tps` 的输出走 log4j 日志、不回命令 sender → 反射给根 logger 临时挂动态代理 Appender 旁路捕获。
- 每处反射都 `runCatching` 降级，撬不动退「未知/-1/空」，绝不让一个读不到的指标搞崩功能。

**5. 防卡的行为采集**
监听器只对内存里的 `AtomicLong` 计数器 +1（纳秒级），后台单线程每 60s 批量落 SQLite；**绝不监听 PlayerMove**，在线时长靠会话增量累加。采集与分析在独立模块 `:behavior`，平台无关，与 Agent 解耦。

**6. fat jar 的 jackson 类加载冲突**
宿主(Velocity)和同居插件各带 jackson，同一份 `com.fasterxml.jackson` 在不同类加载器间劈叉 → `NoClassDefFoundError`。解决：shadowJar 把 jackson 重定位到私有命名空间 + 合并 SPI 服务文件。

**7. 安全：能力即权限，不只是「拦」**
- 高危命令（stop/op/ban…）：可信来源走**人工审批闸**（网页/游戏内可批，带历史），不可信来源直接拒。
- 普通玩家的 `!ai`：**根本不挂任何工具**，只走知识库问答——他想让 AI 踢人，不是被拦住，是压根没那个能力。
- 无人值守的定时 Agent 任务：高危一律拦截只记录，不挂审批单空等。

## 功能

- **自然语言运维**：`!ai 把刷屏的 Steve 踢了` / 网页对话；ReAct（简单）与 Plan-Execute（多步）之间启发式 + LLM 兜底路由。
- **确定性命令**：`status`/`clear`/`value get earth 终极精华` 等首词命中即执行，零 token、可复现，与 LLM 对话两个前门同一引擎。
- **主动运维哨兵**：定时巡检 TPS/内存/在线/掉线，**边沿触发**（状态翻转才报一次，不刷屏），异常调 LLM 出诊断建议 → 控制台 + 网页告警；建议式，不自动碰破坏性操作。
- **定时任务**：广播 / 命令 / **AI 脚本**（说需求 → LLM 编译成确定性步骤 → 到点零 LLM 执行）/ AI 实时（夜间读数据决策类）。
- **物品估值**：EMC 式种子 + 全图传播，人工锚定一个值、关联自动连锁重算，抗模组增删（解析数据与人工锚定分表）。
- **玩家行为分析**：滚动画像 + 规则分群（新人/核心/流失风险）+ 命令/聊天词云；网页看板（在线趋势、7×24 热力、行为时间线）。
- **长期记忆**：跨会话、跨管理通道共享的 `admin` 域，写入受信任门槛约束。
- **Web 控制台**：零依赖（JDK 内置 HttpServer）的单页应用——运维总览、对话、行为看板、知识库编辑、定时任务、审批。

## 模块结构

| 模块 | 职责 | 基线 |
|---|---|---|
| `core` | 平台无关核心：Agent（ReAct/Plan-Execute/路由）、LLM 抽象、安全闸、记忆、知识库、能力注册、运维(哨兵/调度)、命令框架 | Java 8 |
| `bus` | 跨进程总线：接口 + Socket 中枢 / Redis / 进程内三实现 | Java 8 |
| `behavior` | 玩家行为数据平台（存储 + 聚合 + 规则画像），平台无关，只依赖 SQLite + jackson | Java 8 |
| `web` | Web 控制台后端（HttpServer + 单页），平台无关，靠注入依赖工作 | Java 8 |
| `bukkit` | 子服载体：能力提供方 / 嵌入式 Agent；本地动作、行为采集器、物品库 | Java 8 |
| `velocity` | 中心载体（Agent 大脑所在）；产出通用单 jar | Java 11 |

> core 刻意降到 Java 8，让 Java 8 的 Bukkit 子服也能直接依赖它挂嵌入式 Agent（兼容老服）。

## 技术栈

Kotlin · Gradle 多模块（shadowJar 通用 jar）· OpenAI 兼容 / Claude / Ollama 可替换 LLM · 嵌入式 SQLite（xerial）· jackson · 自建 Socket 总线 / Redis · Velocity & Bukkit/Spigot/Paper/混合端 API · 全程反射兼容跨版本/混合端。

## 构建 & 运行

```bash
# 通用 jar（同时是 Velocity 插件和 Bukkit 插件）
./gradlew :velocity:shadowJar
# 产物：velocity/build/libs/windyagent-<version>.jar
```

放进 Velocity 的 `plugins/`（中心），子服 Bukkit 的 `plugins/`（provider）。配置 `windyagent-config.yml`：

```yaml
llm:
  provider: openai            # openai 兼容 / claude / ollama
  api-base-url: "https://.../v1"
  model: "mimo-v2.5-pro"
deployment:
  mode: provider              # provider | standalone | hub
cross-server:
  transport: socket           # 无需 Redis，中枢自建 TCP
web:
  enabled: true               # 浏览器开管理控制台
```

## 现状与边界（实话）

- Phase 1–3（核心 Agent / 跨服 / 安全 / 记忆 / 行为分析 / 估值地基 / 主动运维 / 控制台）基本成形。
- **embedding 语义检索**：架构留坑，默认走关键词，按需接入。
- **变现（商品定价）**：估值引擎在，但没有真实成交数据校准——市场反馈类功能等有真实玩家流量再做，不在没数据时空转。
- 最大的待验证项是**真实在线服务器 + 真实玩家**的长期压测，而不是再堆功能。

## License

个人项目。
