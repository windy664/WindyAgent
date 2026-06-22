// ============================================================
// 与后端（DashboardServer / ChatHandler）对接的薄封装。
// 约定全部沿用现有 dashboard.html，零后端改动：
//   - token 存 localStorage['wa_token']，每次请求带 X-Token 头
//   - 401 抛 UnauthorizedError，由 App 捕获后弹登录
//   - 流式：POST /api/chat/stream，响应是 SSE（data: {"text":...} / data: [DONE]）
// ============================================================

const TOKEN_KEY = 'wa_token'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}
export function setToken(t: string): void {
  localStorage.setItem(TOKEN_KEY, t)
}
export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/** 401 专用错误，App 捕获后清 token + 弹登录门。 */
export class UnauthorizedError extends Error {
  constructor() {
    super('unauthorized')
    this.name = 'UnauthorizedError'
  }
}

/** 带 token 的 fetch；401 直接抛 UnauthorizedError。 */
export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers)
  headers.set('X-Token', getToken())
  const r = await fetch(path, { ...init, headers })
  if (r.status === 401) throw new UnauthorizedError()
  return r
}

/** 便捷 JSON GET/POST。 */
export async function apiJson<T = unknown>(path: string, init?: RequestInit): Promise<T> {
  const r = await apiFetch(path, init)
  return (await r.json()) as T
}

/** 校验 token 是否有效：拿一个最轻的鉴权接口探一下。成功=true。 */
export async function verifyToken(token: string): Promise<boolean> {
  const r = await fetch('/api/chat/history?session=web-console', {
    headers: { 'X-Token': token },
  })
  return r.status !== 401 && r.ok
}

export interface ChatLogEntry {
  role: 'u' | 'a'
  text: string
  ts: number
}

/** 拉取某会话的历史聊天记录（后端 role 为 'u'/'a'）。 */
export function fetchHistory(session: string): Promise<ChatLogEntry[]> {
  return apiJson<ChatLogEntry[]>(`/api/chat/history?session=${encodeURIComponent(session)}`)
}

// ── 用量（/api/usage）──────────────────────────────────────
export interface UsageDaily {
  day: string
  inputTokens: number
  outputTokens: number
  calls: number
  totalLatencyMs: number
}
export interface Usage {
  totalCalls: number
  totalInputTokens: number
  totalOutputTokens: number
  totalLatencyMs: number
  daily: UsageDaily[]
}
export function fetchUsage(days = 7): Promise<Usage> {
  return apiJson<Usage>(`/api/usage?days=${days}`)
}

// ── 审批（/api/approvals）──────────────────────────────────
export interface PendingApproval {
  id: string
  desc: string
  at: number
  remainMs: number
}
export interface ApprovalHistory {
  id: string
  desc: string
  decision: string
  result: string
  at: number
}
export interface Approvals {
  pending: PendingApproval[]
  history: ApprovalHistory[]
  ttlMs: number
}
export function fetchApprovals(): Promise<Approvals> {
  return apiJson<Approvals>('/api/approvals')
}
export function approveAction(id: string): Promise<{ result: string }> {
  return apiJson(`/api/approvals/approve?id=${encodeURIComponent(id)}`)
}
export function denyAction(id: string): Promise<{ desc: string }> {
  return apiJson(`/api/approvals/deny?id=${encodeURIComponent(id)}`)
}

// ── 知识库（/api/kb）──────────────────────────────────────
export interface KbEntry {
  id: string
  title: string
  content: string
  tags: string[]
}
export function fetchKb(): Promise<KbEntry[]> {
  return apiJson<KbEntry[]>('/api/kb')
}
export function saveKb(e: { id?: string; title: string; content: string; tags: string[] }): Promise<{ ok: boolean; id: string }> {
  return apiJson('/api/kb', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(e) })
}
export function deleteKb(id: string): Promise<{ ok: boolean }> {
  return apiJson(`/api/kb?id=${encodeURIComponent(id)}`, { method: 'DELETE' })
}
export function draftKb(text: string): Promise<{ title: string; content: string; tags: string[] }> {
  return apiJson('/api/kb/draft', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text }) })
}

// ── 技能（/api/skills）────────────────────────────────────
export interface SkillArg {
  name: string
  type: string
  description: string
}
export interface SkillSummary {
  name: string
  description: string
  handle: string
  type: 'script' | 'text'
  targets: string
  args: SkillArg[]
}
export interface SkillContent {
  handle: string
  isScript: boolean
  md: string
  script: string
  scriptFile: string
  targets: string
}
export function fetchSkills(): Promise<SkillSummary[]> {
  return apiJson<SkillSummary[]>('/api/skills')
}
export function fetchSkillContent(handle: string): Promise<SkillContent> {
  return apiJson<SkillContent>(`/api/skills/content?handle=${encodeURIComponent(handle)}`)
}
export function saveSkill(e: { handle: string; isScript: boolean; md: string; script: string; targets: string }): Promise<{ ok: boolean; count: number; pushed: string }> {
  return apiJson('/api/skills', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(e) })
}
export function deleteSkill(handle: string): Promise<{ ok: boolean }> {
  return apiJson(`/api/skills?handle=${encodeURIComponent(handle)}`, { method: 'DELETE' })
}
export function reloadSkills(): Promise<{ count: number }> {
  return apiJson('/api/skills/reload', { method: 'POST' })
}
export function syncSkills(): Promise<{ result: string }> {
  return apiJson('/api/skills/sync', { method: 'POST' })
}
export function runSkill(skill: string, server: string, args: Record<string, string>): Promise<{ result: string }> {
  return apiJson('/api/skills/run', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ skill, server, args }) })
}
export function draftSkill(text: string): Promise<{ md: string }> {
  return apiJson('/api/skills/draft', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text }) })
}

