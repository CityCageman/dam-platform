<template>
  <div class="lg-wrap">
    <!-- 顶部工具条 -->
    <div class="lg-toolbar">
      <el-tooltip :content="`当前中心：${centerName || centerId}`" placement="top">
        <span class="lg-center-name" :title="String(centerId)">{{ centerName || '血缘图谱' }}</span>
      </el-tooltip>
      <el-select v-model="depth" size="small" style="width: 92px;" @change="loadGraph">
        <el-option v-for="d in 6" :key="d" :value="d" :label="`${d}跳`" />
      </el-select>
      <template v-if="!isExternal">
        <el-select v-model="layerFilter" multiple collapse-tags clearable size="small" placeholder="全部层级"
                   style="width: 150px;" @change="loadGraph">
          <el-option v-for="l in LAYER_OPTIONS" :key="l.value" :label="l.label" :value="l.value" />
        </el-select>
        <el-select v-model="direction" size="small" style="width: 92px;" @change="loadGraph">
          <el-option label="双向" value="" />
          <el-option label="上游" value="UPSTREAM" />
          <el-option label="下游" value="DOWNSTREAM" />
        </el-select>
        <el-checkbox v-model="crossOnly" size="small" @change="loadGraph">仅跨层</el-checkbox>
      </template>
      <el-button size="small" text type="primary" @click="resetView">重置视图</el-button>
      <el-button v-if="history.length" size="small" text type="primary" @click="goBack">
        ← 返回 {{ history[history.length - 1].name || '上级' }}
      </el-button>
      <span class="lg-hint">单击表节点查看字段级血缘 · 双击下钻 · 滚轮缩放</span>
    </div>

    <!-- 画布 -->
    <div ref="containerRef" class="lg-canvas" :style="{ height: canvasHeight + 'px' }">
      <svg ref="svgRef" width="100%" height="100%"></svg>
      <div v-if="loading" class="lg-mask"><el-icon class="is-loading"><Loading /></el-icon> 加载中...</div>
      <div v-if="!loading && !nodes.length" class="lg-mask">该节点暂无血缘关系</div>

      <!-- 图例 -->
      <div class="lg-legend">
        <div class="lg-legend-title">图例</div>
        <div v-for="item in legendItems" :key="item.key" class="lg-legend-item">
          <span class="lg-dot" :style="{ background: item.color }"></span>{{ item.label }}
        </div>
        <div class="lg-legend-item">
          <span class="lg-line-solid"></span>依赖(DEPEND)
        </div>
        <div class="lg-legend-item">
          <span class="lg-line-dash"></span>引用(REFERENCE)
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import * as d3 from 'd3'
import * as dagre from 'dagre'
import { lineageApi } from '@/api'

const props = withDefaults(defineProps<{
  nodeId?: number | string
  nodeName?: string
  height?: number
  /** 外部节点（AI 识别预览等，传入后不再调用接口加载） */
  externalNodes?: any[]
  /** 外部边 */
  externalEdges?: any[]
}>(), {
  nodeName: '',
  height: 420
})

const emit = defineEmits<{
  (e: 'node-click', payload: { id: any; name: string; type: string }): void
}>()

// ==================== 状态 ====================
const containerRef = ref<HTMLElement>()
const svgRef = ref<SVGSVGElement>()
const canvasHeight = ref(props.height)
const nodes = ref<any[]>([])
const edges = ref<any[]>([])
const loading = ref(false)
const depth = ref(4)
const centerId = ref<any>(props.nodeId)
const centerName = ref(props.nodeName || '')
const history = ref<{ id: any; name: string }[]>([])

let positions = new Map<string, { x: number; y: number }>()
let edgePaths = new Map<string, { x: number; y: number }[]>()
let rankMap = new Map<string, number>()
let fixedPositions = new Map<string, { x: number; y: number }>()
let neighborMap = new Map<string, Set<string>>()
let highlightSet: Set<string> | null = null
let dragged = false
let suppressClick = false
let zoomTransform: d3.ZoomTransform | null = null

