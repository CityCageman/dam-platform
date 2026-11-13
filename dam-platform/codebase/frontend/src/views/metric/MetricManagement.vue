<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">指标管理</h2>
        <p class="page-desc">维护指标定义与计算公式，区分手工填报和系统计算。</p>
      </div>
      <div class="head-actions">
        <el-button @click="loadAll"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新建指标</el-button>
      </div>
    </div>

    <div class="stat-grid" style="margin-bottom: 14px;">
      <div class="stat-card"><div class="stat-label">指标总数</div><div class="stat-value">{{ total }}</div><div class="stat-foot">当前筛选范围</div></div>
      <div class="stat-card"><div class="stat-label">本页手工填报</div><div class="stat-value">{{ manualCount }}</div><div class="stat-foot">MANUAL</div></div>
      <div class="stat-card"><div class="stat-label">本页系统计算</div><div class="stat-value">{{ computedCount }}</div><div class="stat-foot">COMPUTED</div></div>
      <div class="stat-card"><div class="stat-label">本页启用</div><div class="stat-value">{{ enabledCount }}</div><div class="stat-foot">状态正常</div></div>
    </div>

    <div class="panel">
      <div class="search-bar">
        <el-input v-model="query.keyword" placeholder="搜索指标编码/名称/定义" clearable style="width: 260px;" @keyup.enter="search" />
        <el-select v-model="query.metricType" placeholder="指标类型" clearable style="width: 140px;" @change="search">
          <el-option label="手工填报" value="MANUAL" />
          <el-option label="系统计算" value="COMPUTED" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px;" @change="search">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>

      <el-table :data="metrics" stripe v-loading="loading">
        <el-table-column prop="metricCode" label="指标编码" width="150" show-overflow-tooltip />
        <el-table-column prop="metricName" label="指标名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.metricType === 'MANUAL' ? 'warning' : 'success'">
              {{ row.metricType === 'MANUAL' ? '手工填报' : '系统计算' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="businessDomain" label="业务域" width="110" />
        <el-table-column prop="definition" label="业务定义" min-width="220" show-overflow-tooltip />
        <el-table-column prop="formula" label="计算公式" min-width="220" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="version" label="版本" width="70" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="270" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.metricType === 'MANUAL'" text type="warning" size="small" @click="openValues(row)">填报值</el-button>
            <el-button v-if="row.metricType === 'COMPUTED'" text type="success" size="small" @click="openAi(row)">AI 生成 SQL</el-button>
            <el-button text type="danger" size="small" @click="removeMetric(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !metrics.length" description="暂无指标，先新建一个" :image-size="56" />
      <div class="pagination-bar">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.pageSize" :total="total"
                       layout="total, prev, pager, next" @change="loadMetrics" />
      </div>
    </div>

    <el-dialog v-model="showForm" :title="form.id ? '编辑指标' : '新建指标'" width="720px" top="6vh">
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="指标编码" required><el-input v-model="form.metricCode" placeholder="如 OUTP_VISIT_CNT" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="指标名称" required><el-input v-model="form.metricName" placeholder="如 门诊量" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="指标类型" required>
              <el-select v-model="form.metricType" style="width: 100%;">
                <el-option label="手工填报" value="MANUAL" />
                <el-option label="系统计算" value="COMPUTED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="业务域"><el-input v-model="form.businessDomain" placeholder="门诊/住院/财务" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="单位"><el-input v-model="form.unit" placeholder="人次/元/%" /></el-form-item></el-col>
        </el-row>
        <el-alert class="metric-boundary-tip" type="warning" :closable="false" show-icon
                  title="这里是指标定义：公式、来源表、SQL 和取数规则在本页维护；解释、别名和案例请到知识库的“指标解释”中维护。" />
        <el-form-item label="业务定义"><el-input v-model="form.definition" type="textarea" :autosize="{ minRows: 2, maxRows: 8 }" placeholder="这个指标业务上代表什么" /></el-form-item>
        <el-form-item label="计算公式"><el-input v-model="form.formula" type="textarea" :rows="2" placeholder="如 SUM(fee_amount) / COUNT(DISTINCT visit_id)" /></el-form-item>
        <template v-if="form.metricType === 'COMPUTED'">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="数据源">
                <el-select v-model="form.dataSourceId" filterable clearable style="width: 100%;" placeholder="选择数据源">
                  <el-option v-for="d in dataSources" :key="d.id" :label="d.dsName" :value="d.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12"><el-form-item label="时间粒度"><el-input v-model="form.timeGrain" placeholder="日/月/年" /></el-form-item></el-col>
          </el-row>
          <el-form-item label="来源表"><el-input v-model="form.sourceTablesText" placeholder="逗号分隔，如 dwd_visit, dim_dept" /></el-form-item>
          <el-form-item label="统计维度"><el-input v-model="form.dimensionsText" placeholder="逗号分隔，如 科室, 月份" /></el-form-item>
          <el-form-item label="指标 SQL">
            <el-input v-model="form.sqlExpression" type="textarea" :rows="6" placeholder="可由 AI 根据定义和公式生成，保存前可人工修改" />
          </el-form-item>
          <el-collapse class="metric-rule-collapse">
            <el-collapse-item title="取数规则（AI 报表按此生成，知识库只读引用）" name="rule">
              <el-alert type="info" :closable="false" show-icon
                        title="规则归属于指标库；知识库的指标解释只展示本规则，不再单独维护 SQL/取数规则。" />
              <el-row :gutter="12">
                <el-col :span="12">
                  <el-form-item label="规则状态">
                    <el-select v-model="form.reportSpec.ruleStatus" style="width: 100%;">
                      <el-option label="草稿" value="DRAFT" />
                      <el-option label="待审核" value="REVIEW" />
                      <el-option label="已发布" value="PUBLISHED" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="12"><el-form-item label="统计粒度"><el-input v-model="form.reportSpec.statisticalGrain" placeholder="如：就诊号 + 日期 + 科室" /></el-form-item></el-col>
              </el-row>
              <el-row :gutter="12">
                <el-col :span="12"><el-form-item label="去重键"><el-input v-model="form.reportSpec.dedupKeys" placeholder="如：PAADM_RowId" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="必用表"><el-input v-model="form.reportSpec.requiredTables" placeholder="如：PA_Adm、DHC_PatBillOrder" /></el-form-item></el-col>
              </el-row>
              <el-form-item label="业务条件"><el-input v-model="form.reportSpec.conditions" type="textarea" :autosize="{ minRows: 2, maxRows: 8 }" placeholder="如：有费用产生；排除测试患者" /></el-form-item>
              <el-form-item label="主报表 SQL"><el-input v-model="form.reportSpec.mainSql" type="textarea" :autosize="{ minRows: 2, maxRows: 14 }" placeholder="可选：主报表模板" /></el-form-item>
              <el-form-item label="下钻 SQL"><el-input v-model="form.reportSpec.drillSql" type="textarea" :autosize="{ minRows: 2, maxRows: 14 }" placeholder="可选：下钻明细模板" /></el-form-item>
            </el-collapse-item>
          </el-collapse>
        </template>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="负责人"><el-input v-model="form.owner" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button v-if="form.id && form.metricType === 'COMPUTED'" :loading="aiLoading" @click="generateAiSql">
          <el-icon><MagicStick /></el-icon>AI 生成 SQL
        </el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showValues" :title="`填报值：${currentMetric?.metricName || ''}`" width="760px" top="10vh">
      <div class="value-form">
        <el-input v-model="valueForm.period" placeholder="期间，如 2026-10" style="width: 150px;" />
        <el-input v-model="valueForm.dimensionKey" placeholder="维度（可选）" style="width: 180px;" />
        <el-input-number v-model="valueForm.metricValue" :precision="4" placeholder="指标值" style="width: 150px;" />
        <el-input v-model="valueForm.note" placeholder="备注" style="flex: 1;" />
        <el-button type="primary" :loading="valueSaving" @click="submitValue">保存</el-button>
      </div>
      <el-table :data="values" stripe max-height="380">
        <el-table-column prop="period" label="期间" width="120" />
        <el-table-column prop="dimensionKey" label="维度" width="180" show-overflow-tooltip />
        <el-table-column prop="metricValue" label="指标值" width="140" />
        <el-table-column prop="note" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button text type="danger" size="small" @click="removeValue(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!values.length" description="暂无填报值" :image-size="50" />
      <template #footer><el-button @click="showValues = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { metricApi, catalogApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, MagicStick } from '@element-plus/icons-vue'

const metrics = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const query = ref({ keyword: '', metricType: '', status: undefined as number | undefined, page: 1, pageSize: 20 })
const dataSources = ref<any[]>([])
const showForm = ref(false)
const saving = ref(false)
const aiLoading = ref(false)
const form = ref<any>({ metricType: 'COMPUTED', status: 1, sourceTablesText: '', dimensionsText: '' })
const showValues = ref(false)
const currentMetric = ref<any>(null)
const values = ref<any[]>([])
const valueSaving = ref(false)
const valueForm = ref<any>({ period: '', dimensionKey: '', metricValue: undefined, note: '' })

const manualCount = computed(() => metrics.value.filter(m => m.metricType === 'MANUAL').length)
const computedCount = computed(() => metrics.value.filter(m => m.metricType === 'COMPUTED').length)
const enabledCount = computed(() => metrics.value.filter(m => m.status === 1).length)

function parseObject(v: any): any {
  if (!v) return { ruleStatus: 'DRAFT' }
  if (typeof v === 'object') return v
  try { return JSON.parse(v) } catch { return { ruleStatus: 'DRAFT' } }
}

function parseList(v: any): string {
  if (!v) return ''
  try { const a = JSON.parse(v); return Array.isArray(a) ? a.join(', ') : String(v) }
  catch { return String(v) }
}
function toList(v: string): string[] {
  return (v || '').split(/[,，\n]/).map(s => s.trim()).filter(Boolean)
}

async function loadMetrics() {
  loading.value = true
  try {
    const res: any = await metricApi.list({
      keyword: query.value.keyword || undefined,
      metricType: query.value.metricType || undefined,
      status: query.value.status,
      page: query.value.page,
      pageSize: query.value.pageSize
    })
    metrics.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e: any) { ElMessage.error(e.message || '加载指标失败'); metrics.value = [] }
  finally { loading.value = false }
}
async function loadDataSources() {
  try { const res: any = await catalogApi.getDataSources(); dataSources.value = res.data || [] }
  catch { dataSources.value = [] }
}
async function loadAll() { await Promise.all([loadMetrics(), loadDataSources()]) }
function search() { query.value.page = 1; loadMetrics() }
function resetSearch() { query.value.keyword = ''; query.value.metricType = ''; query.value.status = undefined; search() }

function openCreate() {
  form.value = { metricType: 'COMPUTED', status: 1, sourceTablesText: '', dimensionsText: '', reportSpec: { ruleStatus: 'DRAFT' } }
  showForm.value = true
}
async function openEdit(row: any) {
  try {
    const res: any = await metricApi.get(row.id)
    const m = res.data
    form.value = { ...m, sourceTablesText: parseList(m.sourceTables), dimensionsText: parseList(m.dimensions), reportSpec: parseObject(m.reportSpec) }
    showForm.value = true
  } catch (e: any) { ElMessage.error(e.message || '加载指标失败') }
}
async function submitForm() {
  if (!form.value.metricCode?.trim() || !form.value.metricName?.trim()) {
    ElMessage.warning('指标编码和名称不能为空'); return
  }
  saving.value = true
  try {
    const payload = {
      ...form.value,
      sourceTables: toList(form.value.sourceTablesText),
      dimensions: toList(form.value.dimensionsText)
    }
    delete payload.sourceTablesText
    delete payload.dimensionsText
    if (form.value.id) await metricApi.update(form.value.id, payload)
    else await metricApi.create(payload)
    ElMessage.success('指标已保存')
    showForm.value = false
    await loadMetrics()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { saving.value = false }
}
async function removeMetric(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除指标「${row.metricName}」？`, '删除指标', { type: 'warning' })
    await metricApi.remove(row.id)
    ElMessage.success('已删除')
    await loadMetrics()
  } catch (_e) { /* 取消 */ }
}
async function openAi(row: any) {
  try {
    await openEdit(row)
    await generateAiSql()
  } catch (_e) { /* ignore */ }
}
async function generateAiSql() {
  if (!form.value.id) { ElMessage.warning('请先保存指标，再让 AI 生成 SQL'); return }
  aiLoading.value = true
  try {
    const res: any = await metricApi.aiSql(form.value.id)
    form.value.sqlExpression = res.data?.sql || form.value.sqlExpression
    form.value.version = res.data?.version || form.value.version
    ElMessage.success('指标 SQL 已生成，请确认后保存')
  } catch (e: any) { ElMessage.error(e.message || 'AI 生成失败') }
  finally { aiLoading.value = false }
}
async function openValues(row: any) {
  currentMetric.value = row
  showValues.value = true
  try { const res: any = await metricApi.values(row.id); values.value = res.data || [] }
  catch { values.value = [] }
}
async function submitValue() {
  if (!currentMetric.value || !valueForm.value.period) { ElMessage.warning('请填写期间'); return }
  if (valueForm.value.metricValue == null) { ElMessage.warning('请填写指标值'); return }
  valueSaving.value = true
  try {
    await metricApi.addValue(currentMetric.value.id, { ...valueForm.value })
    ElMessage.success('已保存')
    valueForm.value = { period: '', dimensionKey: '', metricValue: undefined, note: '' }
    const res: any = await metricApi.values(currentMetric.value.id); values.value = res.data || []
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { valueSaving.value = false }
}
async function removeValue(row: any) {
  try {
    await metricApi.removeValue(currentMetric.value.id, row.id)
    values.value = values.value.filter(v => v.id !== row.id)
    ElMessage.success('已删除')
  } catch (e: any) { ElMessage.error(e.message || '删除失败') }
}

onMounted(loadAll)
</script>

<style scoped>
.search-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.value-form { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.metric-rule-collapse { margin: 4px 0 16px 100px; }
.metric-boundary-tip { margin: 0 0 14px 100px; }
.metric-rule-collapse .el-alert { margin-bottom: 12px; }
</style>
