<template>
  <div class="agent-chat">
    <div v-if="!hideHeader" class="chat-header">
      <div>
        <div class="chat-title">AI 助手</div>
        <div class="chat-subtitle">基于大模型的智能问答</div>
      </div>
      <el-button v-if="sending" size="small" @click="stop">停止生成</el-button>
    </div>

    <!-- 报表模式工具栏 -->
    <div class="report-toolbar">
      <el-switch v-model="reportMode" size="small" active-text="报表模式" />
      <el-select
        v-if="reportMode"
        v-model="dataSourceId"
        placeholder="选择数据源"
        size="small"
        clearable
        filterable
        style="width: 220px;"
      >
        <el-option v-for="ds in dataSources" :key="ds.id" :label="ds.dsName" :value="ds.id" />
      </el-select>
    </div>

    <div ref="listRef" class="chat-list">
      <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
        <div class="bubble">
          <template v-if="m.role === 'user'">
            <div class="content-text">{{ m.content }}</div>
          </template>

          <template v-else>
            <!-- 思考过程：独立区块、默认展开、可收起 -->
            <div v-if="m.reasoning" class="part-block reasoning-part">
              <div class="part-head" @click="toggleReasoning(i)">
                <span class="part-name"><span class="part-dot reasoning-dot"></span>思考过程</span>
                <span class="part-toggle">{{ reasoningOpen[i] ? '收起' : '展开' }}</span>
              </div>
              <div v-show="reasoningOpen[i]" class="reasoning-box">{{ m.reasoning }}</div>
            </div>

            <!-- 最终结论：独立区块，始终带“结论”标识 -->
            <div v-if="m.content" class="part-block answer-part">
              <div class="part-name"><span class="part-dot answer-dot"></span>结论</div>
              <div class="content-text">{{ m.content }}</div>
            </div>
          </template>

          <!-- 报表结果卡片 -->
          <div v-if="m.role === 'assistant' && m.report" class="report-card">
            <div v-if="m.report.status === 'generating'" class="report-loading">
              <div class="report-loading-line">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span>{{ m.report.taskId ? `报表任务 #${m.report.taskId} 生成中` : '正在创建报表任务...' }}</span>
                <b>{{ m.report.progress || 0 }}%</b>
              </div>
              <el-progress :percentage="m.report.progress || 0" :show-text="false" :stroke-width="4" />
            </div>

            <div v-if="m.report.reportId" class="report-registered">
              <el-icon><CircleCheck /></el-icon>
              <span>已登记到报表体系：<b>{{ m.report.reportName }}</b></span>
              <el-button text type="primary" size="small" @click="goReportCenter">查看</el-button>
            </div>

            <template v-if="m.report.sql">
              <div v-if="m.report.tablesUsed && m.report.tablesUsed.length" class="report-tables">
                引用表：
                <el-tag v-for="t in m.report.tablesUsed" :key="t.tableName" size="small" class="report-table-tag">
                  {{ t.tableComment ? `${t.tableName}（${t.tableComment}）` : t.tableName }}
                </el-tag>
              </div>
              <div class="report-sql-label">生成的 SQL（执行前请确认）</div>
              <pre class="report-sql">{{ m.report.sql }}</pre>
              <div class="report-actions">
                <el-button
                  size="small"
                  type="primary"
                  :loading="m.report.status === 'executing'"
                  :disabled="m.report.status === 'executing'"
                  @click="executeReport(m)"
                >
                  执行查询
                </el-button>
                <el-button size="small" @click="regenerateReport(m)">修改需求</el-button>
                <el-button
                  v-if="m.report.status === 'done' && m.report.columns && m.report.columns.length"
                  size="small"
                  @click="exportExcel(m)"
                >
                  导出 Excel
                </el-button>
              </div>
            </template>

            <div v-if="m.report.error" class="report-error">{{ m.report.error }}</div>

            <template v-if="m.report.status === 'done' && m.report.columns && m.report.columns.length">
              <div class="report-meta">
                共 {{ m.report.rowCount }} 行{{ m.report.truncated ? '（已达上限，结果被截断）' : '' }}
                <template v-if="m.report.rows && m.report.rows.length > 100">，表格仅展示前 100 行，导出含全部</template>
              </div>
              <el-table
                :data="(m.report.rows || []).slice(0, 100)"
                size="small"
                border
                max-height="320"
                style="width: 100%;"
              >
                <el-table-column
                  v-for="c in m.report.columns"
                  :key="c"
                  :prop="c"
                  :label="c"
                  min-width="120"
                  show-overflow-tooltip
                />
              </el-table>
            </template>
          </div>
        </div>
      </div>
      <div v-if="sending && !lastAssistantContent && !lastAssistantReasoning && !lastIsReport" class="thinking">正在思考...</div>
    </div>

    <div class="chat-input">
      <el-input
        v-model="input"
        type="textarea"
        :rows="2"
        resize="none"
        :placeholder="reportMode ? '输入报表需求，如：统计各科室上月门诊量' : '输入你的问题，Ctrl+Enter 发送'"
        @keydown.enter.ctrl.prevent="send"
      />
      <div class="chat-actions">
        <span class="hint">{{ reportMode ? '报表模式：选择数据源后输入需求并生成' : 'Enter 换行，Ctrl+Enter 发送' }}</span>
        <el-button type="primary" :disabled="sending || !input.trim()" @click="send">
          {{ reportMode ? '生成报表' : '发送' }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { agentApi, reportApi, catalogApi } from '@/api'

// 嵌入悬浮对话框时可隐藏自带标题栏，由外部容器提供标题与关闭按钮
defineProps<{ hideHeader?: boolean }>()

interface ReportState {
  status: 'generating' | 'ready' | 'executing' | 'done' | 'error'
  requirement?: string
  sql?: string
  dataSourceId?: number
  dataSourceName?: string
  tablesUsed?: { tableName: string; tableComment?: string }[]
  columns?: string[]
  rows?: Record<string, any>[]
  rowCount?: number
  truncated?: boolean
  error?: string
  taskId?: number
  progress?: number
  reportId?: number
  reportName?: string
}

interface Msg {
  role: 'user' | 'assistant'
  content: string
  reasoning?: string
  report?: ReportState
}

const router = useRouter()
const messages = ref<Msg[]>([])
const input = ref('')
const sending = ref(false)
const listRef = ref<HTMLElement>()
const reasoningOpen = ref<Record<number, boolean>>({})
let abortCtrl: AbortController | null = null

// 报表模式
const reportMode = ref(false)
const dataSourceId = ref<number | null>(null)
const dataSources = ref<any[]>([])

onMounted(async () => {
  try {
    const res: any = await catalogApi.getDataSources()
    dataSources.value = (res.data || []).filter((d: any) => d.status === 1)
  } catch (_e) {
    dataSources.value = []
  }
})

const lastMsg = computed(() => messages.value[messages.value.length - 1])
const lastAssistantContent = computed(() => {
  const last = lastMsg.value
  return last && last.role === 'assistant' ? last.content : ''
})
const lastIsReport = computed(() => !!lastMsg.value?.report)
const lastAssistantReasoning = computed(() => {
  const last = lastMsg.value
  return last && last.role === 'assistant' ? !!last.reasoning : false
})

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  if (reportMode.value) {
    await sendReport(text)
    return
  }

  messages.value.push({ role: 'user', content: text })
  input.value = ''
  // 追加一个空的助手占位，流式返回时逐步填充
  messages.value.push({ role: 'assistant', content: '' })
  sending.value = true
  abortCtrl = new AbortController()

  // 历史消息：不包含刚追加的空助手占位
  const history = messages.value.slice(0, -1).map(m => ({ role: m.role, content: m.content }))
  const last = messages.value[messages.value.length - 1]
  const lastIndex = messages.value.length - 1
  await scrollBottom()

  try {
    await agentApi.streamChat(history, {
      onReasoning: (delta: string) => {
        last.reasoning = (last.reasoning || '') + delta
        if (reasoningOpen.value[lastIndex] === undefined) reasoningOpen.value[lastIndex] = true
        scrollBottom()
      },
      onDelta: (delta: string) => {
        last.content += delta
        scrollBottom()
      },
      onError: (msg: string) => {
        if (!last.content) last.content = `[错误] ${msg}`
        ElMessage.error(msg)
      }
    }, abortCtrl.signal)
  } catch (e: any) {
    if (e?.name === 'AbortError') {
      if (!last.content) last.content = '[已停止]'
    } else {
      if (!last.content) last.content = `[请求失败] ${e?.message || '请稍后重试'}`
      ElMessage.error(e?.message || '请求失败')
    }
  } finally {
    sending.value = false
    abortCtrl = null
  }
}

