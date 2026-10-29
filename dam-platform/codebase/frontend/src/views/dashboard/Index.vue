<template>
  <div v-loading="loading">
    <div class="page-head">
      <div>
        <h2 class="page-title">运营看板</h2>
        <p class="page-desc">一屏掌握资产规模、质量水位与平台运营概况。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <!-- 模块一：总览 -->
    <section class="module">
      <div class="module-head">
        <h3 class="module-title">总览</h3>
        <span class="module-sub">平台核心运营指标</span>
      </div>
      <div class="stat-grid">
        <div class="stat-card" v-for="s in stats" :key="s.label">
          <div class="stat-label">{{ s.label }}</div>
          <div class="stat-value">{{ s.value }}</div>
          <div class="stat-foot">{{ s.foot }}</div>
        </div>
      </div>
    </section>

    <!-- 模块二：数据资产 -->
    <section class="module">
      <div class="module-head">
        <h3 class="module-title">数据资产</h3>
        <div class="module-links">
          <router-link to="/catalog" class="module-link">资产目录 →</router-link>
          <router-link to="/metadata" class="module-link">元数据管理 →</router-link>
          <router-link to="/lineage" class="module-link">数据血缘 →</router-link>
        </div>
      </div>
      <div class="chart-grid">
        <div class="panel">
          <h4 class="panel-title">资产增长趋势</h4>
          <div ref="trendChart" class="chart"></div>
        </div>
        <div class="panel">
          <h4 class="panel-title">热门资产 TOP10<span v-if="!hasHot" class="hint">暂无使用统计</span></h4>
          <div ref="hotChart" class="chart"></div>
        </div>
      </div>
    </section>

    <!-- 模块三：治理能力 -->
    <section class="module">
      <div class="module-head">
        <h3 class="module-title">治理能力</h3>
        <span class="module-sub">常用治理模块快捷入口</span>
      </div>
      <div class="entry-grid">
        <router-link v-for="e in entries" :key="e.path" :to="e.path" class="entry-card">
          <div class="entry-icon" :style="{ background: e.color + '1a', color: e.color }">
            <el-icon :size="20"><component :is="e.icon" /></el-icon>
          </div>
          <div class="entry-body">
            <div class="entry-title">{{ e.title }}</div>
            <div class="entry-desc">{{ e.desc }}</div>
          </div>
        </router-link>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, shallowRef, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { Refresh } from '@element-plus/icons-vue'
import { operationsApi } from '@/api'

const loading = ref(false)
const hasHot = ref(false)
const stats = ref([
  { label: '资产总数', value: '-', foot: '已登记数据资产' },
  { label: '质量评分', value: '-', foot: '按最新规则通过率' },
  { label: '待办工单', value: '-', foot: '待处理 / 处理中' },
  { label: '活跃用户', value: '-', foot: '近期有操作的用户' }
])

// 治理能力快捷入口（与左侧导航大模块保持一致）
const entries = [
  { title: '数据标准', desc: '标准制定与落标核查', path: '/standard', icon: 'Document', color: '#2f7bff' },
  { title: '数据质量', desc: '质量规则与稽核结果', path: '/quality', icon: 'List', color: '#16c2c2' },
  { title: '安全管控', desc: '分级分类与脱敏策略', path: '/security', icon: 'Lock', color: '#ef4444' },
  { title: '生命周期', desc: '冷热分层与归档清理', path: '/lifecycle', icon: 'Timer', color: '#f59e0b' },
  { title: '工单中心', desc: '治理工单协同处理', path: '/operations', icon: 'Tickets', color: '#8b5cf6' },
  { title: '智能助手', desc: 'AI 辅助治理问答', path: '/agent', icon: 'ChatDotRound', color: '#0ea5e9' }
]

const trendChart = shallowRef<HTMLElement>()
const hotChart = shallowRef<HTMLElement>()
const charts: echarts.ECharts[] = []

const AXIS_COLOR = '#8a94a6'
const SPLIT_COLOR = '#f0f2f7'
const GRADIENT = (horizontal = false) => new echarts.graphic.LinearGradient(
  horizontal ? 0 : 0, horizontal ? 0 : 0, horizontal ? 1 : 0, horizontal ? 0 : 1,
  [{ offset: 0, color: '#2f7bff' }, { offset: 1, color: '#16c2c2' }]
)

function mount(el: HTMLElement | undefined, option: any) {
  if (!el) return
  const chart = echarts.init(el)
  chart.setOption(option)
  charts.push(chart)
}

function disposeCharts() {
  charts.forEach(c => c.dispose())
  charts.length = 0
}

