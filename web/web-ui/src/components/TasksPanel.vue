<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
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

const list = ref<ScheduledTask[]>([])
const servers = ref<string[]>([])
const error = ref('')
const loading = ref(false)
const editing = ref<Partial<ScheduledTask> | null>(null)
const dialogVisible = ref(false)
const refining = ref(false)
const saving = ref(false)

const WEEK = [
  { v: 1, l: '一' }, { v: 2, l: '二' }, { v: 3, l: '三' }, { v: 4, l: '四' },
  { v: 5, l: '五' }, { v: 6, l: '六' }, { v: 0, l: '日' },
]

function handle(e: unknown) {
  if (e instanceof UnauthorizedError) return // 请求层集中登出
  error.value = (e as Error).message
}

async function load() {
  loading.value = true
  try {
    list.value = await fetchTasks()
    servers.value = await fetchServers()
    error.value = ''
  } catch (e) {
    handle(e)
  } finally {
    loading.value = false
  }
}

function newTask() {
  editing.value = {
    name: '', enabled: true, action: 'broadcast', target: '*', payload: '',
    type: 'interval', intervalMin: 60, time: '12:00', days: [], script: [],
  }
  dialogVisible.value = true
}
function edit(t: ScheduledTask) {
  editing.value = { ...t, days: [...t.days] }
  dialogVisible.value = true
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
  saving.value = true
  try {
    await saveTask(e)
    dialogVisible.value = false
    editing.value = null
    ElMessage.success('已保存')
    await load()
  } catch (err) {
    handle(err)
  } finally {
    saving.value = false
  }
}

async function remove(t: ScheduledTask) {
  try {
    await ElMessageBox.confirm(`确认删除任务「${t.name}」？`, '删除任务', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteTask(t.id)
    ElMessage.success('已删除')
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
  try {
    const r = await runTask(t.id)
    ElMessageBox.alert(r.result || '（无输出）', `运行结果 · ${t.name}`, {
      confirmButtonText: '好',
      customClass: 'wa-run-result',
    })
  } catch (e) {
    if (e instanceof UnauthorizedError) return
    ElMessage.error((e as Error).message)
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
  return new Date(ts).toLocaleString().slice(5, 16)
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
        <el-button type="primary" @click="newTask">+ 新建任务</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>
    <p v-if="error" class="err">{{ error }}</p>

    <!-- 任务列表 -->
    <div class="panel glass" style="padding: 6px">
      <el-table :data="list" v-loading="loading" empty-text="还没有定时任务，点「新建任务」" style="width: 100%">
        <el-table-column label="任务名" min-width="140">
          <template #default="{ row }">
            <span :class="{ 'task-off': !row.enabled }" style="font-weight: 700">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="触发" min-width="130">
          <template #default="{ row }"><span class="accent">{{ when(row) }}</span></template>
        </el-table-column>
        <el-table-column label="动作 → 目标" min-width="180">
          <template #default="{ row }">
            {{ row.action }} → {{ row.target || '*' }}
            <el-tag v-if="row.script?.length" size="small" effect="plain" round>{{ row.script.length }} 步</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下次运行" min-width="120">
          <template #default="{ row }">{{ nextRun(row.nextRun) }}</template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="toggle(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="run(row)">▶ 运行</el-button>
            <el-button link type="primary" @click="edit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editing?.id ? '编辑任务' : '新建任务'" width="560px" @closed="editing = null">
      <el-form v-if="editing" label-position="top">
        <el-form-item label="任务名">
          <el-input v-model="editing.name" placeholder="任务名" />
        </el-form-item>

        <el-form-item label="触发方式">
          <el-radio-group v-model="editing.type">
            <el-radio-button value="interval">按间隔</el-radio-button>
            <el-radio-button value="daily">每天定时</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="editing.type === 'interval'" label="间隔（分钟）">
          <el-input-number v-model="editing.intervalMin" :min="1" :controls="false" style="width: 140px" />
        </el-form-item>
        <template v-else>
          <el-form-item label="时间（HH:MM）">
            <el-input v-model="editing.time" placeholder="12:00" style="width: 140px" />
          </el-form-item>
          <el-form-item label="星期（不选=每天）">
            <div class="week">
              <button
                v-for="d in WEEK"
                :key="d.v"
                type="button"
                :class="['day', { on: editing.days?.includes(d.v) }]"
                @click="toggleDay(d.v)"
              >{{ d.l }}</button>
            </div>
          </el-form-item>
        </template>

        <el-form-item label="动作">
          <el-select v-model="editing.action" style="width: 160px">
            <el-option label="广播消息" value="broadcast" />
            <el-option label="执行命令" value="command" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标子服">
          <el-select v-model="editing.target" style="width: 200px">
            <el-option label="全部子服" value="*" />
            <el-option v-for="s in servers" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>

        <el-form-item :label="editing.action === 'command' ? '命令（不含 /）' : '广播内容'">
          <div style="display: flex; gap: 8px; width: 100%">
            <el-input v-model="editing.payload" type="textarea" :rows="2" style="flex: 1" />
            <el-button :loading="refining" :disabled="!editing.payload?.trim()" @click="refine">✨ 润色</el-button>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!editing?.name?.trim()" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.accent {
  color: var(--accent);
  font-size: 13px;
}
.task-off {
  opacity: 0.5;
}
/* 星期选择：沿用原版审美（选中=accent 紫） */
.week {
  display: flex;
  gap: 4px;
}
.day {
  padding: 5px 10px;
  border-radius: 8px;
  background: var(--glass2);
  border: 1px solid var(--bd);
  color: var(--fg);
  cursor: pointer;
  font: inherit;
}
.day.on {
  background: linear-gradient(120deg, rgba(255, 143, 200, 0.28), rgba(183, 155, 255, 0.28));
  border-color: var(--violet);
  color: #fff;
}
</style>
