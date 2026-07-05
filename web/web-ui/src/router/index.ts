// ============================================================
// 路由表 —— 中后台「布局路由 + 权限守卫 + 动态菜单」骨架。
//
//   · 历史模式：createWebHashHistory —— 产物是 singlefile html，
//     无服务端路由，必须用 hash（#/ops）否则刷新 404。
//   · 布局路由：ShellLayout 承载顶栏+侧栏，业务页是其子路由；
//     登录 /login、首启向导 /setup 是无外壳的独立 gate。
//   · meta.nav：侧边栏据此自动生成（图标+标签）=动态菜单。
//   · beforeEach：未登录→/login；未配置→/setup =权限/首启守卫。
// ============================================================
import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'

import ShellLayout from '../layouts/ShellLayout.vue'
import TokenGate from '../components/TokenGate.vue'
import SetupWizard from '../components/SetupWizard.vue'
import OpsPanel from '../components/OpsPanel.vue'
import ChatPanel from '../components/ChatPanel.vue'
import BoardPanel from '../components/BoardPanel.vue'
import KnowledgePanel from '../components/KnowledgePanel.vue'
import PurchasePanel from '../components/PurchasePanel.vue'
import TasksPanel from '../components/TasksPanel.vue'
import SkillsPanel from '../components/SkillsPanel.vue'
import UsagePanel from '../components/UsagePanel.vue'
import ApprovalsPanel from '../components/ApprovalsPanel.vue'
import PlayerPanel from '../components/PlayerPanel.vue'

// meta 类型扩展：标题 / 侧栏图标标签 / 是否进导航
declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    icon?: string
    label?: string
    nav?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: TokenGate, meta: { title: '登录' } },
  { path: '/setup', name: 'setup', component: SetupWizard, props: { firstRun: true }, meta: { title: '配置向导' } },
  {
    path: '/',
    component: ShellLayout,
    redirect: '/ops',
    children: [
      { path: 'ops', name: 'ops', component: OpsPanel, meta: { title: '运维总览', icon: '🛰️', label: '运维总览', nav: true } },
      { path: 'chat/:convId?', name: 'chat', component: ChatPanel, meta: { title: 'AI 对话', icon: '💬', label: 'AI 对话', nav: true } },
      { path: 'board', name: 'board', component: BoardPanel, meta: { title: '行为看板', icon: '📊', label: '行为看板', nav: true } },
      { path: 'kb/:id(.*)?', name: 'kb', component: KnowledgePanel, meta: { title: '知识库', icon: '📚', label: '知识库', nav: true } },
      { path: 'players', name: 'players', component: PlayerPanel, meta: { title: '玩家管理', icon: '🧑', label: '玩家管理', nav: true } },
      { path: 'econ', name: 'econ', component: PurchasePanel, meta: { title: '充值管理', icon: '🛒', label: '充值管理', nav: true } },
      { path: 'event', name: 'event', component: TasksPanel, meta: { title: '定时任务', icon: '⏰', label: '定时任务', nav: true } },
      { path: 'skill', name: 'skill', component: SkillsPanel, meta: { title: '技能扩展', icon: '🧪', label: '技能扩展', nav: true } },
      { path: 'settings', name: 'settings', component: SetupWizard, props: { firstRun: false }, meta: { title: '设置中心', icon: '⚙️', label: '设置中心', nav: true } },
      { path: 'sys', name: 'sys', component: UsagePanel, meta: { title: '系统健康', icon: '🖥️', label: '系统健康', nav: true } },
      // 审批不进侧栏（从顶栏「待审」铃铛进入）
      { path: 'approvals', name: 'approvals', component: ApprovalsPanel, meta: { title: '操作审批', icon: '🛡️' } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

export const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

// 权限 + 首启守卫
router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.path === '/login') return auth.authed ? '/' : true
  if (!auth.authed) return { path: '/login' }
  await auth.ensureSetupChecked()
  if (auth.needSetup && to.path !== '/setup') return { path: '/setup' }
  if (!auth.needSetup && to.path === '/setup') return { path: '/' }
  return true
})

// 切页后同步浏览器标题（中后台细节）
router.afterEach((to) => {
  document.title = to.meta.title ? `WindyAgent · ${to.meta.title}` : 'WindyAgent · MC服务器智能助手'
})
