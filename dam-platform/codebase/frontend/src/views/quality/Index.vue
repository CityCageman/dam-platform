<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">数据质量</h2>
        <p class="page-desc">维护字段级质量规则，执行检测并查看通过率、异常样例与趋势。</p>
      </div>
      <div class="head-actions">
        <el-button @click="loadAll"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-button :loading="executingAll" @click="executeAll">执行全部</el-button>
        <el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新建规则</el-button>
      </div>
    </div>

    <div class="stat-grid" style="margin-bottom: 14px;">
      <div class="stat-card">
        <div class="stat-label">规则总数</div>
        <div class="stat-value">{{ overview.totalRules ?? total }}</div>
        <div class="stat-foot">启用 {{ overview.enabledRules ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">平均通过率</div>
        <div class="stat-value">{{ overview.avgScore ?? '-' }}%</div>
        <div class="stat-foot">最近检测结果</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">最后检测</div>
        <div class="stat-value" style="font-size: 16px;">{{ formatTime(overview.lastCheckTime) }}</div>
        <div class="stat-foot">按规则执行时间</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">当前规则</div>
        <div class="stat-value" style="font-size: 16px;">{{ checkRule?.ruleCode || '未选择' }}</div>
        <div class="stat-foot">检测记录：{{ checkList.length }} 条</div>
      </div>
    </div>

    <div class="panel">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="质量规则" name="rules">
          <div class="tab-toolbar">
            <div class="filter-row">
              <el-input v-model="keyword" placeholder="搜索规则编码" clearable style="width: 220px;" @keyup.enter="search" />
              <el-select v-model="ruleType" placeholder="规则类型" clearable style="width: 150px;" @change="search">
                <el-option v-for="t in TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
              <el-button type="primary" @click="search">查询</el-button>
            </div>
            <span class="muted">共 {{ total }} 条</span>
          </div>
          <el-table :data="ruleList" stripe v-loading="loading">
            <el-table-column prop="ruleCode" label="规则编码" min-width="170" show-overflow-tooltip />
            <el-table-column label="类型" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="typeTag(row.ruleType)">{{ typeLabel(row.ruleType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="级别" width="80">
              <template #default="{ row }">{{ levelLabel(row.ruleLevel) }}</template>
            </el-table-column>
            <el-table-column label="严重度" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.severity === 'high' || row.severity === 'HIGH' ? 'danger' : row.severity === 'medium' || row.severity === 'MEDIUM' ? 'warning' : 'info'">
                  {{ severityLabel(row.severity) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="目标" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ targetLabel(row) }}</template>
            </el-table-column>
            <el-table-column prop="expectValue" label="期望值" width="110" show-overflow-tooltip />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="Number(row.status) === 1 ? 'success' : 'info'">{{ Number(row.status) === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="250" fixed="right">
              <template #default="{ row }">
                <el-button text size="small" @click="viewChecks(row)">历史</el-button>
                <el-button text size="small" :loading="executingId === row.id" @click="executeRule(row)">执行</el-button>
                <el-button text size="small" type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button text size="small" @click="toggleRule(row)">{{ Number(row.status) === 1 ? '停用' : '启用' }}</el-button>
                <el-button text size="small" type="danger" @click="removeRule(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && !ruleList.length" description="暂无质量规则" :image-size="60" />
          <div class="pagination-bar">
            <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total"
                           layout="total, prev, pager, next" @change="loadRules" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="检测记录" name="checks">
          <div class="tab-toolbar">
            <span class="muted">{{ checkRule ? `规则：${checkRule.ruleCode}` : '从规则列表点击「历史」查看' }}</span>
            <el-button size="small" @click="loadChecks">刷新</el-button>
          </div>
          <el-table :data="checkList" stripe>
            <el-table-column prop="checkTime" label="检测时间" width="170">
              <template #default="{ row }">{{ formatTime(row.checkTime) }}</template>
            </el-table-column>
            <el-table-column prop="totalCount" label="总记录" width="100" />
            <el-table-column prop="passCount" label="通过" width="90" />
            <el-table-column prop="failCount" label="异常" width="90" />
            <el-table-column label="通过率" width="110">
              <template #default="{ row }">
                <span :style="{ color: Number(row.passRate) >= 95 ? '#16A34A' : Number(row.passRate) >= 80 ? '#D97706' : '#DC2626' }">
                  {{ row.passRate != null ? Number(row.passRate).toFixed(2) + '%' : '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="结果" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.resultStatus === 'PASS' ? 'success' : row.resultStatus === 'FAIL' ? 'danger' : 'info'">
                  {{ row.resultStatus || (Number(row.status) === 2 ? 'ERROR' : '-') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="失败样例/错误" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">{{ row.failSamples || row.errorDetail || '-' }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!checkList.length" description="暂无检测记录，先执行一次规则" :image-size="60" />
        </el-tab-pane>

        <el-tab-pane label="质量趋势" name="trend">
          <div class="tab-toolbar">
            <el-select v-model="trendRuleId" placeholder="全部规则" clearable style="width: 220px;" @change="loadTrendChart">
              <el-option v-for="r in ruleList" :key="r.id" :label="r.ruleCode" :value="r.id" />
            </el-select>
            <el-select v-model="trendDays" style="width: 120px;" @change="loadTrendChart">
              <el-option :value="7" label="近 7 天" />
              <el-option :value="30" label="近 30 天" />
              <el-option :value="90" label="近 90 天" />
            </el-select>
            <div style="flex: 1;" />
            <span class="muted">通过率 %</span>
          </div>
          <div ref="trendChartRef" style="height: 340px;"></div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 规则表单 -->
    <el-dialog v-model="showCreate" :title="isEditRule ? '编辑质量规则' : '新建质量规则'" width="660px" top="10vh">
      <el-form :model="ruleForm" label-width="100px">
        <el-form-item label="规则模板">
          <el-select v-model="templateIndex" placeholder="选择模板可自动填充" clearable style="width: 100%;" @change="applyTemplate">
            <el-option v-for="(t, i) in RULE_TEMPLATES" :key="t.name" :label="t.name" :value="i" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="规则编码" required><el-input v-model="ruleForm.ruleCode" placeholder="如：QR-PATIENT-ID-NOTNULL" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="规则类型">
              <el-select v-model="ruleForm.ruleType" style="width: 100%;">
                <el-option v-for="t in TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="规则级别">
              <el-select v-model="ruleForm.ruleLevel" style="width: 100%;">
                <el-option label="关键" value="critical" /><el-option label="重要" value="important" /><el-option label="一般" value="normal" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="严重度">
              <el-select v-model="ruleForm.severity" style="width: 100%;">
                <el-option label="高" value="high" /><el-option label="中" value="medium" /><el-option label="低" value="low" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="期望值"><el-input v-model="ruleForm.expectValue" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="目标表">
              <el-select v-model="ruleTableId" filterable clearable placeholder="选择资产表" style="width: 100%;" @change="onRuleTableChange">
                <el-option v-for="t in sourceTables" :key="t.id" :label="`${t.assetName} (${t.tableName})`" :value="t.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标字段">
              <el-select v-model="ruleForm.targetColumn" filterable allow-create clearable placeholder="选择或输入字段" style="width: 100%;">
                <el-option v-for="c in ruleColumns" :key="c.columnName" :label="c.columnName" :value="c.columnName" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="规则表达式">
          <el-input v-model="ruleForm.ruleExpression" type="textarea" :rows="4"
                    placeholder="可写条件（如 patient_id IS NULL）或完整 SELECT（返回异常行）" />
          <div class="muted" style="margin-top: 4px;">条件式会自动按目标表补全；完整 SELECT 需返回异常行。</div>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="ruleForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="定时执行"><el-switch v-model="ruleForm.isScheduled" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="Cron">
              <el-input v-model="ruleForm.cronExpression" :disabled="!ruleForm.isScheduled" placeholder="0 0 2 * * ?" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态"><el-switch v-model="ruleForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="submitRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, shallowRef, watch } from 'vue'
import { qualityApi, catalogApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

const TYPE_OPTIONS = [
  { value: 'completeness', label: '完整性' },
  { value: 'accuracy', label: '准确性' },
  { value: 'consistency', label: '一致性' },
  { value: 'timeliness', label: '及时性' },
  { value: 'uniqueness', label: '唯一性' },
  { value: 'validity', label: '有效性' }
]

const RULE_TEMPLATES = [
  { name: '非空校验', type: 'completeness', condition: '{column} IS NULL', expect: '无空值' },
  { name: '唯一性校验', type: 'uniqueness', condition: '{column} IN (SELECT {column} FROM {table} GROUP BY {column} HAVING COUNT(*) > 1)', expect: '无重复' },
  { name: '枚举值校验', type: 'validity', condition: "{column} NOT IN ('A','B')", expect: 'A,B' },
  { name: '数值范围校验', type: 'accuracy', condition: '{column} NOT BETWEEN 0 AND 100', expect: '0~100' },
  { name: '日期时效校验', type: 'timeliness', condition: "{column} < now() - interval '2 days'", expect: '2 天内' },
  { name: '正则格式校验', type: 'validity', condition: "{column} !~ '^[A-Z0-9_-]+$'", expect: '符合格式' }
]

const activeTab = ref('rules')
const keyword = ref('')
const ruleType = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const ruleList = ref<any[]>([])
const checkList = ref<any[]>([])
const checkRule = ref<any>(null)
const overview = ref<any>({})
const loading = ref(false)
const executingAll = ref(false)
const executingId = ref<number>()

const showCreate = ref(false)
const isEditRule = ref(false)
const ruleForm = ref<any>({})
const templateIndex = ref<number>()
const ruleTableId = ref<number>()
const ruleColumns = ref<any[]>([])
const sourceTables = ref<any[]>([])

const trendChartRef = shallowRef<HTMLElement>()
const trendRuleId = ref<number>()
const trendDays = ref(30)
let trendChart: echarts.ECharts | null = null

const typeLabel = (v: string) => TYPE_OPTIONS.find(t => t.value === v)?.label || v || '-'
const typeTag = (v: string) => v === 'completeness' ? 'primary' : v === 'uniqueness' ? 'warning' : v === 'accuracy' ? 'success' : 'info'
const levelLabel = (v: string) => v === 'critical' ? '关键' : v === 'important' ? '重要' : v === 'normal' ? '一般' : v || '-'
const severityLabel = (v: string) => String(v || '').toLowerCase() === 'high' ? '高' : String(v || '').toLowerCase() === 'medium' ? '中' : '低'
const formatTime = (v: string) => v ? String(v).replace('T', ' ').slice(0, 16) : '-'
const targetLabel = (row: any) => {
  const t = sourceTables.value.find(x => x.sourceMetadataId === row.targetTableId || x.id === row.targetTableId)
  return t ? `${t.assetName}${row.targetColumn ? '.' + row.targetColumn : ''}` : (row.targetColumn || '-')
}

async function loadAll() {
  await Promise.all([loadRules(), loadOverview(), loadSourceTables()])
  if (activeTab.value === 'trend') await nextTick(loadTrendChart)
}

async function loadRules() {
  loading.value = true
  try {
    const res: any = await qualityApi.searchRules({
      keyword: keyword.value || undefined,
      ruleType: ruleType.value || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    ruleList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e: any) { ElMessage.error(e.message || '加载规则失败') }
  finally { loading.value = false }
}

async function loadOverview() {
  try {
    const res: any = await qualityApi.getOverview()
    overview.value = res.data || {}
  } catch (_e) { overview.value = {} }
}

async function loadSourceTables() {
  try {
    const res: any = await catalogApi.searchAssets({ assetType: 'table', pageSize: 200, page: 1 })
    sourceTables.value = (res.data?.records || []).map((t: any) => ({ ...t, sourceMetadataId: t.sourceMetadataId || t.id }))
  } catch (_e) { sourceTables.value = [] }
}

function search() { page.value = 1; loadRules() }

async function viewChecks(row: any) {
  checkRule.value = row
  trendRuleId.value = row.id
  await loadChecks()
  activeTab.value = 'checks'
}

async function loadChecks() {
  if (!checkRule.value) { checkList.value = []; return }
  try {
    const res: any = await qualityApi.getCheckHistory(checkRule.value.id)
    checkList.value = res.data || []
  } catch (e: any) { ElMessage.error(e.message || '加载检测记录失败') }
}

async function executeRule(row: any) {
  executingId.value = row.id
  try {
    const res: any = await qualityApi.executeRule(row.id)
    const rate = res.data?.passRate
    ElMessage.success(`检测完成：通过率 ${rate != null ? Number(rate).toFixed(2) + '%' : '-'}`)
    if (checkRule.value?.id === row.id) await loadChecks()
    await loadOverview()
  } catch (e: any) { ElMessage.error(e.message || '执行失败') }
  finally { executingId.value = undefined }
}

async function executeAll() {
  executingAll.value = true
  try {
    const res: any = await qualityApi.executeAll()
    ElMessage.success(`执行完成：${res.data?.length || 0} 条规则`)
    await loadOverview()
  } catch (e: any) { ElMessage.error(e.message || '执行失败') }
  finally { executingAll.value = false }
}

function openCreate() {
  isEditRule.value = false
  templateIndex.value = undefined
  ruleForm.value = { ruleCode: '', ruleType: 'completeness', ruleLevel: 'normal', severity: 'medium', expectValue: '', ruleExpression: '', description: '', isScheduled: false, status: 1 }
  ruleTableId.value = undefined
  ruleColumns.value = []
  showCreate.value = true
}

function openEdit(row: any) {
  isEditRule.value = true
  templateIndex.value = undefined
  ruleForm.value = { ...row }
  ruleTableId.value = sourceTables.value.find(t => t.sourceMetadataId === row.targetTableId)?.id
  ruleColumns.value = []
  if (ruleTableId.value) loadRuleColumns(ruleTableId.value)
  showCreate.value = true
}

async function onRuleTableChange(assetId: number) {
  ruleColumns.value = []
  ruleForm.value.targetColumn = ''
  ruleForm.value.targetTableId = undefined
  if (!assetId) return
  const t = sourceTables.value.find(x => x.id === assetId)
  if (t) ruleForm.value.targetTableId = t.sourceMetadataId
  await loadRuleColumns(assetId)
}

async function loadRuleColumns(assetId: number) {
  try {
    const res: any = await catalogApi.getFields(assetId)
    ruleColumns.value = res.data || []
  } catch (_e) { ruleColumns.value = [] }
}

function applyTemplate(index: number) {
  if (index == null) return
  const t = RULE_TEMPLATES[index]
  const table = sourceTables.value.find(x => x.id === ruleTableId.value)
  const tableName = table?.tableName || '{table}'
  ruleForm.value.ruleType = t.type
  ruleForm.value.expectValue = t.expect
  ruleForm.value.ruleExpression = t.condition
    .replace(/\{table\}/g, tableName)
    .replace(/\{column\}/g, ruleForm.value.targetColumn || '{column}')
}

async function submitRule() {
  if (!ruleForm.value.ruleCode?.trim()) { ElMessage.warning('请输入规则编码'); return }
  if (!ruleForm.value.ruleExpression?.trim()) { ElMessage.warning('请输入规则表达式'); return }
  try {
    if (isEditRule.value) await qualityApi.updateRule(ruleForm.value)
    else await qualityApi.createRule(ruleForm.value)
    ElMessage.success('规则已保存')
    showCreate.value = false
    await loadRules()
    await loadOverview()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
}

async function toggleRule(row: any) {
  try {
    await qualityApi.updateRule({ ...row, status: Number(row.status) === 1 ? 0 : 1 })
    ElMessage.success('状态已更新')
    await loadRules()
  } catch (e: any) { ElMessage.error(e.message || '更新失败') }
}

async function removeRule(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除规则「${row.ruleCode}」？`, '删除确认', { type: 'warning' })
    await qualityApi.deleteRule(row.id)
    ElMessage.success('已删除')
    if (checkRule.value?.id === row.id) { checkRule.value = null; checkList.value = [] }
    await loadRules()
  } catch (_e) { /* 取消 */ }
}

async function loadTrendChart() {
  await nextTick()
  if (!trendChartRef.value) return
  try {
    const res: any = await qualityApi.getTrend(trendRuleId.value || undefined, trendDays.value)
    const data = res.data || {}
    if (!trendChart) trendChart = echarts.init(trendChartRef.value)
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 46, right: 20, top: 24, bottom: 36 },
      xAxis: { type: 'category', data: data.dates || [], axisLabel: { color: '#94A3B8' }, axisLine: { lineStyle: { color: '#E6EAF2' } } },
      yAxis: { type: 'value', min: 0, max: 100, axisLabel: { color: '#94A3B8', formatter: '{value}%' }, splitLine: { lineStyle: { color: '#F1F4F9' } } },
      series: [{
        name: '通过率',
        type: 'line',
        smooth: true,
        data: data.scores || [],
        areaStyle: { opacity: 0.15, color: '#2563EB' },
        lineStyle: { width: 2.5, color: '#2563EB' },
        itemStyle: { color: '#2563EB' }
      }]
    }, true)
  } catch (_e) { /* ignore */ }
}

watch(activeTab, async (v) => {
  if (v === 'trend') await loadTrendChart()
})

onMounted(loadAll)
</script>

<style scoped>
.tab-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.filter-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.muted { color: var(--med-text-light); font-size: 12px; }
</style>
