# WindyAgent 控制台前端（Vue 3 + Vite）

新版管理控制台。构建产物是**一个自包含的单文件 html**，打进 `:web` 模块的 jar，
运行时由 Velocity 内嵌的 HttpServer 直接发出——**不另起进程、运行时不需要 node**。

## 它跟后端怎么对接

零后端改动，沿用现有约定（见 `src/api.ts`）：

- token 存 `localStorage['wa_token']`，每个请求带 `X-Token` 头；401 弹登录门。
- 流式对话：`POST /api/chat/stream`，响应是 SSE（`data: {"text":...}` / `data: [DONE]`），
  用 `fetch` + `ReadableStream` 读（不是 EventSource，因为后端是 POST）。
- 会话 id 默认 `web-console`。

## 构建（已和 gradle 串联，平时不用手跑）

`:web` 模块用 `com.github.node-gradle.node` 插件，**构建时自动下载 node**，
所以你正常编译整个项目时前端会被自动构建并打进 jar：

```
./gradlew build        # 自动：下载 node → npm install → vite build → 同步进 resources
```

产物路径：`web-ui/dist/index.html` → `web/build/generated/webui/dashboard-next.html` → jar。

运行后访问：
- `/`（及别名 `/next`）  新版 Vue 控制台 ← 本工程的产物，已是默认
- `/legacy`             旧版控制台（手写单文件，回退兜底）

确认 Vue 版稳定后，可删 `dashboard.html` + `StaticHandler.serve()` + `/legacy` 路由收尾。

## 本地开发（可选，想热更前端时）

```
cd web/web-ui
npm install
npm run dev            # http://localhost:5173 ，/api 反代到 127.0.0.1:25580
```

如果你的 `web.port` 不是 25580，改 `vite.config.ts` 里的 proxy target。
开发时需要一个**正在运行的 Velocity**（带本插件）提供 `/api`。

## 迁移进度

- [x] 登录门（TokenGate）
- [x] 聊天 + 真流式（ChatPanel）
- [x] 技能扩展（SkillsPanel：列表/查看编辑/新建/删除/重载/同步/AI起草/运行）
- [x] 审批（ApprovalsPanel：待审批 approve/deny + 历史，3s 轮询）
- [x] 用量（UsagePanel：汇总卡片 + 按天表格）
- [x] 知识库（KnowledgePanel：增删改 + AI 起草）
- [x] 设置向导（SetupWizard：首启全屏强制 + 平时「设置」面板重配，/api/setup）
- [x] 服务器 / Ops（OpsPanel：健康卡片 + 子服详情/世界/玩家 + mods/维度TPS + 告警，5s 轮询）
- [x] 定时任务（TasksPanel：列表 + 间隔/每天定时编辑 + 启停/运行/删除 + AI 润色）
- ~~玩家行为看板 / 词云~~ —— **后端无对应 handler**（/api/board /api/words /api/segments /api/player 均未注册），属未实现端点，不迁。

**所有有后端的面板均已迁完，且 `/` 已切到 Vue 版**（旧版降 `/legacy` 兜底）。剩余收尾（可选）：确认稳定后删 `dashboard.html` + `/legacy`。

## 注意

- LLM 输出用 `marked` 渲染 markdown，**未引入 DOMPurify**——这是服主看自家 AI 的内网管理台，
  风险可接受。若将来开放给不可信用户，请加 sanitize。
