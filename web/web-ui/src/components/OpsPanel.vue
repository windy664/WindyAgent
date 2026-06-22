<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  fetchAlerts,
  fetchDimTps,
  fetchHealth,
  fetchMods,
  fetchServerDetail,
  fetchServers,
  UnauthorizedError,
  type AlertEntry,
  type HealthEntry,
  type ServerDetail,
} from '../api'

const emit = defineEmits<{ (e: 'unauthorized'): void }>()

const health = ref<HealthEntry[]>([])
const alerts = ref<AlertEntry[]>([])
const error = ref('')

const selected = ref<string>('')
const detail = ref<ServerDetail | null>(null)
const detailLoading = ref(false)
const extra = ref('')
let timer: number | undefined

// KPI 汇总
const kpiServers = computed(() => health.value.filter((h) => h.connected).length)
const kpiPlayers = computed(() => health.value.reduce((s, h) => s + (h.players ?? 0), 0))
const kpiTps = computed(() => {
  const ts = health.value.map((h) => h.tps).filter((t): t is number => t != null)
  return ts.length ? (ts.reduce((a, b) => a + b, 0) / ts.length).toFixed(1) : '—'
})

function handle(e: unknown) {
  if (e instanceof UnauthorizedError) {
    emit('unauthorized')
    stop()
  } else {
    error.value = (e as Error).message
  }
}

async function load() {
  try {
    const [h, a] = await Promise.all([fetchHealth(), fetchAlerts()])
    if (h.length === 0) {
      const names = await fetchServers()
      health.value = names.map((s) => ({ server: s, status: 'ok' as const, connected: true }))
    } else {
      health.value = h
    }
    alerts.value = a
    error.value = ''
  } catch (e) {
    handle(e)
  }
}

async function openDetail(server: string) {
  selected.value = server
  detail.value = null
  extra.value = ''
  detailLoading.value = true
  try {
    detail.value = await fetchServerDetail(server)
  } catch (e) {
    if (e instanceof UnauthorizedError) emit('unauthorized')
    else error.value = `${server}：${(e as Error).message}`
  } finally {
    detailLoading.value = false
  }
}
function closeDetail() {
  selected.value = ''
  detail.value = null
}

async function showMods() {
  if (!selected.value) return
  try {
    extra.value = (await fetchMods(selected.value)).text
  } catch (e) {
    handle(e)
  }
}
async function showDimTps() {
  if (!selected.value) return
  try {
    extra.value = (await fetchDimTps(selected.value)).text
  } catch (e) {
    handle(e)
  }
}

function uptime(sec: number): string {
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  return h > 0 ? `${h}h ${m}m` : `${m}m`
}
function statusLabel(s: string): string {
  return { ok: '正常', lag: '卡顿', mem: '内存高', offline: '离线' }[s] || s
}
function statusColor(s: string): string {
  return { ok: '#86efc6', lag: '#ffd98a', mem: '#ffb07a', offline: '#ff7a98' }[s] || '#86efc6'
}
function time(ts: number): string {
  return new Date(ts).toLocaleTimeString()
}

function stop() {
  if (timer) {
    clearInterval(timer)
    timer = undefined
  }
}
onMounted(() => {
  load()
  timer = window.setInterval(load, 5000)
})
onUnmounted(stop)
</script>

