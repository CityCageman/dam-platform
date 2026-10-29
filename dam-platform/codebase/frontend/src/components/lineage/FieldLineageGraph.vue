<template>
  <div class="flg-wrap">
    <div v-if="!fields.length" class="flg-empty">该表暂无字段级血缘，或请先在「AI 识别」中识别字段级关系</div>
    <div v-else ref="chartRef" class="flg-canvas" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import * as dagre from 'dagre'

const props = defineProps<{ fields: any[] }>()

const chartRef = ref<HTMLElement>()
let chart: any = null

function render() {
  const el = chartRef.value
  if (!el) return
  if (el.clientWidth === 0 || el.clientHeight === 0) {
    // 容器尚未完成布局（Tab 切换/抽屉展开瞬间），延迟重试直到尺寸就绪
    setTimeout(render, 80)
    return
  }
  if (!chart) chart = echarts.init(el)

  const fields = props.fields || []
  if (!fields.length) { chart.clear(); return }

  // 表 → 分类索引（颜色区分不同表）
  const tableIndex = new Map<string, number>()
  const tableName = (v: any) => (v || '').toString()
  const nodeMap = new Map<string, any>()
  const links: any[] = []

  for (const f of fields) {
    const st = tableName(f.sourceTableName || f.sourceTableId)
    const tt = tableName(f.targetTableName || f.targetTableId)
    if (!st) continue
    if (!tt) continue
    if (!tableIndex.has(st)) tableIndex.set(st, tableIndex.size)
    if (!tableIndex.has(tt)) tableIndex.set(tt, tableIndex.size)
    const sid = `${st}::${f.sourceColumnName}`
    const tid = `${tt}::${f.targetColumnName}`
    if (!nodeMap.has(sid)) nodeMap.set(sid, { id: sid, name: `${st}.${f.sourceColumnName}`, category: tableIndex.get(st) })
    if (!nodeMap.has(tid)) nodeMap.set(tid, { id: tid, name: `${tt}.${f.targetColumnName}`, category: tableIndex.get(tt) })
    links.push({ source: sid, target: tid })
  }

  const nodes = Array.from(nodeMap.values())
  const categories = Array.from(tableIndex.keys()).map(name => ({ name }))

  // dagre 分层布局（字段节点按依赖方向排列）
  const g = new dagre.graphlib.Graph()
  g.setGraph({ rankdir: 'LR', nodesep: 30, ranksep: 120, marginx: 20, marginy: 20 })
  g.setDefaultEdgeLabel(() => ({}))
  nodes.forEach(n => g.setNode(n.id, { width: 160, height: 28 }))
  links.forEach(l => {
    if (l.source !== l.target && g.hasNode(l.source) && g.hasNode(l.target)) g.setEdge(l.source, l.target)
  })
  dagre.layout(g)

  chart.setOption({
    tooltip: { formatter: (p: any) => p.dataType === 'edge' ? `${p.data.source} → ${p.data.target}` : p.name },
    legend: categories.length > 1 ? [{ type: 'scroll', data: categories.map(c => c.name), bottom: 0 }] : undefined,
    series: [{
      type: 'graph',
      categories, // 节点分类（表），供 legend 与颜色区分
      layout: 'none', // 使用 dagre 布局的坐标
      data: nodes.map(n => {
        const dn = g.node(n.id)
        return { id: n.id, name: n.name, category: n.category, symbolSize: 12, x: dn.x, y: dn.y }
      }),
      links,
      roam: true,
      draggable: true,
      label: { show: true, position: 'right', fontSize: 11 },
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: 6,
      lineStyle: { color: '#94a3b8', width: 1.5, curveness: 0.12 }
    }]
  }, true)
}

function resize() {
  chart?.resize()
}

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  nextTick(render)
  window.addEventListener('resize', resize)
  // 监听容器尺寸变化（Tab 切换、抽屉展开时容器从 0 变为实际尺寸），尺寸就绪后自动适配
  if (chartRef.value) {
    resizeObserver = new ResizeObserver(() => {
      chart?.resize()
    })
    resizeObserver.observe(chartRef.value)
  }
})

watch(() => props.fields, () => nextTick(render), { deep: true })
</script>

<style scoped>
.flg-wrap {
  width: 100%;
  height: 100%;
  position: relative;
}
.flg-canvas {
  width: 100%;
  height: 460px;
}
.flg-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: #909399;
  font-size: 13px;
}
</style>
