<template>
  <div class="page-container">
    <div class="page-header">
      <h2>数据标准管理</h2>
    </div>
    <el-card>
      <div class="search-bar">
        <el-input v-model="keyword" placeholder="搜索标准名称/编码" clearable style="width: 280px;" @keyup.enter="search" />
        <el-select v-model="stdType" placeholder="标准类型" clearable style="width: 140px;" @change="search">
          <el-option label="数据字典" value="dictionary" />
          <el-option label="命名规范" value="naming" />
          <el-option label="编码规范" value="coding" />
          <el-option label="格式规范" value="format" />
        </el-select>
        <el-select v-model="status" placeholder="状态" clearable style="width: 120px;" @change="search">
          <el-option label="草稿" :value="0" />
          <el-option label="已发布" :value="1" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button type="success" @click="showCreate = true">新建标准</el-button>
      </div>
      <el-table :data="standardList" stripe>
        <el-table-column prop="codeTableName" label="码表名称" min-width="180" />
        <el-table-column prop="codeTableCode" label="码表编码" width="150" />
        <el-table-column prop="codeTableType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.codeTableType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="Number(row.status) === 1 ? 'success' : 'info'" size="small">{{ Number(row.status) === 1 ? '已发布' : '草稿' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text @click="viewDetail(row)">详情</el-button>
            <el-button size="small" text type="danger" @click="deleteStandard(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 16px;" class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total" layout="total, prev, pager, next" @change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="showCreate" title="新建标准" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="码表名称"><el-input v-model="form.codeTableName" /></el-form-item>
        <el-form-item label="码表编码"><el-input v-model="form.codeTableCode" /></el-form-item>
        <el-form-item label="码表类型">
          <el-select v-model="form.codeTableType">
            <el-option label="数据字典" value="dictionary" />
            <el-option label="命名规范" value="naming" />
            <el-option label="编码规范" value="coding" />
            <el-option label="格式规范" value="format" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="submit">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDetail" title="标准详情" width="700px">
      <el-descriptions :column="2" border v-if="detail">
        <el-descriptions-item label="名称">{{ detail.codeTableName }}</el-descriptions-item>
        <el-descriptions-item label="编码">{{ detail.codeTableCode }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail.codeTableType }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ Number(detail.status) === 1 ? '已发布' : '草稿' }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ detail.description }}</el-descriptions-item>
      </el-descriptions>
      <el-divider />
      <h4 style="margin-bottom: 12px;">标准值域</h4>
      <el-table :data="values" stripe>
        <el-table-column prop="valueCode" label="值编码" width="150" />
        <el-table-column prop="valueName" label="值名称" min-width="200" />
        <el-table-column prop="description" label="描述" min-width="200" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { standardApi } from '@/api'
import { ElMessage } from 'element-plus'

const keyword = ref('')
const stdType = ref('')
const status = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const standardList = ref<any[]>([])
const showCreate = ref(false)
const showDetail = ref(false)
const detail = ref<any>(null)
const values = ref<any[]>([])
const form = ref<any>({})

onMounted(() => loadData())

async function loadData() {
  const res: any = await standardApi.searchStandards({
    keyword: keyword.value || undefined,
    stdType: stdType.value || undefined,
    status: status.value === '' ? undefined : String(status.value),
    page: page.value,
    pageSize: pageSize.value
  })
  standardList.value = res.data.records || []
  total.value = res.data.total || 0
}

function search() { page.value = 1; loadData() }

async function viewDetail(row: any) {
  const res: any = await standardApi.getStandardDetail(row.id)
  detail.value = res.data
  const res2: any = await standardApi.getStandardValues(row.id)
  values.value = res2.data || []
  showDetail.value = true
}

async function deleteStandard(id: number) {
  await standardApi.deleteStandard(id)
  ElMessage.success('删除成功')
  loadData()
}

async function submit() {
  await standardApi.createStandard(form.value)
  ElMessage.success('创建成功')
  showCreate.value = false
  form.value = {}
  loadData()
}
</script>
