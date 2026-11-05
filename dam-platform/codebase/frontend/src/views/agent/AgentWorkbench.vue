<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">AI 工作台</h2>
        <p class="page-desc">任务 · 反馈 · 知识沉淀，AI 结果先审核再写回。</p>
      </div>
      <div class="head-actions">
        <el-button @click="loadAll"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-button type="primary" @click="chatOpen = true"><el-icon><ChatDotRound /></el-icon>AI 助手</el-button>
      </div>
    </div>

    <el-tabs v-model="tab" class="wb-tabs" @tab-change="onTabChange">
      <!-- ==================== 任务队列 ==================== -->
      <el-tab-pane label="任务队列" name="tasks">
        <div class="wb-grid">
          <div class="panel task-list-panel">
            <div class="panel-head">
              <span>任务队列</span>
              <span class="panel-sub">{{ tasks.length }} 条</span>
            </div>
            <div v-loading="loading" class="task-list">
              <div v-for="t in tasks" :key="t.id" class="task-card"
                   :class="{ active: selectedTask?.id === t.id }" @click="selectTask(t)">
                <div class="task-top">
                  <el-tag size="small" :color="typeBg(t.taskType)" :style="{ color: typeColor(t.taskType), border: 'none' }">
                    {{ typeLabel(t.taskType) }}
                  </el-tag>
                  <span class="task-time">{{ formatTime(t.createdAt) }}</span>
                </div>
                <div class="task-title" :title="t.title">{{ t.title }}</div>
                <div class="task-sub">{{ t.targetType || '任务' }} · {{ t.totalCount || 0 }} 条明细</div>
                <div class="task-foot">
                  <span :style="{ color: statusColor(t.status) }">{{ statusLabel(t.status) }}</span>
                  <div class="task-bar"><i :style="{ width: taskProgress(t) + '%', background: statusColor(t.status) }"></i></div>
                  <em>{{ taskProgress(t) }}%</em>
                </div>
              </div>
              <el-empty v-if="!loading && !tasks.length" description="暂无 AI 任务" :image-size="56" />
            </div>
          </div>

          <div class="panel detail-panel">
            <template v-if="selectedTask">
              <div class="detail-head">
                <div>
                  <h3>{{ selectedTask.title }}</h3>
                  <p>{{ typeLabel(selectedTask.taskType) }} · #{{ selectedTask.id }} · {{ formatTime(selectedTask.createdAt) }}</p>
                </div>
                <div class="detail-head-actions">
                  <el-tag :type="statusTag(selectedTask.status)">{{ statusLabel(selectedTask.status) }}</el-tag>
                  <el-button text type="danger" size="small"
                             :disabled="selectedTask.status === 'RUNNING'"
                             @click="deleteSelectedTask">删除任务</el-button>
                </div>
              </div>

              <div v-if="selectedItem" class="suggest">
                <div class="suggest-head">
                  <el-icon><MagicStick /></el-icon>
                  <b>AI 建议</b>
                  <span>置信度 {{ confidenceText(selectedItem) }}</span>
                </div>
                <div class="suggest-body">{{ draftText(selectedItem) }}</div>
              </div>
              <el-empty v-else :description="items.length ? '当前任务草稿已全部处理' : '该任务暂无待审核草稿'" :image-size="48" />

              <div class="block-title">依据</div>
              <div class="chips">
                <span class="chip blue">{{ selectedItem?.targetName || selectedTask.targetType || '任务对象' }}</span>
                <span v-if="selectedItem?.fieldName" class="chip violet">{{ selectedItem.fieldName }}</span>
                <span class="chip teal">{{ selectedTask.model || 'AI 模型' }}</span>
                <span class="chip gray">{{ items.length }} 条明细</span>
              </div>

              <div class="block-title">人工修正</div>
              <el-input v-model="manualNote" type="textarea" :rows="3"
                        placeholder="在 AI 建议基础上补充说明（仅本地备注，审核以 AI 草稿为准）" />

              <div class="detail-actions">
                <el-button type="primary" :disabled="!selectedItem" @click="reviewSelected(true)">
                  <el-icon><Check /></el-icon>确认并写入
                </el-button>
                <el-button :disabled="!selectedItem" @click="reviewSelected(false)">驳回</el-button>
                <el-button text :disabled="!selectedItem" @click="nextDraft">跳过</el-button>
                <el-button v-if="selectedItem?.itemType === 'REPORT'" text type="primary" @click="openDraftEdit">修正报表</el-button>
                <span class="draft-count">待审 {{ draftCount }} 条</span>
              </div>

              <div class="auto-check">
                <div class="auto-title"><el-icon><CircleCheck /></el-icon>自动校验</div>
                <div class="checks">
                  <span><el-icon><Check /></el-icon>目标对象已定位</span>
                  <span><el-icon><Check /></el-icon>草稿格式有效</span>
                  <span><el-icon><Check /></el-icon>审核后写回业务表</span>
                </div>
                <div class="auto-note">任务 #{{ selectedTask.id }} · 采纳/驳回会立即更新任务进度</div>
              </div>
            </template>
            <el-empty v-else description="从左侧选择一个任务" :image-size="60" />
          </div>

          <div class="right-col">
            <div class="panel">
              <div class="panel-head"><span>知识库</span><span class="panel-sub">上下文</span></div>
              <div class="kb-list">
                <div v-for="k in knowledgeItems" :key="k.name" class="kb-item" @click="openKnowledge(k)">
                  <div class="kb-icon" :style="{ color: k.color, background: k.color + '14' }">
                    <el-icon><component :is="k.icon" /></el-icon>
                  </div>
                  <div class="kb-main"><b>{{ k.name }}</b><span>{{ k.desc }}</span></div>
                  <em>{{ k.count }}</em>
                </div>
              </div>
            </div>
            <div class="panel curve-panel">
              <div class="panel-head"><span>学习曲线</span><span class="panel-sub">近期任务</span></div>
              <div ref="miniChart" class="mini-chart"></div>
              <div class="curve-foot">
                <span>采纳率 <b>{{ adoptRate }}%</b></span>
                <span class="up">实时统计</span>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- ==================== 知识库 ==================== -->
      <el-tab-pane label="知识库" name="kb">
        <div class="knowledge-grid">
          <div v-for="k in knowledgeItems" :key="k.name" class="panel knowledge-card">
            <div class="kb-icon large" :style="{ color: k.color, background: k.color + '14' }">
              <el-icon><component :is="k.icon" /></el-icon>
            </div>
            <h3>{{ k.name }}</h3>
            <p>{{ k.desc }}</p>
            <div class="knowledge-foot"><span>{{ k.count }}</span><el-button text type="primary" size="small" @click="openKnowledge(k)">查看 →</el-button></div>
          </div>
        </div>
      </el-tab-pane>

      <!-- ==================== 评估看板 ==================== -->
      <el-tab-pane label="评估看板" name="eval">
        <div class="stat-grid" style="margin-bottom: 14px;">
          <div class="stat-card"><div class="stat-label">任务完成率</div><div class="stat-value">{{ completionRate }}%</div><div class="stat-foot">{{ stats.DONE || 0 }} / {{ stats.total || 0 }}</div></div>
          <div class="stat-card"><div class="stat-label">草稿采纳率</div><div class="stat-value">{{ adoptRate }}%</div><div class="stat-foot">采纳 / 已审核</div></div>
          <div class="stat-card"><div class="stat-label">待审核</div><div class="stat-value">{{ stats.REVIEW || 0 }}</div><div class="stat-foot">任务</div></div>
          <div class="stat-card"><div class="stat-label">失败任务</div><div class="stat-value">{{ stats.FAILED || 0 }}</div><div class="stat-foot">需人工处理</div></div>
        </div>
        <div class="panel">
          <div class="panel-head"><span>近期任务采纳率</span><span class="panel-sub">按任务统计</span></div>
          <div ref="evalChart" class="eval-chart"></div>
        </div>
      </el-tab-pane>

      <!-- ==================== 反馈记录 ==================== -->
      <el-tab-pane label="反馈记录" name="feedback">
        <div class="panel">
          <div class="panel-head"><span>反馈记录</span><span class="panel-sub">最近有审核动作的任务</span></div>
          <el-table :data="feedbackRows" stripe>
            <el-table-column label="时间" width="160"><template #default="{ row }">{{ formatTime(row.time) }}</template></el-table-column>
            <el-table-column label="任务" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ row.title }}</template></el-table-column>
            <el-table-column label="类型" width="120"><template #default="{ row }">{{ typeLabel(row.taskType) }}</template></el-table-column>
            <el-table-column label="动作" width="130">
              <template #default="{ row }">
                <span class="fb-accept">采纳 {{ row.accepted }}</span>
                <span v-if="row.rejected" class="fb-reject"> / 驳回 {{ row.rejected }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="operator" label="操作人" width="120" />
          </el-table>
          <el-empty v-if="!feedbackRows.length" description="暂无审核反馈，处理任务后会出现在这里" :image-size="56" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="chatOpen" title="AI 助手" size="560px" append-to-body>
      <div class="chat-drawer"><AgentChat :hide-header="true" /></div>
    </el-drawer>

    <el-dialog v-model="draftVisible" title="人工修正报表草稿" width="760px" top="6vh">
      <el-form label-width="80px">
        <el-form-item label="报表标题"><el-input v-model="draftForm.reportName" /></el-form-item>
        <el-form-item label="SQL"><el-input v-model="draftForm.sql" type="textarea" :rows="14" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="draftVisible = false">取消</el-button>
        <el-button type="primary" :loading="draftSaving" @click="saveDraft">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { catalogApi, standardApi, metricApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, ChatDotRound, MagicStick, Check, CircleCheck, Collection, TrendCharts, Document, Files } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import AgentChat from './AgentChat.vue'

const router = useRouter()
const tab = ref('tasks')
const chatOpen = ref(false)
const draftVisible = ref(false)
const draftSaving = ref(false)
const draftForm = ref<any>({})
const loading = ref(false)
const tasks = ref<any[]>([])
const stats = ref<any>({})
const selectedTask = ref<any>(null)
const items = ref<any[]>([])
const selectedItemId = ref<number | null>(null)
const manualNote = ref('')
const knowledgeCounts = ref({ domain: 0, metric: 0, standard: 0, history: 0 })
const evalChart = ref<HTMLElement>()
const miniChart = ref<HTMLElement>()
let evalInstance: any = null
let miniInstance: any = null

const COLORS: Record<string, string> = {
  COMMENT: '#2563EB', CLASSIFY: '#D97706', LINEAGE: '#0D9488', REPORT: '#7C3AED'
}
const typeBg = (v: string) => (COLORS[v] || '#64748B') + '14'
const typeColor = (v: string) => COLORS[v] || '#64748B'
const typeLabel = (v: string) => ({ COMMENT: '中文注释', CLASSIFY: '业务分类', LINEAGE: '血缘解析', REPORT: '报表生成' } as any)[v] || v || '-'
const statusLabel = (v: string) => ({ PENDING: '待生成', RUNNING: '执行中', REVIEW: '待复核', DONE: '已完成', FAILED: '失败' } as any)[v] || v || '-'
const statusColor = (v: string) => v === 'DONE' ? '#16A34A' : v === 'REVIEW' ? '#D97706' : v === 'FAILED' ? '#DC2626' : v === 'RUNNING' ? '#2563EB' : '#94A3B8'
const statusTag = (v: string) => v === 'DONE' ? 'success' : v === 'REVIEW' ? 'warning' : v === 'FAILED' ? 'danger' : 'info'
const formatTime = (v: string) => v ? String(v).replace('T', ' ').slice(0, 16) : '-'

const selectedItem = computed(() => {
  const drafts = items.value.filter((x: any) => x.status === 'DRAFT')
  return drafts.find((x: any) => x.id === selectedItemId.value) || drafts[0] || null
})
const draftCount = computed(() => items.value.filter((x: any) => x.status === 'DRAFT').length)
const confidenceText = (item: any) => item?.confidence != null ? Number(item.confidence).toFixed(2) : '-'

const totalReviewed = computed(() => tasks.value.reduce((s, t) => s + Number(t.acceptedCount || 0) + Number(t.rejectedCount || 0), 0))
const totalAccepted = computed(() => tasks.value.reduce((s, t) => s + Number(t.acceptedCount || 0), 0))
const adoptRate = computed(() => totalReviewed.value ? Math.round(totalAccepted.value / totalReviewed.value * 100) : 0)
const completionRate = computed(() => Number(stats.value.total || 0) ? Math.round(Number(stats.value.DONE || 0) / Number(stats.value.total) * 100) : 0)

const knowledgeItems = computed(() => [
  { name: '领域词典', desc: '表名/字段名到中文、分类、等级的映射', count: knowledgeCounts.value.domain || '—', color: '#2563EB', icon: Collection },
  { name: '指标口径', desc: '费用、收入、工作量等指标的计算口径', count: knowledgeCounts.value.metric || '—', color: '#7C3AED', icon: TrendCharts },
  { name: '标准术语', desc: '数据标准与标准代码值，用于校验', count: knowledgeCounts.value.standard || '—', color: '#0D9488', icon: Document },
  { name: '历史案例', desc: '已完成并采纳的 AI 任务样本', count: knowledgeCounts.value.history || '—', color: '#D97706', icon: Files }
])

const feedbackRows = computed(() => tasks.value
  .filter(t => Number(t.acceptedCount || 0) + Number(t.rejectedCount || 0) > 0)
  .sort((a, b) => String(b.updatedAt || b.createdAt || '').localeCompare(String(a.updatedAt || a.createdAt || '')))
  .map(t => ({
    time: t.updatedAt || t.createdAt,
    title: t.title,
    taskType: t.taskType,
    accepted: t.acceptedCount || 0,
    rejected: t.rejectedCount || 0,
    operator: t.updatedBy || t.createdBy || '—'
  })))

function taskProgress(t: any) {
  const total = Number(t.totalCount || 0)
  const done = Number(t.acceptedCount || 0) + Number(t.rejectedCount || 0)
  if (t.status === 'DONE' && total === 0) return 100
  return total ? Math.min(100, Math.round(done / total * 100)) : 0
}

function draftText(item: any) {
  try {
    const d = JSON.parse(item.draftContent || '{}')
    if (item.itemType === 'CLASSIFY') return d.categoryName || d.categoryCode || item.draftContent || '-'
    if (item.itemType === 'REPORT') return d.reportName ? `已生成报表：${d.reportName}` : '已生成报表'
    const parts: string[] = []
    if (d.tableComment) parts.push('注释：' + d.tableComment)
    if (d.subjectDomain) parts.push('主题域：' + d.subjectDomain)
    if (d.securityLevel) parts.push('分级：' + d.securityLevel)
    return parts.length ? parts.join('；') : (item.draftContent || '-')
  } catch { return item.draftContent || '-' }
}

async function loadStats() {
  try { const res: any = await catalogApi.getAiTaskStats(); stats.value = res.data || {} }
  catch { stats.value = {} }
}

async function loadTasks() {
  loading.value = true
  try {
    const res: any = await catalogApi.listAiTasks({ page: 1, pageSize: 50 })
    tasks.value = res.data?.records || []
    if (selectedTask.value) {
      const current = tasks.value.find(t => t.id === selectedTask.value.id)
      if (current) selectedTask.value = { ...selectedTask.value, ...current }
    }
    if (!selectedTask.value && tasks.value.length) await selectTask(tasks.value[0])
    if (selectedTask.value && !tasks.value.some(t => t.id === selectedTask.value.id)) {
      selectedTask.value = tasks.value[0] || null
      if (selectedTask.value) await selectTask(selectedTask.value)
    }
  } catch (_e) { tasks.value = [] }
  finally { loading.value = false }
}

async function selectTask(task: any) {
  selectedTask.value = task
  manualNote.value = ''
  selectedItemId.value = null
  try {
    const [detail, itemRes]: any = await Promise.all([
      catalogApi.getAiTask(task.id),
      catalogApi.getAiTaskItems(task.id, { page: 1, pageSize: 100 })
    ])
    selectedTask.value = detail.data?.task || task
    items.value = itemRes.data?.records || []
    const firstDraft = items.value.find((x: any) => x.status === 'DRAFT')
    selectedItemId.value = firstDraft?.id || null
  } catch (e: any) {
    ElMessage.error(e.message || '加载任务失败')
    items.value = []
  }
}

function openDraftEdit() {
  if (!selectedItem.value) return
  try {
    draftForm.value = JSON.parse(selectedItem.value.draftContent || '{}')
    draftVisible.value = true
  } catch { ElMessage.error('草稿内容解析失败') }
}

async function saveDraft() {
  if (!selectedTask.value || !selectedItem.value) return
  if (!draftForm.value.sql?.trim()) { ElMessage.warning('SQL 不能为空'); return }
  draftSaving.value = true
  try {
    await catalogApi.updateAiTaskItem(selectedTask.value.id, selectedItem.value.id, JSON.stringify(draftForm.value))
    ElMessage.success('草稿已保存，可继续确认入库')
    draftVisible.value = false
    await selectTask(selectedTask.value)
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { draftSaving.value = false }
}

async function deleteSelectedTask() {
  if (!selectedTask.value) return
  try {
    await ElMessageBox.confirm(
      `确认删除任务「${selectedTask.value.title}」及其全部草稿？`,
      '删除任务',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await catalogApi.deleteAiTask(selectedTask.value.id)
    ElMessage.success('任务已删除')
    selectedTask.value = null
    items.value = []
    selectedItemId.value = null
    await loadTasks()
    await loadStats()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message || '删除失败')
  }
}

async function reviewSelected(accept: boolean) {
  if (!selectedTask.value || !selectedItem.value) return
  try {
    await catalogApi.reviewAiTaskItem(selectedTask.value.id, selectedItem.value.id, accept)
    ElMessage.success(accept ? '已采纳并写回' : '已驳回')
    await selectTask(selectedTask.value)
    await loadTasks()
    await loadStats()
    await nextTick()
    renderCharts()
  } catch (e: any) { ElMessage.error(e.message || '操作失败') }
}

function nextDraft() {
  const drafts = items.value.filter((x: any) => x.status === 'DRAFT')
  if (drafts.length <= 1) return
  const idx = drafts.findIndex(x => x.id === selectedItemId.value)
  selectedItemId.value = drafts[(idx + 1) % drafts.length].id
  manualNote.value = ''
}

async function loadKnowledge() {
  try {
    const tags: any = await catalogApi.getTags()
    knowledgeCounts.value.domain = (tags.data || []).length
  } catch { /* ignore */ }
  try {
    const res: any = await standardApi.searchStandards({ page: 1, pageSize: 1 })
    knowledgeCounts.value.standard = res.data?.total || 0
  } catch { /* ignore */ }
  try {
    const res: any = await metricApi.list({ page: 1, pageSize: 1 })
    knowledgeCounts.value.metric = res.data?.total || 0
  } catch { /* ignore */ }
  knowledgeCounts.value.history = Number(stats.value.DONE || 0)
}

async function loadAll() {
  await loadStats()
  await Promise.all([loadTasks(), loadKnowledge()])
}

function acceptanceSeries() {
  return tasks.value
    .filter(t => Number(t.acceptedCount || 0) + Number(t.rejectedCount || 0) > 0)
    .slice(0, 12)
    .reverse()
    .map(t => {
      const total = Number(t.acceptedCount || 0) + Number(t.rejectedCount || 0)
      return { name: formatTime(t.createdAt).slice(5, 10), value: total ? Math.round(Number(t.acceptedCount) / total * 100) : 0 }
    })
}

function renderCharts() {
  const series = acceptanceSeries()
  const categories = series.length ? series.map(x => x.name) : ['暂无']
  const values = series.length ? series.map(x => x.value) : [0]
  if (evalChart.value && evalChart.value.clientWidth > 0) {
    if (!evalInstance) evalInstance = echarts.init(evalChart.value)
    evalInstance.setOption({
      grid: { left: 42, right: 20, top: 20, bottom: 30 },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: categories, axisLabel: { color: '#94A3B8', fontSize: 10 } },
      yAxis: { type: 'value', max: 100, axisLabel: { color: '#94A3B8', fontSize: 10 }, splitLine: { lineStyle: { color: '#EEF2F7' } } },
      series: [{ type: 'line', data: values, smooth: true, symbolSize: 6, lineStyle: { color: '#16A34A', width: 2 }, itemStyle: { color: '#16A34A' }, areaStyle: { color: 'rgba(22,163,74,.08)' } }]
    }, true)
  }
  if (miniChart.value && miniChart.value.clientWidth > 0) {
    if (!miniInstance) miniInstance = echarts.init(miniChart.value)
    miniInstance.setOption({
      grid: { left: 4, right: 4, top: 8, bottom: 4 },
      xAxis: { type: 'category', show: false, data: categories },
      yAxis: { type: 'value', show: false, max: 100 },
      series: [{ type: 'line', data: values, smooth: true, symbol: 'none', lineStyle: { color: '#16A34A', width: 2 }, areaStyle: { color: 'rgba(22,163,74,.08)' } }]
    }, true)
    miniInstance.resize()
  }
}

function onTabChange(name: any) {
  if (name === 'eval') nextTick(() => { renderCharts(); evalInstance?.resize() })
}

function openKnowledge(k: any) {
  if (k.name === '指标口径') router.push('/metrics')
  else if (k.name === '标准术语') router.push('/standard')
  else if (k.name === '领域词典') router.push('/metadata')
  else if (k.name === '历史案例') tab.value = 'feedback'
}

onMounted(async () => {
  await loadAll()
  await nextTick()
  renderCharts()
})

onBeforeUnmount(() => {
  evalInstance?.dispose()
  miniInstance?.dispose()
})
</script>

<style scoped>
.wb-grid { display: grid; grid-template-columns: 330px minmax(0, 1fr) 290px; gap: 14px; align-items: start; }
.panel { background: var(--med-surface); border: 1px solid var(--med-border); border-radius: var(--med-radius); }
.panel-head { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px 10px; font-size: 13px; font-weight: 600; color: var(--med-text); }
.panel-sub { font-size: 10.5px; color: var(--med-text-light); font-weight: 400; }
.task-list-panel { min-height: 580px; }
.task-list { max-height: calc(100vh - 290px); overflow-y: auto; padding: 0 10px 12px; }
.task-card { border: 1px solid var(--med-border); border-left: 3px solid transparent; border-radius: 9px; padding: 11px 12px; margin-bottom: 8px; cursor: pointer; background: #fff; transition: all .15s; }
.task-card:hover { border-color: #C7D7F5; }
.task-card.active { border-color: var(--med-primary); border-left-color: var(--med-primary); background: var(--med-primary-lighter); }
.task-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.task-time { font-size: 9.5px; color: var(--med-text-light); }
.task-title { font-size: 12.5px; font-weight: 600; color: var(--med-text); margin: 7px 0 3px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.task-sub { font-size: 10px; color: var(--med-text-light); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.task-foot { display: flex; align-items: center; gap: 7px; margin-top: 9px; font-size: 10px; }
.task-foot span { white-space: nowrap; }
.task-bar { flex: 1; height: 4px; background: #EEF2F7; border-radius: 3px; overflow: hidden; }
.task-bar i { display: block; height: 100%; border-radius: 3px; }
.task-foot em { font-style: normal; font-size: 9.5px; color: var(--med-text-light); }
.detail-panel { min-height: 580px; padding-bottom: 14px; }
.detail-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 16px 18px 10px; }
.detail-head h3 { margin: 0; font-size: 15px; color: var(--med-text); }
.detail-head p { margin: 4px 0 0; font-size: 10.5px; color: var(--med-text-light); }
.detail-head-actions { display: flex; align-items: center; gap: 8px; flex: 0 0 auto; }
.suggest { margin: 4px 18px 0; background: #F0FDF4; border: 1px solid #BBF7D0; border-radius: 9px; padding: 12px 14px; }
.suggest-head { display: flex; align-items: center; gap: 6px; color: #15803D; font-size: 11px; }
.suggest-head b { font-weight: 600; }
.suggest-head span { margin-left: auto; color: #16A34A; font-size: 10px; }
.suggest-body { margin-top: 7px; color: #166534; font-size: 12.5px; line-height: 1.7; }
.block-title { margin: 14px 18px 7px; font-size: 11px; font-weight: 600; color: var(--med-text-secondary); }
.chips { display: flex; gap: 6px; flex-wrap: wrap; padding: 0 18px; }
.chip { font-size: 10px; border-radius: 5px; padding: 3px 8px; }
.chip.blue { color: #2563EB; background: #EFF6FF; }
.chip.violet { color: #7C3AED; background: #F5F3FF; }
.chip.teal { color: #0D9488; background: #F0FDFA; }
.chip.gray { color: #64748B; background: #F1F5F9; }
.detail-panel :deep(.el-textarea) { padding: 0 18px; }
.detail-actions { display: flex; align-items: center; gap: 8px; padding: 14px 18px 0; }
.draft-count { margin-left: auto; font-size: 10.5px; color: var(--med-text-light); }
.auto-check { margin: 14px 18px 0; background: #F8FAFD; border-radius: 8px; padding: 10px 12px; }
.auto-title { display: flex; align-items: center; gap: 5px; font-size: 10.5px; font-weight: 600; color: var(--med-text-secondary); }
.checks { display: flex; flex-wrap: wrap; gap: 8px 16px; margin-top: 7px; color: #16A34A; font-size: 10px; }
.checks span { display: inline-flex; align-items: center; gap: 4px; }
.auto-note { margin-top: 6px; font-size: 9.5px; color: var(--med-text-light); }
.right-col { display: flex; flex-direction: column; gap: 14px; }
.kb-list { padding: 0 10px 12px; }
.kb-item { display: flex; align-items: center; gap: 9px; padding: 9px; border-radius: 8px; background: #F8FAFD; margin-bottom: 7px; cursor: pointer; }
.kb-item:hover { background: #F1F5F9; }
.kb-icon { width: 30px; height: 30px; border-radius: 8px; display: grid; place-items: center; flex: 0 0 30px; font-size: 15px; }
.kb-icon.large { width: 40px; height: 40px; font-size: 19px; margin-bottom: 10px; }
.kb-main { flex: 1; min-width: 0; }
.kb-main b { display: block; font-size: 11.5px; font-weight: 600; color: var(--med-text); }
.kb-main span { display: block; font-size: 9.5px; color: var(--med-text-light); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kb-item em { font-style: normal; font-size: 10px; color: var(--med-text-light); }
.curve-panel { padding-bottom: 12px; }
.mini-chart { height: 88px; margin: 0 10px; }
.curve-foot { display: flex; align-items: center; gap: 8px; padding: 0 14px; font-size: 10px; color: var(--med-text-light); }
.curve-foot b { color: #16A34A; font-size: 15px; }
.curve-foot .up { color: #16A34A; }
.knowledge-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 14px; }
.knowledge-card { padding: 16px; }
.knowledge-card h3 { margin: 0 0 5px; font-size: 13px; color: var(--med-text); }
.knowledge-card p { margin: 0; font-size: 10.5px; line-height: 1.7; color: var(--med-text-light); min-height: 36px; }
.knowledge-foot { display: flex; align-items: center; justify-content: space-between; margin-top: 10px; padding-top: 9px; border-top: 1px solid var(--med-border-light); font-size: 11px; color: var(--med-text-secondary); }
.eval-chart { height: 320px; padding: 0 10px 10px; }
.fb-accept { color: #16A34A; }
.fb-reject { color: #DC2626; }
.chat-drawer { height: calc(100vh - 120px); }
.wb-tabs :deep(.el-tabs__header) { margin-bottom: 14px; }
@media (max-width: 1450px) { .wb-grid { grid-template-columns: 300px minmax(0, 1fr); } .right-col { display: none; } }
</style>
