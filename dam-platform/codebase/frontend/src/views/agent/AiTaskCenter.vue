<template>
  <div class="ai-tasks">
    <div class="stat-grid" style="margin-bottom: 14px;">
      <div class="stat-card">
        <div class="stat-label">任务总数</div>
        <div class="stat-value">{{ stats.total || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">待生成</div>
        <div class="stat-value">{{ stats.PENDING || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">待审核</div>
        <div class="stat-value">{{ stats.REVIEW || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已完成</div>
        <div class="stat-value">{{ stats.DONE || 0 }}</div>
      </div>
    </div>

    <div class="panel">
      <div class="ai-filter">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px;" @change="loadTasks">
          <el-option v-for="s in STATUS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-select v-model="query.taskType" placeholder="全部类型" clearable style="width: 140px;" @change="loadTasks">
          <el-option v-for="t in TYPES" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="搜索任务标题" clearable style="width: 220px;"
                  @keyup.enter="loadTasks" @clear="loadTasks" />
        <el-button type="primary" @click="loadTasks">查询</el-button>
        <el-button @click="loadAll">刷新</el-button>
        <div style="flex: 1;" />
        <el-button @click="createDemo">新建注释任务</el-button>
      </div>

      <el-table :data="tasks" stripe v-loading="loading" style="cursor: pointer;" @row-click="openDetail">
        <el-table-column prop="title" label="任务" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.taskType)">{{ typeLabel(row.taskType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="150">
          <template #default="{ row }">
            <span class="progress-text">{{ row.acceptedCount || 0 }} 采纳 / {{ row.rejectedCount || 0 }} 驳回 / {{ row.totalCount || 0 }} 条</span>
          </template>
        </el-table-column>
        <el-table-column prop="model" label="模型" width="140" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click.stop="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !tasks.length" description="暂无 AI 任务，AI 注释/分类/血缘生成后会自动进入这里" :image-size="70" />
      <div class="pagination-bar">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.pageSize" :total="total"
                       layout="total, prev, pager, next" @change="loadTasks" />
      </div>
    </div>

    <el-drawer v-model="drawer" :title="current?.title || '任务详情'" size="62%" append-to-body>
      <template v-if="current">
        <el-descriptions :column="3" border size="small" style="margin-bottom: 14px;">
          <el-descriptions-item label="类型">{{ typeLabel(current.taskType) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(current.status) }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ current.model || '-' }}</el-descriptions-item>
          <el-descriptions-item label="目标">{{ current.targetType || '-' }} / {{ current.targetId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="进度">{{ current.acceptedCount || 0 }} / {{ current.rejectedCount || 0 }} / {{ current.totalCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(current.createdAt) }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="items" stripe size="small">
          <el-table-column prop="targetName" label="目标" min-width="150" show-overflow-tooltip />
          <el-table-column prop="fieldName" label="字段" width="130" show-overflow-tooltip />
          <el-table-column prop="draftContent" label="AI 草稿" min-width="240" show-overflow-tooltip />
          <el-table-column label="置信度" width="80">
            <template #default="{ row }">{{ row.confidence ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="itemTag(row.status)">{{ itemLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'DRAFT'">
                <el-button text type="primary" size="small" @click="review(row, true)">采纳</el-button>
                <el-button text type="danger" size="small" @click="review(row, false)">驳回</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!items.length" description="该任务暂无草稿明细" :image-size="60" />
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { catalogApi } from '@/api'
import { ElMessage } from 'element-plus'

const STATUS = [
  { value: 'PENDING', label: '待生成' },
  { value: 'RUNNING', label: '生成中' },
  { value: 'REVIEW', label: '待审核' },
  { value: 'DONE', label: '已完成' },
  { value: 'FAILED', label: '失败' }
]
const TYPES = [
  { value: 'COMMENT', label: '注释生成' },
  { value: 'CLASSIFY', label: '分类分级' },
  { value: 'LINEAGE', label: '血缘识别' },
  { value: 'REPORT', label: '报表生成' }
]

const stats = ref<any>({})
const tasks = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const query = ref({ status: '', taskType: '', keyword: '', page: 1, pageSize: 20 })
const drawer = ref(false)
const current = ref<any>(null)
const items = ref<any[]>([])

const statusLabel = (v: string) => STATUS.find(s => s.value === v)?.label || v || '-'
const statusTag = (v: string) => v === 'DONE' ? 'success' : v === 'REVIEW' ? 'warning' : v === 'FAILED' ? 'danger' : 'info'
const typeLabel = (v: string) => TYPES.find(t => t.value === v)?.label || v || '-'
const typeTag = (v: string) => v === 'COMMENT' ? 'primary' : v === 'CLASSIFY' ? 'warning' : v === 'LINEAGE' ? 'success' : 'info'
const itemLabel = (v: string) => v === 'ACCEPTED' ? '已采纳' : v === 'REJECTED' ? '已驳回' : '草稿'
const itemTag = (v: string) => v === 'ACCEPTED' ? 'success' : v === 'REJECTED' ? 'danger' : 'info'
const formatTime = (v: string) => v ? String(v).replace('T', ' ').slice(0, 19) : '-'

async function loadStats() {
  try {
    const res: any = await catalogApi.getAiTaskStats()
    stats.value = res.data || {}
  } catch (_e) { stats.value = {} }
}

async function loadTasks() {
  loading.value = true
  try {
    const res: any = await catalogApi.listAiTasks({
      status: query.value.status || undefined,
      taskType: query.value.taskType || undefined,
      keyword: query.value.keyword || undefined,
      page: query.value.page,
      pageSize: query.value.pageSize
    })
    tasks.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (_e) { tasks.value = []; total.value = 0 }
  finally { loading.value = false }
}

async function loadAll() { await Promise.all([loadTasks(), loadStats()]) }

async function openDetail(row: any) {
  try {
    const res: any = await catalogApi.getAiTask(row.id)
    current.value = res.data?.task
    items.value = res.data?.items || []
    drawer.value = true
  } catch (e: any) { ElMessage.error(e.message || '加载任务失败') }
}

async function review(item: any, accept: boolean) {
  if (!current.value) return
  try {
    await catalogApi.reviewAiTaskItem(current.value.id, item.id, accept)
    ElMessage.success(accept ? '已采纳' : '已驳回')
    await openDetail(current.value)
    await loadAll()
  } catch (e: any) { ElMessage.error(e.message || '操作失败') }
}

/** 新建一个空的注释任务（占位，后续 AI 生成流程会写入草稿明细） */
async function createDemo() {
  try {
    await catalogApi.createAiTask({
      taskType: 'COMMENT',
      title: 'AI 注释任务 ' + new Date().toLocaleString(),
      targetType: 'DATASOURCE',
      status: 'PENDING'
    })
    ElMessage.success('任务已创建')
    await loadAll()
  } catch (e: any) { ElMessage.error(e.message || '创建失败') }
}

onMounted(loadAll)
</script>

<style scoped>
.ai-filter { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.progress-text { font-size: 12px; color: var(--med-text-secondary); }
</style>
