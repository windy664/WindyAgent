<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  deleteSkill,
  draftSkill,
  fetchServers,
  fetchSkillContent,
  fetchSkills,
  reloadSkills,
  runSkill,
  saveSkill,
  syncSkills,
  UnauthorizedError,
  type SkillSummary,
} from '../api'

const emit = defineEmits<{ (e: 'unauthorized'): void }>()

const list = ref<SkillSummary[]>([])
const servers = ref<string[]>([])
const error = ref('')
const toast = ref('')

const editing = ref<{
  handle: string
  isScript: boolean
  md: string
  script: string
  targets: string
  isNew: boolean
} | null>(null)

const runServer = ref('')
const runArgs = ref<Record<string, string>>({})
const runResult = ref('')
const running = ref(false)
const selected = ref<SkillSummary | null>(null)
const drafting = ref(false)
const draftInput = ref('')

function handle(e: unknown) {
  if (e instanceof UnauthorizedError) emit('unauthorized')
  else error.value = (e as Error).message
}
function flash(msg: string) {
  toast.value = msg
  setTimeout(() => (toast.value = ''), 2500)
}

async function load() {
  try {
    list.value = await fetchSkills()
    servers.value = await fetchServers()
    error.value = ''
  } catch (e) {
    handle(e)
  }
}

function newSkill() {
  selected.value = null
  editing.value = { handle: '', isScript: false, md: '', script: '', targets: '', isNew: true }
}

async function openSkill(s: SkillSummary) {
  selected.value = s
  runResult.value = ''
  runArgs.value = {}
  runServer.value = ''
  try {
    const c = await fetchSkillContent(s.handle)
    editing.value = { handle: c.handle, isScript: c.isScript, md: c.md, script: c.script, targets: c.targets, isNew: false }
  } catch (e) {
    handle(e)
  }
}

async function save() {
  const e = editing.value
  if (!e || !e.handle.trim()) return
  try {
    const r = await saveSkill({ handle: e.handle, isScript: e.isScript, md: e.md, script: e.script, targets: e.targets })
    flash(r.pushed ? `已保存并下发：${r.pushed}` : '已保存')
    e.isNew = false
    await load()
  } catch (err) {
    handle(err)
  }
}

async function remove() {
  const e = editing.value
  if (!e || e.isNew) return
  if (!confirm(`删除技能「${e.handle}」？`)) return
  try {
    await deleteSkill(e.handle)
    editing.value = null
    selected.value = null
    await load()
  } catch (err) {
    handle(err)
  }
}

async function reload() {
  try {
    flash(`已重载 ${(await reloadSkills()).count} 个技能`)
    await load()
  } catch (e) {
    handle(e)
  }
}
async function sync() {
  try {
    flash(`下发：${(await syncSkills()).result}`)
  } catch (e) {
    handle(e)
  }
}

async function run() {
  if (!selected.value) return
  running.value = true
  runResult.value = ''
  try {
    runResult.value = (await runSkill(selected.value.handle, runServer.value, runArgs.value)).result
  } catch (e) {
    if (e instanceof UnauthorizedError) emit('unauthorized')
    else runResult.value = '⚠️ ' + (e as Error).message
  } finally {
    running.value = false
  }
}

