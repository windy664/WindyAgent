<script setup lang="ts">
import { onMounted, ref } from 'vue'
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
  type RankEntry,
  type Revenue,
} from '../api'

const emit = defineEmits<{ (e: 'unauthorized'): void }>()

const available = ref<boolean | null>(null)
const error = ref('')
const tab = ref<'orders' | 'ranking' | 'grant'>('orders')

const revenue = ref<Revenue | null>(null)

// 订单
const orders = ref<OrdersPage | null>(null)
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
const gResult = ref('')

const STATUSES = ['PENDING', 'PAID', 'CANCELLED', 'EXPIRED', 'REFUNDED']
const METHODS = ['alipay', 'wechat', 'paypal', 'afdian', 'mock']

function yuan(fen: number): string {
  return '¥' + (fen / 100).toFixed(2)
}
function time(ts: number): string {
  return ts ? new Date(ts).toLocaleString().slice(5, 16) : '—'
}
function handle(e: unknown) {
  if (e instanceof UnauthorizedError) emit('unauthorized')
  else error.value = (e as Error).message
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
  try {
    orders.value = await fetchOrders(page.value, size, {
      status: fStatus.value,
      method: fMethod.value,
      player: fPlayer.value.trim(),
    })
    error.value = ''
  } catch (e) {
    handle(e)
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

async function cancel(id: string) {
  if (!confirm(`取消订单 ${id}？`)) return
  try {
    await closeOrder(id)
    await loadOrders()
  } catch (e) {
    handle(e)
  }
}
async function refund(id: string) {
  if (!confirm(`退款订单 ${id}？此操作不可逆`)) return
  try {
    await refundOrder(id)
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
    gResult.value = '请填写玩家名和金额'
    return
  }
  const cmds = gCmds.value.split('\n').map((c) => c.trim()).filter(Boolean)
  try {
    const r = await grantOrder(player, amount, cmds)
    gResult.value = `✅ 已补单 ${r.orderId}（${player} · ¥${amount}）`
    gPlayer.value = ''
    gAmount.value = null
    gCmds.value = ''
    await loadRevenue()
  } catch (e) {
    if (e instanceof UnauthorizedError) emit('unauthorized')
    else gResult.value = '❌ ' + (e as Error).message
  }
}

function switchTab(t: 'orders' | 'ranking' | 'grant') {
  tab.value = t
  if (t === 'ranking' && ranking.value.length === 0) loadRanking()
}

const totalPages = () => (orders.value ? Math.ceil(orders.value.total / orders.value.size) : 1)

onMounted(init)
</script>

<template>
  <div class="board">
    <div class="bhead">
      <div>
        <h2>🛒 充值管理</h2>
        <p>WindyPurchase 订单 · 收入统计 · 补单</p>
      </div>
      <div class="tools"><button class="btn ghost sm" @click="init">刷新</button></div>
    </div>

    <div v-if="available === false" class="build">
      <div class="big">🔌</div>
      <h2>未安装 WindyPurchase</h2>
      <p>请先安装 WindyPurchase 插件以启用充值管理功能</p>
    </div>

    <template v-else-if="available">
      <p v-if="error" class="err">{{ error }}</p>

      <!-- KPI -->
      <div class="kpis" v-if="revenue">
        <div class="kpi glass"><div class="ic">💰</div><div class="v">{{ yuan(revenue.todayAmount) }}</div><div class="k">今日收入</div></div>
        <div class="kpi glass"><div class="ic">📈</div><div class="v">{{ yuan(revenue.totalAmount) }}</div><div class="k">总收入</div></div>
        <div class="kpi glass"><div class="ic">📦</div><div class="v">{{ revenue.totalOrders }}</div><div class="k">总订单</div></div>
      </div>

      <!-- tabs -->
      <div class="ptabs">
        <button class="btn sm" :class="{ ghost: tab !== 'orders' }" @click="switchTab('orders')">📋 订单</button>
        <button class="btn sm" :class="{ ghost: tab !== 'ranking' }" @click="switchTab('ranking')">🏆 排行</button>
        <button class="btn sm" :class="{ ghost: tab !== 'grant' }" @click="switchTab('grant')">➕ 补单</button>
      </div>

      <!-- 订单 -->
      <div v-if="tab === 'orders'">
        <div class="filters">
          <input v-model="fPlayer" placeholder="玩家名" style="width: 140px" />
          <select v-model="fStatus" style="width: 130px"><option value="">全部状态</option><option v-for="s in STATUSES" :key="s" :value="s">{{ s }}</option></select>
          <select v-model="fMethod" style="width: 130px"><option value="">全部方式</option><option v-for="m in METHODS" :key="m" :value="m">{{ m }}</option></select>
          <button class="btn sm" @click="query">查询</button>
        </div>
        <div class="panel glass" style="padding: 0; overflow: hidden">
          <table class="otbl">
            <thead>
              <tr><th>订单号</th><th>玩家</th><th class="r">金额</th><th>方式</th><th>状态</th><th>时间</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="o in orders?.data || []" :key="o.orderId">
                <td class="mono">{{ o.orderId }}</td>
                <td>{{ o.playerName }}</td>
                <td class="r">{{ yuan(o.amount) }}</td>
                <td>{{ o.paymentMethod }}</td>
                <td><span class="st" :class="o.status">{{ o.status }}</span></td>
                <td>{{ time(o.createdAt) }}</td>
                <td>
                  <button v-if="o.status === 'PENDING'" class="lnk" @click="cancel(o.orderId)">取消</button>
                  <button v-if="o.status === 'PAID'" class="lnk red" @click="refund(o.orderId)">退款</button>
                </td>
              </tr>
              <tr v-if="orders && orders.data.length === 0"><td colspan="7" class="muted" style="text-align: center; padding: 20px">无订单</td></tr>
            </tbody>
          </table>
        </div>
        <div v-if="orders && totalPages() > 1" class="pages">
          <button class="btn ghost sm" :disabled="page <= 1" @click="gotoPage(page - 1)">‹</button>
          <span>{{ page }} / {{ totalPages() }}</span>
          <button class="btn ghost sm" :disabled="page >= totalPages()" @click="gotoPage(page + 1)">›</button>
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
        <label class="muted" style="font-size: 12px">玩家名</label>
        <input v-model="gPlayer" placeholder="玩家名" />
        <label class="muted" style="font-size: 12px; display: block; margin-top: 8px">金额（元）</label>
        <input v-model.number="gAmount" type="number" min="1" placeholder="金额" />
        <label class="muted" style="font-size: 12px; display: block; margin-top: 8px">命令（每行一条，%player% 为玩家名）</label>
        <textarea v-model="gCmds" rows="3" placeholder="give %player% diamond 64"></textarea>
        <button class="btn" style="margin-top: 10px" @click="doGrant">确认补单</button>
        <div v-if="gResult" style="margin-top: 8px; font-size: 13px">{{ gResult }}</div>
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
.filters {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.otbl {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.otbl th {
  text-align: left;
  padding: 9px 12px;
  background: rgba(255, 255, 255, 0.04);
  color: var(--mut);
  font-weight: 700;
}
.otbl th.r,
.otbl td.r {
  text-align: right;
}
.otbl td {
  padding: 8px 12px;
  border-bottom: 1px solid var(--line);
}
.mono {
  font-family: Consolas, monospace;
  font-size: 12px;
}
.st {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 8px;
  background: var(--glass2);
}
.st.PAID {
  color: var(--mint);
}
.st.PENDING {
  color: var(--gold);
}
.st.REFUNDED,
.st.CANCELLED,
.st.EXPIRED {
  color: var(--red);
}
.lnk {
  background: none;
  border: none;
  color: var(--violet);
  cursor: pointer;
  font: inherit;
  padding: 0;
}
.lnk.red {
  color: var(--red);
}
.pages {
  display: flex;
  gap: 10px;
  justify-content: center;
  align-items: center;
  margin-top: 12px;
  font-size: 13px;
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
