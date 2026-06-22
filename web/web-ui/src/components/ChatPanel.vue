<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { marked } from 'marked'
import { fetchHistory, fetchServers, streamChat, UnauthorizedError } from '../api'

const emit = defineEmits<{ (e: 'unauthorized'): void }>()

interface Msg {
  role: 'user' | 'assistant'
  text: string
  streaming?: boolean
}
interface Conv {
  id: string
  title: string
  ts: number
}

const CONV_KEY = 'wa_convs'
function loadConvs(): Conv[] {
  try {
    return JSON.parse(localStorage.getItem(CONV_KEY) || '[]')
  } catch {
    return []
  }
}
function persist() {
  localStorage.setItem(CONV_KEY, JSON.stringify(convs.value))
}
function newId() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 6)
}

const convs = ref<Conv[]>(loadConvs())
const currentId = ref<string>(convs.value[0]?.id || newId())
const session = computed(() => `web-${currentId.value}`)

const messages = ref<Msg[]>([])
const input = ref('')
const sending = ref(false)
const scroller = ref<HTMLElement | null>(null)

const servers = ref<string[]>([])
const target = ref('') // '' = 总控；否则子服名

const CHIPS = [
  '各子服现在 TPS 怎么样？',
  '现在哪些玩家在线？',
  '给全服广播一条公告',
  '服务器的规则是什么？',
]

function render(text: string): string {
  return marked.parse(text, { async: false }) as string
}

async function scrollToBottom() {
  await nextTick()
  const el = scroller.value
  if (el) el.scrollTop = el.scrollHeight
}

async function loadHistory() {
  try {
    const log = await fetchHistory(session.value)
    messages.value = log.map((e) => ({ role: e.role === 'u' ? 'user' : 'assistant', text: e.text }))
    scrollToBottom()
  } catch (e) {
    if (e instanceof UnauthorizedError) emit('unauthorized')
  }
}

function newChat() {
  currentId.value = newId()
  messages.value = []
  input.value = ''
}
function switchConv(id: string) {
  if (id === currentId.value) return
  currentId.value = id
  loadHistory()
}
function deleteConv(id: string) {
  convs.value = convs.value.filter((c) => c.id !== id)
  persist()
  if (id === currentId.value) {
    if (convs.value.length) switchConv(convs.value[0].id)
    else newChat()
  }
}
function clearAllConvs() {
  if (!confirm('清空所有对话记录？')) return
  convs.value = []
  persist()
  newChat()
}

function ensureConv(firstText: string) {
  let c = convs.value.find((x) => x.id === currentId.value)
  if (!c) {
    c = { id: currentId.value, title: firstText.slice(0, 18) || '新对话', ts: Date.now() }
    convs.value.unshift(c)
  } else if (c.title === '新对话' || !c.title) {
    c.title = firstText.slice(0, 18)
  }
  c.ts = Date.now()
  persist()
}