/** 报表模式：创建异步报表任务 → 轮询进度 → 展示已登记的报表 */
async function sendReport(text: string) {
  if (!dataSourceId.value) {
    ElMessage.warning('请先选择数据源')
    return
  }
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  const report: ReportState = {
    status: 'generating',
    dataSourceId: dataSourceId.value,
    requirement: text,
    progress: 0
  }
  messages.value.push({ role: 'assistant', content: '', report })
  sending.value = true
  await scrollBottom()

  try {
    await runReportTask(report, text)
  } catch (e: any) {
    report.status = 'error'
    report.error = e.message || '生成失败'
    ElMessage.error(report.error)
  } finally {
    sending.value = false
    await scrollBottom()
  }
}

function sleep(ms: number) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

async function runReportTask(report: ReportState, requirement: string) {
  const created: any = await reportApi.generateAsync({
    requirement,
    dataSourceId: report.dataSourceId!
  })
  const taskId = Number(created.data?.taskId)
  if (!taskId) throw new Error('报表任务创建失败')
  report.taskId = taskId
  report.progress = 5

  for (let i = 0; i < 120; i++) {
    await sleep(2500)
    const taskRes: any = await catalogApi.getAiTask(taskId)
    const task = taskRes.data?.task
    if (!task) continue
    if (task.status === 'FAILED') {
      throw new Error(task.errorMessage || '报表生成失败')
    }
    if (task.status === 'DONE') {
      const itemsRes: any = await catalogApi.getAiTaskItems(taskId, { page: 1, pageSize: 10 })
      const item = (itemsRes.data?.records || []).find((x: any) => x.itemType === 'REPORT')
      if (!item) throw new Error('报表任务已完成，但未找到生成结果')
      const d = JSON.parse(item.draftContent || '{}')
      report.sql = d.sql || ''
      report.dataSourceName = d.dataSourceName
      report.tablesUsed = d.tablesUsed || []
      report.reportId = d.reportId
      report.reportName = d.reportName
      report.progress = 100
      report.status = 'ready'
      return
    }
    report.progress = task.status === 'RUNNING'
      ? Math.min(90, 10 + i * 3)
      : Math.min(10, report.progress || 0)
  }
  throw new Error('报表生成超时，请稍后在 AI 工作台任务队列查看进度')
}