// ==================== 节点类型配色 ====================
const TYPE_COLORS: Record<string, string> = {
  TABLE: '#4A90D9', REPORT: '#F5A623', VIEW: '#7ED321',
  PROCEDURE: '#D0021B', DICT: '#9B9B9B', ETL_JOB: '#F5A623', CTE: '#13c2c2'
}
const TYPE_LABELS: Record<string, string> = {
  TABLE: '数据表', REPORT: '报表', VIEW: '视图', PROCEDURE: '存储过程',
  DICT: '字典表', ETL_JOB: 'ETL作业', CTE: 'CTE', FIELD: '字段'
}
const LAYER_COLORS: Record<string, string> = {
  SOURCE: '#64748B', ODS: '#D97706', DWD: '#0D9488', DIM: '#EC4899',
  DWS: '#2563EB', ADS: '#7C3AED', REPORT: '#DC2626'
}
const LAYER_LABELS: Record<string, string> = {
  SOURCE: '源库', ODS: 'ODS', DWD: 'DWD', DIM: 'DIM', DWS: 'DWS', ADS: 'ADS', REPORT: '报表'
}
const LAYER_OPTIONS = Object.keys(LAYER_COLORS).map(k => ({ value: k, label: LAYER_LABELS[k], color: LAYER_COLORS[k] }))
const typeLegendItems = Object.keys(TYPE_COLORS).map(k => ({ key: k, label: TYPE_LABELS[k] || k, color: TYPE_COLORS[k] }))

// 优先展示层级图例（有 layer 数据时），否则回退到类型图例
const legendItems = computed(() => {
  const present = LAYER_OPTIONS.filter(l => nodes.value.some((n: any) => n.layer === l.value))
  return present.length ? present.map(l => ({ key: l.value, label: l.label, color: l.color })) : typeLegendItems
})

const layerFilter = ref<string[]>([])
const crossOnly = ref(false)
const direction = ref('')
const isExternal = computed(() => Array.isArray(props.externalNodes))

const LAYER_LABEL_MAP: Record<string, string> = {
  SOURCE: '源库', ODS: 'ODS', DWD: 'DWD', DIM: 'DIM', DWS: 'DWS', ADS: 'ADS', REPORT: '报表'
}
const TYPE_LABEL_MAP: Record<string, string> = {
  TABLE: '数据表', REPORT: '报表', VIEW: '视图', PROCEDURE: '存储过程', DICT: '字典表',
  ETL_JOB: 'ETL 作业', CTE: 'CTE', FIELD: '字段'
}
function truncate(v: string, n: number) {
  return v.length > n ? v.slice(0, n - 1) + '…' : v
}
function nodeMeta(d: any) {
  const layer = d.layer ? (LAYER_LABEL_MAP[d.layer] || d.layer) : (TYPE_LABEL_MAP[d.type] || d.type || '节点')
  const count = d.fieldCount ?? d.columnCount
  return count ? `${layer} · ${count} 字段` : layer
}

function nodeColor(d: any): string {
  if (d.type === 'FIELD') return '#059669'
  if (d.layer && LAYER_COLORS[d.layer]) return LAYER_COLORS[d.layer]
  return TYPE_COLORS[d.type] || '#72156A'
}

// ==================== 数据加载 ====================
async function loadGraph() {
  loading.value = true
  try {
    const res: any = await lineageApi.getLineageGraph(
      centerId.value, depth.value, false,
      layerFilter.value.length ? layerFilter.value.join(',') : undefined,
      crossOnly.value || undefined,
      direction.value || undefined
    )
    nodes.value = res.data?.nodes || []
    edges.value = res.data?.edges || []
    const c = nodes.value.find((n: any) => String(n.id) === String(centerId.value))
    if (c) centerName.value = c.label || centerName.value
    if (!nodes.value.some((n: any) => String(n.id) === String(centerId.value))) {
      nodes.value = [{ id: centerId.value, label: centerName.value || String(centerId.value), type: 'TABLE', isCenter: true }, ...nodes.value]
    }
    fixedPositions = new Map()
    await nextTick()
    render()
  } catch (_e) {
    nodes.value = []
    edges.value = []
  } finally {
    loading.value = false
  }
}

/** 外部数据模式：直接用传入的 nodes/edges 渲染（AI 识别预览等，无需接口） */
function loadExternal() {
  nodes.value = props.externalNodes || []
  edges.value = props.externalEdges || []
  centerId.value = undefined
  centerName.value = ''
  fixedPositions = new Map()
  rankMap = new Map()
  zoomTransform = null
  nextTick(render)
}

