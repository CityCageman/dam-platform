<template>
  <div class="page-container">
    <div class="page-header"><h2>工单中心</h2></div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="全部工单" name="all">
        <el-card>
          <div class="search-bar">
            <el-select v-model="ticketType" placeholder="工单类型" clearable style="width: 140px;" @change="loadTickets">
              <el-option label="数据申请" value="data_access" />
              <el-option label="质量问题" value="quality_issue" />
              <el-option label="变更申请" value="change_request" />
            </el-select>
            <el-select v-model="ticketStatus" placeholder="状态" clearable style="width: 120px;" @change="loadTickets">
              <el-option label="待处理" value="pending" /><el-option label="处理中" value="processing" />
              <el-option label="已完成" value="completed" /><el-option label="已驳回" value="rejected" />
            </el-select>
            <el-button type="success" @click="showCreate = true">新建工单</el-button>
          </div>
          <el-table :data="tickets" stripe>
            <el-table-column prop="ticketNo" label="编号" width="140" />
            <el-table-column prop="ticketType" label="类型" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ row.ticketType === 'data_access' ? '数据申请' : row.ticketType === 'quality_issue' ? '质量问题' : '变更申请' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="200" />
            <el-table-column prop="priority" label="优先级" width="80">
              <template #default="{ row }">
                <el-tag :type="row.priority === 'high' ? 'danger' : row.priority === 'medium' ? 'warning' : 'info'" size="small">{{ row.priority }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="Number(row.status) === 2 ? 'success' : Number(row.status) === 3 ? 'danger' : 'warning'" size="small">
                  {{ ['待处理', '处理中', '已完成', '已驳回'][Number(row.status)] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="180" />
          </el-table>
          <div style="margin-top: 16px;" class="pagination-bar">
            <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total" layout="total, prev, pager, next" @change="loadTickets" />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showCreate" title="新建工单" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="工单类型">
          <el-select v-model="form.ticketType">
            <el-option label="数据申请" value="data_access" /><el-option label="质量问题" value="quality_issue" /><el-option label="变更申请" value="change_request" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority">
            <el-option label="高" value="high" /><el-option label="中" value="medium" /><el-option label="低" value="low" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="submit">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { operationsApi } from '@/api'
import { ElMessage } from 'element-plus'

const activeTab = ref('all')
const tickets = ref<any[]>([])
const ticketType = ref('')
const ticketStatus = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const showCreate = ref(false)
const form = ref<any>({ priority: 'medium' })

onMounted(() => loadTickets())

async function loadTickets() {
  const res: any = await operationsApi.searchTickets({
    ticketType: ticketType.value || undefined,
    status: ticketStatus.value || undefined,
    page: page.value,
    pageSize: pageSize.value
  })
  tickets.value = res.data?.records || []
  total.value = res.data?.total || 0
}

async function submit() {
  await operationsApi.createTicket(form.value)
  ElMessage.success('创建成功')
  showCreate.value = false
  form.value = { priority: 'medium' }
  loadTickets()
}
</script>