async function goReportCenter() {
  router.push('/reports')
}

/** 执行生成的报表 SQL *//** 执行生成的报表 SQL */
async function executeReport(m: Msg) {
  const r = m.report
  if (!r || !r.sql || r.status === 'executing') return
  r.status = 'executing'
  r.error = undefined
  try {
    const res: any = await reportApi.execute({ dataSourceId: r.dataSourceId!, sql: r.sql, limit: 500 })
    const d = res.data || {}
    r.columns = d.columns || []
    r.rows = d.rows || []
    r.rowCount = d.rowCount || 0
    r.truncated = !!d.truncated
    r.status = 'done'
  } catch (e: any) {
    r.status = 'error'
    r.error = e.message || '执行失败'
    ElMessage.error(e.message || '执行失败')
  }
  await scrollBottom()
}

/** 修改需求后重新生成报表 SQL（异步任务） */
async function regenerateReport(m: Msg) {
  const r = m.report
  if (!r || r.status === 'executing') return
  let value: string
  try {
    const res = await ElMessageBox.prompt('修改报表需求后重新生成 SQL', '重新生成', {
      confirmButtonText: '生成',
      cancelButtonText: '取消',
      inputValue: r.requirement || '',
      inputType: 'textarea'
    })
    value = res.value || ''
  } catch {
    return
  }
  if (!value.trim()) return
  r.requirement = value.trim()
  r.status = 'generating'
  r.progress = 0
  r.taskId = undefined
  r.reportId = undefined
  r.reportName = undefined
  r.sql = undefined
  r.tablesUsed = []
  r.columns = undefined
  r.rows = undefined
  r.rowCount = undefined
  r.truncated = undefined
  r.error = undefined
  await scrollBottom()
  try {
    await runReportTask(r, value.trim())
  } catch (e: any) {
    r.status = 'error'
    r.error = e.message || '生成失败'
    ElMessage.error(r.error)
  }
  await scrollBottom()
}