// ==================== 布局计算（dagre 分层布局，自动最小化连线交叉） ====================
// 六列泳道（DIM 合并进 DWD 列，保持 6 列）
const LANES = [
  { key: 'REPORT', label: '报表', en: 'REPORT', color: '#EC4899', bg: '#FDF2F8' },
  { key: 'ADS', label: 'ADS 应用层', en: 'ADS', color: '#7C3AED', bg: '#F5F3FF' },
  { key: 'DWS', label: 'DWS 汇总层', en: 'DWS', color: '#2563EB', bg: '#EFF6FF' },
  { key: 'DWD', label: 'DWD / DIM 明细层', en: 'DWD', color: '#0D9488', bg: '#F0FDFA' },
  { key: 'ODS', label: 'ODS 贴源层', en: 'ODS', color: '#D97706', bg: '#FFFBEB' },
  { key: 'SOURCE', label: '源库', en: 'SOURCE', color: '#64748B', bg: '#F1F5F9' }
]
const LANE_KEYS = LANES.map(l => l.key)
let laneMode = false
let laneHeight = 420

function computeLayout(nodeList: any[], edgeList: any[]) {
  // 有层级信息时使用六列泳道；AI 预览等无层级场景继续用 dagre
  if (nodeList.some((n: any) => n.layer)) {
    return computeLaneLayout(nodeList)
  }
  const g = new dagre.graphlib.Graph()
  g.setGraph({ rankdir: 'LR', nodesep: 50, ranksep: 110, marginx: 30, marginy: 30 })
  g.setDefaultEdgeLabel(() => ({}))
  nodeList.forEach(n => g.setNode(String(n.id), { width: 200, height: 64 }))
  edgeList.forEach(e => {
    const s = String(e.source), t = String(e.target)
    if (s !== t && g.hasNode(s) && g.hasNode(t)) g.setEdge(s, t)
  })
  dagre.layout(g)
  const out = new Map<string, { x: number; y: number }>()
  const paths = new Map<string, { x: number; y: number }[]>()
  g.nodes().forEach(id => {
    const n = g.node(id)
    out.set(id, { x: n.x, y: n.y })
  })
  g.edges().forEach(e => {
    const pts = g.edge(e).points || []
    paths.set(`${e.v}->${e.w}`, pts.map((p: any) => ({ x: p.x, y: p.y })))
  })
  return { out, paths, laneMode: false, height: 0 }
}

/** 六列泳道布局：x 按层固定，y 在列内均匀展开 */
function computeLaneLayout(nodeList: any[]) {
  const groups = new Map<string, any[]>(LANE_KEYS.map(k => [k, []]))
  for (const n of nodeList) {
    const key = n.layer === 'DIM' ? 'DWD' : (groups.has(n.layer) ? n.layer : 'SOURCE')
    groups.get(key)!.push(n)
  }
  const maxCount = Math.max(1, ...Array.from(groups.values()).map(a => a.length))
  const laneGap = 250
  const nodeGap = 78
  const height = Math.max(460, maxCount * nodeGap + 180)
  const out = new Map<string, { x: number; y: number }>()
  LANES.forEach((lane, i) => {
    const list = groups.get(lane.key) || []
    const x = 140 + i * laneGap
    const startY = 100 + (maxCount - list.length) * nodeGap / 2
    list.forEach((n: any, j: number) => out.set(String(n.id), { x, y: startY + j * nodeGap }))
  })
  return { out, paths: new Map<string, { x: number; y: number }[]>(), laneMode: true, height, laneGap, laneX0: 140 }
}

/** 计算每个节点距离中心节点的层级（BFS，用于背景带与透明度分级） */
function computeRanks(nodeList: any[], edgeList: any[], centerId: any) {
  const ranks = new Map<string, number>()
  if (centerId == null || centerId === '') return ranks
  const cKey = String(centerId)
  ranks.set(cKey, 0)
  const adjacency = new Map<string, string[]>()
  nodeList.forEach(n => adjacency.set(String(n.id), []))
  edgeList.forEach(e => {
    const s = String(e.source), t = String(e.target)
    adjacency.get(s)?.push(t)
    adjacency.get(t)?.push(s)
  })
  const queue: string[] = [cKey]
  while (queue.length) {
    const cur = queue.shift()!
    const r = ranks.get(cur)!
    for (const nb of adjacency.get(cur) || []) {
      if (!ranks.has(nb)) {
        ranks.set(nb, r + 1)
        queue.push(nb)
      }
    }
  }
  return ranks
}

