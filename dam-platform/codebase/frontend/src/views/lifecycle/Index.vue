<template>
  <div class="page-container">
    <div class="page-header"><h2>生命周期管理</h2></div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="存储策略" name="policies">
        <el-card>
          <div class="search-bar">
            <el-button type="success" @click="showCreate = true">新建策略</el-button>
          </div>
          <el-table :data="policies" stripe>
            <el-table-column prop="policyName" label="策略名称" min-width="150" />
            <el-table-column prop="targetType" label="资产类型" width="120" />
            <el-table-column prop="hotDays" label="热数据(天)" width="120" />
            <el-table-column prop="coldDays" label="冷数据(天)" width="120" />
            <el-table-column prop="archiveDays" label="归档触发(天)" width="120" />
            <el-table-column label="自动执行" width="100">
              <template #default="{ row }"><el-tag :type="row.autoExecute ? 'success' : 'info'" size="small">{{ row.autoExecute ? '是' : '否' }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }"><el-tag :type="Number(row.status) === 1 ? 'success' : 'info'" size="small">{{ Number(row.status) === 1 ? '启用' : '停用' }}</el-tag></template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="销毁记录" name="destructions">
        <el-card>
          <el-table :data="destructions" stripe>
            <el-table-column prop="assetName" label="资产名称" min-width="150" />
            <el-table-column prop="destructionType" label="销毁类型" width="120" />
            <el-table-column prop="reason" label="原因" min-width="200" show-overflow-tooltip />
            <el-table-column prop="approvalStatus" label="审批状态" width="120">
              <template #default="{ row }">
                <el-tag :type="row.approvalStatus === 'approved' ? 'success' : row.approvalStatus === 'rejected' ? 'danger' : 'warning'" size="small">{{ row.approvalStatus }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="180" />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showCreate" title="新建策略" width="500px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="策略名称"><el-input v-model="form.policyName" /></el-form-item>
        <el-form-item label="资产类型">
          <el-select v-model="form.targetType">
            <el-option label="数据库表" value="TABLE" /><el-option label="文件" value="FILE" /><el-option label="API" value="API" /><el-option label="报表" value="REPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="热数据(天)"><el-input-number v-model="form.hotDays" :min="1" /></el-form-item>
        <el-form-item label="冷数据(天)"><el-input-number v-model="form.coldDays" :min="1" /></el-form-item>
        <el-form-item label="归档触发(天)"><el-input-number v-model="form.archiveDays" :min="1" /></el-form-item>
        <el-form-item label="自动执行"><el-switch v-model="form.autoExecute" active-text="是" inactive-text="否" /></el-form-item>
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
import { lifecycleApi } from '@/api'
import { ElMessage } from 'element-plus'

const activeTab = ref('policies')
const policies = ref<any[]>([])
const destructions = ref<any[]>([])
const showCreate = ref(false)
const form = ref<any>({ hotDays: 90, coldDays: 365, archiveDays: 730, autoExecute: true })

onMounted(() => {
  loadPolicies()
  loadDestructions()
})

async function loadPolicies() {
  const res: any = await lifecycleApi.getPolicies()
  policies.value = res.data || []
}

async function loadDestructions() {
  const res: any = await lifecycleApi.getDestructionRecords()
  destructions.value = res.data || []
}

async function submit() {
  await lifecycleApi.createPolicy(form.value)
  ElMessage.success('创建成功')
  showCreate.value = false
  form.value = { hotDays: 90, coldDays: 365, archiveDays: 730, autoExecute: true }
  loadPolicies()
}
</script>
