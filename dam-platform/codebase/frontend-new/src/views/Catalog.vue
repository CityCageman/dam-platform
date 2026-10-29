<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">数据目录</h2>
        <p class="page-desc">四层数据目录：数据中心 → 业务域 → 模型（事实 / 维度），统一管理平台内数仓分层与主题模型。</p>
      </div>
      <div class="stat-row">
        <div class="stat"><b>{{ domainCount }}</b><span>业务域</span></div>
        <div class="stat"><b>{{ factCount }}</b><span>事实模型</span></div>
        <div class="stat"><b>{{ dimCount }}</b><span>维度模型</span></div>
      </div>
    </div>

    <div class="catalog-layout">
      <!-- 左：目录树 -->
      <div class="panel tree-panel">
        <div class="panel-toolbar">
          <span class="panel-title">目录结构</span>
          <el-button size="small" type="primary" plain :icon="Plus" @click="openDomain(null)">新建域</el-button>
        </div>
        <el-input v-model="filterText" placeholder="搜索目录" size="small" clearable class="tree-filter">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-tree ref="treeRef" :data="tree" node-key="key" :props="treeProps" default-expand-all
                 :filter-node-method="filterNode" highlight-current @node-click="onSelect">
          <template #default="{ data }">
            <span class="tree-node">
              <el-icon :class="'type-icon ' + data.type?.toLowerCase()"><component :is="iconOf(data)" /></el-icon>
              <span class="node-name" :class="{ dim: data.type === 'DIMENSION' }">{{ data.name }}</span>
              <el-tag v-if="data.type === 'FACT'" size="small" effect="plain" type="warning">事实</el-tag>
              <el-tag v-else-if="data.type === 'DIMENSION'" size="small" effect="plain" type="success">维度</el-tag>
              <span v-else-if="data.type === 'DOMAIN'" class="node-count">{{ (data.children || []).length }}</span>
            </span>
          </template>
        </el-tree>
      </div>

      <!-- 右：详情 -->
      <div class="panel detail-panel">
        <template v-if="selected">
          <!-- 域详情 -->
          <template v-if="selected.type === 'DOMAIN'">
            <div class="detail-head">
              <div>
                <div class="detail-title">
                  <el-icon><FolderOpened /></el-icon>{{ selected.name }}
                  <el-tag size="small" effect="plain">{{ selected.domainCode }}</el-tag>
                </div>
                <p class="detail-desc">{{ selected.description || '暂无描述' }}</p>
              </div>
              <div class="detail-actions">
                <el-button size="small" :icon="Plus" @click="openDomain(selected)">新建子域</el-button>
                <el-button size="small" type="primary" plain @click="openFact(selected)">新建事实模型</el-button>
                <el-button size="small" type="success" plain @click="openDimension(selected)">新建维度模型</el-button>
                <el-button size="small" type="danger" plain :icon="Delete" @click="removeNode(selected)">删除</el-button>
              </div>
            </div>
            <div class="model-grid">
              <div class="model-card" v-for="m in (selected.children || [])" :key="m.type + m.id" @click="onSelect({ data: m })">
                <div class="model-head">
                  <el-tag size="small" :type="m.type === 'FACT' ? 'warning' : 'success'" effect="dark">
                    {{ m.type === 'FACT' ? '事实' : '维度' }}
                  </el-tag>
                  <span class="model-name">{{ m.name }}</span>
                </div>
                <div class="model-code mono">{{ m.code }}</div>
                <div class="model-src" v-if="m.sourceTableName">
                  <el-icon><Coin /></el-icon>{{ m.sourceTableName }}
                </div>
                <div class="model-desc">{{ m.description || '—' }}</div>
              </div>
              <el-empty v-if="!(selected.children || []).length" description="该域下暂无模型" :image-size="60" />
            </div>
          </template>

          <!-- 模型详情 -->
          <template v-else>
            <div class="detail-head">
              <div>
                <div class="detail-title">
                  <el-tag size="small" :type="selected.type === 'FACT' ? 'warning' : 'success'" effect="dark">
                    {{ selected.type === 'FACT' ? '事实模型' : '维度模型' }}
                  </el-tag>
                  {{ selected.name }}
                  <el-tag size="small" effect="plain" class="mono">{{ selected.code }}</el-tag>
                </div>
                <p class="detail-desc">{{ selected.description || '暂无描述' }}</p>
              </div>
              <div class="detail-actions">
                <el-button size="small" @click="selected.type === 'FACT' ? openFact(null, selected) : openDimension(null, selected)">
                  编辑
                </el-button>
                <el-button size="small" type="danger" plain :icon="Delete" @click="removeNode(selected)">删除</el-button>
              </div>
            </div>
            <el-descriptions :column="2" border size="small" class="detail-desc-list">
              <el-descriptions-item label="编码"><span class="mono">{{ selected.code }}</span></el-descriptions-item>
              <el-descriptions-item label="粒度" v-if="selected.type === 'FACT'">{{ selected.granularity || '—' }}</el-descriptions-item>
              <el-descriptions-item label="来源表">{{ selected.sourceTableName || '未挂接' }}</el-descriptions-item>
              <el-descriptions-item label="来源表说明">{{ selected.sourceTableComment || '—' }}</el-descriptions-item>
            </el-descriptions>
          </template>
        </template>
        <el-empty v-else description="点击左侧目录查看详情" />
      </div>
    </div>

    <!-- 域弹窗 -->
    <el-dialog v-model="domainDialog" :title="domainForm.id ? '编辑域' : (domainForm.parentId ? '新建子域' : '新建业务域')" width="480px">
      <el-form :model="domainForm" label-width="80px">
        <el-form-item label="上级域" v-if="domainForm.parentId">
          <el-input :model-value="parentName" disabled />
        </el-form-item>
        <el-form-item label="域编码" v-if="!domainForm.id">
          <el-input v-model="domainForm.domainCode" placeholder="如 MD-CLINICAL" />
        </el-form-item>
        <el-form-item label="域名称">
          <el-input v-model="domainForm.domainName" placeholder="如 临床域" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="domainForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="domainForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="domainDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDomain">保存</el-button>
      </template>
    </el-dialog>

    <!-- 事实模型弹窗 -->
    <el-dialog v-model="factDialog" :title="factForm.id ? '编辑事实模型' : '新建事实模型'" width="520px">
      <el-form :model="factForm" label-width="80px">
        <el-form-item label="所属域">
          <el-tree-select v-model="factForm.domainId" :data="domainOptions" check-strictly style="width:100%" />
        </el-form-item>
        <el-form-item label="编码" v-if="!factForm.id">
          <el-input v-model="factForm.factCode" placeholder="如 F_VISIT" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="factForm.factName" placeholder="如 就诊事实表" />
        </el-form-item>
        <el-form-item label="粒度">
          <el-input v-model="factForm.granularity" placeholder="如 一患者一就诊一行" />
        </el-form-item>
        <el-form-item label="来源表">
          <el-select v-model="factForm.sourceTableId" filterable clearable style="width:100%">
            <el-option v-for="t in tables" :key="t.id" :label="`${t.tableName}（${t.tableComment || t.schemaName}）`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="factForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="factDialog = false">取消</el-button>
        <el-button type="primary" @click="saveFact">保存</el-button>
      </template>
    </el-dialog>

    <!-- 维度模型弹窗 -->
    <el-dialog v-model="dimDialog" :title="dimForm.id ? '编辑维度模型' : '新建维度模型'" width="520px">
      <el-form :model="dimForm" label-width="80px">
        <el-form-item label="所属域">
          <el-tree-select v-model="dimForm.domainId" :data="domainOptions" check-strictly style="width:100%" />
        </el-form-item>
        <el-form-item label="编码" v-if="!dimForm.id">
          <el-input v-model="dimForm.dimensionCode" placeholder="如 DIM_PATIENT" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="dimForm.dimensionName" placeholder="如 患者维度" />
        </el-form-item>
        <el-form-item label="来源表">
          <el-select v-model="dimForm.sourceTableId" filterable clearable style="width:100%">
            <el-option v-for="t in tables" :key="t.id" :label="`${t.tableName}（${t.tableComment || t.schemaName}）`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="dimForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dimDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDimension">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Search, FolderOpened, Coin, OfficeBuilding, DataAnalysis, Grid } from '@element-plus/icons-vue'