function buildNeighbors() {
  const map = new Map<string, Set<string>>()
  for (const e of edges.value) {
    const s = String(e.source), t = String(e.target)
    if (!map.has(s)) map.set(s, new Set())
    if (!map.has(t)) map.set(t, new Set())
    map.get(s)!.add(t)
    map.get(t)!.add(s)
  }
  return map
}

function pos(key: string): { x: number; y: number } | undefined {
  if (fixedPositions.has(key)) return fixedPositions.get(key)
  return positions.get(key)
}

/** 边路径：优先用 dagre 折线（平滑），拖动节点时回退为直线 */
function edgePathD(d: any) {
  const s = pos(String(d.source)), t = pos(String(d.target))
  const key = `${String(d.source)}->${String(d.target)}`
  if (laneMode && s && t) {
    const dx = Math.max(60, Math.abs(t.x - s.x) * 0.45)
    return `M${s.x},${s.y} C${s.x + dx},${s.y} ${t.x - dx},${t.y} ${t.x},${t.y}`
  }
  const pts = edgePaths.get(key)
  if (fixedPositions.size > 0 || !pts || pts.length < 2) {
    return `M${s?.x ?? 0},${s?.y ?? 0} L${t?.x ?? 0},${t?.y ?? 0}`
  }
  return d3.line<{ x: number; y: number }>()
    .x(p => p.x).y(p => p.y).curve(d3.curveBasis)(pts) || ''
}

