<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">模型管理</h2>
        <p class="page-desc">维护主题域模型与字段映射，支持模型发布、DDL 预览和资产合规对比。</p>
      </div>
      <div class="head-actions">
        <el-button @click="loadAll"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-button @click="triggerImport"><el-icon><Upload /></el-icon>导入模型</el-button>
        <input ref="importInput" type="file" accept=".json,application/json" style="display: none;" @change="onImportFile" />
        <el-button type="primary" @click="openCreateModel"><el-icon><Plus /></el-icon>新建模型</el-button>
      </div>
    </div>

    <div class="stat-grid" style="margin-bottom: 14px;">
      <div class="stat-card">
        <div class="stat-label">模型总数</div>
        <div class="stat-value">{{ models.length }}</div>
        <div class="stat-foot">已发布 {{ publishedCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">当前模型字段</div>
        <div class="stat-value">{{ modelFields.length }}</div>
        <div class="stat-foot">主键 {{ pkCount }} · 外键 {{ fkCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">当前模型状态</div>
        <div class="stat-value" style="font-size: 20px;">{{ selectedModel ? statusLabel(selectedModel.status) : '-' }}</div>
        <div class="stat-foot">{{ selectedModel?.domainCode || '未选择模型' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">最近更新</div>
        <div class="stat-value" style="font-size: 16px;">{{ selectedModel ? formatTime(selectedModel.updatedAt) : '-' }}</div>
        <div class="stat-foot">模型编码 / 状态 / 字段</div>
      </div>
    </div>

    <div class="model-layout">
      <aside class="panel model-aside">
        <div class="aside-toolbar">
          <el-input v-model="keyword" placeholder="搜索模型名称/编码" clearable :prefix-icon="Search" />
        </div>
        <el-radio-group v-model="statusFilter" size="small" class="status-filter">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="1">已发布</el-radio-button>
          <el-radio-button label="0">已归档</el-radio-button>
        </el-radio-group>
        <div class="model-list">
          <div v-for="m in filteredModels" :key="m.id" class="model-item"
               :class="{ active: selectedModelId === m.id }" @click="selectModel(m)">
            <div class="model-row">
              <b class="model-name">{{ m.domainName }}</b>
              <el-tag size="small" :type="Number(m.status) === 1 ? 'success' : 'info'">
                {{ statusLabel(m.status) }}
              </el-tag>
            </div>
            <div class="model-code">{{ m.domainCode }}</div>
            <div class="model-desc">{{ m.description || '暂无描述' }}</div>
          </div>
          <el-empty v-if="!filteredModels.length" description="暂无匹配模型" :image-size="56" />
        </div>
      </aside>

      <section class="model-main">
        <template v-if="selectedModel">
          <div class="panel model-header">
            <div class="mh-left">
              <div class="mh-title">
                {{ selectedModel.domainName }}
                <el-tag size="small" :type="Number(selectedModel.status) === 1 ? 'success' : 'info'">
                  {{ statusLabel(selectedModel.status) }}
                </el-tag>
              </div>
              <div class="mh-sub">{{ selectedModel.domainCode }} · {{ selectedModel.description || '暂无描述' }}</div>
            </div>
            <div class="mh-actions">
              <el-button size="small" @click="openEditModel">编辑</el-button>
              <el-button size="small" @click="openCloneModel">复制</el-button>
              <el-button size="small" @click="exportModel">导出</el-button>
              <el-button v-if="Number(selectedModel.status) !== 1" size="small" type="success" plain @click="publishModel">发布</el-button>
              <el-button v-else size="small" type="warning" plain @click="archiveModel">归档</el-button>
              <el-button size="small" type="danger" plain @click="removeModel">删除</el-button>
            </div>
          </div>

          <div class="panel" style="margin-top: 14px;">
            <el-tabs v-model="rightTab">
              <el-tab-pane label="字段定义" name="fields">
                <div class="tab-toolbar">
                  <span class="muted">共 {{ modelFields.length }} 个字段 · 主键 {{ pkCount }} · 外键 {{ fkCount }}</span>
                  <el-button type="primary" size="small" @click="openAddField"><el-icon><Plus /></el-icon>添加字段</el-button>
                </div>
                <el-table :data="modelFields" stripe>
                  <el-table-column prop="sortOrder" label="排序" width="60" />
                  <el-table-column label="目标字段" min-width="150">
                    <template #default="{ row }"><b>{{ row.targetColumnName || row.fieldName }}</b></template>
                  </el-table-column>
                  <el-table-column label="类型" width="110">
                    <template #default="{ row }">{{ row.targetColumnType || row.dataType || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="主键" width="70">
                    <template #default="{ row }">
                      <el-tag v-if="Number(row.isPrimaryKey) === 1" size="small" type="warning">PK</el-tag>
                      <span v-else class="muted">-</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="外键" width="70">
                    <template #default="{ row }">
                      <el-tag v-if="Number(row.isForeignKey) === 1" size="small" type="info">FK</el-tag>
                      <span v-else class="muted">-</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="来源" min-width="150" show-overflow-tooltip>
                    <template #default="{ row }">{{ sourceLabel(row) }}</template>
                  </el-table-column>
                  <el-table-column label="转换规则" min-width="160" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.transformRule || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="操作" width="190" fixed="right">
                    <template #default="{ row }">
                      <el-button text size="small" :disabled="modelFields[0]?.id === row.id" @click="moveField(row, -1)">↑</el-button>
                      <el-button text size="small" :disabled="modelFields[modelFields.length - 1]?.id === row.id" @click="moveField(row, 1)">↓</el-button>
                      <el-button text type="primary" size="small" @click="editField(row)">编辑</el-button>
                      <el-button text type="danger" size="small" @click="removeField(row)">删除</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <el-empty v-if="!modelFields.length" description="暂无字段定义" :image-size="60" />
              </el-tab-pane>

              <el-tab-pane label="DDL 预览" name="ddl">
                <div class="tab-toolbar">
                  <span class="muted">根据当前字段定义生成，执行前请确认类型与主键。</span>
                  <div>
                    <el-button size="small" @click="copyDdl">复制</el-button>
                    <el-button size="small" @click="downloadDdl">下载 .sql</el-button>
                  </div>
                </div>
                <pre class="ddl-block">{{ ddl }}</pre>
              </el-tab-pane>

              <el-tab-pane label="对比资产" name="compare">
                <div class="tab-toolbar">
                  <span class="muted">选择资产：</span>
                  <el-select v-model="compareAssetId" placeholder="选择要对比的资产" style="width: 320px;" filterable @change="doCompare">
                    <el-option v-for="a in comparableAssets" :key="a.id"
                               :label="`${a.assetName} (${a.tableName}) · ${a.fieldCount}字段`" :value="a.id" />
                  </el-select>
                </div>
                <template v-if="compareResult">
                  <el-alert
                    :title="`合规率: ${compareResult.summary.complianceRate}%`"
                    :type="compareResult.summary.complianceRate >= 80 ? 'success' : compareResult.summary.complianceRate >= 50 ? 'warning' : 'error'"
                    :description="`模型 ${compareResult.summary.totalModelFields} 字段，资产 ${compareResult.summary.totalAssetFields} 字段；匹配 ${compareResult.summary.matched}，缺失 ${compareResult.summary.missing}，多余 ${compareResult.summary.extra}，类型/长度不一致 ${compareResult.summary.mismatched}`"
                    show-icon style="margin-bottom: 12px;"
                  />
                  <el-descriptions :column="2" border size="small" style="margin-bottom: 12px;">
                    <el-descriptions-item label="模型">{{ compareResult.modelName }}</el-descriptions-item>
                    <el-descriptions-item label="资产">{{ compareResult.assetName }}</el-descriptions-item>
                  </el-descriptions>
                  <el-table v-if="compareResult.missing.length" :data="compareResult.missing" stripe max-height="220" style="margin-bottom: 12px;">
                    <el-table-column label="缺失字段（模型有，资产无）" min-width="180">
                      <template #default="{ row }">{{ row.fieldName }}</template>
                    </el-table-column>
                    <el-table-column prop="expectedType" label="期望类型" width="100" />
                    <el-table-column prop="expectedLength" label="期望长度" width="90" />
                  </el-table>
                  <el-table v-if="compareResult.mismatched.length" :data="compareResult.mismatched" stripe max-height="220" style="margin-bottom: 12px;">
                    <el-table-column label="类型/长度不一致" min-width="180">
                      <template #default="{ row }">{{ row.fieldName }}</template>
                    </el-table-column>
                    <el-table-column label="差异" min-width="240">
                      <template #default="{ row }">
                        <div v-for="d in row.differences" :key="d.property" style="font-size: 12px;">
                          {{ d.property }}: {{ d.expected }} → {{ d.actual }}
                        </div>
                      </template>
                    </el-table-column>
                  </el-table>
                  <el-table v-if="compareResult.extra.length" :data="compareResult.extra" stripe max-height="220">
                    <el-table-column label="多余字段（资产有，模型无）" min-width="180">
                      <template #default="{ row }">{{ row.fieldName }}</template>
                    </el-table-column>
                    <el-table-column prop="dataType" label="类型" width="100" />
                    <el-table-column prop="dataLength" label="长度" width="80" />
                  </el-table>
                  <el-empty v-if="!compareResult.missing.length && !compareResult.mismatched.length && !compareResult.extra.length"
                            description="完全匹配，字段全部符合模型标准" />
                </template>
                <el-empty v-else description="选择一个资产开始对比" :image-size="60" />
              </el-tab-pane>
            </el-tabs>
          </div>
        </template>
        <div v-else class="panel empty-panel">
          <el-empty description="请从左侧选择模型，或新建一个模型" />
        </div>
      </section>
    </div>

    <!-- 新建/编辑模型 -->
    <el-dialog v-model="showForm" :title="isEdit ? '编辑模型' : '新建模型'" width="520px" top="22vh">
      <el-form :model="form" label-width="90px">
        <el-form-item label="模型名称" required><el-input v-model="form.domainName" placeholder="例如：患者核心模型" /></el-form-item>
        <el-form-item label="模型编码" required><el-input v-model="form.domainCode" placeholder="例如：PATIENT_CORE" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button type="primary" @click="submitModel">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showClone" title="复制模型" width="500px" top="20vh">
      <el-form :model="cloneForm" label-width="90px">
        <el-form-item label="新模型名称" required><el-input v-model="cloneForm.domainName" /></el-form-item>
        <el-form-item label="新模型编码" required><el-input v-model="cloneForm.domainCode" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="cloneForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showClone = false">取消</el-button>
        <el-button type="primary" :loading="cloning" @click="submitClone">复制</el-button>
      </template>
    </el-dialog>

    <!-- 添加/编辑字段 -->
    <el-dialog v-model="showFieldForm" :title="isEditField ? '编辑字段' : '添加字段'" width="640px" top="12vh">
      <el-form :model="fieldForm" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="模型类型">
              <el-select v-model="fieldForm.modelType" style="width: 100%;">
                <el-option label="事实表 FACT" value="FACT" />
                <el-option label="维度表 DIMENSION" value="DIMENSION" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序号">
              <el-input-number v-model="fieldForm.sortOrder" :min="1" :max="9999" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="来源表">
          <el-select v-model="fieldForm.sourceTableId" filterable clearable placeholder="可选：从资产表映射" style="width: 100%;"
                     @change="onSourceTableChange">
            <el-option v-for="t in sourceTables" :key="t.id" :label="`${t.assetName} (${t.tableName})`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源字段">
          <el-select v-model="fieldForm.sourceColumnId" filterable clearable placeholder="先选择来源表" style="width: 100%;"
                     :disabled="!fieldForm.sourceTableId" @change="onSourceColumnChange">
            <el-option v-for="c in sourceColumns" :key="c.id" :label="`${c.columnName} ${c.columnType || ''}`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="14">
            <el-form-item label="目标字段" required><el-input v-model="fieldForm.targetColumnName" placeholder="例如：patient_name" /></el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="类型" required>
              <el-select v-model="fieldForm.targetColumnType" filterable allow-create style="width: 100%;">
                <el-option v-for="t in TYPE_OPTIONS" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="主键"><el-switch v-model="fieldForm.isPrimaryKey" :active-value="1" :inactive-value="0" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="外键"><el-switch v-model="fieldForm.isForeignKey" :active-value="1" :inactive-value="0" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="引用维度">
              <el-select v-model="fieldForm.refDimensionId" clearable placeholder="可选" style="width: 100%;">
                <el-option v-for="m in refModels" :key="m.id" :label="m.domainName" :value="m.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="转换规则"><el-input v-model="fieldForm.transformRule" type="textarea" :rows="2" placeholder="如：UPPER(patient_name)" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFieldForm = false">取消</el-button>
        <el-button type="primary" @click="submitField">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { modelApi, catalogApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'

const TYPE_OPTIONS = ['VARCHAR(100)', 'VARCHAR(200)', 'VARCHAR(500)', 'CHAR(10)', 'INT', 'BIGINT', 'DECIMAL(18,2)', 'FLOAT', 'DATE', 'DATETIME', 'TIMESTAMP', 'TEXT', 'BOOLEAN']

const models = ref<any[]>([])
const selectedModelId = ref<number>(0)
const selectedModel = ref<any>(null)
const modelFields = ref<any[]>([])
const rightTab = ref('fields')
const keyword = ref('')
const statusFilter = ref('')
const loading = ref(false)

const showForm = ref(false)
const isEdit = ref(false)
const form = ref<any>({})
const showClone = ref(false)
const cloning = ref(false)
const cloneForm = ref<any>({})
const importInput = ref<HTMLInputElement>()

const showFieldForm = ref(false)
const isEditField = ref(false)
const fieldForm = ref<any>({})
const sourceTables = ref<any[]>([])
const sourceColumns = ref<any[]>([])

const comparableAssets = ref<any[]>([])
const compareAssetId = ref<number>()
const compareResult = ref<any>(null)

const filteredModels = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return models.value.filter(m => {
    if (statusFilter.value !== '' && String(m.status) !== statusFilter.value) return false
    if (!kw) return true
    return String(m.domainName || '').toLowerCase().includes(kw)
      || String(m.domainCode || '').toLowerCase().includes(kw)
  })
})
const publishedCount = computed(() => models.value.filter(m => Number(m.status) === 1).length)
const pkCount = computed(() => modelFields.value.filter(f => Number(f.isPrimaryKey) === 1).length)
const fkCount = computed(() => modelFields.value.filter(f => Number(f.isForeignKey) === 1).length)
const refModels = computed(() => models.value.filter(m => m.id !== selectedModelId.value))

const ddl = computed(() => {
  if (!selectedModel.value) return ''
  const name = selectedModel.value.domainCode || 'model_table'
  if (!modelFields.value.length) {
    return `-- ${selectedModel.value.domainName}\n-- 暂无字段，添加字段后生成 DDL`
  }
  const pks = modelFields.value.filter(f => Number(f.isPrimaryKey) === 1).map(f => f.targetColumnName || f.fieldName)
  const cols = modelFields.value.map(f => {
    const type = f.targetColumnType || f.dataType || 'VARCHAR(100)'
    const notNull = Number(f.isPrimaryKey) === 1 ? ' NOT NULL' : ''
    return `  ${f.targetColumnName || f.fieldName} ${type}${notNull}`
  })
  if (pks.length) cols.push(`  PRIMARY KEY (${pks.join(', ')})`)
  return `-- ${selectedModel.value.domainName}\nCREATE TABLE ${name} (\n${cols.join(',\n')}\n);`
})

const statusLabel = (v: any) => Number(v) === 1 ? '已发布' : '已归档'
const formatTime = (v: string) => v ? String(v).replace('T', ' ').slice(0, 16) : '-'

function sourceLabel(f: any) {
  if (!f.sourceTableId) return '-'
  const t = sourceTables.value.find(x => x.id === f.sourceTableId)
  return t ? (t.assetName || t.tableName) : `表#${f.sourceTableId}`
}

async function loadAll() {
  await Promise.all([loadModels(), loadSourceTables()])
}

async function loadModels() {
  loading.value = true
  try {
    const res: any = await modelApi.getModels()
    models.value = res.data || []
    if (selectedModelId.value) {
      const found = models.value.find(m => m.id === selectedModelId.value)
      if (found) {
        selectedModel.value = found
        await loadFields(found.id)
      } else {
        selectedModelId.value = 0
        selectedModel.value = null
        modelFields.value = []
      }
    } else if (models.value.length) {
      await selectModel(models.value[0])
    }
  } catch (e: any) {
    ElMessage.error(e.message || '加载模型失败')
  } finally {
    loading.value = false
  }
}

async function loadSourceTables() {
  try {
    const res: any = await catalogApi.searchAssets({ assetType: 'table', pageSize: 200, page: 1 })
    sourceTables.value = res.data?.records || []
  } catch (_e) { sourceTables.value = [] }
}

async function selectModel(m: any) {
  selectedModelId.value = m.id
  selectedModel.value = m
  rightTab.value = 'fields'
  compareAssetId.value = undefined
  compareResult.value = null
  await loadFields(m.id)
  await loadComparableAssets()
}

async function loadFields(modelId: number) {
  try {
    const res: any = await modelApi.getModelFields(modelId)
    modelFields.value = res.data || []
  } catch (_e) { modelFields.value = [] }
}

async function loadComparableAssets() {
  try {
    const res: any = await modelApi.getComparableAssets()
    comparableAssets.value = res.data || []
  } catch (_e) { comparableAssets.value = [] }
}

function openCreateModel() {
  isEdit.value = false
  form.value = { domainName: '', domainCode: '', description: '' }
  showForm.value = true
}

function openEditModel() {
  if (!selectedModel.value) return
  isEdit.value = true
  form.value = { ...selectedModel.value }
  showForm.value = true
}

async function submitModel() {
  if (!form.value.domainName?.trim()) { ElMessage.warning('请输入模型名称'); return }
  if (!form.value.domainCode?.trim()) { ElMessage.warning('请输入模型编码'); return }
  try {
    const res: any = isEdit.value ? await modelApi.updateModel(form.value) : await modelApi.createModel(form.value)
    if (res.data?.id) selectedModelId.value = res.data.id
    ElMessage.success(isEdit.value ? '编辑成功' : '创建成功')
    showForm.value = false
    await loadModels()
  } catch (e: any) { ElMessage.error(e.message || '操作失败') }
}

async function publishModel() {
  if (!selectedModel.value) return
  try {
    await modelApi.publishModel(selectedModel.value.id)
    ElMessage.success('发布成功')
    await loadModels()
  } catch (e: any) { ElMessage.error(e.message || '发布失败') }
}

async function archiveModel() {
  if (!selectedModel.value) return
  try {
    await modelApi.archiveModel(selectedModel.value.id)
    ElMessage.success('已归档')
    await loadModels()
  } catch (e: any) { ElMessage.error(e.message || '归档失败') }
}

async function removeModel() {
  if (!selectedModel.value) return
  try {
    await ElMessageBox.confirm(`确认删除模型「${selectedModel.value.domainName}」？关联字段也会删除。`, '删除确认', { type: 'warning' })
    await modelApi.deleteModel(selectedModel.value.id)
    ElMessage.success('删除成功')
    selectedModelId.value = 0
    selectedModel.value = null
    modelFields.value = []
    await loadModels()
  } catch (_e) { /* 取消 */ }
}

function openAddField() {
  if (!selectedModel.value) return
  isEditField.value = false
  fieldForm.value = {
    modelId: selectedModel.value.id,
    modelType: 'FACT',
    sourceTableId: undefined,
    sourceColumnId: undefined,
    targetColumnName: '',
    targetColumnType: 'VARCHAR(100)',
    isPrimaryKey: 0,
    isForeignKey: 0,
    refDimensionId: undefined,
    transformRule: '',
    sortOrder: modelFields.value.length + 1
  }
  sourceColumns.value = []
  showFieldForm.value = true
}

async function editField(row: any) {
  isEditField.value = true
  fieldForm.value = { ...row }
  sourceColumns.value = []
  if (row.sourceTableId) {
    await loadSourceColumns(row.sourceTableId)
  }
  showFieldForm.value = true
}

async function onSourceTableChange(tableId: number) {
  fieldForm.value.sourceColumnId = undefined
  sourceColumns.value = []
  if (tableId) await loadSourceColumns(tableId)
}

async function loadSourceColumns(tableId: number) {
  try {
    const res: any = await catalogApi.getFields(tableId)
    sourceColumns.value = res.data || []
  } catch (_e) { sourceColumns.value = [] }
}

function onSourceColumnChange(columnId: number) {
  const c = sourceColumns.value.find(x => x.id === columnId)
  if (!c) return
  if (!fieldForm.value.targetColumnName) fieldForm.value.targetColumnName = c.columnName
  if (!fieldForm.value.targetColumnType) fieldForm.value.targetColumnType = c.columnType
}

async function submitField() {
  if (!fieldForm.value.targetColumnName?.trim()) { ElMessage.warning('请输入目标字段名'); return }
  if (!fieldForm.value.targetColumnType?.trim()) { ElMessage.warning('请选择数据类型'); return }
  try {
    if (isEditField.value) {
      await modelApi.updateModelField(fieldForm.value)
      ElMessage.success('字段已更新')
    } else {
      await modelApi.createModelField(selectedModelId.value, fieldForm.value)
      ElMessage.success('字段已添加')
    }
    showFieldForm.value = false
    await loadFields(selectedModelId.value)
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
}

async function removeField(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除字段「${row.targetColumnName || row.fieldName}」？`, '删除确认', { type: 'warning' })
    await modelApi.deleteModelField(row.id)
    ElMessage.success('字段已删除')
    await loadFields(selectedModelId.value)
  } catch (_e) { /* 取消 */ }
}

async function doCompare() {
  if (!compareAssetId.value) return
  try {
    const res: any = await modelApi.compareModelWithAsset(selectedModelId.value, compareAssetId.value)
    compareResult.value = res.data
    rightTab.value = 'compare'
  } catch (e: any) { ElMessage.error(e.message || '对比失败') }
}

function openCloneModel() {
  if (!selectedModel.value) return
  cloneForm.value = {
    domainName: selectedModel.value.domainName + ' 副本',
    domainCode: selectedModel.value.domainCode + '_COPY',
    description: selectedModel.value.description || ''
  }
  showClone.value = true
}

async function submitClone() {
  if (!cloneForm.value.domainName?.trim() || !cloneForm.value.domainCode?.trim()) {
    ElMessage.warning('名称和编码不能为空'); return
  }
  cloning.value = true
  try {
    const res: any = await modelApi.createModel({ ...cloneForm.value, status: 0 })
    const newModel = res.data
    for (const f of modelFields.value) {
      await modelApi.createModelField(newModel.id, {
        modelType: f.modelType || 'FACT',
        sourceTableId: f.sourceTableId,
        sourceColumnId: f.sourceColumnId,
        targetColumnName: f.targetColumnName,
        targetColumnType: f.targetColumnType,
        isPrimaryKey: f.isPrimaryKey,
        isForeignKey: f.isForeignKey,
        refDimensionId: f.refDimensionId,
        transformRule: f.transformRule,
        sortOrder: f.sortOrder,
        status: 1
      })
    }
    ElMessage.success(`已复制模型和 ${modelFields.value.length} 个字段`)
    showClone.value = false
    selectedModelId.value = newModel.id
    await loadModels()
  } catch (e: any) { ElMessage.error(e.message || '复制失败') }
  finally { cloning.value = false }
}

function exportModel() {
  if (!selectedModel.value) return
  const payload = {
    exportedAt: new Date().toISOString(),
    model: {
      domainName: selectedModel.value.domainName,
      domainCode: selectedModel.value.domainCode,
      description: selectedModel.value.description
    },
    fields: modelFields.value.map(f => ({
      modelType: f.modelType,
      sourceTableId: f.sourceTableId,
      sourceColumnId: f.sourceColumnId,
      targetColumnName: f.targetColumnName,
      targetColumnType: f.targetColumnType,
      isPrimaryKey: f.isPrimaryKey,
      isForeignKey: f.isForeignKey,
      refDimensionId: f.refDimensionId,
      transformRule: f.transformRule,
      sortOrder: f.sortOrder
    }))
  }
  downloadJson(payload, `${selectedModel.value.domainCode || 'model'}.json`)
}

function downloadJson(data: any, filename: string) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

function triggerImport() { importInput.value?.click() }

async function onImportFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    const data = JSON.parse(await file.text())
    if (!data.model?.domainName || !data.model?.domainCode) throw new Error('文件缺少 model.domainName/domainCode')
    await ElMessageBox.confirm(`导入模型「${data.model.domainName}」及 ${(data.fields || []).length} 个字段？`, '导入确认', { type: 'info' })
    const res: any = await modelApi.createModel({
      domainName: data.model.domainName,
      domainCode: data.model.domainCode,
      description: data.model.description || '',
      status: 0
    })
    for (const f of (data.fields || [])) {
      await modelApi.createModelField(res.data.id, {
        modelType: f.modelType || 'FACT',
        sourceTableId: f.sourceTableId,
        sourceColumnId: f.sourceColumnId,
        targetColumnName: f.targetColumnName,
        targetColumnType: f.targetColumnType,
        isPrimaryKey: f.isPrimaryKey ?? 0,
        isForeignKey: f.isForeignKey ?? 0,
        refDimensionId: f.refDimensionId,
        transformRule: f.transformRule,
        sortOrder: f.sortOrder,
        status: 1
      })
    }
    ElMessage.success('模型导入成功')
    selectedModelId.value = res.data.id
    await loadModels()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message || '导入失败')
  } finally {
    input.value = ''
  }
}

async function moveField(row: any, direction: number) {
  const index = modelFields.value.findIndex(f => f.id === row.id)
  const target = index + direction
  if (index < 0 || target < 0 || target >= modelFields.value.length) return
  const other = modelFields.value[target]
  const a = row.sortOrder ?? index + 1
  const b = other.sortOrder ?? target + 1
  try {
    await Promise.all([
      modelApi.updateModelField({ ...row, sortOrder: b }),
      modelApi.updateModelField({ ...other, sortOrder: a })
    ])
    await loadFields(selectedModelId.value)
  } catch (e: any) { ElMessage.error(e.message || '排序失败') }
}

async function copyDdl() {
  try {
    await navigator.clipboard.writeText(ddl.value)
    ElMessage.success('DDL 已复制')
  } catch (_e) {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}

function downloadDdl() {
  const blob = new Blob([ddl.value], { type: 'text/sql;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${selectedModel.value?.domainCode || 'model'}.sql`
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(loadAll)
</script>

<style scoped>
.model-layout { display: grid; grid-template-columns: 300px minmax(0, 1fr); gap: 16px; align-items: start; }
.model-aside { padding: 14px; position: sticky; top: 84px; }
.aside-toolbar { margin-bottom: 10px; }
.status-filter { width: 100%; margin-bottom: 10px; }
:deep(.status-filter .el-radio-button) { flex: 1; }
:deep(.status-filter .el-radio-button__inner) { width: 100%; }
.model-list { max-height: calc(100vh - 300px); overflow-y: auto; }
.model-item { padding: 10px; border-radius: 9px; cursor: pointer; border: 1px solid transparent; }
.model-item:hover { background: #F5F7FB; }
.model-item.active { background: var(--med-primary-lighter); border-color: #D6E6FF; }
.model-row { display: flex; align-items: center; justify-content: space-between; gap: 6px; }
.model-name { font-size: 13px; color: var(--med-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.model-code { font-family: ui-monospace, Menlo, monospace; font-size: 10.5px; color: var(--med-text-light); margin-top: 3px; }
.model-desc { font-size: 10.5px; color: var(--med-text-light); margin-top: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.model-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.mh-title { font-size: 17px; font-weight: 700; color: var(--med-text); display: flex; align-items: center; gap: 8px; }
.mh-sub { margin-top: 6px; color: var(--med-text-light); font-size: 12px; }
.mh-actions { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.tab-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.muted { color: var(--med-text-light); font-size: 12px; }
.ddl-block { background: #0F172A; color: #E2E8F0; border-radius: 10px; padding: 16px; font-family: ui-monospace, Menlo, monospace; font-size: 12px; line-height: 1.7; overflow: auto; max-height: 520px; white-space: pre; }
.empty-panel { min-height: 420px; display: flex; align-items: center; justify-content: center; }
@media (max-width: 1100px) {
  .model-layout { grid-template-columns: minmax(0, 1fr); }
  .model-aside { position: static; }
  .model-list { max-height: 260px; }
}
</style>
