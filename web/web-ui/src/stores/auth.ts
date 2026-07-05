// ============================================================
// 鉴权 store —— 全局登录态的单一来源。
//   · token 落 localStorage（沿用 http/client 的存取）
//   · 向请求层注册 401 集中登出钩子：任意接口 401 自动登出
//   · 首启配置检查（needSetup）：路由守卫据此把未配置用户导向向导
// ============================================================
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getToken, setToken, clearToken, setUnauthorizedHandler } from '../http/client'
import { fetchSetupState } from '../api'

export const useAuthStore = defineStore('auth', () => {
  const authed = ref(!!getToken())
  const needSetup = ref(false)
  const setupChecked = ref(false)

  function login(token: string) {
    setToken(token)
    authed.value = true
  }

  function logout() {
    clearToken()
    authed.value = false
    setupChecked.value = false // 换 token 后需重新检查配置
  }

  /** 首启配置检查（每登录态只查一次，结果缓存）。 */
  async function ensureSetupChecked() {
    if (setupChecked.value) return
    const st = await fetchSetupState()
    needSetup.value = st.configured === false
    setupChecked.value = true
  }

  // 请求层 401 → 集中登出（替代各组件 emit('unauthorized')）。
  setUnauthorizedHandler(() => {
    authed.value = false
    setupChecked.value = false
  })

  return { authed, needSetup, login, logout, ensureSetupChecked }
})
