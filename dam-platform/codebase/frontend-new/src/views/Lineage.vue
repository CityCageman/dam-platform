<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">数据血缘</h2>
        <p class="page-desc">以任一资产为中心，查看上下游依赖链路，支持表级与字段级血缘，用于影响分析与问题溯源。</p>
      </div>
      <el-button :icon="Plus" @click="openEdge">新增血缘关系</el-button>
    </div>

    <div class="panel toolbar">
      <el-radio-group v-model="mode" size="small" @change="onModeChange">
        <el-radio-button value="asset">资产血缘</el-radio-button>
        <el-radio-button value="meta">全域血缘</el-radio-button>
        <el-radio-button value="sql">SQL 血缘</el-radio-button>
        <el-radio-button value="ai">AI 推断</el-radio-button>
      </el-radio-group>

      <template v-if="mode === 'asset'">
        <span class="label">中心资产</span>
        <el-select v-model="assetId" filterable placeholder="选择资产" style="width: 300px" @change="load">
          <el-option v-for="a in assets" :key="a.id" :label="`${a.name}（${a.assetCode}）`" :value="a.id" />
        </el-select>
        <span class="label">扩散层数</span>
        <el-radio-group v-model="depth" size="small" @change="load">
          <el-radio-button :value="1">1 层</el-radio-button>
          <el-radio-button :value="2">2 层</el-radio-button>
          <el-radio-button :value="3">3 层</el-radio-button>
        </el-radio-group>
      </template>

      <template v-else>
        <span class="label">方向</span>
        <el-radio-group v-model="direction" size="small" @change="loadMeta">
          <el-radio-button value="BOTH">双向</el-radio-button>
          <el-radio-button value="UP">仅上游</el-radio-button>
          <el-radio-button value="DOWN">仅下游</el-radio-button>
        </el-radio-group>
        <span class="tip">覆盖元数据表 / 码表 / 模型的全链路血缘，点击数据表节点可设为中心展开。</span>
        <el-button v-if="metaTableId" size="small" @click="metaTableId = null; loadMeta()">清除中心</el-button>
        <span class="legend">
          <span class="lg" v-for="(m, t) in NODE_META" :key="t"><i :style="{ background: m.color }"></i>{{ m.label }}</span>
        </span>
      </template>

      <div class="spacer"></div>
      <span class="muted" v-if="mode === 'asset' || mode === 'meta'">
        {{ graph.nodes?.length || 0 }} 个节点 · {{ graph.edges?.length || 0 }} 条关系
      </span>
    </div>

    <!-- SQL 血缘解析（Druid 确定性解析，AI 无关） -->
    <div class="panel work-panel" v-if="mode === 'sql'">
      <div class="work-head">
        <el-select v-model="dbType" style="width: 130px">
          <el-option label="PostgreSQL" value="POSTGRESQL" />
          <el-option label="MySQL / Doris" value="MYSQL" />
          <el-option label="Oracle（近似 IRIS）" value="ORACLE" />
          <el-option label="SQL Server（近似 IRIS）" value="SQLSERVER" />
        </el-select>
        <el-button type="primary" :loading="sqlBusy" @click="parseSql">解析血缘</el-button>
        <el-button :disabled="!sqlResult?.edges?.length" :loading="sqlSaving" @click="saveSql">
          入库（{{ sqlResult?.edges?.filter(e => e.resolvable).length || 0 }} 条可入库）
        </el-button>
        <span class="tip">粘贴 ETL 脚本（支持多语句分号分隔），解析 INSERT-SELECT / CTAS / UPDATE / MERGE 的 源表→目标表 血缘。</span>
      </div>
      <el-input v-model="sqlText" type="textarea" :rows="8" class="sql-input mono"
                placeholder="-- 例：INSERT INTO ods.visit_visitinfo SELECT ... FROM iris_mzgh.gh_mx a JOIN emr.patient b ON ..." />
      <template v-if="sqlResult">
        <el-alert v-if="sqlResult.unmapped?.length" type="warning" :closable="false" class="work-alert"
                  :title="`以下表未在元数据中登记，对应边无法入库：${sqlResult.unmapped.join('、')}`" />
        <el-table :data="sqlResult.edges" size="small" max-height="300">
          <el-table-column label="上游表" min-width="180">
            <template #default="{ row }"><span class="mono">{{ row.sourceName }}</span></template>
          </el-table-column>
          <el-table-column label="下游表" min-width="180">
            <template #default="{ row }"><span class="mono">{{ row.targetName }}</span></template>
          </el-table-column>
          <el-table-column prop="relationType" label="类型" width="110">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ REL_LABEL[row.relationType] || row.relationType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="涉及字段" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ (row.columns || []).join(', ') }}</template>
          </el-table-column>
          <el-table-column label="可入库" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.resolvable ? 'success' : 'info'">{{ row.resolvable ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>

    <!-- AI 血缘推断 -->
    <div class="panel work-panel" v-if="mode === 'ai'">
      <div class="work-head">
        <span class="label">范围</span>
        <el-select v-model="aiSchema" clearable filterable placeholder="全部 schema" style="width: 170px">
          <el-option v-for="s in metaSchemas" :key="s" :label="s" :value="s" />
        </el-select>
        <span class="label">最多表数</span>
        <el-input-number v-model="aiLimit" :min="4" :max="120" :step="12" controls-position="right" style="width: 110px" />
        <el-checkbox v-model="aiAutoSave">推断后直接入库</el-checkbox>
        <el-button type="primary" :loading="aiBusy" @click="runAiInfer">开始 AI 推断</el-button>
        <span class="tip">把元数据表/字段（含中文注释）交给大模型，按语义推断上游源表→下游加工表的血缘。</span>
      </div>
      <template v-if="aiResult">
        <div class="work-head" style="margin-bottom:8px">
          <el-tag size="small">模型：{{ aiResult.model }}</el-tag>
          <el-tag size="small" type="info">参与表：{{ aiResult.tableCount }}</el-tag>
          <el-tag size="small" type="success">推断边：{{ aiResult.edges?.length || 0 }}</el-tag>
          <span class="tip" v-if="aiResult.unmapped?.length">未识别表：{{ aiResult.unmapped.join('、') }}</span>
        </div>
        <el-table :data="aiResult.edges" size="small" max-height="360">
          <el-table-column label="上游表" min-width="170">
            <template #default="{ row }"><span class="mono">{{ row.source }}</span></template>
          </el-table-column>
          <el-table-column label="下游表" min-width="170">
            <template #default="{ row }"><span class="mono">{{ row.target }}</span></template>
          </el-table-column>
          <el-table-column prop="columnMapping" label="字段映射" min-width="220" show-overflow-tooltip />
          <el-table-column prop="reason" label="推断依据" min-width="200" show-overflow-tooltip />
          <el-table-column label="置信度" width="100" align="center">
            <template #default="{ row }">
              <span :style="{ color: row.confidence >= 0.8 ? '#52c41a' : row.confidence >= 0.6 ? '#e6a23c' : '#f5222d' }">
                {{ (row.confidence * 100).toFixed(0) }}%
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.saved" size="small" type="success">已入库</el-tag>
              <el-tag v-else size="small" type="info">待确认</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div class="work-head" v-if="!aiAutoSave && aiResult.edges?.length" style="margin-top:10px">
          <el-button type="primary" :loading="aiBusy" @click="saveAiEdges">入库这 {{ aiResult.edges.length }} 条</el-button>
          <span class="tip">入库后在「全域血缘」图中查看（关系类型 AI_INFER）。</span>
        </div>
      </template>
    </div>

    <div class="layout" :class="{ full: mode !== 'asset' }" v-show="mode === 'asset' || mode === 'meta'">
      <div class="panel chart-panel">
        <div ref="chartRef" class="graph"></div>
      </div>

      <div class="panel side" v-if="mode === 'asset'">
        <h3 class="panel-title">关系明细</h3>
        <div class="edge" v-for="e in graph.edges" :key="e.id">
          <div class="edge-line">
            <span class="node-name">{{ nameOf(e.sourceAssetId) }}</span>
            <el-icon class="arrow"><Right /></el-icon>
            <span class="node-name">{{ nameOf(e.targetAssetId) }}</span>
          </div>
          <div class="edge-meta">
            <span class="mono">{{ e.jobName || '手工维护' }}</span>
            <el-tag size="small" effect="plain">{{ e.transformType || 'ETL' }}</el-tag>
            <span v-if="e.sourceColumn || e.targetColumn" class="mono muted">
              {{ e.sourceColumn || '*' }} → {{ e.targetColumn || '*' }}
            </span>
            <el-icon class="del" @click="removeEdge(e)"><Delete /></el-icon>
          </div>
        </div>
        <el-empty v-if="!graph.edges?.length" description="该资产暂无血缘关系" :image-size="70" />
      </div>
    </div>

    <el-dialog v-model="edgeDialog" title="新增血缘关系" width="560px">
      <el-form :model="edgeForm" label-width="96px">
        <el-form-item label="上游资产">
          <el-select v-model="edgeForm.sourceAssetId" filterable style="width:100%">
            <el-option v-for="a in assets" :key="a.id" :label="`${a.name}（${a.assetCode}）`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="下游资产">
          <el-select v-model="edgeForm.targetAssetId" filterable style="width:100%">
            <el-option v-for="a in assets" :key="a.id" :label="`${a.name}（${a.assetCode}）`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="上游字段">
          <el-input v-model="edgeForm.sourceColumn" placeholder="留空表示表级血缘" />
        </el-form-item>
        <el-form-item label="下游字段">
          <el-input v-model="edgeForm.targetColumn" placeholder="留空表示表级血缘" />
        </el-form-item>
        <el-form-item label="作业名称">
          <el-input v-model="edgeForm.jobName" placeholder="如：etl_dim_patient" />
        </el-form-item>
        <el-form-item label="加工方式">
          <el-select v-model="edgeForm.transformType" style="width:100%">
            <el-option label="ETL" value="ETL" />
            <el-option label="SQL" value="SQL" />
            <el-option label="API" value="API" />
            <el-option label="手工填报" value="MANUAL" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="edgeDialog = false">取消</el-button>
        <el-button type="primary" @click="saveEdge">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { Plus, Right, Delete } from '@element-plus/icons-vue'
