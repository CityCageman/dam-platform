<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">数据服务</h2>
        <p class="page-desc">管理对外数据 API 的编码、访问路径、请求方法与发布状态。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="loadApis">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </div>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索 API 编码/名称/路径"
          clearable
          style="width: 280px;"
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

      <el-table :data="apis" stripe v-loading="loading">
        <el-table-column prop="apiCode" label="API 编码" min-width="160" show-overflow-tooltip />
        <el-table-column prop="apiName" label="API 名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="path" label="访问路径" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.path || '-' }}</template>
        </el-table-column>
        <el-table-column label="方法" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="methodTag(row.method)">{{ row.method || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !apis.length" description="暂无数据服务 API" :image-size="60" />
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @change="loadApis"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { dataServiceApi } from '@/api'
import type { DataApiDefinition, PageQuery } from '@/api'

const STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'OFFLINE', label: '已下线' }
]

const query = ref<PageQuery>({ keyword: '', status: '', page: 1, pageSize: 20 })
const apis = ref<DataApiDefinition[]>([])
const total = ref(0)
const loading = ref(false)

async function loadApis() {
  loading.value = true
  try {
    const res = await dataServiceApi.listApis({
      ...query.value,
      keyword: query.value.keyword?.trim() || undefined,
      status: query.value.status || undefined
    })
    apis.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } catch (_e) {
    apis.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.value.page = 1
  loadApis()
}

function resetQuery() {
  query.value = { keyword: '', status: '', page: 1, pageSize: 20 }
  loadApis()
}

function statusLabel(status?: string) {
  return STATUS_OPTIONS.find(item => item.value === status)?.label || status || '-'
}

function statusTag(status?: string): 'success' | 'warning' | 'info' {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'OFFLINE') return 'warning'
  return 'info'
}

function methodTag(method?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (method === 'GET') return 'success'
  if (method === 'POST') return 'warning'
  if (method === 'DELETE') return 'danger'
  return 'info'
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

onMounted(loadApis)
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
