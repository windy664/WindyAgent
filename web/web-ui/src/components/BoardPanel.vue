<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import {
  Chart,
  type ChartConfiguration,
  registerables,
} from 'chart.js'
import {
  fetchBoardStats,
  fetchWords,
  UnauthorizedError,
  type BoardStats,
} from '../api'
import { storeToRefs } from 'pinia'
import { useChromeStore } from '../stores/chrome'

Chart.register(...registerables)

// 当前操作子服改由 chrome store 提供（去掉父级 prop 透传，路由下仍 reactive）。
const { currentServer: server } = storeToRefs(useChromeStore())
const emit = defineEmits<{ (e: 'unauthorized'): void }>()

const stats = ref<BoardStats | null>(null)
const noData = ref(false)
const error = ref('')
const wcSource = ref<'cmd' | 'chat'>('cmd')
const words = ref<{ word: string; count: number }[]>([])

const trendEl = ref<HTMLCanvasElement | null>(null)
const behEl = ref<HTMLCanvasElement | null>(null)
const segEl = ref<HTMLCanvasElement | null>(null)
const cmdEl = ref<HTMLCanvasElement | null>(null)
const topEl = ref<HTMLCanvasElement | null>(null)
let charts: Chart[] = []

const PAL = ['#ff8fc8', '#b79bff', '#7fd7ff', '#86efc6', '#ffd98a', '#ff7a98']

function destroyCharts() {
  charts.forEach((c) => c.destroy())
  charts = []
}

function line(el: HTMLCanvasElement, labels: string[], data: number[]) {
  charts.push(
    new Chart(el, {
      type: 'line',
      data: {
        labels,
        datasets: [
          {
            data,
            borderColor: '#b79bff',
            backgroundColor: 'rgba(183,155,255,.16)',
            fill: true,
            tension: 0.35,
            pointRadius: 3,
            pointBackgroundColor: '#ff8fc8',
          },
        ],
      },
      options: gridOpts(false),
    } as ChartConfiguration),
  )
}
function bar(el: HTMLCanvasElement, labels: string[], data: number[], color: string | string[]) {
  charts.push(
    new Chart(el, {
      type: 'bar',
      data: { labels, datasets: [{ data, backgroundColor: color, borderRadius: 7, barThickness: 16 }] },
      options: { indexAxis: 'y', ...gridOpts(true) },
    } as ChartConfiguration),
  )
}
function donut(el: HTMLCanvasElement, labels: string[], data: number[]) {
  charts.push(
    new Chart(el, {
      type: 'doughnut',
      data: { labels, datasets: [{ data, backgroundColor: PAL, borderColor: 'rgba(0,0,0,.25)', borderWidth: 2, hoverOffset: 6 }] },
      options: { cutout: '60%', plugins: { legend: { position: 'right', labels: { usePointStyle: true, padding: 11, boxWidth: 8, color: '#f3f0ff', font: { weight: 700 } } } } },
    } as ChartConfiguration),
  )
}
function gridOpts(yTicks: boolean) {
  return {
    plugins: { legend: { display: false } },
    scales: {
      x: { grid: { color: 'rgba(255,255,255,.06)' }, border: { display: false }, ticks: { color: '#bdb6da' } },
      y: { grid: { display: !yTicks, color: 'rgba(255,255,255,.06)' }, border: { display: false }, beginAtZero: true, ticks: { color: yTicks ? '#f3f0ff' : '#bdb6da', font: { weight: 700 as const } } },
    },
  }
}

async function load() {
  if (!server.value) {
    noData.value = true
    return
  }
  try {
    stats.value = await fetchBoardStats(server.value)
    noData.value = false
    error.value = ''
    await nextTick()
    drawAll()
    loadWords()
  } catch (e) {
    if (e instanceof UnauthorizedError) emit('unauthorized')
    else {
      // 后端未实现该端点 → 占位，不报错刷屏
      noData.value = true
      stats.value = null
    }
  }
}

function drawAll() {
  destroyCharts()
  const s = stats.value
  if (!s) return
  if (s.trend?.length && trendEl.value) line(trendEl.value, s.trend.map((x) => x.day), s.trend.map((x) => x.peak))
  if (s.behavior?.length && behEl.value) donut(behEl.value, s.behavior.map((x) => x.label), s.behavior.map((x) => x.value))
  if (s.segments?.length && segEl.value) bar(segEl.value, s.segments.map((x) => x.label), s.segments.map((x) => x.value), PAL)
  if (s.topCommands?.length && cmdEl.value) bar(cmdEl.value, s.topCommands.map((x) => x.word), s.topCommands.map((x) => x.count), 'rgba(127,215,255,.85)')
  if (s.topPlaytime?.length && topEl.value) bar(topEl.value, s.topPlaytime.map((x) => x.name), s.topPlaytime.map((x) => x.playtimeMin), '#b79bff')
}