// ── 已连接子服列表（/api/servers）──────────────────────────
export function fetchServers(): Promise<string[]> {
  return apiJson<string[]>('/api/servers').catch(() => [])
}

// ── 首启/设置向导（/api/setup）─────────────────────────────
export interface SetupState {
  configured: boolean
  provider: string
  model: string
  apiBaseUrl: string
  fastModel: string
  ollamaUrl: string
}
/** 旧 jar 无此接口 → 当作已配置（不挡路）。 */
export function fetchSetupState(): Promise<SetupState> {
  return apiJson<SetupState>('/api/setup/state').catch(
    () => ({ configured: true, provider: '', model: '', apiBaseUrl: '', fastModel: '', ollamaUrl: '' }),
  )
}
export function saveSetup(e: {
  provider: string
  apiKey: string
  model: string
  apiBaseUrl: string
  fastModel: string
}): Promise<{ ok: boolean; restart: boolean }> {
  return apiJson('/api/setup', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(e),
  })
}

// ── 代理状态（/api/status）─────────────────────────────────
export interface ProxyStatus {
  name?: string
  platform?: string
  proxyVersion?: string
  mcVersion?: string
  onlinePlayers?: number
}
export function fetchStatus(): Promise<ProxyStatus> {
  return apiJson<ProxyStatus>('/api/status').catch(() => ({}))
}

// ── 行为看板（/api/stats|board|words|segments|player，带 server）──
// 注：这些端点后端可能未注册（返回 404/unknown），调用方需优雅降级。
export interface BoardStats {
  kpis?: { icon?: string; value?: string | number; label?: string; delta?: string }[]
  trend?: { day: string; peak: number }[]
  behavior?: { label: string; value: number }[]
  segments?: { label: string; value: number }[]
  topCommands?: { word: string; count: number }[]
  topPlaytime?: { name: string; playtimeMin: number }[]
  heat?: number[][]
}
export interface WordItem {
  word: string
  count: number
}
export function fetchBoardStats(server: string): Promise<BoardStats> {
  return apiJson<BoardStats>(`/api/stats?server=${encodeURIComponent(server)}`)
}
export function fetchSegments(server: string): Promise<{ label: string; value: number }[]> {
  return apiJson(`/api/segments?server=${encodeURIComponent(server)}`)
}
export function fetchWords(server: string, source: 'cmd' | 'chat', limit = 120): Promise<WordItem[]> {
  return apiJson(`/api/words?server=${encodeURIComponent(server)}&source=${source}&limit=${limit}`)
}

// ── 充值管理（/api/purchase/*，桥接 WindyPurchase）────────────
export interface PurchaseOrder {
  orderId: string
  playerName: string
  amount: number
  currency: string
  paymentMethod: string
  status: string
  createdAt: number
  paidAt?: number
}
export interface OrdersPage {
  total: number
  page: number
  size: number
  data: PurchaseOrder[]
}
export interface Revenue {
  totalAmount: number
  todayAmount: number
  totalOrders: number
}
export interface RankEntry {
  playerId: string
  playerName: string
  totalAmount: number
}
export function purchaseAvailable(): Promise<{ available: boolean }> {
  return apiJson<{ available: boolean }>('/api/purchase/available').catch(() => ({ available: false }))
}
export function fetchOrders(
  page: number,
  size: number,
  filters: { status?: string; method?: string; player?: string } = {},
): Promise<OrdersPage> {
  const p = new URLSearchParams({ page: String(page), size: String(size) })
  if (filters.status) p.set('status', filters.status)
  if (filters.method) p.set('method', filters.method)
  if (filters.player) p.set('player', filters.player)
  return apiJson<OrdersPage>(`/api/purchase/orders?${p}`)
}
export function closeOrder(id: string): Promise<{ ok: boolean }> {
  return apiJson(`/api/purchase/orders/${encodeURIComponent(id)}/close`, { method: 'POST' })
}
export function refundOrder(id: string): Promise<{ ok: boolean }> {
  return apiJson(`/api/purchase/orders/${encodeURIComponent(id)}/refund`, { method: 'POST' })
}
export function fetchRevenue(): Promise<Revenue> {
  return apiJson<Revenue>('/api/purchase/revenue')
}
export function fetchRanking(limit = 10): Promise<RankEntry[]> {
  return apiJson<RankEntry[]>(`/api/purchase/revenue/ranking?limit=${limit}`)
}
export function grantOrder(player: string, amount: number, commands: string[]): Promise<{ orderId: string; player: string; amount: number }> {
  return apiJson('/api/purchase/grant', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ player, amount, commands }),
  })
}

