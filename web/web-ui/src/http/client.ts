// ============================================================
// 请求客户端 —— 带「拦截器管线」的 fetch 封装（工程化请求层）。
//
// 设计目标（对齐企业中后台的 request 封装）：
//   · 请求拦截器：统一注入鉴权头（X-Token）、Content-Type、公共 query 等
//   · 响应拦截器：统一 401 → 触发登出钩子、统一错误出口（错误提示/上报）
//   · 业务层（api.ts）只描述「调哪个接口」，不再各自手搓 header / 401 分支
//
// 与旧实现的兼容：仍导出 apiFetch / apiJson / UnauthorizedError，
// 且 401 仍会抛 UnauthorizedError（老组件的 catch 不受影响），
// 同时新增全局 onUnauthorized 钩子，由 auth store 注册做集中登出。
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

/** 401 专用错误：业务层可 catch 后弹登录门；客户端也会触发全局登出钩子。 */
export class UnauthorizedError extends Error {
  constructor() {
    super('unauthorized')
    this.name = 'UnauthorizedError'
  }
}

/** 一次请求的可变上下文，拦截器按顺序对其加工。 */
export interface RequestContext {
  path: string
  init: RequestInit
  headers: Headers
}

type RequestInterceptor = (ctx: RequestContext) => void | Promise<void>
type ResponseInterceptor = (res: Response, ctx: RequestContext) => void | Promise<void>
type ErrorHandler = (err: unknown, ctx: RequestContext) => void

const requestInterceptors: RequestInterceptor[] = []
const responseInterceptors: ResponseInterceptor[] = []
let unauthorizedHandler: (() => void) | null = null
let errorHandler: ErrorHandler | null = null

export function registerRequestInterceptor(fn: RequestInterceptor): void {
  requestInterceptors.push(fn)
}
export function registerResponseInterceptor(fn: ResponseInterceptor): void {
  responseInterceptors.push(fn)
}
/** 由 auth store 注册：401 时集中登出（清 token + 跳登录）。 */
export function setUnauthorizedHandler(fn: () => void): void {
  unauthorizedHandler = fn
}
/** 主动触发集中登出：供绕过拦截器的请求（如 SSE 流式）复用同一登出路径。 */
export function notifyUnauthorized(): void {
  unauthorizedHandler?.()
}
/** 由 UI 层注册：网络/服务端错误的统一提示出口（如毛玻璃 toast）。 */
export function setErrorHandler(fn: ErrorHandler): void {
  errorHandler = fn
}

/**
 * 安全入口前缀：若控制台经宝塔式「安全入口」访问，浏览器地址是 /<entry>/，
 * 则 API 也必须打到 /<entry>/api/...。从当前页面路径推导前缀（hash 路由下 pathname 即为 /<entry>/）。
 * 未启用安全入口时 pathname 为 /，前缀为空，行为不变。
 */
export function basePrefix(): string {
  // 取 hash 之前的目录部分，去掉结尾文件名（如 index.html）
  let p = window.location.pathname
  const slash = p.lastIndexOf('/')
  if (slash >= 0) p = p.slice(0, slash) // 目录部分
  return p.replace(/\/$/, '') // 去尾斜杠；根路径下得到 ""
}

// ── 默认拦截器 ───────────────────────────────────────────────
// 请求：给绝对 /api 路径补安全入口前缀 + 注入鉴权头。业务层因此无需关心 token 与入口前缀。
registerRequestInterceptor((ctx) => {
  const pre = basePrefix()
  if (pre && ctx.path.startsWith('/api/')) ctx.path = pre + ctx.path
  ctx.headers.set('X-Token', getToken())
})
// 响应：401 统一抛错 + 触发集中登出。
registerResponseInterceptor((res) => {
  if (res.status === 401) {
    unauthorizedHandler?.()
    throw new UnauthorizedError()
  }
})

/** 带拦截器管线的 fetch；401 抛 UnauthorizedError。 */
export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers)
  const ctx: RequestContext = { path, init, headers }
  try {
    for (const fn of requestInterceptors) await fn(ctx)
    const res = await fetch(ctx.path, { ...ctx.init, headers: ctx.headers })
    for (const fn of responseInterceptors) await fn(res, ctx)
    return res
  } catch (err) {
    // UnauthorizedError 是受控登出信号，不当作「错误」上报提示。
    if (!(err instanceof UnauthorizedError)) errorHandler?.(err, ctx)
    throw err
  }
}

/** 便捷 JSON 请求。 */
export async function apiJson<T = unknown>(path: string, init?: RequestInit): Promise<T> {
  const r = await apiFetch(path, init)
  return (await r.json()) as T
}

/** application/json POST 简写：自动补 Content-Type 并序列化 body。 */
export function apiPost<T = unknown>(path: string, body?: unknown, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (body !== undefined && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  return apiJson<T>(path, {
    method: 'POST',
    ...init,
    headers,
    body: body !== undefined ? JSON.stringify(body) : init.body,
  })
}
