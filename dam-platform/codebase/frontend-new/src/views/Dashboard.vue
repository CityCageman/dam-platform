<template>
  <div v-loading="loading">
    <div class="page-head">
      <div>
        <h2 class="page-title">数据资产总览</h2>
        <p class="page-desc">一屏掌握资产规模、质量水位与安全分布，数据来自元数据采集与治理任务的最新结果。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="stat-grid">
      <div class="stat-card" v-for="s in stats" :key="s.label">
        <div class="stat-label">{{ s.label }}</div>
        <div class="stat-value">{{ s.value }}</div>
        <div class="stat-foot">{{ s.foot }}</div>
      </div>
    </div>

    <!-- 三期：数据资产域（dam）指标 -->
    <div class="panel dam-panel">
      <div class="dam-head">
        <h3 class="panel-title">数据资产域 · dam 层（三期）</h3>
        <div class="dam-links">
          <router-link to="/catalog" class="dam-link">数据目录 →</router-link>
          <router-link to="/patient360" class="dam-link">患者 360 →</router-link>
        </div>
      </div>
      <div class="dam-grid">
        <div class="dam-cell" v-for="c in damCells" :key="c.label">
          <div class="dam-num">{{ fmtNum(c.value) }}</div>
          <div class="dam-label">{{ c.label }}</div>
        </div>
      </div>
    </div>

    <div class="row">
      <div class="panel">
        <h3 class="panel-title">数据质量趋势（近 7 天平均通过率）</h3>
        <div ref="trendRef" class="chart"></div>
      </div>
      <div class="panel">
        <h3 class="panel-title">资产安全分级分布</h3>
        <div ref="levelRef" class="chart"></div>
      </div>
    </div>

    <div class="row">
      <div class="panel">
        <h3 class="panel-title">业务域资产分布</h3>
        <div ref="domainRef" class="chart-sm"></div>
      </div>
      <div class="panel">
        <h3 class="panel-title">最近更新资产</h3>
        <div class="recent">
          <div class="recent-item" v-for="a in data.recentAssets" :key="a.id" @click="openAsset(a.id)">
            <div class="recent-main">
              <span class="recent-name">{{ a.name }}</span>
              <el-tag size="small" effect="plain">{{ TYPE_LABEL[a.assetType] || a.assetType }}</el-tag>
              <span class="level-dot" :style="{ background: LEVEL_META[a.securityLevel]?.color }"></span>
              <span class="muted">{{ LEVEL_META[a.securityLevel]?.name }}</span>
            </div>
            <div class="recent-meta">
              <span class="mono">{{ a.assetCode }}</span>
              <span>{{ a.domainName || '未分类' }}</span>
              <span>{{ fmtTime(a.updatedAt) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="panel" v-if="data.pendingIssues?.length">
      <h3 class="panel-title">待处理质量问题</h3>
      <el-table :data="data.pendingIssues" size="small">
        <el-table-column prop="ruleName" label="规则" min-width="200" />
        <el-table-column prop="assetName" label="所属资产" min-width="140" />
        <el-table-column label="通过率" width="110">
          <template #default="{ row }">
            <span class="danger">{{ row.passRate }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="最近执行" width="150">
          <template #default="{ row }">{{ fmtTime(row.runAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag type="danger" size="small">未通过</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { Refresh } from '@element-plus/icons-vue'
import { api, LEVEL_META, TYPE_LABEL, fmtTime, fmtNum } from '../api'

const router = useRouter()
const loading = ref(false)
const data = ref({})
const dam = ref({})
const trendRef = ref()
const levelRef = ref()
const domainRef = ref()
let charts = []

const damCells = computed(() => {
  const d = dam.value
  return [
    { label: '业务域', value: d.domainCount || 0 },
    { label: '事实模型', value: d.factCount || 0 },
    { label: '维度模型', value: d.dimensionCount || 0 },
    { label: '患者主索引', value: d.patientCount || 0 },
    { label: '就诊记录', value: d.encounterCount || 0 },
    { label: '元数据表', value: d.damTableCount || 0 },
    { label: '元数据字段', value: d.damColumnCount || 0 },
    { label: '血缘关系', value: d.lineageEdgeCount || 0 },
    { label: '码表 / 码值 / 对照', value: `${d.codeTableCount || 0} / ${d.codeValueCount || 0} / ${d.crosswalkCount || 0}` },
    { label: '码表国标覆盖', value: `${d.standardTableCount || 0} 张有依据 / ${d.localTableCount || 0} 张院内` },
    { label: '标准外扩展码值', value: d.localExtensionValueCount || 0 },
    { label: '脱敏策略', value: d.maskingPolicyCount || 0 },
    { label: '网关 API', value: d.apiServiceCount || 0 },
    { label: '调度任务（启用）', value: `${d.schedJobCount || 0}（${d.schedEnabledCount || 0}）` }
  ]
})

const stats = computed(() => {
  const d = data.value
  return [
    { label: '数据资产', value: fmtNum(d.assetCount || 0), foot: `${d.domainCount || 0} 个业务域` },
    { label: '元数据表', value: fmtNum(d.tableCount || 0), foot: `${fmtNum(d.columnCount || 0)} 个字段` },
    { label: '接入数据源', value: d.sourceCount || 0, foot: 'HIS / EMR / LIS + 自建库' },
    { label: '数据标准', value: d.standardCount || 0, foot: '已发布生效' },
    { label: '质量规则', value: d.ruleCount || 0, foot: `${d.lineageCount || 0} 条血缘关系` },
    { label: '平均质量分', value: d.avgQuality || 0, foot: '按最新规则通过率' }
  ]
})

function initChart(el, option) {
  const chart = echarts.init(el)
  chart.setOption(option)
  charts.push(chart)
  return chart
}

function render() {
  charts.forEach((c) => c.dispose())
  charts = []
  const d = data.value

  initChart(trendRef.value, {
    grid: { left: 40, right: 16, top: 24, bottom: 30 },
    tooltip: { trigger: 'axis', valueFormatter: (v) => v + '%' },
    xAxis: {
      type: 'category',
      data: (d.qualityTrend || []).map((x) => x.day),
      axisLine: { lineStyle: { color: '#e5e9f2' } },
      axisLabel: { color: '#8a94a6' }
    },
    yAxis: {
      type: 'value',
      min: 80,
      max: 100,
      splitLine: { lineStyle: { color: '#f0f2f7' } },
      axisLabel: { color: '#8a94a6', formatter: '{value}%' }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbolSize: 7,
        data: (d.qualityTrend || []).map((x) => x.passRate),
        itemStyle: { color: '#2f7bff' },
        lineStyle: { width: 2.5 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(47,123,255,.28)' },
            { offset: 1, color: 'rgba(47,123,255,0)' }
          ])
        }
      }
    ]
  })

  initChart(levelRef.value, {
    tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 个资产（{d}%）' },
    legend: { bottom: 0, icon: 'circle', textStyle: { color: '#5b6b85', fontSize: 12 } },
    series: [
      {
        type: 'pie',
        radius: ['48%', '70%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 3, borderRadius: 4 },
        label: { show: false },
        data: (d.levelDistribution || []).map((x) => ({
          name: x.name,
          value: x.cnt,
          itemStyle: { color: x.color }
        }))
      }
    ]
  })

  initChart(domainRef.value, {
    grid: { left: 80, right: 24, top: 12, bottom: 24 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f0f2f7' } },
      axisLabel: { color: '#8a94a6' }
    },
    yAxis: {
      type: 'category',
      data: (d.domainDistribution || []).map((x) => x.name),
      axisLine: { lineStyle: { color: '#e5e9f2' } },
      axisLabel: { color: '#5b6b85' }
    },
    series: [
      {
        type: 'bar',
        barWidth: 14,
        data: (d.domainDistribution || []).map((x) => x.cnt),
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#16c2c2' },
            { offset: 1, color: '#2f7bff' }
          ])
        }
      }
    ]
  })
}

