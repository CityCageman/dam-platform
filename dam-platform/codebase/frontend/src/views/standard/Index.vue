<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">数据标准</h2>
        <p class="page-desc">维护码表、标准值域与字段映射，发布后可执行自动校验并生成偏差报告。</p>
      </div>
      <div class="head-actions">
        <el-button @click="loadAll"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-button :loading="validating" @click="autoValidate"><el-icon><Connection /></el-icon>自动校验</el-button>
        <el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新建标准</el-button>
      </div>
    </div>

    <div class="stat-grid" style="margin-bottom: 14px;">
      <div class="stat-card">
        <div class="stat-label">标准总数</div>
        <div class="stat-value">{{ coverage.totalStandards ?? total }}</div>
        <div class="stat-foot">已发布 {{ coverage.publishedStandards ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">草稿/未发布</div>
        <div class="stat-value">{{ coverage.draftStandards ?? 0 }}</div>
        <div class="stat-foot">发布后才能参与自动校验</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">字段映射</div>
        <div class="stat-value">{{ coverage.totalMappings ?? 0 }}</div>
        <div class="stat-foot">源码值 → 标准码值</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">覆盖率</div>
        <div class="stat-value">{{ coverage.coverageRate ?? 0 }}%</div>
        <div class="stat-foot">映射数 / 标准数</div>
      </div>
    </div>

    <div class="panel">
      <div class="std-filter">
        <el-input v-model="keyword" placeholder="搜索码表名称/编码" clearable style="width: 260px;" @keyup.enter="search" />
        <el-select v-model="stdType" placeholder="标准类型" clearable style="width: 140px;" @change="search">
          <el-option v-for="t in TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-select v-model="status" placeholder="状态" clearable style="width: 120px;" @change="search">
          <el-option label="已发布" :value="1" />
          <el-option label="草稿/未发布" :value="0" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
        <div style="flex: 1;" />
        <span class="muted">共 {{ total }} 条标准</span>
      </div>

      <el-table :data="standardList" stripe v-loading="loading" style="cursor: pointer;" @row-click="viewDetail">
        <el-table-column prop="codeTableName" label="码表名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="codeTableCode" label="码表编码" width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.codeTableType)">{{ typeLabel(row.codeTableType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="Number(row.status) === 1 ? 'success' : 'info'">
              {{ Number(row.status) === 1 ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="160">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click.stop="viewDetail(row)">详情</el-button>
            <el-button text size="small" @click.stop="openEdit(row)">编辑</el-button>
            <el-button v-if="Number(row.status) !== 1" text type="success" size="small" @click.stop="publish(row)">发布</el-button>
            <el-button v-else text type="warning" size="small" @click.stop="archive(row)">归档</el-button>
            <el-button text type="danger" size="small" @click.stop="removeStandard(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !standardList.length" description="暂无标准，点击右上角新建" :image-size="70" />
      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total"
                       layout="total, prev, pager, next" @change="loadData" />
      </div>
    </div>

    <!-- 新建/编辑标准 -->
    <el-dialog v-model="showForm" :title="isEdit ? '编辑标准' : '新建标准'" width="560px" top="18vh">
      <el-form :model="form" label-width="90px">
        <el-form-item label="码表名称" required><el-input v-model="form.codeTableName" placeholder="例如：患者性别" /></el-form-item>
        <el-form-item label="码表编码" required><el-input v-model="form.codeTableCode" placeholder="例如：GENDER" /></el-form-item>
        <el-form-item label="标准类型">
          <el-select v-model="form.codeTableType" style="width: 100%;">
            <el-option v-for="t in TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button type="primary" @click="submitStandard">保存</el-button>
      </template>
    </el-dialog>

    <!-- 标准详情 -->
    <el-drawer v-model="showDetail" :title="detail?.codeTableName || '标准详情'" size="66%" append-to-body>
      <template v-if="detail">
        <div class="detail-head">
          <div>
            <b>{{ detail.codeTableName }}</b>
            <el-tag size="small" :type="Number(detail.status) === 1 ? 'success' : 'info'" style="margin-left: 8px;">
              {{ Number(detail.status) === 1 ? '已发布' : '草稿' }}
            </el-tag>
            <span class="muted" style="margin-left: 8px;">{{ detail.codeTableCode }}</span>
          </div>
          <div>
            <el-button size="small" @click="openEdit(detail)">编辑</el-button>
            <el-button v-if="Number(detail.status) !== 1" size="small" type="success" plain @click="publish(detail)">发布</el-button>
            <el-button v-else size="small" type="warning" plain @click="archive(detail)">归档</el-button>
          </div>
        </div>
        <el-descriptions :column="3" border size="small" style="margin-bottom: 14px;">
          <el-descriptions-item label="类型">{{ typeLabel(detail.codeTableType) }}</el-descriptions-item>
          <el-descriptions-item label="编码">{{ detail.codeTableCode }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ Number(detail.status) === 1 ? '已发布' : '草稿' }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="3">{{ detail.description || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="detailTab">
          <el-tab-pane label="标准值域" name="values">
            <div class="tab-toolbar">
              <span class="muted">共 {{ values.length }} 个码值</span>
              <el-button size="small" type="primary" @click="openAddValue"><el-icon><Plus /></el-icon>添加码值</el-button>
            </div>
            <el-table :data="values" stripe size="small">
              <el-table-column prop="codeValue" label="码值" width="130" />
              <el-table-column prop="codeName" label="名称" min-width="160" />
              <el-table-column prop="sortOrder" label="排序" width="70" />
              <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="Number(row.status) === 1 ? 'success' : 'info'">{{ Number(row.status) === 1 ? '启用' : '停用' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button text type="primary" size="small" @click="editValue(row)">编辑</el-button>
                  <el-button text type="danger" size="small" @click="removeValue(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!values.length" description="暂无码值，发布前建议先维护值域" :image-size="56" />
          </el-tab-pane>

          <el-tab-pane label="字段映射" name="mappings">
            <div class="tab-toolbar">
              <span class="muted">共 {{ mappings.length }} 条映射</span>
              <el-button size="small" type="primary" @click="openAddMapping"><el-icon><Plus /></el-icon>添加映射</el-button>
            </div>
            <el-table :data="mappings" stripe size="small">
              <el-table-column label="来源表" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">{{ sourceTableName(row.sourceTableId) }}</template>
              </el-table-column>
              <el-table-column prop="sourceColumn" label="来源字段" width="130" />
              <el-table-column prop="sourceValue" label="源码值" width="120" />
              <el-table-column prop="standardCodeValue" label="标准码值" width="120" />
              <el-table-column prop="mappingRule" label="映射规则" min-width="180" show-overflow-tooltip />
              <el-table-column label="操作" width="80" fixed="right">
                <template #default="{ row }">
                  <el-button text type="danger" size="small" @click="removeMapping(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!mappings.length" description="暂无字段映射" :image-size="56" />
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <!-- 码值 -->
    <el-dialog v-model="showValueForm" :title="isEditValue ? '编辑码值' : '添加码值'" width="500px" top="22vh">
      <el-form :model="valueForm" label-width="80px">
        <el-form-item label="码值" required><el-input v-model="valueForm.codeValue" placeholder="如：1" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="valueForm.codeName" placeholder="如：男" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="valueForm.sortOrder" :min="1" :max="9999" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="valueForm.status" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="valueForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showValueForm = false">取消</el-button>
        <el-button type="primary" @click="submitValue">保存</el-button>
      </template>
    </el-dialog>

    <!-- 映射 -->
    <el-dialog v-model="showMappingForm" title="添加字段映射" width="580px" top="16vh">
      <el-form :model="mappingForm" label-width="95px">
        <el-form-item label="来源表">
          <el-select v-model="mappingForm.sourceTableId" filterable clearable placeholder="选择资产表" style="width: 100%;"
                     @change="onMappingTableChange">
            <el-option v-for="t in sourceTables" :key="t.id" :label="`${t.assetName} (${t.tableName})`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源字段">
          <el-select v-model="mappingForm.sourceColumn" filterable allow-create clearable placeholder="选择或输入字段" style="width: 100%;">
            <el-option v-for="c in mappingColumns" :key="c.columnName" :label="c.columnName" :value="c.columnName" />
          </el-select>
        </el-form-item>
        <el-form-item label="源码值"><el-input v-model="mappingForm.sourceValue" placeholder="如：M" /></el-form-item>
        <el-form-item label="标准码值">
          <el-select v-model="mappingForm.standardCodeValue" filterable allow-create clearable placeholder="选择标准值域" style="width: 100%;">
            <el-option v-for="v in values" :key="v.id" :label="`${v.codeValue} - ${v.codeName}`" :value="v.codeValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="映射规则"><el-input v-model="mappingForm.mappingRule" type="textarea" :rows="2" placeholder="如：M→1，F→2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showMappingForm = false">取消</el-button>
        <el-button type="primary" @click="submitMapping">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { standardApi, catalogApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Connection } from '@element-plus/icons-vue'

const TYPE_OPTIONS = [
  { value: 'dictionary', label: '数据字典' },
  { value: 'naming', label: '命名规范' },
  { value: 'coding', label: '编码规范' },
  { value: 'format', label: '格式规范' }
]

const keyword = ref('')
const stdType = ref('')
const status = ref<any>('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const standardList = ref<any[]>([])
const coverage = ref<any>({})
const loading = ref(false)
const validating = ref(false)

const showForm = ref(false)
const isEdit = ref(false)
const form = ref<any>({})

const showDetail = ref(false)
const detail = ref<any>(null)
const detailTab = ref('values')
const values = ref<any[]>([])
const mappings = ref<any[]>([])
const sourceTables = ref<any[]>([])
const mappingColumns = ref<any[]>([])

const showValueForm = ref(false)
const isEditValue = ref(false)
const valueForm = ref<any>({})

const showMappingForm = ref(false)
const mappingForm = ref<any>({})

const typeLabel = (v: string) => TYPE_OPTIONS.find(t => t.value === v)?.label || v || '-'
const typeTag = (v: string) => v === 'dictionary' ? 'primary' : v === 'naming' ? 'success' : v === 'coding' ? 'warning' : 'info'
const formatTime = (v: string) => v ? String(v).replace('T', ' ').slice(0, 16) : '-'
const sourceTableName = (id: number) => {
  const t = sourceTables.value.find(x => x.id === id)
  return t ? (t.assetName || t.tableName) : (id ? `表#${id}` : '-')
}

async function loadAll() {
  await Promise.all([loadData(), loadCoverage(), loadSourceTables()])
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await standardApi.searchStandards({
      keyword: keyword.value || undefined,
      stdType: stdType.value || undefined,
      status: status.value === '' || status.value === null ? undefined : String(status.value),
      page: page.value,
      pageSize: pageSize.value
    })
    standardList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e: any) {
    ElMessage.error(e.message || '加载标准失败')
    standardList.value = []
  } finally { loading.value = false }
}

async function loadCoverage() {
  try {
    const res: any = await standardApi.getCoverage()
    coverage.value = res.data || {}
  } catch (_e) { coverage.value = {} }
}

async function loadSourceTables() {
  try {
    const res: any = await catalogApi.searchAssets({ assetType: 'table', pageSize: 200, page: 1 })
    sourceTables.value = res.data?.records || []
  } catch (_e) { sourceTables.value = [] }
}

function search() { page.value = 1; loadData() }
function resetSearch() { keyword.value = ''; stdType.value = ''; status.value = ''; page.value = 1; loadData() }

function openCreate() {
  isEdit.value = false
  form.value = { codeTableName: '', codeTableCode: '', codeTableType: 'dictionary', description: '', status: 0 }
  showForm.value = true
}

function openEdit(row: any) {
  isEdit.value = true
  form.value = { ...row }
  showForm.value = true
}

async function submitStandard() {
  if (!form.value.codeTableName?.trim()) { ElMessage.warning('请输入码表名称'); return }
  if (!form.value.codeTableCode?.trim()) { ElMessage.warning('请输入码表编码'); return }
  try {
    if (isEdit.value) await standardApi.updateStandard(form.value.id, form.value)
    else await standardApi.createStandard(form.value)
    ElMessage.success(isEdit.value ? '编辑成功' : '创建成功')
    showForm.value = false
    await loadAll()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
}

async function publish(row: any) {
  try {
    await standardApi.publishStandard(row.id)
    ElMessage.success('已发布')
    await loadAll()
    if (detail.value?.id === row.id) await loadDetail(row.id)
  } catch (e: any) { ElMessage.error(e.message || '发布失败') }
}

async function archive(row: any) {
  try {
    await standardApi.archiveStandard(row.id)
    ElMessage.success('已归档')
    await loadAll()
    if (detail.value?.id === row.id) await loadDetail(row.id)
  } catch (e: any) { ElMessage.error(e.message || '归档失败') }
}

async function removeStandard(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除标准「${row.codeTableName}」？值域与映射不会自动清理。`, '删除确认', { type: 'warning' })
    await standardApi.deleteStandard(row.id)
    ElMessage.success('删除成功')
    if (detail.value?.id === row.id) showDetail.value = false
    await loadAll()
  } catch (_e) { /* 取消 */ }
}

async function viewDetail(row: any) {
  await loadDetail(row.id)
  showDetail.value = true
  detailTab.value = 'values'
}

async function loadDetail(id: number) {
  try {
    const res: any = await standardApi.getStandardDetail(id)
    detail.value = res.data
    const [v, m] = await Promise.all([
      standardApi.getStandardValues(id).catch(() => ({ data: [] })),
      standardApi.getMappings(id).catch(() => ({ data: [] }))
    ])
    values.value = v.data || []
    mappings.value = m.data || []
  } catch (e: any) { ElMessage.error(e.message || '加载详情失败') }
}

function openAddValue() {
  if (!detail.value) return
  isEditValue.value = false
  valueForm.value = { codeTableId: detail.value.id, codeValue: '', codeName: '', sortOrder: values.value.length + 1, description: '', status: 1 }
  showValueForm.value = true
}

function editValue(row: any) {
  isEditValue.value = true
  valueForm.value = { ...row }
  showValueForm.value = true
}

async function submitValue() {
  if (!valueForm.value.codeValue?.trim() || !valueForm.value.codeName?.trim()) {
    ElMessage.warning('码值和名称不能为空'); return
  }
  try {
    if (isEditValue.value) await standardApi.updateStandardValue(valueForm.value.id, valueForm.value)
    else await standardApi.createStandardValue(valueForm.value)
    ElMessage.success('码值已保存')
    showValueForm.value = false
    await loadDetail(detail.value.id)
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
}

async function removeValue(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除码值「${row.codeValue} - ${row.codeName}」？`, '删除确认', { type: 'warning' })
    await standardApi.deleteStandardValue(row.id)
    ElMessage.success('已删除')
    await loadDetail(detail.value.id)
  } catch (_e) { /* 取消 */ }
}

function openAddMapping() {
  if (!detail.value) return
  mappingForm.value = {
    standardCodeTableId: detail.value.id,
    sourceTableId: undefined,
    sourceColumn: '',
    sourceValue: '',
    standardCodeValue: '',
    mappingRule: '',
    status: 1
  }
  mappingColumns.value = []
  showMappingForm.value = true
}

async function onMappingTableChange(tableId: number) {
  mappingForm.value.sourceColumn = ''
  mappingColumns.value = []
  if (!tableId) return
  try {
    const res: any = await catalogApi.getFields(tableId)
    mappingColumns.value = res.data || []
  } catch (_e) { mappingColumns.value = [] }
}

async function submitMapping() {
  if (!mappingForm.value.sourceTableId) { ElMessage.warning('请选择来源表'); return }
  if (!mappingForm.value.sourceColumn) { ElMessage.warning('请选择来源字段'); return }
  try {
    await standardApi.createMapping(mappingForm.value)
    ElMessage.success('映射已添加')
    showMappingForm.value = false
    await loadDetail(detail.value.id)
    await loadCoverage()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
}

async function removeMapping(row: any) {
  try {
    await ElMessageBox.confirm('确认删除该字段映射？', '删除确认', { type: 'warning' })
    await standardApi.removeMapping(row.id)
    ElMessage.success('已删除')
    await loadDetail(detail.value.id)
    await loadCoverage()
  } catch (_e) { /* 取消 */ }
}

async function autoValidate() {
  validating.value = true
  try {
    const res: any = await standardApi.autoValidate()
    ElMessage.success(`校验完成，发现 ${res.data?.deviationCount ?? 0} 个偏差`)
    await loadCoverage()
  } catch (e: any) { ElMessage.error(e.message || '校验失败') }
  finally { validating.value = false }
}

onMounted(loadAll)
</script>

<style scoped>
.std-filter { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.muted { color: var(--med-text-light); font-size: 12px; }
.detail-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 12px; }
.tab-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; gap: 10px; }
</style>
