<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  clearToken,
  fetchAlerts,
  fetchApprovals,
  fetchServers,
  fetchSetupState,
  fetchStatus,
  getToken,
  type AlertEntry,
  type ProxyStatus,
} from './api'
import TokenGate from './components/TokenGate.vue'
import ChatPanel from './components/ChatPanel.vue'
import SkillsPanel from './components/SkillsPanel.vue'
import ApprovalsPanel from './components/ApprovalsPanel.vue'
import UsagePanel from './components/UsagePanel.vue'
import KnowledgePanel from './components/KnowledgePanel.vue'
import OpsPanel from './components/OpsPanel.vue'
import TasksPanel from './components/TasksPanel.vue'
import PurchasePanel from './components/PurchasePanel.vue'
import BoardPanel from './components/BoardPanel.vue'
import SetupWizard from './components/SetupWizard.vue'

const authed = ref(!!getToken())
const needSetup = ref(false)
// 随机二次元背景（与原版一致；失败则回退 CSS 渐变）
const bgUrl = ref('')
onMounted(() => {
  bgUrl.value = `url('https://t.alcy.cc/moe?_=${Date.now()}')`
})
const status = ref<ProxyStatus>({})
const servers = ref<string[]>([])
const currentServer = ref('')
const pendingCount = ref(0)
const alerts = ref<AlertEntry[]>([])
const bellOpen = ref(false)
let poll: number | undefined

type View =
  | 'ops' | 'chat' | 'board' | 'kb' | 'players'
  | 'econ' | 'event' | 'skill' | 'settings' | 'sys' | 'approvals'
const view = ref<View>('ops')

// 侧栏导航 —— 对齐原版（图标 + 标签）。没后端的页用占位。
const navItems: { key: View; icon: string; label: string }[] = [
  { key: 'ops', icon: '🛰️', label: '运维总览' },
  { key: 'chat', icon: '💬', label: 'AI 对话' },
  { key: 'board', icon: '📊', label: '行为看板' },
  { key: 'kb', icon: '📚', label: '知识库' },
  { key: 'players', icon: '🧑', label: '玩家管理' },
  { key: 'econ', icon: '🛒', label: '充值管理' },
  { key: 'event', icon: '⏰', label: '定时任务' },
  { key: 'skill', icon: '🧪', label: '技能扩展' },
  { key: 'settings', icon: '⚙️', label: '设置中心' },
  { key: 'sys', icon: '🖥️', label: '系统健康' },
]

const TITLES: Record<View, string> = {
  ops: '🛰️ 运维总览', chat: '💬 AI 对话', board: '📊 行为看板', kb: '📚 知识库',
  players: '🧑 玩家管理', econ: '🛒 充值管理', event: '⏰ 定时任务', skill: '🧪 技能扩展',
  settings: '⚙️ 设置中心', sys: '🖥️ 系统健康', approvals: '🛡️ 操作审批',
}
const pageTitle = computed(() => TITLES[view.value])

async function refreshChrome() {
  status.value = await fetchStatus()
  servers.value = await fetchServers()
  if (!currentServer.value && servers.value.length) currentServer.value = servers.value[0]
  const ap = await fetchApprovals().catch(() => null)
  pendingCount.value = ap?.pending.length ?? 0
  alerts.value = await fetchAlerts()
}

async function afterAuth() {
  const st = await fetchSetupState()
  needSetup.value = st.configured === false
  if (!needSetup.value) {
    await refreshChrome()
    poll = window.setInterval(refreshChrome, 5000)
  }
}

watch(authed, (v) => {
  if (v) afterAuth()
  else stopPoll()
})
onMounted(() => {
  if (authed.value) afterAuth()
})
function stopPoll() {
  if (poll) {
    clearInterval(poll)
    poll = undefined
  }
}
onUnmounted(stopPoll)

function onUnauthorized() {
  clearToken()
  authed.value = false
}
function logout() {
  clearToken()
  authed.value = false
}
function alertTime(ts: number) {
  return new Date(ts).toLocaleTimeString()
}
</script>