import { api, LEVEL_META, TYPE_LABEL } from '../api'

const route = useRoute()
const mode = ref('asset')
const direction = ref('BOTH')
const metaTableId = ref(null)
const assets = ref([])
const assetId = ref(null)
const depth = ref(2)
const graph = ref({ nodes: [], edges: [] })
const chartRef = ref()
const edgeDialog = ref(false)
let chart = null

const NODE_META = {
  TABLE: { label: '数据表', color: '#2f7bff' },
  CODE_TABLE: { label: '码表', color: '#9254de' },
  MODEL_FACT: { label: '事实模型', color: '#e6a23c' },
  MODEL_DIMENSION: { label: '维度模型', color: '#52c41a' }
}

const edgeForm = reactive({
  sourceAssetId: null, targetAssetId: null, sourceColumn: '', targetColumn: '',
  jobName: '', transformType: 'ETL'
})

async function loadAssets() {
  const res = await api.assets({ page: 1, size: 200 })
  assets.value = res.items
}

function nameOf(id) {
  return assets.value.find((a) => a.id === id)?.name || `#${id}`
}

function onModeChange() {
  graph.value = { nodes: [], edges: [] }
  if (mode.value === 'meta') {
    loadMeta()
  } else if (mode.value === 'asset') {
    load()
  }
  nextTick(() => chart?.resize())
}

