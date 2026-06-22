<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  deleteKb,
  draftKb,
  fetchKb,
  saveKb,
  UnauthorizedError,
  type KbEntry,
} from '../api'

const emit = defineEmits<{ (e: 'unauthorized'): void }>()

const list = ref<KbEntry[]>([])
const error = ref('')
const search = ref('')
const selected = ref<KbEntry | null>(null)
const editing = ref<KbEntry | null>(null)
const tagsText = ref('')
const drafting = ref(false)
const draftInput = ref('')

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return list.value
  return list.value.filter(
    (e) =>
      e.title.toLowerCase().includes(q) ||
      e.content.toLowerCase().includes(q) ||
      e.tags.some((t) => t.toLowerCase().includes(q)),
  )
})

function handle(e: unknown) {
  if (e instanceof UnauthorizedError) emit('unauthorized')
  else error.value = (e as Error).message
}

async function load() {
  try {
    list.value = await fetchKb()
    error.value = ''
  } catch (e) {
    handle(e)
  }
}

function select(e: KbEntry) {
  selected.value = e
  editing.value = null
}
function newEntry() {
  editing.value = { id: '', title: '', content: '', tags: [] }
  selected.value = null
  tagsText.value = ''
}
function edit(e: KbEntry) {
  editing.value = { ...e }
  tagsText.value = e.tags.join(', ')
}
function cancel() {
  editing.value = null
}

async function save() {
  const e = editing.value
  if (!e || !e.title.trim()) return
  const tags = tagsText.value.split(/[,，]/).map((t) => t.trim()).filter(Boolean)
  try {
    await saveKb({ id: e.id || undefined, title: e.title, content: e.content, tags })
    editing.value = null
    await load()
  } catch (err) {
    handle(err)
  }
}

async function remove(e: KbEntry) {
  if (!confirm(`删除知识「${e.title}」？`)) return
  try {
    await deleteKb(e.id)
    if (selected.value?.id === e.id) selected.value = null
    await load()
  } catch (err) {
    handle(err)
  }
}

async function aiDraft() {
  const text = draftInput.value.trim()
  if (!text) return
  drafting.value = true
  try {
    const d = await draftKb(text)
    editing.value = { id: '', title: d.title, content: d.content, tags: d.tags }
    tagsText.value = d.tags.join(', ')
    draftInput.value = ''
  } catch (err) {
    handle(err)
  } finally {
    drafting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="kbwrap">
    <div class="kbside glass">
      <input v-model="search" placeholder="🔍 搜索标题 / 标签 / 内容" style="margin-bottom: 10px" />
      <div class="kblist">
        <div
          v-for="e in filtered"
          :key="e.id"
          :class="['kbitem', { on: selected?.id === e.id }]"
          @click="select(e)"
        >
          <div class="it">{{ e.title }}</div>
          <div class="is">{{ e.tags.length ? e.tags.join(' · ') : e.content }}</div>
        </div>
        <div v-if="filtered.length === 0" class="muted" style="padding: 16px; font-size: 13px">无条目</div>
      </div>
      <button class="btn sm" style="width: 100%; margin-top: 8px" @click="newEntry">+ 新建条目</button>
    </div>

    <div class="kbmain">
      <p v-if="error" class="err">{{ error }}</p>

      <!-- 编辑 / 新建 -->
      <div v-if="editing" class="panel glass">
        <h3>{{ editing.id ? '编辑条目' : '新建条目' }}</h3>
        <details style="margin-bottom: 12px">
          <summary style="cursor: pointer; color: var(--violet); font-weight: 700">🤖 AI 起草 —— 说人话自动整理</summary>
          <textarea v-model="draftInput" rows="2" placeholder="例：会员 VIP 每月 30 元，享每日 3 次传送、专属称号" style="margin-top: 8px"></textarea>
          <button class="btn sm" style="margin-top: 8px" :disabled="drafting || !draftInput.trim()" @click="aiDraft">
            {{ drafting ? '生成中…' : '生成草稿 ✨' }}
          </button>
        </details>
        <label class="muted" style="font-size: 12px">标题</label>
        <input v-model="editing.title" placeholder="如：会员权益" />
        <label class="muted" style="font-size: 12px; display: block; margin-top: 8px">标签（逗号分隔）</label>
        <input v-model="tagsText" placeholder="会员, 价格, vip" />
        <label class="muted" style="font-size: 12px; display: block; margin-top: 8px">正文</label>
        <textarea v-model="editing.content" rows="8"></textarea>
        <div style="margin-top: 12px; display: flex; gap: 8px">
          <button class="btn" :disabled="!editing.title.trim()" @click="save">保存</button>
          <button class="btn ghost" @click="cancel">取消</button>
        </div>
      </div>

      <!-- 详情 -->
      <div v-else-if="selected" class="panel glass kbdetail">
        <h3>
          {{ selected.title }}
          <span>
            <button class="btn ghost sm" @click="edit(selected)">编辑</button>
            <button class="btn ghost sm" style="margin-left: 6px" @click="remove(selected)">删除</button>
          </span>
        </h3>
        <div>
          <span v-for="t in selected.tags" :key="t" class="tag2">{{ t }}</span>
        </div>
        <div class="body">{{ selected.content }}</div>
      </div>

      <!-- 空 -->
      <div v-else class="panel glass">
        <p class="muted">← 从左侧目录选一条查看；或「+ 新建」添加（支持 AI 起草）。</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.kblist {
  display: flex;
  flex-direction: column;
}
.kbdetail h3 {
  margin: 0 0 10px;
  font-size: 19px;
}
.kbdetail .body {
  white-space: pre-wrap;
  line-height: 1.65;
  margin-top: 12px;
}
</style>