import { api } from '../api'
import { auth } from '../store'

const tree = ref([])
const tables = ref([])
const filterText = ref('')
const treeRef = ref()
const selected = ref(null)

const domainDialog = ref(false)
const factDialog = ref(false)
const dimDialog = ref(false)

const domainForm = ref({})
const factForm = ref({})
const dimForm = ref({})

const canEdit = computed(() => ['ADMIN', 'STEWARD'].includes(auth.user?.role))

const treeProps = { children: 'children', label: 'name' }

const domainCount = computed(() => countType(tree.value, 'DOMAIN'))
const factCount = computed(() => countType(tree.value, 'FACT'))
const dimCount = computed(() => countType(tree.value, 'DIMENSION'))

const parentName = computed(() => {
  const p = findDomain(tree.value, domainForm.value.parentId)
  return p ? `${p.name}（${p.domainCode}）` : ''
})

/** 域下拉树选项（仅域节点，check-strictly 允许选任意层） */
const domainOptions = computed(() => {
  const walk = (nodes) => nodes.filter((n) => n.type === 'DOMAIN').map((n) => ({
    value: n.id, label: n.name, children: walk(n.children || [])
  }))
  return walk(tree.value)
})

function countType(nodes, type) {
  let c = 0
  for (const n of nodes) {
    if (n.type === type) c++
    c += countType(n.children || [], type)
  }
  return c
}