// ---------- SQL 血缘 ----------
const REL_LABEL = { SQL_INSERT: '写入', SQL_CTAS: '建表', SQL_UPDATE: '更新', SQL_MERGE: '合并', SQL_WRITE: '写入' }
const sqlText = ref('')
const dbType = ref('POSTGRESQL')
const sqlBusy = ref(false)
const sqlSaving = ref(false)
const sqlResult = ref(null)

async function parseSql() {
  if (!sqlText.value.trim()) return ElMessage.warning('请粘贴 SQL 脚本')
  sqlBusy.value = true
  try {
    sqlResult.value = await api.sqlLineageParse({ sqlText: sqlText.value, dbType: dbType.value })
    if (!sqlResult.value.edges?.length) ElMessage.info('未解析出血缘边，请检查 SQL 是否包含 INSERT-SELECT / CTAS 等写入语句')
  } finally {
    sqlBusy.value = false
  }
}

async function saveSql() {
  const edges = (sqlResult.value?.edges || []).filter((e) => e.resolvable)
  if (!edges.length) return
  sqlSaving.value = true
  try {
    const r = await api.sqlLineageSave(edges)
    ElMessage.success(`已入库 ${r.saved} 条血缘（跳过重复/不可解析 ${r.skipped} 条），可在全域血缘中查看`)
  } finally {
    sqlSaving.value = false
  }
}

// ---------- AI 血缘推断 ----------
const aiSchema = ref(null)
const aiLimit = ref(36)
const aiAutoSave = ref(false)
const aiBusy = ref(false)
const aiResult = ref(null)
const metaSchemas = ref([])

async function runAiInfer() {
  aiBusy.value = true
  aiResult.value = null
  try {
    aiResult.value = await api.aiInferLineage({
      schemaName: aiSchema.value || undefined,
      limit: aiLimit.value,
      save: aiAutoSave.value
    })
    if (!aiResult.value.edges?.length) ElMessage.info('AI 未推断出可信血缘，可扩大表数或补全中文注释后再试')
  } finally {
    aiBusy.value = false
  }
}

