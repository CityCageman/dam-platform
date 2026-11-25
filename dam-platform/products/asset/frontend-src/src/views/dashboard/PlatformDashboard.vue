<template>
  <div class="platform-dashboard" v-loading="loading">
    <div class="platform-head">
      <div>
        <h3>数据中台运营</h3>
        <p>平台组件、任务执行与数据服务运行概览</p>
      </div>
      <el-button :icon="Refresh" size="small" @click="load">刷新</el-button>
    </div>

    <div class="platform-stats">
      <div class="platform-stat" v-for="s in scaleStats" :key="s.label">
        <span>{{ s.label }}</span>
        <b>{{ s.value }}</b>
        <em>{{ s.foot }}</em>
      </div>
    </div>

    <div class="platform-grid">
      <section class="platform-panel">
        <div class="panel-head">
          <h4>平台组件</h4>
          <span>核心服务运行状态</span>
        </div>
        <div class="service-list">
          <div v-for="s in services" :key="s.code" class="service-row">
            <span class="service-dot" :class="s.online ? 'is-online' : 'is-offline'"></span>
            <div class="service-name">
              <b>{{ s.name }}</b>
              <span>{{ s.code }}</span>
            </div>
            <el-tag :type="s.online ? 'success' : 'danger'" size="small" effect="plain">
              {{ s.online ? '正常' : '异常' }}
            </el-tag>
          </div>
        </div>
      </section>

      <section class="platform-panel">
        <div class="panel-head">
          <h4>任务运行</h4>
          <span>TaskCenter 实例状态</span>
        </div>
        <div class="status-list">
          <div v-for="s in taskStatus" :key="s.label" class="status-row">
            <span>{{ s.label }}</span>
            <b :class="s.className">{{ s.value }}</b>
          </div>
        </div>
      </section>
    </div>

    <section class="platform-panel recent-panel">
      <div class="panel-head">
        <h4>最近失败任务</h4>
        <router-link to="/task-center" class="panel-link">进入任务中心 →</router-link>
      </div>
      <el-table :data="failedTasks" size="small" empty-text="暂无失败任务">
        <el-table-column prop="id" label="实例" width="90" />
        <el-table-column label="任务" min-width="180">
          <template #default="{ row }">{{ row.taskName || row.taskCode || row.definitionName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }"><el-tag type="danger" size="small">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="失败原因" min-width="260" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { platformApi, schedulerApi, dataServiceApi, integrationApi } from '@/api'

const loading = ref(false)
const services = ref([
  { code: 'SCHEDULER', name: '调度中心', online: false },
  { code: 'TASK_CENTER', name: '任务中心', online: false },
  { code: 'DATA_SERVICE', name: '数据服务', online: false },
  { code: 'INTEGRATION', name: '数据集成', online: false }
])
const scaleStats = ref([
  { label: '调度任务', value: '-', foot: '已登记任务' },
  { label: '运行实例', value: '-', foot: 'TaskCenter 实例' },
  { label: '数据 API', value: '-', foot: '数据服务接口' },
  { label: '集成任务', value: '-', foot: '数据接入任务' }
])
const taskStatus = ref([
  { label: '待执行', value: '-', className: 'is-pending' },
  { label: '执行中', value: '-', className: 'is-running' },
  { label: '成功', value: '-', className: 'is-success' },
  { label: '失败', value: '-', className: 'is-failed' },
  { label: '已取消', value: '-', className: 'is-cancelled' }
])
const failedTasks = ref<any[]>([])

function dataOf(res: any) {
  return res?.data ?? res
}

function recordsOf(res: any): any[] {
  const data = dataOf(res)
  return data?.records || data?.list || (Array.isArray(data) ? data : [])
}

function totalOf(res: any): number {
  const data = dataOf(res)
  return Number(data?.total ?? recordsOf(res).length ?? 0)
}