// ==================== 渲染 ====================
function render() {
  const svgEl = svgRef.value
  if (!svgEl) return
  const svg = d3.select(svgEl)
  svg.selectAll('*').remove()
  if (!nodes.value.length) return

  const width = containerRef.value?.clientWidth || 700
  const layout: any = computeLayout(nodes.value, edges.value)
  canvasHeight.value = Math.max(props.height, layout.height ? layout.height + 90 : props.height)
  const height = canvasHeight.value
  positions = layout.out
  edgePaths = layout.paths
  laneMode = !!layout.laneMode
  laneHeight = layout.height || height
  rankMap = computeRanks(nodes.value, edges.value, centerId.value)
  neighborMap = buildNeighbors()
  highlightSet = null

  const g = svg.append('g')

  if (laneMode) {
    // 六列泳道：顶部卡片式列头 + 浅色列背景
    const laneW = 230
    LANES.forEach((lane, i) => {
      const x = 140 + i * 250
      g.append('rect')
        .attr('x', x - laneW / 2)
        .attr('y', 10)
        .attr('width', laneW)
        .attr('height', 44)
        .attr('fill', lane.bg)
        .attr('stroke', lane.color + '33')
        .attr('rx', 9)
      g.append('text')
        .attr('x', x - laneW / 2 + 14)
        .attr('y', 31)
        .attr('font-size', 12)
        .attr('font-weight', 700)
        .attr('fill', lane.color)
        .attr('pointer-events', 'none')
        .text(lane.label)
      g.append('text')
        .attr('x', x - laneW / 2 + 14)
        .attr('y', 45)
        .attr('font-size', 9)
        .attr('fill', lane.color)
        .attr('fill-opacity', 0.65)
        .attr('pointer-events', 'none')
        .text(lane.en)
      g.append('rect')
        .attr('x', x - laneW / 2)
        .attr('y', 64)
        .attr('width', laneW)
        .attr('height', laneHeight)
        .attr('fill', lane.bg)
        .attr('fill-opacity', 0.45)
        .attr('rx', 10)
    })
  } else {
  // 层级背景带（有中心按 BFS 层级；无中心按 dagre 分层 x 坐标，从左到右交替着色）
  const bandW = 240
  const hasCenter = centerId.value != null && centerId.value !== ''
  const bands: { x: number; label: string; isCenter: boolean }[] = []
  if (hasCenter) {
    const rankX = new Map<number, number>()
    positions.forEach((p, id) => {
      const r = rankMap.get(id) ?? 0
      if (!rankX.has(r)) rankX.set(r, p.x)
    })
    Array.from(rankX.entries()).sort((a, b) => a[0] - b[0]).forEach(([r, x]) => {
      bands.push({ x, label: r === 0 ? '中心' : `${r} 层`, isCenter: r === 0 })
    })
  } else {
    const xMap = new Map<number, number>()
    positions.forEach(p => {
      const k = Math.round(p.x / 40) * 40
      if (!xMap.has(k)) xMap.set(k, p.x)
    })
    Array.from(xMap.entries()).sort((a, b) => a[0] - b[0]).forEach(([, x], i) => {
      bands.push({ x, label: `${i + 1} 层`, isCenter: false })
    })
  }
  bands.forEach((b, i) => {
    if (b.isCenter) return
    g.append('rect')
      .attr('x', b.x - bandW / 2)
      .attr('y', -30)
      .attr('width', bandW)
      .attr('height', height + 60)
      .attr('fill', i % 2 === 0 ? '#eef3f8' : '#f8fafc')
      .attr('rx', 10)
    g.append('text')
      .attr('x', b.x)
      .attr('y', 16)
      .attr('text-anchor', 'middle')
      .attr('font-size', 11)
      .attr('font-weight', 600)
      .attr('fill', '#94a3b8')
      .attr('pointer-events', 'none')
      .text(b.label)
  })
  }

  // 缩放/平移
  const zoom = d3.zoom<SVGSVGElement, unknown>()
    .scaleExtent([0.2, 4])
    .on('zoom', (event) => {
      zoomTransform = event.transform
      g.attr('transform', event.transform)
    })
  svg.call(zoom as any)
  if (zoomTransform) {
    svg.call(zoom.transform as any, zoomTransform)
  } else if (laneMode) {
    const contentWidth = 6 * 250 + 40
    if (width < contentWidth) {
      const scale = Math.max(0.55, (width - 16) / contentWidth)
      svg.call(zoom.transform as any, d3.zoomIdentity.translate(8, 0).scale(scale))
    }
  }

  // 点击空白取消高亮（仅当点击目标是 svg 背景或背景带时，节点点击由 onNodeClick 处理）
  svg.on('click', (event: any) => {
    const target = event.target as Element
    if (target && target.closest && target.closest('.lg-node')) return
    highlightSet = null
    applyOpacity(svg)
  })

  // 箭头
  svg.append('defs').append('marker')
    .attr('id', 'lg-arrow')
    .attr('viewBox', '0 -5 10 10')
    .attr('refX', 22)
    .attr('refY', 0)
    .attr('markerWidth', 6)
    .attr('markerHeight', 6)
    .attr('orient', 'auto')
    .append('path')
    .attr('d', 'M0,-5L10,0L0,5')
    .attr('fill', '#94a3b8')

  // 边（dagre 折线路径，平滑曲线，减少交叉）
  const link = g.append('g')
    .selectAll('path')
    .data(edges.value)
    .join('path')
    .attr('class', 'lg-link')
    .attr('d', (d: any) => edgePathD(d))
    .attr('fill', 'none')
    .attr('stroke', (d: any) => d.relationType === 'REFERENCE' ? '#8b5cf6' : '#94a3b8')
    .attr('stroke-dasharray', (d: any) => d.relationType === 'REFERENCE' ? '6 3' : null)
    .attr('stroke-width', (d: any) => Math.min(1.5 + (d.count || 1) * 0.6, 4.5))
    .attr('stroke-opacity', 0.75)
    .attr('marker-end', 'url(#lg-arrow)')

  // 聚合边计数标注（×N）
  const linkLabel = g.append('g')
    .selectAll('text')
    .data(edges.value.filter((e: any) => (e.count || 1) > 1))
    .join('text')
    .attr('class', 'lg-link-label')
    .text((d: any) => `×${d.count}`)
    .attr('font-size', '10px')
    .attr('fill', '#8b5cf6')
    .attr('text-anchor', 'middle')
    .attr('dy', -6)

  // 节点（卡片式：圆角矩形 + 居中表名，字段节点不再混入）
  const dragBehavior = d3.drag<SVGGElement, any, any>()
    .clickDistance(6) // 移动超过 6px 才视为拖动
    .on('start', (event: any, d: any) => {
      dragged = false
      if (!event.active) event.subject.fx = event.subject.x
    })
    .on('drag', (event: any, d: any) => {
      dragged = true
      d.fx = event.x; d.fy = event.y
      fixedPositions.set(String(d.id), { x: event.x, y: event.y })
      updatePositions()
    })
    .on('end', (event: any, d: any) => {
      d.fx = null; d.fy = null
      // 点击（未拖动）时在此处触发下钻，避免 click 事件被 d3.drag 抑制
      if (!dragged) {
        suppressClick = true
        onNodeClick(d)
      }
    }) as any
  const NODE_W = 200
  const NODE_H = 64
  const node = g.append('g')
    .selectAll('g')
    .data(nodes.value)
    .join<SVGGElement, any>('g')
    .attr('class', 'lg-node')
    .style('cursor', 'pointer')
    .on('click', (_ev: any, d: any) => {
      // 若已在 drag end 中处理过点击，则跳过（避免重复下钻）
      if (suppressClick) { suppressClick = false; return }
      onNodeClick(d)
    })
    .on('dblclick', (_ev: any, d: any) => onNodeDblClick(d))
    .call(dragBehavior)

  // 卡片：白底 + 左侧层级色条 + 名称/元信息 + 右侧圆点
  node.append('rect')
    .attr('class', 'lg-node-card')
    .attr('x', -NODE_W / 2)
    .attr('y', -NODE_H / 2)
    .attr('width', NODE_W)
    .attr('height', NODE_H)
    .attr('rx', 10)
    .attr('fill', '#ffffff')
    .attr('stroke', (d: any) => d.isCenter ? nodeColor(d) : '#E2E8F0')
    .attr('stroke-width', (d: any) => d.isCenter ? 2.5 : 1)

  node.append('rect')
    .attr('x', -NODE_W / 2)
    .attr('y', -NODE_H / 2)
    .attr('width', 5)
    .attr('height', NODE_H)
    .attr('rx', 2)
    .attr('fill', (d: any) => nodeColor(d))

  node.append('text')
    .attr('class', 'lg-label')
    .attr('x', -NODE_W / 2 + 16)
    .attr('y', -4)
    .attr('text-anchor', 'start')
    .attr('font-size', 12)
    .attr('font-weight', 600)
    .attr('fill', '#0F172A')
    .style('pointer-events', 'none')
    .text((d: any) => truncate(d.label || d.name || '', 22))

  node.append('text')
    .attr('class', 'lg-meta')
    .attr('x', -NODE_W / 2 + 16)
    .attr('y', 16)
    .attr('text-anchor', 'start')
    .attr('font-size', 10)
    .attr('fill', '#64748B')
    .style('pointer-events', 'none')
    .text((d: any) => nodeMeta(d))

  node.append('circle')
    .attr('cx', NODE_W / 2 - 14)
    .attr('cy', 0)
    .attr('r', 4)
    .attr('fill', (d: any) => nodeColor(d))
    .style('pointer-events', 'none')

  // tooltip（原生 title，展示全名与关联条件）
  node.append('title').text((d: any) => d.name || d.label)
  link.append('title').text((d: any) => (d.labels || []).join('\n') || '')
  linkLabel.append('title').text((d: any) => (d.labels || []).join('\n') || '')

  updatePositions()
  return svg
}

