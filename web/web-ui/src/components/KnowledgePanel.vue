<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { marked } from 'marked'
import { ElButton, ElInput, ElMessage, ElMessageBox, ElTag } from 'element-plus'
import MonacoEditor from './MonacoEditor.vue'
import KnowledgeGraph from './KnowledgeGraph.vue'
import { ensureWikilinkExtension, setNoteIndex } from '../wikilinks'
import {
  aiEditKb,
  deleteKb,
  fetchCapabilities,
  fetchKb,
  fetchKbEntry,
  moveKb,
  saveKb,
  searchKb,
  UnauthorizedError,
  type CapCommand,
  type CapServer,
  type KbEntry,
  type KbMeta,
} from '../api'

ensureWikilinkExtension() // 注册 [[双链]] 的 marked 扩展（全局单例）

const route = useRoute()
const router = useRouter()
const list = ref<KbMeta[]>([]) // 只存元数据（不含正文），正文点开再拉
const error = ref('')
const search = ref('')
const searchHits = ref<Set<string>>(new Set()) // 后端全文检索命中的 id
const selected = ref<KbEntry | null>(null) // 当前查看的条目（含正文，懒加载）
const editing = ref<KbEntry | null>(null)
const caps = ref<CapServer[]>([]) // 插件命令目录（只读，实时）
const selectedCap = ref<{ server: string; plugin: string; name: string; description: string; aliases: string[] } | null>(null)
const tagsText = ref('')
const folderText = ref('')
const saving = ref(false)
const previewMode = ref(false) // 编辑器：false=编辑 true=预览
const showGraph = ref(false) // 主区：false=详情/编辑 true=关系图

// ── AI 编辑 ──
const aiBusy = ref(false)
const aiInstruction = ref('')
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const mona = ref<any>(null) // MonacoEditor 暴露的方法句柄
const AI_ACTIONS = [
  { id: 'polish', label: '✨ 润色' },
  { id: 'continue', label: '✍️ 续写' },
  { id: 'summarize', label: '📝 总结' },
  { id: 'translate', label: '🌐 翻译' },
]

// ── 右键上下文菜单 ──
interface CtxItem {
  label: string
  danger?: boolean
  act: () => void
}
const ctx = ref<{ show: boolean; x: number; y: number; items: CtxItem[] }>({
  show: false,
  x: 0,
  y: 0,
  items: [],
})
function openCtx(ev: MouseEvent, items: CtxItem[]) {
  ctx.value = { show: true, x: ev.clientX, y: ev.clientY, items }
}
function closeCtx() {
  if (ctx.value.show) ctx.value = { ...ctx.value, show: false }
}
function runCtx(item: CtxItem) {
  closeCtx()
  item.act()
}
function folderMenu(path: string): CtxItem[] {
  return [
    { label: '➕ 在此新建条目', act: () => newEntry(path) },
    { label: '📁 新建子文件夹', act: () => newSubfolder(path) },
    { label: '✏️ 重命名', act: () => renameFolder(path) },
    { label: '🗑 删除（含条目）', danger: true, act: () => deleteFolder(path) },
  ]
}
function entryMenu(e: KbMeta): CtxItem[] {
  return [
    { label: '✏️ 编辑', act: () => edit(e) },
    { label: '📂 移动到…', act: () => moveEntry(e) },
    { label: '🗑 删除', danger: true, act: () => remove(e) },
  ]
}

// 反向链接：哪些条目通过 [[双链]] 指向当前选中条目（用元数据 links，无需正文）
const backlinks = computed<KbMeta[]>(() => {
  const cur = selected.value
  if (!cur) return []
  return list.value.filter((e) => e.id !== cur.id && e.links.includes(cur.id))
})
const capTotal = computed(() => caps.value.reduce((n, s) => n + s.plugins.reduce((m, p) => m + p.commands.length, 0), 0))