async function saveAiEdges() {
  const edges = (aiResult.value?.edges || []).map((e) => ({
    sourceTableId: e.sourceTableId,
    targetTableId: e.targetTableId,
    relationType: 'AI_INFER',
    discoveredBy: 'AI',
    columns: (e.columnMapping || '').split(';').map((x) => x.trim()).filter(Boolean),
    statement: e.reason
  }))
  if (!edges.length) return
  aiBusy.value = true
  try {
    const r = await api.sqlLineageSave(edges)
    ElMessage.success(`已入库 ${r.saved} 条 AI 血缘（跳过 ${r.skipped} 条重复）`)
    aiResult.value.edges.forEach((e) => { e.saved = true })
  } finally {
    aiBusy.value = false
  }
}

async function load() {
  if (!assetId.value) return
  graph.value = await api.lineageGraph(assetId.value, depth.value)
  await nextTick()
  render()
}

/** 全域血缘（v2）：dam.metadata_lineage 通用节点对 */
async function loadMeta() {
  graph.value = await api.lineageV2({
    tableId: metaTableId.value || undefined,
    depth: 2,
    direction: direction.value
  })
  await nextTick()
  render()
}

function render() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  if (mode.value === 'meta') {
    renderMeta()
    return
  }

  const nodes = graph.value.nodes || []
  const edges = graph.value.edges || []

  chart.setOption({
    tooltip: {
      formatter: (p) => {
        if (p.dataType === 'edge') {
          return `${nameOf(p.data.source)}\n  →  ${nameOf(p.data.target)}\n${p.data.jobName || ''}`
        }
        return `<b>${p.data.name}</b><br/>${p.data.code}<br/>${p.data.typeLabel} · ${p.data.levelLabel}`
      }
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        force: { repulsion: 520, edgeLength: [140, 220], gravity: 0.06 },
        symbolSize: (v, p) => (p.data.center ? 62 : 46),
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: 8,
        label: {
          show: true,
          position: 'bottom',
          fontSize: 11,
          color: '#34405a',
          formatter: (p) => p.data.name
        },
        lineStyle: { color: '#b8c4d9', width: 1.4, curveness: 0.08, opacity: 0.9 },
        emphasis: { focus: 'adjacency', lineStyle: { width: 2.4, color: '#2f7bff' } },
        itemStyle: {
          borderColor: '#fff',
          borderWidth: 2,
          shadowBlur: 12,
          shadowColor: 'rgba(31,42,61,.12)'
        },
        data: nodes.map((n) => ({
          name: n.name,
          code: n.assetCode,
          typeLabel: TYPE_LABEL[n.assetType] || n.assetType,
          levelLabel: LEVEL_META[n.securityLevel]?.name || n.securityLevel,
          center: n.id === graph.value.centerId,
          itemStyle: {
            color: n.id === graph.value.centerId
              ? '#2f7bff'
              : (LEVEL_META[n.securityLevel]?.color || '#98a2b3')
          }
        })),
        links: edges.map((e) => ({
          source: nameOf(e.sourceAssetId),
          target: nameOf(e.targetAssetId),
          jobName: e.jobName
        }))
      }
    ]
  })
}

