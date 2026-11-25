<template>
  <CockpitShell
    title="平台驾驶舱"
    description="服务状态、失败任务、集成任务与 API 运行情况。"
    :loading="loading"
    :updated-at="updatedAt"
    @refresh="load"
  >
    <div class="ck-stat-grid">
      <div class="ck-stat is-green">
        <div class="ck-stat-label">服务状态</div>
        <div class="ck-stat-value">{{ onlineCount }}/4</div>
        <div class="ck-stat-foot">调度 / 任务 / 服务 / 集成</div>
      </div>
      <div class="ck-stat is-danger">
        <div class="ck-stat-label">失败任务</div>
        <div class="ck-stat-value">{{ failedRows.length }}</div>
        <div class="ck-stat-foot">任务中心失败实例</div>
      </div>
      <div class="ck-stat is-gold">
        <div class="ck-stat-label">集成任务</div>
        <div class="ck-stat-value">{{ integrationTotal }}</div>
        <div class="ck-stat-foot">运行中 {{ integrationRunning }} · 失败 {{ integrationFailed }}</div>
      </div>
      <div class="ck-stat is-accent">
        <div class="ck-stat-label">数据 API</div>
        <div class="ck-stat-value">{{ apiTotal }}</div>
        <div class="ck-stat-foot">已发布 {{ apiPublished }}</div>
      </div>
      <div class="ck-stat">
        <div class="ck-stat-label">运行实例</div>
        <div class="ck-stat-value">{{ instanceTotal }}</div>
        <div class="ck-stat-foot">任务中心实例总数</div>
      </div>
    </div>

    <div class="ck-grid ck-grid-main">
      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">服务状态</h3>
          <span class="ck-card-sub">平台核心服务健康检查</span>
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
          <h3 class="ck-card-title">任务状态</h3>
          <router-link to="/task-center" class="ck-link">任务中心 →</router-link>
        </div>
        <div class="ck-kv">
          <div class="ck-kv-row"><span>待执行</span><b>{{ taskCounts.PENDING }}</b></div>
          <div class="ck-kv-row"><span>执行中</span><b>{{ taskCounts.RUNNING }}</b></div>
          <div class="ck-kv-row"><span>成功</span><b>{{ taskCounts.SUCCESS }}</b></div>
          <div class="ck-kv-row"><span>失败</span><b>{{ taskCounts.FAILED }}</b></div>
        </div>
        <div class="ck-list" style="margin-top: 12px;">
          <router-link to="/scheduler" class="ck-list-item">
            <div><b>调度中心</b><span>调度任务与运行记录</span></div>
            <em>{{ schedulerTotal }}</em>
          </router-link>
          <router-link to="/integration" class="ck-list-item">
            <div><b>数据集成</b><span>集成任务与最近运行</span></div>
            <em>{{ integrationTotal }}</em>
          </router-link>
        </div>
      </section>
    </div>

    <div class="ck-grid ck-grid-2">
      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">集成任务</h3>
          <router-link to="/integration" class="ck-link">查看全部 →</router-link>
        </div>
        <el-table v-if="integrationRows.length" :data="take(integrationRows, 5)" size="small" max-height="280">
          <el-table-column label="任务" min-width="170">
            <template #default="{ row }">{{ row.jobName || row.jobCode || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)" effect="plain">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最近运行" width="165">
            <template #default="{ row }">{{ formatDateTime(row.lastRunAt || row.updatedAt) }}</template>
          </el-table-column>
        </el-table>
        <div v-else class="ck-empty-block">
          <el-empty description="暂无集成任务" :image-size="48" />
        </div>
      </section>

      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">API 状态</h3>
          <router-link to="/data-service" class="ck-link">查看全部 →</router-link>
        </div>
        <el-table v-if="apiRows.length" :data="take(apiRows, 5)" size="small" max-height="280">
          <el-table-column label="API" min-width="150">
            <template #default="{ row }">{{ row.apiName || row.apiCode || '-' }}</template>
          </el-table-column>
          <el-table-column label="路径" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ display(row.path) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)" effect="plain">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div v-else class="ck-empty-block">
          <el-empty description="暂无 API 数据" :image-size="48" />
        </div>
      </section>
    </div>

    <section class="ck-card">
      <div class="ck-card-head">
        <h3 class="ck-card-title">最近失败</h3>
        <router-link to="/task-center" class="ck-link">进入任务中心 →</router-link>
      </div>
      <el-table v-if="failedRows.length" :data="take(failedRows, 8)" size="small" max-height="320">
        <el-table-column prop="id" label="实例" width="80" />
        <el-table-column label="任务" min-width="180">
          <template #default="{ row }">{{ row.taskName || row.definitionName || row.taskCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag type="danger" size="small">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ display(row.errorMessage) }}</template>
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
import { dataServiceApi, integrationApi, platformApi, schedulerApi } from '@/api'
import CockpitShell from './components/CockpitShell.vue'
import {
  countStatus,
  display,
  formatDateTime,
  nowText,
  recordsOf,
  settledValue,
  statusTagType,
  statusText,
  take,
  totalOf
} from './shared'

