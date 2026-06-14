# WindyAgent

全自主 Minecraft 服务器 AI 运营系统。基于 Claude/OpenAI/Ollama 等可替换 LLM，通过 MCP 协议调用工具，结合 RAG 知识库，实现服务器管理、商业决策、玩家营销的全自动化。

---

## 架构总览

```
WindyAgent
│
├── 外部触发层
│   ├── VelocityClient          ← 玩家游戏内触发（主要载体）
│   ├── QQClient (XingtuBot)    ← QQ 绑定入口 + 消息触发
│   ├── WebClient               ← 管理后台
│   └── CLIClient               ← 本地测试
│
├── API Gateway 层
│   ├── REST API
│   │   ├── POST /v1/agent/chat
│   │   ├── POST /v1/agent/task
│   │   └── GET  /v1/agent/status
│   ├── WebSocket               ← 实时事件推送回各平台
│   ├── AuthFilter              ← Token 认证
│   ├── RateLimiter
│   └── RequestRouter
│
├── Agent Engine 层
│   ├── AgentRouter
│   ├── ReActAgent              ← 简单任务
│   └── PlanExecuteAgent        ← 复杂多步任务
│
├── LLM Layer
│   ├── LLMProvider (interface)
│   ├── ClaudeProvider
│   ├── OpenAIProvider
│   └── OllamaProvider          ← 本地免费
│
├── Skill Engine                ← 服主自定义扩展
│   ├── SkillLoader             ← 扫描加载 MD + 脚本文件
│   ├── SkillRegistry
│   ├── ScriptExecutor
│   └── Skill
│       ├── skill.md            ← 描述：Agent 理解此 Skill 的用途
│       └── skill.script        ← 执行：Agent 调用时运行
│
├── MCP Client
│   └── 连接所有 MCP Server
│
├── RAG Layer
│   ├── Retriever
│   ├── VectorStore
│   └── Indexer
│       ├── ModDocIndexer       ← 模组文档（TODO）
│       └── SkillDocIndexer     ← Skill MD 文档
│
├── Memory Layer
│   ├── ShortTermMemory         ← 当前对话上下文
│   └── LongTermMemory          ← 历史决策/玩家行为
│
├── Scheduler
│   ├── ServerMonitorTask       ← 定时检查服务器状态
│   ├── BusinessReviewTask      ← 定时分析营收
│   └── MarketingTask           ← 定时营销触发
│
├── Cross-Server Bus            ← 复用 RedisBungee 的 Redis
│   ├── RedisPublisher          ← 向指定子服发指令
│   └── RedisSubscriber         ← 接收各服事件
│
├── MCP Servers 层
│   ├── Minecraft MCP Server
│   │   ├── KickPlayerTool
│   │   ├── BanPlayerTool
│   │   ├── BroadcastTool
│   │   ├── ExecuteCommandTool
│   │   └── GetServerStatsTool
│   │
│   ├── Economy MCP Server
│   │   ├── Economy Adapter
│   │   │   ├── VaultAdapter
│   │   │   ├── PlayerPointsAdapter
│   │   │   └── CMIAdapter
│   │   ├── GetBalanceTool
│   │   ├── SetBalanceTool
│   │   └── RevenueReportTool
│   │
│   ├── Payment MCP Server      ← 调 SakuraPurchase HTTP API
│   │   ├── CreateOrderTool
│   │   ├── QueryOrderTool
│   │   └── CloseOrderTool
│   │
│   ├── Marketing MCP Server
│   │   ├── SendQQMessageTool
│   │   ├── SendGameNoticeTool
│   │   └── CreatePromotionTool
│   │
│   ├── Analytics MCP Server
│   │   ├── PlayerActivityTool
│   │   ├── ChurnPredictTool
│   │   └── RevenueReportTool
│   │
│   └── Knowledge MCP Server
│       ├── ItemEvaluatorTool
│       ├── MembershipQueryTool
│       └── MarketHistoryTool
│
├── 数据层
│   ├── VectorDB (Qdrant)       ← RAG 向量存储
│   ├── PostgreSQL              ← 业务数据 + 支付记录
│   └── Redis                  ← 缓存 + 跨服通信（RedisBungee 共用）
│
└── Safety Layer
    ├── PermissionGuard         ← 操作权限分级
    ├── ActionAuditLog          ← 全操作日志
    └── HumanApprovalGate       ← 退款/关机/封号必过此关
```

