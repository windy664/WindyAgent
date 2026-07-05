// ============================================================
// 外壳（chrome）store —— 顶栏/侧栏共享的运行态：
//   代理状态、子服列表、当前操作子服、待审数、运维告警。
//   轮询集中在此（原先散在 App.vue），面板与外壳都从这里读。
// ============================================================
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  fetchAlerts,
  fetchApprovals,
  fetchServers,
  fetchStatus,
  type AlertEntry,
  type ProxyStatus,
} from '../api'

const POLL_MS = 5000

export const useChromeStore = defineStore('chrome', () => {
  const status = ref<ProxyStatus>({})
  const servers = ref<string[]>([])
  const currentServer = ref('')
  const pendingCount = ref(0)
  const alerts = ref<AlertEntry[]>([])

  const hasAlerts = computed(() => alerts.value.length > 0)

  let timer: number | undefined

  async function refresh() {
    status.value = await fetchStatus()
    servers.value = await fetchServers()
    if (!currentServer.value && servers.value.length) currentServer.value = servers.value[0]
    const ap = await fetchApprovals().catch(() => null)
    pendingCount.value = ap?.pending.length ?? 0
    alerts.value = await fetchAlerts()
  }

  function startPoll() {
    stopPoll()
    refresh()
    timer = window.setInterval(refresh, POLL_MS)
  }

  function stopPoll() {
    if (timer) {
      clearInterval(timer)
      timer = undefined
    }
  }

  return {
    status,
    servers,
    currentServer,
    pendingCount,
    alerts,
    hasAlerts,
    refresh,
    startPoll,
    stopPoll,
  }
})
