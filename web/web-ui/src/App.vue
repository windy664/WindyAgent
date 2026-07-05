<script setup lang="ts">
// 应用根：仅负责全局背景 + 顶层路由出口。
// 顶层路由要么是 gate（登录 / 首启向导），要么是 ShellLayout（带顶栏侧栏的主框架）。
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

// 随机二次元背景（与原版一致；失败则回退 CSS 渐变）
const bgUrl = ref('')
onMounted(() => {
  bgUrl.value = `url('https://t.alcy.cc/moe?_=${Date.now()}')`
})

// 集中登出闭环：任意来源（401 / 主动）把 authed 置 false → 自动回登录页。
watch(
  () => auth.authed,
  (v) => {
    if (!v && route.path !== '/login') router.push('/login')
  },
)
</script>

<template>
  <!-- 背景 -->
  <div id="bg" :style="bgUrl ? { backgroundImage: bgUrl } : undefined"></div>
  <div id="scrim"></div>

  <router-view />
</template>
