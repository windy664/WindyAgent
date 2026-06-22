<script setup lang="ts">
import { ref } from 'vue'
import { setToken, verifyToken } from '../api'

const emit = defineEmits<{ (e: 'authed'): void }>()

const value = ref('')
const err = ref('')
const busy = ref(false)

async function enter() {
  const t = value.value.trim()
  if (!t) {
    err.value = '请输入令牌'
    return
  }
  busy.value = true
  err.value = ''
  try {
    const ok = await verifyToken(t)
    if (!ok) {
      err.value = '令牌无效，请重新输入'
      return
    }
    setToken(t)
    emit('authed')
  } catch (e) {
    err.value = '连不上：' + (e as Error).message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="gate">
    <div class="card">
      <h1>WindyAgent 控制台</h1>
      <p class="hint">输入访问令牌进入</p>
      <input
        v-model="value"
        type="password"
        placeholder="访问令牌"
        @keydown.enter="enter"
      />
      <button :disabled="busy" @click="enter">{{ busy ? '验证中…' : '进入 ✨' }}</button>
      <p v-if="err" class="err">{{ err }}</p>
    </div>
  </div>
</template>

<style scoped>
.gate {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
}
.card {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 32px;
  width: 320px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  text-align: center;
}
.card h1 {
  margin: 0;
  font-size: 20px;
}
.hint {
  color: var(--muted);
  margin: 0;
  font-size: 13px;
}
.card input {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--panel-2);
  color: var(--fg);
  font: inherit;
}
.card button {
  padding: 11px;
  border: none;
  border-radius: 12px;
  background: var(--grad);
  color: #fff;
  font-weight: 800;
  cursor: pointer;
  box-shadow: 0 5px 16px rgba(183, 155, 255, 0.38);
}
.card button:hover:not(:disabled) {
  filter: brightness(1.07);
}
.err {
  color: #ff6b6b;
  font-size: 13px;
  margin: 0;
}
</style>
