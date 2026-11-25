<template>
  <CockpitShell
    title="超级管理驾驶舱"
    description="平台全量总览：运行、资产、任务、API 与待办一屏掌握。"
    :loading="loading"
    :updated-at="updatedAt"
    @refresh="load"
  >
    <div class="ck-stat-grid">
      <div class="ck-stat is-green">
        <div class="ck-stat-label">平台服务</div>
        <div class="ck-stat-value">{{ onlineCount }}/4</div>
        <div class="ck-stat-foot">核心服务在线</div>
      </div>
      <div class="ck-stat is-accent">
        <div class="ck-stat-label">资产总数</div>
        <div class="ck-stat-value">{{ assetTotal }}</div>
        <div class="ck-stat-foot">已登记数据资产</div>
      </div>
      <div class="ck-stat">
        <div class="ck-stat-label">任务实例</div>
        <div class="ck-stat-value">{{ taskTotal }}</div>
        <div class="ck-stat-foot">任务中心运行实例</div>
      </div>
      <div class="ck-stat is-gold">
        <div class="ck-stat-label">数据 API</div>
        <div class="ck-stat-value">{{ apiTotal }}</div>
        <div class="ck-stat-foot">已登记 已发布 {{ publishedApis }}</div>
      </div>
      <div class="ck-stat is-danger">
        <div class="ck-stat-label">待办事项</div>
        <div class="ck-stat-value">{{ todoTotal }}</div>
        <div class="ck-stat-foot">工单 {{ pendingTickets }} · 申请 {{ pendingRequests.length }} · 失败 {{ failedCount }}</div>
      </div>
    </div>

    <div class="ck-grid ck-grid-2">
      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">平台运行</h3>
          <span class="ck-card-sub">核心服务状态</span>
        </div>
        <div class="ck-service-list">
          <div v-for="service in services" :key="service.code" class="ck-service-row">
            <span class="ck-dot" :class="service.online ? 'is-online' : 'is-offline'"></span>
            <div class="ck-service-main">
              <b>{{ service.name }}</b>
              <span>{{ service.code }}</span>
            </div>
            <el-tag :type="service.online ? 'success' : 'danger'" size="small" effect="plain">
              {{ service.online ? '正常' : '异常' }}
            </el-tag>
          </div>
        </div>
      </section>

      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">任务运行</h3>
          <router-link to="/task-center" class="ck-link">任务中心 →</router-link>
        </div>
        <div class="ck-kv">
          <div class="ck-kv-row"><span>待执行</span><b>{{ taskCounts.PENDING }}</b></div>
          <div class="ck-kv-row"><span>执行中</span><b>{{ taskCounts.RUNNING }}</b></div>
          <div class="ck-kv-row"><span>成功</span><b>{{ taskCounts.SUCCESS }}</b></div>
          <div class="ck-kv-row"><span>失败</span><b>{{ taskCounts.FAILED }}</b></div>
        </div>
      </section>
    </div>

    <div class="ck-grid ck-grid-2">
      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">资产概览</h3>
          <router-link to="/catalog" class="ck-link">资产目录 →</router-link>
        </div>
        <div class="ck-kv" style="margin-bottom: 14px;">
          <div class="ck-kv-row"><span>资产总数</span><b>{{ assetTotal }}</b></div>
          <div class="ck-kv-row"><span>平均质量评分</span><b>{{ display(overview.avgQualityScore) }}</b></div>
          <div class="ck-kv-row"><span>活跃用户</span><b>{{ display(overview.activeUsers) }}</b></div>
          <div class="ck-kv-row"><span>本月新增</span><b>{{ monthAssetIncrement }}</b></div>
        </div>
        <h4 class="ck-card-sub" style="margin: 0 0 8px;">近 6 个月资产增量</h4>
        <MiniBars :items="assetBars" empty-text="资产趋势暂无数据" />
      </section>

      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">API 服务</h3>
          <router-link to="/data-service" class="ck-link">数据服务 →</router-link>
        </div>
        <div class="ck-kv" style="margin-bottom: 12px;">
          <div class="ck-kv-row"><span>API 总数</span><b>{{ apiTotal }}</b></div>
          <div class="ck-kv-row"><span>已发布</span><b>{{ publishedApis }}</b></div>
          <div class="ck-kv-row"><span>调度任务</span><b>{{ schedulerTotal }}</b></div>
          <div class="ck-kv-row"><span>集成任务</span><b>{{ integrationTotal }}</b></div>
        </div>
        <div v-if="apiRows.length" class="ck-list">
          <div v-for="api in take(apiRows, 4)" :key="api.id ?? api.apiCode" class="ck-list-item">
            <div>
              <b>{{ api.apiName || api.apiCode || api.path || '未命名 API' }}</b>
              <span>{{ api.method || 'GET' }} {{ api.path || '—' }}</span>
            </div>
            <el-tag size="small" :type="statusTagType(api.status)" effect="plain">{{ statusText(api.status) }}</el-tag>
          </div>
        </div>
        <div v-else class="ck-empty-block">
          <el-empty description="暂无 API 数据" :image-size="48" />
        </div>
      </section>
    </div>

    <div class="ck-grid ck-grid-main">
      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">最近失败任务</h3>
          <router-link to="/task-center" class="ck-link">查看全部 →</router-link>
        </div>
        <el-table v-if="failedTasks.length" :data="failedTasks" size="small" max-height="260">
          <el-table-column prop="id" label="实例" width="80" />
          <el-table-column label="任务" min-width="160">
            <template #default="{ row }">{{ row.taskName || row.definitionName || row.taskCode || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><el-tag type="danger" size="small">{{ statusText(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="时间" width="165">
            <template #default="{ row }">{{ formatDateTime(row.startTime || row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <div v-else class="ck-empty-block">
          <el-empty description="暂无失败任务" :image-size="48" />
        </div>
      </section>

      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">待办事项</h3>
        </div>
        <div class="ck-list">
          <router-link to="/operations" class="ck-list-item">
            <div><b>待处理工单</b><span>数据申请与质量问题</span></div>
            <em>{{ pendingTickets }}</em>
          </router-link>
          <router-link to="/security" class="ck-list-item">
            <div><b>待审批数据申请</b><span>安全访问审批</span></div>
            <em>{{ pendingRequests.length }}</em>
          </router-link>
          <router-link to="/task-center" class="ck-list-item">
            <div><b>失败任务</b><span>需要排查的调度与集成任务</span></div>
            <em>{{ failedCount }}</em>
          </router-link>
          <router-link to="/quality" class="ck-list-item">
            <div><b>质量异常</b><span>查看规则执行与异常样例</span></div>
            <em>进入</em>
          </router-link>
        </div>
      </section>
    </div>

    <section class="ck-card">
      <div class="ck-card-head">
        <h3 class="ck-card-title">全部驾驶舱入口</h3>
        <span class="ck-card-sub">超级管理员可访问全部角色视图</span>
      </div>
      <div class="ck-entry-grid">
        <router-link
          v-for="role in ALL_ROLES"
          :key="role"
          :to="ROLE_DEFINITIONS[role].cockpitPath"
          class="ck-entry-card"
        >
          <div class="ck-entry-icon">
            <el-icon :size="18"><component :is="resolvePortalIcon(ROLE_DEFINITIONS[role].icon)" /></el-icon>
          </div>
          <div>
            <div class="ck-entry-name">{{ ROLE_DEFINITIONS[role].cockpitTitle }}</div>
            <div class="ck-entry-desc">{{ ROLE_DEFINITIONS[role].name }}</div>
          </div>
        </router-link>
      </div>
    </section>
  </CockpitShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  catalogApi,
  dataServiceApi,
  integrationApi,
  operationsApi,
  platformApi,
  schedulerApi,
  securityApi
} from '@/api'
import { ALL_ROLES, ROLE_DEFINITIONS } from '@/config/roles'
import { resolvePortalIcon } from '@/config/portal'
import CockpitShell from './components/CockpitShell.vue'
import MiniBars from './components/MiniBars.vue'
import {
  arrayOf,
  countStatus,
  display,
  formatDateTime,
  nowText,
  recordsOf,
  settledValue,
  statusTagType,
  statusText,
  take,
  toNumber,
  totalOf,
  unwrap
} from './shared'

