<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">任务中心</h2>
        <p class="page-desc">集中查看任务定义与运行实例，手动触发任务并排查实例执行日志。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="definitionsLoading || instancesLoading" @click="refreshAll">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="loadError"
      class="page-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="loadError"
    />

    <div class="panel">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="任务定义" name="definitions">
          <div class="toolbar">
            <el-input
              v-model="definitionKeyword"
              placeholder="搜索任务编码/名称/类型"
              clearable
              style="width: 280px;"
            />
            <el-select v-model="definitionStatus" placeholder="全部状态" clearable style="width: 140px;">
              <el-option
                v-for="item in definitionStatusOptions"
                :key="item"
                :label="statusLabel(item)"
                :value="item"
              />
            </el-select>
            <span class="toolbar-total">共 {{ filteredDefinitions.length }} 个定义</span>
          </div>

          <el-table :data="filteredDefinitions" stripe v-loading="definitionsLoading">
            <el-table-column label="任务编码" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ definitionCode(row) }}</template>
            </el-table-column>
            <el-table-column label="任务名称" min-width="190" show-overflow-tooltip>
              <template #default="{ row }">{{ definitionName(row) }}</template>
            </el-table-column>
            <el-table-column label="类型" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ definitionType(row) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="调度表达式" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ definitionCron(row) }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button
                  text
                  type="primary"
                  :loading="triggeringId === row.id"
                  @click="triggerDefinition(row)"
                >
                  触发
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!definitionsLoading && !filteredDefinitions.length"
            description="暂无任务定义"
            :image-size="60"
          />
        </el-tab-pane>

        <el-tab-pane label="运行实例" name="instances">
          <div class="toolbar">
            <el-input
              v-model="instanceKeyword"
              placeholder="搜索实例 ID/任务名称"
              clearable
              style="width: 280px;"
            />
            <el-select v-model="instanceStatus" placeholder="全部状态" clearable style="width: 140px;">
              <el-option
                v-for="item in instanceStatusOptions"
                :key="item"
                :label="statusLabel(item)"
                :value="item"
              />
            </el-select>
            <el-button :loading="instancesLoading" @click="loadInstances">查询</el-button>
            <span class="toolbar-total">共 {{ filteredInstances.length }} 个实例</span>
          </div>

          <el-table :data="filteredInstances" stripe v-loading="instancesLoading">
            <el-table-column label="实例 ID" width="100">
              <template #default="{ row }">#{{ row.id ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="所属任务" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ instanceTask(row) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="触发方式" width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ instanceTriggerType(row) }}</template>
            </el-table-column>
            <el-table-column label="开始时间" width="170">
              <template #default="{ row }">{{ formatTime(row.startTime || row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="结束时间" width="170">
              <template #default="{ row }">{{ formatTime(row.endTime) }}</template>
            </el-table-column>
            <el-table-column label="耗时" width="110">
              <template #default="{ row }">{{ instanceDuration(row) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" @click="openLogs(row)">查看日志</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!instancesLoading && !filteredInstances.length"
            description="暂无运行实例"
            :image-size="60"
          />
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-drawer v-model="logDrawer" :title="logTitle" size="58%" append-to-body>
      <div v-loading="logsLoading" class="log-wrap">
        <el-alert
          v-if="logsError"
          class="log-alert"
          type="warning"
          :closable="false"
          show-icon
          :title="logsError"
        />
        <pre v-if="logText" class="log-pre">{{ logText }}</pre>
        <el-empty
          v-else-if="!logsLoading && !logsError"
          description="该实例暂无日志"
          :image-size="60"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { taskCenterApi } from '@/api'
import type { TaskCenterDefinition, TaskCenterInstance, TaskCenterLog } from '@/api'

type TagType = 'success' | 'warning' | 'danger' | 'info'

const activeTab = ref<'definitions' | 'instances'>('definitions')

const definitions = ref<TaskCenterDefinition[]>([])
const instances = ref<TaskCenterInstance[]>([])
const definitionsLoading = ref(false)
const instancesLoading = ref(false)
const definitionsError = ref('')
const instancesError = ref('')
const triggeringId = ref<number | null>(null)

const definitionKeyword = ref('')
const definitionStatus = ref('')
const instanceKeyword = ref('')
const instanceStatus = ref('')

const logDrawer = ref(false)
const logInstance = ref<TaskCenterInstance | null>(null)
const logs = ref<TaskCenterLog[]>([])
const logsLoading = ref(false)
const logsError = ref('')
const logTextOverride = ref('')

