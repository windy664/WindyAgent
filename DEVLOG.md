# WindyAgent 开发日志

## 2026-06-15 Groovy 技能（skill）：服主免重编译给 Agent 加新能力

**动机**：Agent 跑在 Java 服务端却没法执行代码去调装过来插件的 API。讨论后明确：要的不是「LLM 现场写代码」（每次现编、不可审、违背确定化哲学），而是**让服主写好脚本、Agent 按名调用**——审一次冻结，等于一条确定性能力，且免重编译即可扩展。

**做法**：服主在子服插件数据目录 `skills/*.groovy` 放脚本，头部注释声明 `name/description/arg`，正文 Groovy。
- `bukkit/skill/`：`SkillRegistry`（扫目录解析头部）、`SkillEngine`（`GroovyShell` + binding 注入 `server/plugins/actions/args/log`，经 `BukkitActions.onMainGuarded` 在主线程跑、带超时看门狗）、`SkillTool`（包成本地 `AgentTool`）、`SkillDef/SkillArgs`。
- 嵌入式（standalone/hub）：技能 = 本地工具，LLM 直接可见。
- provider：无嵌入式 Agent，技能经新增 `run_skill` 动作执行，并随能力目录推回中心（`SkillDef.toCapabilityCommand()`，source=「WindyAgent 技能」）。
- 中心 / hub：`core/agent/RemoteSkillTool`（`run_skill_on_server`）跨服调；先 `search_capabilities` 查、再调。

**安全**：信任边界 = 文件系统（能放文件的即服主）→ 技能不过 CommandGuard、无人值守也可调，但每次记 audit。看门狗只解除 Agent 等待、不强杀主线程脚本（强 interrupt 主线程有害）；`ThreadInterrupt` AST 给协作式取消。真正兜底是「脚本人工审过」。

**依赖/构建**：`bukkit` 加 `org.codehaus.groovy:groovy:3.0.22`（Java 8 兼容），随 fat jar 打包，**不 relocate**（Bukkit 插件类加载器天然隔离，区别于 Velocity 宿主共享的 jackson）。config 加 `skills.{enabled,dir,timeout-sec}`，示例脚本 `deploy/bukkit/skills/welcome_vip.groovy`。

**参数预校验**：对照「Agent-Skill 标准 6 步」第②步补硬校验——`SkillDef.validate()` 在跳主线程前拦缺参/类型不符，回报 LLM 补参（不进 GroovyShell）；统一收口在 `SkillEngine.run` 入口，本地/跨服两条调用自动覆盖。

**WebUI 技能面板**（侧栏「🧪 技能扩展」）：因技能在子服、控制台在中心，全部经总线远程操作选中子服的 `skills/` 目录——
- provider 加 `skill_list/get/save/delete/reload` 五个动作（`SkillRegistry` 加文件读写 + `safeFile` 防目录穿越）；
- 中心 `DashboardServer` 加 `/api/skills*`（GET 列表 / POST 存 / DELETE 删 / content 读 / reload 重载 / run 测试运行）；
- 前端复用知识库的双栏布局：左列表、右编辑器（文件名 + Groovy 正文 + 保存热重载 + 删除 + 「测试运行」面板填 JSON 参数直接在子服跑）。
- 信任沿用：能进 token 闸的 WebUI 管理员 = 服主，故允许经总线给任意在线子服写技能文件。

**改对齐 Anthropic Agent Skills（SKILL.md 三态）**：原实现把 skill 等同「可执行 Groovy」，漏了「纯文字技能」一整类。重做 loader 支持三态：
- **纯文字**（`skills/x.md` 或文件夹 `SKILL.md` 无 script）：正文是操作流程，被调用时工具**只把正文返回进上下文**（记 audit=TEXT），Agent 据此用现有工具办事，不写代码。对应「渐进式披露」。
- **脚本+文字**（文件夹 `SKILL.md` 含 `script: x.groovy`）：md 正文进工具描述（何时/怎么用），调用即跑脚本。
- **纯脚本**（扁平 `x.groovy`，旧的 `// name` 头）：保留兼容。
- `SkillDef` 加 `body/script` 双槽 + `isScript`；loader 解析 YAML frontmatter（name/description/script/args）。`SkillTool.execute`/provider `run_skill` 按 `isScript` 分支：文字回正文、脚本跑引擎。
- WebUI 改 handle 制（文件夹名）+ 「📄纯文字 / ⚙️脚本+文字」切换，md 与 script 两个编辑框；save 统一落文件夹格式（`<handle>/SKILL.md` + `script.groovy`），扁平技能编辑保存即迁移成文件夹。`skill_get/save/delete` 总线动作参数由 file/content 改 handle/md/script/isScript。
- 默认示例 `deploy/bukkit/skills/`：`welcome_vip/`(脚本+文字)、`handle_refund/SKILL.md`(纯文字)、`online_report.groovy`(纯脚本)。
- **AI 起草纯文字技能**：复用知识库 draft 套路，新增 `draftSkill` lambda(Velocity)+`/api/skills/draft`，服主说一句需求 → LLM 生成流程型 SKILL.md，填回编辑框并切到「纯文字」类型；人确认后再保存。

**TODO**：等用户编译验证；实测 Groovy 在 Youer/Mohist 混合端的类加载与对 Vault 等插件的跨插件 API 可见性；WebUI 写文件目前不限制内容（管理员自负），如需可加大小/语法预检。

## 2026-06-14 定时任务加「AI 脚本」：需求 → LLM 编译成步骤 → 确定性执行

用户指出 AI 定时任务不该是纯文本到点让 Agent 现场瞎跑（不可控、每次烧 token）。改成**创建时把需求编译成具体步骤脚本**，到点确定性执行。
- **模型**：`ScheduledTask` 加 `action="script"` + `script: List<TaskStep>`（TaskStep={action(broadcast/command),target,payload}）。
- **编译**：`/api/tasks/compile`（DashboardServer + velocity `compileScript` lambda）——LLM 受限 codegen：只准输出 broadcast/command 两种动作的 JSON 数组；velocity 侧截取 `[..]` + jackson 校验，脏输出退 `[]`。
- **执行**：调度器 `script` 分支逐步跑 `runStep`（确定性 broadcast/run_command，不调 LLM/Agent）。`agent` 实时类型保留给夜间整理这种要读数据决策的。
- **前端**：动作加「📜 AI 脚本」；选它→需求描述 + 「✨生成脚本」→ 步骤预览（编号/动作/目标/payload）；保存带 script。`tfActionChange` 按动作切换 payload 标签 + AI 按钮（脚本=生成脚本，实时=AI整理，广播/命令=无）。
- 价值：脚本可见、可确认、可复用，到点零 LLM、确定性、可预测——契合"先确定化再请模型"的取向。
- 改动：core(TaskScheduler 模型)、web(DashboardServer)、velocity(compile+调度器 runStep/script)、dashboard.html。需编译（/api/tasks/compile 新后端）。


## 2026-06-14 运维总览：子服健康卡片 → 服务器列表 + 点开详情

把首页"子服健康"改成**服务器列表**(一行一服:状态点/名/平台/TPS·在线·内存),**点行弹详情**。
- **bukkit `server_detail` 动作**：`BukkitActions.serverDetail()` 主线程采——概况(uptime/在线·最大/内存/插件数/白名单/正版/视距) + **每个世界**(维度/游戏时间`tickToHM`/天气/实体数/加载区块/人数/难度) + **在线玩家**(名/世界/延迟 getPing 反射/模式)。tps/平台/内存由 handler 预算传入(`currentTps()` 抽出复用，含 NMS 回退)。全 Bukkit 稳定 API + runCatching 降级。
- **web** `/api/serverdetail` → proxy。**前端**：健康网格→`.slist` 行(点击 openServerDetail)；详情走 infoModal(新增 `showDetail` 渲染 HTML，`showInfo` 维持纯文本)，含概况 dgrid + 世界/玩家 dtbl + (forge系)模组/分维度TPS 按钮(从原卡片移入)。
- 数据集按用户确认：概况 + 世界 + 玩家(砍了出生点/MOTD/磁盘占用/游戏规则明细)。
- 改动：bukkit(BukkitActions/handler)、web(DashboardServer)、dashboard.html。需编译(/api/serverdetail 是新后端)。



## 2026-06-14 玩家聊天触发的 AI 只走知识问答，不进 Agent

安全边界：玩家(不可信)不该能借 AI 踢人/查他人数据/跑命令。改为玩家走**纯知识库问答**。
- **core `PlayerQa`**（新）：检索知识库(+命中弱时扩词) → LLM 据此作答，**完全不挂工具、不进 ReAct/Plan 循环**。系统提示限定"只依据知识库、查无则让问管理员、不能执行任何操作"。单次调用，省 token。
- **入口分流**：游戏内 `!ai`（`VelocityChatListener`，永远不可信）→ **永远 PlayerQa**（去掉 agent/sessions 依赖）；`/ai` 命令（`AgentCommand`）→ 按权限：管理员/控制台(TRUSTED)→ 完整 Agent；普通玩家(UNTRUSTED)→ PlayerQa。
- 元命令(help/clear/history)仍可用；用便宜模型(fastLlm)答玩家问。
- 改动：core(PlayerQa)、velocity(VelocityChatListener/AgentCommand/装配)。需编译。
- **子服端对齐（同批补上）**：standalone/hub 的嵌入式 Agent 同样处理——`BukkitChatListener` 玩家永远走 PlayerQa；`BukkitCommand` 按权限分流（管理员→Agent，玩家→PlayerQa）。
  - 顺带补一处**早先的不一致**：`BukkitAgentRunner` 之前**没接知识库**（只有能力检索）。现补 `KnowledgeManager` + `KnowledgeSearchTool` + `KnowledgeWriteTool` + `PlayerQa`，让单机 Agent 也能查/写知识、玩家也能知识问答，与 Velocity 对等。
  - 三模式边界厘清：provider(无 agent，玩家经 Velocity 入→VelocityChatListener 的 PlayerQa) / standalone / hub 均覆盖。

## 2026-06-14 WebUI 审批栏目（待审 + 历史，首页面板 + 顶栏角标）

高危操作的审批闸之前只能游戏内/控制台 `/ai-approve` 批，给 WebUI 补上入口。
- **core `PendingApprovals`**：加结构化 `items()` + **审批历史** `historyItems()`（approve/deny/过期 都在唯一收口 record，故游戏内/控制台/网页批的都进同一份历史）；`Item`/`Decision` 数据类 + `ttl()`。
- **web `DashboardServer`**：注入 `pending`；`/api/approvals`（待审 + 历史 + 剩余时效一次拉）、`/api/approvals/approve`、`/deny`。
- **前端**：按用户选的——运维总览**首页顶部**待审面板（红框，单号 + 完整命令描述 + 剩余分钟 + ✅批准/❌驳回）+ **顶栏 🛡️待审角标**（始终更新、点击跳首页）；下方「🗂️ 审批历史」可折叠（✅/❌/⌛ 记录）。30s 轮询。
- 改动：core(PendingApprovals)、web(DashboardServer)、velocity(传 pending)、dashboard.html。需编译。

## 2026-06-14 检索现状梳理 + embedding 长期主义留坑（不动代码）

复盘当前 RAG 检索，决定 embedding 先留坑、暂不接（长期主义：架构缝留着、推迟到真需要更强语义）。
- **当前 = 关键词稀疏 + LLM 扩词兜底**：`KeywordKnowledgeStore` 切词无分词库（拉丁 `[a-z0-9]+`≥2 + 中文相邻二元组），打分=字段加权词频（标题×3/标签×2/正文×1）→ 滤0分排序 topK。三处共用：知识库 `KnowledgeSearchTool`、长期记忆 `recall`(+`recallMinScore`阈值+作用域)、能力检索 `CapabilityRegistry`(+`CommandSynonyms`中英同义词表)。命中弱 → `LlmQueryExpander` 用便宜模型扩词再搜（只在需要时烧 token）。
- **向量+余弦是已建好的坑**：`VectorIndex.cosine` + `OpenAICompatEmbeddingProvider` + `buildEmbeddingProvider` + 配置 `embedding.enabled` 全在；卡点=mimo 无 `/embeddings` 出不了向量 → L3 永远落回关键词。能力检索接了该判断；**知识库还没接 VectorIndex**。
- **填坑路径（备查）**：配 embedding 即可，免本地——首选硅基流动 `BAAI/bge-m3`（免费/OpenAI兼容/国内直连，base `https://api.siliconflow.cn/v1`），或本地 Ollama bge-m3。开后能力检索即语义；知识库需再补一行接 VectorIndex（增量嵌入+后台线程）。
- 本条仅梳理+记忆（`project-rag-embedding-parked`），不改代码。

## 2026-06-14 定时任务升级为 Agent 驱动 + 夜间自动整理知识库

