<script setup lang="ts">
// ============================================================
// 知识库关系图（高仿 Obsidian graph view，零依赖手写力导向）。
// 节点 = 条目，边 = 正文里的 [[双链]]（解析得到目标才连）。
// - requestAnimationFrame 力模拟：斥力 + 弹簧 + 向心，alpha 冷却后自动停。
// - 指针拖拽节点、点击节点 emit('select', id)。
// - 节点半径按度数（连接数）微调，孤立点也画出来。
// ============================================================
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { KbMeta } from '../api'

const props = defineProps<{ entries: KbMeta[]; selectedId?: string | null }>()
const emit = defineEmits<{ (e: 'select', id: string): void }>()

interface Node {
  id: string
  title: string
  x: number
  y: number
  vx: number
  vy: number
  deg: number
  fixed: boolean
}
interface Edge {
  s: number
  t: number
}

const W = 800
const H = 600
const nodes = ref<Node[]>([])
const edges = ref<Edge[]>([])
let raf = 0
let alpha = 0

function build() {
  const prev = new Map(nodes.value.map((n) => [n.id, n]))
  const list = props.entries
  const idx = new Map<string, number>()
  const ns: Node[] = list.map((e, i) => {
    idx.set(e.id, i)
    const p = prev.get(e.id)
    return {
      id: e.id,
      title: e.title,
      x: p?.x ?? W / 2 + Math.cos((i / Math.max(1, list.length)) * Math.PI * 2) * 200 + (Math.random() - 0.5) * 40,
      y: p?.y ?? H / 2 + Math.sin((i / Math.max(1, list.length)) * Math.PI * 2) * 200 + (Math.random() - 0.5) * 40,
      vx: 0,
      vy: 0,
      deg: 0,
      fixed: false,
    }
  })
  const es: Edge[] = []
  const seen = new Set<string>()
  for (const e of list) {
    const s = idx.get(e.id)!
    for (const tid of e.links) {
      const t = idx.get(tid)
      if (t === undefined || t === s) continue
      const key = s < t ? `${s}-${t}` : `${t}-${s}`
      if (seen.has(key)) continue
      seen.add(key)
      es.push({ s, t })
      ns[s].deg++
      ns[t].deg++
    }
  }
  nodes.value = ns
  edges.value = es
  kick()
}

function kick() {
  alpha = 1
  if (!raf) raf = requestAnimationFrame(tick)
}

function tick() {
  const ns = nodes.value
  const es = edges.value
  const n = ns.length
  // 斥力（Coulomb，O(n²)，知识库规模够用）
  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) {
      let dx = ns[i].x - ns[j].x
      let dy = ns[i].y - ns[j].y
      let d2 = dx * dx + dy * dy
      if (d2 < 0.01) {
        dx = Math.random() - 0.5
        dy = Math.random() - 0.5
        d2 = 0.01
      }
      const f = 4200 / d2
      const d = Math.sqrt(d2)
      const fx = (dx / d) * f
      const fy = (dy / d) * f
      ns[i].vx += fx
      ns[i].vy += fy
      ns[j].vx -= fx
      ns[j].vy -= fy
    }
  }
  // 弹簧（边）
  const L = 90
  for (const e of es) {
    const a = ns[e.s]
    const b = ns[e.t]
    const dx = b.x - a.x
    const dy = b.y - a.y
    const d = Math.sqrt(dx * dx + dy * dy) || 0.01
    const f = (d - L) * 0.05
    const fx = (dx / d) * f
    const fy = (dy / d) * f
    a.vx += fx
    a.vy += fy
    b.vx -= fx
    b.vy -= fy
  }
  // 向心 + 积分
  for (const nd of ns) {
    if (nd.fixed) {
      nd.vx = 0
      nd.vy = 0
      continue
    }
    nd.vx += (W / 2 - nd.x) * 0.008
    nd.vy += (H / 2 - nd.y) * 0.008
    nd.vx *= 0.86
    nd.vy *= 0.86
    nd.x += nd.vx * alpha
    nd.y += nd.vy * alpha
    nd.x = Math.max(20, Math.min(W - 20, nd.x))
    nd.y = Math.max(20, Math.min(H - 20, nd.y))
  }
  nodes.value = ns.slice() // 触发响应式
  alpha *= 0.985
  if (alpha > 0.02 || dragging.value >= 0) {
    raf = requestAnimationFrame(tick)
  } else {
    raf = 0
  }
}