// ── Ops 看板（ServerHandler）───────────────────────────────
export interface HealthEntry {
  server: string
  status: 'ok' | 'lag' | 'mem' | 'offline'
  connected: boolean
  tps?: number
  players?: number
  memUsedMb?: number
  memMaxMb?: number
  memPct?: number
  platform?: string
  mcVersion?: string
  brand?: string
  modCount?: number
  ts?: number
}
export function fetchHealth(): Promise<HealthEntry[]> {
  return apiJson<HealthEntry[]>('/api/health').catch(() => [])
}

export interface AlertEntry {
  server: string
  kind: string
  severity: string
  detail: string
  advice?: string
  ts: number
}
export function fetchAlerts(): Promise<AlertEntry[]> {
  return apiJson<AlertEntry[]>('/api/alerts').catch(() => [])
}

export interface WorldInfo {
  name: string
  env: string
  timeHM: string
  day: boolean
  weather: string
  entities: number
  chunks: number
  players: number
  difficulty: string
}
export interface PlayerInfo {
  name: string
  world: string
  ping: number
  gamemode: string
}
export interface ServerDetail {
  uptimeSec: number
  online: number
  maxPlayers: number
  memUsedMb: number
  memMaxMb: number
  tps: number
  platform: string
  mcVersion: string
  modCount: number
  pluginCount: number
  whitelist: boolean
  onlineMode: boolean
  viewDistance?: number
  worlds: WorldInfo[]
  players: PlayerInfo[]
}
export function fetchServerDetail(server: string): Promise<ServerDetail> {
  return apiJson<ServerDetail>(`/api/serverdetail?server=${encodeURIComponent(server)}`)
}
export function fetchMods(server: string): Promise<{ text: string }> {
  return apiJson(`/api/mods?server=${encodeURIComponent(server)}`)
}
export function fetchDimTps(server: string): Promise<{ text: string }> {
  return apiJson(`/api/dimtps?server=${encodeURIComponent(server)}`)
}

// ── 定时任务（/api/tasks）─────────────────────────────────
export interface TaskStep {
  action: string
  target: string
  payload: string
}
export interface ScheduledTask {
  id: string
  name: string
  enabled: boolean
  action: string
  target: string
  payload: string
  type: 'interval' | 'daily'
  intervalMin: number
  time: string
  days: number[]
  script: TaskStep[]
  lastRun: number
  lastResult: string
  nextRun: number
}
export function fetchTasks(): Promise<ScheduledTask[]> {
  return apiJson<ScheduledTask[]>('/api/tasks')
}
export function saveTask(t: Partial<ScheduledTask>): Promise<{ ok: boolean; id: string }> {
  return apiJson('/api/tasks', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(t) })
}
export function deleteTask(id: string): Promise<{ ok: boolean }> {
  return apiJson(`/api/tasks?id=${encodeURIComponent(id)}`, { method: 'DELETE' })
}
export function toggleTask(id: string): Promise<{ ok: boolean; enabled: boolean }> {
  return apiJson(`/api/tasks/toggle?id=${encodeURIComponent(id)}`)
}
export function runTask(id: string): Promise<{ result: string }> {
  return apiJson(`/api/tasks/run?id=${encodeURIComponent(id)}`)
}
export function refineTask(text: string): Promise<{ text: string }> {
  return apiJson('/api/tasks/refine', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text }) })
}

/**
 * 流式对话：异步生成器，逐块吐出文本增量。
 * 用法：`for await (const chunk of streamChat(sid, msg)) { reply += chunk }`
 *
 * 直接读 response.body 的 ReadableStream（不用 EventSource——后端是 POST + SSE，
 * EventSource 只支持 GET）。
 */
export async function* streamChat(
  session: string,
  message: string,
  display?: string,
): AsyncGenerator<string, void, void> {
  const resp = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'X-Token': getToken(), 'Content-Type': 'application/json' },
    body: JSON.stringify({ session, message, display: display ?? message }),
  })
  if (resp.status === 401) throw new UnauthorizedError()
  if (!resp.body) throw new Error('no stream body')

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buf = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buf += decoder.decode(value, { stream: true })
    // SSE 以 \n\n 分帧，这里按行解析，跨 chunk 的半行留在 buf 里
    const lines = buf.split('\n')
    buf = lines.pop() ?? ''
    for (const line of lines) {
      if (!line.startsWith('data: ')) continue
      const d = line.slice(6).trim()
      if (d === '[DONE]') return
      if (!d) continue
      let j: { text?: string; error?: string }
      try {
        j = JSON.parse(d)
      } catch {
        continue // 半截 JSON，下一轮再拼
      }
      if (j.error) throw new Error(j.error)
      if (j.text) yield j.text
    }
  }
}
