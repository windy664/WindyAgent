<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElButton, ElOption, ElSelect, ElTable, ElTableColumn } from 'element-plus'
import { fetchUsage, UnauthorizedError, type Usage } from '../api'

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
    if (e instanceof UnauthorizedError) return // 请求层集中登出
    error.value = (e as Error).message
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
function rowLatency(d: { calls: number; totalLatencyMs: number }): string {
  return (d.calls ? Math.round(d.totalLatencyMs / d.calls) : 0) + 'ms'
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
        <el-select v-model="days" @change="load" style="width: 120px">
          <el-option :value="7" label="近 7 天" />
          <el-option :value="14" label="近 14 天" />
          <el-option :value="30" label="近 30 天" />
        </el-select>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <p v-if="error" class="err">{{ error }}</p>

    <template v-if="usage">
      <div class="kpis">
        <div class="kpi glass"><div class="v">{{ fmt(usage.totalCalls) }}</div><div class="k">总调用</div></div>
        <div class="kpi glass"><div class="v">{{ fmt(usage.totalInputTokens) }}</div><div class="k">输入 token</div></div>
        <div class="kpi glass"><div class="v">{{ fmt(usage.totalOutputTokens) }}</div><div class="k">输出 token</div></div>
        <div class="kpi glass"><div class="v">{{ fmt(avgLatency) }}<span class="unit">ms</span></div><div class="k">平均延迟</div></div>
      </div>

      <div class="panel glass" style="padding: 6px">
        <el-table :data="usage.daily" v-loading="loading" empty-text="暂无数据" style="width: 100%">
          <el-table-column prop="day" label="日期" min-width="120" />
          <el-table-column label="调用" align="right"><template #default="{ row }">{{ fmt(row.calls) }}</template></el-table-column>
          <el-table-column label="输入" align="right"><template #default="{ row }">{{ fmt(row.inputTokens) }}</template></el-table-column>
          <el-table-column label="输出" align="right"><template #default="{ row }">{{ fmt(row.outputTokens) }}</template></el-table-column>
          <el-table-column label="均延迟" align="right"><template #default="{ row }">{{ rowLatency(row) }}</template></el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>

<style scoped>
.unit {
  font-size: 14px;
  opacity: 0.6;
  margin-left: 2px;
}
</style>
