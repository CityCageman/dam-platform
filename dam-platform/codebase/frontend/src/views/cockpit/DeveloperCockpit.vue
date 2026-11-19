<template>
  <CockpitShell
    title="开发工作台"
    description="我的开发任务、待发布、调度失败与最近执行。"
    :loading="loading"
    :updated-at="updatedAt"
    @refresh="load"
  >
    <div class="ck-stat-grid">
      <div class="ck-stat is-accent">
        <div class="ck-stat-label">我的开发任务</div>
        <div class="ck-stat-value">{{ devTasks.length }}</div>
        <div class="ck-stat-foot">{{ mineMatched ? '按创建人筛选' : '未返回创建人，展示全部任务' }}</div>
      </div>
      <div class="ck-stat is-gold">
        <div class="ck-stat-label">待发布</div>
        <div class="ck-stat-value">{{ draftTasks.length }}</div>
        <div class="ck-stat-foot">草稿状态的开发任务</div>
      </div>
      <div class="ck-stat is-danger">
        <div class="ck-stat-label">调度失败</div>
        <div class="ck-stat-value">{{ failedInstances.length }}</div>
        <div class="ck-stat-foot">任务中心失败实例</div>
      </div>
      <div class="ck-stat is-green">
        <div class="ck-stat-label">最近执行</div>
        <div class="ck-stat-value">{{ instanceTotal }}</div>
        <div class="ck-stat-foot">任务中心运行实例</div>
      </div>
    </div>

    <div class="ck-grid ck-grid-main">
      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">我的开发任务</h3>
          <router-link to="/develop" class="ck-link">开发工作台 →</router-link>
        </div>
        <el-table v-if="devTasks.length" :data="take(devTasks, 8)" size="small" max-height="320">
          <el-table-column label="任务名称" min-width="170">
            <template #default="{ row }">{{ row.name || row.taskName || row.code || '-' }}</template>
          </el-table-column>
          <el-table-column label="编码" width="130">
            <template #default="{ row }">{{ display(row.code) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ display(row.type) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)" effect="plain">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="165">
            <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <div v-else class="ck-empty-block">
          <el-empty description="暂无开发任务" :image-size="48" />
        </div>
      </section>

      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">待发布</h3>
          <span class="ck-card-sub">草稿任务发布后才会进入调度</span>
        </div>
        <div v-if="draftTasks.length" class="ck-list">
          <div v-for="task in take(draftTasks, 5)" :key="task.id ?? task.code" class="ck-list-item">
            <div>
              <b>{{ task.name || task.taskName || task.code || '未命名任务' }}</b>
              <span>{{ task.code || '—' }} · {{ formatDateTime(task.updatedAt || task.createdAt) }}</span>
            </div>
            <el-tag size="small" type="warning" effect="plain">待发布</el-tag>
          </div>
        </div>
        <div v-else class="ck-empty-block">
          <el-empty description="暂无待发布任务" :image-size="48" />
        </div>
        <div class="ck-list" style="margin-top: 12px;">
          <router-link to="/develop" class="ck-list-item">
            <div><b>进入数据开发</b><span>新建 / 编辑 / 发布开发任务</span></div>
            <em>进入</em>
          </router-link>
        </div>
      </section>
    </div>

    <section class="ck-card">
      <div class="ck-card-head">
        <h3 class="ck-card-title">调度失败与最近执行</h3>
        <router-link to="/task-center" class="ck-link">任务中心 →</router-link>
      </div>
      <el-table v-if="recentInstances.length" :data="recentInstances" size="small" max-height="320">
        <el-table-column prop="id" label="实例" width="80" />
        <el-table-column label="任务" min-width="180">
          <template #default="{ row }">{{ row.taskName || row.definitionName || row.taskCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)" effect="plain">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ display(row.errorMessage) }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="165">
          <template #default="{ row }">{{ formatDateTime(row.startTime || row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <div v-else class="ck-empty-block">
        <el-empty description="暂无最近执行记录" :image-size="48" />
      </div>
    </section>

    <section class="ck-card">
      <div class="ck-card-head">
        <h3 class="ck-card-title">快捷入口</h3>
        <span class="ck-card-sub">开发 / 调度 / 血缘</span>
      </div>
      <div class="ck-entry-grid">
        <router-link to="/develop" class="ck-entry-card">
          <div class="ck-entry-icon"><el-icon :size="18"><Cpu /></el-icon></div>
          <div><div class="ck-entry-name">数据开发</div><div class="ck-entry-desc">任务编辑、发布与版本回溯</div></div>
        </router-link>
        <router-link to="/scheduler" class="ck-entry-card">
          <div class="ck-entry-icon"><el-icon :size="18"><Timer /></el-icon></div>
          <div><div class="ck-entry-name">调度中心</div><div class="ck-entry-desc">调度任务与运行状态</div></div>
        </router-link>
        <router-link to="/lineage" class="ck-entry-card">
          <div class="ck-entry-icon"><el-icon :size="18"><Share /></el-icon></div>
          <div><div class="ck-entry-name">数据血缘</div><div class="ck-entry-desc">表级 / 字段级加工链路</div></div>
        </router-link>
        <router-link to="/task-center" class="ck-entry-card">
          <div class="ck-entry-icon"><el-icon :size="18"><Tickets /></el-icon></div>
          <div><div class="ck-entry-name">任务中心</div><div class="ck-entry-desc">实例日志与失败排查</div></div>
        </router-link>
      </div>
    </section>
  </CockpitShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Cpu, Share, Tickets, Timer } from '@element-plus/icons-vue'
import { devApi, platformApi } from '@/api'
import { useUserStore } from '@/store/user'
import CockpitShell from './components/CockpitShell.vue'
import {
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

const userStore = useUserStore()

const loading = ref(false)
const updatedAt = ref('')
const allDevTasks = ref<any[]>([])
const instances = ref<any[]>([])
const instanceTotal = ref(0)

const username = computed(() => String(userStore.userInfo?.username || '').trim())
const mineMatched = computed(() => allDevTasks.value.some(task => String(task.createdBy || '').trim() === username.value))
const devTasks = computed(() => {
  if (!username.value) return allDevTasks.value
  const mine = allDevTasks.value.filter(task => String(task.createdBy || '').trim() === username.value)
  return mine.length ? mine : allDevTasks.value
})
const draftTasks = computed(() => devTasks.value.filter(task => String(task.status).toUpperCase() === 'DRAFT'))
const failedInstances = computed(() => instances.value.filter(row => String(row.status).toUpperCase() === 'FAILED'))
const recentInstances = computed(() => take(instances.value, 8))

async function load() {
  loading.value = true
  try {
    const results = await Promise.allSettled([
      devApi.getTasks(),
      platformApi.getTaskInstances({ page: 1, pageSize: 200 })
    ])

    allDevTasks.value = recordsOf(settledValue(results[0]))

    const instanceRes = settledValue(results[1])
    instances.value = recordsOf(instanceRes)
    instanceTotal.value = totalOf(instanceRes) || instances.value.length
  } finally {
    loading.value = false
    updatedAt.value = nowText()
  }
}

onMounted(load)
</script>
