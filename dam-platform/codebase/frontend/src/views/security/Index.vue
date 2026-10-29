<template>
  <div class="page-container">
    <div class="page-header"><h2>安全管控</h2></div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="敏感规则" name="rules">
        <el-card>
          <div class="search-bar">
            <el-button type="success" @click="showCreateRule = true">新增规则</el-button>
          </div>
          <el-table :data="sensitiveRules" stripe>
            <el-table-column prop="ruleName" label="规则名称" min-width="160" />
            <el-table-column prop="ruleType" label="规则类型" width="140" />
            <el-table-column prop="rulePattern" label="匹配模式" min-width="250" show-overflow-tooltip />
            <el-table-column prop="sensitivityLevel" label="级别" width="80">
              <template #default="{ row }">
                <el-tag :type="(row.sensitivityLevel || '').toLowerCase() === 'high' ? 'danger' : (row.sensitivityLevel || '').toLowerCase() === 'medium' ? 'warning' : 'info'" size="small">{{ row.sensitivityLevel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }"><el-tag :type="Number(row.status) === 1 ? 'success' : 'info'" size="small">{{ Number(row.status) === 1 ? '启用' : '停用' }}</el-tag></template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="脱敏策略" name="masking">
        <el-card>
          <div class="search-bar">
            <el-button type="success" @click="showCreateMasking = true">新增策略</el-button>
          </div>
          <el-table :data="maskingPolicies" stripe>
            <el-table-column prop="policyName" label="策略名称" min-width="160" />
            <el-table-column prop="targetType" label="目标类型" width="140" />
            <el-table-column prop="maskingType" label="脱敏类型" width="120" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }"><el-tag :type="Number(row.status) === 1 ? 'success' : 'info'" size="small">{{ Number(row.status) === 1 ? '启用' : '停用' }}</el-tag></template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="审计日志" name="audit">
        <el-card>
          <div class="search-bar">
            <el-select v-model="auditAction" placeholder="操作类型" clearable style="width: 140px;" @change="searchAudit">
              <el-option label="登录" value="login" /><el-option label="查询" value="query" />
              <el-option label="导出" value="export" /><el-option label="修改" value="modify" />
            </el-select>
            <el-button type="primary" @click="searchAudit">查询</el-button>
          </div>
          <el-table :data="auditLogs" stripe>
            <el-table-column prop="operatorId" label="操作人" width="120" />
            <el-table-column prop="operationType" label="操作" width="100" />
            <el-table-column prop="assetName" label="对象" width="140" show-overflow-tooltip />
            <el-table-column prop="sqlContent" label="SQL/详情" min-width="200" show-overflow-tooltip />
            <el-table-column prop="operatorIp" label="IP" width="140" />
            <el-table-column label="时间" width="180">
              <template #default="{ row }">{{ row.operationTime || row.createdAt || '-' }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="脱敏预览" name="maskingPreview">
        <el-card>
          <template #header><span>脱敏效果预览</span></template>
          <el-form label-width="100px">
            <el-form-item label="原始数据">
              <el-input v-model="maskPreview.value" type="textarea" :rows="3" placeholder="输入需要测试脱敏的数据..." />
            </el-form-item>
            <el-form-item label="脱敏算法">
              <el-select v-model="maskPreview.algorithm" style="width: 200px;">
                <el-option v-for="alg in algorithms" :key="alg" :label="algorithmLabel(alg)" :value="alg" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handlePreview">预览脱敏</el-button>
              <el-button @click="maskPreview.value = ''; maskPreview.result = ''">清空</el-button>
            </el-form-item>
            <el-form-item label="脱敏结果" v-if="maskPreview.result">
              <div class="bg-muted" style="padding: 12px 16px; border-radius: 4px; width: 100%;">
                <div class="text-success" style="font-size: 16px; font-weight: bold;">{{ maskPreview.result }}</div>
              </div>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showCreateRule" title="新增敏感规则" width="500px">
      <el-form :model="ruleForm" label-width="100px">
        <el-form-item label="规则名称"><el-input v-model="ruleForm.ruleName" /></el-form-item>
        <el-form-item label="规则类型"><el-input v-model="ruleForm.ruleType" /></el-form-item>
        <el-form-item label="匹配模式"><el-input v-model="ruleForm.rulePattern" placeholder="正则表达式或字段名模式" /></el-form-item>
        <el-form-item label="级别">
          <el-select v-model="ruleForm.sensitivityLevel">
            <el-option label="高" value="high" /><el-option label="中" value="medium" /><el-option label="低" value="low" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateRule = false">取消</el-button>
        <el-button type="primary" @click="submitRule">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCreateMasking" title="新增脱敏策略" width="500px">
      <el-form :model="maskingForm" label-width="100px">
        <el-form-item label="策略名称"><el-input v-model="maskingForm.policyName" /></el-form-item>
        <el-form-item label="目标类型"><el-input v-model="maskingForm.targetType" placeholder="TABLE/FILE/API" /></el-form-item>
        <el-form-item label="目标字段"><el-input v-model="maskingForm.targetColumn" /></el-form-item>
        <el-form-item label="脱敏类型">
          <el-select v-model="maskingForm.maskingType">
            <el-option v-for="alg in algorithms" :key="alg" :label="algorithmLabel(alg)" :value="alg" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateMasking = false">取消</el-button>
        <el-button type="primary" @click="submitMasking">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { securityApi } from '@/api'