const loading = ref(false)
const updatedAt = ref('')
const services = ref([
  { code: 'SCHEDULER', name: '调度中心', online: false },
  { code: 'TASK_CENTER', name: '任务中心', online: false },
  { code: 'DATA_SERVICE', name: '数据服务', online: false },
  { code: 'INTEGRATION', name: '数据集成', online: false }
])

const overview = ref<any>({})
const assetTotal = ref(0)
const schedulerTotal = ref(0)
const integrationTotal = ref(0)
const taskTotal = ref(0)
const apiTotal = ref(0)
const apiRows = ref<any[]>([])
const instances = ref<any[]>([])
const pendingRequests = ref<any[]>([])
const failedTasks = ref<any[]>([])
const monthly = ref<{ months: string[]; assetCounts: number[] }>({ months: [], assetCounts: [] })

const onlineCount = computed(() => services.value.filter(service => service.online).length)
const pendingTickets = computed(() => toNumber(overview.value.pendingTickets, 0))
const failedCount = computed(() => countStatus(instances.value, 'FAILED'))
const todoTotal = computed(() => pendingTickets.value + pendingRequests.value.length + failedCount.value)
const publishedApis = computed(() => apiRows.value.filter(api => String(api.status).toUpperCase() === 'PUBLISHED').length)

