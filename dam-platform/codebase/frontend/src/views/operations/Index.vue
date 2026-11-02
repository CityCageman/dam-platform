<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">工单中心</h2>
        <p class="page-desc">处理数据申请、质量问题与变更工单，跟踪 SLA、指派处理人并记录处理过程。</p>
      </div>
      <div class="head-actions">
        <el-button @click="loadAll"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新建工单</el-button>
      </div>
    </div>

    <div class="stat-grid" style="margin-bottom: 14px;">
      <div class="stat-card">
        <div class="stat-label">工单总数</div>
        <div class="stat-value">{{ overview.totalTickets ?? total }}</div>
        <div class="stat-foot">全部类型</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">待处理</div>
        <div class="stat-value" style="color: #D97706;">{{ overview.pendingTickets ?? 0 }}</div>
        <div class="stat-foot">需要尽快响应</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已完成</div>
        <div class="stat-value" style="color: #16A34A;">{{ overview.completedTickets ?? 0 }}</div>
        <div class="stat-foot">已闭环</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">活跃用户</div>
        <div class="stat-value">{{ overview.activeUsers ?? 0 }}</div>
        <div class="stat-foot">近 30 天</div>
      </div>
    </div>

    <div class="panel">
      <div class="ticket-filter">
        <el-select v-model="ticketType" placeholder="工单类型" clearable style="width: 150px;" @change="searchTickets">
          <el-option v-for="t in TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-select v-model="ticketStatus" placeholder="状态" clearable style="width: 130px;" @change="searchTickets">
          <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" @click="searchTickets">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <div style="flex: 1;" />
        <span class="muted">共 {{ total }} 条</span>
      </div>

      <el-table :data="tickets" stripe v-loading="loading" style="cursor: pointer;" @row-click="openTicket">
        <el-table-column prop="ticketNo" label="编号" width="140" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.ticketType)">{{ typeLabel(row.ticketType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="优先级" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.priority === 'high' ? 'danger' : row.priority === 'medium' ? 'warning' : 'info'">
              {{ priorityLabel(row.priority) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assigneeId" label="处理人" width="90">
          <template #default="{ row }">{{ row.assigneeId || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click.stop="openTicket(row)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !tickets.length" description="暂无工单" :image-size="60" />
      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total"
                       layout="total, prev, pager, next" @change="loadTickets" />
      </div>
    </div>

    <!-- 工单详情 -->
    <el-drawer v-model="showDetail" :title="current?.title || '工单详情'" size="58%" append-to-body>
      <template v-if="current">
        <div class="detail-head">
          <div>
            <b>{{ current.ticketNo }}</b>
            <el-tag size="small" :type="statusTag(current.status)" style="margin-left: 8px;">{{ statusLabel(current.status) }}</el-tag>
            <el-tag size="small" :type="typeTag(current.ticketType)" style="margin-left: 6px;">{{ typeLabel(current.ticketType) }}</el-tag>
          </div>
          <div class="action-row">
            <el-button v-if="Number(current.status) === 0" size="small" type="primary" @click="changeStatus('processing')">开始处理</el-button>
            <el-button v-if="Number(current.status) === 1" size="small" type="success" @click="changeStatus('completed')">完成</el-button>
            <el-button v-if="Number(current.status) < 2" size="small" type="danger" plain @click="changeStatus('rejected')">驳回</el-button>
          </div>
        </div>

        <el-descriptions :column="3" border size="small" style="margin-bottom: 12px;">
          <el-descriptions-item label="类型">{{ typeLabel(current.ticketType) }}</el-descriptions-item>
          <el-descriptions-item label="优先级">{{ priorityLabel(current.priority) }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ current.applicantId || current.createdBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="处理人">{{ current.assigneeId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="期望完成">{{ formatTime(current.expectedCompleteTime) }}</el-descriptions-item>
          <el-descriptions-item label="实际完成">{{ formatTime(current.actualCompleteTime) }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="3">{{ current.description || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="sla-row">
          <div class="sla-card">
            <span class="muted">SLA 状态</span>
            <b :style="{ color: sla?.isOverdue ? '#DC2626' : '#16A34A' }">{{ sla?.slaStatus || '未设置' }}</b>
          </div>
          <div class="sla-card">
            <span class="muted">截止时间</span>
            <b>{{ formatTime(sla?.slaDeadline) }}</b>
          </div>
          <div class="assign-box">
            <el-input-number v-model="assigneeId" :min="1" placeholder="处理人ID" style="width: 130px;" />
            <el-button size="small" @click="assignTicket">指派</el-button>
          </div>
        </div>

        <el-divider content-position="left">处理记录</el-divider>
        <div class="comment-list">
          <div v-for="c in comments" :key="c.id" class="comment-item">
            <div class="comment-meta">
              <b>{{ c.commenterId || c.createdBy || '系统' }}</b>
              <span class="muted">{{ formatTime(c.createdAt) }}</span>
            </div>
            <div class="comment-content">{{ c.content }}</div>
          </div>
          <el-empty v-if="!comments.length" description="暂无处理记录" :image-size="50" />
        </div>
        <div class="comment-input">
          <el-input v-model="commentText" type="textarea" :rows="2" placeholder="填写处理说明..." maxlength="500" show-word-limit />
          <el-button type="primary" :disabled="!commentText.trim()" @click="addComment">提交</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 新建工单 -->
    <el-dialog v-model="showCreate" title="新建工单" width="600px" top="14vh">
      <el-form :model="form" label-width="100px">
        <el-form-item label="工单类型">
          <el-select v-model="form.ticketType" style="width: 100%;">
            <el-option v-for="t in TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required><el-input v-model="form.title" placeholder="简要描述问题/申请" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority" style="width: 100%;">
            <el-option label="高" value="high" /><el-option label="中" value="medium" /><el-option label="低" value="low" />
          </el-select>
        </el-form-item>
        <el-form-item label="期望完成">
          <el-date-picker v-model="form.expectedCompleteTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                          placeholder="可选" style="width: 100%;" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="submitTicket">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { operationsApi } from '@/api'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'

const TYPE_OPTIONS = [
  { value: 'data_access', label: '数据申请' },
  { value: 'quality_issue', label: '质量问题' },
  { value: 'change_request', label: '变更申请' }
]
const STATUS_OPTIONS = [
  { value: 'pending', label: '待处理' },
  { value: 'processing', label: '处理中' },
  { value: 'completed', label: '已完成' },
  { value: 'rejected', label: '已驳回' }
]

const tickets = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const ticketType = ref('')
const ticketStatus = ref('')
const overview = ref<any>({})

const showDetail = ref(false)
const current = ref<any>(null)
const comments = ref<any[]>([])
const commentText = ref('')
const sla = ref<any>(null)
const assigneeId = ref<number>()

const showCreate = ref(false)
const form = ref<any>({ ticketType: 'data_access', priority: 'medium' })

const typeLabel = (v: string) => TYPE_OPTIONS.find(t => t.value === v)?.label || v || '-'
const typeTag = (v: string) => v === 'data_access' ? 'primary' : v === 'quality_issue' ? 'warning' : 'info'
const priorityLabel = (v: string) => v === 'high' ? '高' : v === 'medium' ? '中' : '低'
const statusLabel = (v: any) => ['待处理', '处理中', '已完成', '已驳回'][Number(v)] || v
const statusTag = (v: any) => Number(v) === 2 ? 'success' : Number(v) === 3 ? 'danger' : Number(v) === 1 ? 'primary' : 'warning'
const formatTime = (v: string) => v ? String(v).replace('T', ' ').slice(0, 16) : '-'

async function loadAll() {
  await Promise.all([loadTickets(), loadDashboard()])
}

async function loadDashboard() {
  try {
    const res: any = await operationsApi.getDashboard()
    overview.value = res.data?.overview || {}
  } catch (_e) { overview.value = {} }
}

async function loadTickets() {
  loading.value = true
  try {
    const res: any = await operationsApi.searchTickets({
      ticketType: ticketType.value || undefined,
      status: ticketStatus.value || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    tickets.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e: any) { ElMessage.error(e.message || '加载工单失败') }
  finally { loading.value = false }
}

function searchTickets() { page.value = 1; loadTickets() }
function resetFilters() { ticketType.value = ''; ticketStatus.value = ''; page.value = 1; loadTickets() }

function openCreate() {
  form.value = { ticketType: 'data_access', title: '', description: '', priority: 'medium', expectedCompleteTime: undefined }
  showCreate.value = true
}

async function submitTicket() {
  if (!form.value.title?.trim()) { ElMessage.warning('请输入标题'); return }
  try {
    const res: any = await operationsApi.createTicket(form.value)
    ElMessage.success('工单已创建')
    showCreate.value = false
    await loadAll()
    if (res.data) await openTicket(res.data)
  } catch (e: any) { ElMessage.error(e.message || '创建失败') }
}

async function openTicket(row: any) {
  current.value = { ...row }
  commentText.value = ''
  assigneeId.value = row.assigneeId || undefined
  comments.value = []
  sla.value = null
  showDetail.value = true
  try {
    const [c, s] = await Promise.all([
      operationsApi.getTicketComments(row.id).catch(() => ({ data: [] })),
      operationsApi.getTicketSla(row.id).catch(() => ({ data: null }))
    ])
    comments.value = c.data || []
    sla.value = s.data
  } catch (_e) { /* ignore */ }
}

async function changeStatus(status: string) {
  if (!current.value) return
  try {
    const res: any = await operationsApi.updateTicketStatus(current.value.id, { status, comment: '' })
    current.value = res.data || { ...current.value, status }
    ElMessage.success('状态已更新')
    await loadAll()
    await refreshSla()
  } catch (e: any) { ElMessage.error(e.message || '更新失败') }
}

async function assignTicket() {
  if (!current.value) return
  if (!assigneeId.value) { ElMessage.warning('请输入处理人ID'); return }
  try {
    const res: any = await operationsApi.assignTicket(current.value.id, { assigneeId: assigneeId.value })
    current.value = res.data || current.value
    ElMessage.success('已指派')
    await loadTickets()
  } catch (e: any) { ElMessage.error(e.message || '指派失败') }
}

async function addComment() {
  if (!current.value || !commentText.value.trim()) return
  try {
    await operationsApi.addComment(current.value.id, { content: commentText.value.trim() })
    commentText.value = ''
    const res: any = await operationsApi.getTicketComments(current.value.id)
    comments.value = res.data || []
    ElMessage.success('已提交')
  } catch (e: any) { ElMessage.error(e.message || '提交失败') }
}

async function refreshSla() {
  if (!current.value) return
  try {
    const res: any = await operationsApi.getTicketSla(current.value.id)
    sla.value = res.data
  } catch (_e) { /* ignore */ }
}

onMounted(loadAll)
</script>

<style scoped>
.ticket-filter { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.muted { color: var(--med-text-light); font-size: 12px; }
.detail-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 12px; }
.action-row { display: flex; gap: 8px; }
.sla-row { display: flex; gap: 12px; align-items: stretch; margin-bottom: 12px; flex-wrap: wrap; }
.sla-card { flex: 1; min-width: 160px; background: #F8FAFD; border: 1px solid var(--med-border); border-radius: 10px; padding: 10px 12px; display: flex; flex-direction: column; gap: 4px; }
.sla-card b { font-size: 13px; }
.assign-box { display: flex; align-items: center; gap: 8px; }
.comment-list { max-height: 260px; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; }
.comment-item { background: #F8FAFD; border-radius: 9px; padding: 10px 12px; }
.comment-meta { display: flex; justify-content: space-between; font-size: 11px; color: var(--med-text-secondary); margin-bottom: 4px; }
.comment-content { font-size: 12.5px; color: var(--med-text); white-space: pre-wrap; }
.comment-input { display: flex; gap: 8px; align-items: flex-end; margin-top: 12px; }
.comment-input .el-textarea { flex: 1; }
</style>
