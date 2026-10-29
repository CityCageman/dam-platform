<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">报表体系</h2>
        <p class="page-desc">按维度浏览当前角色可见范围内的报表，支持在线查看结果。</p>
      </div>
      <div class="head-actions">
        <el-input v-model="keyword" placeholder="搜索报表名称/编码" clearable style="width: 220px;" @keyup.enter="reload" />
        <el-button @click="reload">查询</el-button>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon> 登记报表
        </el-button>
      </div>
    </div>

    <!-- 维度 -->
    <div class="panel" style="margin-bottom: 14px;">
      <el-tabs v-model="activeDim" @tab-change="onDimChange">
        <el-tab-pane v-for="d in dims" :key="d.code" :label="d.name" :name="d.code" />
      </el-tabs>
      <div class="dim-values">
        <el-check-tag :checked="!activeValue" @change="selectValue('')">全部</el-check-tag>
        <el-check-tag
          v-for="v in currentValues"
          :key="String(v.code)"
          :checked="activeValue === String(v.code)"
          @change="selectValue(String(v.code))"
        >
          {{ v.name }}
        </el-check-tag>
        <span v-if="!currentValues.length" class="dim-empty">暂无可选维度值</span>
      </div>
    </div>

    <!-- 卡片 -->
    <div v-loading="loading" class="report-grid">
      <div v-for="c in cards" :key="c.id" class="report-card" @click="openDetail(c)">
        <div class="card-cover">
          <span class="cover-text">{{ (c.reportName || '报').slice(0, 1) }}</span>
          <el-tag v-if="c.execType" size="small" class="cover-tag" effect="dark">{{ execLabel(c.execType) }}</el-tag>
        </div>
        <div class="card-body">
          <div class="card-title" :title="c.reportName">{{ c.reportName }}</div>
          <div class="card-desc">{{ c.description || '暂无描述' }}</div>
          <div class="card-tags">
            <el-tag v-if="c.categoryPathText" size="small" type="info" effect="plain">{{ c.categoryPathText }}</el-tag>
            <el-tag v-if="c.updateFrequency" size="small" type="success" effect="plain">{{ freqLabel(c.updateFrequency) }}</el-tag>
          </div>
          <div class="card-foot">
            <span>{{ c.owner || '未指定负责人' }}</span>
            <span>{{ formatTime(c.updatedAt) }}</span>
          </div>
        </div>
      </div>
      <el-empty v-if="!loading && !cards.length" description="暂无报表，可先登记文档或在报表识别完成后展示" :image-size="80" style="grid-column: 1 / -1;" />
    </div>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @change="loadCards"
      />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="showDetail" :title="detail?.report?.reportName || '报表详情'" size="62%">
      <div v-if="detail" v-loading="running">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="报表编码">{{ detail.report.reportCode }}</el-descriptions-item>
          <el-descriptions-item label="执行方式">{{ execLabel(detail.report.execType) }}</el-descriptions-item>
          <el-descriptions-item label="更新频率">{{ freqLabel(detail.report.updateFrequency) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ detail.report.owner || '-' }}</el-descriptions-item>
          <el-descriptions-item label="主题域 / 类目" :span="2">
            {{ (detail.subjectDomains || []).map((s: any) => (s.subjectDomainName ? s.subjectDomainName + '/' : '') + s.categoryName).join('；') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ detail.report.description || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 参数 -->
        <div v-if="paramDefs.length" class="param-block">
          <div class="block-title">查询参数</div>
          <el-form label-width="110px" size="small">
            <el-form-item v-for="p in paramDefs" :key="p.code" :label="p.label || p.code">
              <el-input v-model="paramValues[p.code]" :placeholder="p.required ? '必填' : '可选'" style="width: 260px;" />
            </el-form-item>
          </el-form>
        </div>

        <div class="run-bar">
          <el-button type="primary" :loading="running" @click="runReport">在线查看</el-button>
          <el-button @click="openEdit(detail.report)">编辑</el-button>
          <el-button type="danger" plain @click="removeReport(detail.report)">删除</el-button>
          <span v-if="runResult" class="run-meta">
            共 {{ runResult.rowCount }} 行 · {{ runResult.elapsedMs }} ms
            <el-tag v-if="runResult.truncated" type="warning" size="small" style="margin-left: 6px;">已截断（最多 500 行）</el-tag>
          </span>
        </div>

        <div v-if="runError" class="run-error">{{ runError }}</div>

        <el-table v-if="runResult && runResult.columns?.length" :data="runResult.rows" stripe border size="small" max-height="420" style="margin-top: 12px;">
          <el-table-column v-for="col in runResult.columns" :key="col" :prop="col" :label="col" min-width="120" show-overflow-tooltip />
        </el-table>
        <el-empty v-else-if="runResult && !runResult.columns?.length" description="该报表无需取数（外链/静态）" :image-size="60" />
      </div>
    </el-drawer>

    <!-- 登记/编辑 -->
    <el-dialog v-model="showForm" :title="form.id ? '编辑报表' : '登记报表'" width="640px" top="6vh">
      <el-form :model="form" label-width="110px">
        <el-form-item label="报表名称" required>
          <el-input v-model="form.reportName" placeholder="如 门诊收入日报" />
        </el-form-item>
        <el-form-item label="报表编码">
          <el-input v-model="form.reportCode" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="执行方式">
          <el-select v-model="form.execType" style="width: 100%;">
            <el-option label="SQL 查询" value="SQL" />
            <el-option label="存储过程" value="PROC" />
            <el-option label="外链页面" value="IFRAME" />
            <el-option label="HTTP 接口" value="HTTP" />
            <el-option label="仅静态说明" value="STATIC" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.execType === 'SQL' || form.execType === 'PROC'" label="数据源">
          <el-select v-model="form.dataSourceId" filterable style="width: 100%;">
            <el-option v-for="ds in dataSources" :key="ds.id" :label="ds.dsName" :value="ds.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.execType === 'SQL' || form.execType === 'PROC'" label="查询语句">
          <el-input v-model="form.sqlContent" type="textarea" :rows="5"
                    :placeholder="form.execType === 'PROC' ? 'CALL 报表过程(:startDate, :endDate)' : 'SELECT ... 可用 :paramName 绑定参数'" />
        </el-form-item>
        <el-form-item v-if="form.execType === 'IFRAME' || form.execType === 'HTTP'" label="访问地址">
          <el-input v-model="form.endpointUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="报表分类">
          <el-tree-select
            v-model="form.categoryId"
            :data="categories"
            node-key="id"
            :props="{ label: 'categoryName', children: 'children', disabled: disabledDomainNode }"
            placeholder="选择类目（需先建主题域类目）"
            clearable check-strictly style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="更新频率">
          <el-select v-model="form.updateFrequency" clearable style="width: 100%;">
            <el-option v-for="v in freqValues" :key="String(v.code)" :label="v.name" :value="String(v.code)" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.owner" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveReport">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { reportCatalogApi, catalogApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const dims = ref<any[]>([])
const activeDim = ref<string>('SUBJECT_DOMAIN')
const activeValue = ref<string>('')
const keyword = ref('')
const cards = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(24)
const loading = ref(false)

const showDetail = ref(false)
const detail = ref<any>(null)
const running = ref(false)
const runResult = ref<any>(null)
const runError = ref('')
const paramValues = ref<Record<string, any>>({})

const showForm = ref(false)
const saving = ref(false)
const form = ref<any>({ execType: 'SQL', categoryId: undefined })
const categories = ref<any[]>([])
const dataSources = ref<any[]>([])

const currentDim = computed(() => dims.value.find((d: any) => d.code === activeDim.value))
const currentValues = computed(() => currentDim.value?.values || [])
const freqValues = computed(() => dims.value.find((d: any) => d.code === 'UPDATE_FREQ')?.values || [])
const paramDefs = computed(() => (detail.value?.paramDefs || []) as any[])

const EXEC_LABEL: Record<string, string> = {
  SQL: 'SQL 查询', PROC: '存储过程', IFRAME: '外链页面', HTTP: 'HTTP 接口', STATIC: '静态'
}
function execLabel(v: string) { return EXEC_LABEL[v] || v || '-' }
function freqLabel(v: string) { return freqValues.value.find((x: any) => String(x.code) === String(v))?.name || v }
function formatTime(t?: string) { return t ? String(t).replace('T', ' ').slice(0, 16) : '' }
function disabledDomainNode(d: any) { return !d.parentId }

onMounted(async () => {
  await Promise.all([loadDims(), loadCategories(), loadDataSources()])
  await loadCards()
})

async function loadDims() {
  try {
    const res: any = await reportCatalogApi.getDimensions()
    dims.value = res.data || []
    if (dims.value.length && !dims.value.find((d: any) => d.code === activeDim.value)) {
      activeDim.value = dims.value[0].code
    }
  } catch (_e) { dims.value = [] }
}

async function loadCategories() {
  try {
    const res: any = await catalogApi.getCategoryDomains()
    categories.value = buildTree(res.data || [])
  } catch (_e) { categories.value = [] }
}

function buildTree(list: any[]): any[] {
  const map = new Map<number, any>()
  const tree: any[] = []
  list.forEach(item => map.set(item.id, { ...item, children: [] }))
  list.forEach(item => {
    if (item.parentId && map.has(item.parentId)) map.get(item.parentId)!.children.push(map.get(item.id))
    else tree.push(map.get(item.id))
  })
  return tree
}

async function loadDataSources() {
  try {
    const res: any = await catalogApi.getDataSources()
    dataSources.value = res.data || []
  } catch (_e) { dataSources.value = [] }
}

function onDimChange() {
  activeValue.value = ''
  page.value = 1
  loadCards()
}

function selectValue(v: string) {
  activeValue.value = v
  page.value = 1
  loadCards()
}

function reload() {
  page.value = 1
  loadCards()
}

async function loadCards() {
  loading.value = true
  try {
    const res: any = await reportCatalogApi.getCards({
      dimensionCode: activeValue.value ? activeDim.value : undefined,
      dimensionValue: activeValue.value || undefined,
      keyword: keyword.value || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    cards.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (_e) {
    cards.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function openDetail(card: any) {
  showDetail.value = true
  detail.value = null
  runResult.value = null
  runError.value = ''
  paramValues.value = {}
  try {
    const res: any = await reportCatalogApi.getDetail(card.id)
    detail.value = res.data
    for (const p of (res.data?.paramDefs || [])) {
      if (paramValues.value[p.code] == null) paramValues.value[p.code] = ''
    }
  } catch (e: any) {
    ElMessage.error(e.message || '加载报表失败')
  }
}

async function runReport() {
  if (!detail.value?.report?.id) return
  running.value = true
  runError.value = ''
  try {
    const res: any = await reportCatalogApi.run(detail.value.report.id, paramValues.value)
    runResult.value = res.data
    if (res.data?.execType === 'IFRAME' || res.data?.execType === 'HTTP') {
      ElMessage.info('该报表为外链页面，请点击下方地址打开')
    }
  } catch (e: any) {
    runResult.value = null
    runError.value = e.message || '执行失败'
  } finally {
    running.value = false
  }
}

function openCreate() {
  form.value = { execType: 'SQL', categoryId: undefined, status: 1 }
  showForm.value = true
}

function openEdit(report: any) {
  form.value = {
    id: report.id,
    reportName: report.reportName,
    reportCode: report.reportCode,
    execType: report.execType || 'SQL',
    dataSourceId: report.dataSourceId,
    sqlContent: report.sqlContent,
    endpointUrl: report.endpointUrl,
    updateFrequency: report.updateFrequency,
    owner: report.owner,
    description: report.description,
    status: report.status ?? 1
  }
  showForm.value = true
}

async function saveReport() {
  if (!form.value.reportName) {
    ElMessage.warning('请填写报表名称')
    return
  }
  saving.value = true
  try {
    if (form.value.id) await reportCatalogApi.update(form.value.id, form.value)
    else await reportCatalogApi.create(form.value)
    ElMessage.success('保存成功')
    showForm.value = false
    await loadCards()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeReport(report: any) {
  try {
    await ElMessageBox.confirm(`确认删除报表「${report.reportName}」？`, '提示', { type: 'warning' })
    await reportCatalogApi.remove(report.id)
    ElMessage.success('已删除')
    showDetail.value = false
    await loadCards()
  } catch (_e) { /* 取消 */ }
}
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-title { margin: 0; font-size: 20px; }
.page-desc { margin: 4px 0 0; color: var(--med-text-light, #8a94a6); font-size: 13px; }
.head-actions { display: flex; gap: 8px; align-items: center; }
.dim-values { display: flex; flex-wrap: wrap; gap: 8px; padding-top: 4px; }
.dim-empty { color: #909399; font-size: 12px; }
.report-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
  min-height: 200px;
}
.report-card {
  background: #fff;
  border: 1px solid var(--med-border, #e8ecf3);
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow .2s, transform .2s;
  display: flex;
  flex-direction: column;
}
.report-card:hover { box-shadow: 0 8px 24px rgba(16, 41, 76, .1); transform: translateY(-2px); }
.card-cover {
  height: 76px;
  background: linear-gradient(135deg, #2f7bff 0%, #16c2c2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}
.cover-text { color: #fff; font-size: 28px; font-weight: 700; opacity: .9; }
.cover-tag { position: absolute; right: 8px; top: 8px; }
.card-body { padding: 10px 12px 12px; display: flex; flex-direction: column; gap: 6px; flex: 1; }
.card-title { font-size: 14px; font-weight: 600; color: #1f2d3d; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-desc { font-size: 12px; color: #8a94a6; height: 34px; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.card-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.card-foot { display: flex; justify-content: space-between; font-size: 11px; color: #a8b1c2; margin-top: auto; }
.pagination-bar { display: flex; justify-content: flex-end; margin-top: 16px; }
.param-block { margin-top: 16px; }
.block-title { font-weight: 600; margin-bottom: 8px; }
.run-bar { display: flex; align-items: center; gap: 10px; margin-top: 16px; }
.run-meta { color: #8a94a6; font-size: 12px; }
.run-error { margin-top: 10px; color: #f56c6c; font-size: 13px; }
</style>