<template>
  <!-- 背景 -->
  <div id="bg" :style="bgUrl ? { backgroundImage: bgUrl } : undefined"></div>
  <div id="scrim"></div>

  <TokenGate v-if="!authed" @authed="authed = true" />
  <SetupWizard v-else-if="needSetup" first-run @unauthorized="onUnauthorized" />

  <template v-else>
    <!-- 顶栏 -->
    <div class="topbar">
      <div class="tbtitle">{{ pageTitle }}</div>
      <div class="grow"></div>
      <div class="pill" title="当前操作的子服" v-if="servers.length">
        <span style="opacity: 0.7">🌐</span>
        <select v-model="currentServer">
          <option v-for="s in servers" :key="s" :value="s">{{ s }}</option>
        </select>
      </div>
      <div class="pill" title="在线玩家">
        <span class="dot"></span>{{ status.onlinePlayers ?? 0 }} 在线
      </div>
      <div
        v-if="pendingCount > 0"
        class="pill"
        style="cursor: pointer; border-color: rgba(255, 122, 152, 0.6); color: #fff"
        @click="view = 'approvals'"
      >
        🛡️ 待审 <b>{{ pendingCount }}</b>
      </div>
      <div class="ticon" title="运维告警" @click="bellOpen = !bellOpen">
        🔔<span v-if="alerts.length" class="bdot">{{ alerts.length }}</span>
      </div>
      <div class="ticon" title="退出登录" @click="logout">🚪</div>

      <div v-if="bellOpen" class="alertpop glass">
        <div v-if="alerts.length === 0" class="muted" style="padding: 16px; text-align: center">
          暂无告警 ✨
        </div>
        <div v-for="(a, i) in alerts" :key="i" class="ai">
          <div style="font-size: 12px; color: var(--muted)">
            <b>{{ a.server }}</b> · {{ a.kind }} · {{ alertTime(a.ts) }}
          </div>
          <div>{{ a.detail }}</div>
          <div v-if="a.advice" style="color: var(--violet); margin-top: 3px">💡 {{ a.advice }}</div>
        </div>
      </div>
    </div>

    <!-- 三栏外壳 -->
    <div class="shell">
      <aside class="sidebar">
        <div class="logo">
          <div class="lg">🌸</div>
          <div>
            <div class="lt">WindyAgent</div>
            <div class="ls">MC服务器智能助手</div>
          </div>
        </div>
        <button
          v-for="item in navItems"
          :key="item.key"
          :class="['nav', { on: view === item.key }]"
          @click="view = item.key"
        >
          <span class="i">{{ item.icon }}</span><span>{{ item.label }}</span>
        </button>
        <div class="grow1"></div>
        <div class="srvpanel glass">
          <div class="nm">🟢 {{ status.name || 'WindyAgent' }}</div>
          <div class="rw"><span>在线</span><span>{{ status.onlinePlayers ?? '—' }}</span></div>
          <div class="rw"><span>服务端</span><span>{{ status.proxyVersion || status.platform || 'Velocity' }}</span></div>
        </div>
      </aside>

      <main class="content">
        <OpsPanel v-if="view === 'ops'" @unauthorized="onUnauthorized" />
        <ChatPanel v-else-if="view === 'chat'" @unauthorized="onUnauthorized" />
        <BoardPanel v-else-if="view === 'board'" :server="currentServer" @unauthorized="onUnauthorized" />
        <KnowledgePanel v-else-if="view === 'kb'" @unauthorized="onUnauthorized" />
        <TasksPanel v-else-if="view === 'event'" @unauthorized="onUnauthorized" />
        <PurchasePanel v-else-if="view === 'econ'" @unauthorized="onUnauthorized" />
        <SkillsPanel v-else-if="view === 'skill'" @unauthorized="onUnauthorized" />
        <UsagePanel v-else-if="view === 'sys'" @unauthorized="onUnauthorized" />
        <SetupWizard v-else-if="view === 'settings'" @unauthorized="onUnauthorized" @done="view = 'ops'" />
        <ApprovalsPanel v-else-if="view === 'approvals'" @unauthorized="onUnauthorized" />

        <!-- 暂无后端的页：占位（与原版一致的版式） -->
        <div v-else class="board">
          <div class="bhead">
            <div>
              <h2>{{ pageTitle }}</h2>
              <p v-if="view === 'players'">玩家管理（在线表 / 踢人 / 封禁）建设中，先用「AI 对话」自然语言操作。</p>
            </div>
          </div>
          <div class="panel glass">
            <div style="text-align: center; padding: 48px; color: var(--muted)">
              <div style="font-size: 44px">🚧</div>
              <p>该面板建设中</p>
            </div>
          </div>
        </div>
      </main>
    </div>
  </template>
</template>

<style scoped>
#bg {
  position: fixed;
  inset: 0;
  z-index: -2;
  background-size: cover;
  background-position: center;
  background-image: linear-gradient(135deg, #2a2150, #3a2a5a, #26314f);
}
#scrim {
  position: fixed;
  inset: 0;
  z-index: -1;
  background: linear-gradient(180deg, rgba(10, 8, 20, 0.2), rgba(10, 8, 20, 0.4));
}
.alertpop {
  position: fixed;
  top: 56px;
  right: 14px;
  width: 374px;
  max-height: 66vh;
  overflow: auto;
  border-radius: 14px;
  padding: 8px;
  z-index: 9999;
  box-shadow: 0 22px 56px rgba(0, 0, 0, 0.6), 0 0 0 1px rgba(255, 255, 255, 0.1);
}
.alertpop .ai {
  padding: 9px 11px;
  border-radius: 10px;
  margin-bottom: 6px;
  background: var(--panel-2);
  border-left: 3px solid var(--muted);
  font-size: 13px;
}
</style>