// ── 目录树折叠状态（按 folder 全路径记 collapsed，默认展开）持久化到 localStorage ──
const COLLAPSE_KEY = 'wa_kb_collapsed'
const collapsedSet = ref<Set<string>>(loadCollapsed())
function loadCollapsed(): Set<string> {
  try {
    return new Set(JSON.parse(localStorage.getItem(COLLAPSE_KEY) || '[]'))
  } catch {
    return new Set()
  }
}
function persistCollapsed() {
  localStorage.setItem(COLLAPSE_KEY, JSON.stringify([...collapsedSet.value]))
}
function toggleFolder(path: string) {
  if (collapsedSet.value.has(path)) collapsedSet.value.delete(path)
  else collapsedSet.value.add(path)
  collapsedSet.value = new Set(collapsedSet.value) // 触发响应式
  persistCollapsed()
}
// 搜索时强制全展开；否则看 collapsedSet
function isExpanded(path: string): boolean {
  return searching.value || !collapsedSet.value.has(path)
}

const searching = computed(() => search.value.trim().length > 0)

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return list.value
  return list.value.filter(
    (e) =>
      e.title.toLowerCase().includes(q) ||
      e.folder.toLowerCase().includes(q) ||
      e.tags.some((t) => t.toLowerCase().includes(q)) ||
      searchHits.value.has(e.id), // 正文命中（后端稀疏检索）
  )
})
// 输入变化 → 后端全文检索（≥2 字，防抖），补客户端匹配不到的正文命中
let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(search, (q) => {
  const s = q.trim()
  clearTimeout(searchTimer)
  if (s.length < 2) {
    searchHits.value = new Set()
    return
  }
  searchTimer = setTimeout(async () => {
    try {
      const hits = await searchKb(s)
      searchHits.value = new Set(hits.map((h) => h.id))
    } catch {
      searchHits.value = new Set()
    }
  }, 220)
})

// ── 递归目录树：把 folder（"/" 分隔的多层路径）构建成真·文件夹树 ──
interface FolderNode {
  name: string
  path: string // 全路径，作为 key
  folders: Map<string, FolderNode>
  entries: KbMeta[] // 直属本层的条目
}
function buildTree(entries: KbMeta[]): FolderNode {
  const root: FolderNode = { name: '', path: '', folders: new Map(), entries: [] }
  for (const e of entries) {
    const segs = (e.folder || '').split('/').map((s) => s.trim()).filter(Boolean)
    let node = root
    let acc = ''
    for (const seg of segs) {
      acc = acc ? `${acc}/${seg}` : seg
      if (!node.folders.has(seg))
        node.folders.set(seg, { name: seg, path: acc, folders: new Map(), entries: [] })
      node = node.folders.get(seg)!
    }
    node.entries.push(e)
  }
  return root
}
function countEntries(node: FolderNode): number {
  let n = node.entries.length
  for (const f of node.folders.values()) n += countEntries(f)
  return n
}

// 扁平化成可渲染行（尊重展开状态）：文件夹在前、条目在后，各自按名排序
type Row =
  | { kind: 'folder'; depth: number; path: string; name: string; count: number }
  | { kind: 'entry'; depth: number; entry: KbMeta }
function flatten(node: FolderNode, depth: number, out: Row[]) {
  const folders = [...node.folders.values()].sort((a, b) => a.name.localeCompare(b.name))
  for (const f of folders) {
    out.push({ kind: 'folder', depth, path: f.path, name: f.name, count: countEntries(f) })
    if (isExpanded(f.path)) flatten(f, depth + 1, out)
  }
  const entries = [...node.entries].sort((a, b) => a.title.localeCompare(b.title))
  for (const e of entries) out.push({ kind: 'entry', depth, entry: e })
}
const rows = computed<Row[]>(() => {
  const out: Row[] = []
  flatten(buildTree(filtered.value), 0, out)
  return out
})

// markdown 渲染（详情 + 编辑器预览共用）
function renderMd(text: string): string {
  return marked.parse(text || '', { async: false }) as string
}

function handle(e: unknown) {
  if (e instanceof UnauthorizedError) return
  error.value = (e as Error).message
}

async function load() {
  try {
    list.value = await fetchKb()
    setNoteIndex(list.value) // 刷新双链名称索引（渲染解析 + Monaco 补全共用）
    error.value = ''
  } catch (e) {
    handle(e)
  }
}
async function loadCaps() {
  try {
    caps.value = await fetchCapabilities()
  } catch {
    caps.value = []
  }
}