/** 全域血缘渲染：节点按类型着色，点击节点设为中心 */
function renderMeta() {
  const nodes = graph.value.nodes || []
  const edges = graph.value.edges || []
  chart.setOption({
    tooltip: {
      formatter: (p) => {
        if (p.dataType === 'edge') {
          const e = edges[p.dataIndex] || {}
          const lines = [`<b>${p.data.source.split(':')[0]}</b> → <b>${p.data.target.split(':')[0]}</b>`]
          if (e.relationType) lines.push(`关系：${e.relationType}`)
          if (e.transformRule) lines.push(`加工：${e.transformRule}`)
          if (e.sourceSystem) lines.push(`系统：${e.sourceSystem}`)
          return lines.join('<br/>')
        }
        const m = NODE_META[p.data.nodeType] || {}
        return `<b>${p.data.metaName}</b><br/>类型：${m.label || p.data.nodeType}` +
          (p.data.datasource ? `<br/>数据源：${p.data.datasource}` : '') +
          (p.data.comment ? `<br/>${p.data.comment}` : '')
      }
    },
    legend: {
      top: 10, left: 14, icon: 'circle', itemWidth: 9, itemHeight: 9,
      textStyle: { color: '#5b6b85', fontSize: 12 },
      data: Object.values(NODE_META).map((m) => m.label)
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        force: { repulsion: 620, edgeLength: [150, 260], gravity: 0.05 },
        symbolSize: (v, p) => (p.data.nodeType === 'TABLE' && p.data.name === ('TABLE:' + metaTableId.value) ? 62 : 46),
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: 8,
        label: {
          show: true,
          position: 'bottom',
          fontSize: 11,
          color: '#34405a',
          formatter: (p) => p.data.metaName || p.data.name
        },
        lineStyle: { color: '#b8c4d9', width: 1.4, curveness: 0.08, opacity: 0.9 },
        emphasis: { focus: 'adjacency', lineStyle: { width: 2.4, color: '#2f7bff' } },
        itemStyle: {
          borderColor: '#fff',
          borderWidth: 2,
          shadowBlur: 12,
          shadowColor: 'rgba(31,42,61,.12)'
        },
        data: nodes.map((n) => ({
          name: n.key,
          nodeType: n.type,
          metaName: n.name,
          comment: n.comment,
          datasource: n.datasource,
          itemStyle: { color: (NODE_META[n.type] || {}).color || '#98a2b3' }
        })),
        links: edges.map((e) => ({
          source: e.source,
          target: e.target,
          relationType: e.relationType,
          transformRule: e.transformRule,
          sourceSystem: e.sourceSystem
        }))
      }
    ]
  }, true)

  chart.off('click')
  chart.on('click', (p) => {
    if (mode.value === 'meta' && p.dataType === 'node' && p.data.nodeType === 'TABLE') {
      const id = Number(p.data.name.split(':')[1])
      metaTableId.value = metaTableId.value === id ? null : id
      loadMeta()
    }
  })
}

function openEdge() {
  Object.assign(edgeForm, {
    sourceAssetId: assetId.value, targetAssetId: null,
    sourceColumn: '', targetColumn: '', jobName: '', transformType: 'ETL'
  })
  edgeDialog.value = true
}

async function saveEdge() {
  if (!edgeForm.sourceAssetId || !edgeForm.targetAssetId) return ElMessage.warning('请选择上下游资产')
  await api.createEdge({
    ...edgeForm,
    level: edgeForm.sourceColumn || edgeForm.targetColumn ? 'COLUMN' : 'TABLE'
  })
  ElMessage.success('已新增血缘关系')
  edgeDialog.value = false
  load()
}

async function removeEdge(e) {
  await api.deleteEdge(e.id)
  ElMessage.success('已删除')
  load()
}

const onResize = () => chart?.resize()

onMounted(async () => {
  await loadAssets()
  api.damOverview().then((d) => {
    metaSchemas.value = (d.schemas || []).map((s) => s.schemaName).filter(Boolean)
  }).catch(() => {})
  if (route.query.assetId) {
    assetId.value = Number(route.query.assetId)
  } else {
    // 默认选血缘关系最多的资产，避免打开就是空图
    const best = [...assets.value].sort((a, b) =>
      ((b.upstreamCount || 0) + (b.downstreamCount || 0)) - ((a.upstreamCount || 0) + (a.downstreamCount || 0)))[0]
    assetId.value = best?.id ?? assets.value[0]?.id
  }
  await load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  margin-bottom: 14px;
}

.label {
  color: #7a8699;
  font-size: 12px;
}

.spacer {
  flex: 1;
}

.layout {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 14px;
}

.layout.full { grid-template-columns: 1fr; }

.tip { color: #a2abb9; font-size: 11.5px; }

.legend { display: flex; gap: 12px; align-items: center; margin-left: 4px; }
.lg { display: flex; align-items: center; gap: 5px; font-size: 11.5px; color: #5b6b85; }
.lg i { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }

.chart-panel {
  padding: 0;
  overflow: hidden;
}

.graph {
  height: 560px;
  width: 100%;
}

.side {
  max-height: 560px;
  overflow: auto;
}

.edge {
  padding: 9px 0;
  border-bottom: 1px dashed #eef1f7;
}

.edge-line {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  font-weight: 500;
}

.arrow {
  color: #98a2b3;
  font-size: 12px;
}

.edge-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 5px;
  color: #98a2b3;
  font-size: 11px;
}

.del {
  margin-left: auto;
  cursor: pointer;
  color: #c0c8d6;
}

.del:hover {
  color: #f5222d;
}

.node-name {
  color: #34405a;
}

/* SQL 血缘 / AI 推断面板 */
.work-panel {
  padding: 14px 16px;
  margin-bottom: 14px;
}

.work-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.work-head .label {
  color: #7a8699;
  font-size: 12px;
}

.sql-input :deep(textarea) {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}

.work-alert {
  margin: 10px 0;
}
</style>
