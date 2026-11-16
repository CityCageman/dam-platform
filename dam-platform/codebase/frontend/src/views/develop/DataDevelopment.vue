<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">数据开发</h2>
        <p class="page-desc">管理开发任务的编码、类型与脚本内容，支持发布、下线与历史版本查看。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="loadTasks">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新建任务
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
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索任务编码/名称/类型"
          clearable
          style="width: 280px;"
        />
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px;">
          <el-option
            v-for="item in statusOptions"
            :key="item"
            :label="statusLabel(item)"
            :value="item"
          />
        </el-select>
        <el-button :loading="loading" @click="loadTasks">查询</el-button>
        <span class="toolbar-total">共 {{ filteredTasks.length }} 个任务</span>
      </div>

      <el-table :data="filteredTasks" stripe v-loading="loading">
        <el-table-column label="任务编码" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ taskCode(row) }}</template>
        </el-table-column>
        <el-table-column label="任务名称" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ taskName(row) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ taskType(row) }}</template>
        </el-table-column>
        <el-table-column label="数据源 ID" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ taskDatasource(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="90">
          <template #default="{ row }">{{ taskVersion(row) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="isPublished(row)"
              text
              type="warning"
              size="small"
              :loading="actingId === row.id"
              @click="offline(row)"
            >
              下线
            </el-button>
            <el-button
              v-else
              text
              type="success"
              size="small"
              :loading="actingId === row.id"
              @click="publish(row)"
            >
              发布
            </el-button>
            <el-button text type="info" size="small" @click="openVersions(row)">版本</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!loading && !filteredTasks.length"
        description="暂无开发任务"
        :image-size="60"
      />
    </div>

    <el-dialog
      v-model="formVisible"
      :title="editId === null ? '新建开发任务' : '编辑开发任务'"
      width="680px"
      top="7vh"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="96px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="任务编码" prop="code">
              <el-input v-model="form.code" placeholder="例如：dwd_patient_daily" :disabled="editId !== null" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务名称" prop="name">
              <el-input v-model="form.name" placeholder="例如：患者主题日增量" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="任务类型" prop="type">
              <el-select v-model="form.type" filterable allow-create style="width: 100%;">
                <el-option v-for="item in TYPE_OPTIONS" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据源 ID" prop="datasourceId">
              <el-input-number
                v-model="form.datasourceId"
                :min="1"
                :controls="false"
                placeholder="请输入数据源 ID"
                style="width: 100%;"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="任务内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="10"
            class="code-input"
            placeholder="请输入 SQL / 脚本内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="versionVisible"
      :title="versionTitle"
      width="860px"
      top="8vh"
    >
      <el-alert
        v-if="versionsError"
        class="page-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="versionsError"
      />
      <el-table :data="versions" stripe v-loading="versionsLoading" max-height="480">
        <el-table-column label="版本" width="90">
          <template #default="{ row }">{{ versionLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="变更说明" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ versionChangeLog(row) }}</template>
        </el-table-column>
        <el-table-column label="内容摘要" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ contentSummary(row.content) }}</template>
        </el-table-column>
        <el-table-column label="创建人" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ versionCreator(row) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!versionsLoading && !versions.length"
        description="该任务暂无历史版本"
        :image-size="60"
      />
      <template #footer>
        <el-button @click="versionVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { devApi } from '@/api'
import type { DevTask, DevTaskPayload, DevTaskVersion } from '@/api'

type TagType = 'success' | 'warning' | 'danger' | 'info'

interface DevTaskForm {
  code: string
  name: string
  type: string
  content: string
  datasourceId: number | null
}

const TYPE_OPTIONS = ['SQL', 'SHELL', 'PYTHON', 'SCALA', 'SPARK', 'FLINK']

const tasks = ref<DevTask[]>([])
const loading = ref(false)
const loadError = ref('')
const keyword = ref('')
const statusFilter = ref('')
const actingId = ref<number | null>(null)

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const submitting = ref(false)
const editId = ref<number | null>(null)
const form = ref<DevTaskForm>({
  code: '',
  name: '',
  type: 'SQL',
  content: '',
  datasourceId: null
})

const formRules: FormRules = {
  code: [{ required: true, message: '请输入任务编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择任务类型', trigger: 'change' }],
  datasourceId: [{ required: true, message: '请输入数据源 ID', trigger: 'blur', type: 'number' }],
  content: [{ required: true, message: '请输入任务内容', trigger: 'blur' }]
}

const versionVisible = ref(false)
const versionTask = ref<DevTask | null>(null)
const versions = ref<DevTaskVersion[]>([])
const versionsLoading = ref(false)
const versionsError = ref('')

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

function numberField(row: Record<string, any>, key: string): number | null {
  const value = row[key]
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim() !== '' && !Number.isNaN(Number(value))) {
    return Number(value)
  }
  return null
}

function taskCode(row: Record<string, any>) {
  return textOf(row, ['code', 'taskCode']) || '-'
}

function taskName(row: Record<string, any>) {
  return textOf(row, ['name', 'taskName']) || '-'
}

function taskType(row: Record<string, any>) {
  return textOf(row, ['type', 'taskType']) || '-'
}

function taskDatasource(row: Record<string, any>) {
  return textOf(row, ['datasourceId', 'dataSourceId']) || '-'
}

function taskVersion(row: Record<string, any>) {
  const value = textOf(row, ['latestVersion', 'currentVersion', 'version'])
  return value ? `v${value}` : '-'
}

const STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  ENABLED: '已启用',
  DISABLED: '已停用',
  PUBLISHED: '已发布',
  OFFLINE: '已下线',
  RUNNING: '运行中',
  FAILED: '失败'
}

function statusLabel(status?: string) {
  if (!status) return '-'
  return STATUS_LABELS[status.toUpperCase()] || status
}

function statusTag(status?: string): TagType {
  const value = (status || '').toUpperCase()
  if (['PUBLISHED', 'ENABLED', 'SUCCESS', 'COMPLETED'].includes(value)) return 'success'
  if (['RUNNING', 'PENDING'].includes(value)) return 'warning'
  if (['FAILED', 'ERROR'].includes(value)) return 'danger'
  return 'info'
}

function isPublished(row: Record<string, any>) {
  return textOf(row, ['status']).toUpperCase() === 'PUBLISHED'
}

const statusOptions = computed(() =>
  Array.from(new Set(tasks.value.map(task => task.status).filter((status): status is string => !!status)))
)

const filteredTasks = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  return tasks.value.filter(row => {
    if (statusFilter.value && row.status !== statusFilter.value) return false
    if (!value) return true
    return [taskCode(row), taskName(row), taskType(row)]
      .some(text => text.toLowerCase().includes(value))
  })
})

const versionTitle = computed(() => {
  const name = versionTask.value ? taskName(versionTask.value) : ''
  return name ? `历史版本 · ${name}` : '历史版本'
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

async function loadTasks() {
  loading.value = true
  try {
    const res = await devApi.getTasks()
    tasks.value = toList<DevTask>(res.data)
    loadError.value = ''
  } catch (error) {
    tasks.value = []
    loadError.value = errorMessage(error, '开发任务加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.value = {
    code: '',
    name: '',
    type: 'SQL',
    content: '',
    datasourceId: null
  }
  nextTick(() => formRef.value?.clearValidate())
}

function openCreate() {
  editId.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(row: Record<string, any>) {
  editId.value = row.id
  form.value = {
    code: taskCode(row) === '-' ? '' : taskCode(row),
    name: taskName(row) === '-' ? '' : taskName(row),
    type: taskType(row) === '-' ? 'SQL' : taskType(row),
    content: textOf(row, ['content']),
    datasourceId: numberField(row, 'datasourceId') ?? numberField(row, 'dataSourceId')
  }
  formVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function submitForm() {
  if (!formRef.value) return
  let valid = false
  try {
    valid = await formRef.value.validate()
  } catch {
    valid = false
  }
  if (!valid) return

  const payload: DevTaskPayload = {
    code: form.value.code.trim(),
    name: form.value.name.trim(),
    type: form.value.type,
    content: form.value.content,
    datasourceId: form.value.datasourceId
  }

  submitting.value = true
  try {
    if (editId.value === null) {
      await devApi.createTask(payload)
      ElMessage.success('创建成功')
    } else {
      await devApi.updateTask(editId.value, payload)
      ElMessage.success('保存成功')
    }
    formVisible.value = false
    await loadTasks()
  } catch {
    // 请求拦截器已统一提示错误
  } finally {
    submitting.value = false
  }
}

async function publish(row: Record<string, any>) {
  const name = taskName(row)
  try {
    await ElMessageBox.confirm(`确认发布任务「${name}」？发布后最新版本将生效。`, '发布任务', {
      type: 'warning',
      confirmButtonText: '发布',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  actingId.value = row.id
  try {
    await devApi.publishTask(row.id)
    ElMessage.success('发布成功')
    await loadTasks()
  } catch {
    // 请求拦截器已统一提示错误
  } finally {
    actingId.value = null
  }
}

async function offline(row: Record<string, any>) {
  const name = taskName(row)
  try {
    await ElMessageBox.confirm(`确认下线任务「${name}」？下线后将停止对外提供最新版本。`, '下线任务', {
      type: 'warning',
      confirmButtonText: '下线',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  actingId.value = row.id
  try {
    await devApi.offlineTask(row.id)
    ElMessage.success('已下线')
    await loadTasks()
  } catch {
    // 请求拦截器已统一提示错误
  } finally {
    actingId.value = null
  }
}

async function openVersions(row: Record<string, any>) {
  versionTask.value = row as DevTask
  versionVisible.value = true
  versions.value = []
  versionsError.value = ''
  versionsLoading.value = true
  try {
    const res = await devApi.getVersions(row.id)
    versions.value = toList<DevTaskVersion>(res.data)
  } catch (error) {
    versions.value = []
    versionsError.value = errorMessage(error, '版本列表加载失败')
  } finally {
    versionsLoading.value = false
  }
}

function versionLabel(row: DevTaskVersion) {
  const value = textOf(row, ['version', 'versionNo', 'versionNumber'])
  return value ? `v${value}` : '-'
}

function versionChangeLog(row: DevTaskVersion) {
  return textOf(row, ['changeLog', 'remark', 'description']) || '-'
}

function versionCreator(row: DevTaskVersion) {
  return textOf(row, ['createdBy', 'creator', 'operator']) || '-'
}

function contentSummary(content: unknown) {
  if (typeof content !== 'string' || !content.trim()) return '-'
  const oneLine = content.replace(/\s+/g, ' ').trim()
  return oneLine.length > 120 ? `${oneLine.slice(0, 120)}…` : oneLine
}

onMounted(loadTasks)
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
.code-input :deep(.el-textarea__inner) {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12.5px;
  line-height: 1.6;
}
</style>