// URL 是选中条目的唯一真相：select = push /kb/<id>；真正取正文在 route 监听里做（刷新/书签可直达）
function routeId(): string {
  const raw = route.params.id
  return (Array.isArray(raw) ? raw[0] : raw) || ''
}
function select(idOrMeta: string | KbMeta) {
  const id = typeof idOrMeta === 'string' ? idOrMeta : idOrMeta.id
  if (routeId() === id) {
    if (selected.value?.id !== id) loadEntry(id)
    return
  }
  router.push({ name: 'kb', params: { id } })
}
async function loadEntry(id: string) {
  editing.value = null
  selectedCap.value = null
  showGraph.value = false
  try {
    selected.value = await fetchKbEntry(id)
  } catch (err) {
    handle(err)
  }
}
watch(
  () => route.params.id,
  (raw) => {
    const id = Array.isArray(raw) ? raw[0] : raw
    if (id) loadEntry(String(id))
    else selected.value = null
  },
)

// 点击 [[双链]]：解析到 → 跳转；解析不到（缺失）→ 以该名称新建（沿用当前分类）
function onWikiClick(ev: MouseEvent) {
  const a = (ev.target as HTMLElement).closest('a.wikilink') as HTMLElement | null
  if (!a) return
  ev.preventDefault()
  const id = a.getAttribute('data-id')
  if (id) {
    select(id)
    return
  }
  const name = a.getAttribute('data-wl') || ''
  newEntry(selected.value?.folder || '')
  if (editing.value) editing.value.title = name
}

function onGraphSelect(id: string) {
  select(id)
}

// 插件命令（只读）
function selectCap(server: string, plugin: string, c: CapCommand) {
  selectedCap.value = { server, plugin, name: c.name, description: c.description, aliases: c.aliases }
  selected.value = null
  editing.value = null
  showGraph.value = false
}

function newEntry(folder = '') {
  editing.value = { id: '', title: '', content: '', tags: [], folder }
  selected.value = null
  selectedCap.value = null
  tagsText.value = ''
  folderText.value = folder
  previewMode.value = false
}
async function edit(m: KbMeta | KbEntry) {
  try {
    const full: KbEntry = 'content' in m ? m : await fetchKbEntry(m.id)
    editing.value = { ...full }
    tagsText.value = full.tags.join(', ')
    folderText.value = full.folder || ''
    previewMode.value = false
    selected.value = null
    selectedCap.value = null
  } catch (err) {
    handle(err)
  }
}
function cancel() {
  editing.value = null
}

async function save() {
  const e = editing.value
  if (!e || !e.title.trim()) return
  const tags = tagsText.value.split(/[,，]/).map((t) => t.trim()).filter(Boolean)
  saving.value = true
  try {
    const { id } = await saveKb({ id: e.id || undefined, title: e.title, content: e.content, tags, folder: folderText.value.trim() })
    editing.value = null
    ElMessage.success('已保存')
    await load()
    select(id) // 保存后跳到该条（URL 同步）
  } catch (err) {
    handle(err)
  } finally {
    saving.value = false
  }
}