function updatePositions() {
  const svgEl = svgRef.value
  if (!svgEl) return
  const svg = d3.select(svgEl)
  svg.selectAll<SVGPathElement, any>('path.lg-link')
    .attr('d', (d: any) => edgePathD(d))
  svg.selectAll<SVGGElement, any>('g.lg-node')
    .attr('transform', (d: any) => `translate(${pos(String(d.id))?.x ?? 0},${pos(String(d.id))?.y ?? 0})`)
  svg.selectAll<SVGTextElement, any>('text.lg-link-label')
    .attr('x', (d: any) => ((pos(String(d.source))?.x ?? 0) + (pos(String(d.target))?.x ?? 0)) / 2)
    .attr('y', (d: any) => ((pos(String(d.source))?.y ?? 0) + (pos(String(d.target))?.y ?? 0)) / 2)
  applyOpacity(svg)
}

/** 两跳高亮：高亮节点及其一跳邻居，其余降透明度 */
function applyOpacity(svg: any) {
  svg.selectAll('g.lg-node')
    .attr('opacity', (d: any) => !highlightSet || highlightSet.has(String(d.id)) ? 1 : 0.15)
  svg.selectAll('path.lg-link')
    .attr('opacity', (d: any) => !highlightSet
      || (highlightSet.has(String(d.source)) && highlightSet.has(String(d.target))) ? 1 : 0.15)
}