function findDomain(nodes, id) {
  for (const n of nodes) {
    if (n.type === 'DOMAIN' && n.id === id) return n
    const hit = findDomain(n.children || [], id)
    if (hit) return hit
  }
  return null
}

function iconOf(data) {
  if (data.type === 'DOMAIN') return OfficeBuilding
  if (data.type === 'FACT') return DataAnalysis
  if (data.type === 'DIMENSION') return Grid
  return Coin
}

async function load() {
  tree.value = await api.domainTree()
  markKeys(tree.value)
  tables.value = await api.tables({})
}

function markKeys(nodes) {
  for (const n of nodes) {
    n.key = (n.type || 'DOMAIN') + ':' + n.id
    markKeys(n.children || [])
  }
}

function onSelect({ data }) {
  selected.value = data
}

watch(filterText, (v) => treeRef.value?.filter(v))
function filterNode(value, data) {
  if (!value) return true
  return (data.name || '').includes(value) || (data.code || '').includes(value)
}

// ---------- 域 ----------
function openDomain(parent) {
  if (!canEdit.value) return ElMessage.warning('仅数据管理员 / 数据管家可操作')
  domainForm.value = parent
    ? { parentId: parent.id, domainCode: '', domainName: '', description: '', sortOrder: 0 }
    : { parentId: null, domainCode: '', domainName: '', description: '', sortOrder: 0 }
  domainDialog.value = true
}

async function saveDomain() {
  const f = domainForm.value
  if (!f.domainName?.trim()) return ElMessage.warning('请填写域名称')
  if (f.id) {
    await api.updateDomain(f.id, { domainName: f.domainName, description: f.description, sortOrder: f.sortOrder })
  } else {
    if (!f.domainCode?.trim()) return ElMessage.warning('请填写域编码')
    await api.createDomain(f)
  }
  ElMessage.success('已保存')
  domainDialog.value = false
  load()
}

// ---------- 事实模型 ----------
function openFact(domain, model) {
  if (!canEdit.value) return ElMessage.warning('仅数据管理员 / 数据管家可操作')
  factForm.value = {
    id: model?.id ?? null,
    domainId: model?.domainId ?? domain?.id ?? null,
    factCode: '', factName: model?.name ?? '',
    granularity: model?.granularity ?? '',
    sourceTableId: model?.sourceTableId ?? null,
    description: model?.description ?? ''
  }
  factDialog.value = true
}

async function saveFact() {
  const f = factForm.value
  if (!f.domainId) return ElMessage.warning('请选择所属域')
  if (f.id) {
    await api.updateFact(f.id, { factName: f.factName, granularity: f.granularity, sourceTableId: f.sourceTableId, description: f.description })
  } else {
    if (!f.factCode?.trim() || !f.factName?.trim()) return ElMessage.warning('请填写编码与名称')
    await api.createFact(f)
  }
  ElMessage.success('已保存')
  factDialog.value = false
  load()
}

// ---------- 维度模型 ----------
function openDimension(domain, model) {
  if (!canEdit.value) return ElMessage.warning('仅数据管理员 / 数据管家可操作')
  dimForm.value = {
    id: model?.id ?? null,
    domainId: model?.domainId ?? domain?.id ?? null,
    dimensionCode: '', dimensionName: model?.name ?? '',
    sourceTableId: model?.sourceTableId ?? null,
    description: model?.description ?? ''
  }
  dimDialog.value = true
}