async function load() {
  loading.value = true
  disposeCharts()
  try {
    const res: any = await operationsApi.getDashboard()
    const data = res.data
    if (data) {
      const o = data.overview || {}
      stats.value = [
        { label: '资产总数', value: o.totalAssets ?? '-', foot: '已登记数据资产' },
        { label: '质量评分', value: o.avgQualityScore ?? '-', foot: '按最新规则通过率' },
        { label: '待办工单', value: o.pendingTickets ?? '-', foot: '待处理 / 处理中' },
        { label: '活跃用户', value: o.activeUsers ?? '-', foot: '近期有操作的用户' }
      ]

      if (data.monthlyTrend) {
        const t = data.monthlyTrend
        mount(trendChart.value, {
          grid: { left: 40, right: 16, top: 24, bottom: 30 },
          xAxis: { type: 'category', data: t.months, axisLine: { lineStyle: { color: '#e5e9f2' } }, axisLabel: { color: AXIS_COLOR } },
          yAxis: { type: 'value', splitLine: { lineStyle: { color: SPLIT_COLOR } }, axisLabel: { color: AXIS_COLOR } },
          series: [
            { name: '资产增量', data: t.assetCounts, type: 'bar', barWidth: 16, itemStyle: { borderRadius: [4, 4, 0, 0], color: GRADIENT() } },
            { name: '工单增量', data: t.ticketCounts, type: 'line', smooth: true, itemStyle: { color: '#16c2c2' }, lineStyle: { width: 2.5 } }
          ],
          tooltip: { trigger: 'axis' }
        })
      }

      if (data.hotAssets) {
        hasHot.value = data.hotAssets.length > 0
        const top10 = data.hotAssets.slice(0, 10)
        mount(hotChart.value, {
          grid: { left: 70, right: 24, top: 12, bottom: 24 },
          xAxis: { type: 'value', splitLine: { lineStyle: { color: SPLIT_COLOR } }, axisLabel: { color: AXIS_COLOR } },
          yAxis: { type: 'category', data: top10.map((a: any) => '#' + a.assetId), inverse: true, axisLine: { lineStyle: { color: '#e5e9f2' } }, axisLabel: { color: '#5b6b85' } },
          series: [{ data: top10.map((a: any) => a.queryCount || 0), type: 'bar', barWidth: 14, itemStyle: { borderRadius: [0, 6, 6, 0], color: GRADIENT(true) } }],
          tooltip: { trigger: 'axis' }
        })
      }
    }
  } catch (_e) {
    // 接口异常时给空图，避免整页报错
    mount(trendChart.value, { grid: { left: 40, right: 16, top: 24, bottom: 30 }, xAxis: { type: 'category', data: [] }, yAxis: { type: 'value' }, series: [] })
    mount(hotChart.value, { grid: { left: 70, right: 24, top: 12, bottom: 24 }, xAxis: { type: 'value' }, yAxis: { type: 'category', data: [] }, series: [] })
  } finally {
    loading.value = false
  }
}

onMounted(load)
onBeforeUnmount(disposeCharts)
</script>

<style scoped>
.module { margin-top: 20px; }
.module-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.module-title {
  margin: 0; font-size: 15px; font-weight: 600; color: var(--med-text);
  padding-left: 10px; border-left: 3px solid var(--med-primary); line-height: 1.3;
}
.module-sub { font-size: 12px; color: var(--med-text-light); margin-left: 8px; }
.module-links { display: flex; gap: 14px; }
.module-link { font-size: 12px; color: var(--med-primary); text-decoration: none; }
.module-link:hover { text-decoration: underline; }

.chart-grid { display: grid; grid-template-columns: 1.6fr 1fr; gap: 14px; }
.chart { height: 260px; }
.hint { font-size: 11px; color: var(--med-text-light); font-weight: 400; margin-left: 8px; }

.entry-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
.entry-card {
  display: flex; align-items: center; gap: 12px; padding: 14px 16px;
  background: #fff; border: 1px solid var(--med-border); border-radius: 12px;
  text-decoration: none; color: inherit; transition: all .2s;
}
.entry-card:hover {
  border-color: var(--med-primary-soft);
  box-shadow: 0 6px 18px rgba(16, 41, 76, .08);
  transform: translateY(-1px);
}
.entry-icon {
  width: 38px; height: 38px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center; flex: 0 0 38px;
}
.entry-body { min-width: 0; }
.entry-title { font-size: 13.5px; font-weight: 600; color: var(--med-text); }
.entry-desc { font-size: 11.5px; color: var(--med-text-light); margin-top: 3px; }

@media (max-width: 1200px) {
  .chart-grid { grid-template-columns: 1fr; }
}
</style>
