<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchUsage, UnauthorizedError, type Usage } from '../api'

const emit = defineEmits<{ (e: 'unauthorized'): void }>()

const usage = ref<Usage | null>(null)
const days = ref(7)
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    usage.value = await fetchUsage(days.value)
  } catch (e) {
    if (e instanceof UnauthorizedError) emit('unauthorized')
    else error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

const avgLatency = computed(() => {
  const u = usage.value
  if (!u || u.totalCalls === 0) return 0
  return Math.round(u.totalLatencyMs / u.totalCalls)
})

function fmt(n: number): string {
  return n.toLocaleString()
}

onMounted(load)
</script>

<template>
  <div class="board">
    <div class="bhead">
      <div>
        <h2>🖥️ 系统健康 / 用量</h2>
        <p>LLM 调用量 · token 消耗 · 平均延迟</p>
      </div>
      <div class="tools">
        <select v-model.number="days" @change="load" style="width: auto">
          <option :value="7">近 7 天</option>
          <option :value="14">近 14 天</option>
          <option :value="30">近 30 天</option>
        </select>
        <button class="btn ghost sm" @click="load" :disabled="loading">{{ loading ? '…' : '刷新' }}</button>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <template v-if="usage">
      <div class="cards">
        <div class="card"><div class="num">{{ fmt(usage.totalCalls) }}</div><div class="lbl">总调用</div></div>
        <div class="card"><div class="num">{{ fmt(usage.totalInputTokens) }}</div><div class="lbl">输入 token</div></div>
        <div class="card"><div class="num">{{ fmt(usage.totalOutputTokens) }}</div><div class="lbl">输出 token</div></div>
        <div class="card"><div class="num">{{ fmt(avgLatency) }}<span class="unit">ms</span></div><div class="lbl">平均延迟</div></div>
      </div>

      <table>
        <thead>
          <tr><th>日期</th><th>调用</th><th>输入</th><th>输出</th><th>均延迟</th></tr>
        </thead>
        <tbody>
          <tr v-for="d in usage.daily" :key="d.day">
            <td>{{ d.day }}</td>
            <td>{{ fmt(d.calls) }}</td>
            <td>{{ fmt(d.inputTokens) }}</td>
            <td>{{ fmt(d.outputTokens) }}</td>
            <td>{{ d.calls ? Math.round(d.totalLatencyMs / d.calls) : 0 }}ms</td>
          </tr>
          <tr v-if="usage.daily.length === 0"><td colspan="5" class="muted">暂无数据</td></tr>
        </tbody>
      </table>
    </template>
  </div>
</template>

<style scoped>
.view { padding: 20px; overflow-y: auto; width: 100%; }
header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
header h2 { margin: 0; }
.actions { display: flex; gap: 8px; }
select, button { background: var(--panel-2); color: var(--fg); border: 1px solid var(--border); border-radius: 8px; padding: 6px 12px; cursor: pointer; }
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; margin-bottom: 20px; }
.card { background: var(--panel); border: 1px solid var(--border); border-radius: 12px; padding: 16px; }
.num { font-size: 26px; font-weight: 700; }
.unit { font-size: 14px; opacity: 0.6; margin-left: 2px; }
.lbl { color: var(--muted); font-size: 13px; margin-top: 4px; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--border); }
th { color: var(--muted); font-weight: 600; }
.muted { color: var(--muted); text-align: center; }
.error { color: #ff6b6b; }
</style>
