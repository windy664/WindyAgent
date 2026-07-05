<script setup lang="ts">
// ============================================================
// Monaco 编辑器封装（VS Code 同款编辑内核）。
// - 只引 editor.api + markdown 语言贡献，避免把全部语言打进单文件包。
// - worker 用 ?worker&inline：被 Vite 内联成 base64，兼容 vite-plugin-singlefile。
//   markdown 无独立 language worker，基础 editor.worker 足够（否则控制台报警告）。
// - 主题 windy-dark：半透明深底，透出外层毛玻璃皮肤。
// v-model 双向绑定 modelValue。
// ============================================================
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api'
import 'monaco-editor/esm/vs/basic-languages/markdown/markdown.contribution'
import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker&inline'
import { noteTitles } from '../wikilinks'

// `[[双链]]` 自动补全（全局注册一次）：在 `[[` 后列出知识库全部标题。
let wikilinkCompletionRegistered = false
function ensureWikilinkCompletion() {
  if (wikilinkCompletionRegistered) return
  monaco.languages.registerCompletionItemProvider('markdown', {
    triggerCharacters: ['['],
    provideCompletionItems(model, position) {
      const line = model.getValueInRange({
        startLineNumber: position.lineNumber,
        startColumn: 1,
        endLineNumber: position.lineNumber,
        endColumn: position.column,
      })
      const m = /\[\[([^\]|\n]*)$/.exec(line)
      if (!m) return { suggestions: [] }
      const range = new monaco.Range(
        position.lineNumber,
        position.column - m[1].length,
        position.lineNumber,
        position.column,
      )
      return {
        suggestions: noteTitles.map((t) => ({
          label: t,
          kind: monaco.languages.CompletionItemKind.Reference,
          insertText: `${t}]]`,
          range,
        })),
      }
    },
  })
  wikilinkCompletionRegistered = true
}

// 全局只需设一次
if (!(self as unknown as { MonacoEnvironment?: unknown }).MonacoEnvironment) {
  ;(self as unknown as { MonacoEnvironment: unknown }).MonacoEnvironment = {
    getWorker: () => new EditorWorker(),
  }
}

let themeDefined = false
function ensureTheme() {
  if (themeDefined) return
  monaco.editor.defineTheme('windy-dark', {
    base: 'vs-dark',
    inherit: true,
    rules: [],
    colors: {
      'editor.background': '#140f28cc',
      'editorGutter.background': '#00000000',
      'minimap.background': '#140f2866',
      'editor.lineHighlightBackground': '#ffffff0d',
      'editorLineNumber.foreground': '#6b6494',
      'editorLineNumber.activeForeground': '#b79bff',
      'editorCursor.foreground': '#ff8fc8',
      'editor.selectionBackground': '#b79bff44',
      'editorIndentGuide.background1': '#ffffff12',
      'scrollbarSlider.background': '#ffffff1a',
    },
  })
  themeDefined = true
}

const props = withDefaults(
  defineProps<{
    modelValue: string
    language?: string
    height?: string
    aiActions?: { id: string; label: string }[]
  }>(),
  { language: 'markdown', height: '460px', aiActions: () => [] },
)
const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
  (e: 'ai', id: string): void
}>()

const host = ref<HTMLDivElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | null = null

// 供父组件（AI 栏 / 右键动作）操作选区的句柄
defineExpose({
  /** 当前选中文本；无选区返回 ''。 */
  getSelection(): string {
    if (!editor) return ''
    const sel = editor.getSelection()
    const model = editor.getModel()
    if (!sel || !model || sel.isEmpty()) return ''
    return model.getValueInRange(sel)
  },
  /** 用 text 替换当前选区（触发 v-model 同步）。 */
  replaceSelection(text: string) {
    if (!editor) return
    const sel = editor.getSelection()
    if (!sel) return
    editor.executeEdits('windy-ai', [{ range: sel, text, forceMoveMarkers: true }])
    editor.focus()
  },
  /** 在正文末尾追加一段（续写用）。 */
  insertAtEnd(text: string) {
    if (!editor) return
    const model = editor.getModel()
    if (!model) return
    const line = model.getLineCount()
    const col = model.getLineMaxColumn(line)
    const range = new monaco.Range(line, col, line, col)
    editor.executeEdits('windy-ai', [{ range, text: '\n' + text, forceMoveMarkers: true }])
    editor.focus()
  },
})

onMounted(() => {
  ensureTheme()
  ensureWikilinkCompletion()
  editor = monaco.editor.create(host.value!, {
    value: props.modelValue ?? '',
    language: props.language,
    theme: 'windy-dark',
    automaticLayout: true, // 容器尺寸变化自动 relayout
    minimap: { enabled: true, scale: 1 },
    wordWrap: 'on',
    fontSize: 13,
    lineHeight: 21,
    lineNumbers: 'on',
    scrollBeyondLastLine: false,
    smoothScrolling: true,
    renderWhitespace: 'none',
    padding: { top: 10, bottom: 10 },
    fontFamily: "Consolas, 'Courier New', 'Microsoft YaHei', monospace",
    scrollbar: { verticalScrollbarSize: 9, horizontalScrollbarSize: 9 },
    overviewRulerLanes: 0,
    tabSize: 2,
  })
  editor.onDidChangeModelContent(() => {
    const v = editor!.getValue()
    if (v !== props.modelValue) emit('update:modelValue', v)
  })
  // 原生右键菜单里挂上 AI 动作（仿 Word/Notion 的选中右键 → AI）
  for (const a of props.aiActions) {
    editor.addAction({
      id: 'windy-kb-ai-' + a.id,
      label: 'AI · ' + a.label,
      contextMenuGroupId: 'windy-ai',
      contextMenuOrder: 1,
      run: () => emit('ai', a.id),
    })
  }
})

// 外部改值（如切换条目 / AI 起草）时同步进编辑器，避免光标丢失只在真不同才 set
watch(
  () => props.modelValue,
  (v) => {
    if (editor && v !== editor.getValue()) editor.setValue(v ?? '')
  },
)

onBeforeUnmount(() => {
  editor?.dispose()
  editor = null
})
</script>

<template>
  <div ref="host" class="monaco-host" :style="{ height }"></div>
</template>

<style scoped>
.monaco-host {
  width: 100%;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--bd);
  background: rgba(20, 15, 40, 0.5);
}
/* Monaco 自带字体渲染带阴影会糊，关掉全局 text-shadow */
.monaco-host :deep(.monaco-editor),
.monaco-host :deep(.monaco-editor *) {
  text-shadow: none !important;
}
</style>