/** 导出结果到表格/** 导出结果到表格（CSV，UTF-8 BOM，Excel 可直接打开；剔除高危依赖 xlsx） */
function exportExcel(m: Msg) {
  const r = m.report
  if (!r || !r.columns || !r.columns.length || !r.rows) return
  const esc = (v: any) => {
    const s = v == null ? '' : String(v)
    return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s
  }
  const header: string[] = r.columns
  const lines: string[] = [header.map(esc).join(',')]
  for (const row of r.rows) {
    lines.push(header.map((c: string) => esc(row[c])).join(','))
  }
  const name = r.dataSourceName ? `${r.dataSourceName}_报表_${Date.now()}.csv` : `报表_${Date.now()}.csv`
  const blob = new Blob(['\uFEFF' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  URL.revokeObjectURL(url)
}

function stop() {
  abortCtrl?.abort()
}

function toggleReasoning(i: number) {
  reasoningOpen.value[i] = !reasoningOpen.value[i]
}

async function scrollBottom() {
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
}
</script>

<style scoped>
.agent-chat {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.chat-header {
  padding: 16px 24px;
  background: var(--med-white);
  border-bottom: 1px solid var(--med-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.chat-title {
  font-weight: 600;
  color: var(--med-text);
}
.chat-subtitle {
  font-size: 12px;
  color: var(--med-text-light);
  margin-top: 2px;
}
.report-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 24px;
  background: #f5f7fa;
  border-bottom: 1px solid var(--med-border);
}
.chat-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.msg-row {
  display: flex;
}
.msg-row.user {
  justify-content: flex-end;
}
.msg-row.assistant {
  justify-content: flex-start;
}
.bubble {
  max-width: 72%;
  padding: 10px 14px;
  border-radius: 8px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
}
.msg-row.user .bubble {
  background: var(--med-primary);
  color: #fff;
}
.msg-row.assistant .bubble {
  background: var(--med-white);
  color: var(--med-text);
  border: 1px solid var(--med-border);
}
.msg-row.assistant .bubble:has(.report-card) {
  max-width: 90%;
}
.thinking {
  color: var(--med-text-light);
  font-size: 13px;
  padding-left: 4px;
}
.part-block { margin-bottom: 10px; }
.part-block:last-child { margin-bottom: 0; }
.part-head { display: flex; align-items: center; justify-content: space-between; cursor: pointer; user-select: none; margin-bottom: 6px; }
.part-name { display: inline-flex; align-items: center; gap: 6px; font-size: 11px; font-weight: 600; color: var(--med-text-secondary); }
.part-dot { width: 6px; height: 6px; border-radius: 50%; display: inline-block; }
.reasoning-dot { background: #94A3B8; }
.answer-dot { background: var(--med-primary); }
.part-toggle { font-size: 10.5px; color: var(--med-text-light); }
.reasoning-part { background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 8px; padding: 9px 11px; }
.answer-part { border-left: 3px solid var(--med-primary); padding-left: 10px; }
.reasoning-box {
  background: #fff;
  border-left: 2px solid #CBD5E1;
  padding: 8px 10px;
  border-radius: 4px;
  font-size: 12px;
  color: #64748B;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 220px;
  overflow-y: auto;
}
.content-text {
  white-space: pre-wrap;
  word-break: break-word;
}
.chat-input {
  padding: 16px 24px;
  background: var(--med-white);
  border-top: 1px solid var(--med-border);
}
.chat-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.hint {
  font-size: 12px;
  color: var(--med-text-light);
}
/* 报表卡片 */
.report-card {
  margin-top: 8px;
}
.report-loading { padding: 8px 0; }
.report-loading-line { display: flex; align-items: center; gap: 6px; color: var(--med-text-light); font-size: 12px; margin-bottom: 6px; }
.report-loading-line b { margin-left: auto; color: var(--med-primary); }
.report-registered { display: flex; align-items: center; gap: 6px; margin: 8px 0; padding: 7px 10px; border-radius: 7px; background: #F0FDF4; color: #15803D; font-size: 11.5px; }
.report-registered b { font-weight: 600; }
.report-tables {
  font-size: 12px;
  color: var(--med-text-light);
  margin-bottom: 6px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}
.report-table-tag {
  margin: 0;
}
.report-sql-label {
  font-size: 12px;
  color: var(--med-text-light);
  margin-bottom: 4px;
}
.report-sql {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
  margin: 0;
}
.report-actions {
  margin: 8px 0;
  display: flex;
  gap: 8px;
}
.report-error {
  color: #f56c6c;
  font-size: 13px;
  margin-top: 6px;
}
.report-meta {
  color: var(--med-text-light);
  font-size: 12px;
  margin-bottom: 6px;
}
</style>
