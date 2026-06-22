<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import {
  approveAction,
  denyAction,
  fetchApprovals,
  UnauthorizedError,
  type Approvals,
} from '../api'

const emit = defineEmits<{ (e: 'unauthorized'): void }>()

const data = ref<Approvals | null>(null)
const error = ref('')
const busyId = ref('')
let timer: number | undefined

async function load() {
  try {
    data.value = await fetchApprovals()
    error.value = ''
  } catch (e) {
    if (e instanceof UnauthorizedError) {
      emit('unauthorized')
      stopPolling()
    } else {
      error.value = (e as Error).message
    }
  }
}

async function approve(id: string) {
  busyId.value = id
  try {
    await approveAction(id)
    await load()
  } catch (e) {
    if (e instanceof UnauthorizedError) emit('unauthorized')
  } finally {
    busyId.value = ''
  }
}
async function deny(id: string) {
  busyId.value = id
  try {
    await denyAction(id)
    await load()
  } catch (e) {
    if (e instanceof UnauthorizedError) emit('unauthorized')
  } finally {
    busyId.value = ''
  }
}

function secs(ms: number): string {
  return Math.ceil(ms / 1000) + 's'
}
function time(at: number): string {
  return new Date(at).toLocaleTimeString()
}

function stopPolling() {
  if (timer) {
    clearInterval(timer)
    timer = undefined
  }
}

onMounted(() => {
  load()
  // 待审批是时效性的（TTL 倒计时），轮询刷新
  timer = window.setInterval(load, 3000)
})
onUnmounted(stopPolling)
</script>

<template>
  <div class="board">
    <div class="bhead">
      <div>
        <h2>✅ 高危操作审批</h2>
        <p>AI 触发的高危操作在此人工确认 · TTL 倒计时</p>
      </div>
      <div class="tools"><button class="btn ghost sm" @click="load">刷新</button></div>
    </div>
    <p v-if="error" class="err">{{ error }}</p>

    <section>
      <h3>待审批 <span class="count" v-if="data?.pending.length">{{ data.pending.length }}</span></h3>
      <div v-if="data && data.pending.length === 0" class="muted">没有待审批的操作 ✨</div>
      <div v-for="p in data?.pending || []" :key="p.id" class="item pending">
        <div class="info">
          <div class="desc">{{ p.desc }}</div>
          <div class="meta">#{{ p.id }} · 剩余 {{ secs(p.remainMs) }}</div>
        </div>
        <div class="btns">
          <button class="ok" :disabled="busyId === p.id" @click="approve(p.id)">批准</button>
          <button class="no" :disabled="busyId === p.id" @click="deny(p.id)">拒绝</button>
        </div>
      </div>
    </section>

    <section>
      <h3>历史</h3>
      <div v-if="data && data.history.length === 0" class="muted">暂无历史</div>
      <div v-for="h in data?.history || []" :key="h.id + h.at" class="item">
        <div class="info">
          <div class="desc">{{ h.desc }}</div>
          <div class="meta">#{{ h.id }} · {{ time(h.at) }} · {{ h.result }}</div>
        </div>
        <span :class="['badge', h.decision]">{{ h.decision === 'approve' ? '已批准' : '已拒绝' }}</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.view { padding: 20px; overflow-y: auto; width: 100%; }
header { display: flex; align-items: center; justify-content: space-between; }
header h2 { margin: 0; }
button { background: var(--panel-2); color: var(--fg); border: 1px solid var(--border); border-radius: 8px; padding: 6px 12px; cursor: pointer; }
section { margin-top: 20px; }
h3 { font-size: 14px; color: var(--muted); display: flex; align-items: center; gap: 8px; }
.count { background: var(--accent); color: #fff; border-radius: 10px; padding: 0 8px; font-size: 12px; }
.item { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 14px; border: 1px solid var(--border); border-radius: 10px; margin-bottom: 8px; background: var(--panel); }
.item.pending { border-color: var(--accent); }
.desc { font-weight: 500; }
.meta { font-size: 12px; color: var(--muted); margin-top: 3px; }
.btns { display: flex; gap: 8px; }
.ok { background: #16a34a; border: none; color: #fff; }
.no { background: #dc2626; border: none; color: #fff; }
.badge { font-size: 12px; padding: 3px 10px; border-radius: 8px; }
.badge.approve { background: rgba(22, 163, 74, 0.2); color: #4ade80; }
.badge.deny { background: rgba(220, 38, 38, 0.2); color: #f87171; }
.muted { color: var(--muted); }
.error { color: #ff6b6b; }
</style>
