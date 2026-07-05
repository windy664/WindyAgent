<script setup lang="ts">
// ============================================================
// 主框架布局（ProLayout 式）：顶栏 + 侧栏 + 内容区(router-view)。
// 视觉与原 App.vue 完全一致（同样的 class / 毛玻璃 / 渐变 / 动画），
// 只是：菜单改由路由表生成、状态读 chrome store、内容区换路由出口。
// ============================================================
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useChromeStore } from '../stores/chrome'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const chrome = useChromeStore()

const bellOpen = ref(false)

// 侧栏折叠成图标列（icon rail），状态记忆到 localStorage
const collapsed = ref(localStorage.getItem('wa_sidebar_collapsed') === '1')
function toggleSidebar() {
  collapsed.value = !collapsed.value
  localStorage.setItem('wa_sidebar_collapsed', collapsed.value ? '1' : '0')
}

// 侧栏菜单：从路由表 meta.nav 自动生成（动态菜单），保留声明顺序。
const navItems = computed(() => {
  const layout = router.options.routes.find((r) => r.path === '/')
  return (layout?.children ?? [])
    .filter((c) => c.meta?.nav)
    .map((c) => ({
      path: '/' + c.path.replace(/\/:.*$/, ''), // 去掉 :param 段（如 chat/:convId? → chat）
      name: String(c.name),
      icon: c.meta!.icon as string,
      label: c.meta!.label as string,
    }))
})

const pageTitle = computed(() => {
  const m = route.meta
  return m.icon ? `${m.icon} ${m.title}` : (m.title ?? '')
})

function go(path: string) {
  router.push(path)
}
function logout() {
  auth.logout()
}
function alertTime(ts: number) {
  return new Date(ts).toLocaleTimeString()
}

onMounted(() => chrome.startPoll())
onUnmounted(() => chrome.stopPoll())
</script>

<template>
  <!-- 顶栏 -->
  <div class="topbar">
    <div class="tbtitle">{{ pageTitle }}</div>
    <div class="grow"></div>
    <div class="pill" title="当前操作的子服" v-if="chrome.servers.length">
      <span style="opacity: 0.7">🌐</span>
      <select v-model="chrome.currentServer">
        <option v-for="s in chrome.servers" :key="s" :value="s">{{ s }}</option>
      </select>
    </div>
    <div class="pill" title="在线玩家">
      <span class="dot"></span>{{ chrome.status.onlinePlayers ?? 0 }} 在线
    </div>
    <div
      v-if="chrome.pendingCount > 0"
      class="pill"
      style="cursor: pointer; border-color: rgba(255, 122, 152, 0.6); color: #fff"
      @click="go('/approvals')"
    >
      🛡️ 待审 <b>{{ chrome.pendingCount }}</b>
    </div>
    <div class="ticon" title="运维告警" @click="bellOpen = !bellOpen">
      🔔<span v-if="chrome.alerts.length" class="bdot">{{ chrome.alerts.length }}</span>
    </div>
    <div class="ticon" title="退出登录" @click="logout">🚪</div>

    <div v-if="bellOpen" class="alertpop glass">
      <div v-if="chrome.alerts.length === 0" class="muted" style="padding: 16px; text-align: center">
        暂无告警 ✨
      </div>
      <div v-for="(a, i) in chrome.alerts" :key="i" class="ai">
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
    <aside :class="['sidebar', { collapsed }]">
      <div class="logo">
        <div class="lg">🌸</div>
        <div class="logo-txt" v-show="!collapsed">
          <div class="lt">WindyAgent</div>
          <div class="ls">MC服务器智能助手</div>
        </div>
        <button class="collapse-btn" :title="collapsed ? '展开侧栏' : '收起侧栏'" @click="toggleSidebar">
          {{ collapsed ? '»' : '«' }}
        </button>
      </div>
      <!-- 导航区：独立滚动（导航项多/窗口矮时，仅此区滚动，logo 与服务器面板始终可见）-->
      <nav class="navlist">
        <button
          v-for="item in navItems"
          :key="item.name"
          :class="['nav', { on: route.name === item.name }]"
          :title="collapsed ? item.label : ''"
          @click="go(item.path)"
        >
          <span class="i">{{ item.icon }}</span><span class="lbl" v-show="!collapsed">{{ item.label }}</span>
        </button>
      </nav>
      <div class="srvpanel glass" v-show="!collapsed">
        <div class="nm">🟢 {{ chrome.status.name || 'WindyAgent' }}</div>
        <div class="rw"><span>在线</span><span>{{ chrome.status.onlinePlayers ?? '—' }}</span></div>
        <div class="rw"><span>服务端</span><span>{{ chrome.status.proxyVersion || chrome.status.platform || 'Velocity' }}</span></div>
      </div>
    </aside>

    <main class="content">
      <router-view />
    </main>
  </div>
</template>