async function aiDraft() {
  const text = draftInput.value.trim()
  if (!text) return
  drafting.value = true
  try {
    const d = await draftSkill(text)
    if (!editing.value) newSkill()
    editing.value!.md = d.md
    draftInput.value = ''
  } catch (e) {
    handle(e)
  } finally {
    drafting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="board">
    <div class="bhead">
      <div>
        <h2>🧪 技能扩展</h2>
        <p>📄 文字技能在中心执行 · ⚙️ 脚本技能下发到目标子服执行 · 保存即热重载</p>
      </div>
      <div class="tools">
        <button class="btn sm" @click="newSkill">+ 新建技能</button>
        <button class="btn ghost sm" @click="sync">⬇ 下发到子服</button>
        <button class="btn ghost sm" @click="reload">↻ 重载库</button>
        <button class="btn ghost sm" @click="load">刷新</button>
      </div>
    </div>

    <div v-if="toast" class="toast">{{ toast }}</div>
    <p v-if="error" class="err">{{ error }}</p>

    <div class="kbwrap" style="padding: 0">
      <div class="kbside glass">
        <div
          v-for="s in list"
          :key="s.handle"
          :class="['kbitem', { on: selected?.handle === s.handle }]"
          @click="openSkill(s)"
        >
          <div class="it">{{ s.type === 'script' ? '⚙️' : '📄' }} {{ s.name }}</div>
          <div class="is">{{ s.description }}</div>
        </div>
        <div v-if="list.length === 0" class="muted" style="padding: 16px; font-size: 13px">还没有技能</div>
      </div>

      <div class="kbmain">
        <div v-if="editing" class="panel glass">
          <div class="frow">
            <input v-model="editing.handle" placeholder="技能名（handle）" :disabled="!editing.isNew" />
            <label class="chk"><input type="checkbox" v-model="editing.isScript" /> 脚本技能</label>
          </div>
          <details style="margin: 10px 0">
            <summary style="cursor: pointer; color: var(--violet); font-weight: 700">🤖 AI 起草 SKILL.md</summary>
            <textarea v-model="draftInput" rows="2" placeholder="描述你想要的技能…" style="margin-top: 8px"></textarea>
            <button class="btn sm" style="margin-top: 8px" :disabled="drafting || !draftInput.trim()" @click="aiDraft">
              {{ drafting ? '生成中…' : '生成草稿 ✨' }}
            </button>
          </details>
          <input v-if="editing.isScript" v-model="editing.targets" placeholder="目标子服（逗号分隔；留空=全部）" style="margin-bottom: 8px" />
          <label class="muted" style="font-size: 12px">SKILL.md</label>
          <textarea v-model="editing.md" rows="8" placeholder="name / description / args …"></textarea>
          <template v-if="editing.isScript">
            <label class="muted" style="font-size: 12px; display: block; margin-top: 8px">Groovy 脚本</label>
            <textarea v-model="editing.script" rows="10" class="code"></textarea>
          </template>
          <div style="margin-top: 12px; display: flex; gap: 8px">
            <button class="btn" :disabled="!editing.handle.trim()" @click="save">保存</button>
            <button v-if="!editing.isNew" class="btn ghost" @click="remove">删除</button>
          </div>

          <div v-if="selected" class="run-box">
            <h4>▶ 运行</h4>
            <select v-if="editing.isScript" v-model="runServer" style="margin-bottom: 8px">
              <option value="">选择目标子服…</option>
              <option v-for="srv in servers" :key="srv" :value="srv">{{ srv }}</option>
            </select>
            <div v-for="arg in selected.args" :key="arg.name" class="arg">
              <label class="muted">{{ arg.name }} ({{ arg.type }})</label>
              <input v-model="runArgs[arg.name]" :placeholder="arg.description" />
            </div>
            <button class="btn sm" :disabled="running || (editing.isScript && !runServer)" @click="run">
              {{ running ? '运行中…' : '▶ 运行' }}
            </button>
            <pre v-if="runResult" class="result">{{ runResult }}</pre>
          </div>
        </div>

        <div v-else class="panel glass">
          <p class="muted">← 从左侧选一个技能查看/编辑，或「+ 新建技能」。</p>
          <p class="muted" style="font-size: 12px; margin-top: 10px">
            两类技能：<b>📄 纯文字</b>=一套操作流程，Agent 读懂后用现有工具执行；<b>⚙️ 脚本+文字</b>=SKILL.md 说清何时/怎么用 + Groovy 脚本干现有工具做不到的事。
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.frow {
  display: flex;
  gap: 12px;
  align-items: center;
}
.frow input {
  flex: 1;
}
.chk {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
  color: var(--mut);
  font-size: 13px;
}
.chk input {
  width: auto;
}
textarea.code {
  font-family: Consolas, monospace;
}
.run-box {
  margin-top: 16px;
  border-top: 1px solid var(--line);
  padding-top: 14px;
}
.run-box h4 {
  margin: 0 0 10px;
  font-size: 14px;
}
.arg {
  margin-bottom: 8px;
}
.arg label {
  display: block;
  font-size: 12px;
  margin-bottom: 3px;
}
.result {
  background: rgba(0, 0, 0, 0.32);
  border-radius: 9px;
  padding: 10px;
  white-space: pre-wrap;
  font-size: 13px;
  max-height: 300px;
  overflow-y: auto;
  margin-top: 10px;
}
.toast {
  background: rgba(99, 102, 241, 0.2);
  border: 1px solid var(--violet);
  border-radius: 10px;
  padding: 8px 12px;
  margin-bottom: 12px;
}
</style>