const loading = ref(false)
const updatedAt = ref('')
const services = ref([
  { code: 'SCHEDULER', name: '调度中心', online: false },
  { code: 'TASK_CENTER', name: '任务中心', online: false },
  { code: 'DATA_SERVICE', name: '数据服务', online: false },
  { code: 'INTEGRATION', name: '数据集成', online: false }
])
const instances = ref<any[]>([])
const integrationRows = ref<any[]>([])
const apiRows = ref<any[]>([])
const schedulerTotal = ref(0)
const integrationTotal = ref(0)
const apiTotal = ref(0)
const instanceTotal = ref(0)

const onlineCount = computed(() => services.value.filter(service => service.online).length)
const failedRows = computed(() => instances.value.filter(row => String(row.status).toUpperCase() === 'FAILED'))
const integrationRunning = computed(() => countStatus(integrationRows.value, 'RUNNING'))
const integrationFailed = computed(() => countStatus(integrationRows.value, 'FAILED'))
const apiPublished = computed(() => apiRows.value.filter(api => String(api.status).toUpperCase() === 'PUBLISHED').length)
const taskCounts = computed(() => ({
  PENDING: countStatus(instances.value, 'PENDING'),
  RUNNING: countStatus(instances.value, 'RUNNING'),
  SUCCESS: countStatus(instances.value, 'SUCCESS'),
  FAILED: countStatus(instances.value, 'FAILED')
}))

async function load() {
  loading.value = true
  try {
    const results = await Promise.allSettled([
      platformApi.getSchedulerStatus(),
      platformApi.getTaskCenterStatus(),
      platformApi.getDataServiceStatus(),
      platformApi.getIntegrationStatus(),
      platformApi.getTaskInstances({ page: 1, pageSize: 200 }),
      schedulerApi.listJobs({ page: 1, pageSize: 1 }),
      integrationApi.listJobs({ page: 1, pageSize: 100 }),
      dataServiceApi.listApis({ page: 1, pageSize: 100 })
    ])

    services.value.forEach((service, index) => {
      service.online = results[index].status === 'fulfilled'
    })

    const instanceRes = settledValue(results[4])
    instances.value = recordsOf(instanceRes)
    instanceTotal.value = totalOf(instanceRes) || instances.value.length

    schedulerTotal.value = totalOf(settledValue(results[5]))

    const integrationRes = settledValue(results[6])
    integrationRows.value = recordsOf(integrationRes)
    integrationTotal.value = totalOf(integrationRes) || integrationRows.value.length

    const apiRes = settledValue(results[7])
    apiRows.value = recordsOf(apiRes)
    apiTotal.value = totalOf(apiRes) || apiRows.value.length
  } finally {
    loading.value = false
    updatedAt.value = nowText()
  }
}

onMounted(load)
</script>
