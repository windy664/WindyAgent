<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import {
  closeOrder,
  fetchOrders,
  fetchRanking,
  fetchRevenue,
  grantOrder,
  purchaseAvailable,
  refundOrder,
  UnauthorizedError,
  type OrdersPage,
  type PurchaseOrder,
  type RankEntry,
  type Revenue,
} from '../api'

const available = ref<boolean | null>(null)
const error = ref('')
const tab = ref<'orders' | 'ranking' | 'grant'>('orders')

const revenue = ref<Revenue | null>(null)

// 订单
const orders = ref<OrdersPage | null>(null)
const loading = ref(false)
const page = ref(1)
const size = 20
const fPlayer = ref('')
const fStatus = ref('')
const fMethod = ref('')

// 排行
const ranking = ref<RankEntry[]>([])

// 补单
const gPlayer = ref('')
const gAmount = ref<number | null>(null)
const gCmds = ref('')
const granting = ref(false)

const STATUSES = ['PENDING', 'PAID', 'CANCELLED', 'EXPIRED', 'REFUNDED']
const METHODS = ['alipay', 'wechat', 'paypal', 'afdian', 'mock']

function yuan(fen: number): string {
  return '¥' + (fen / 100).toFixed(2)
}
function time(ts: number): string {
  return ts ? new Date(ts).toLocaleString().slice(5, 16) : '—'
}
// 状态 → el-tag 语义色
function tagType(s: string): 'success' | 'warning' | 'danger' | 'info' {
  if (s === 'PAID') return 'success'
  if (s === 'PENDING') return 'warning'
  if (s === 'REFUNDED' || s === 'CANCELLED' || s === 'EXPIRED') return 'danger'
  return 'info'
}
function handle(e: unknown) {
  if (e instanceof UnauthorizedError) return // 请求层集中登出
  error.value = (e as Error).message
}

async function init() {
  available.value = (await purchaseAvailable()).available
  if (!available.value) return
  await loadRevenue()
  await loadOrders()
}

async function loadRevenue() {
  try {
    revenue.value = await fetchRevenue()
  } catch (e) {
    handle(e)
  }
}

async function loadOrders() {
  loading.value = true
  try {
    orders.value = await fetchOrders(page.value, size, {
      status: fStatus.value,
      method: fMethod.value,
      player: fPlayer.value.trim(),
    })
    error.value = ''
  } catch (e) {
    handle(e)
  } finally {
    loading.value = false
  }
}
function query() {
  page.value = 1
  loadOrders()
}
function gotoPage(p: number) {
  page.value = p
  loadOrders()
}

async function cancel(o: PurchaseOrder) {
  try {
    await ElMessageBox.confirm(`确认取消订单 ${o.orderId}？`, '取消订单', {
      type: 'warning',
      confirmButtonText: '取消订单',
      cancelButtonText: '返回',
    })
  } catch {
    return // 用户放弃
  }
  try {
    await closeOrder(o.orderId)
    ElMessage.success('订单已取消')
    await loadOrders()
  } catch (e) {
    handle(e)
  }
}
async function refund(o: PurchaseOrder) {
  try {
    await ElMessageBox.confirm(`确认退款订单 ${o.orderId}？此操作不可逆。`, '退款', {
      type: 'warning',
      confirmButtonText: '确认退款',
      cancelButtonText: '返回',
    })
  } catch {
    return
  }
  try {
    await refundOrder(o.orderId)
    ElMessage.success('已退款')
    await loadOrders()
    await loadRevenue()
  } catch (e) {
    handle(e)
  }
}

async function loadRanking() {
  try {
    ranking.value = await fetchRanking(20)
  } catch (e) {
    handle(e)
  }
}

async function doGrant() {
  const player = gPlayer.value.trim()
  const amount = gAmount.value
  if (!player || !amount || amount <= 0) {
    ElMessage.warning('请填写玩家名和正确金额')
    return
  }
  const cmds = gCmds.value.split('\n').map((c) => c.trim()).filter(Boolean)
  granting.value = true
  try {
    const r = await grantOrder(player, amount, cmds)
    ElMessage.success(`已补单 ${r.orderId}（${player} · ¥${amount}）`)
    gPlayer.value = ''
    gAmount.value = null
    gCmds.value = ''
    await loadRevenue()
  } catch (e) {
    if (e instanceof UnauthorizedError) return
    ElMessage.error((e as Error).message)
  } finally {
    granting.value = false
  }
}

function setTab(t: 'orders' | 'ranking' | 'grant') {
  tab.value = t
  if (t === 'ranking' && ranking.value.length === 0) loadRanking()
}

onMounted(init)
</script>