把定时任务从"死命令"升级成 **Agent 驱动**，并落地"夜间自动把数据沉淀成知识"。
- **AI 任务类型**：`ScheduledTask.action` 增 `agent`——payload=自然语言指令，到点交 **Agent 自己执行**（拆解+调工具）。WebUI 任务表单加「🤖 AI 任务」选项 + 「✨ AI整理」按钮（把一句话润成清晰任务描述，`/api/tasks/refine`）。
- **无人值守安全**：`AgentContext.unattended` + `RequestContext.unattended()`；定时 agent 任务以 TRUSTED + unattended 执行；`RemoteCommandTool` 在 unattended 下把高危命令**直接拦截只记录**（不挂审批单空等）。
- **给 Agent 补两只手**：`KnowledgeWriteTool`（写/更新知识库，仅 TRUSTED）+ `OpsInsightTool`（velocity，`ops_digest`：汇各子服统计/分群/聊天词/命令词 + 近期告警成摘要）。原始数据仍留 behavior 库，工具只出摘要。
- **内置夜间任务**：每天 **00:00**，`builtin-nightly-curation`（首次启动且无任务时自动种入）——指令让 Agent：ops_digest 取数 → 聊天/命令热词提炼 FAQ 写知识库 → 告警归纳进滚动「运营日志」条目(id=ops-log) → 玩家结构变化 remember 进管理方记忆。维度=①FAQ②运营摘要进KB、③画像进记忆。
- 调度器移到 agent 之后构建（agent 任务要用它）；exec 按 action 分流 agent/broadcast/command。
- 改动：core(AgentContext/RequestContext/AgentRouter/RemoteCommandTool/KnowledgeWriteTool)、velocity(OpsInsightTool + 装配 + 内置任务 + refine)、web(DashboardServer +/api/tasks/refine)、dashboard.html(AI任务选项+整理按钮)。需编译。

## 2026-06-14 定时任务功能（调度器 + CRUD API + WebUI 管理页）

补上"活动管理"占位页 → 改成可用的「⏰ 定时任务」。
- **core/ops `TaskScheduler`**（新，平台无关）：任务存盘 `tasks.json`，单 daemon 线程每 30s 巡检到点任务，经注入的 `exec` 执行。`ScheduledTask` 模型：action(broadcast/command) + target(子服名 / *=全部已连) + payload + 调度(interval 每 N 分钟 / daily 每天 HH:MM，可限定周几)。CRUD 即时落盘 + 重算 nextRun；支持 runNow 手动触发、toggle 启停。
- **velocity 装配**：调度器 exec 接总线——到点把 broadcast/run_command 下发到目标子服（* 遍历在线集），回执汇总写 lastResult。
- **web `DashboardServer`**：`/api/tasks`（GET 列表 / POST 增改 / DELETE 删）+ `/api/tasks/run`、`/api/tasks/toggle`；手工解析 JSON（web 无 jackson-kotlin）。
- **前端**：导航「活动管理🎉」→「定时任务⏰」；任务列表（状态/动作/调度/下次触发/上次结果 + 立即/启停/编辑/删）+ 动态表单（动作·目标·内容·触发方式·周几）。
- 需 cross-server 启用（任务下发走总线）。改动：core(TaskScheduler)、web(DashboardServer)、velocity(装配)、dashboard.html。需编译。

## 2026-06-14 Youer 实测修正：TPS 走 NMS、维度 TPS 改捕日志

第二步在 Youer 26.1.2 实测：`server_mods` 反射通了（6 模组）；但发现两点要改：
- **Youer 无 Bukkit `getTPS()`** → health_query 的 tps=-1（健康卡片空、哨兵 LAG 判定也失效）。修：`getTPS()` 失败时退 **NMS 反射**——`MinecraftServer.getAverageTickTimeNanos()`/`getAverageTickTime()`，回退字段 `tickTimesNanos`/`tickTimes`，推算整体 TPS（Youer 官方映射，方法名直接可用）。
- **`/neoforge tps` 输出走 log4j 服务器日志、不回命令 sender** → 原 `CommandCapture`（抓 sender.sendMessage）抓不到。改 **`LogCapture`**：反射给 log4j 根 LoggerConfig 临时挂动态代理 Appender，异步派发命令 + 600ms 捕获窗口 + 按 "TPS" 过滤行，完事摘除。全反射、不编译期依赖 log4j-core，失败兜底空。
- 实测确认输出格式：`Overworld/The Nether/The End/Overall: 20.000 TPS (x ms/tick)`。
- 改动：bukkit(LogCapture 新 + BukkitActions.dispatchAsync + NeoForgeOps.dimensionTps 改 + handler nmsTps)。`CommandCapture`/`runCapture` 暂留（抓 sender 输出的命令仍可用）。需编译。

## 2026-06-14 NeoForge 专属能力（差异化·第二步，需 Youer 实测）

基于第一步的类型探测，给 forge/neoforge 子服解锁专属能力（按 platform 门控）。
- **命令输出捕获** `CommandCapture`（新）：动态代理伪造 CommandSender 抓 sendMessage 文本（Bukkit 不回传命令输出，只能旁路捕获）；权限方法全返回 true 让 op 命令得跑，spigot() 等返回同一收集器代理覆盖组件路径。`BukkitActions.runCapture(cmd)` 主线程跑命令 + 收集。
- **`NeoForgeOps`**（新）：① 模组清单——反射 `ModList.get().getMods()` 取 id/名/版本；② 分维度 TPS——捕获 `/neoforge tps`（NeoForge）或 `/forge tps`（旧 Forge）输出，捕不到给说明兜底。非 forge/neoforge 直接拒。
- **总线动作**：`server_mods` / `dimension_tps`（handler，按类型门控）。**web**：`/api/mods`、`/api/dimtps`（proxyText 包成 {text}）。**前端**：运维总览健康卡片**仅对 forge/neoforge 子服**显示「🧩 模组 / ⏱️ 维度TPS」按钮 → 结果弹窗。
- **不确定点（待 Youer 实测）**：命令捕获能否拿到输出，取决于 Youer 是否把 NeoForge 命令桥接进 Bukkit dispatch 并把输出路由回 sender；拿不到则按钮显示"未捕获"说明，整体 TPS 不受影响。
- 改动：bukkit(CommandCapture + NeoForgeOps + BukkitActions.runCapture + handler 2 动作)、web(DashboardServer 2 路由 + proxyText)、dashboard.html(门控按钮 + 弹窗)。需编译。

## 2026-06-14 子服核心类型探测（差异化能力地基·第一步）

按"不同子服核心类型解锁不同能力"的方向，先做检测 + 上送 + 展示这层（稳、纯反射，不依赖命令捕获）。
- **bukkit `ServerProfile`**（新）：纯反射/品牌判别探测子服形态——platform（neoforge-hybrid / forge-hybrid / paper / spigot / craftbukkit，靠探 `net.neoforged.fml.*` / `net.minecraftforge.*` / `io.papermc.*` 类）、mcVersion、brand、modCount（forge/neoforge 反射 `ModList.get().getMods().size`）、hasTps。探一次缓存，探不到降级。
- **上送**：搭 `health_query` 回报带上 platform/mcVersion/brand/modCount → `HealthSnapshot` 加这些字段 → `snapshotsJson` 输出 → `/api/health`。
- **展示**：运维总览健康卡片加类型徽章（🧩 NeoForge 混合端 · MC1.21 · N 模组）。
- 这层为「按类型门控工具」铺好数据地基。第二步（NeoForge 专属：`/neoforge tps` 分维度、ModList 模组清单工具，需自定义 CommandSender 捕获命令输出）待 Youer 实测再做。
- 改动：bukkit(ServerProfile + health_query)、core(HealthSnapshot + snapshotsJson)、velocity(probe 解析)、dashboard.html(徽章)。需编译。

## 2026-06-14 控制台信息架构重排：首页=运维总览，对话独立成页

定位是"主动运维"，故把控制台落地页从「聊天」换成「运维总览」，对话降为侧栏一项。
- **新首页「🛰️ 运维总览」**（默认页）：① KPI 行(在线子服/在线玩家/活跃告警/哨兵状态) ② 子服健康卡片网格(每服状态点 + TPS/在线/内存%，实时) ③ 运维告警/处置过程时间线(哨兵告警 + LLM 建议)。30s 轮询，可见时刷新。
- **对话**移到第二项「💬 AI 对话」，不再是落地页。
- 后端补 `/api/health`：`HealthMonitor.snapshotsJson()` 出当前各子服健康+状态(offline>lag>mem>ok)；`DashboardServer` 加 `health` 供给函数，velocity 传 `{ sentinel?.snapshotsJson() ?: "[]" }`。哨兵关或无数据时前端降级为「连接的子服显示正常」。
- 改动：core(HealthMonitor +snapshotsJson)、web(DashboardServer +/api/health)、velocity(传 health)、dashboard.html(新页+导航+健康卡片 CSS/JS)。需编译（健康/告警数据来自新 jar）。

## 2026-06-14 主动运维哨兵 MVP（监控→评估→建议→通知 闭环）

把 Agent 从"纯被动响应"推进到"主动运维"的第一个闭环。处置策略=**建议式走人工审批**（哨兵只报警+给建议，不自动执行命令，高危仍走 ai-approve）。
- **采（子服）**：`BukkitCapabilityHandler` 加 `health_query` 动作 → 回 `{tps,online,memUsedMb,memMaxMb}`。TPS 用反射取（spigot-api 1.12 编译期无 getTPS，Paper/Youer 运行时有，远古服 -1 跳过）。
- **评（中心，core/ops）**：新 `HealthMonitor`（平台无关，单 daemon 线程）定时巡检在线子服，**边沿触发**（状态翻转才报一次，不刷屏）：掉线/无响应、TPS<阈值、内存>阈值%、在线骤降；恢复报 RECOVERED。`HealthSnapshot`/`Incident`/`IncidentKind` 模型。
- **触发/建议**：异常调 LLM（便宜模型）出"一句话诊断 + 具体处置建议"（`sentinelAdvise`），不自动执行。
- **通知（core/ops + web）**：`Notifier` 抽象 + `CompositeNotifier`；`LogNotifier`（控制台）+ `AlertCenter`（web，内存环形缓冲，实现 Notifier）→ `/api/alerts`。前端顶栏 🔔 角标计数 + 下拉告警列表（含建议），30s 轮询。
- **装配（velocity）**：探测=总线 dispatch `health_query` + 解析（阻塞 get 带超时）；在线集复用 onlineServers()；`AlertCenter` 早建、哨兵与看板共用；shutdown 停哨兵。
- 配置：新 `sentinel:` 段（enabled/interval-sec/tps-min/mem-pct/player-drop/advise）。deploy/velocity 模板 + 活 VC 配置（已开）。
- 改动：core(ops 4 文件 + AgentConfig)、bukkit(health_query)、web(AlertCenter + DashboardServer)、velocity(装配)、dashboard.html(🔔)、配置。需编译。
- 后续（未做）：一键把建议落成 pending-approval（现在是建议→人工手动执行）；QQ/Webhook 通知通道；错误日志/崩溃栈采集；服务器健康落库做趋势。


## 2026-06-14 长期记忆：管理方统一域 + 写入信任门槛

针对"非玩家(管理方)输入有没有自动记忆、学的是不是有效记忆"的诊断与加固。现状：召回全自动(每请求注入 top-K，带 recallMinScore=2 过滤弱命中)、写入是 Agent 自主调 `remember`(有去重 + 上限淘汰)。两个缺口补上：
- **管理方统一记忆域 `admin`**：新增 `LongTermMemory.ADMIN`。所有 TRUSTED 通道(网页控制台 / VC 控制台 / 有 windyagent.admin 的 /ai)写入默认落到 `admin` 域、召回也带上它 → **管理方不管从哪进，学到的互通**（之前各通道按 sessionId 隔离、不互通）。
  - `recall`/`list` 加 `includeAdmin` 形参，AgentRouter 按 `trust==TRUSTED` 传入；MemoryCommand 同步(可信用户 list 含 admin，标 `[管理]`)。
- **写入信任门槛**：`RememberTool` 加 `RequestContext.current()!=TRUSTED → 拒写`。普通玩家 `!ai`(UNTRUSTED)不能再往长期记忆写东西，**保证"有效记忆"只来自管理方**，挡污染。默认 scope 从 sessionId 改为 `admin`；显式 `global`=全服、或填玩家名定向。
- 改动：core(LongTermMemory 接口 + FileLongTermMemory + RememberTool + AgentRouter + BuiltinCommands)。需编译。旧记忆仍按原 scope 保留，不迁移。

## 2026-06-14 控制台命令提示面板 + 路由容忍斜杠

WebUI 聊天加命令提示（用户预期斜杠触发，实际原是裸词）：
- 后端 `AgentCommandRouter`：token 解析加 `.removePrefix("/")`，`/clear` == `clear`（兼顾 Web/IM 习惯，裸词仍可）。
- 前端 `dashboard.html`：输入 `/` 弹命令面板（help/clear/history/status/value/memory + 管理员 pending/approve/deny），↑↓选 / Enter·Tab 补全 / Esc 关；命令名+描述+管理员标记。命令清单前端硬编码（小而稳，避免穿透 router→DashboardServer 取列表的管线）。
- 修坑：选子服时 send() 会加"（默认子服…）"前缀顶掉命令词 → 斜杠命令一律直发路由、不加前缀。
- 注：面板 UI 前端即时可见；斜杠真正触发 + 前缀豁免需编译。旧 jar 上临时用「裸词 + 总控」触发命令。

