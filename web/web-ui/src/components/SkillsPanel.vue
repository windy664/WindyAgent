<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  ElButton,
  ElCheckbox,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
} from 'element-plus'
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

const list = ref<SkillSummary[]>([])
const servers = ref<string[]>([])
const error = ref('')

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
const saving = ref(false)
const selected = ref<SkillSummary | null>(null)
const drafting = ref(false)
const draftInput = ref('')

function handle(e: unknown) {
  if (e instanceof UnauthorizedError) return // 请求层集中登出
  error.value = (e as Error).message
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
  saving.value = true
  try {
    const r = await saveSkill({ handle: e.handle, isScript: e.isScript, md: e.md, script: e.script, targets: e.targets })
    ElMessage.success(r.pushed ? `已保存并下发：${r.pushed}` : '已保存')
    e.isNew = false
    await load()
  } catch (err) {
    handle(err)
  } finally {
    saving.value = false
  }
}

async function remove() {
  const e = editing.value
  if (!e || e.isNew) return
  try {
    await ElMessageBox.confirm(`确认删除技能「${e.handle}」？`, '删除技能', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteSkill(e.handle)
    editing.value = null
    selected.value = null
    ElMessage.success('已删除')
    await load()
  } catch (err) {
    handle(err)
  }
}

async function reload() {
  try {
    ElMessage.success(`已重载 ${(await reloadSkills()).count} 个技能`)
    await load()
  } catch (e) {
    handle(e)
  }
}
async function sync() {
  try {
    ElMessage.success(`下发：${(await syncSkills()).result}`)
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
    if (e instanceof UnauthorizedError) return
    runResult.value = '⚠️ ' + (e as Error).message
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
        <el-button type="primary" @click="newSkill">+ 新建技能</el-button>
        <el-button @click="sync">⬇ 下发到子服</el-button>
        <el-button @click="reload">↻ 重载库</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

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
            <el-input v-model="editing.handle" placeholder="技能名（handle）" :disabled="!editing.isNew" style="flex: 1" />
            <el-checkbox v-model="editing.isScript">脚本技能</el-checkbox>
          </div>
          <details style="margin: 10px 0">
            <summary style="cursor: pointer; color: var(--violet); font-weight: 700">🤖 AI 起草 SKILL.md</summary>
            <div style="display: flex; gap: 8px; margin-top: 8px">
              <el-input v-model="draftInput" type="textarea" :rows="2" placeholder="描述你想要的技能…" style="flex: 1" />
              <el-button :loading="drafting" :disabled="!draftInput.trim()" @click="aiDraft">生成草稿 ✨</el-button>
            </div>
          </details>
          <el-input v-if="editing.isScript" v-model="editing.targets" placeholder="目标子服（逗号分隔；留空=全部）" style="margin-bottom: 8px" />
          <label class="muted" style="font-size: 12px">SKILL.md</label>
          <el-input v-model="editing.md" type="textarea" :rows="8" placeholder="name / description / args …" />
          <template v-if="editing.isScript">
            <label class="muted" style="font-size: 12px; display: block; margin-top: 8px">Groovy 脚本</label>
            <el-input v-model="editing.script" type="textarea" :rows="10" class="code-input" />
          </template>
          <div style="margin-top: 12px; display: flex; gap: 8px">
            <el-button type="primary" :loading="saving" :disabled="!editing.handle.trim()" @click="save">保存</el-button>
            <el-button v-if="!editing.isNew" @click="remove">删除</el-button>
          </div>

          <div v-if="selected" class="run-box">
            <h4>▶ 运行</h4>
            <el-select v-if="editing.isScript" v-model="runServer" placeholder="选择目标子服…" style="margin-bottom: 8px; width: 100%">
              <el-option v-for="srv in servers" :key="srv" :label="srv" :value="srv" />
            </el-select>
            <div v-for="arg in selected.args" :key="arg.name" class="arg">
              <label class="muted">{{ arg.name }} ({{ arg.type }})</label>
              <el-input v-model="runArgs[arg.name]" :placeholder="arg.description" />
            </div>
            <el-button :loading="running" :disabled="editing.isScript && !runServer" @click="run">▶ 运行</el-button>
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
.code-input :deep(.el-textarea__inner) {
  font-family: Consolas, monospace;
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
</style>
