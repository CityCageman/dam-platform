<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">安全管控</h2>
        <p class="page-desc">敏感数据识别、脱敏策略、审计日志与脱敏效果预览。</p>
      </div>
      <div class="head-actions">
        <el-button @click="loadAll"><el-icon><Refresh /></el-icon>刷新</el-button>
      </div>
    </div>

    <div class="stat-grid" style="margin-bottom: 14px;">
      <div class="stat-card">
        <div class="stat-label">敏感规则</div>
        <div class="stat-value">{{ sensitiveRules.length }}</div>
        <div class="stat-foot">高敏感 {{ highRules }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">脱敏策略</div>
        <div class="stat-value">{{ maskingPolicies.length }}</div>
        <div class="stat-foot">启用 {{ enabledPolicies }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">审计日志</div>
        <div class="stat-value">{{ auditLogs.length }}</div>
        <div class="stat-foot">当前筛选结果</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">脱敏算法</div>
        <div class="stat-value">{{ algorithms.length }}</div>
        <div class="stat-foot">可用于策略/预览</div>
      </div>
    </div>
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
      <el-tab-pane label="数据权限" name="permissions">
        <el-card>
          <div class="search-bar">
            <el-button type="success" @click="openPermission()">新增权限策略</el-button>
            <span class="muted">ABAC：主体（角色/部门/用户）对资源（表/字段）的操作权限</span>
          </div>
          <el-table :data="permissions" stripe>
            <el-table-column prop="permissionCode" label="权限编码" width="150" show-overflow-tooltip />
            <el-table-column prop="permissionName" label="名称" min-width="150" show-overflow-tooltip />
            <el-table-column label="主体" width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.subjectType }} / {{ row.subjectId }}</template>
            </el-table-column>
            <el-table-column label="资源" width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.resourceType }} / {{ row.resourceId }}</template>
            </el-table-column>
            <el-table-column prop="actionType" label="操作" width="100" />
            <el-table-column prop="priority" label="优先级" width="80" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="Number(row.status) === 1 ? 'success' : 'info'">{{ Number(row.status) === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="openPermission(row)">编辑</el-button>
                <el-button text type="danger" size="small" @click="removePermission(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!permissions.length" description="暂无权限策略" :image-size="56" />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="访问申请" name="access">
        <el-card>
          <div class="search-bar">
            <el-select v-model="accessStatus" placeholder="申请状态" clearable style="width: 130px;" @change="loadAccess">
              <el-option label="待审批" value="pending" /><el-option label="已批准" value="approved" /><el-option label="已拒绝" value="rejected" />
            </el-select>
            <el-input-number v-model="approverId" :min="1" placeholder="审批人ID" style="width: 130px;" />
            <el-button type="primary" @click="loadAccess">查询</el-button>
            <el-button type="success" @click="openAccess">新建申请</el-button>
          </div>
          <el-table :data="accessRequests" stripe>
            <el-table-column prop="applicationNo" label="申请单号" width="150" />
            <el-table-column prop="applicantId" label="申请人" width="100" />
            <el-table-column label="资产" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ assetName(row.assetId) }}</template>
            </el-table-column>
            <el-table-column prop="accessType" label="类型" width="100" />
            <el-table-column prop="accessReason" label="理由" min-width="180" show-overflow-tooltip />
            <el-table-column prop="validityDays" label="有效期(天)" width="100" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="Number(row.status) === 1 ? 'success' : Number(row.status) === 2 ? 'danger' : 'warning'">
                  {{ accessLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <template v-if="Number(row.status) === 0">
                  <el-button text type="success" size="small" @click="approve(row, true)">批准</el-button>
                  <el-button text type="danger" size="small" @click="approve(row, false)">拒绝</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!accessRequests.length" description="暂无访问申请" :image-size="56" />
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

    <el-dialog v-model="showPermissionForm" :title="isEditPermission ? '编辑权限策略' : '新增权限策略'" width="620px" top="12vh">
      <el-form :model="permissionForm" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="权限编码" required><el-input v-model="permissionForm.permissionCode" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="权限名称" required><el-input v-model="permissionForm.permissionName" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="主体类型">
              <el-select v-model="permissionForm.subjectType" style="width: 100%;">
                <el-option label="角色" value="ROLE" /><el-option label="部门" value="DEPARTMENT" /><el-option label="用户" value="USER" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="主体ID"><el-input v-model="permissionForm.subjectId" placeholder="角色/部门/用户ID" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="资源类型">
              <el-select v-model="permissionForm.resourceType" style="width: 100%;">
                <el-option label="数据表" value="TABLE" /><el-option label="字段" value="COLUMN" /><el-option label="资产" value="ASSET" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="资源ID"><el-input-number v-model="permissionForm.resourceId" :min="0" style="width: 100%;" /></el-form-item></el-col>
          <el-col :span="8">
            <el-form-item label="操作">
              <el-select v-model="permissionForm.actionType" style="width: 100%;">
                <el-option label="查询" value="SELECT" /><el-option label="导出" value="EXPORT" />
                <el-option label="修改" value="UPDATE" /><el-option label="删除" value="DELETE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="优先级"><el-input-number v-model="permissionForm.priority" :min="0" :max="9999" style="width: 100%;" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="状态"><el-switch v-model="permissionForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="行过滤条件"><el-input v-model="permissionForm.filterCondition" placeholder="如：dept_id = ${deptId}" /></el-form-item>
        <el-form-item label="列掩码"><el-input v-model="permissionForm.columnMask" placeholder="如：phone=mask" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="permissionForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPermissionForm = false">取消</el-button>
        <el-button type="primary" @click="submitPermission">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showAccessForm" title="新建数据访问申请" width="560px" top="16vh">
      <el-form :model="accessForm" label-width="100px">
        <el-form-item label="申请资产">
          <el-select v-model="accessForm.assetId" filterable placeholder="选择资产" style="width: 100%;">
            <el-option v-for="a in assets" :key="a.id" :label="`${a.assetName} (${a.tableName || a.sourceMetadataType || ''})`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="访问类型">
          <el-select v-model="accessForm.accessType" style="width: 100%;">
            <el-option label="查询" value="READ" /><el-option label="导出" value="EXPORT" /><el-option label="接口调用" value="API" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请理由"><el-input v-model="accessForm.accessReason" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="有效期(天)"><el-input-number v-model="accessForm.validityDays" :min="1" :max="3650" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAccessForm = false">取消</el-button>
        <el-button type="primary" @click="submitAccess">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { securityApi, catalogApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed } from 'vue'
import { Refresh } from '@element-plus/icons-vue'

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

const permissions = ref<any[]>([])
const accessRequests = ref<any[]>([])
const accessStatus = ref('')
const approverId = ref<number>(1)
const assets = ref<any[]>([])
const showPermissionForm = ref(false)
const isEditPermission = ref(false)
const permissionForm = ref<any>({})
const showAccessForm = ref(false)
const accessForm = ref<any>({})
const highRules = computed(() => sensitiveRules.value.filter(r => String(r.sensitivityLevel).toLowerCase() === 'high').length)
const enabledPolicies = computed(() => maskingPolicies.value.filter(p => Number(p.status) === 1).length)

function loadAll() {
  loadAlgorithms()
  loadRules()
  loadMasking()
  searchAudit()
  loadPermissions()
  loadAccess()
  loadAssets()
}

onMounted(loadAll)

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

async function loadPermissions() {
  try {
    const res: any = await securityApi.getPermissions()
    permissions.value = res.data || []
  } catch (_e) { permissions.value = [] }
}

async function loadAccess() {
  try {
    const res: any = await securityApi.searchAccessRequests({
      status: accessStatus.value || undefined,
      page: 1,
      pageSize: 100
    })
    accessRequests.value = res.data?.records || []
  } catch (_e) { accessRequests.value = [] }
}

async function loadAssets() {
  try {
    const res: any = await catalogApi.searchAssets({ page: 1, pageSize: 200 })
    assets.value = res.data?.records || []
  } catch (_e) { assets.value = [] }
}

function assetName(id: number) {
  const a = assets.value.find(x => x.id === id)
  return a ? a.assetName : (id ? `资产#${id}` : '-')
}

const accessLabel = (v: any) => Number(v) === 1 ? '已批准' : Number(v) === 2 ? '已拒绝' : '待审批'

function openPermission(row?: any) {
  isEditPermission.value = !!row
  permissionForm.value = row ? { ...row } : {
    permissionCode: '', permissionName: '', subjectType: 'ROLE', subjectId: '',
    resourceType: 'TABLE', resourceId: undefined, actionType: 'SELECT', priority: 100,
    status: 1, filterCondition: '', columnMask: '', description: ''
  }
  showPermissionForm.value = true
}

async function submitPermission() {
  if (!permissionForm.value.permissionCode?.trim() || !permissionForm.value.permissionName?.trim()) {
    ElMessage.warning('权限编码和名称不能为空'); return
  }
  try {
    if (isEditPermission.value) await securityApi.updatePermission(permissionForm.value)
    else await securityApi.createPermission(permissionForm.value)
    ElMessage.success('权限策略已保存')
    showPermissionForm.value = false
    await loadPermissions()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
}

async function removePermission(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除权限策略「${row.permissionName}」？`, '删除确认', { type: 'warning' })
    await securityApi.deletePermission(row.id)
    ElMessage.success('已删除')
    await loadPermissions()
  } catch (_e) { /* 取消 */ }
}

function openAccess() {
  accessForm.value = { assetId: undefined, accessType: 'READ', accessReason: '', validityDays: 30 }
  showAccessForm.value = true
}

async function submitAccess() {
  if (!accessForm.value.assetId) { ElMessage.warning('请选择资产'); return }
  if (!accessForm.value.accessReason?.trim()) { ElMessage.warning('请填写申请理由'); return }
  try {
    await securityApi.createAccessRequest(accessForm.value)
    ElMessage.success('申请已提交')
    showAccessForm.value = false
    await loadAccess()
  } catch (e: any) { ElMessage.error(e.message || '提交失败') }
}

async function approve(row: any, approved: boolean) {
  try {
    const action = approved ? '批准' : '拒绝'
    const { value } = await ElMessageBox.prompt(`请输入${action}意见（可选）`, `${action}申请`, { inputValue: '' })
    await securityApi.approveRequest(row.id, { approved, approverId: approverId.value, comment: value || '' })
    ElMessage.success(`已${action}`)
    await loadAccess()
  } catch (_e) { /* 取消 */ }
}

</script>