## 2026-06-14 控制台打磨：微交互 + 可读性 + 聊天记录持久化

一轮 WebUI 体验打磨（多为 dashboard.html，最后一项含 DashboardServer.kt）：
- **微交互**：点击涟漪（委托到可点控件、落点扩散）、按压回弹、卡片悬浮抬升、切页淡入 + 面板/KPI 错峰入场、导航选中高亮条；尊重 `prefers-reduced-motion`。
- **可读性**：AI 回复气泡原用 `--glass2`(白6%)几乎全透、被背景动漫图干扰，改深色实底 `rgba(20,15,40,.72)` + `backdrop-filter:blur(14px)`。
- **去冗余**：删聊天页右栏底部版本署名卡片。
- **聊天记录持久化（两层，graceful degradation）**：
  - 前端 localStorage 按会话存（`wa_chat_<session>`）+ 记住选中子服（`wa_target`），刷新/切子服回灌；
  - 后端 `DashboardServer` 把对话存盘 `chatlog/<session>.jsonl`，加 `/api/chat/history` 读回——**跨刷新跨设备、重启不丢**；前端 `renderHist` 优先拉后端、404/离线退回 localStorage。
  - 前端发消息额外带 `display`(去掉"默认子服"前缀的原文)，存档干净；`clear` 指令清后端会话 + 删存档。
  - 与 AI 上下文独立：SessionManager 仍限 max-history 轮（省 token），存档可更长（读回最近 200 条）。
- 注：本批后端改动（缓存头 + 聊天存档接口）需编译进 jar 才生效；纯 HTML 改动用 dataDir 预览副本即时可见，视觉定稿后编译并删预览副本。

## 2026-06-14 控制台缓存与“副本优先”踩坑 + 禁缓存头

加词云后用户看不到，排查发现两层障碍：① `DashboardServer.respond()` 没发缓存头，浏览器拿旧页；
② `page()` 优先读 `dataDir/dashboard.html` 覆盖副本，而数据目录里那份是早上热改留下的旧版（无词云），
把 jar 内置的新版盖住了——换 jar/重启/清缓存都没用。处理：
- 给所有响应加 `Cache-Control: no-cache, no-store, must-revalidate`（API 实时数据+HTML 热改即时生效）。
- 删掉用户数据目录的 `dashboard.html` 覆盖副本，约定**前端跟 jar 走**，不再留热改副本（避免旧副本盖新版）。
- 改动：DashboardServer.kt（缓存头）。

## 2026-06-14 词云画进看板（wordcloud2.js，命令/聊天双源）

之前词频数据(`word_freq` 表 + `/api/words?source=cmd|chat`)早备好，只差前端画图。本轮补上：
- 引 `wordcloud@1.2.2/src/wordcloud2.js`（同 Chart.js 走 jsdelivr CDN）。
- 看板"热门命令/Top5"行与"行为时间线"之间加一块 **☁️ 词云**面板，带 **命令/聊天** 两源切换按钮（`switchWords`）。
- `loadWords`→`/api/words?source=&limit=120`，`renderWordCloud` 按词频降序、`weightFactor=12+√(n/max)*52` 缩放字号，二次元配色随机、canvas 宽随容器自适应。空数据显示提示（聊天源提示需 track-chat）。
- 命令词云一直有数据（始终采集）；聊天词云等子服 track-chat 真采到才有（Youer 升 26.1.2.76 后即可）。
- 保留原"热门命令"横条图（精确排名）与词云（可一眼看全貌 + 含聊天）互补。改动：dashboard.html。

## 2026-06-14 修「假在线」：总线给出真实在线集，选/派子服不再白等离线

老问题：`connectedServers` 指向能力注册表 `registry.servers()`，而注册表是**持久化目录**（曾见过=含已离线子服）。
于是 WebUI 子服选择器/`/api/servers`、value 远端校验都会列出/选中离线子服 → 派过去白等超时。
- `MessageBus` 加 `onlineServers(): Set<String>?`——**权威在线来源**。`null`=该传输报不了（无心跳 Redis）→ 调用方退回注册表"曾见过"集（旧行为不退化）；非空=可判定。
- `SocketHubBus`：返回 `connections.keys`（REGISTER 入、断开移除）——真实活动连接。
- `InProcessBus`：返回已注册 handler 集。
- velocity 装配：`val online = { b.onlineServers() ?: registry.servers() }`，`connectedServers` 与 `RemoteValueExecutor` 都改用它。
- 边界保持：能力**检索**仍走注册表（曾见过的目录、含离线，重启免重推不变）；只有"在线/可派发"判定改用实时集。
- 改动：bus(MessageBus 接口 + SocketHubBus/InProcessBus)、velocity(装配两处)。Redis 模式无心跳，暂退回旧行为（后续可加 presence 心跳）。

## 2026-06-14 Youer 修复聊天，Bukkit 侧切回 AsyncPlayerChatEvent 直采

Youer 26.1.2.76（commit dc8737b：聊天链 `FutureChain` 改走 `server.chatExecutor`）修好了之前那个
"AsyncPlayerChatEvent 在主线程触发刷异常"的老 bug——该事件现已正常在异步聊天线程触发。于是 Bukkit 侧聊天采集
从之前的兜底（废弃同步 `PlayerChatEvent`，Youer 根本不触发）**切回标准 `AsyncPlayerChatEvent`**：
- `BehaviorTracker.chatListener` 改监听 `AsyncPlayerChatEvent`（异步线程上对 ConcurrentHashMap/AtomicLong 自增本就安全，契合防卡设计）；顺手补上**每人聊天计数** `chats`（schema 早有列、之前一直没人写）。
- 配置：本机 Youer 活配置 `behavior.track-chat: true` 开启本服直采；deploy 模板注释更新（Youer≥26.1.2.76 / Paper 系可 true，旧混合端仍 false）。
- 两条采集路互补：本服 `AsyncPlayerChatEvent` + Velocity 代理层 `ChatWordCollector`（玩家经代理时），都过总线入 `word_freq`。
- 记忆 `reference-youer-asyncchat-bug` 标记为「已修复」。

## 2026-06-14 长期主义重构：行为子系统独立成 :behavior 模块

把玩家行为/画像从 bukkit 里提出来，单独成**平台无关模块** `:behavior`。动因：画像是一套**确定性数据平台**，
Agent 只是它的消费方之一（谈不上"属于 Agent"）；独立模块给将来"大数据量换存储"留了干净的边界（只动本模块，外部 API 不变）。
- **新 `:behavior`**（不依赖 core/bus/任何载体，只 jackson + sqlite-jdbc）：
  - `BehaviorDatabase`（整体搬来，改包名 `org.windy.windyagent.behavior`，含 ProfileDelta/SessionRow/EventRow/FeedRow/Stats/Profile）；
  - `BehaviorAnalytics`（**新**，从旧 BehaviorService 抽出的分析层：stats/board/segments/player/words JSON + recordChatWords）。纯规则、不碰 LLM。
- **bukkit 只留采集器 + 装配**：`BehaviorTracker`（吃 Bukkit 事件，必须在子服）改导入指向 `:behavior`；
  `BehaviorService` 瘦成**装配门面**（构建 db+tracker+analytics、生命周期、转发，无分析逻辑）。
- 分层：采集器→写 db；analytics→读 db 出汇总；两者经 db 解耦。对外零影响（门面没换包，Handler/Plugin 不改）。
- 纯搬家、无功能变更。改动：settings.gradle、新 behavior 模块、bukkit(build.gradle + Tracker/Service 改、删旧 DB)。
  velocity 通用 jar 经 `project(':bukkit')` 传递把 `:behavior` 一并 shade，jackson 照常重定位。

## 2026-06-14 产品级控制台 + 看板真数据（趋势/热力/时间线）

按用户设计稿把控制台做成产品级 SPA：顶部通栏 + 左侧栏(Logo+8导航+服务器面板) + 中间主区 + 右信息栏(仅聊天页)。
两张页面照搭：聊天页(开场白+能力分类+快捷问句+多轮+右栏4模块)、行为看板(KPI行+多图表网格+画像查询)。建设中页占位。
- **占位补真**(从现有数据算,无需新高频采集)：
  - **时段热力图** 7×24：来自 sessions 登入时刻(`heatmap()`)。
  - **行为时间线** feed：合并 events(死亡/成就)+sessions(登入/登出) 倒序(`feed()`)；聊天页"最近日志"同源。
  - **在线趋势**：新增轻量 `online_snap` 表，flush 时记 `onlineSince.size()`(不调 Bukkit API、不卡)，`trendDaily` 出近7天每日峰值。
- 真数据面板：KPI、行为分布环图(建造/探索/战斗/合成)、活跃度分层(segments)、热门命令(words)、时长Top5、趋势、热力、时间线、画像(含解读层)。
  仍占位：留存/收入/区域(缺时序/经济/位置数据,不编假数)。
- 接口：`behavior_board` → web `/api/board`(趋势+热力+feed 一次拉)。前端优雅降级(后端没部署则面板空、不报错)。
- 改动：bukkit(BehaviorDatabase/Tracker/Service/Handler)、web(/api/board)、dashboard.html(整体重写,产品级布局)。

## 2026-06-14 词频基建 + 命令词云（聊天词云待 Paper 服）

用户问"聊天统计/词云"。实话：聊天采集之前因 Youer 的 AsyncPlayerChatEvent 主线程 bug 撤了，拿不到聊天内容。
方案=**通用词频基建，多源喂**：
- **命令词云（始终采，Youer 可用）**：`onCommand` 取命令名(去参数)累加。
- **聊天词云（可选，仅 Paper 系）**：`behavior.track-chat`(默认 false) 才注册一个**独立** `AsyncPlayerChatEvent` Listener —
  Youer 关着就完全不沾那个 bug；Paper 子服开了就采。编译安全(用 spigot-api 的 String 版事件，不碰 Adventure)。
- **粗分词**：拉丁/数字整词 + 中文双字组(无分词库的廉价办法) + 停用词过滤。
- 存 `word_freq(source,word,count)` 表(upsert 累加)；`topWords` 出 Top-N。
- 接口：`BehaviorService.wordsJson` → 总线 `behavior_words` → web `/api/words?source=cmd&limit=`。
- 改动：core(config)、bukkit(BehaviorDatabase/Tracker/Service/Handler)、web(DashboardServer 加 /api/words)、配置三份。
- **UI 词云(wordcloud2.js)待做**：数据已备好，画图下一轮接。

## 2026-06-13 控制台升级：左栏右内容 SPA + 多轮聊天 + 知识库编辑 + 二次元玻璃

按用户愿景把控制台从单看板做成完整 SPA：
- **布局**：左侧栏导航（聊天/行为看板/知识库）+ 右侧内容；随机二次元背景图(`t.alcy.cc/moe`,可换) + 暗色毛玻璃(压住任意背景图保可读)。
- **首页大聊天(多轮)**：`POST /api/chat{message,session}` → velocity 把它接到 `router.dispatch` 优先(命令)否则 `agent.run`，多轮历史由 `SessionManager` 维护(session=web-console)，TRUSTED。等于把游戏内 `!ai` 搬进网页(命令+对话都行)。"新对话"发 clear 重置会话。
- **知识库编辑**：core 新增 `KnowledgeManager`(可增删改 + 热重载，本身是 KnowledgeStore，编辑即时可检索)。`GET/POST/DELETE /api/kb` CRUD；`POST /api/kb/draft` 用 LLM 把人话整理成 {title,content,tags} 草稿，人确认后保存。velocity 把 `KnowledgeLoader.load` 换成 `KnowledgeManager`，KnowledgeSearchTool 照常挂。
- **DashboardServer 扩展**：注入 chat/kb/draft，加 POST 体解析；bus 改可空(无跨服也能聊天/改知识库)。web 构建移到 router/agent 之后(依赖它们)，bus+connectedServers 从跨服块捕获。
- 配置/安全不变：token 鉴权、默认绑 127.0.0.1、知识库写在数据目录 `knowledge/`、定价/落库类仍走审批。

## 2026-06-13 WebUI 拆成独立 `web` 模块

用户预期 WebUI 会长出更多功能(对话/知识库编辑/更多页)，要求拆独立模块。而 `DashboardServer` 本就不依赖任何 Velocity 类
(只靠注入的 MessageBus + 供给函数)，于是拆成**平台无关的 `web` 模块**(`include 'web'`，依赖 core/bus，Java 8 基线)：
- `org.windy.windyagent.web.DashboardServer` + `resources/dashboard.html` 从 velocity 迁入 web 模块。
- velocity `implementation project(':web')` 挂载它(传 bus/token/dataDir/connectedServers)，通用 jar shadowJar 一并打包。
- 收益：WebUI 后续功能都在 web 模块内长，且 velocity 现挂、未来 Bukkit-hub 也能挂(平台无关)。
- 同步修正旧记忆"不拆多模块"——已被 Phase 2 多模块化取代，原则改为"有目的的子系统可成模块"。

## 2026-06-13 AI 管理控制台 WebUI（行为看板，v1）

