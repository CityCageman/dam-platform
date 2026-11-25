<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">生命周期管理</h2>
        <p class="page-desc">维护存储分层策略、执行归档/销毁，并管理数据销毁审批。</p>
      </div>
      <div class="head-actions">
        <el-button @click="loadAll"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-button :loading="executing" @click="executeTiering"><el-icon><VideoPlay /></el-icon>执行分层</el-button>
        <el-button type="primary" @click="openCreatePolicy"><el-icon><Plus /></el-icon>新建策略</el-button>
      </div>
    </div>

    <div class="stat-grid" style="margin-bottom: 14px;">
      <div class="stat-card">
        <div class="stat-label">策略总数</div>
        <div class="stat-value">{{ policies.length }}</div>
        <div class="stat-foot">启用 {{ enabledPolicies }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">资产分层</div>
        <div class="stat-value">{{ tier.total || 0 }}</div>
        <div class="stat-foot">热 {{ tier.hot || 0 }} · 温 {{ tier.warm || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">冷/过期</div>
        <div class="stat-value">{{ (tier.cold || 0) + (tier.expired || 0) }}</div>
        <div class="stat-foot">冷 {{ tier.cold || 0 }} · 过期 {{ tier.expired || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">待审批销毁</div>
        <div class="stat-value">{{ pendingDestructions }}</div>
        <div class="stat-foot">销毁记录 {{ destructions.length }}</div>
      </div>
    </div>

    <div class="panel">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="存储策略" name="policies">
          <div class="tab-toolbar">
            <span class="muted">按资产年龄与策略判断热/温/冷/过期</span>
            <el-button size="small" type="primary" @click="openCreatePolicy"><el-icon><Plus /></el-icon>新建策略</el-button>
          </div>
          <el-table :data="policies" stripe>
            <el-table-column prop="policyCode" label="策略编码" width="140" />
            <el-table-column prop="policyName" label="策略名称" min-width="160" />
            <el-table-column label="资产类型" width="110">
              <template #default="{ row }">{{ targetLabel(row.targetType) }}</template>
            </el-table-column>
            <el-table-column prop="hotDays" label="热(天)" width="80" />
            <el-table-column prop="coldDays" label="冷(天)" width="80" />
            <el-table-column prop="archiveDays" label="归档(天)" width="90" />
            <el-table-column label="自动执行" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.autoExecute ? 'success' : 'info'">{{ row.autoExecute ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="Number(row.status) === 1 ? 'success' : 'info'">{{ Number(row.status) === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="openEditPolicy(row)">编辑</el-button>
                <el-button text size="small" @click="openCandidates(row)">命中预览</el-button>
                <el-button text size="small" @click="togglePolicy(row)">{{ Number(row.status) === 1 ? '停用' : '启用' }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!policies.length" description="暂无策略" :image-size="60" />
        </el-tab-pane>

        <el-tab-pane label="销毁记录" name="destructions">
          <div class="tab-toolbar">
            <span class="muted">销毁申请需审批通过后才可执行</span>
            <el-button size="small" type="primary" @click="openCreateDestruction"><el-icon><Plus /></el-icon>新建销毁申请</el-button>
          </div>
          <el-table :data="destructions" stripe>
            <el-table-column prop="destructionNo" label="单号" width="150" />
            <el-table-column prop="targetName" label="目标" min-width="150" show-overflow-tooltip />
            <el-table-column prop="destructionReason" label="原因" min-width="180" show-overflow-tooltip />
            <el-table-column prop="destructionMethod" label="方式" width="110" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="Number(row.status) === 1 ? 'success' : Number(row.status) === 2 ? 'danger' : 'warning'">
                  {{ destructionLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="申请时间" width="160">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <template v-if="Number(row.status) === 0">
                  <el-button text type="success" size="small" @click="approve(row, 1)">批准</el-button>
                  <el-button text type="danger" size="small" @click="approve(row, 2)">拒绝</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!destructions.length" description="暂无销毁记录" :image-size="60" />
        </el-tab-pane>

        <el-tab-pane label="执行记录" name="executions">
          <div class="tab-toolbar">
            <el-select v-model="execAction" placeholder="全部动作" clearable style="width: 130px;" @change="loadExecutions">
              <el-option label="归档" value="archive" /><el-option label="销毁" value="destroy" /><el-option label="通知" value="notify" />
            </el-select>
            <el-button @click="loadExecutions">查询</el-button>
            <div style="flex: 1;" />
            <span class="muted">共 {{ executions.length }} 条</span>
          </div>
          <el-table :data="executions" stripe>
            <el-table-column prop="policyName" label="策略" width="150" show-overflow-tooltip />
            <el-table-column prop="assetName" label="资产" min-width="160" show-overflow-tooltip />
            <el-table-column prop="tier" label="分层" width="100" />
            <el-table-column label="动作" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.action === 'archive' ? 'warning' : row.action === 'destroy' ? 'danger' : 'info'">
                  {{ actionLabel(row.action) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" min-width="220" show-overflow-tooltip />
            <el-table-column label="执行时间" width="160">
              <template #default="{ row }">{{ formatTime(row.executedAt) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!executions.length" description="暂无执行记录；执行分层后会写入" :image-size="56" />
        </el-tab-pane>

        <el-tab-pane label="分层分布" name="tiering">
          <div class="tier-grid">
            <div class="tier-card hot"><b>{{ tier.hot || 0 }}</b><span>热数据</span></div>
            <div class="tier-card warm"><b>{{ tier.warm || 0 }}</b><span>温数据</span></div>
            <div class="tier-card cold"><b>{{ tier.cold || 0 }}</b><span>冷数据</span></div>
            <div class="tier-card expired"><b>{{ tier.expired || 0 }}</b><span>过期</span></div>
            <div class="tier-card total"><b>{{ tier.total || 0 }}</b><span>资产总数</span></div>
          </div>
          <div class="tab-toolbar" style="margin-top: 16px;">
            <span class="muted">执行启用的策略：归档、销毁、通知；执行前请确认销毁策略。</span>
            <el-button type="warning" :loading="executing" @click="executeTiering">立即执行</el-button>
          </div>
          <el-alert v-if="lastExecute" :closable="false" type="success" show-icon
                    :title="`上次执行：归档 ${lastExecute.archived || 0}，销毁 ${lastExecute.destroyed || 0}，通知 ${lastExecute.notified || 0}`" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 策略 -->
    <el-dialog v-model="showPolicyForm" :title="isEditPolicy ? '编辑策略' : '新建策略'" width="620px" top="12vh">
      <el-form :model="policyForm" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="策略编码" required><el-input v-model="policyForm.policyCode" placeholder="如：HOT_90" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="策略名称" required><el-input v-model="policyForm.policyName" placeholder="如：默认分层策略" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="资产类型">
              <el-select v-model="policyForm.targetType" style="width: 100%;">
                <el-option label="数据库表" value="TABLE" /><el-option label="文件" value="FILE" />
                <el-option label="API" value="API" /><el-option label="报表" value="REPORT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="自动执行"><el-switch v-model="policyForm.autoExecute" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="热数据(天)"><el-input-number v-model="policyForm.hotDays" :min="1" style="width: 100%;" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="冷数据(天)"><el-input-number v-model="policyForm.coldDays" :min="1" style="width: 100%;" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="归档(天)"><el-input-number v-model="policyForm.archiveDays" :min="1" style="width: 100%;" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="描述"><el-input v-model="policyForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="policyForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPolicyForm = false">取消</el-button>
        <el-button type="primary" @click="submitPolicy">保存</el-button>
      </template>
    </el-dialog>

    <!-- 销毁申请 -->
    <el-dialog v-model="showDestructionForm" title="新建销毁申请" width="560px" top="16vh">
      <el-form :model="destructionForm" label-width="100px">
        <el-form-item label="目标类型">
          <el-select v-model="destructionForm.targetType" style="width: 100%;">
            <el-option label="数据库表" value="TABLE" /><el-option label="文件" value="FILE" /><el-option label="API" value="API" /><el-option label="报表" value="REPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标名称" required><el-input v-model="destructionForm.targetName" placeholder="表名/文件名" /></el-form-item>
        <el-form-item label="目标ID"><el-input-number v-model="destructionForm.targetId" :min="0" style="width: 100%;" /></el-form-item>
        <el-form-item label="销毁原因" required><el-input v-model="destructionForm.destructionReason" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="销毁方式">
          <el-select v-model="destructionForm.destructionMethod" style="width: 100%;">
            <el-option label="物理删除" value="PHYSICAL" /><el-option label="逻辑删除" value="LOGICAL" /><el-option label="匿名化" value="ANONYMIZE" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="destructionForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDestructionForm = false">取消</el-button>
        <el-button type="primary" @click="submitDestruction">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCandidates" :title="`命中预览：${candidatePolicy?.policyName || ''}`" width="760px" top="10vh">
      <el-alert :closable="false" type="info" show-icon
                :title="`命中 ${candidates.length} 个资产；归档/销毁/通知动作已按策略规则计算，执行前请确认。`"
                style="margin-bottom: 12px;" />
      <el-table :data="candidates" stripe max-height="480">
        <el-table-column prop="assetName" label="资产" min-width="160" show-overflow-tooltip />
        <el-table-column prop="tier" label="分层" width="110" />
        <el-table-column label="动作" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.action === 'archive' ? 'warning' : row.action === 'destroy' ? 'danger' : 'info'">
              {{ actionLabel(row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ageDays" label="已创建(天)" width="100" />
        <el-table-column prop="reason" label="判定原因" min-width="240" show-overflow-tooltip />
      </el-table>
      <el-empty v-if="!candidates.length" description="按当前策略没有命中资产" :image-size="56" />
      <template #footer>
        <el-button @click="showCandidates = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { lifecycleApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, VideoPlay } from '@element-plus/icons-vue'

const activeTab = ref('policies')
const policies = ref<any[]>([])
const destructions = ref<any[]>([])
const tier = ref<any>({})
const executing = ref(false)
const lastExecute = ref<any>(null)
const showPolicyForm = ref(false)
const isEditPolicy = ref(false)
const policyForm = ref<any>({})
const showDestructionForm = ref(false)
const destructionForm = ref<any>({})
const executions = ref<any[]>([])
const execAction = ref('')
const showCandidates = ref(false)
const candidatePolicy = ref<any>(null)
const candidates = ref<any[]>([])

const enabledPolicies = computed(() => policies.value.filter(p => Number(p.status) === 1).length)
const pendingDestructions = computed(() => destructions.value.filter(d => Number(d.status) === 0).length)

const targetLabel = (v: string) => ({ TABLE: '数据库表', FILE: '文件', API: 'API', REPORT: '报表' } as any)[v] || v || '-'
const destructionLabel = (v: any) => Number(v) === 1 ? '已批准' : Number(v) === 2 ? '已拒绝' : '待审批'
const formatTime = (v: string) => v ? String(v).replace('T', ' ').slice(0, 16) : '-'

async function loadAll() {
  await Promise.all([loadPolicies(), loadDestructions(), loadTier(), loadExecutions()])
}

async function loadPolicies() {
  try {
    const res: any = await lifecycleApi.getPolicies()
    policies.value = res.data || []
  } catch (e: any) { ElMessage.error(e.message || '加载策略失败') }
}

async function loadDestructions() {
  try {
    const res: any = await lifecycleApi.getDestructionRecords()
    destructions.value = res.data || []
  } catch (e: any) { ElMessage.error(e.message || '加载销毁记录失败') }
}

async function loadTier() {
  try {
    const res: any = await lifecycleApi.getTierDistribution()
    tier.value = res.data || {}
  } catch (_e) { tier.value = {} }
}

function openCreatePolicy() {
  isEditPolicy.value = false
  policyForm.value = { policyCode: '', policyName: '', targetType: 'TABLE', hotDays: 90, coldDays: 365, archiveDays: 730, autoExecute: true, status: 1, description: '' }
  showPolicyForm.value = true
}

function openEditPolicy(row: any) {
  isEditPolicy.value = true
  policyForm.value = { ...row }
  showPolicyForm.value = true
}

async function submitPolicy() {
  if (!policyForm.value.policyCode?.trim() || !policyForm.value.policyName?.trim()) {
    ElMessage.warning('策略编码和名称不能为空'); return
  }
  try {
    if (isEditPolicy.value) await lifecycleApi.updatePolicy(policyForm.value)
    else await lifecycleApi.createPolicy(policyForm.value)
    ElMessage.success('保存成功')
    showPolicyForm.value = false
    await loadPolicies()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
}

async function togglePolicy(row: any) {
  try {
    await lifecycleApi.updatePolicy({ ...row, status: Number(row.status) === 1 ? 0 : 1 })
    ElMessage.success('状态已更新')
    await loadPolicies()
  } catch (e: any) { ElMessage.error(e.message || '更新失败') }
}

function openCreateDestruction() {
  destructionForm.value = { targetType: 'TABLE', targetName: '', targetId: undefined, destructionReason: '', destructionMethod: 'LOGICAL', remark: '' }
  showDestructionForm.value = true
}

async function submitDestruction() {
  if (!destructionForm.value.targetName?.trim() || !destructionForm.value.destructionReason?.trim()) {
    ElMessage.warning('目标名称和销毁原因不能为空'); return
  }
  try {
    await lifecycleApi.createDestruction(destructionForm.value)
    ElMessage.success('销毁申请已提交')
    showDestructionForm.value = false
    await loadDestructions()
  } catch (e: any) { ElMessage.error(e.message || '提交失败') }
}

async function approve(row: any, status: number) {
  try {
    const action = status === 1 ? '批准' : '拒绝'
    const { value } = await ElMessageBox.prompt(`请输入${action}意见（可选）`, `${action}销毁申请`, { inputValue: '' })
    await lifecycleApi.approveDestruction(row.id, { status, remark: value || '' })
    ElMessage.success(`已${action}`)
    await loadDestructions()
  } catch (_e) { /* 取消 */ }
}

const actionLabel = (v: string) => ({ archive: '归档', destroy: '销毁', notify: '通知' } as any)[v] || v || '-'

async function loadExecutions() {
  try {
    const res: any = await lifecycleApi.getExecutions({ action: execAction.value || undefined, limit: 300 })
    executions.value = res.data || []
  } catch (_e) { executions.value = [] }
}

async function openCandidates(row: any) {
  try {
    candidatePolicy.value = row
    const res: any = await lifecycleApi.getPolicyCandidates(row.id)
    candidates.value = res.data?.rows || []
    showCandidates.value = true
  } catch (e: any) { ElMessage.error(e.message || '加载命中资产失败') }
}

async function executeTiering() {
  try {
    await ElMessageBox.confirm('执行会按启用策略归档/销毁资产，确认继续？', '执行分层', { type: 'warning' })
    executing.value = true
    const res: any = await lifecycleApi.executeTiering()
    lastExecute.value = res.data || {}
    ElMessage.success(`执行完成：归档 ${res.data?.archived || 0}，销毁 ${res.data?.destroyed || 0}，通知 ${res.data?.notified || 0}`)
    await loadAll()
    await loadExecutions()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message || '执行失败')
  } finally { executing.value = false }
}

onMounted(loadAll)
</script>

<style scoped>
.tab-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.muted { color: var(--med-text-light); font-size: 12px; }
.tier-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
.tier-card { border-radius: 12px; padding: 18px; color: #fff; display: flex; flex-direction: column; gap: 6px; }
.tier-card b { font-size: 26px; }
.tier-card span { font-size: 12px; opacity: .9; }
.tier-card.hot { background: linear-gradient(135deg, #EF4444, #F97316); }
.tier-card.warm { background: linear-gradient(135deg, #F59E0B, #EAB308); }
.tier-card.cold { background: linear-gradient(135deg, #2563EB, #0EA5E9); }
.tier-card.expired { background: linear-gradient(135deg, #64748B, #334155); }
.tier-card.total { background: linear-gradient(135deg, #7C3AED, #2563EB); }
@media (max-width: 900px) { .tier-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
