<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">数据标准</h2>
        <p class="page-desc">统一字段命名、编码、类型与度量口径；码表定义标准答案，代码对照定义源系统到标准答案的翻译规则。</p>
      </div>
      <el-button v-if="tab === 'std'" type="primary" :icon="Plus" @click="openForm()">新增标准</el-button>
      <el-button v-else type="primary" :icon="Refresh" @click="loadCodeTables">刷新</el-button>
    </div>

    <el-tabs v-model="tab" class="std-tabs">
      <!-- ==================== 数据标准 ==================== -->
      <el-tab-pane label="数据标准" name="std">
        <div class="stats">
          <div class="panel conform">
            <h3 class="panel-title">字段映射符合情况</h3>
            <div class="conform-body">
              <div class="ring" :style="ringStyle">
                <div class="ring-inner">
                  <div class="ring-value">{{ conformance.conformRate || 0 }}%</div>
                  <div class="ring-label">符合率</div>
                </div>
              </div>
              <div class="legend">
                <div class="legend-item">
                  <span class="dot" style="background:#12b76a"></span>符合 <b>{{ conformance.conform || 0 }}</b>
                </div>
                <div class="legend-item">
                  <span class="dot" style="background:#f04438"></span>偏差 <b>{{ conformance.deviate || 0 }}</b>
                </div>
                <div class="legend-item">
                  <span class="dot" style="background:#98a2b3"></span>未映射 <b>{{ conformance.unmapped || 0 }}</b>
                </div>
                <div class="legend-total">共 {{ conformance.total || 0 }} 个受检字段</div>
              </div>
            </div>
          </div>

          <div class="panel">
            <h3 class="panel-title">标准覆盖 Top</h3>
            <div class="top-list">
              <div class="top-item" v-for="s in (conformance.byStandard || []).slice(0, 5)" :key="s.id">
                <span class="top-name">{{ s.name }}</span>
                <el-progress :percentage="s.mapped ? Math.round((s.conform / s.mapped) * 100) : 0"
                             :stroke-width="6" :show-text="false" style="flex:1" />
                <span class="muted">{{ s.conform }}/{{ s.mapped }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="panel">
          <div class="toolbar">
            <el-input v-model="filters.keyword" placeholder="搜索标准名称 / 编码 / 规则" clearable :prefix-icon="Search"
                      style="width: 260px" @keyup.enter="load" @clear="load" />
            <el-select v-model="filters.type" placeholder="标准类型" clearable style="width: 130px" @change="load">
              <el-option v-for="t in types" :key="t" :label="t" :value="t" />
            </el-select>
            <el-select v-model="filters.status" placeholder="状态" clearable style="width: 120px" @change="load">
              <el-option label="生效中" value="ACTIVE" />
              <el-option label="草稿" value="DRAFT" />
              <el-option label="已废止" value="DEPRECATED" />
            </el-select>
            <div class="spacer"></div>
            <span class="muted">{{ rows.length }} 条标准</span>
          </div>

          <el-table :data="rows" size="small">
            <el-table-column prop="code" label="标准编号" width="110">
              <template #default="{ row }"><span class="mono">{{ row.code }}</span></template>
            </el-table-column>
            <el-table-column prop="name" label="标准名称" min-width="130" />
            <el-table-column prop="categoryName" label="分类" width="110" />
            <el-table-column label="类型" width="90">
              <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.stdType }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="ruleContent" label="规则内容" min-width="280" show-overflow-tooltip />
            <el-table-column prop="exampleValue" label="示例" width="140" show-overflow-tooltip />
            <el-table-column label="映射" width="110">
              <template #default="{ row }">
                <span>{{ row.conformCount }}/{{ row.mappingCount }}</span>
                <span class="muted"> 符合</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : row.status === 'DRAFT' ? 'info' : 'warning'">
                  {{ row.status === 'ACTIVE' ? '生效中' : row.status === 'DRAFT' ? '草稿' : '已废止' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="70" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button size="small" text type="primary" @click="openForm(row)">编辑</el-button>
                <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ==================== 标准码表 ==================== -->
      <el-tab-pane label="标准码表" name="code">
        <div class="panel hint-panel">
          <div class="hint-body">
            <span class="hint-label">代码对照归口</span>
            <span>
              码表与对照规则在本平台定义，数据中台通过 <code>GET /openapi/standards/codes</code> 只读拉取到
              <code>mid.code_crosswalk</code> 镜像后执行转换。改动对照后，到中台点一次「重算 DIM/DWD」即可生效。
            </span>
          </div>
        </div>

        <div class="panel hint-panel">
          <div class="hint-body">
            <span class="hint-label">码值来源</span>
            <span>
              凡国家标准（GB/GB·T）、卫生行业标准（WS/WS·T）、卫健委数据字典（CT 系列）中有对应码表的，
              <b>码值一律采用标准原值</b>，不自造；标准中确实没有的（如病区、院内药品）标为「院内扩展」，
              标准表缺少的个别值（如频次 tid）也保留原值并单独标记，不强行并到「其他」以免丢语义。
            </span>
          </div>
        </div>

        <div class="panel">
          <div class="toolbar">
            <el-input v-model="codeFilter" placeholder="搜索码表编码 / 名称 / 标准编号" clearable :prefix-icon="Search"
                      style="width: 280px" @keyup.enter="loadCodeTables" @clear="loadCodeTables" />
            <div class="spacer"></div>
            <span class="muted">
              {{ codeTables.length }} 张码表 ·
              有标准依据 {{ codeTables.filter(t => t.standardSource && t.standardSource !== 'LOCAL').length }} 张 ·
              院内扩展 {{ codeTables.filter(t => t.standardSource === 'LOCAL').length }} 张
            </span>
          </div>

          <el-table :data="codeTables" size="small" @row-click="openCodeTable" class="clickable">
            <el-table-column prop="codeTableCode" label="码表编码" width="150">
              <template #default="{ row }"><span class="mono">{{ row.codeTableCode }}</span></template>
            </el-table-column>
            <el-table-column prop="codeTableName" label="码表名称" min-width="140" />
            <el-table-column label="标准依据" min-width="230">
              <template #default="{ row }">
                <template v-if="row.standardId">
                  <el-tag size="small" :type="srcTagType(row.standardSource)" effect="dark" class="tag-gap">
                    {{ srcLabel(row.standardSource) }}
                  </el-tag>
                  <span class="mono small">{{ row.standardId }}</span>
                </template>
                <span v-else class="muted small">院内扩展，标准中无对应码表</span>
              </template>
            </el-table-column>
            <el-table-column prop="valueCount" label="标准码值" width="96" align="right">
              <template #default="{ row }">
                {{ row.valueCount }}
                <el-tooltip v-if="row.extensionValueCount" :content="`其中 ${row.extensionValueCount} 条为院内扩展（标准中无此值）`">
                  <span class="ext-flag">+{{ row.extensionValueCount }}</span>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="已对照码值" width="130">
              <template #default="{ row }">
                <el-progress :percentage="row.valueCount ? Math.round((row.coveredValueCount / row.valueCount) * 100) : 0"
                             :stroke-width="5" :show-text="false" />
                <span class="muted small">{{ row.coveredValueCount }}/{{ row.valueCount }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="crosswalkCount" label="对照条数" width="90" align="right" />
            <el-table-column label="来源系统" width="130">
              <template #default="{ row }">
                <el-tag v-for="s in (row.sourceSystems || [])" :key="s" size="small" effect="plain" class="tag-gap">{{ s }}</el-tag>
                <span v-if="!row.sourceCount" class="muted small">未配置</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button size="small" text type="primary" @click.stop="openCodeTable(row)">查看对照</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 标准编辑 -->
    <el-dialog v-model="dialog" :title="form.id ? '编辑标准' : '新增标准'" width="600px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="标准名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.categoryId" style="width:100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标准类型">
          <el-select v-model="form.stdType" style="width:100%">
            <el-option v-for="t in types" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则内容">
          <el-input v-model="form.ruleContent" type="textarea" :rows="3" placeholder="如：18 位，符合 GB 11643-1999" />
        </el-form-item>
        <el-form-item label="示例值">
          <el-input v-model="form.exampleValue" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.owner" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="生效中" value="ACTIVE" />
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已废止" value="DEPRECATED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 码表详情：码值 + 源系统对照 -->
    <el-drawer v-model="drawer" :title="current ? `${current.codeTableName}（${current.codeTableCode}）` : ''" size="920px">
      <div v-if="current" class="std-ref">
        <template v-if="current.standardId">
          <el-tag size="small" :type="srcTagType(current.standardSource)" effect="dark">{{ srcLabel(current.standardSource) }}</el-tag>
          <span class="mono">{{ current.standardId }}</span>
          <span class="muted">{{ current.standardName }}</span>
          <span v-if="current.standardVersion" class="muted small">· {{ current.standardVersion }}</span>
        </template>
        <template v-else>
          <el-tag size="small" type="info" effect="dark">院内扩展</el-tag>
          <span class="muted">{{ current.standardName || '标准中无对应码表，码值由院内定义' }}</span>
        </template>
      </div>

      <el-tabs v-model="drawerTab">
        <el-tab-pane :label="`标准码值（${codeValues.length}）`" name="values">
          <el-table :data="codeValues" size="small" height="calc(100vh - 260px)">
            <el-table-column prop="codeValue" label="码值" width="120">
              <template #default="{ row }"><span class="mono">{{ row.codeValue }}</span></template>
            </el-table-column>
            <el-table-column prop="codeName" label="名称" min-width="160" />
            <el-table-column label="来源" width="96">
              <template #default="{ row }">
                <el-tag v-if="row.isExtension" size="small" type="warning" effect="plain">院内扩展</el-tag>
                <span v-else class="muted small">标准值</span>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="190" show-overflow-tooltip />
            <el-table-column label="被哪些源值映射到" min-width="210">
              <template #default="{ row }">
                <el-tag v-for="c in mappedSources(row.codeValue)" :key="c.id" size="small" effect="plain" class="tag-gap">
                  {{ c.sourceSystem }}:{{ c.sourceValue }}
                </el-tag>
                <span v-if="!mappedSources(row.codeValue).length" class="muted small">无</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`源系统对照（${crosswalkRows.length}）`" name="crosswalk">
          <div class="toolbar">
            <el-select v-model="cwFilter.sourceSystem" placeholder="来源系统" clearable style="width: 140px"
                       @change="loadCrosswalk">
              <el-option v-for="s in crosswalkSources" :key="s" :label="s" :value="s" />
            </el-select>
            <div class="spacer"></div>
            <el-button type="primary" size="small" :icon="Plus" @click="openCwForm()">新增对照</el-button>
          </div>

          <el-table :data="crosswalkRows" size="small" height="calc(100vh - 280px)">
            <el-table-column prop="sourceSystem" label="来源系统" width="100" />
            <el-table-column label="源表.字段" min-width="200">
              <template #default="{ row }">
                <span v-if="row.sourceTable" class="mono small">{{ row.sourceTable }}.{{ row.sourceColumn }}</span>
                <span v-else class="muted small">未绑定</span>
              </template>
            </el-table-column>
            <el-table-column label="源码值" width="110">
              <template #default="{ row }"><span class="mono">{{ row.sourceValue }}</span></template>
            </el-table-column>
            <el-table-column prop="sourceName" label="源码含义" width="120" />
            <el-table-column label="→ 标准码值" width="130">
              <template #default="{ row }">
                <span class="mono">{{ row.targetValue }}</span>
                <span class="muted small"> {{ row.targetName }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="priority" label="优先级" width="80" align="right" />
            <el-table-column prop="remark" label="依据/备注" min-width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button size="small" text type="primary" @click="openCwForm(row)">编辑</el-button>
                <el-button size="small" text type="danger" @click="removeCw(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <!-- 对照编辑 -->
    <el-dialog v-model="cwDialog" :title="cwForm.id ? '编辑代码对照' : '新增代码对照'" width="620px">
      <el-form :model="cwForm" label-width="110px">
        <el-form-item label="来源系统" required>
          <el-select v-model="cwForm.sourceSystem" allow-create filterable default-first-option
                     placeholder="如 HIS / LIS / PACS" style="width:100%">
            <el-option v-for="s in crosswalkSources" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="源表">
          <el-input v-model="cwForm.sourceTable" placeholder="如 PA_PatMas（填了才能被中台扫描发现未对照值）" />
        </el-form-item>
        <el-form-item label="源字段">
          <el-input v-model="cwForm.sourceColumn" placeholder="如 maritalstatus" />
        </el-form-item>
        <el-form-item label="源码值" required>
          <el-input v-model="cwForm.sourceValue" placeholder="源系统里实际存的值，如 1 或 M" />
        </el-form-item>
        <el-form-item label="源码含义">
          <el-input v-model="cwForm.sourceName" placeholder="可选，便于人工核对" />
        </el-form-item>
        <el-form-item label="映射到标准码值" required>
          <el-select v-model="cwForm.targetValue" filterable style="width:100%">
            <el-option v-for="v in codeValues" :key="v.codeValue" :label="`${v.codeValue}  ${v.codeName}`" :value="v.codeValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="cwForm.priority" :min="1" :max="999" />
        </el-form-item>
        <el-form-item label="依据/备注">
          <el-input v-model="cwForm.remark" type="textarea" :rows="2" placeholder="如：基线映射，待院方确认" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cwDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCw">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { api } from '../api'

const tab = ref('std')

const rows = ref([])
const categories = ref([])
const conformance = ref({})
const dialog = ref(false)
const types = ['命名', '编码', '类型', '枚举', '度量']

const filters = reactive({ keyword: '', type: '', status: '' })

const form = reactive({
  id: null, name: '', categoryId: null, stdType: '编码', ruleContent: '',
  exampleValue: '', owner: '', status: 'ACTIVE'
})

// 码表
const codeTables = ref([])
const codeFilter = ref('')
const drawer = ref(false)
const drawerTab = ref('values')
const current = ref(null)
const codeValues = ref([])
const crosswalkRows = ref([])
const crosswalkSources = ref([])
const cwFilter = reactive({ sourceSystem: '' })
const cwDialog = ref(false)
const cwForm = reactive({
  id: null, codeTableId: null, sourceSystem: 'HIS', sourceTable: '', sourceColumn: '',
  sourceValue: '', sourceName: '', targetValue: '', priority: 100, remark: ''
})

const ringStyle = computed(() => {
  const rate = conformance.value.conformRate || 0
  const deg = Math.round(rate * 3.6)
  return {
    background: `conic-gradient(#12b76a 0 ${deg}deg, #eef1f7 ${deg}deg 360deg)`
  }
})

// 标准层级的中文标识与配色
const SRC_LABEL = { GB: '国标', WS: '行标', NHC: '卫健委', LOCAL: '院内' }
const SRC_TAG = { GB: 'danger', WS: 'warning', NHC: 'primary', LOCAL: 'info' }
const srcLabel = (s) => SRC_LABEL[s] || '未标注'
const srcTagType = (s) => SRC_TAG[s] || 'info'

async function load() {
  rows.value = await api.standards({
    keyword: filters.keyword || undefined,
    type: filters.type || undefined,
    status: filters.status || undefined
  })
  conformance.value = await api.conformance()
}

function openForm(row) {
  if (row) Object.assign(form, row)
  else Object.assign(form, {
    id: null, name: '', categoryId: null, stdType: '编码', ruleContent: '',
    exampleValue: '', owner: '', status: 'ACTIVE'
  })
  dialog.value = true
}

async function save() {
  if (!form.name.trim()) return ElMessage.warning('请填写标准名称')
  const payload = { ...form }
  delete payload.id
  if (form.id) await api.updateStandard(form.id, payload)
  else await api.createStandard(payload)
  ElMessage.success('已保存')
  dialog.value = false
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除标准「${row.name}」？`, '提示', { type: 'warning' })
  await api.deleteStandard(row.id)
  ElMessage.success('已删除')
  load()
}

// ---------------- 码表 ----------------
async function loadCodeTables() {
  const list = await api.codeTables({ keyword: codeFilter.value || undefined })
  // 后端只给了 sourceCount，补一下系统名列表供展示
  codeTables.value = list.map((t) => ({ ...t, sourceSystems: t.sourceSystems || [] }))
  const all = await api.crosswalk({})
  const byTable = {}
  all.forEach((c) => {
    const s = byTable[c.codeTableCode] || (byTable[c.codeTableCode] = new Set())
    s.add(c.sourceSystem)
  })
  codeTables.value.forEach((t) => {
    t.sourceSystems = byTable[t.codeTableCode] ? [...byTable[t.codeTableCode]] : []
  })
}

async function openCodeTable(row) {
  current.value = row
  drawerTab.value = 'values'
  cwFilter.sourceSystem = ''
  drawer.value = true
  await Promise.all([loadValues(), loadCrosswalk(), loadSources()])
}

async function loadValues() {
  codeValues.value = await api.codeValues(current.value.id)
}

async function loadCrosswalk() {
  crosswalkRows.value = await api.crosswalk({
    codeTableId: current.value.id,
    sourceSystem: cwFilter.sourceSystem || undefined
  })
}

async function loadSources() {
  crosswalkSources.value = await api.crosswalkSources()
}

function mappedSources(codeValue) {
  return crosswalkRows.value.filter((c) => c.targetValue === codeValue)
}

function openCwForm(row) {
  if (row) {
    Object.assign(cwForm, row)
  } else {
    Object.assign(cwForm, {
      id: null, codeTableId: current.value.id, sourceSystem: 'HIS', sourceTable: '', sourceColumn: '',
      sourceValue: '', sourceName: '', targetValue: '', priority: 100, remark: ''
    })
  }
  cwForm.codeTableId = current.value.id
  cwDialog.value = true
}

async function saveCw() {
  if (!cwForm.sourceSystem || !cwForm.sourceValue || !cwForm.targetValue) {
    return ElMessage.warning('来源系统、源码值、目标码值必填')
  }
  const payload = {
    codeTableId: cwForm.codeTableId,
    sourceSystem: cwForm.sourceSystem,
    sourceTable: cwForm.sourceTable || null,
    sourceColumn: cwForm.sourceColumn || null,
    sourceValue: String(cwForm.sourceValue),
    sourceName: cwForm.sourceName || null,
    targetValue: cwForm.targetValue,
    priority: cwForm.priority || 100,
    remark: cwForm.remark || null,
    status: 1
  }
  try {
    if (cwForm.id) await api.updateCrosswalk(cwForm.id, payload)
    else await api.createCrosswalk(payload)
    ElMessage.success('已保存')
    cwDialog.value = false
    await Promise.all([loadCrosswalk(), loadCodeTables(), loadSources()])
  } catch (e) {
    // 拦截器已提示
  }
}

async function removeCw(row) {
  await ElMessageBox.confirm(
    `删除对照 ${row.sourceSystem}:${row.sourceValue} → ${row.targetValue}？删除后中台重算时该源码值会转成空。`,
    '提示', { type: 'warning' })
  await api.deleteCrosswalk(row.id)
  ElMessage.success('已删除')
  await Promise.all([loadCrosswalk(), loadCodeTables()])
}

onMounted(async () => {
  categories.value = await api.standardCategories()
  await load()
})
</script>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}

.std-tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}

.hint-panel {
  padding: 12px 16px;
}

.hint-body {
  font-size: 12.5px;
  color: #5b6b85;
  line-height: 1.7;
}

.hint-label {
  display: inline-block;
  margin-right: 10px;
  padding: 1px 8px;
  border-radius: 4px;
  background: #eef4ff;
  color: #2f6fed;
  font-size: 11.5px;
}

.hint-body code {
  padding: 1px 5px;
  border-radius: 3px;
  background: #f4f6fa;
  font-size: 11.5px;
}

.clickable :deep(tbody tr) {
  cursor: pointer;
}

.tag-gap {
  margin: 1px 4px 1px 0;
}

.std-ref {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: -4px 0 12px;
  padding: 8px 12px;
  border-radius: 6px;
  background: #f7f9fc;
  border: 1px solid #e8edf5;
  font-size: 12.5px;
  color: #34405a;
}

.ext-flag {
  display: inline-block;
  margin-left: 4px;
  padding: 0 5px;
  border-radius: 3px;
  background: #fff7e6;
  color: #b25b00;
  font-size: 11px;
  line-height: 16px;
}

.small {
  font-size: 11.5px;
}

.conform-body {
  display: flex;
  align-items: center;
  gap: 28px;
}

.ring {
  width: 132px;
  height: 132px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ring-inner {
  width: 100px;
  height: 100px;
  background: #fff;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.ring-value {
  font-size: 22px;
  font-weight: 700;
}

.ring-label {
  color: #98a2b3;
  font-size: 11px;
  margin-top: 2px;
}

.legend {
  display: flex;
  flex-direction: column;
  gap: 9px;
  font-size: 12.5px;
  color: #5b6b85;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dot {
  width: 9px;
  height: 9px;
  border-radius: 3px;
}

.legend-total {
  color: #98a2b3;
  font-size: 11.5px;
  margin-top: 4px;
}

.top-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.top-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12.5px;
}

.top-name {
  width: 84px;
  color: #34405a;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.spacer {
  flex: 1;
}
</style>