A 阶段采集验收通过后接前端——给腐竹一个"看得见"的控制台。架构决策：**后端不拆 Gradle 模块**(放 velocity `web` 包，因为要拉总线/agent/KB 句柄)；
前端**单页打包进 jar**(API 设计成 JSON+token，将来要做大随时拆独立前端，零返工)。
- `web/DashboardServer.kt`：JDK 内置 `HttpServer`(零依赖)。路由 `/`(静态页) + `/api/servers|stats|segments|player`。
  `/api/*` 经总线把 `behavior_*` 动作派发到子服、子服回 JSON 直接透传。token 鉴权(query 或 X-Token 头)，默认绑 127.0.0.1。
- `resources/dashboard.html`：单页(原生 JS + CDN Chart.js)。卡片(总数/活跃/新增/时长/死亡/破坏/合成/成就)+ 分群环图 + 时长 Top5 + 玩家画像查询。token 存 localStorage。
- 配置 `web.*`(enabled/host/port/token)，默认 enabled=false(需显式开+设 token)。装配在 velocity 跨服块内(需 bus+registry)，shutdown 停。
- **下一轮**：`/api/chat`(网页直连 agent) + `/api/kb`(知识库 CRUD + AI 起草)——腐竹在 UI 里用 AI 改知识库。API 已留位。

## 2026-06-13 玩家行为分析 A 阶段：采集后端（防卡架构）

新功能起步——给服主一个"看得见"的运营数据底座。本轮做 A 阶段（采集+存储+画像+总线接口），看板（VC WebUI）下一轮接。
- **防卡架构**（核心）：监听器（主线程/异步线程）只对内存原子计数器 +1（纳秒级，高并发无压力）；一个后台 daemon 单线程每
  `flush-interval-sec` 把增量**批量写库**（不走 Bukkit 调度器，混合端更稳）；**不监听 PlayerMove**，在线时长靠会话增量累加。
- **采集**（`BehaviorTracker`，priority MONITOR）：会话(join/quit→时长)、死亡、命令、聊天、放置/破坏/合成、**成就**（`PlayerAdvancementDoneEvent`，
  滤掉 `minecraft:recipes/*` 噪声 → 模组进度漏斗信号）。
- **分表存储**（`BehaviorDatabase`，SQLite，ON CONFLICT 增量 upsert）：`profile`(滚动画像,长存) / `sessions` / `events`(死亡·成就原始,按 retention-days 清)。
- **T0/T1**（`BehaviorService` 出 JSON）：T0 描述统计(总数/活跃1d·7d/新人/均时长/死亡·破坏·合成·成就/时长 top5)；T1 规则分群(新人/核心/活跃/流失风险，阈值可配)。
- 装配：`WindyAgentBukkitPlugin` 三模式都启动采集；provider 的 `BukkitCapabilityHandler` 加总线动作 `behavior_stats/segments/player`(返回 JSON，供下一轮看板)。
- 配置 `behavior.*`(enabled/flush-interval/retention/churn/active-minutes/newbie)。flush 时打日志（没看板也能看到采集在动）。
- **不做**：T2 真算法(流失预测/推荐/聚类)需足量真实数据，推迟。

## 📍 进度快照（截至 2026-06-13）

按原 Phase 路线图：

| 阶段 | 内容 | 状态 |
|---|---|---|
| Phase 1 基础 Agent | ReAct + LLM 抽象 + 基础工具 + Velocity 单平台 + 命令触发 | ✅ 已验收 |
| Phase 2 架构+跨服+安全 | 多模块(core/bus/velocity/bukkit)、AgentRouter(ReAct/PlanExecute)、多传输总线(Redis/socket/inprocess)、三部署形态、安全层(分权+审批闸+注入加固) | ✅ 大部分完成 |
| Phase 3 RAG+记忆 | 知识库底座、长期记忆+召回、能力检索(关键词+LLM 扩展) | ✅ 完成(提前做) |
| Phase 4 变现 | 自动商品决策(估值/定价/礼包) → 支付适配器 + 订单状态机 | ⚠️ 只搭了估值地基，已冻结 |

**欠债 / 主动推迟：**
1. **Phase 2 支线：QQ/星图Bot(OneBot)接入** + QQ↔UUID 绑定 + WebSocket —— 从未做。
2. **Phase 4 后半：支付适配器 + 订单状态机** —— 未做(待定支付通道)。
3. **物品估值** —— 已冻结为"开服先验"，市场反馈校正待有玩家(原因见 memory/project-valuation-parked)。
4. **embedding 语义检索(本地 Ollama)** —— 推迟，现走关键词。
5. **真实运营验证** —— 整个项目没上过真服（当前最大瓶颈，非代码问题）。

**定位**：技术原型基本成型(Phase 1–3 走完)。下一步真正的推进(Phase 4 变现 / 估值校正 / 可靠性硬化)**都依赖"有真实服 + 玩家 + 支付通道"，而非更多功能**。当前是一个干净的"暂停点"。

---

## 2026-06-13 砍掉冗余的 ops 确定性命令层

`say/run/kick/who/bal` 这套确定性运维命令是之前"两个都做"那轮多加的，**逐个重复了早就存在的 LLM 工具**
(broadcast / run_command / kick_player / get_online_players / get_balance)，且同轮已改系统提示词让 LLM 直接执行运营指令 →
这层纯冗余。删除：`command/OpsExecutor.kt`、`command/OpsCommands.kt`、`velocity/RemoteOpsExecutor.kt`、`bukkit/LocalOpsExecutor.kt`
及 CommandContext/Router/两端插件的装配。保留 `value` 估值命令(LLM 干不好的结构化计算)+ 原有 LLM 工具。
(C 档的 appraise_item/refresh_item_index LLM 工具与 value 重叠，本轮用户选择保留，未动。)

## 2026-06-13 借 item-alchemy 种子表 + LLM 稀有度档定价

