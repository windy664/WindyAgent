<script setup lang="ts">
import { useRoute } from 'vue-router'
// 无后端的页：建设中占位（沿用原版 board 版式，毛玻璃一致）。
defineProps<{ hint?: string }>()
const route = useRoute()
</script>

<template>
  <div class="board">
    <div class="bhead">
      <div>
        <h2>{{ route.meta.icon }} {{ route.meta.title }}</h2>
        <p v-if="hint">{{ hint }}</p>
      </div>
    </div>
    <div class="panel glass">
      <div style="text-align: center; padding: 48px; color: var(--muted)">
        <div style="font-size: 44px">🚧</div>
        <p>该面板建设中</p>
      </div>
    </div>
  </div>
</template>