---

## 设计原则

- **平台无关**：Velocity 只是载体，未来可迁移至子服或其他平台，核心逻辑不变
- **LLM 可替换**：不锁定 Claude，支持 OpenAI 兼容接口和 Ollama 本地模型
- **工具标准化**：所有工具通过 MCP 协议暴露，Agent 核心不依赖具体实现
- **安全边界**：退款、关机、封号等高风险操作强制走人工审批，不允许 AI 自动执行
- **Skill 扩展**：服主通过编写 MD 文档 + 脚本即可为 Agent 添加新能力，无需改动核心代码

---

## 外部依赖说明

| 组件 | 用途 | 备注 |
|------|------|------|
| RedisBungee | 跨服通信 | 复用现有 Redis，不额外部署 |
| Vault / PlayerPoints / CMI | 经济数据 | 通过 Adapter 层统一接口 |
| SakuraPurchase | 支付处理 | 保持独立 Bukkit 插件，重构后暴露 HTTP API |
| XingtuBot | QQ 绑定 + 消息触发 | OneBot 协议对接 |

---

## 开发计划

### Phase 1 — 跑通主干

目标：能收到消息 → 调 LLM → 执行游戏指令 → 返回结果

- [ ] 多模块 Gradle 项目结构（core / velocity / gateway）
- [ ] LLM Layer：LLMProvider 接口 + ClaudeProvider + OllamaProvider
- [ ] Agent Engine：ReActAgent（基础工具循环）
- [ ] MCP Client：连接本地 MCP Server
- [ ] Minecraft MCP Server：KickPlayer、Broadcast、GetServerStats
- [ ] API Gateway：REST `/v1/agent/chat` 基础端点
- [ ] Velocity 平台适配：玩家聊天触发 Agent
- [ ] CLI 平台：本地测试入口

### Phase 2 — 平台接入

目标：玩家游戏内或 QQ 触发 Agent，指令跨服下发

- [ ] QQ/XingtuBot 接入（OneBot 协议）
- [ ] QQ 账号 ↔ Minecraft UUID 绑定系统
- [ ] Cross-Server Bus：Redis Pub/Sub 跨服指令
- [ ] WebSocket 实时事件推送
- [ ] AgentRouter：ReAct vs PlanExecute 自动选择

### Phase 3 — 数据与记忆

目标：Agent 有记忆，服主可扩展 Skill

- [ ] PostgreSQL 数据库接入
- [ ] Memory Layer：ShortTermMemory + LongTermMemory
- [ ] RAG Layer：VectorDB (Qdrant) + Retriever + Indexer
- [ ] Skill Engine：SkillLoader + ScriptExecutor
- [ ] Scheduler：定时监控任务

### Phase 4 — 商业闭环

目标：自主运营、推送营销、处理支付

- [ ] Economy Adapter：Vault / PlayerPoints / CMI
- [ ] SakuraPurchase 重构 + Payment MCP Server
- [ ] Marketing MCP Server：QQ 推送 + 游戏内推送
- [ ] Analytics MCP Server：玩家行为 + 营收分析
- [ ] Knowledge MCP Server：道具评估 + 会员体系
- [ ] Safety Layer 完善：PermissionGuard + HumanApprovalGate

---

## 技术栈

- **语言**：Kotlin + Java 21
- **构建**：Gradle 多模块
- **平台**：Velocity 4.x（主）
- **LLM SDK**：anthropic-java 2.34.0
- **MCP**：Model Context Protocol
- **数据库**：PostgreSQL + Redis + Qdrant
- **支付**：微信支付 + 支付宝（通过 SakuraPurchase）