import { ElMessage } from 'element-plus'

const activeTab = ref('rules')
const algorithms = ref<string[]>([])
const sensitiveRules = ref<any[]>([])
const maskingPolicies = ref<any[]>([])
const auditLogs = ref<any[]>([])
const auditAction = ref('')
const showCreateRule = ref(false)
const showCreateMasking = ref(false)
const ruleForm = ref<any>({ sensitivityLevel: 'medium' })
const maskingForm = ref<any>({ maskingType: 'mask' })
const maskPreview = ref({ value: '', algorithm: 'mask', result: '' })

onMounted(() => {
  loadAlgorithms()
  loadRules()
  loadMasking()
  searchAudit()
})

async function loadAlgorithms() {
  try {
    const res: any = await securityApi.getMaskingAlgorithms()
    algorithms.value = res.data || []
  } catch (_e) {
    algorithms.value = []
  }
}

const algorithmLabels: Record<string, string> = {
  mask: '通用掩码',
  replace: '替换',
  hash: '哈希',
  shuffle: '截断',
  id_card: '身份证掩码',
  phone: '手机号掩码',
  email: '邮箱掩码',
  name: '姓名掩码'
}

function algorithmLabel(alg: string) {
  return algorithmLabels[alg] || alg
}

async function loadRules() {
  const res: any = await securityApi.getSensitiveRules()
  sensitiveRules.value = res.data || []
}

async function loadMasking() {
  const res: any = await securityApi.getMaskingPolicies()
  maskingPolicies.value = res.data || []
}

async function searchAudit() {
  const res: any = await securityApi.searchAuditLogs({ action: auditAction.value || undefined })
  auditLogs.value = res.data?.records || []
}

async function handlePreview() {
  if (!maskPreview.value.value) {
    ElMessage.warning('请输入要测试脱敏的数据')
    return
  }
  try {
    const res: any = await securityApi.previewMasking({
      value: maskPreview.value.value,
      algorithm: maskPreview.value.algorithm
    })
    maskPreview.value.result = res.data?.masked || ''
  } catch (e: any) {
    ElMessage.error('脱敏预览失败')
  }
}

async function submitRule() {
  await securityApi.createSensitiveRule(ruleForm.value)
  ElMessage.success('创建成功')
  showCreateRule.value = false
  ruleForm.value = { sensitivityLevel: 'medium' }
  loadRules()
}

async function submitMasking() {
  await securityApi.createMaskingPolicy(maskingForm.value)
  ElMessage.success('创建成功')
  showCreateMasking.value = false
  maskingForm.value = { maskingType: 'mask' }
  loadMasking()
}
</script>
