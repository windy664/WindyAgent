// ============================================================
// 双链（wikilink）支持：把知识库当 Obsidian vault 用。
// - 维护「笔记名 → id」索引（供渲染解析 + Monaco `[[` 自动补全）。
// - 注册一个 marked 内联扩展，把 `[[标题]]` / `[[标题|显示文字]]` 渲染成可点击链接
//   （解析得到走 .wikilink，解析不到走 .wikilink.missing，点击可新建）。
// - 提供 parseWikiTargets：从正文抽出所有双链目标，供反向链接 / 关系图计算。
// 单一来源，KnowledgePanel / MonacoEditor / KnowledgeGraph 共用。
// ============================================================
import { marked } from 'marked'

export interface NoteRef {
  id: string
  title: string
}

// name(小写去空格) → id。标题与 id 末段（文件名）都建索引，尽量命中。
let nameToId = new Map<string, string>()
// 供 Monaco 补全用的「当前全部标题」，export let = 实时绑定，读取即取最新值。
export let noteTitles: string[] = []

export function setNoteIndex(entries: NoteRef[]): void {
  const m = new Map<string, string>()
  for (const e of entries) {
    const key = norm(e.title)
    if (key) m.set(key, e.id)
    const base = norm(e.id.split('/').pop() || e.id)
    if (base && !m.has(base)) m.set(base, e.id)
  }
  nameToId = m
  noteTitles = entries.map((e) => e.title).sort((a, b) => a.localeCompare(b))
}

export function resolveWikiName(name: string): string | null {
  return nameToId.get(norm(name)) ?? null
}

/** 抽出正文里所有双链目标（原始文本，未解析）。 */
export function parseWikiTargets(content: string): string[] {
  const out: string[] = []
  const re = /\[\[([^\]|\n]+?)(?:\|[^\]\n]+?)?\]\]/g
  let m: RegExpExecArray | null
  while ((m = re.exec(content))) out.push(m[1].trim())
  return out
}

function norm(s: string): string {
  return s.toLowerCase().trim()
}

function esc(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

let extRegistered = false
/** 注册 marked 的 wikilink 内联扩展（全局单例，注册一次）。渲染前调用一次即可。 */
export function ensureWikilinkExtension(): void {
  if (extRegistered) return
  // 用 any 规避 marked 严格的 TokenizerExtension 类型（自定义 token 带额外字段）
  const ext: unknown = {
    name: 'wikilink',
    level: 'inline',
    start(src: string) {
      const i = src.indexOf('[[')
      return i < 0 ? undefined : i
    },
    tokenizer(src: string) {
      const m = /^\[\[([^\]|\n]+?)(?:\|([^\]\n]+?))?\]\]/.exec(src)
      if (m) {
        return { type: 'wikilink', raw: m[0], target: m[1].trim(), alias: (m[2] ?? m[1]).trim() }
      }
      return undefined
    },
    renderer(token: { target: string; alias: string }) {
      const id = resolveWikiName(token.target)
      const cls = id ? 'wikilink' : 'wikilink missing'
      return `<a class="${cls}" data-wl="${esc(token.target)}" data-id="${esc(id ?? '')}">${esc(token.alias)}</a>`
    },
  }
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  marked.use({ extensions: [ext as any] })
  extRegistered = true
}