async function load() {
  loading.value = true
  try {
    const [sc, tc, ds, it, instances] = await Promise.allSettled([
      platformApi.getSchedulerStatus(),
      platformApi.getTaskCenterStatus(),
      platformApi.getDataServiceStatus(),
      platformApi.getIntegrationStatus(),
      platformApi.getTaskInstances({ page: 1, pageSize: 200 }),
      schedulerApi.listJobs({ page: 1, pageSize: 1 }),
      dataServiceApi.listApis({ page: 1, pageSize: 1 }),
      integrationApi.listJobs({ page: 1, pageSize: 1 })
    ])
    services.value[0].online = sc.status === 'fulfilled' && (sc.value as any)?.code === 200
    services.value[1].online = tc.status === 'fulfilled' && (tc.value as any)?.code === 200
    services.value[2].online = ds.status === 'fulfilled' && (ds.value as any)?.code === 200
    services.value[3].online = it.status === 'fulfilled' && (it.value as any)?.code === 200

    const schedulerRes = await schedulerApi.listJobs({ page: 1, pageSize: 1 })
    const apiRes = await dataServiceApi.listApis({ page: 1, pageSize: 1 })
    const intRes = await integrationApi.listJobs({ page: 1, pageSize: 1 })
    const instancesRes = await platformApi.getTaskInstances({ page: 1, pageSize: 200 })
    const rows = recordsOf(instancesRes)
    scaleStats.value = [
      { label: '调度任务', value: String(totalOf(schedulerRes)), foot: '已登记任务' },
      { label: '运行实例', value: String(totalOf(instancesRes)), foot: 'TaskCenter 实例' },
      { label: '数据 API', value: String(totalOf(apiRes)), foot: '数据服务接口' },
      { label: '集成任务', value: String(totalOf(intRes)), foot: '数据接入任务' }
    ]
    const count = (s: string) => rows.filter((r: any) => String(r.status).toUpperCase() === s).length
    taskStatus.value = [
      { label: '待执行', value: String(count('PENDING')), className: 'is-pending' },
      { label: '执行中', value: String(count('RUNNING')), className: 'is-running' },
      { label: '成功', value: String(count('SUCCESS')), className: 'is-success' },
      { label: '失败', value: String(count('FAILED')), className: 'is-failed' },
      { label: '已取消', value: String(count('CANCELLED')), className: 'is-cancelled' }
    ]
    failedTasks.value = rows.filter((r: any) => String(r.status).toUpperCase() === 'FAILED').slice(0, 5)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.platform-head { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 16px; }
.platform-head h3 { margin: 0; font-size: 17px; color: var(--med-text); }
.platform-head p { margin: 5px 0 0; font-size: 12px; color: var(--med-text-light); }
.platform-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.platform-stat { padding: 15px 18px; border: 1px solid var(--med-border); border-radius: 12px; background: #fff; }
.platform-stat span { display: block; font-size: 12px; color: var(--med-text-light); }
.platform-stat b { display: block; margin-top: 8px; font-size: 24px; color: var(--med-text); }
.platform-stat em { display: block; margin-top: 4px; font-size: 11px; font-style: normal; color: var(--med-text-light); }
.platform-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-top: 14px; }
.platform-panel { padding: 16px 18px; border: 1px solid var(--med-border); border-radius: 12px; background: #fff; }
.panel-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.panel-head h4 { margin: 0; font-size: 14px; color: var(--med-text); }
.panel-head span { font-size: 11px; color: var(--med-text-light); }
.panel-link { font-size: 12px; color: var(--med-primary); text-decoration: none; }
.service-list { display: flex; flex-direction: column; gap: 8px; }
.service-row { display: flex; align-items: center; gap: 10px; padding: 9px 0; border-bottom: 1px solid var(--med-border); }
.service-row:last-child { border-bottom: 0; }
.service-dot { width: 8px; height: 8px; border-radius: 50%; flex: 0 0 8px; }
.service-dot.is-online { background: #22a06b; box-shadow: 0 0 0 3px rgba(34,160,107,.12); }
.service-dot.is-offline { background: #d9534f; box-shadow: 0 0 0 3px rgba(217,83,79,.12); }
.service-name { flex: 1; min-width: 0; }
.service-name b { display: block; font-size: 12.5px; color: var(--med-text); }
.service-name span { font-size: 10px; color: var(--med-text-light); }
.status-list { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.status-row { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; border-radius: 9px; background: #f8fafd; }
.status-row span { font-size: 12px; color: var(--med-text-light); }
.status-row b { font-size: 18px; }
.is-pending { color: #c58a1a; } .is-running { color: #2d6cdf; } .is-success { color: #22a06b; } .is-failed { color: #d9534f; } .is-cancelled { color: #8a94a6; }
.recent-panel { margin-top: 14px; }
@media (max-width: 900px) { .platform-stats { grid-template-columns: 1fr 1fr; } .platform-grid { grid-template-columns: 1fr; } }
</style>