async function saveDimension() {
  const f = dimForm.value
  if (!f.domainId) return ElMessage.warning('请选择所属域')
  if (f.id) {
    await api.updateDimension(f.id, { dimensionName: f.dimensionName, sourceTableId: f.sourceTableId, description: f.description })
  } else {
    if (!f.dimensionCode?.trim() || !f.dimensionName?.trim()) return ElMessage.warning('请填写编码与名称')
    await api.createDimension(f)
  }
  ElMessage.success('已保存')
  dimDialog.value = false
  load()
}

// ---------- 删除 ----------
async function removeNode(node) {
  if (!canEdit.value) return ElMessage.warning('仅数据管理员 / 数据管家可操作')
  const label = node.type === 'DOMAIN' ? '域' : node.type === 'FACT' ? '事实模型' : '维度模型'
  try {
    await ElMessageBox.confirm(`确定删除${label}「${node.name}」？删除后可在数据库恢复（软删除）。`, '删除确认', { type: 'warning' })
  } catch { return }
  try {
    if (node.type === 'DOMAIN') await api.removeDomain(node.id)
    else if (node.type === 'FACT') await api.removeFact(node.id)
    else await api.removeDimension(node.id)
    ElMessage.success('已删除（软删除）')
    selected.value = null
    load()
  } catch { /* 拦截器已提示（引用保护等） */ }
}

onMounted(load)
</script>

<style scoped>
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 14px;
}

.page-title { margin: 0 0 6px; font-size: 19px; }
.page-desc { margin: 0; color: #8a94a6; font-size: 12.5px; }

.stat-row { display: flex; gap: 12px; }
.stat {
  background: #fff; border: 1px solid #e9edf5; border-radius: 10px;
  padding: 8px 18px; text-align: center; min-width: 92px;
}
.stat b { display: block; font-size: 20px; }
.stat span { font-size: 11px; color: #8a94a6; }

.catalog-layout {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 14px;
}

.panel {
  background: #fff; border: 1px solid #e9edf5; border-radius: 12px;
}

.tree-panel { padding: 14px; max-height: 640px; overflow: auto; }

.panel-toolbar {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;
}

.panel-title { font-size: 13.5px; font-weight: 600; color: #1f2a3d; }

.tree-filter { margin-bottom: 8px; }

.tree-node {
  display: flex; align-items: center; gap: 6px; font-size: 13px; flex: 1; min-width: 0;
}

.node-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.node-name.dim { color: #3a9a5f; }

.type-icon { color: #7a8db0; font-size: 14px; }
.type-icon.domain { color: #2f7bff; }
.type-icon.fact { color: #e6a23c; }
.type-icon.dimension { color: #52c41a; }

.node-count {
  color: #98a2b3; font-size: 11px; background: #f4f6fa;
  border-radius: 8px; padding: 0 7px;
}

.detail-panel { padding: 18px 20px; min-height: 480px; }

.detail-head {
  display: flex; justify-content: space-between; align-items: flex-start;
  gap: 12px; margin-bottom: 16px;
}

.detail-title {
  display: flex; align-items: center; gap: 8px;
  font-size: 16px; font-weight: 600; color: #1f2a3d;
}

.detail-desc { margin: 8px 0 0; color: #8a94a6; font-size: 12.5px; }

.detail-actions { display: flex; gap: 8px; flex-shrink: 0; }

.model-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px;
}

.model-card {
  border: 1px solid #e9edf5; border-radius: 10px; padding: 12px 14px;
  cursor: pointer; transition: all .15s;
}
.model-card:hover { border-color: #2f7bff; box-shadow: 0 6px 16px rgba(47,123,255,.10); transform: translateY(-1px); }

.model-head { display: flex; align-items: center; gap: 8px; }
.model-name { font-weight: 600; font-size: 13.5px; color: #1f2a3d; }
.model-code { color: #98a2b3; font-size: 11px; margin-top: 4px; }
.model-src {
  display: flex; align-items: center; gap: 5px;
  color: #5b6b85; font-size: 11.5px; margin-top: 6px;
}
.model-desc {
  color: #a2abb9; font-size: 11.5px; margin-top: 6px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

.detail-desc-list { margin-top: 4px; }

.mono { font-family: monospace; }
</style>
