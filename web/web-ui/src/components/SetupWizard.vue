<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { fetchSetupState, saveSetup, UnauthorizedError } from '../api'

// firstRun=true：首启未配置，全屏强制；false：从「设置」面板进来，可重配。
const props = defineProps<{ firstRun?: boolean }>()
const emit = defineEmits<{ (e: 'unauthorized'): void; (e: 'done'): void }>()

const form = reactive({
  provider: 'openai',
  apiKey: '',
  model: '',
  apiBaseUrl: '',
  fastModel: '',
})
const msg = ref('')
const msgColor = ref('')
const saving = ref(false)

const PLACEHOLDERS: Record<string, { model: string; base: string }> = {
  claude: { model: 'claude-opus-4-8', base: '留空=Anthropic 官方接口' },
  openai: { model: 'mimo-v2.5-pro', base: 'https://your-endpoint/v1' },
  ollama: { model: 'llama3.2', base: 'http://localhost:11434' },
}
const ph = computed(() => PLACEHOLDERS[form.provider] || { model: '', base: '' })
const needKey = computed(() => form.provider !== 'ollama')

async function load() {
  try {
    const st = await fetchSetupState()
    if (st.provider) form.provider = st.provider
    if (st.model) form.model = st.model
    if (st.apiBaseUrl) form.apiBaseUrl = st.apiBaseUrl
    if (st.fastModel) form.fastModel = st.fastModel
  } catch (e) {
    if (e instanceof UnauthorizedError) emit('unauthorized')
  }
}

async function submit() {
  const model = form.model.trim() || ph.value.model
  if (needKey.value && !form.apiKey.trim()) {
    msgColor.value = '#ff7a98'
    msg.value = '请填写 API Key'
    return
  }
  if (!model) {
    msgColor.value = '#ff7a98'
    msg.value = '请填写模型名'
    return
  }
  saving.value = true
  msgColor.value = '#bdb6da'
  msg.value = '保存中…'
  try {
    await saveSetup({
      provider: form.provider,
      apiKey: form.apiKey.trim(),
      model,
      apiBaseUrl: form.apiBaseUrl.trim(),
      fastModel: form.fastModel.trim(),
    })
    msgColor.value = '#86efc6'
    msg.value = '✅ 已保存！重启 Velocity 代理后生效。'
    if (!props.firstRun) setTimeout(() => emit('done'), 1200)
  } catch (e) {
    if (e instanceof UnauthorizedError) return emit('unauthorized')
    msgColor.value = '#ff7a98'
    msg.value = '保存失败：' + (e as Error).message
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div :class="['setup', { full: firstRun }]">
    <div class="card">
      <h1>{{ firstRun ? '配置向导' : '⚙️ LLM 设置' }}</h1>
      <p class="hint" v-if="firstRun">首次使用，先配置一个 LLM 提供方</p>

      <label>提供方</label>
      <select v-model="form.provider">
        <option value="claude">claude — Anthropic</option>
        <option value="openai">openai — 任意 OpenAI 兼容协议（mimo/智谱/讯飞…）</option>
        <option value="ollama">ollama — 本地，免 key</option>
      </select>

      <template v-if="needKey">
        <label>API Key</label>
        <input v-model="form.apiKey" type="password" placeholder="sk-... / 你的密钥" />
      </template>

      <label>API 地址（可选）</label>
      <input v-model="form.apiBaseUrl" :placeholder="ph.base" />

      <label>模型</label>
      <input v-model="form.model" :placeholder="ph.model" />

      <label>便宜模型（可选，省 token）</label>
      <input v-model="form.fastModel" placeholder="留空=与主模型相同" />

      <button class="primary" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存配置' }}</button>
      <p v-if="msg" class="msg" :style="{ color: msgColor }">{{ msg }}</p>
    </div>
  </div>
</template>

<style scoped>
.setup { width: 100%; overflow-y: auto; padding: 24px; }
.setup.full { display: flex; align-items: center; justify-content: center; height: 100vh; }
.card { background: var(--panel); border: 1px solid var(--border); border-radius: 16px; padding: 28px; width: 420px; max-width: 100%; display: flex; flex-direction: column; gap: 8px; }
.card h1 { margin: 0 0 4px; font-size: 20px; }
.hint { color: var(--muted); margin: 0 0 8px; font-size: 13px; }
label { font-size: 12px; color: var(--muted); margin-top: 6px; }
input, select { background: var(--panel-2); color: var(--fg); border: 1px solid var(--border); border-radius: 8px; padding: 9px 11px; font: inherit; }
.primary { background: var(--grad); border: none; color: #fff; font-weight: 800; border-radius: 12px; padding: 12px; margin-top: 14px; cursor: pointer; box-shadow: 0 5px 16px rgba(183,155,255,.38); }
.primary:hover:not(:disabled) { filter: brightness(1.07); }
.primary:disabled { opacity: 0.5; cursor: default; }
.msg { font-size: 13px; margin: 6px 0 0; }
</style>
