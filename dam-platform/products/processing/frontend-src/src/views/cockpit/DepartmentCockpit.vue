<template>
  <CockpitShell
    title="科室驾驶舱"
    :description="departmentName ? `${departmentName} 的资产、质量异常、数据申请与指标待办。` : '当前账号未返回科室信息，以下为可用的全院概览。'"
    :loading="loading"
    :updated-at="updatedAt"
    @refresh="load"
  >
    <template #meta>
      <div style="margin-top: 8px;">
        <span class="ck-dept-chip">
          <el-icon><OfficeBuilding /></el-icon>
          科室：{{ departmentName || '未设置' }}
        </span>
      </div>
    </template>

    <div class="ck-stat-grid">
      <div class="ck-stat is-accent">
        <div class="ck-stat-label">本科室资产</div>
        <div class="ck-stat-value">{{ myAssets.length }}</div>
        <div class="ck-stat-foot">按资产 department 字段筛选</div>
      </div>
      <div class="ck-stat is-danger">
        <div class="ck-stat-label">质量异常</div>
        <div class="ck-stat-value">{{ qualityAnomalies }}</div>
        <div class="ck-stat-foot">近 30 天通过率低于 100% 的检测</div>
      </div>
      <div class="ck-stat is-gold">
        <div class="ck-stat-label">数据申请</div>
        <div class="ck-stat-value">{{ applicationTotal }}</div>
        <div class="ck-stat-foot">待审批 {{ pendingApplications.length }}</div>
      </div>
      <div class="ck-stat is-green">
        <div class="ck-stat-label">指标数量</div>
        <div class="ck-stat-value">{{ metricTotal }}</div>
        <div class="ck-stat-foot">已登记业务指标</div>
      </div>
      <div class="ck-stat">
        <div class="ck-stat-label">待办事项</div>
        <div class="ck-stat-value">{{ todoTotal }}</div>
        <div class="ck-stat-foot">工单 {{ pendingTickets }} · 失败任务 {{ failedTotal }}</div>
      </div>
    </div>

    <div class="ck-grid ck-grid-main">
      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">本科室资产</h3>
          <router-link to="/catalog" class="ck-link">资产目录 →</router-link>
        </div>
        <el-table v-if="myAssets.length" :data="take(myAssets, 6)" size="small" max-height="300">
          <el-table-column label="资产名称" min-width="170">
            <template #default="{ row }">{{ row.assetName || row.name || row.tableName || '-' }}</template>
          </el-table-column>
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ display(row.assetType) }}</template>
          </el-table-column>
          <el-table-column label="来源系统" min-width="110">
            <template #default="{ row }">{{ display(row.sourceSystem) }}</template>
          </el-table-column>
          <el-table-column label="更新时间" width="165">
            <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <div v-else class="ck-empty-block">
          <el-empty
            :description="departmentName ? '暂无本科室资产数据（后端暂未提供科室维度过滤）' : '当前账号未返回科室信息'"
            :image-size="52"
          />
        </div>
      </section>

      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">质量与待办</h3>
          <router-link to="/quality" class="ck-link">数据质量 →</router-link>
        </div>
        <div class="ck-kv">
          <div class="ck-kv-row"><span>质量达标率</span><b>{{ qualityRate }}</b></div>
          <div class="ck-kv-row"><span>质量规则</span><b>{{ display(quality.totalRules) }}</b></div>
          <div class="ck-kv-row"><span>待处理工单</span><b>{{ pendingTickets }}</b></div>
          <div class="ck-kv-row"><span>失败任务</span><b>{{ failedTotal }}</b></div>
        </div>
        <div class="ck-list" style="margin-top: 12px;">
          <router-link to="/metrics" class="ck-list-item">
            <div><b>本科室指标</b><span>查看指标口径与指标值</span></div>
            <em>{{ metricTotal }}</em>
          </router-link>
          <router-link to="/operations" class="ck-list-item">
            <div><b>数据申请与工单</b><span>处理数据使用申请与质量问题</span></div>
            <em>{{ pendingApplications.length }}</em>
          </router-link>
          <router-link to="/task-center" class="ck-list-item">
            <div><b>任务执行</b><span>查看失败任务与最近执行</span></div>
            <em>进入</em>
          </router-link>
        </div>
      </section>
    </div>

    <section class="ck-card">
      <div class="ck-card-head">
        <h3 class="ck-card-title">数据申请</h3>
        <router-link to="/security" class="ck-link">安全审批 →</router-link>
      </div>
      <el-table v-if="applications.length" :data="take(applications, 6)" size="small" max-height="280">
        <el-table-column label="申请单号" min-width="150">
          <template #default="{ row }">{{ row.applicationNo || row.id || '-' }}</template>
        </el-table-column>
        <el-table-column label="资产" width="90">
          <template #default="{ row }">{{ display(row.assetId) }}</template>
        </el-table-column>
        <el-table-column label="访问类型" width="110">
          <template #default="{ row }">{{ display(row.accessType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="applicationTagType(row.status)" effect="plain">
              {{ applicationStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="165">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <div v-else class="ck-empty-block">
        <el-empty description="暂无数据申请记录" :image-size="48" />
      </div>
    </section>
  </CockpitShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { OfficeBuilding } from '@element-plus/icons-vue'
import { catalogApi, metricApi, operationsApi, platformApi, qualityApi, securityApi } from '@/api'
import { useUserStore } from '@/store/user'
import CockpitShell from './components/CockpitShell.vue'
import {
  arrayOf,
  display,
  formatDateTime,
  nowText,
  percentText,
  recordsOf,
  settledValue,
  take,
  toNumber,
  totalOf,
  unwrap
} from './shared'

const userStore = useUserStore()

const loading = ref(false)
const updatedAt = ref('')
const assetRows = ref<any[]>([])
const applications = ref<any[]>([])
const quality = ref<any>({})
const qualityScores = ref<number[]>([])
const metricTotal = ref(0)
const pendingTickets = ref(0)
const failedTotal = ref(0)

const departmentName = computed(() => userStore.department || userStore.userInfo?.department || '')
const myAssets = computed(() => {
  const target = departmentName.value.trim()
  if (!target) return []
  return assetRows.value.filter(row => String(row.department || '').trim() === target)
})

const qualityRate = computed(() => percentText(quality.value.avgScore))
const qualityAnomalies = computed(() => qualityScores.value.filter(score => score < 100).length)
const applicationTotal = computed(() => applications.value.length)
const pendingApplications = computed(() =>
  applications.value.filter(row => {
    const status = String(row.status ?? '').toUpperCase()
    return status === '0' || status === 'PENDING' || status === '待审批'
  })
)
const todoTotal = computed(() => pendingTickets.value + failedTotal.value)

function applicationStatusText(status: any): string {
  const value = String(status ?? '').toUpperCase()
  if (value === '0' || value === 'PENDING') return '待审批'
  if (value === '1' || value === 'APPROVED') return '已通过'
  if (value === '2' || value === 'REJECTED') return '已驳回'
  return status === null || status === undefined || status === '' ? '未知' : String(status)
}

function applicationTagType(status: any): 'success' | 'danger' | 'warning' | 'info' {
  const value = String(status ?? '').toUpperCase()
  if (value === '1' || value === 'APPROVED') return 'success'
  if (value === '2' || value === 'REJECTED') return 'danger'
  if (value === '0' || value === 'PENDING') return 'warning'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    const results = await Promise.allSettled([
      catalogApi.searchAssets({ page: 1, pageSize: 200 }),
      qualityApi.getOverview(),
      qualityApi.getTrend(undefined, 30),
      securityApi.searchAccessRequests({ page: 1, pageSize: 200 }),
      metricApi.list({ page: 1, pageSize: 1 }),
      operationsApi.getDashboard(),
      platformApi.getTaskInstances({ status: 'FAILED', page: 1, pageSize: 1 })
    ])

    assetRows.value = recordsOf(settledValue(results[0]))
    quality.value = unwrap(settledValue(results[1])) || {}

    const trend = unwrap(settledValue(results[2])) || {}
    qualityScores.value = arrayOf(trend.scores).map((value: any) => toNumber(value, 0))

    applications.value = recordsOf(settledValue(results[3]))

    metricTotal.value = totalOf(settledValue(results[4]))

    const dashboard = unwrap(settledValue(results[5])) || {}
    pendingTickets.value = toNumber(dashboard.pendingTickets, 0)

    const failedRes = settledValue(results[6])
    failedTotal.value = totalOf(failedRes) || recordsOf(failedRes).length
  } finally {
    loading.value = false
    updatedAt.value = nowText()
  }
}

onMounted(load)
</script>
