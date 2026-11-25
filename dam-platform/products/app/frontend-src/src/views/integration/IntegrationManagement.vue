<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">数据集成</h2>
        <p class="page-desc">管理数据集成任务，查看任务状态、源端/目标端与创建时间。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="loadJobs">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </div>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索任务编码/名称"
          clearable
          style="width: 260px;"
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px;" @change="search">
          <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <span class="toolbar-total">共 {{ total }} 条</span>
      </div>

      <el-table :data="jobs" stripe v-loading="loading">
        <el-table-column prop="jobCode" label="任务编码" min-width="160" show-overflow-tooltip />
        <el-table-column prop="jobName" label="任务名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="源端 / 目标端" width="160">
          <template #default="{ row }">{{ row.sourceType || '-' }} → {{ row.targetType || '-' }}</template>
        </el-table-column>
        <el-table-column label="同步模式" width="110">
          <template #default="{ row }">{{ row.syncMode || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !jobs.length" description="暂无集成任务" :image-size="60" />
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @change="loadJobs"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { integrationApi } from '@/api'
import type { IntegrationJob, PageQuery } from '@/api'

const STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'ENABLED', label: '已启用' },
  { value: 'DISABLED', label: '已停用' },
  { value: 'RUNNING', label: '运行中' },
  { value: 'FAILED', label: '失败' }
]

const query = ref<PageQuery>({ keyword: '', status: '', page: 1, pageSize: 20 })
const jobs = ref<IntegrationJob[]>([])
const total = ref(0)
const loading = ref(false)

async function loadJobs() {
  loading.value = true
  try {
    const res = await integrationApi.listJobs({
      ...query.value,
      keyword: query.value.keyword?.trim() || undefined,
      status: query.value.status || undefined
    })
    jobs.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } catch (_e) {
    jobs.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.value.page = 1
  loadJobs()
}

function resetQuery() {
  query.value = { keyword: '', status: '', page: 1, pageSize: 20 }
  loadJobs()
}

function statusLabel(status?: string) {
  return STATUS_OPTIONS.find(item => item.value === status)?.label || status || '-'
}

function statusTag(status?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'ENABLED') return 'success'
  if (status === 'RUNNING') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

onMounted(loadJobs)
</script>

<style scoped>
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
</style>