function onNodeClick(d: any) {
  if (dragged) { dragged = false; return }
  const key = String(d.id)
  // 再次点击同一中心节点则取消高亮
  if (highlightSet && highlightSet.size === 1 && highlightSet.has(key)) {
    highlightSet = null
  } else {
    highlightSet = new Set([key, ...(neighborMap.get(key) || [])])
  }
  if (svgRef.value) applyOpacity(d3.select(svgRef.value))
  emit('node-click', { id: d.id, name: d.name || d.label, type: d.type })
}

/** 双击下钻：以该节点为新中心重新加载（字段节点不可下钻） */
function onNodeDblClick(d: any) {
  if (d.type === 'FIELD') return
  if (String(d.id) === String(centerId.value)) return
  history.value.push({ id: centerId.value, name: centerName.value })
  centerId.value = d.id
  centerName.value = d.label || String(d.id)
  loadGraph()
}

function goBack() {
  const prev = history.value.pop()
  if (!prev) return
  centerId.value = prev.id
  centerName.value = prev.name
  loadGraph()
}

function resetView() {
  zoomTransform = null
  fixedPositions = new Map()
  if (svgRef.value) {
    const svg = d3.select(svgRef.value)
    svg.call(d3.zoom<SVGSVGElement, unknown>().transform as any, d3.zoomIdentity)
  }
  const layout: any = computeLayout(nodes.value, edges.value)
  positions = layout.out
  edgePaths = layout.paths
  laneMode = !!layout.laneMode
  laneHeight = layout.height || 420
  updatePositions()
}

// ==================== 生命周期 ====================
watch(() => props.nodeId, (v) => {
  if (v == null) return
  if (String(v) !== String(centerId.value)) {
    centerId.value = v
    centerName.value = props.nodeName || ''
    history.value = []
    loadGraph()
  }
})

// 外部数据模式（AI 识别预览）：数据变化时重新渲染
watch(() => props.externalNodes, () => {
  if (props.externalNodes && props.externalNodes.length) loadExternal()
}, { deep: true })

onMounted(() => {
  if (props.externalNodes && props.externalNodes.length) loadExternal()
  else loadGraph()
})

// 对外暴露：刷新图谱 / 重置视图
// @ts-ignore
defineExpose({ reload: loadGraph, resetView })
</script>

<style scoped>
.lg-wrap {
  position: relative;
  width: 100%;
}
.lg-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid #e4e7ed;
  border-bottom: none;
  border-radius: 4px 4px 0 0;
  background: #fafafa;
  flex-wrap: wrap;
}
.lg-center-name {
  font-weight: 600;
  font-size: 13px;
  color: #1f2d3d;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lg-field-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #606266;
}
.lg-hint {
  margin-left: auto;
  font-size: 12px;
  color: #a0a4ab;
}
.lg-canvas {
  position: relative;
  border: 1px solid #e4e7ed;
  border-radius: 0 0 10px 10px;
  overflow: hidden;
  background: #fbfcfe;
  transition: height .2s ease;
}
.lg-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 13px;
  background: rgba(255, 255, 255, 0.6);
}
.lg-legend {
  position: absolute;
  bottom: 10px;
  right: 10px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 8px 10px;
  font-size: 11px;
  color: #606266;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  pointer-events: none;
}
.lg-legend-title {
  font-weight: 600;
  margin-bottom: 4px;
  color: #303133;
}
.lg-legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 2px 0;
  white-space: nowrap;
}
.lg-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  display: inline-block;
  border: 1px solid rgba(0, 0, 0, 0.08);
}
.lg-line-solid {
  width: 18px;
  height: 2px;
  background: #94a3b8;
  display: inline-block;
}
.lg-line-dash {
  width: 18px;
  height: 2px;
  background: repeating-linear-gradient(90deg, #8b5cf6 0 5px, transparent 5px 8px);
  display: inline-block;
}
</style>