async function loadWords() {
  try {
    words.value = (await fetchWords(server.value, wcSource.value, 60)).filter((w) => w.word && w.count > 0)
  } catch {
    words.value = []
  }
}
function switchWords(src: 'cmd' | 'chat') {
  wcSource.value = src
  loadWords()
}
// 词云用纯 CSS 标签云（按词频缩放字号），避免再引一个 canvas 库
function fontSize(count: number): number {
  const max = Math.max(...words.value.map((w) => w.count), 1)
  return 12 + Math.round((count / max) * 26)
}

watch(server, load)
onMounted(load)
</script>

<template>
  <div class="board">
    <div class="bhead">
      <div>
        <h2>📊 行为看板</h2>
        <p>玩家行为与活跃度总览（数据来自子服实时采集）</p>
      </div>
      <div class="tools">
        <span class="pill">📅 最近 7 天</span>
        <button class="btn ghost sm" @click="load">刷新</button>
      </div>
    </div>

    <div v-if="noData" class="ph" style="height: 300px">
      {{ server ? '该子服暂无行为数据，或后端未启用采集（需子服上报）。' : '请先在顶栏选择一个子服。' }}
    </div>

    <template v-else>
      <!-- KPI -->
      <div class="kpis" v-if="stats?.kpis?.length">
        <div v-for="(k, i) in stats.kpis" :key="i" class="kpi glass">
          <div class="ic">{{ k.icon || '📊' }}</div>
          <div class="v">{{ k.value }}</div>
          <div class="k">{{ k.label }}</div>
        </div>
      </div>

      <div class="g2">
        <div class="panel glass"><h3>📈 在线人数趋势 <span class="tag">近7天峰值</span></h3><canvas ref="trendEl" height="180"></canvas></div>
        <div class="panel glass"><h3>🔥 在线时段分布 <span class="tag">周 × 小时</span></h3><div class="ph" style="height: 180px">热力图待子服上报</div></div>
      </div>
      <div class="g3">
        <div class="panel glass"><h3>🎮 玩家行为分布</h3><canvas ref="behEl" height="190"></canvas></div>
        <div class="panel glass"><h3>📉 玩家留存</h3><div class="ph" style="height: 190px">待接入按日留存计算</div></div>
        <div class="panel glass"><h3>🪜 活跃度分层</h3><canvas ref="segEl" height="190"></canvas></div>
      </div>
      <div class="g2">
        <div class="panel glass"><h3>⌨️ 热门命令 <span class="tag">词频</span></h3><canvas ref="cmdEl" height="200"></canvas></div>
        <div class="panel glass"><h3>🏆 在线时长 Top5</h3><canvas ref="topEl" height="200"></canvas></div>
      </div>

      <!-- 词云（CSS 标签云）-->
      <div class="panel glass" style="margin-top: 16px">
        <h3>
          ☁️ 词云
          <span>
            <button class="btn ghost sm" :class="{ on: wcSource === 'cmd' }" @click="switchWords('cmd')">命令</button>
            <button class="btn ghost sm" :class="{ on: wcSource === 'chat' }" style="margin-left: 6px" @click="switchWords('chat')">聊天</button>
          </span>
        </h3>
        <div v-if="words.length" class="wcloud">
          <span v-for="w in words" :key="w.word" :style="{ fontSize: fontSize(w.count) + 'px', color: PAL[w.word.length % PAL.length] }">{{ w.word }}</span>
        </div>
        <div v-else class="ph" style="height: 120px">暂无词频数据（命令词云始终采集；聊天词云需子服 track-chat 开启）</div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.g3 {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}
.wcloud {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  align-items: center;
  justify-content: center;
  padding: 24px;
  min-height: 120px;
}
.wcloud span {
  font-weight: 800;
  line-height: 1;
}
h3 .btn.on {
  background: var(--grad);
  color: #fff;
}
@media (max-width: 980px) {
  .g3 {
    grid-template-columns: 1fr;
  }
}
</style>
