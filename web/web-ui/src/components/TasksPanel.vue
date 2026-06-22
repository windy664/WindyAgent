<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  deleteTask,
  fetchServers,
  fetchTasks,
  refineTask,
  runTask,
  saveTask,
  toggleTask,
  UnauthorizedError,
  type ScheduledTask,
} from '../api'

const emit = defineEmits<{ (e: 'unauthorized'): void }>()

const list = ref<ScheduledTask[]>([])
const servers = ref<string[]>([])
const error = ref('')
const toast = ref('')
const editing = ref<Partial<ScheduledTask> | null>(null)
const runResult = ref<{ id: string; text: string } | null>(null)
const refining = ref(false)

const WEEK = [
  { v: 1, l: '一' }, { v: 2, l: '二' }, { v: 3, l: '三' }, { v: 4, l: '四' },
  { v: 5, l: '五' }, { v: 6, l: '六' }, { v: 0, l: '日' },
]

function handle(e: unknown) {
  if (e instanceof UnauthorizedError) emit('unauthorized')
  else error.value = (e as Error).message
}
function flash(m: string) {
  toast.value = m
  setTimeout(() => (toast.value = ''), 2500)
}

async function load() {
  try {
    list.value = await fetchTasks()
    servers.value = await fetchServers()
    error.value = ''
  } catch (e) {
    handle(e)
  }
}

function newTask() {
  editing.value = {
    name: '', enabled: true, action: 'broadcast', target: '*', payload: '',
    type: 'interval', intervalMin: 60, time: '12:00', days: [], script: [],
  }
}
function edit(t: ScheduledTask) {
  editing.value = { ...t, days: [...t.days] }
}
function cancel() {
  editing.value = null
}

function toggleDay(v: number) {
  const e = editing.value
  if (!e) return
  const days = e.days || (e.days = [])
  const i = days.indexOf(v)
  if (i >= 0) days.splice(i, 1)
  else days.push(v)
}

async function save() {
  const e = editing.value
  if (!e || !e.name?.trim()) return
  try {
    await saveTask(e)
    editing.value = null
    flash('已保存')
    await load()
  } catch (err) {
    handle(err)
  }
}

async function remove(t: ScheduledTask) {
  if (!confirm(`删除任务「${t.name}」？`)) return
  try {
    await deleteTask(t.id)
    await load()
  } catch (e) {
    handle(e)
  }
}
async function toggle(t: ScheduledTask) {
  try {
    await toggleTask(t.id)
    await load()
  } catch (e) {
    handle(e)
  }
}
async function run(t: ScheduledTask) {
  runResult.value = { id: t.id, text: '运行中…' }
  try {
    const r = await runTask(t.id)
    runResult.value = { id: t.id, text: r.result }
  } catch (e) {
    if (e instanceof UnauthorizedError) emit('unauthorized')
    else runResult.value = { id: t.id, text: '⚠️ ' + (e as Error).message }
  }
}

async function refine() {
  const e = editing.value
  if (!e?.payload?.trim()) return
  refining.value = true
  try {
    const r = await refineTask(e.payload)
    e.payload = r.text
  } catch (err) {
    handle(err)
  } finally {
    refining.value = false
  }
}

function when(t: ScheduledTask): string {
  if (t.type === 'daily') {
    const d = t.days.length ? t.days.map((x) => WEEK.find((w) => w.v === x)?.l).join('') : '每天'
    return `${d} ${t.time}`
  }
  return `每 ${t.intervalMin} 分钟`
}
function nextRun(ts: number): string {
  if (ts <= 0) return '—'
  return new Date(ts).toLocaleString()
}

onMounted(load)
</script>

