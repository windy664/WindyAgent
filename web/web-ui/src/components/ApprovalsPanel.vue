<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElButton, ElMessage, ElTable, ElTableColumn, ElTag } from 'element-plus'
import {
  approveAction,
  denyAction,
  fetchApprovals,
  UnauthorizedError,
  type Approvals,
} from '../api'

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
    ElMessage.success('已批准')
    await load()
  } catch (e) {
    if (!(e instanceof UnauthorizedError)) ElMessage.error((e as Error).message)
  } finally {
    busyId.value = ''
  }
}
async function deny(id: string) {
  busyId.value = id
  try {
    await denyAction(id)
    ElMessage.info('已拒绝')
    await load()
  } catch (e) {
    if (!(e instanceof UnauthorizedError)) ElMessage.error((e as Error).message)
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
      <div class="tools"><el-button @click="load">刷新</el-button></div>
    </div>
    <p v-if="error" class="err">{{ error }}</p>

    <!-- 待审批：醒目卡片（保留紧迫感）-->
    <section>
      <h3>待审批 <el-tag v-if="data?.pending.length" type="warning" effect="dark" round size="small">{{ data.pending.length }}</el-tag></h3>
      <div v-if="data && data.pending.length === 0" class="muted">没有待审批的操作 ✨</div>
      <div v-for="p in data?.pending || []" :key="p.id" class="item pending glass">
        <div class="info">
          <div class="desc">{{ p.desc }}</div>
          <div class="meta">#{{ p.id }} · 剩余 <span class="remain">{{ secs(p.remainMs) }}</span></div>
        </div>
        <div class="btns">
          <el-button color="#22a559" :dark="true" :loading="busyId === p.id" @click="approve(p.id)">批准</el-button>
          <el-button color="#e04b5c" :dark="true" :loading="busyId === p.id" @click="deny(p.id)">拒绝</el-button>
        </div>
      </div>
    </section>

    <!-- 历史：规范表格 -->
    <section>
      <h3>历史</h3>
      <div class="panel glass" style="padding: 6px">
        <el-table :data="data?.history || []" empty-text="暂无历史" style="width: 100%">
          <el-table-column label="操作" min-width="240">
            <template #default="{ row }">
              <div class="desc">{{ row.desc }}</div>
              <div class="meta">#{{ row.id }} · {{ row.result }}</div>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="120">
            <template #default="{ row }">{{ time(row.at) }}</template>
          </el-table-column>
          <el-table-column label="结果" width="110">
            <template #default="{ row }">
              <el-tag :type="row.decision === 'approve' ? 'success' : 'danger'" effect="plain" round size="small">
                {{ row.decision === 'approve' ? '已批准' : '已拒绝' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>
  </div>
</template>

<style scoped>
section {
  margin-top: 20px;
}
h3 {
  font-size: 14px;
  color: var(--muted);
  display: flex;
  align-items: center;
  gap: 8px;
}
.item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  margin-bottom: 8px;
}
.item.pending {
  border: 1px solid rgba(183, 155, 255, 0.55);
  box-shadow: 0 0 0 1px rgba(183, 155, 255, 0.18);
}
.desc {
  font-weight: 600;
}
.meta {
  font-size: 12px;
  color: var(--muted);
  margin-top: 3px;
}
.remain {
  color: var(--gold);
  font-weight: 700;
}
.btns {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
</style>