function openAsset(id) {
  router.push({ path: '/assets', query: { id } })
}

async function load() {
  loading.value = true
  try {
    data.value = await api.overview()
    api.damOverview().then((d) => { dam.value = d })
    setTimeout(render, 0)
  } finally {
    loading.value = false
  }
}

const onResize = () => charts.forEach((c) => c.resize())

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c.dispose())
})

watch(() => data.value.qualityTrend, () => render())
</script>

<style scoped>
.dam-panel { margin: 14px 0; padding: 16px 18px; }

.dam-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }

.dam-links { display: flex; gap: 14px; }
.dam-link { font-size: 12px; color: #2f7bff; text-decoration: none; }
.dam-link:hover { text-decoration: underline; }

.dam-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
}

.dam-cell {
  background: linear-gradient(135deg, #f6f9ff, #fbfdff);
  border: 1px solid #e6edf9;
  border-radius: 10px;
  padding: 10px 14px;
}

.dam-num { font-size: 20px; font-weight: 700; color: #1f2a3d; }
.dam-label { font-size: 11px; color: #8a94a6; margin-top: 3px; }

.row {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 14px;
  margin: 14px 0;
}

.row:has(> :only-child) {
  grid-template-columns: 1fr;
}

.chart {
  height: 240px;
}

.chart-sm {
  height: 200px;
}

.recent-item {
  padding: 10px 4px;
  border-bottom: 1px dashed #eef1f7;
  cursor: pointer;
}

.recent-item:last-child {
  border-bottom: none;
}

.recent-item:hover .recent-name {
  color: #2f7bff;
}

.recent-main {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
}

.level-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-left: 4px;
}

.recent-meta {
  display: flex;
  gap: 14px;
  color: #98a2b3;
  font-size: 11.5px;
  margin-top: 4px;
  padding-left: 2px;
}

.danger {
  color: #f5222d;
  font-weight: 600;
}

@media (max-width: 1200px) {
  .row {
    grid-template-columns: 1fr;
  }
}
</style>