// ── 拖拽 ──
const svg = ref<SVGSVGElement | null>(null)
const dragging = ref(-1)
function toView(ev: PointerEvent): { x: number; y: number } {
  const r = svg.value!.getBoundingClientRect()
  return { x: ((ev.clientX - r.left) / r.width) * W, y: ((ev.clientY - r.top) / r.height) * H }
}
function onDown(i: number, ev: PointerEvent) {
  dragging.value = i
  nodes.value[i].fixed = true
  ;(ev.target as Element).setPointerCapture?.(ev.pointerId)
  kick()
}
function onMove(ev: PointerEvent) {
  if (dragging.value < 0) return
  const p = toView(ev)
  const nd = nodes.value[dragging.value]
  nd.x = p.x
  nd.y = p.y
  nodes.value = nodes.value.slice()
}
function onUp() {
  if (dragging.value >= 0) {
    nodes.value[dragging.value].fixed = false
    dragging.value = -1
    kick()
  }
}

function nodeRadius(nd: Node): number {
  return 5 + Math.min(6, nd.deg * 1.5)
}
const hasData = computed(() => nodes.value.length > 0)

watch(
  () => props.entries,
  () => build(),
  { immediate: true, deep: false },
)

onBeforeUnmount(() => {
  if (raf) cancelAnimationFrame(raf)
})
</script>

<template>
  <div class="graphwrap">
    <svg
      v-if="hasData"
      ref="svg"
      class="graph"
      :viewBox="`0 0 ${W} ${H}`"
      preserveAspectRatio="xMidYMid meet"
      @pointermove="onMove"
      @pointerup="onUp"
      @pointerleave="onUp"
    >
      <line
        v-for="(e, i) in edges"
        :key="'e' + i"
        :x1="nodes[e.s].x"
        :y1="nodes[e.s].y"
        :x2="nodes[e.t].x"
        :y2="nodes[e.t].y"
        class="edge"
      />
      <g
        v-for="(nd, i) in nodes"
        :key="nd.id"
        :transform="`translate(${nd.x},${nd.y})`"
        class="node"
        :class="{ on: nd.id === selectedId }"
        @pointerdown="onDown(i, $event)"
        @click="emit('select', nd.id)"
      >
        <circle :r="nodeRadius(nd)" />
        <text :y="nodeRadius(nd) + 12">{{ nd.title }}</text>
      </g>
    </svg>
    <div v-else class="muted empty">知识库为空，无法生成关系图。</div>
  </div>
</template>

<style scoped>
.graphwrap {
  width: 100%;
  height: 100%;
  min-height: 420px;
  display: flex;
}
.graph {
  width: 100%;
  height: 100%;
  min-height: 420px;
  touch-action: none;
  cursor: grab;
}
.edge {
  stroke: var(--bd);
  stroke-width: 1;
  opacity: 0.5;
}
.node {
  cursor: pointer;
}
.node circle {
  fill: var(--violet);
  stroke: rgba(255, 255, 255, 0.5);
  stroke-width: 1;
  transition: fill 0.15s;
}
.node:hover circle {
  fill: var(--pink);
}
.node.on circle {
  fill: var(--gold);
  stroke: #fff;
  stroke-width: 2;
}
.node text {
  fill: var(--mut);
  font-size: 11px;
  text-anchor: middle;
  pointer-events: none;
  user-select: none;
}
.node:hover text,
.node.on text {
  fill: var(--fg);
}
.empty {
  margin: auto;
  font-size: 13px;
}
</style>
