<template>
  <CockpitShell
    title="院领导驾驶舱"
    description="全院资产规模、服务调用、质量水位与风险概览。"
    :loading="loading"
    :updated-at="updatedAt"
    @refresh="load"
  >
    <div class="ck-stat-grid">
      <div class="ck-stat is-accent">
        <div class="ck-stat-label">全院资产规模</div>
        <div class="ck-stat-value">{{ assetTotal }}</div>
        <div class="ck-stat-foot">已登记数据资产</div>
      </div>
      <div class="ck-stat is-green">
        <div class="ck-stat-label">服务调用</div>
        <div class="ck-stat-value">{{ serviceCalls }}</div>
        <div class="ck-stat-foot">热门资产累计查询次数</div>
      </div>
      <div class="ck-stat is-gold">
        <div class="ck-stat-label">质量达标率</div>
        <div class="ck-stat-value">{{ qualityRate }}</div>
        <div class="ck-stat-foot">最近检测平均通过率</div>
      </div>
      <div class="ck-stat is-danger">
        <div class="ck-stat-label">风险事项</div>
        <div class="ck-stat-value">{{ riskTotal }}</div>
        <div class="ck-stat-foot">待办工单 {{ pendingTickets }} · 失败任务 {{ failedTotal }}</div>
      </div>
    </div>

    <div class="ck-grid ck-grid-2">
      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">全院资产增长趋势</h3>
          <span class="ck-card-sub">近 12 个月</span>
        </div>
        <MiniBars :items="assetBars" empty-text="暂无资产趋势数据" />
      </section>
      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">运营工单趋势</h3>
          <span class="ck-card-sub">近 12 个月</span>
        </div>
        <MiniBars :items="ticketBars" empty-text="暂无工单趋势数据" />
      </section>
    </div>

    <div class="ck-grid ck-grid-main">
      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">科室资产排名</h3>
          <span class="ck-card-sub">按资产科室字段抽样统计</span>
        </div>
        <MiniBars
          v-if="departmentRanking.length"
          :items="departmentRanking"
          label-width="110px"
          empty-text="暂无科室维度数据"
        />
        <div v-else class="ck-empty-block">
          <el-empty description="暂无科室维度数据" :image-size="56" />
        </div>
      </section>

      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">风险与待办</h3>
          <router-link to="/operations" class="ck-link">工单中心 →</router-link>
        </div>
        <div class="ck-kv">
          <div class="ck-kv-row"><span>待处理工单</span><b>{{ pendingTickets }}</b></div>
          <div class="ck-kv-row"><span>待审批申请</span><b>{{ pendingRequests.length }}</b></div>
          <div class="ck-kv-row"><span>失败任务</span><b>{{ failedTotal }}</b></div>
          <div class="ck-kv-row"><span>质量规则</span><b>{{ display(quality.totalRules) }}</b></div>
        </div>
        <div class="ck-list" style="margin-top: 12px;">
          <router-link to="/quality" class="ck-list-item">
            <div><b>质量异常核查</b><span>最近检测通过率 {{ qualityRate }}，最近检测 {{ formatDateTime(quality.lastCheckTime) }}</span></div>
            <em>进入</em>
          </router-link>
          <router-link to="/security" class="ck-list-item">
            <div><b>数据访问审批</b><span>待审批数据申请 {{ pendingRequests.length }} 条</span></div>
            <em>进入</em>
          </router-link>
        </div>
      </section>
    </div>

    <section class="ck-card">
      <div class="ck-card-head">
        <h3 class="ck-card-title">最近失败任务</h3>
        <router-link to="/task-center" class="ck-link">查看全部 →</router-link>
      </div>
      <el-table v-if="failedTasks.length" :data="failedTasks" size="small" max-height="260">
        <el-table-column prop="id" label="实例" width="80" />
        <el-table-column label="任务" min-width="180">
          <template #default="{ row }">{{ row.taskName || row.definitionName || row.taskCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag type="danger" size="small">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="165">
          <template #default="{ row }">{{ formatDateTime(row.startTime || row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <div v-else class="ck-empty-block">
        <el-empty description="暂无失败任务" :image-size="48" />
      </div>
    </section>
  </CockpitShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { catalogApi, operationsApi, platformApi, qualityApi, securityApi } from '@/api'
import CockpitShell from './components/CockpitShell.vue'
import MiniBars from './components/MiniBars.vue'
import {
  arrayOf,
  display,
  formatDateTime,
  nowText,
  percentText,
  recordsOf,
  settledValue,
  statusText,
  sumBy,
  take,
  toNumber,
  totalOf,
  unwrap
} from './shared'

const loading = ref(false)
const updatedAt = ref('')
const overview = ref<any>({})
const monthly = ref<{ months: string[]; assetCounts: number[]; ticketCounts: number[] }>({
  months: [],
  assetCounts: [],
  ticketCounts: []
})
const quality = ref<any>({})
const assetRows = ref<any[]>([])
const failedTasks = ref<any[]>([])
const failedTotal = ref(0)
const pendingRequests = ref<any[]>([])

const assetTotal = computed(() => toNumber(overview.value.totalAssets, assetRows.value.length))
const pendingTickets = computed(() => toNumber(overview.value.pendingTickets, 0))
const qualityRate = computed(() => percentText(quality.value.avgScore ?? overview.value.avgQualityScore))
const serviceCalls = computed(() => sumBy(arrayOf(overview.value.hotAssets), row => row.queryCount))
const riskTotal = computed(() => pendingTickets.value + failedTotal.value + pendingRequests.value.length)

const assetBars = computed(() => barsOf(monthly.value.months, monthly.value.assetCounts))
const ticketBars = computed(() => barsOf(monthly.value.months, monthly.value.ticketCounts))

const departmentRanking = computed(() => {
  const counts = new Map<string, number>()
  for (const row of assetRows.value) {
    const department = String(row.department || '').trim()
    if (!department) continue
    counts.set(department, (counts.get(department) || 0) + 1)
  }
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 6)
    .map(([label, value]) => ({ label, value }))
})

function barsOf(months: string[], values: number[]) {
  const start = Math.max(0, months.length - 12)
  return months.slice(start).map((month, index) => ({
    label: month,
    value: toNumber(values[start + index], 0)
  }))
}

async function load() {
  loading.value = true
  try {
    const results = await Promise.allSettled([
      operationsApi.getDashboard(),
      qualityApi.getOverview(),
      catalogApi.searchAssets({ page: 1, pageSize: 200 }),
      platformApi.getTaskInstances({ status: 'FAILED', page: 1, pageSize: 5 }),
      securityApi.getPendingRequests()
    ])

    const dashboard = unwrap(settledValue(results[0])) || {}
    overview.value = dashboard

    quality.value = unwrap(settledValue(results[1])) || {}

    const assetRes = settledValue(results[2])
    assetRows.value = recordsOf(assetRes)

    const failedRes = settledValue(results[3])
    failedTasks.value = take(recordsOf(failedRes), 5)
    failedTotal.value = totalOf(failedRes) || failedTasks.value.length

    pendingRequests.value = arrayOf(unwrap(settledValue(results[4])))

    const trend = dashboard.monthlyTrend || {}
    monthly.value = {
      months: arrayOf(trend.months),
      assetCounts: arrayOf(trend.assetCounts).map((value: any) => toNumber(value, 0)),
      ticketCounts: arrayOf(trend.ticketCounts).map((value: any) => toNumber(value, 0))
    }
  } finally {
    loading.value = false
    updatedAt.value = nowText()
  }
}

onMounted(load)
</script>