const taskCounts = computed(() => ({
  PENDING: countStatus(instances.value, 'PENDING'),
  RUNNING: countStatus(instances.value, 'RUNNING'),
  SUCCESS: countStatus(instances.value, 'SUCCESS'),
  FAILED: countStatus(instances.value, 'FAILED')
}))

const assetBars = computed(() => {
  const months = monthly.value.months
  const counts = monthly.value.assetCounts
  const start = Math.max(0, months.length - 6)
  return months.slice(start).map((month, index) => ({
    label: month,
    value: toNumber(counts[start + index], 0)
  }))
})

const monthAssetIncrement = computed(() => assetBars.value.length ? assetBars.value[assetBars.value.length - 1].value : '—')

async function load() {
  loading.value = true
  try {
    const results = await Promise.allSettled([
      platformApi.getSchedulerStatus(),
      platformApi.getTaskCenterStatus(),
      platformApi.getDataServiceStatus(),
      platformApi.getIntegrationStatus(),
      operationsApi.getDashboard(),
      catalogApi.searchAssets({ page: 1, pageSize: 1 }),
      schedulerApi.listJobs({ page: 1, pageSize: 1 }),
      dataServiceApi.listApis({ page: 1, pageSize: 200 }),
      integrationApi.listJobs({ page: 1, pageSize: 1 }),
      platformApi.getTaskInstances({ page: 1, pageSize: 200 }),
      securityApi.getPendingRequests()
    ])

    services.value.forEach((service, index) => {
      service.online = results[index].status === 'fulfilled'
    })

    const dashboard = unwrap(settledValue(results[4])) || {}
    overview.value = dashboard

    const assetRes = settledValue(results[5])
    assetTotal.value = totalOf(assetRes) || toNumber(dashboard.totalAssets, 0)

    const schedulerRes = settledValue(results[6])
    schedulerTotal.value = totalOf(schedulerRes)

    const apiRes = settledValue(results[7])
    apiRows.value = recordsOf(apiRes)
    apiTotal.value = totalOf(apiRes) || apiRows.value.length

    const integrationRes = settledValue(results[8])
    integrationTotal.value = totalOf(integrationRes)

    const instanceRes = settledValue(results[9])
    instances.value = recordsOf(instanceRes)
    taskTotal.value = totalOf(instanceRes) || instances.value.length
    failedTasks.value = take(instances.value.filter(row => String(row.status).toUpperCase() === 'FAILED'), 5)

    pendingRequests.value = arrayOf(unwrap(settledValue(results[10])))

    const trend = dashboard.monthlyTrend || {}
    monthly.value = {
      months: arrayOf(trend.months),
      assetCounts: arrayOf(trend.assetCounts).map((value: any) => toNumber(value, 0))
    }
  } finally {
    loading.value = false
    updatedAt.value = nowText()
  }
}

onMounted(load)
</script>