async function remove(e: KbMeta | KbEntry) {
  try {
    await ElMessageBox.confirm(`确认删除知识「${e.title}」？`, '删除条目', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteKb(e.id)
    if (selected.value?.id === e.id) {
      selected.value = null
      router.push({ name: 'kb' })
    }
    ElMessage.success('已删除')
    await load()
  } catch (err) {
    handle(err)
  }
}

// AI 编辑：action=预置动作（空=纯自定义需求）。有选区只改选区，否则作用于全文；续写走末尾追加。
async function runAi(action: string, instruction = '') {
  const e = editing.value
  if (!e) return
  if (!action && !instruction.trim()) return
  const sel = (mona.value?.getSelection?.() as string) || ''
  const text = sel || e.content || ''
  if (!text.trim() && !instruction.trim()) {
    ElMessage.warning('没有可处理的内容（先写点正文，或填写自定义需求）')
    return
  }
  aiBusy.value = true
  try {
    const { result } = await aiEditKb(action, instruction, text)
    if (!result) return
    if (sel) mona.value?.replaceSelection?.(result)
    else if (action === 'continue') mona.value?.insertAtEnd?.(result)
    else e.content = result // 整体替换（v-model 同步进编辑器）
    ElMessage.success('AI 已处理')
  } catch (err) {
    handle(err)
  } finally {
    aiBusy.value = false
  }
}
function runCustomAi() {
  const instr = aiInstruction.value.trim()
  if (!instr) return
  runAi('', instr)
  aiInstruction.value = ''
}
function onEditorAi(id: string) {
  runAi(id)
}

// ── 文件夹 / 条目管理（右键菜单调用）──
async function newSubfolder(parent: string) {
  try {
    const { value } = await ElMessageBox.prompt('子文件夹名称', '新建子文件夹', {
      confirmButtonText: '创建',
      cancelButtonText: '取消',
    })
    const name = (value || '').trim()
    if (name) newEntry(parent ? `${parent}/${name}` : name)
  } catch {
    /* 取消 */
  }
}
async function renameFolder(path: string) {
  const base = path.split('/').pop() || path
  let name = ''
  try {
    const { value } = await ElMessageBox.prompt('新的文件夹名', '重命名文件夹', {
      inputValue: base,
      confirmButtonText: '重命名',
      cancelButtonText: '取消',
    })
    name = (value || '').trim()
  } catch {
    return
  }
  if (!name || name === base) return
  const parent = path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : ''
  const newPath = parent ? `${parent}/${name}` : name
  const affected = list.value.filter((e) => e.folder === path || e.folder.startsWith(path + '/'))
  const selId = selected.value?.id
  try {
    for (const e of affected) {
      const nf = newPath + e.folder.slice(path.length) // 保留子层级；move 不改正文
      await moveKb(e.id, nf)
    }
    ElMessage.success('已重命名')
    if (selId && affected.some((a) => a.id === selId)) {
      selected.value = null
      router.push({ name: 'kb' })
    }
    await load()
  } catch (err) {
    handle(err)
  }
}
async function deleteFolder(path: string) {
  const affected = list.value.filter((e) => e.folder === path || e.folder.startsWith(path + '/'))
  try {
    await ElMessageBox.confirm(`确认删除文件夹「${path}」及其下 ${affected.length} 条知识？`, '删除文件夹', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  const selId = selected.value?.id
  try {
    for (const e of affected) await deleteKb(e.id)
    if (selId && affected.some((e) => e.id === selId)) {
      selected.value = null
      router.push({ name: 'kb' })
    }
    ElMessage.success('已删除')
    await load()
  } catch (err) {
    handle(err)
  }
}
async function moveEntry(e: KbMeta) {
  let folder = ''
  try {
    const { value } = await ElMessageBox.prompt('目标分类（目录，支持多层如 活动/2026；留空=根）', '移动到', {
      inputValue: e.folder || '',
      confirmButtonText: '移动',
      cancelButtonText: '取消',
    })
    folder = (value || '').trim()
  } catch {
    return
  }
  try {
    const { id: newId } = await moveKb(e.id, folder) // 不改正文
    ElMessage.success('已移动')
    await load()
    if (selected.value?.id === e.id) select(newId)
  } catch (err) {
    handle(err)
  }
}

onMounted(async () => {
  document.addEventListener('click', closeCtx)
  // 「插件命令」根首次默认折叠（之后尊重用户展开/收起的持久化）
  if (!localStorage.getItem('wa_kb_caps_init')) {
    collapsedSet.value = new Set([...collapsedSet.value, 'cap:'])
    persistCollapsed()
    localStorage.setItem('wa_kb_caps_init', '1')
  }
  await load()
  loadCaps()
  const id = routeId()
  if (id) loadEntry(id) // 刷新/直达 /kb/<路径> 时把该条正文拉出来
})
onBeforeUnmount(() => document.removeEventListener('click', closeCtx))
</script>

<template>
  <div class="kbwrap">
    <!-- 左侧：真·递归目录树（资源管理器式）-->
    <div class="kbside glass">
      <div class="kbside-head">
        <el-input v-model="search" placeholder="🔍 搜索标题 / 标签 / 分类 / 内容" clearable />
        <button class="rootadd" title="新建条目（根目录）" @click="newEntry()">＋</button>
      </div>
      <div class="kbhint muted">右键文件夹 / 条目：新建 · 重命名 · 移动 · 删除</div>

      <!-- 插件命令（只读，实时来自各子服能力目录）-->
      <div v-if="caps.length" class="captree">
        <div class="kbfolder caproot" @click="toggleFolder('cap:')">
          <span class="caret">{{ isExpanded('cap:') ? '▾' : '▸' }}</span>
          <span class="fico">📟</span>
          <span class="fname">插件命令</span>
          <span class="fcount">{{ capTotal }}</span>
        </div>
        <template v-if="isExpanded('cap:')">
          <template v-for="sv in caps" :key="'s-' + sv.server">
            <div class="kbfolder" :style="{ paddingLeft: '20px' }" @click="toggleFolder('cap:' + sv.server)">
              <span class="caret">{{ isExpanded('cap:' + sv.server) ? '▾' : '▸' }}</span>
              <span class="fico">🖥</span>
              <span class="fname">{{ sv.server }}</span>
            </div>
            <template v-if="isExpanded('cap:' + sv.server)">
              <template v-for="pl in sv.plugins" :key="'p-' + sv.server + '-' + pl.plugin">
                <div class="kbfolder" :style="{ paddingLeft: '34px' }" @click="toggleFolder('cap:' + sv.server + '/' + pl.plugin)">
                  <span class="caret">{{ isExpanded('cap:' + sv.server + '/' + pl.plugin) ? '▾' : '▸' }}</span>
                  <span class="fico">🧩</span>
                  <span class="fname">{{ pl.plugin }}</span>
                  <span class="fcount">{{ pl.commands.length }}</span>
                </div>
                <template v-if="isExpanded('cap:' + sv.server + '/' + pl.plugin)">
                  <div
                    v-for="c in pl.commands"
                    :key="c.name"
                    class="kbitem"
                    :class="{ on: selectedCap && selectedCap.server === sv.server && selectedCap.name === c.name }"
                    :style="{ paddingLeft: '44px' }"
                    @click="selectCap(sv.server, pl.plugin, c)"
                  >
                    <span class="dico">⌘</span>
                    <span class="it">{{ c.name }}</span>
                  </div>
                </template>
              </template>
            </template>
          </template>
        </template>
        <div class="capdiv"></div>
      </div>

      <div class="kbtree">
        <template v-for="(row, i) in rows" :key="i">
          <!-- 文件夹行 -->
          <div
            v-if="row.kind === 'folder'"
            class="kbfolder"
            :style="{ paddingLeft: 6 + row.depth * 14 + 'px' }"
            @click="toggleFolder(row.path)"
            @contextmenu.prevent.stop="openCtx($event, folderMenu(row.path))"
          >
            <span class="caret">{{ isExpanded(row.path) ? '▾' : '▸' }}</span>
            <span class="fico">{{ isExpanded(row.path) ? '📂' : '📁' }}</span>
            <span class="fname">{{ row.name }}</span>
            <span class="fcount">{{ row.count }}</span>
            <span class="fadd" title="在此文件夹新建" @click.stop="newEntry(row.path)">＋</span>
          </div>
          <!-- 条目行 -->
          <div
            v-else
            :class="['kbitem', { on: selected?.id === row.entry.id }]"
            :style="{ paddingLeft: 10 + row.depth * 14 + 'px' }"
            @click="select(row.entry)"
            @contextmenu.prevent.stop="openCtx($event, entryMenu(row.entry))"
          >
            <span class="dico">📄</span>
            <span class="it">{{ row.entry.title }}</span>
          </div>
        </template>
        <div v-if="rows.length === 0" class="muted" style="padding: 16px; font-size: 13px">无条目</div>
      </div>
    </div>

    <div class="kbmain">
      <div class="kbtoolbar">
        <el-button size="small" :type="showGraph ? 'primary' : ''" @click="showGraph = !showGraph">
          {{ showGraph ? '← 返回编辑' : '🕸 关系图' }}
        </el-button>
      </div>
      <p v-if="error" class="err">{{ error }}</p>

      <!-- 关系图（高仿 Obsidian graph view）-->
      <div v-if="showGraph" class="panel glass graphpanel">
        <KnowledgeGraph :entries="list" :selected-id="selected?.id" @select="onGraphSelect" />
      </div>

      <!-- 编辑 / 新建 -->
      <template v-else>
      <div v-if="editing" class="panel glass">
        <h3>{{ editing.id ? '编辑条目' : '新建条目' }}</h3>
        <div class="frow2">
          <div style="flex: 1">
            <label class="muted lb">标题</label>
            <el-input v-model="editing.title" placeholder="如：会员权益" />
          </div>
          <div style="flex: 1">
            <label class="muted lb">分类（目录，支持 活动/2026 多层）</label>
            <el-input v-model="folderText" placeholder="如：规则 / FAQ / 活动/2026" />
          </div>
        </div>
        <label class="muted lb">标签（逗号分隔）</label>
        <el-input v-model="tagsText" placeholder="会员, 价格, vip" />

        <div class="mdhead">
          <label class="muted lb" style="margin: 0">正文（Markdown）</label>
          <div class="mdtabs">
            <button :class="['mdtab', { on: !previewMode }]" @click="previewMode = false">✏️ 编辑</button>
            <button :class="['mdtab', { on: previewMode }]" @click="previewMode = true">👁 预览</button>
          </div>
        </div>
        <div class="aibar">
          <span class="ailabel">🤖 AI</span>
          <el-button v-for="a in AI_ACTIONS" :key="a.id" size="small" :loading="aiBusy" @click="runAi(a.id)">{{ a.label }}</el-button>
          <el-input
            v-model="aiInstruction"
            size="small"
            class="aiinput"
            placeholder="对选中/全文提需求，如：更正式的口吻、补一条退款 FAQ…"
            @keyup.enter="runCustomAi"
          />
          <el-button size="small" type="primary" :loading="aiBusy" :disabled="!aiInstruction.trim()" @click="runCustomAi">执行</el-button>
        </div>
        <div class="aihint muted">选中一段只改这段，不选则作用于全文；编辑器内右键也有这些 AI 动作。</div>
        <MonacoEditor
          v-show="!previewMode"
          ref="mona"
          v-model="editing.content"
          :ai-actions="AI_ACTIONS"
          height="clamp(300px, calc(100vh - 490px), 620px)"
          @ai="onEditorAi"
        />
        <div v-show="previewMode" class="mdpreview panel" v-html="renderMd(editing.content)" @click="onWikiClick"></div>

        <div style="margin-top: 12px; display: flex; gap: 8px">
          <el-button type="primary" :loading="saving" :disabled="!editing.title.trim()" @click="save">保存</el-button>
          <el-button @click="cancel">取消</el-button>
        </div>
      </div>

      <!-- 插件命令详情（只读，实时）-->
      <div v-else-if="selectedCap" class="panel glass kbdetail">
        <h3>
          <span>⌘ {{ selectedCap.name }}</span>
          <el-tag size="small" type="info" effect="plain" round>只读 · 实时</el-tag>
        </h3>
        <div class="meta">
          <el-tag size="small" effect="plain" round style="margin-right: 6px">🖥 {{ selectedCap.server }}</el-tag>
          <el-tag size="small" effect="plain" round style="margin-right: 6px">🧩 {{ selectedCap.plugin }}</el-tag>
        </div>
        <p v-if="selectedCap.aliases.length" class="muted" style="margin: 0 0 10px">别名：{{ selectedCap.aliases.join('、') }}</p>
        <div class="mdbody">{{ selectedCap.description || '（该命令暂无描述）' }}</div>
      </div>

      <!-- 详情（markdown 渲染）-->
      <div v-else-if="selected" class="panel glass kbdetail">
        <h3>
          <span>{{ selected.title }}</span>
          <span>
            <el-button size="small" @click="edit(selected)">编辑</el-button>
            <el-button size="small" @click="remove(selected)">删除</el-button>
          </span>
        </h3>
        <div class="meta">
          <el-tag v-if="selected.folder" size="small" type="info" effect="plain" round style="margin-right: 6px">📁 {{ selected.folder }}</el-tag>
          <el-tag v-for="t in selected.tags" :key="t" size="small" effect="plain" round style="margin-right: 6px">{{ t }}</el-tag>
        </div>
        <div class="mdbody" v-html="renderMd(selected.content)" @click="onWikiClick"></div>
        <div v-if="backlinks.length" class="backlinks">
          <div class="bltitle">🔗 反向链接（{{ backlinks.length }}）</div>
          <div v-for="b in backlinks" :key="b.id" class="blitem" @click="select(b)">📄 {{ b.title }}</div>
        </div>
      </div>

      <!-- 空 -->
      <div v-else class="panel glass">
        <p class="muted">← 从左侧目录选一条查看；右键文件夹 / 条目可新建、重命名、移动、删除；「＋」新建根目录条目。</p>
      </div>
      </template>
    </div>

    <!-- 右键上下文菜单 -->
    <div
      v-if="ctx.show"
      class="ctxmenu glass"
      :style="{ left: ctx.x + 'px', top: ctx.y + 'px' }"
      @click.stop
      @contextmenu.prevent
    >
      <div
        v-for="(it, i) in ctx.items"
        :key="i"
        :class="['ctxitem', { danger: it.danger }]"
        @click="runCtx(it)"
      >
        {{ it.label }}
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 目录树（资源管理器式：文件夹可无限层级缩进）*/
.kbtree {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.kbfolder {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 8px;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 700;
  font-size: 13px;
  color: var(--fg);
  user-select: none;
}
.kbfolder:hover {
  background: var(--glass2);
}
.kbfolder .caret {
  width: 11px;
  color: var(--mut);
  font-size: 10px;
  flex-shrink: 0;
}
.kbfolder .fico {
  flex-shrink: 0;
}
.kbfolder .fname {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kbfolder .fcount {
  font-size: 11px;
  color: var(--mut);
  background: var(--glass2);
  border-radius: 10px;
  padding: 0 7px;
}
.kbfolder .fadd {
  opacity: 0;
  color: var(--violet);
  font-weight: 800;
  font-size: 15px;
  width: 16px;
  text-align: center;
  transition: opacity 0.12s;
}
.kbfolder:hover .fadd {
  opacity: 1;
}
.kbfolder .fadd:hover {
  color: var(--pink);
}
.kbitem {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 8px;
  cursor: pointer;
}
.kbitem:hover {
  background: var(--glass2);
}
.kbitem.on {
  background: linear-gradient(120deg, rgba(255, 143, 200, 0.22), rgba(183, 155, 255, 0.22));
  box-shadow: inset 0 0 0 1px var(--bd);
}
.kbitem .dico {
  flex-shrink: 0;
  font-size: 13px;
}
.kbitem .it {
  font-weight: 600;
  font-size: 13.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 编辑器 */
.lb {
  font-size: 12px;
  display: block;
  margin-top: 8px;
}
.frow2 {
  display: flex;
  gap: 10px;
}
.mdhead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  margin-bottom: 6px;
}
.mdtabs {
  display: flex;
  gap: 4px;
}
.mdtab {
  font: inherit;
  font-size: 12px;
  padding: 3px 12px;
  border-radius: 8px;
  border: 1px solid var(--bd);
  background: var(--glass2);
  color: var(--mut);
  cursor: pointer;
}
.mdtab.on {
  background: linear-gradient(120deg, rgba(255, 143, 200, 0.28), rgba(183, 155, 255, 0.28));
  color: #fff;
  border-color: var(--violet);
}
.mdpreview {
  min-height: 200px;
  max-height: 60vh;
  overflow: auto;
  margin-top: 0;
  border-radius: 12px;
}

/* 侧栏头部：搜索 + 极简＋（去掉了原底部大按钮）*/
.kbside-head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.rootadd {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid var(--bd);
  background: var(--glass2);
  color: var(--violet);
  font-size: 20px;
  font-weight: 800;
  line-height: 1;
  cursor: pointer;
}
.rootadd:hover {
  background: linear-gradient(120deg, rgba(255, 143, 200, 0.28), rgba(183, 155, 255, 0.28));
  color: #fff;
  border-color: var(--violet);
}
.kbhint {
  font-size: 11px;
  margin: 6px 2px 8px;
  opacity: 0.75;
}

/* 插件命令只读虚拟目录 */
.captree {
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin-bottom: 4px;
}
.captree .caproot .fname {
  color: var(--gold);
}
.captree .kbitem {
  opacity: 0.92;
}
.captree .kbitem .dico {
  color: var(--gold);
}
.capdiv {
  height: 1px;
  background: var(--line);
  margin: 8px 4px 4px;
}

/* 右键上下文菜单 */
.ctxmenu {
  position: fixed;
  z-index: 3000;
  min-width: 168px;
  padding: 5px;
  border-radius: 10px;
  border: 1px solid var(--bd);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.45);
}
.ctxitem {
  padding: 7px 12px;
  border-radius: 7px;
  font-size: 13.5px;
  cursor: pointer;
  color: var(--fg);
  white-space: nowrap;
}
.ctxitem:hover {
  background: var(--glass2);
  color: var(--violet);
}
.ctxitem.danger {
  color: var(--pink);
}
.ctxitem.danger:hover {
  background: rgba(255, 143, 200, 0.14);
}

/* AI 栏 */
.aibar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin: 8px 0 4px;
}
.aibar .ailabel {
  font-size: 12px;
  font-weight: 700;
  color: var(--violet);
}
.aibar .aiinput {
  flex: 1;
  min-width: 180px;
}
.aihint {
  font-size: 11px;
  margin: 0 0 8px;
  opacity: 0.72;
}

/* 主区工具栏（关系图切换）*/
.kbtoolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 10px;
}
.graphpanel {
  height: clamp(440px, calc(100vh - 220px), 760px);
  padding: 8px;
  display: flex;
}

/* 双链（wikilink）—— 详情 + 预览共用 */
.mdbody :deep(a.wikilink),
.mdpreview :deep(a.wikilink) {
  color: var(--violet);
  background: rgba(183, 155, 255, 0.14);
  border-radius: 5px;
  padding: 0 5px;
  text-decoration: none;
  cursor: pointer;
  font-weight: 600;
}
.mdbody :deep(a.wikilink:hover),
.mdpreview :deep(a.wikilink:hover) {
  background: rgba(183, 155, 255, 0.28);
}
.mdbody :deep(a.wikilink.missing),
.mdpreview :deep(a.wikilink.missing) {
  color: var(--pink);
  background: rgba(255, 143, 200, 0.12);
  border: 1px dashed rgba(255, 143, 200, 0.5);
}

/* 反向链接 */
.backlinks {
  margin-top: 18px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}
.backlinks .bltitle {
  font-size: 13px;
  font-weight: 700;
  color: var(--mut);
  margin-bottom: 6px;
}
.backlinks .blitem {
  font-size: 13.5px;
  padding: 4px 8px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--fg);
}
.backlinks .blitem:hover {
  background: var(--glass2);
  color: var(--violet);
}

/* markdown 渲染排版（详情 + 预览共用 .mdbody/.mdpreview）*/
.kbdetail h3 {
  margin: 0 0 10px;
  font-size: 19px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.kbdetail .meta {
  margin-bottom: 12px;
}
.mdbody,
.mdpreview {
  line-height: 1.7;
}
.mdbody :deep(h1),
.mdpreview :deep(h1) { font-size: 20px; margin: 0.6em 0 0.4em; border-bottom: 1px solid var(--line); padding-bottom: 4px; }
.mdbody :deep(h2),
.mdpreview :deep(h2) { font-size: 17px; margin: 0.6em 0 0.4em; }
.mdbody :deep(h3),
.mdpreview :deep(h3) { font-size: 15px; margin: 0.5em 0 0.3em; }
.mdbody :deep(p),
.mdpreview :deep(p) { margin: 0.5em 0; }
.mdbody :deep(ul),
.mdbody :deep(ol),
.mdpreview :deep(ul),
.mdpreview :deep(ol) { padding-left: 1.4em; margin: 0.4em 0; }
.mdbody :deep(a),
.mdpreview :deep(a) { color: var(--gold); }
.mdbody :deep(strong),
.mdpreview :deep(strong) { color: #fff; }
.mdbody :deep(code),
.mdpreview :deep(code) {
  background: rgba(0, 0, 0, 0.32);
  padding: 1px 6px;
  border-radius: 5px;
  font-family: Consolas, monospace;
  font-size: 13px;
  color: #ffd98a;
}
.mdbody :deep(pre),
.mdpreview :deep(pre) {
  background: rgba(0, 0, 0, 0.4);
  border: 1px solid var(--bd);
  border-radius: 10px;
  padding: 12px 14px;
  overflow: auto;
  margin: 0.6em 0;
}
.mdbody :deep(pre code),
.mdpreview :deep(pre code) {
  background: none;
  padding: 0;
  color: #e8e0f5;
}
.mdbody :deep(blockquote),
.mdpreview :deep(blockquote) {
  border-left: 3px solid var(--violet);
  margin: 0.6em 0;
  padding: 2px 12px;
  color: var(--mut);
}
.mdbody :deep(table),
.mdpreview :deep(table) { border-collapse: collapse; margin: 0.6em 0; }
.mdbody :deep(th),
.mdbody :deep(td),
.mdpreview :deep(th),
.mdpreview :deep(td) { border: 1px solid var(--bd); padding: 5px 10px; }
</style>
