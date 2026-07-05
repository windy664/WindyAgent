<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElButton, ElMessage, ElMessageBox, ElTable, ElTableColumn } from 'element-plus'
import {
  fetchPlayers,
  playerAction,
  type PlayerAction,
  type PlayersData,
} from '../api'

const data = ref<PlayersData>({ columns: [], actions: [], rows: [] })
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    data.value = await fetchPlayers()
  } finally {
    loading.value = false
  }
}

function nameOf(row: Record<string, unknown>): string {
  return String(row.name ?? '')
}

async function doAction(a: PlayerAction, row: Record<string, unknown>) {
  const player = nameOf(row)
  // 破坏性操作二次确认
  if (a.danger) {
    try {
      await ElMessageBox.confirm(`确认对「${player}」执行「${a.label}」？`, a.label, {
        type: 'warning',
        confirmButtonText: a.label,
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
  }
  try {
    const r = await playerAction(a.id, player)
    ElMessage.success(r.result || '已执行')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(load)
</script>

<template>
  <div class="board">
    <div class="bhead">
      <div>
        <h2>🧑 玩家管理</h2>
        <p>在线玩家实时列表 · 踢出等操作 · 更多集成信息（经济/领地/封禁…）随插件接入自动出现</p>
      </div>
      <div class="tools"><el-button :loading="loading" @click="load">刷新</el-button></div>
    </div>

    <div class="kpis">
      <div class="kpi glass"><div class="ic">🧑</div><div class="v">{{ data.rows.length }}</div><div class="k">当前在线</div></div>
    </div>

    <div class="panel glass" style="padding: 6px">
      <el-table :data="data.rows" v-loading="loading" empty-text="当前无玩家在线" style="width: 100%">
        <el-table-column label="玩家" min-width="140">
          <template #default="{ row }"><span style="font-weight: 700">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column label="子服" min-width="110">
          <template #default="{ row }">{{ row.server ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="延迟" width="90">
          <template #default="{ row }">{{ row.ping != null ? Math.round(Number(row.ping)) + 'ms' : '—' }}</template>
        </el-table-column>
        <!-- 各插件贡献的扩展列（经济余额/领地数/封禁状态…），后端有则自动出现 -->
        <el-table-column v-for="c in data.columns" :key="c.key" :label="c.label" min-width="110">
          <template #default="{ row }">{{ row[c.key] ?? '—' }}</template>
        </el-table-column>
        <!-- 各来源贡献的操作按钮 -->
        <el-table-column v-if="data.actions.length" label="操作" :width="Math.max(120, data.actions.length * 76)">
          <template #default="{ row }">
            <el-button
              v-for="a in data.actions"
              :key="a.id"
              link
              :type="a.danger ? 'danger' : 'primary'"
              @click="doAction(a, row)"
            >{{ a.label }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>