<template>
  <div class="board">
    <div class="bhead">
      <div>
        <h2>⏰ 定时任务</h2>
        <p>定时广播 / 执行命令 —— 到点自动跑（每隔 N 分钟，或每天定时）</p>
      </div>
      <div class="tools">
        <button class="btn sm" @click="newTask">+ 新建任务</button>
        <button class="btn ghost sm" @click="load">刷新</button>
      </div>
    </div>
    <p v-if="error" class="err">{{ error }}</p>
    <div v-if="toast" class="toast">{{ toast }}</div>

    <!-- 编辑器 -->
    <div v-if="editing" class="editor">
      <input v-model="editing.name" placeholder="任务名" class="name-in" />
      <div class="row">
        <label>触发</label>
        <select v-model="editing.type">
          <option value="interval">按间隔</option>
          <option value="daily">每天定时</option>
        </select>
        <template v-if="editing.type === 'interval'">
          <input v-model.number="editing.intervalMin" type="number" min="1" class="num" /> 分钟
        </template>
        <template v-else>
          <input v-model="editing.time" placeholder="HH:MM" class="time" />
          <span class="week">
            <button
              v-for="d in WEEK"
              :key="d.v"
              type="button"
              :class="['day', { on: editing.days?.includes(d.v) }]"
              @click="toggleDay(d.v)"
            >{{ d.l }}</button>
          </span>
          <span class="hint">（不选=每天）</span>
        </template>
      </div>
      <div class="row">
        <label>动作</label>
        <select v-model="editing.action">
          <option value="broadcast">广播消息</option>
          <option value="command">执行命令</option>
        </select>
        <label>目标</label>
        <select v-model="editing.target">
          <option value="*">全部子服</option>
          <option v-for="s in servers" :key="s" :value="s">{{ s }}</option>
        </select>
      </div>
      <div class="payload-row">
        <textarea v-model="editing.payload" rows="2" :placeholder="editing.action === 'command' ? '命令（不含 /）' : '广播内容'"></textarea>
        <button @click="refine" :disabled="refining || !editing.payload?.trim()">{{ refining ? '…' : '✨ 润色' }}</button>
      </div>
      <div class="editor-btns">
        <button class="primary" @click="save" :disabled="!editing.name?.trim()">保存</button>
        <button @click="cancel">取消</button>
      </div>
    </div>

    <!-- 列表 -->
    <div class="list">
      <div v-for="t in list" :key="t.id" class="task" :class="{ off: !t.enabled }">
        <div class="t-main">
          <div class="t-head">
            <span class="t-name">{{ t.name }}</span>
            <span class="t-when">{{ when(t) }}</span>
          </div>
          <div class="t-meta">
            {{ t.action }} → {{ t.target || '*' }}
            <span v-if="t.script?.length"> · {{ t.script.length }} 步脚本</span>
            · 下次 {{ nextRun(t.nextRun) }}
          </div>
          <div v-if="t.lastResult" class="t-last">上次：{{ t.lastResult }}</div>
          <pre v-if="runResult?.id === t.id" class="run-out">{{ runResult.text }}</pre>
        </div>
        <div class="t-btns">
          <button @click="toggle(t)">{{ t.enabled ? '停用' : '启用' }}</button>
          <button @click="run(t)">▶ 运行</button>
          <button @click="edit(t)">编辑</button>
          <button class="danger" @click="remove(t)">删除</button>
        </div>
      </div>
      <div v-if="list.length === 0 && !editing" class="muted">还没有定时任务，点「新建」。</div>
    </div>
  </div>
</template>

<style scoped>
.view { padding: 20px; overflow-y: auto; width: 100%; }
header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
header h2 { margin: 0; }
button { background: var(--panel-2); color: var(--fg); border: 1px solid var(--border); border-radius: 8px; padding: 6px 12px; cursor: pointer; }
button.primary { background: var(--grad); border: none; color: #fff; font-weight: 800; box-shadow: 0 5px 16px rgba(183,155,255,.38); }
button.primary:hover:not(:disabled) { filter: brightness(1.07); }
button.danger { color: #f87171; }
button:disabled { opacity: 0.5; cursor: default; }
input, textarea, select { background: var(--panel-2); color: var(--fg); border: 1px solid var(--border); border-radius: 8px; padding: 8px 10px; font: inherit; }
.editor { display: flex; flex-direction: column; gap: 10px; background: var(--panel); border: 1px solid var(--accent); border-radius: 12px; padding: 14px; margin-bottom: 16px; }
.name-in { width: 100%; box-sizing: border-box; }
.row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.row label { color: var(--muted); font-size: 13px; }
.row select, .row input { width: auto; }
.payload-row textarea { width: auto; }
.num { width: 70px; }
.time { width: 80px; }
.week { display: flex; gap: 3px; }
.day { padding: 4px 8px; border-radius: 6px; }
.day.on { background: var(--accent); border-color: var(--accent); color: #fff; }
.hint { color: var(--muted); font-size: 12px; }
.payload-row { display: flex; gap: 8px; }
.payload-row textarea { flex: 1; }
.payload-row button { white-space: nowrap; }
.editor-btns { display: flex; gap: 8px; }
.list { display: flex; flex-direction: column; gap: 10px; }
.task { display: flex; justify-content: space-between; gap: 12px; background: var(--panel); border: 1px solid var(--border); border-radius: 10px; padding: 12px 14px; }
.task.off { opacity: 0.55; }
.t-main { flex: 1; min-width: 0; }
.t-head { display: flex; align-items: baseline; gap: 10px; }
.t-name { font-weight: 600; }
.t-when { font-size: 12px; color: var(--accent); }
.t-meta { font-size: 12px; color: var(--muted); margin-top: 3px; }
.t-last { font-size: 12px; color: var(--muted); margin-top: 3px; word-break: break-all; }
.run-out { background: rgba(0,0,0,0.3); border-radius: 8px; padding: 8px; font-size: 12px; white-space: pre-wrap; margin-top: 6px; max-height: 200px; overflow-y: auto; }
.t-btns { display: flex; flex-direction: column; gap: 6px; align-items: stretch; flex-shrink: 0; }
.t-btns button { white-space: nowrap; padding: 6px 14px; }
.muted { color: var(--muted); text-align: center; padding: 30px; }
.toast { background: rgba(99,102,241,0.2); border: 1px solid var(--accent); border-radius: 8px; padding: 8px 12px; margin-bottom: 12px; }
.error { color: #ff6b6b; }
</style>