研究了 ProjectE 式 EMC 模组 item-alchemy(Pitan76)的实现：种子值全是**人手写、按稀有度标定**（DIRT=1…DIAMOND=8192…NETHER_STAR=139264），配方多趟传播(unsetRecipes 推迟重试=我们的 Bellman-Ford)，且**只认 crafting/smelting/stonecutting**（机器/祭坛配方它也覆盖不到）、对第三方模组物品**靠人填 config/附属模组**。结论：我们架构同构、没错；它的痛点(模组物品靠人手填)正是我们 LLM 的切入点。
- **抄它的原版比例**：把 item-alchemy 的 vanilla EMC 按比例缩放(锚 铁锭=10 → 钻=320/金=80/下界之星=5440)，重写 base-values(minecraft 基材 + 对应 #c 标签一致)。比例比拍脑袋权威(钻=32×铁,不是之前的 10×)。落地现网 Youer + core/deploy 模板。**纯配置,免编译。**
- **LLM 改"稀有度档"**(`LlmRootPricer` 重写)：LLM 只把无配方根**归入 common/uncommon/rare/epic/legendary 档**(它擅长分类、不擅长报精确数)，档→金币值由配置 `item-valuation.rarity-tiers` 决定，精确价靠传播派生。一致、可解释、可调、省 token。模型若直接给数字也兜底接受。
- 配置 `rarity-tiers`(默认 4/16/64/256/1024) + `AgentConfig.itemRarityTiers()`；经 batchSize 同样的方式传进 RemoteValueExecutor(VC 调 LLM)/LocalValueExecutor。
- 改动：core(AgentConfig/LlmRootPricer)、velocity(RemoteValueExecutor/Plugin)、bukkit(LocalValueExecutor/Runner)；配置四份。**编译/部署交用户。**

## 2026-06-13 估值 LLM 补根：EMC 溯源 + LLM 给"无配方根"定种子价

EMC 配方溯源的死角是「无合成路径的源头物」（矿/掉落/原料，如神秘农业的 inferium_essence）——它们没成本可追，
导致整条依赖链悬空。新增 `value llm`：**配方溯源负责能算的，LLM 负责给算不出的根定种子价，再让传播级联出其余**，各扬其长。
- **分工**：propagate 标出"悬空且被≥1 配方依赖"的根（`engine.lastRoots`，按入度排序 cap 80）；`value llm <子服>` 把这批根交 LLM 定价。
- **token 最省**：只喂根（几十个）+ 少量货币锚点校准量纲，要求只输出紧凑 JSON `{id:数值}`，用 fast-model 一次请求。core `LlmRootPricer`。
- **架构**（子服无 LLM、大脑在 VC）：`value llm` 在中心编排——总线 `value_roots` 取根 → VC 本机 LLM 定价 → `value_seed` 回写子服 → 级联重算。
  standalone/hub 的 bukkit 自带 LLM 则 `LocalValueExecutor` 本地直跑。core 新增共享 `RootInfo`/`RootsBundle`。
- **分表/优先级**：新表 `llm_seeds`（持久、刷库不删、upsert 累积、可 clearLlmSeeds 重估）。种子优先级 **人工 override > 配置 base > LLM**。
  basis 新增 `llm`、置信度 `medium`（标"LLM 估，建议核对"）。
- **安全**：`value llm` 仅 admin（花 token/钱）；LLM 出的是估值非定价，落商店仍走人审闸。
- 改动：core(valuation 包 2 新文件 + ValueExecutor/BuiltinCommands)、bukkit(ItemDatabase/ValuationEngine/ItemService/Handler/LocalValueExecutor/Runner)、velocity(RemoteValueExecutor/Plugin)。**编译/部署交用户**。

## 2026-06-13 估值真 bug：标签材料解析（第 1 轮 0 更新根因）

实测 `value build` 种子 18 但传播第 1 轮就 0 更新、全退默认值 1。查真实数据定位：**现代 MC(1.21/NeoForge 26.x) 配方材料绝大多数用标签**
（`#c:ingots/gold`、`#c:glass_blocks/colorless`…），而种子全是具体物品 → 每条配方都有 INF 材料 → 整图无解。
- **标签解析**：`ModItemParser` 解析 `data/<ns>/tags/item(s)/<路径>.json` → `TagEdge(#<ns>:<路径>, member)`（member 可为具体 id 或 #子标签）；
  `ItemDatabase` 加 `tags` 表（随 rebuild 重建）+ `loadTagMembers()`；`ValuationEngine` 把标签当节点，每轮松弛
  `标签值=成员最小值`（无工序开销、可嵌套递归），再松弛配方。`#c:ingots/inferium`→`inferium_ingot` 这类 mod 自有标签自动解析。
- **桥接原版/NeoForge 标签**：`#c:ingots/gold` 等定义在 loader 不在 mod jar → base-values 支持 **#标签键** 作种子，
  配置补了 ~22 个常用 c: 标签（金/铁/铜/钻/绿宝石/红石/玻璃/末影珍珠…）。
- **掉落型源头要人工锚定（设计点）**：`inferium_essence` 是种地掉落、输出配方全是 block/ingot 循环拆解、无路径追到种子 →
  它是该 mod 的"本位货币"，本就该 `value set` 人工锚定（正是用户验证过的工作流）。build 完成后**自动报告**已解析/退默认数量，
  并列出"被大量配方依赖却无解的高频源头"（按入度排序 top6）引导 `value set`（`engine.lastReport()` → 命令回执）。
- 仅改 bukkit 模块（item 包 4 文件）+ deploy/bukkit 与现网 Youer 配置。**编译/部署交用户**。

## 2026-06-13 命令体验：运营指令"只问不做"修复（提示词治本 + 确定性命令层兜底）

实测发现 `ai <自然语言>` 常被 LLM 当问答、只反问不执行（mimo 工具调用偏弱 + 系统提示太保守）。两手都做：
- **治本·提示词**（`SystemPrompt`）：明确区分「闲聊/问答」与「运营指令（祈使句）」——后者**立即调工具执行、不要只口头答复或反问"要我做吗"**；
  只在缺必要参数时一次性追问关键的那个；高危照常走安全闸。删掉原先"保守到不敢动手"的措辞。
- **兜底·确定性命令层**（仿 value）：高频运维动词首词命中即执行、**不走 LLM**。
  core `OpsExecutor` 接口 + `OpsCommands`（say/run/kick/who/bal 各一个 AgentSubcommand，写类需 admin、读类放开）；
  CommandContext/router +opsExecutor。velocity `RemoteOpsExecutor`（`<动词> <子服|*> [参数]`：子服走总线复用既有 broadcast/run_command/
  get_online_players/get_balance/kick_player 动作，`*` 走代理全局广播/在线；run 仍过 guard/审批，与 RemoteCommandTool 同闸）；
  bukkit `LocalOpsExecutor`（无子服名，直调 BukkitActions，进出设/清 RequestContext 让 guard/kick 按信任判定）。
- **同时修估值 UX**：`value build/set` 改成命令侧等执行器至多 4s（< 5s 总线超时）——跑得快就**同步回真实结果**（物品/配方/种子数），
  慢才转「仍在跑，value status 查」，不再让用户对着"已开始"干等。传播**种子为 0 时打 ⚠ WARNING**（估值无价值源），不再静默退默认值。
- **修配置坑**：Youer 落地配置是估值功能之前的老模板、缺 `item-valuation` 段 → base-values 空 → 种子 0 → 全退默认值。
  给 `deploy/bukkit` 模板 + 现网补 18 个 minecraft 基材锚点；`item-valuation` 加 craft-overhead/propagation-max-iter。
- 命令清单（确定性、首词命中即执行）：help/clear/history/status/memory/value + say/run/kick/who/bal + approve/deny/pending。
- 构建/部署绿（VC桥梁 + Youer 26.1.2）。

## 2026-06-13 EMC 式估值引擎 + 抗模组增删 schema + value 命令

把上轮「按需递归成本」demo 升级成 **EMC 式：种子值 + 全图传播**，并落地确定性 `value` 运维命令。
- **抗模组增删分表**（`ItemDatabase` 重写）：`items`/`recipes` 解析所得、刷库时重建；`overrides` 人工锚定值
  **持久、刷库绝不触碰**（宝贵）；`valuations` 传播结果重生成；`meta` 杂项。`rebuild()` 只动 items/recipes。
  孤儿锚定（模组删后 id 不在 items）`orphanOverrides()` 可查可清、不自动删。`items` 加 category/tier 列（tier 留空待后）。
- **传播引擎**（`ValuationEngine` 取代 `ItemValuator`）：种子=人工 override ∪ 配置 base（override 盖 base），
  Bellman-Ford 式松弛——其余 +∞，反复取每个有配方物品 `min(Σ材料值×数 / 产出数)+加工开销`，迭代到无变化（值只降→单调收敛，环天然处理）。
  **每轮打进度日志到子服控制台**；二次传播算置信度（种子/全已知=高，含未知基材=低）；结果写 `valuations`，appraise/propose 改**读它**（快、稳）。
- **人工锚定关联自动连锁（核心）**：`value set X v` → upsert overrides 成新种子 → **重跑同一 propagate()** →
  所有「最便宜路径用到 X」的**下游**自动跟进；X 的**上游原料不变**（语义正确）；override 最高权威。人只调一个，关联靠重传播自动跟。
- **value 确定性命令**（不走 LLM）：core `ValueExecutor` 接口 + `ValueCommand`（注册进 router，CommandContext/router +valueExecutor）。
  两实现：velocity `RemoteValueExecutor`（`value <子命令> <子服> [参数]` → 校验子服已连(取自 CapabilityRegistry) → 总线派发 `value_<sub>`）；
  bukkit `LocalValueExecutor`（无子服名，直调引擎）。子命令 build/get/set/unset/orphans/status/servers；写类(build/set/unset)需 admin、只读放开。
  **长操作异步**：build/set 触发的传播在子服**单线程执行器**上跑、命令即时回「已开始，value status 查」，避开 5s 总线超时。
- **三前门同一引擎**：`value` 命令（运维）、`appraise/propose` 工具（LLM 自然语言）、`value_*` 总线动作 共用同一 `ItemService`。
- 配置 `item-valuation` 加 `craft-overhead`(0.1)、`propagation-max-iter`(50)。
- 构建/部署绿（VC桥梁 + Youer 26.1.2）。验证路径：`ai value build earth` 异步传播看子服日志 → `value get` 精华层级递增 →
  `value set inferium 50` 后上层精华随之上升（关联跟进）→ `value build` 重解析后 override 仍在（人工值未被冲）。

## 2026-06-13 模组物品估值 demo（商业化地基）

商业化要给物品估值/定价/组礼包。厘清：Velocity 无物品、执行 NeoForge 脆 → **不执行模组，解析 jar 静态 JSON**；
上万物品文件+关键词撑不住 → **SQLite**；**估值≠检索，是沿合成树递归算成本**（结构化计算）。
- bukkit `item` 包：`ModItemParser`（jar zip 解析 lang `zh_cn`/`en_us` + `data/<mod>/recipe` JSON，处理 shaped/shapeless，
  tag/自定义类型尽力而为）、`ItemDatabase`（嵌入式 SQLite，items/recipes 表，mod 列+output 索引）、
  `ItemValuator`（递归 min-cost + 基材基准价 + 环/深度 guard + 置信度 + 路径拆解；proposePack 组篮子打折）、
  `ItemService`（门面，启动后台 warmup 建库，ensureBuilt 懒建）。
- 工具：本地 `Bukkit{Refresh,Appraise,ProposePack}` + provider 总线动作 refresh_items/appraise_item/propose_pack +
  中心 `Remote{Appraise,ProposePack,RefreshItems}Tool`（DB 留子服，只查询/结果过总线）。velocity 注册远端工具。
- 配置 `item-valuation`(enabled/base-values 基材基准价/default/pack-discount/currency)。`sqlite-jdbc` shade 进 bukkit（jar 涨到 44M，含跨平台原生库）。
- 定价提案是**建议**，落商店才走已有审批闸（本轮不落库）。
- 实测数据：Youer 4 模组（神秘农业 981 配方，精华层级链=天然估值展示，zh_cn 内置中文名）。
- 边界：模组自定义配方类型/功能型物品成本锚失效 → 退基材值 + 低置信标注；真实成交价校准、本地 Ollama embedding 留后。
- 踩坑：KDoc 里 `recipe(s)/*.json` 的 `/*` 触发 Kotlin 嵌套块注释，吞掉收尾 `*/` → 编译「Unclosed comment」，改写措辞解决。

## 2026-06-12（夜）省 token + 记忆数据治理

- **A 缓存友好结构**：召回记忆从系统提示挪进**当次 user 消息**（`userMessageWithMemory`），
  系统提示+工具定义保持稳定→ provider 前缀缓存可命中（ReAct 多轮/跨请求复用）。两 agent 改用。
- **B 元任务便宜模型**：`llm.fast-model` + `buildFastProvider`；`AgentRouter` 分类、`LlmQueryExpander` 扩展走 fast 模型，
  省 pro 模型 token；未配则用主模型（兼容）。两载体注入。
- **C 记忆治理**（也省 token）：
  - 召回阈值 `memory.recall-min-score`(默认2)：`KeywordKnowledgeStore.searchScored` 暴露分数，弱命中不召回，垃圾不进上下文。
  - 写入去重：`remember` 前同 scope 归一化相等/互相包含则复用，不重复攒。
  - `memory clean` 命令清精确重复。
- **延后**：Claude `cache_control`（anthropic-java SDK 改动有破坏风险，且用户跑 mimo 不走 Claude 路径，不值当）；
  LRU 淘汰、LLM 周期 consolidation。
- 构建/部署绿。诚实定位：保证省的是便宜模型+治理；缓存看 provider 是否前缀自动缓存（mimo 待确认，harmless）。

## 2026-06-12（夜）长期记忆 LongTermMemory（Phase 3 核心）

短期上下文（SessionManager）之外补**跨会话长期记忆**。文件持久 + 复用稀疏检索，零外部依赖。
- core `memory` 包：`MemoryEntry`/`LongTermMemory`(接口)/`FileLongTermMemory`(memories.json 持久；
  recall 把记忆映射成 KnowledgeEntry 交 `KeywordKnowledgeStore` 检索，**复用不重写**；scope=玩家会话或 global；max-entries 淘汰)。
- **写**：`RememberTool`（remember 工具），scope 默认当前玩家（读 `RequestContext.sessionId`）；SystemPrompt 加 remember 指引。
- **读（透明召回）**：`RequestContext` +sessionId；`AgentContext` +recalled；`AgentRouter` 注入 memory，run 时
  `recall(sessionId, userMessage, topK)` → 填 recalled；`ReActAgent`/`PlanExecuteAgent` 经 `systemPromptWithMemory` 拼进系统提示。
  Agent 无需显式查就"想起"，零额外 LLM。
- **管理**：`memory` 命令实做（列出/forget/clear，clear 只清自己 scope）；CommandContext/Router 透传 memory。
- 配置 `memory`(enabled/recall-top-k/max-entries)；两载体建 FileLongTermMemory 注入 AgentRouter + RememberTool + 命令路由。
- 构建/部署绿。验证：`ai 记住我喜欢生存`→`ai clear`→`ai 我喜欢什么模式`答"生存"；重启仍记得；`ai memory`/`forget`/`clear`。

## 2026-06-12（夜）载体无关的 Agent 命令框架（做进 core）

用户：把 agent 常用命令做进 core，未来载体不确定（QQ/Web/CLI），命令逻辑该载体无关、一处实现处处复用。
- core 新 `command` 包：`AgentSubcommand`/`CommandContext`/`AgentCommandRouter` + 内置
  `help/clear(reset/new)/history/status/pending/approve/deny/memory(占位钩子)`（中文别名齐）。
- 路由：`dispatch(input, sessionId, trust)` 取首 token 命中元命令→处理返回文案；**未命中返回 null**→调用方交给 Agent 对话。
  requiresTrusted 的（approve/deny/pending）对不可信来源回"需管理员"。
- 载体接入＝薄适配：velocity/bukkit 的 `/ai` 命令与 `!ai` 聊天**先 dispatch 再 run**；载体提供 statusSupplier。
  顶层 `/ai-approve|deny|pending` 改为转发 router（单一来源），与 `/ai approve …` 等效。
- 价值：未来载体只需 parse+dispatch+输出即白嫖全部命令。`clear/history` 直接服务"上下文对话"；`memory` 为长期记忆留钩子。
- 构建/部署绿。验证：`ai help`/`ai status`/`ai 记住X→ai clear→已忘`/`ai history`/控制台 approve 与 `/ai approve` 等效。

## 2026-06-12（夜）Safety 续：kick 纳入信任闸

run_command 之外，把破坏性具名动作 `kick` 也收进护栏（之前完全没拦，注入"踢掉某某"AI 照踢）。
- core `safety/ActionGate.guardTrusted(action,target,audit)`：UNTRUSTED→拒+审计 DENY，TRUSTED→放行+ALLOW。
- bukkit：`BukkitActions.kick` 内置信任闸（复用自带 audit），覆盖 BukkitKickTool；provider 的 kick_player 总线动作目前无中心派发方，无影响。
- velocity：`KickPlayerTool` 注入 audit + ActionGate；`VelocityPlatform` 透传 audit。
- 效果：玩家聊天 `!ai 踢了某某` → 拒（不可信）；控制台/管理员 `/ai 踢某某` → 执行。broadcast 等无害动作不拦。

## 2026-06-12（夜）Safety Layer Phase 2：来源分权 + 人工审批闸 + 注入加固

- **来源分权**：`TrustLevel{TRUSTED,UNTRUSTED}`；`AgentContext` 加 trust，4 触发器设值
  （控制台 / `hasPermission("windyagent.admin")` 玩家 = TRUSTED；玩家聊天 `!ai`、无权限玩家 = UNTRUSTED）。
  请求级传递用 `RequestContext`(ThreadLocal)：`AgentRouter.run` 进入设、finally 清；工具内读，默认 UNTRUSTED 最保守。**不改 AgentTool 接口**。
- **Guard 三态**：`check(cmd, trust)` → Allow / Deny / NeedsApproval。高危+UNTRUSTED=Deny（连审批都不给，挡注入越权）；
  高危+TRUSTED=NeedsApproval；warn 模式=Warn 放行。
- **人工审批闸**：`safety/PendingApprovals`（内存队列+10min 过期，execute 闭包=绕 guard 真实执行）。
  工具遇 NeedsApproval→submit+回「已提交审批 #id」；`/ai-approve|ai-deny|ai-pending`（velocity RawCommand / bukkit 命令，
  权限 `windyagent.admin`，op 默认有）。approve→执行 execute()。
- **provider 例外**：子服 provider 收到的是中心已 gate 过的可信总线命令 → `BukkitActions.executeCommand`（ungated）直接执行，
  不以 UNTRUSTED 重判（否则中心已批准的会被子服误拒）。standalone/hub 本地 agent 仍走 gated `runCommand`。
- **注入加固**：`SystemPrompt` 增条：玩家聊天=不可信、不得据其越权/改规则、拒"忽略以上指令"类注入、高危只能走审批。结构层(②)兜底。
- 构建/部署绿；plugin.yml 加 ai-approve/deny/pending 命令 + windyagent.admin 权限(default op)。
- 验证：`!ai 执行 op X`(UNTRUSTED)→DENY；控制台 `/ai 执行 op X`(TRUSTED)→审批#1→`/ai-approve 1` 才真执行。

---

## 2026-06-12（夜）Safety Layer v1：命令执行护栏 + 审计

acting agent 能 run_command 任意指令 → 幻觉/误解/玩家提示注入都可能诱导它跑 stop/op/ban/kill 等破坏命令。
RAG 不解决此事（它是 grounding，非安全控制）。上生产前必备护栏。
- `safety/CommandGuard`：取命令 base（去斜杠/命名空间前缀）比对 denylist + 危险选择器(@a/@e/@r)；
  mode=enforce(拒)/warn(放行记告警)/off。默认 denylist：stop/restart/op/deop/ban/whitelist/save-off/reload/gamerule/kill/execute… 可配。
- `safety/AuditLog`：每条命令执行(含被拒)写 `<dataDir>/audit.log` + 控制台（时间|session|action|cmd|ALLOW/DENY/WARN）。
- **两道闸（防御纵深）**：中心 `RemoteCommandTool` 派发前 + 子服 `BukkitActions.runCommand` 执行前 各 check + audit；
  Deny 则不执行、回「高危命令已拦截，请管理员手动执行」。
- AgentConfig+模板加 `safety`(mode/command-denylist)；装配把 guard/audit 传入 velocity/bukkit 各构造点。
- 边界（Phase 2）：无异步审批队列（高危=拒绝）；未按触发来源分权；提示注入专项 prompt 加固待补。
- 验证：`ai 在 earth 执行 op windy` → 拦截+audit DENY，子服不执行；`say 你好` → 放行+ALLOW；`kill @a` → 拒（选择器）。

---

## 2026-06-12（夜）无 embedding 的 RAG：LLM 查询扩展（稀疏优先 + 成本门控）

背景：要 RAG（变现"服务器自答客"），但 mimo 无 embedding → 用「稀疏检索 + LLM」式无嵌入 RAG。
认知澄清：RAG≠embedding；向量只是稠密检索一种，稀疏(BM25/关键词)+LLM 增强同样是正经 RAG。
另注：RAG 是 grounding/质量控制，**不是安全控制**——执行护栏/提示注入是正交风险，待 Safety Layer。

- `rag/LlmQueryExpander(llm)`：一次 chat 调用把查询扩成 3~8 个检索词（中英/同义/口语→规范），逗号解析；失败/空退回。
- `KnowledgeSearchTool` / `SearchCapabilitiesTool`：**稀疏优先**，命中 < `rag.min-hits` 且配了 expander 才扩展再检索，
  结果标注「（已智能扩展：…）」。多数命中查询**零额外 LLM**，成本可控、可关（`rag.query-expansion`）。
- 复用现有稀疏底座（`KeywordKnowledgeStore` 中文二元组加权、`CapabilityRegistry`+`CommandSynonyms`），不改检索器内部；
  扩展词拼进查询串交其再切词。两载体把 `llm` 传入工具（按开关）。AgentConfig+模板加 `rag` 段。
- 部署：示例知识 `会员.md` 放入 VC 知识库便于验证。
- 验证：`ai 充值有什么好处` → 稀疏弱命中 → 日志「智能扩展：会员 vip 权益…」→ 命中会员条目作答；
  `ai 会员` 直接命中则不触发扩展（成本门控）。

---

## 2026-06-12（夜）能力目录：双向持久 + 变更驱动推送

用户拍板：别每次重启都重推/重建。设计 = **子服变更才推 + 中心永久记忆**。
- **Bukkit（变更门控 + 文件记忆）**：建目录后算**内容签名**（排除 builtAt），与 `dataFolder/catalog.sig`
  里上次已提交的签名比对——变了才 `deliver` 并写新签名；没变 `跳过下发`。CapabilitySync 内实现。
- **Velocity / 中心（磁盘持久）**：`CapabilityRegistry(embedder, persistDir)`；收到目录 `put` 即写
  `<dataDir>/capability/<server>.json`；启动 `load()` 从盘读回 → 重启免等子服重推。bukkit standalone/hub 同。
- **配合关键**：正因中心持久，子服「没变不推」才安全（否则中心重启会空）。稳态零推送。
- 边界：embedding 开启时 load 仍会重嵌入（向量盘缓存留待 L3 真用时再加）；中心磁盘被清而子服签名未变是少见死角，
  删 catalog.sig 或一次插件变更即可强制重推。
- 顺带：`BukkitCapabilityHandler` 加收/发日志（`← 收到中心指令` / `→ 回复中心`），子服控制台可见中心派活。

---

## 2026-06-12（夜）修：混合端主线程跳转（run_command 等超时）

实机：L2+同义词检索全好（中文「传送」命中 24 条、来源标到 [cmi]/[youer]），但 `run_command_on_server`
全部 5000ms 超时。排查：pause-when-empty=-1 已生效、本次无 pausing 日志 → **不是暂停**。
根因＝`BukkitActions.onMain` 靠 `Bukkit.getScheduler().runTask` 跳主线程，而 **Youer(Mohist 系 NeoForge 混合端)
的 Bukkit 调度器不可靠**（同 catalog sync 早先被迫改独立线程的毛病）。检索不碰主线程故无恙；
run_command/broadcast/kick/balance 全要主线程 → 调度器不执行 → onMain 干等 → 总线超时。

修：`onMain` 改用 **NMS/NeoForge MinecraftServer 作主线程执行器**（它实现 Executor、`.execute()` 必在主线程下一 tick 跑），
反射 `Bukkit.getServer().getServer()` 取之，回退 Bukkit 调度器。Paper 上同样适用（CraftServer.getServer() 也是 Executor）。

---

## 2026-06-12（夜）能力检索 L2 打磨 + L3 向量 RAG 地基

实机：目录同步成功（中心收 464 条、search_capabilities 本地命中），但暴露三类问题 → L2 打磨；
并按用户要求上 L3 语义检索（复用 mimo /embeddings）。

### L2 打磨（零基建，已部署）
- **查询分词 OR + 按命中词数排序**（`CapabilityRegistry.scoredWithServer`）：多词查询 `"tp home warp spawn"`
  不再整串子串落空，一次捞回全部、命中词多者靠前 → 根除 Agent 反复猜词。
- **来源归属用命名空间 key**（`BukkitActions.capabilityCommands`）：混合端许多插件命令非 PluginCommand 实例，
  改从 `cmi:home`/`xconomy:money` 这类 key 取真实来源（原 452 条全堆"原版/模组"的错判修正）。
- **按命令名去重**：消除 `/tp ×2`、`/tpa ×2`（命名空间/别名包装是不同对象，distinct 去不掉）。

### L3 向量 RAG 地基（已部署，默认关）
- `EmbeddingProvider` + `OpenAICompatEmbeddingProvider`（POST {base}/embeddings，复用 llm base-url/key + 单独模型名）；
  `buildEmbeddingProvider`；config 加 `embedding:`(enabled/model/可选 base/key)。
- `VectorIndex`（内存余弦，按 server 分桶，线性扫描，不引 Qdrant）。
- `CapabilityRegistry(embedder?)`：accept 时**异步分批嵌入**该服命令；`search` **向量优先、失败/空退回 L2 关键词**。
- `SearchCapabilitiesTool` 改为**先 search 再判空**（不再按关键词 count 门控，否则语义命中会被误判"未找到"）。
- 两端注册表注入 embedder（velocity / bukkit-runner）。

### 状态 → 转向：embedding 降为可选，默认走「关键词 + 中英同义词」
- 实测 mimo 全是 chat/ASR/TTS、**无 embedding 模型**。结论：**不能把 embedding 当硬依赖**（多数用户没有）。
- 改为：embedding **可选增强**（有嵌入接口者开，失败自动退回）；默认路径 = L2 关键词 + **中英同义词扩展**。
- `CommandSynonyms`（结构化、零基建、可审计）：query 含「传送/经济/领地…」即扩展到 `tp/home/warp` `money/bal` `claim/region` 等
  英文关键词并入检索 → **中文也能命中英文命令，不需要任何嵌入接口**。`scoredWithServer` 接入扩展；工具描述说明可中/英文搜。
- 洞察：Agent 本就是 LLM，自己懂「传送=tp」；同义词表是兜底+加速。用户 mimo config 已 `embedding.enabled: false`。
- 预期：`ai earth 有什么传送相关指令` → 同义词扩展命中 /tp /home /warp /tpa，一次返回，零外部依赖。

---

## 2026-06-12（夜）能力目录同步：从 per-query 实时自省 → 预同步中心索引

### 病根（实机暴露）
L1 实时自省是 **per-query live 模型**：中心每次提问都 `VC→总线→子服现扫 CommandMap→回`，
一次往返只够查一个关键词。Youer 464 条命令下，Agent 反复猜词（cmi/spawn/effect/money…）8 次往返、
撞满 10 次迭代仍答不出。用户拍板正确架构：**子服启动建目录 → 推回中心 → 中心本地索引 → Agent 零往返检索**。

### 实现
- **bus**：新 DTO `Capability.kt`（CapabilityCommand/Catalog）；`MessageBus` 加**默认空**的
  `publishCatalog`/`onCatalog`（不破坏旧实现，单向推送方向）；Socket 加 `CATALOG` 帧
  （`SocketClientBus.publishCatalog` + 重连自愈重推；`SocketHubBus.onCatalog`）；
  Redis 加 `windyagent:catalog` 频道；InProcess 直回调。
- **core**：`CapabilityRegistry`（server→catalog，`accept(json)` 入表；L2 子串过滤 search/overview/count）；
  `SearchCapabilitiesTool`（`search_capabilities`，**查中心本地、零往返**；空 query 给来源概览；
  描述里明示"一次即可、语义问题转知识库"）。
- **bukkit**：`BukkitActions.capabilityCommands()`（复用 commandSnapshot 映射 DTO）；
  `CapabilitySync`（启动延迟 ~5s 建目录 + PluginEnable/Disable 去抖重建；不用 ServerLoadEvent 以保 1.12 编译基线；
  异步线程跑、不占主线程）。provider 经 `bus.publishCatalog` 推回；standalone/hub 入本地 registry，hub 另 `onCatalog` 收他服。
- **装配/清理**：velocity 建 registry + `onCatalog` + 注册 `SearchCapabilitiesTool`；
  **删** `RemoteListCommandsTool` / `BukkitListCommandsTool` / handler `list_commands` 动作 / `BukkitActions.listCommands`
  （per-query 旧路，正是病根）。core 加 `jackson-module-kotlin`（反序列化目录 data class）。

### 构建/部署 ✅
全量 shadowJar 绿；两端部署到 VC桥梁/Youer。velocity 含 Search/Registry、已无 RemoteListCommands；bukkit 含 CapabilitySync+DTO。

### 待验证（实机，重启两端）
- 子服日志见「能力目录已生成 — NNN 条」；中心见「已接收子服 earth 能力目录 — NNN 条」。
- `ai earth 有什么和钱有关的命令` → 中心 `Tool call: search_capabilities` **一次本地命中**，不再 list_commands 往返/撞迭代。
- L3 向量 RAG（语义"好玩/怎么玩"）仍留后；KnowledgeStore/检索接口已为切换留好。

---

## 2026-06-12（夜）L1 实时指令自省 + demo 实机部署

### 实机部署（Velocity + Youer 混合端 / socket / 无 Redis）
- 目标机：`VC桥梁`(Velocity 3.x, bind :20253) + `Youer 26.1.2`（MohistMC NeoForge+Bukkit 混合端，
  MC 26.1.2，实现 Bukkit API 26.1.2-R0.1）。子服在 velocity.toml 注册名 = **earth**(127.0.0.1:25565)。
- 两端铺最新 jar + 统一配置：Velocity=socket 中枢(127.0.0.1:25599, 填 key)；Youer=provider, server-name=earth, 连 25599。
- 经济：Youer 只有 Vault 本体、无经济后端 → 余额需用户自行装**任意挂 Vault 的经济插件**（CMI/Ess/SunLight 皆可，
  Vault 抽象层使其 provider 无关，`get_balance` 零改动通吃）。EssentialsX 在 MC 26.1.2 上不兼容，弃用。

### L1 实时指令自省工具 ✅（provider 无关的"指令大全"）
最佳数据源不是文档，是服务器自己——读 Bukkit `CommandMap` 拿**真实已装**命令，版本永远对、无文档过期。
- `BukkitActions.listCommands(query, limit)`：跨版本反射读 knownCommands（优先 `getCommandMap/getKnownCommands`，
  回退沿类层级找字段）；去重、按名/别名/描述过滤、限量（混合端命令上千，必须带 query+limit 防爆）；
  输出 `/命令 — 描述（用法）[来源插件]`。
- handler `list_commands` + 本地 `BukkitListCommandsTool`(`list_server_commands`) + core `RemoteListCommandsTool`
  (`list_commands_on_server`)；velocity 与 bukkit-hub 两端注册。
- 定位：知识库 L1 层（L2=文档整理进 KeywordKnowledgeStore，L3=向量 RAG，按量级递进；均靠 KnowledgeStore 抽象切换）。

---

## 2026-06-12（夜）配置统一 + 寻址去 Velocity 化（澄清）

用户审查配置时点出两问题：① 两端配置不一致（Bukkit 要改 config.yml + windyagent-config.yml 两份，
总线键路径还和 Velocity 不同）；② 不想靠 Velocity 的信道/识别连接（VC 信道不稳）。

- **配置全统一到 `windyagent-config.yml`**：两载体只读这一本、同一套 schema。
  - 新增 `deployment` 段（`mode` + `server-name`），仅 Bukkit 读取；Velocity 忽略。
  - `AgentConfig` 加 `mode()`/`serverName()`；`WindyAgentBukkitPlugin` 改为只读 `AgentConfig`，
    **废除 Bukkit 原生 config.yml**（已删，不再 saveDefaultConfig）；`BukkitAgentRunner.start(cfg,…)` 由入口统一传配置。
  - 总线键（transport/timeout/redis/socket）两端同在 `cross-server:` 下；socket.host 注释按角色说明
    （中枢=监听地址、提供方=要连的中枢地址）。
- **寻址澄清（本就如此，补强注释）**：跨服寻址完全基于子服**自报的 server-name**（连总线时注册），
  不碰 Velocity 玩家信道、不读 Velocity 服务器注册表；仅 InProcessBus 测试 stub 用到 `allServers`，与生产无关。
  - 后续可做：中心维护「谁连上来了」的动态名单，彻底脱离 VC（记账于此）。
- 构建：`:velocity:shadowJar :bukkit:shadowJar` 全绿；bukkit jar 现仅含 `windyagent-config.yml`（无 config.yml）。

---

## 2026-06-12（夜）Vault 经济（总线）+ MCP 地基

用户场景：Velocity 问问题 → 去 Bukkit 取 Vault 经济数据。厘清了**两条正交通道**：
自家子服能力走**总线**（不该套 MCP）；MCP 留给**第三方/外部标准化工具**。本轮两者都做。

### 轨道 1：Vault 经济走现有总线 ✅
在「RemoteTool over MessageBus」这条现成链路上加 `get_balance`，零新概念：
- bukkit：`VaultHook`（软依赖，Economy 引用收拢一处、先判存在再触碰，未装 Vault 不崩）；
  `BukkitActions.balance()`；handler 加 `get_balance` 分支；本地 `BukkitBalanceTool`（standalone/hub）。
- core：`RemoteBalanceTool : AgentTool`（dispatch `get_balance`），与 RemoteCommandTool 同套路。
- velocity：注册 `RemoteBalanceTool`；hub 模式的 BukkitAgentRunner 同样挂上。
- 构建：bukkit 加 jitpack + `compileOnly VaultAPI:1.7`，plugin.yml `softdepend: [Vault]`（不打包，运行时由 Vault 提供）。

### 轨道 2：MCP 地基（落实 DEVLOG 预留的 McpToolAdapter）✅
- core 新增 `mcp` 包，**复用已有 okhttp + jackson，零新依赖、不拉 MCP SDK**：
  - `McpClient`：JSON-RPC 2.0 over Streamable HTTP（initialize / tools/list / tools/call），
    记录 `Mcp-Session-Id`，响应兼容纯 JSON 与 SSE。
  - `McpToolAdapter : AgentTool`：把远端 MCP 工具包装成本地工具，**ReAct 核心零改动**——
    Agent 对 MCP 工具 / 跨服 RemoteTool / 本地工具一视同仁。
  - `McpLoader`：按配置逐个握手、拉工具、包装；单 server 失败跳过不影响其它。
- `AgentConfig.mcpServers()` 读 `mcp.servers`（name/url/headers）；velocity 与 bukkit 启动时
  把 MCP 工具加入 extraTools；config 模板加 `mcp` 块（默认空）。

### 构建验证 ✅
`:core:build :velocity:shadowJar :bukkit:shadowJar` 全绿。两 jar 均含
McpClient/McpToolAdapter/McpLoader + RemoteBalanceTool + okhttp；bukkit 另含 VaultHook/BukkitBalanceTool。

### ⚠️ 待办（手动/实机）
1. **Vault 经济实机**：子服装 Vault + 经济插件，Velocity `!ai 查 xxx 在 earth 的余额` 走 get_balance 验证。
2. **MCP 联调**：起一个 MCP server（HTTP），配 `mcp.servers`，验证 tools/list 接入 + tools/call。
   当前只覆盖 HTTP+JSON/SSE，未做 stdio 传输与流式增量（按需再补）。
3. 旧账仍挂：config 模板真实 api-key 泄漏；各跨进程链路均未实机联调。

---

## 2026-06-12（晚）Phase 2 续：AgentRouter + 多传输 + 三部署形态

### 1. AgentRouter（Phase 2 清单项）✅
- 抽 `Agent` 接口（`run(context): AgentResponse`），载体与 Router 只依赖它，不绑定具体实现。
- `AgentLoop.kt`：把 ReAct 工具循环抽成共享 `toolLoop()` + `syncHistory()`，ReAct / PlanExecute 复用，避免重复。
- `ReActAgent` 瘦身为接口实现；新增 `PlanExecuteAgent`（复杂多步：**规划一次（不给工具，防规划阶段误触发副作用）→ 带计划连续执行**，省 LLM 成本、不为每步开一轮）。
- `AgentRouter`：**启发式优先 + LLM 兜底**——中文多步连接词/编号/长度/问候零成本判别；模棱两可才调一次轻量 LLM 分类；分类失败保守走 ReAct。Velocity/Bukkit 入口都改用 Router。

### 2. 通信层抽象 + 三种传输 ✅
**背景**：测试环境无 Redis；且未来用户部署各异（有的没 Velocity、有的没 Redis）。Plugin-Messaging 因高并发场景**否决**（搭玩家连接、主线程串行投递）。
- 抽 `MessageBus` 接口（bus 模块），`RemoteCommandTool` 与各载体只依赖它，靠配置 `transport` 切换。`RedisBus` 去实现它。
- `InProcessBus`（测试）：进程内回显 stub，单 Velocity 实例即可验证中心侧整链，不跨进程。
- `SocketBus`（**无 Redis 的生产传输**，零新依赖、原始 TCP、Java 8）：
  - `SocketHubBus`（中枢/中心侧）：自建 `ServerSocket`，子服 dial-home 注册名字；`dispatch` 按名写帧、按 requestId 关联回包（复用 RedisBus 的 pending+超时套路）。星型拓扑、thread-per-connection，无需 Netty。
  - `SocketClientBus`（子服侧）：连中枢、注册、读请求 → 线程池执行 handler（读循环不阻塞、并发回包）→ 写回包；断线 2s 重连。
  - `SocketFrame`：长度前缀 + JSON 帧（REGISTER/ACK/REQUEST/REPLY）；REGISTER 带可选共享密钥鉴权。

### 3. 命门：core 降到 Java 8 ✅（验证字节码 major=52）
- core 依赖全是 Java 8 基线（anthropic 2.34.0/okhttp 4.12/jackson 2.17/snakeyaml 2.2），故 core 可编 Java 8。
- **消除了「bukkit 不能依赖 core」的唯一障碍**（原因仅是 core=11、老 Paper=8）。velocity 仍 11，Java 8 的 core 在 11 上照跑。
- 顺手把 `RemoteCommandTool` 从 velocity 移到 core（只依赖 MessageBus+AgentTool），core `api project(':bus')`，velocity 与 bukkit-hub 共用。

### 4. Bukkit 按模式启动 + 嵌入式 Agent（覆盖三部署形态）✅
bukkit 现 `implementation project(':core')`。`config.yml` 加 `mode`：
- **provider**（默认＝原能力提供方）：连总线（redis/socket 客户端）+ `BukkitCapabilityHandler`。形态 A（Velocity+子服）。
- **standalone**：本机嵌入式 Agent，无总线。形态 B（单 Paper 服）。
- **hub**：嵌入式 Agent + `SocketHubBus`/RedisBus 中枢，本服本地工具 + `RemoteCommandTool` 派发其它子服并存。形态 C（无代理多 Bukkit）。
- 复用而非重写：本服 4 动作抽成 `BukkitActions`（统一主线程跳转），provider 的 handler 与嵌入式的 `Bukkit*Tool` 共用。
- 新增 `BukkitPlatform`/`BukkitAgentRunner`/`BukkitCommand`(`/ai`)/`BukkitChatListener`(`!ai`)，镜像 Velocity 入口；LLM 配置复用 core 的 windyagent-config.yml（jar 内模板自动释放）。
- slf4j：core 用 slf4j 记日志，老 Spigot 运行时不带实现 → bukkit 打包 `slf4j-api` + `slf4j-jdk14`（转 JUL），任意宿主都能落地。

### 构建验证 ✅
- `gradlew :core:build :velocity:shadowJar :bukkit:shadowJar` 全绿。
- velocity jar：SocketHubBus + RemoteCommandTool(core) + InProcessBus + anthropic + 配置 ✓。
- bukkit jar（30.5MB，含嵌入式 Agent）：BukkitPlatform/BukkitAgentRunner + AgentRouter + RemoteCommandTool + SocketClient/HubBus + slf4j + anthropic + windyagent-config.yml/config.yml/plugin.yml ✓。

### ⚠️ 待办/隐患（手动联调，本地无 Redis/真服务端）
1. **SocketBus 双进程联调**：起 Velocity(transport: socket) + 一个 Bukkit(provider, transport: socket) 验证注册→派发→回包；hub 模式同理。
2. **standalone 实机**：bukkit jar 丢单 Paper 服，`mode: standalone` + 填 LLM key，验证 `!ai`/`/ai` + 本地工具。
3. **A 形态无 Redis 烟雾测试**：velocity `transport: inprocess`，控制台 `/ai 在 <子服> 给我钻石` 看回显 stub。
4. （旧账，正交）config 模板真实 api-key 泄漏未堵；跨进程运行时整体未实机联调。

---

## 2026-06-12（下午）Phase 2 启动

### 架构决策（用户拍板，三选三推荐项）
1. **Bukkit 定位 = 纯能力提供方**：子服只暴露自身能力（给物品/传送/Vault/跑指令）被中心 Agent 远程调用，**不自带 Agent**。中心一个大脑 + 多能力提供方，避免 N 个独立 Agent 导致上下文碎片 + LLM 成本翻倍。
2. **Velocity↔Bukkit 通信 = 复用 Redis 总线**：复用 RedisBungee 的 Redis 做请求/响应 pub-sub，零新增基建、零新端口。中心侧把远端能力包装成 `AgentTool`（`RemoteTool`），ReAct 核心零改动。MCP 留给将来接第三方工具时再上（`McpToolAdapter : AgentTool` 并存）。
3. **先拆多模块再写 Bukkit**：加 Bukkit 本就强制多模块（Velocity/Bukkit API 不能同 jar）。

### 已完成：多模块拆分（Phase 2 第一步）✅
- 单模块 → 多模块：`core` + `velocity`（`bus` / `bukkit` 待建）
  - `core`：agent / llm / prompt / `Platform` 接口 / `AgentTool` / 配置加载 + `windyagent-config.yml` 模板。平台无关，被各载体依赖并 shade。
  - `velocity`：现载体，依赖 `:core`，shadow 出 fat jar
- **包名保持不变**（`org.windy.windyagent.*` 各模块不重叠）→ import 零改动，纯物理移动
- 源码目录 `src/main/java` → `src/main/kotlin`（规范化）
- 构建脚本：
  - 根 `build.gradle` 改聚合器，`subprojects {}` 统一 `java-library` + kotlin + toolchain 11 + 仓库
  - `core/build.gradle`：jackson 用 `api` 暴露（各载体工具解析 inputJson 共用）；anthropic/okhttp/snakeyaml 用 `implementation`（core 内部用，仍随 runtimeClasspath 被 shade）；slf4j-api 用 `compileOnly`（宿主自带，1.7.36 兼容 Velocity 3.x 与现代 Paper）
  - `velocity/build.gradle`：`compileOnly velocity-api` + `implementation project(':core')` + shadow
  - `settings.gradle`：`rootProject.name='windyagent'` + `include 'core','velocity'`
- NeoForge 残留（mixins.json / assets / neoforge.mods.toml）挪进 `disabled-neoforge/_resources/` 保管
- **构建验证**：`gradlew :velocity:shadowJar` 通过，产物 `velocity/build/libs/windyagent-1.0-SNAPSHOT.jar`，内容齐全（core 类 + velocity 类 + 配置 + anthropic/snakeyaml 全 shade）

### 已完成：bus + bukkit + 中心 RemoteTool ✅
用户定的 Bukkit 目标：**Spigot 插件、1.13+ 为主、兼容 1.12**。

**关键约束 — Java 版本分层**：1.12/1.13 老服多为 Java 8，而 core/velocity 是 Java 11（Java 8 JVM 加载不了 11 字节码）。解法契合「Bukkit=纯能力提供方」：**bukkit 不依赖 core**，只依赖 bus；bus 与 bukkit 都按 **Java 8** 编译。
- `core`(11) 只给 velocity；`bus`(8) velocity+bukkit 共用；`velocity`(11)=core+bus；`bukkit`(8)=仅 bus
- 兼容 1.12~1.13+：bukkit 编译期用 `spigot-api:1.12.2`，只调跨版本稳定 API（dispatchCommand/broadcastMessage/getOnlinePlayers/getPlayerExact/kickPlayer）；`plugin.yml` 设 `api-version: 1.13`（1.12 忽略该字段照常加载）

**`bus` 模块**（`org.windy.windyagent.bus`）
- `BusProtocol.kt`：`ToolRequest`/`ToolReply` DTO + 频道约定（req:`windyagent:req:<server>`、reply:`windyagent:reply`）
- `RedisBus.kt`：Jedis pub/sub。中心 `startReplyListener()`+`dispatch()`(带超时 future)；子服 `listen()`。独立守护线程订阅、断线 2s 重连。**不用 slf4j**（Spigot 1.12 无实现），内部 JUL。

**`bukkit` 模块**（能力提供方插件）
- `WindyAgentBukkitPlugin`：读 config.yml（server-name + redis），订阅本服请求频道
- `BukkitCapabilityHandler`：**关键——用 `Bukkit.getScheduler().runTask` 跳回主线程执行**（Bukkit API 非线程安全），再阻塞订阅线程等结果。支持动作：run_command / broadcast / get_online_players / kick_player
- 资源：`plugin.yml`（api-version 1.13）、`config.yml`（server-name + redis）

**中心侧 `RemoteCommandTool : AgentTool`**（velocity）
- 把「向子服派发 run_command 并等回包」包装成普通工具 → ReAct 核心零改动（这就是预留的 `McpToolAdapter` 套路的第一个实例）
- `VelocityPlatform` 加 `extraTools` 形参；`WindyAgentVelocityPlugin` 在 `cross-server.enabled` 时建 `RedisBus`+注册 RemoteTool，shutdown 时 close
- 配置：`windyagent-config.yml` 加 `cross-server`（enabled/timeout-ms/redis），`AgentConfig` 加对应读取方法

**构建验证**：`gradlew :velocity:shadowJar :bukkit:shadowJar` 通过
- velocity jar：RemoteCommandTool + RedisBus + jedis + anthropic + velocity-plugin.json ✓
- bukkit jar：plugin.yml + config.yml + Handler + RedisBus + jedis + kotlin ✓，**不含** core/anthropic/llm ✓（Java 8，无版本冲突）

### ⚠️ 待办/隐患
1. **`windyagent-config.yml` 模板里写了真实 api-key（`tp-...`）会随 jar 分发 → 凭证泄漏**，应改回占位空串，密钥只放服主本地数据目录
2. **实机联调**：需 Redis + 一个 Velocity + 一个 Spigot 子服，验证 `!ai 在 earth 子服给我钻石` → RemoteCommandTool → Redis → 子服执行 give。本地无 Redis/服务端，未跑通运行时
3. 子服侧能力目前只有 4 个动作；后续按需加（给钱/经济用 Vault → 那才考虑 Economy MCP）

### 已完成：知识库检索基础（RAG 结构化优先起步）✅
**背景决策**：用户提出商业变现，但随即意识到"先接支付/硬编 SKU 是本末倒置"，真正要的是让 Agent 有**自动商品决策能力**，而那依赖 RAG 知识库。结论：把 Phase 3 的 RAG/Knowledge 地基提前；变现(Phase 4)留后。本轮先做知识库底座。
- **工程取向：结构化优先，不急上向量库**。初期知识量小且结构化，关键词检索足够、且确定可审计；将来量大再换向量检索，靠 `KnowledgeStore` 抽象 + `AgentTool` 包装实现零改动切换（同 MCP/跨服 RemoteTool 套路）。
- core 新增 `knowledge` 包（平台无关）：
  - `KnowledgeEntry`：一条知识（id/title/content/tags）
  - `KnowledgeStore`：检索抽象接口（`search(query, topK)` + `size()`）
  - `KeywordKnowledgeStore`：关键词加权打分（标题×3/标签×2/正文×1）；**中文无空格 → 对连续中文段取相邻二元组**，让「春节礼包」类查询能命中
  - `KnowledgeLoader`：从 `<dataDir>/knowledge/*.md` 加载，每文件一条，支持 frontmatter（title/tags）；首启动自动建目录 + 写示例
  - `KnowledgeSearchTool : AgentTool`：`knowledge_search`，给 Agent 检索商品/价格/规则/玩法
- 接线：`SystemPrompt` 加一条「需要服务器特定信息先 knowledge_search、不要编造」；`WindyAgentVelocityPlugin` 启动时 `KnowledgeLoader.load`，有知识则把工具加入 `extraTools`
- **构建验证**：`:velocity:shadowJar` 通过，`org/windy/windyagent/knowledge/*` 全部进包

### 下一步候选
1. **决策工具**：在知识库之上加 `propose_offer`（Agent 设计商品/定价提案）+ 提案→人工审批闸（钱相关不全自动）
2. **LongTermMemory**：把 SessionManager 的短期记忆补上长期记忆
3. Phase 2 平台接入：QQ/XingtuBot（OneBot）+ QQ↔UUID 绑定、WebSocket、AgentRouter
4. Phase 4 变现收尾：支付适配器 + 订单状态机（待用户定支付通道后）

---

## 2026-06-12

### 本日主题
demo 跑通后打磨触发方式、prompt 架构与本地化，明确 MCP 的取舍。

### 完成的事

#### 1. 新增控制台触发（命令）
- 原来只有 `PlayerChatEvent` → **只有游戏内玩家**能触发，控制台不行
- 新增 `AgentCommand.kt`（Velocity `RawCommand`），控制台 + 玩家都能用
  - 回复直接发回 `invocation.source()`，不走 `platform.sendResponse`（后者按玩家名查在线玩家，控制台没有对应 Player 收不到）
  - 控制台用固定会话 id `"console"` 维持上下文
  - 命令名由 `trigger` 去掉前缀符号得出：`!ai` → `/ai`（控制台不带斜杠 `ai <消息>`）
- 聊天监听 `!ai` 保留不动 → 现在两种入口并存

#### 2. system prompt 按「平台无关」原则重构
- 之前 prompt 焊死在 `VelocityPlatform`，违背 readme 设计原则
- 拆三层：
  - `agent/SystemPrompt.kt`（**核心通用**）：身份 + 工具纪律 + 安全准则，所有载体共用
  - `platform/Platform.kt`（接口）：`systemPrompt` 默认 = `SystemPrompt.build(platformContext)`，载体不必重写
  - `VelocityPlatform`（载体）：只留 `platformContext`（「你跑在 Velocity 代理上」）
- 将来加 QQ/Web/CLI 载体，各写一句 `platformContext` 即可，通用部分自动继承

#### 3. 修复「一句你好就乱调工具」
- 现象：发「你好」模型主动调 `get_server_info` + `get_online_players`
- 根因：prompt 没约束「何时不该用工具」
- 在核心 `SystemPrompt` 加「工具使用规则」：闲聊/问候直接回答，不许为保险/凑数据调工具

#### 4. 全量 prompt 中文化（面向国内服主 + mimo 模型）
- 系统提示、`platformContext`、三个工具的 `description` + 参数 schema 描述、工具返回文本 → 全中文
- 运行时文案（命令用法/正在处理/出错、`ReActAgent` 兜底）→ 全中文
- `SystemPrompt` 加硬约束「请始终使用简体中文回复」
- 启动日志（`logger`）保留英文（运维向，非 prompt）

#### 5. 关键决策：MCP 暂不实现，留到跨进程能力阶段
- **现状**：工具走自定义 `AgentTool` 接口，**进程内直连** `ProxyServer` API，无任何 MCP client/server。readme 里的 `MCP Client` / `MCP Servers 层` 目前全是规划，未落地。
- **决定**：现在不上 MCP，是有意为之。
  - MCP 的价值在「跨进程标准化」，不在「单进程调函数」
  - Velocity 自身工具（广播/踢人/查状态）同进程直连最快，套 MCP 只是多一层 JSON-RPC 负担
  - 跨进程能力（Economy / Payment 调 SakuraPurchase / 跨子服指令 / 服主外接 Skill）才值得上 MCP，对应 **Phase 3/4**
- **预留口子**：`AgentTool` 抽象已够干净，未来只需写 `McpToolAdapter : AgentTool` 把远端 MCP tool 包装成本地工具喂给 `ReActAgent`，核心循环零改动。

#### 6. 补齐 KickPlayerTool + Phase 1 收尾
- 新增 `KickPlayerTool.kt`（Phase 1 清单点名的踢人工具），按玩家名 `disconnect`，支持理由参数，已注册进 `VelocityPlatform.tools`
- `gradlew shadowJar` 编译通过，产物 `build/libs/windyagent-1.0-SNAPSHOT.jar`（30MB fat jar）
  - 验证：`AgentCommand` / `KickPlayerTool` 类在包内，`velocity-plugin.json` + 配置模板在根目录，依赖（anthropic/okhttp/kotlin/snakeyaml）全部 shade 进去

### Phase 1 验收结论
**核心主干达标**：收消息（聊天/控制台）→ LLM（mimo/Claude/Ollama 可换）→ ReAct 工具循环（broadcast/查在线/查服务器/踢人）→ 中文返回，编译出包通过。
- 主动推迟项（不算欠债）：MCP（→Phase 3/4）、多模块 Gradle + API Gateway + CLI（→并入 Phase 2 结构性前置自然解决）
- 仅剩人工动作：把 jar 丢进真实 Velocity + 填 mimo key 跑一遍实机（昨天的实机 TODO）

### Phase 2 起点（下次）
1. 实机验证今天的 jar（控制台 `/ai`、你好不再乱查、中文、踢人）
2. 开 Phase 2：多模块拆分（core/velocity/gateway）+ QQ/XingtuBot 接入 + Redis 跨服

---

## 2026-06-11

### 本日主题
确定测试载体策略、全面切换 Kotlin、打通 Velocity (Java 11) 构建链路。

### 完成的事

#### 1. 配置方式：环境变量 → YAML 配置文件
- 放弃了环境变量 / JVM `-D` 参数方案（对 B 端服主不友好）
- 改为 Velocity 标准做法：`resources/windyagent-config.yml` 默认模板，首启动自动释放到数据目录
  - Velocity：`plugins/windyagent/windyagent-config.yml`
  - NeoForge：`config/windyagent/windyagent-config.yml`（载体搁置后暂不生效）
- `AgentConfig.kt` 用 SnakeYAML 读取，支持点路径取值（`llm.api-key` 等）

#### 2. LLM Provider 三选一
| provider | 实现 | 协议 | 备注 |
|---|---|---|---|
| `claude` | `ClaudeProvider` | Anthropic 原生 | 走 anthropic-java SDK，支持 `api-base-url` 代理 |
| `openai` | `OpenAICompatProvider` | OpenAI 兼容 | **新增**，给 mimo / 讯飞 / 智谱等 |
| `ollama` | `OllamaProvider` | OpenAI 兼容 | 本地 |
- 测试环境定了用小米 **mimo-v2.5-pro**，端点 `https://token-plan-cn.xiaomimimo.com/v1`（OpenAI 协议）
- `OpenAICompatProvider` 处理了 baseUrl 末尾 `/v1` 拼接、Bearer 鉴权

#### 3. 全量 Java → Kotlin
- 28 个文件全部改写为 `.kt`，删光 `.java`
- `record` → `data class`、`switch` → `when`、try-catch → `runCatching`
- `buildProvider()` 提取为顶层函数放 `ProviderFactory.kt`，两个载体共用

#### 4. 关键架构决策：现阶段只做 Velocity (Java 11)
**冲突**：NeoForge 26.1.2.75 强制 **Java 25**，但目标用户的 Velocity 群组端可能只有 **Java 11**。Java 11 JVM 无法加载 Java 25 字节码 → 单 JAR 双载体在此版本组合下走不通。

**决定**：现阶段只做 Velocity，NeoForge 搁置（可逆）。
- `platform/neoforge/` + `Windyagent.kt`（@Mod 入口）→ 移到 `disabled-neoforge/`（未删）
- 原 NeoForge 构建配置 → 备份为 `build.gradle.neoforge-backup`

#### 5. 构建链路修复（Velocity / Java 11）
- `build.gradle` 重写：移除 NeoForge moddev 插件，改纯 Velocity + shadow 构建
- 依赖打包：Velocity 不自动下依赖，必须 shade（anthropic SDK / okhttp / jackson / snakeyaml / kotlin-stdlib），`velocity-api` 改 `compileOnly`
- 已验证 anthropic-java 2.34.0 依赖 `kotlin-stdlib-jdk8` → Java 8 baseline → Java 11 可运行
- 一连串版本对齐：
  - Kotlin `2.1.20` → `2.2.20`（支持 JDK 25，虽然现在 toolchain 是 11）
  - toolchain `25` → `11`
  - 新增 `com.gradleup.shadow 9.4.2`（要求 Gradle 9+）
  - Gradle wrapper `8.4.1`（无效版本号）→ `9.5.1`
  - foojay-resolver `0.8.0` → `1.0.0`（0.8.0 引用了 Gradle 9 已移除的 `JvmVendorSpec.IBM_SEMERU`）
  - IDEA 的 **Gradle JVM** 用本机 Zulu JDK 21（Gradle 9 要求 JDK 17+；toolchain 的 Java 11 由 foojay 自动下载）

### 明天继续（TODO）
1. **跑通构建**：IDEA Reload 同步后 `gradlew shadowJar`，确认产物 `build/libs/windyagent-1.0-SNAPSHOT.jar`
   - 若还有报错继续排（重点关注 shadow 打包、Kotlin 编译）
2. **实机测试 Velocity**：jar 丢进 `plugins/`，填 mimo key，游戏内发 `!ai <消息>` 验证 ReAct + 工具调用（broadcast / get_online_players / get_server_info）
3. 测试通过后，规划 Phase 2（QQ/XingtuBot、Redis 跨服、支付等）

### 待办备忘
- NeoForge 载体何时恢复：需要时单独建 Java 25 构建产物，core 编 Java 11、NeoForge 编 Java 25 各出一个 jar
- 残留的 NeoForge 资源（`windyagent.mixins.json`、`assets/.../en_us.json`）对 Velocity 无害，暂留