function quickAsk(text: string) {
  input.value = text
  send()
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  input.value = ''
  sending.value = true
  ensureConv(text)

  const apiMsg = target.value ? `[针对子服 ${target.value}] ${text}` : text
  messages.value.push({ role: 'user', text })
  messages.value.push({ role: 'assistant', text: '', streaming: true })
  // 取数组里的响应式代理（不能用原始对象，否则改 .text 不触发更新→看起来不流式）
  const a = messages.value[messages.value.length - 1]
  scrollToBottom()

  try {
    for await (const chunk of streamChat(session.value, apiMsg, text)) {
      a.text += chunk
      scrollToBottom()
    }
  } catch (e) {
    if (e instanceof UnauthorizedError) emit('unauthorized')
    else a.text += `\n\n> ⚠️ ${(e as Error).message}`
  } finally {
    a.streaming = false
    sending.value = false
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}
function convTime(ts: number) {
  return new Date(ts).toLocaleString().slice(5, 16)
}

onMounted(async () => {
  servers.value = await fetchServers()
  if (convs.value.length) loadHistory()
})
</script>

<template>
  <div class="chat-page">
    <div class="chatmain">
      <div class="msgs" ref="scroller">
        <template v-if="messages.length">
          <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role === 'user' ? 'u' : 'a']">
            <div class="mbody" v-html="render(m.text)"></div><span v-if="m.streaming" class="typing-cursor">▊</span>
          </div>
        </template>
        <div v-else class="welcome">
          <div class="wbot">
            <div class="av">🌸</div>
            <div class="tx">
              <h3>你好，腐竹 🌸</h3>
              <p>我是 WindyAgent，能帮你管理玩家、查信息、记知识、下指令。支持多轮对话。</p>
            </div>
          </div>
          <div class="wgrid">
            <div class="wcat glass"><h5>🧑 玩家管理</h5><ul><li>查在线 / 踢人 / 查余额</li><li>看玩家画像与行为</li></ul></div>
            <div class="wcat glass"><h5>🛒 商店与物品</h5><ul><li>物品估值 / 组礼包</li><li>价格与商品建议</li></ul></div>
            <div class="wcat glass"><h5>🔎 信息查询</h5><ul><li>服务器状态 / 子服切换</li><li>知识库问答</li></ul></div>
            <div class="wcat glass"><h5>🧠 记忆功能</h5><ul><li>记住偏好与服务器约定</li><li>跨会话长期记忆</li></ul></div>
          </div>
          <div class="chips">
            <button v-for="q in CHIPS" :key="q" class="chip" @click="quickAsk(q)">{{ q }}</button>
          </div>
        </div>
      </div>

      <div class="composer" style="position: relative">
        <select v-model="target" class="ctarget" title="对谁说：总控=代理全局；选子服=本轮默认操作该服">
          <option value="">总控</option>
          <option v-for="s in servers" :key="s" :value="s">{{ s }}</option>
        </select>
        <textarea
          v-model="input"
          rows="1"
          :disabled="sending"
          placeholder="说点什么…（Enter 发送，Shift+Enter 换行）"
          @keydown="onKeydown"
        ></textarea>
        <button class="btn ghost sm" @click="newChat">新对话</button>
        <button class="btn" :disabled="sending || !input.trim()" @click="send">发送 ✨</button>
      </div>
    </div>

    <aside class="rightbar">
      <div class="card glass" style="flex: 1; display: flex; flex-direction: column; overflow: hidden; padding: 0">
        <div class="rh">
          <h4 style="margin: 0">💬 对话记录</h4>
          <button class="btn ghost sm" style="font-size: 11px; padding: 4px 10px" @click="clearAllConvs">清空</button>
        </div>
        <div class="convlist">
          <div
            v-for="c in convs"
            :key="c.id"
            :class="['conv-item', { active: c.id === currentId }]"
            @click="switchConv(c.id)"
          >
            <div class="ct">{{ c.title || '新对话' }}</div>
            <div class="cm">
              <span>{{ convTime(c.ts) }}</span>
              <span class="cd" @click.stop="deleteConv(c.id)">删除</span>
            </div>
          </div>
          <div v-if="convs.length === 0" class="conv-empty">还没有对话<br />开始聊天会自动记录</div>
        </div>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
}
.ctarget {
  width: auto;
  min-width: 96px;
  flex-shrink: 0;
  height: 46px;
  background: rgba(16, 12, 32, 0.86);
  border: 1px solid rgba(255, 255, 255, 0.22);
  color: #fff;
  font-weight: 700;
  cursor: pointer;
}
.composer textarea {
  min-height: 46px;
}
.mbody :deep(p) {
  margin: 0.3em 0;
}
.mbody :deep(p:first-child) {
  margin-top: 0;
}
.mbody :deep(p:last-child) {
  margin-bottom: 0;
}
.rh {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 15px 10px;
  border-bottom: 1px solid var(--line);
}
.convlist {
  flex: 1;
  overflow-y: auto;
  padding: 6px 8px;
}
.wcat ul {
  margin: 0;
  padding-left: 16px;
  color: var(--mut);
  font-size: 12.5px;
  line-height: 1.7;
}
</style>