<template>
  <div class="board">
    <div class="bhead">
      <div>
        <h2>🛰️ 运维总览</h2>
        <p>子服健康实时巡检 · 异常告警与处置建议</p>
      </div>
      <div class="tools">
        <span class="pill"><span class="dot"></span>哨兵</span>
        <button class="btn ghost sm" @click="load">刷新</button>
      </div>
    </div>

    <p v-if="error" class="err">{{ error }}</p>

    <!-- KPI -->
    <div class="kpis">
      <div class="kpi glass"><div class="ic">🖥️</div><div class="v">{{ kpiServers }}</div><div class="k">在线子服</div></div>
      <div class="kpi glass"><div class="ic">🧑</div><div class="v">{{ kpiPlayers }}</div><div class="k">在线玩家</div></div>
      <div class="kpi glass"><div class="ic">⚡</div><div class="v">{{ kpiTps }}</div><div class="k">平均 TPS</div></div>
      <div class="kpi glass"><div class="ic">🚨</div><div class="v">{{ alerts.length }}</div><div class="k">当前告警</div></div>
    </div>

    <!-- 服务器健康 -->
    <div class="panel glass">
      <h3>🖥️ 服务器 <span class="tag">点击卡片看详情</span></h3>
      <div class="hgrid">
        <div
          v-for="h in health"
          :key="h.server"
          class="hcard"
          :class="{ active: selected === h.server }"
          @click="openDetail(h.server)"
        >
          <div class="hn">
            <span class="sdot" :style="{ background: statusColor(h.status) }"></span>
            {{ h.server }}
            <span class="hstatus" :style="{ color: statusColor(h.status) }">{{ statusLabel(h.status) }}</span>
          </div>
          <div class="hs">
            <div v-if="h.tps != null"><div class="v">{{ h.tps.toFixed(1) }}</div><div class="k">TPS</div></div>
            <div v-if="h.players != null"><div class="v">{{ h.players }}</div><div class="k">玩家</div></div>
            <div v-if="h.memPct != null"><div class="v">{{ h.memPct }}%</div><div class="k">内存</div></div>
          </div>
          <div v-if="h.platform || h.mcVersion" class="hmeta">
            {{ h.platform }} {{ h.mcVersion }}<span v-if="h.modCount != null && h.modCount >= 0"> · {{ h.modCount }} mods</span>
          </div>
        </div>
        <div v-if="health.length === 0" class="muted">没有已连接的子服</div>
      </div>
    </div>

    <!-- 子服详情 -->
    <div v-if="selected" class="panel glass" style="margin-top: 16px">
      <h3>
        📋 {{ selected }} 详情
        <span class="tag" style="cursor: pointer" @click="closeDetail">收起 ✕</span>
      </h3>
      <div v-if="detailLoading" class="muted">加载中…</div>
      <template v-else-if="detail">
        <div class="dgrid">
          <div class="dcell"><div class="dv">{{ detail.tps.toFixed(1) }}</div><div class="dk">TPS</div></div>
          <div class="dcell"><div class="dv">{{ detail.online }}/{{ detail.maxPlayers }}</div><div class="dk">在线</div></div>
          <div class="dcell"><div class="dv">{{ detail.memUsedMb }}</div><div class="dk">内存MB</div></div>
          <div class="dcell"><div class="dv">{{ uptime(detail.uptimeSec) }}</div><div class="dk">运行</div></div>
          <div class="dcell"><div class="dv">{{ detail.pluginCount }}</div><div class="dk">插件</div></div>
          <div class="dcell" v-if="detail.modCount >= 0"><div class="dv">{{ detail.modCount }}</div><div class="dk">mods</div></div>
        </div>
        <div class="meta-row">
          {{ detail.platform }} {{ detail.mcVersion }} · {{ detail.onlineMode ? '正版验证' : '离线模式' }}<span v-if="detail.whitelist"> · 白名单开</span>
        </div>
        <div class="actions">
          <button class="btn ghost sm" @click="showMods">查看 mods</button>
          <button class="btn ghost sm" @click="showDimTps">维度 TPS</button>
        </div>
        <pre v-if="extra" class="extra">{{ extra }}</pre>
        <div class="cols">
          <div>
            <h4>世界（{{ detail.worlds.length }}）</h4>
            <table>
              <tr v-for="w in detail.worlds" :key="w.name">
                <td>{{ w.name }}</td><td>{{ w.players }}人</td><td>{{ w.entities }}实体</td><td>{{ w.chunks }}区块</td><td>{{ w.weather }}</td>
              </tr>
            </table>
          </div>
          <div>
            <h4>在线玩家（{{ detail.players.length }}）</h4>
            <table>
              <tr v-for="p in detail.players" :key="p.name">
                <td>{{ p.name }}</td><td>{{ p.world }}</td><td>{{ p.ping }}ms</td><td>{{ p.gamemode }}</td>
              </tr>
              <tr v-if="detail.players.length === 0"><td class="muted">无人在线</td></tr>
            </table>
          </div>
        </div>
      </template>
    </div>

    <!-- 告警 -->
    <div class="panel glass" style="margin-top: 16px">
      <h3>🚨 运维告警 / 处置过程 <span class="tag">哨兵自动</span></h3>
      <div v-if="alerts.length === 0" class="muted">无告警 ✨</div>
      <div v-for="(a, i) in alerts" :key="i" class="alert" :class="a.severity">
        <div class="a-head"><b>{{ a.server }}</b> · {{ a.kind }} · {{ time(a.ts) }}</div>
        <div>{{ a.detail }}</div>
        <div v-if="a.advice" class="advice">💡 {{ a.advice }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hcard {
  border-radius: 14px;
  padding: 14px;
  background: var(--panel-2);
  border: 1px solid var(--border);
  cursor: pointer;
  transition: transform 0.2s, border-color 0.2s;
}
.hcard:hover { transform: translateY(-3px); border-color: rgba(183, 155, 255, 0.5); }
.hcard.active { border-color: var(--violet); }
.hn { font-weight: 800; display: flex; align-items: center; gap: 8px; }
.hstatus { font-size: 11px; margin-left: auto; }
.sdot { width: 9px; height: 9px; border-radius: 50%; display: inline-block; flex-shrink: 0; }
.hs { display: flex; gap: 16px; margin-top: 11px; }
.hs .v { font-weight: 800; font-size: 19px; font-variant-numeric: tabular-nums; }
.hs .k { font-size: 11px; color: var(--muted); }
.hmeta { font-size: 11px; color: var(--muted); margin-top: 9px; }

.dgrid { display: grid; grid-template-columns: repeat(auto-fill, minmax(88px, 1fr)); gap: 8px; }
.dcell { background: var(--panel-2); border-radius: 11px; padding: 10px; text-align: center; }
.dv { font-weight: 800; font-size: 17px; font-variant-numeric: tabular-nums; }
.dk { font-size: 11px; color: var(--muted); margin-top: 2px; }
.meta-row { color: var(--muted); font-size: 12px; margin: 12px 0; }
.actions { display: flex; gap: 8px; margin-bottom: 12px; }
.extra { background: rgba(0, 0, 0, 0.32); border-radius: 9px; padding: 10px; white-space: pre-wrap; font-size: 12px; max-height: 260px; overflow-y: auto; margin-bottom: 12px; }
.cols { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
h4 { font-size: 13px; color: var(--muted); margin: 0 0 6px; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
td { padding: 5px 8px; border-bottom: 1px solid var(--border); }
.alert { border: 1px solid var(--border); border-left-width: 4px; border-radius: 10px; padding: 10px 12px; margin-bottom: 8px; background: var(--panel-2); font-size: 13px; }
.alert.high, .alert.critical { border-left-color: var(--red); }
.alert.warn, .alert.medium { border-left-color: var(--gold); }
.a-head { color: var(--muted); margin-bottom: 3px; }
.advice { color: var(--violet); margin-top: 3px; }
.muted { color: var(--muted); }
@media (max-width: 720px) { .cols { grid-template-columns: 1fr; } }
</style>