/** 统一把数组 / 分页包装 / records 包装转换为列表 */
function toList<T>(data: unknown): T[] {
  if (Array.isArray(data)) return data as T[]
  if (data && typeof data === 'object') {
    const obj = data as Record<string, unknown>
    for (const key of ['records', 'rows', 'list', 'items', 'content']) {
      const value = obj[key]
      if (Array.isArray(value)) return value as T[]
    }
  }
  return []
}

/** 读取对象中的第一个非空字段，兼容后端字段别名 */
function textOf(row: Record<string, unknown>, keys: string[]): string {
  for (const key of keys) {
    const value = row[key]
    if (value === null || value === undefined || value === '') continue
    if (typeof value === 'string') return value
    if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  }
  return ''
}

function definitionCode(row: Record<string, any>) {
  return textOf(row, ['code', 'definitionCode', 'taskCode', 'jobCode']) || '-'
}

function definitionName(row: Record<string, any>) {
  return textOf(row, ['name', 'definitionName', 'taskName', 'jobName']) || '-'
}

function definitionType(row: Record<string, any>) {
  return textOf(row, ['type', 'taskType', 'definitionType']) || '-'
}

function definitionCron(row: Record<string, any>) {
  return textOf(row, ['cronExpression', 'cron', 'scheduleExpression']) || '-'
}

function instanceTask(row: Record<string, any>) {
  return textOf(row, ['definitionName', 'taskName', 'name', 'definitionCode', 'taskCode']) ||
    (row.definitionId !== undefined && row.definitionId !== null ? `定义 #${row.definitionId}` : '-')
}

function instanceTriggerType(row: Record<string, any>) {
  return textOf(row, ['triggerType', 'triggerMode', 'triggerSource']) || '-'
}

function instanceDuration(row: Record<string, any>) {
  if (typeof row.durationMs === 'number' && Number.isFinite(row.durationMs)) {
    return formatDuration(row.durationMs)
  }
  const start = row.startTime || row.createdAt
  const end = row.endTime
  if (!start || !end) return '-'
  const diff = new Date(end).getTime() - new Date(start).getTime()
  if (!Number.isFinite(diff) || diff < 0) return '-'
  return formatDuration(diff)
}

function formatDuration(ms: number) {
  if (ms < 1000) return `${Math.round(ms)} ms`
  const seconds = Math.round(ms / 1000)
  if (seconds < 60) return `${seconds} 秒`
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return `${minutes} 分 ${rest} 秒`
}

const STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  ENABLED: '已启用',
  DISABLED: '已停用',
  PAUSED: '已暂停',
  PENDING: '待运行',
  QUEUED: '排队中',
  WAITING: '等待中',
  RUNNING: '运行中',
  EXECUTING: '执行中',
  SUCCESS: '成功',
  SUCCEEDED: '成功',
  COMPLETED: '已完成',
  DONE: '已完成',
  FAILED: '失败',
  ERROR: '异常',
  TIMEOUT: '超时',
  CANCELED: '已取消',
  CANCELLED: '已取消',
  PUBLISHED: '已发布',
  OFFLINE: '已下线'
}

function statusLabel(status?: string) {
  if (!status) return '-'
  return STATUS_LABELS[status.toUpperCase()] || status
}

function statusTag(status?: string): TagType {
  const value = (status || '').toUpperCase()
  if (['SUCCESS', 'SUCCEEDED', 'COMPLETED', 'DONE', 'ENABLED', 'PUBLISHED'].includes(value)) return 'success'
  if (['RUNNING', 'EXECUTING', 'PENDING', 'QUEUED', 'WAITING'].includes(value)) return 'warning'
  if (['FAILED', 'ERROR', 'TIMEOUT'].includes(value)) return 'danger'
  return 'info'
}

function statusOptionsOf(rows: Array<{ status?: string }>) {
  return Array.from(
    new Set(rows.map(row => row.status).filter((status): status is string => !!status))
  )
}

const definitionStatusOptions = computed(() => statusOptionsOf(definitions.value))
const instanceStatusOptions = computed(() => statusOptionsOf(instances.value))

const filteredDefinitions = computed(() => {
  const keyword = definitionKeyword.value.trim().toLowerCase()
  return definitions.value.filter(row => {
    if (definitionStatus.value && row.status !== definitionStatus.value) return false
    if (!keyword) return true
    return [definitionCode(row), definitionName(row), definitionType(row)]
      .some(value => value.toLowerCase().includes(keyword))
  })
})