<template>
  <div class="board">
    <div class="bhead">
      <div>
        <h2>🛒 充值管理</h2>
        <p>WindyPurchase 订单 · 收入统计 · 补单</p>
      </div>
      <div class="tools"><el-button @click="init">刷新</el-button></div>
    </div>

    <div v-if="available === false" class="build">
      <div class="big">🔌</div>
      <h2>未安装 WindyPurchase</h2>
      <p>请先安装 WindyPurchase 插件以启用充值管理功能</p>
    </div>

    <template v-else-if="available">
      <p v-if="error" class="err">{{ error }}</p>

      <!-- KPI（保留原版玻璃卡）-->
      <div class="kpis" v-if="revenue">
        <div class="kpi glass"><div class="ic">💰</div><div class="v">{{ yuan(revenue.todayAmount) }}</div><div class="k">今日收入</div></div>
        <div class="kpi glass"><div class="ic">📈</div><div class="v">{{ yuan(revenue.totalAmount) }}</div><div class="k">总收入</div></div>
        <div class="kpi glass"><div class="ic">📦</div><div class="v">{{ revenue.totalOrders }}</div><div class="k">总订单</div></div>
      </div>

      <!-- tabs（分离胶囊按钮，沿用原版审美：激活=渐变，未激活=玻璃）-->
      <div class="ptabs">
        <el-button :type="tab === 'orders' ? 'primary' : 'default'" @click="setTab('orders')">📋 订单</el-button>
        <el-button :type="tab === 'ranking' ? 'primary' : 'default'" @click="setTab('ranking')">🏆 排行</el-button>
        <el-button :type="tab === 'grant' ? 'primary' : 'default'" @click="setTab('grant')">➕ 补单</el-button>
      </div>

      <!-- 订单 -->
      <div v-if="tab === 'orders'">
        <div class="filters">
          <el-input v-model="fPlayer" placeholder="玩家名" clearable style="width: 150px" @keyup.enter="query" />
          <el-select v-model="fStatus" placeholder="全部状态" clearable style="width: 140px">
            <el-option v-for="s in STATUSES" :key="s" :label="s" :value="s" />
          </el-select>
          <el-select v-model="fMethod" placeholder="全部方式" clearable style="width: 140px">
            <el-option v-for="m in METHODS" :key="m" :label="m" :value="m" />
          </el-select>
          <el-button type="primary" @click="query">查询</el-button>
        </div>

        <div class="panel glass" style="padding: 6px">
          <el-table :data="orders?.data || []" v-loading="loading" empty-text="无订单" style="width: 100%">
            <el-table-column prop="orderId" label="订单号" min-width="170">
              <template #default="{ row }"><span class="mono">{{ row.orderId }}</span></template>
            </el-table-column>
            <el-table-column prop="playerName" label="玩家" min-width="100" />
            <el-table-column label="金额" align="right" width="110">
              <template #default="{ row }">{{ yuan(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="paymentMethod" label="方式" width="90" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="tagType(row.status)" effect="plain" round size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="时间" width="120">
              <template #default="{ row }">{{ time(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button v-if="row.status === 'PENDING'" link type="primary" @click="cancel(row)">取消</el-button>
                <el-button v-if="row.status === 'PAID'" link type="danger" @click="refund(row)">退款</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="orders && orders.total > size" class="pages">
          <el-pagination
            background
            layout="prev, pager, next"
            :current-page="page"
            :page-size="size"
            :total="orders.total"
            @current-change="gotoPage"
          />
        </div>
      </div>

      <!-- 排行 -->
      <div v-else-if="tab === 'ranking'" class="panel glass" style="max-width: 520px">
        <div v-for="(r, i) in ranking" :key="r.playerId" class="rank-row">
          <span class="rk" :class="{ top: i < 3 }">{{ i + 1 }}</span>
          <span class="rn">{{ r.playerName || r.playerId }}</span>
          <span class="ra">{{ yuan(r.totalAmount) }}</span>
        </div>
        <div v-if="ranking.length === 0" class="muted">暂无数据</div>
      </div>

      <!-- 补单 -->
      <div v-else-if="tab === 'grant'" class="panel glass" style="max-width: 460px">
        <el-form label-position="top">
          <el-form-item label="玩家名">
            <el-input v-model="gPlayer" placeholder="玩家名" />
          </el-form-item>
          <el-form-item label="金额（元）">
            <el-input-number v-model="gAmount" :min="1" :controls="false" placeholder="金额" style="width: 100%" />
          </el-form-item>
          <el-form-item label="命令（每行一条，%player% 为玩家名）">
            <el-input v-model="gCmds" type="textarea" :rows="3" placeholder="give %player% diamond 64" />
          </el-form-item>
          <el-button type="primary" :loading="granting" @click="doGrant">确认补单</el-button>
        </el-form>
      </div>
    </template>

    <div v-else class="muted" style="padding: 40px; text-align: center">检测中…</div>
  </div>
</template>

<style scoped>
.ptabs {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}
/* el-button 相邻自带 12px 左外边距，flex+gap 下清掉避免叠加 */
.ptabs :deep(.el-button + .el-button) {
  margin-left: 0;
}
.filters {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.mono {
  font-family: Consolas, monospace;
  font-size: 12px;
}
.pages {
  display: flex;
  justify-content: center;
  margin-top: 14px;
}
.rank-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 4px;
  border-bottom: 1px solid var(--line);
}
.rk {
  width: 24px;
  text-align: center;
  font-weight: 800;
  color: var(--mut);
}
.rk.top {
  color: var(--gold);
}
.rn {
  flex: 1;
  font-weight: 700;
}
.ra {
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}
</style>