const filteredInstances = computed(() => {
  const keyword = instanceKeyword.value.trim().toLowerCase()
  return instances.value.filter(row => {
    if (instanceStatus.value && row.status !== instanceStatus.value) return false
    if (!keyword) return true
    const idText = row.id === undefined || row.id === null ? '' : String(row.id)
    return [idText, instanceTask(row), instanceTriggerType(row)]
      .some(value => value.toLowerCase().includes(keyword))
  })
})

const loadError = computed(() => [definitionsError.value, instancesError.value].filter(Boolean).join('；'))

const logTitle = computed(() => {
  const task = logInstance.value ? instanceTask(logInstance.value) : ''
  return task ? `实例日志 · ${task}` : '实例日志'
})

const logText = computed(() => {
  if (logTextOverride.value) return logTextOverride.value
  return logs.value
    .map(line => {
      const time = formatTime(textOf(line, ['timestamp', 'createdAt', 'time']))
      const level = textOf(line, ['level', 'logLevel'])
      const content = textOf(line, ['message', 'content', 'logContent', 'logMessage', 'detail'])
      return [time === '-' ? '' : time, level, content || JSON.stringify(line)]
        .filter(Boolean)
        .join(' ')
    })
    .join('\n')
})

function formatTime(value?: string | number | null) {
  if (value === undefined || value === null || value === '') return '-'
  if (typeof value === 'number') {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
  }
  const text = String(value)
  if (/^\d{10,13}$/.test(text)) {
    const timestamp = text.length === 10 ? Number(text) * 1000 : Number(text)
    const date = new Date(timestamp)
    if (!Number.isNaN(date.getTime())) return date.toLocaleString('zh-CN', { hour12: false })
  }
  return text.replace('T', ' ').replace(/\.\d+Z?$/, '').slice(0, 19)
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? `${fallback}：${error.message}` : fallback
}

async function loadDefinitions() {
  definitionsLoading.value = true
  try {
    const res = await taskCenterApi.getDefinitions()
    definitions.value = toList<TaskCenterDefinition>(res.data)
    definitionsError.value = ''
  } catch (error) {
    definitions.value = []
    definitionsError.value = errorMessage(error, '任务定义加载失败')
  } finally {
    definitionsLoading.value = false
  }
}

async function loadInstances() {
  instancesLoading.value = true
  try {
    const res = await taskCenterApi.getInstances()
    instances.value = toList<TaskCenterInstance>(res.data)
    instancesError.value = ''
  } catch (error) {
    instances.value = []
    instancesError.value = errorMessage(error, '运行实例加载失败')
  } finally {
    instancesLoading.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadDefinitions(), loadInstances()])
}

async function triggerDefinition(row: Record<string, any>) {
  const name = definitionName(row)
  try {
    await ElMessageBox.confirm(`确认立即触发任务「${name}」？`, '触发任务', {
      type: 'warning',
      confirmButtonText: '触发',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  triggeringId.value = row.id
  try {
    const res = await taskCenterApi.triggerDefinition(row.id)
    const instance = res.data as TaskCenterInstance | undefined
    ElMessage.success(instance?.id ? `已触发，实例 #${instance.id}` : '已触发任务')
    activeTab.value = 'instances'
    await loadInstances()
  } catch {
    // 请求拦截器已统一提示错误
  } finally {
    triggeringId.value = null
  }
}

async function openLogs(row: Record<string, any>) {
  logInstance.value = row as TaskCenterInstance
  logDrawer.value = true
  logs.value = []
  logTextOverride.value = ''
  logsError.value = ''
  logsLoading.value = true
  try {
    const res = await taskCenterApi.getInstanceLogs(row.id)
    const data = res.data
    if (typeof data === 'string') {
      logTextOverride.value = data
    } else if (Array.isArray(data)) {
      logs.value = data
    } else if (data && typeof data === 'object') {
      logs.value = toList<TaskCenterLog>(data)
      const content = (data as Record<string, unknown>).content
      if (!logs.value.length && typeof content === 'string') logTextOverride.value = content
    }
  } catch (error) {
    logsError.value = errorMessage(error, '日志加载失败')
  } finally {
    logsLoading.value = false
  }
}

onMounted(refreshAll)
</script>

<style scoped>
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.page-alert {
  margin-bottom: 14px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.toolbar-total {
  margin-left: auto;
  color: var(--med-text-light);
  font-size: 12px;
}
.log-wrap {
  min-height: 200px;
}
.log-alert {
  margin-bottom: 12px;
}
.log-pre {
  margin: 0;
  padding: 16px;
  border-radius: 10px;
  background: #1f1b24;
  color: #e8e2ea;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: calc(100vh - 220px);
  overflow: auto;
}
</style>
