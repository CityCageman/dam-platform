<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">元数据管理</h2>
        <p class="page-desc">接入数据源后自动采集表结构与字段信息，「资产平台自建库」可直接同步验证链路。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openSource()">新增数据源</el-button>
    </div>

    <div class="stat-grid" style="margin-bottom:14px">
      <div class="stat-card">
        <div class="stat-label">数据源</div>
        <div class="stat-value">{{ stats.sourceCount || 0 }}</div>
        <div class="stat-foot">PG / MySQL / IRIS</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">采集表数</div>
        <div class="stat-value">{{ stats.tableCount || 0 }}</div>
        <div class="stat-foot">含平台自建库</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">字段总数</div>
        <div class="stat-value">{{ stats.columnCount || 0 }}</div>
        <div class="stat-foot">已建立元数据</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已登记资产</div>
        <div class="stat-value">{{ assetCount }}</div>
        <div class="stat-foot">可一键登记</div>
      </div>
    </div>

    <div class="panel dam-block">
      <div class="dam-block-head">
        <h3 class="panel-title">dam 元数据层（三期 · PowerJob 采集）</h3>
        <span class="muted">
          {{ dam.damSchemaCount || 0 }} 个 schema · {{ dam.damTableCount || 0 }} 张表 · {{ dam.damColumnCount || 0 }} 个字段
        </span>
      </div>
      <div class="dam-schema-row">
        <div class="dam-schema-card" v-for="s in dam.schemas" :key="s.id">
          <div class="ds-name mono">{{ s.schemaName || 'public' }}</div>
          <div class="ds-meta">
            <span><b>{{ s.tableCount }}</b> 表</span>
            <span><b>{{ s.columnCount }}</b> 字段</span>
            <span><b>{{ fmtNum(s.rowCount) }}</b> 行</span>
          </div>
          <div class="ds-comment" v-if="s.schemaComment">{{ s.schemaComment }}</div>
        </div>
        <el-empty v-if="!dam.schemas?.length" description="尚未采集" :image-size="46" />
      </div>

      <div class="dam-coll" v-if="dam.recentCollections?.length">
        <div class="coll-title">最近采集记录</div>
        <el-table :data="dam.recentCollections" size="small">
          <el-table-column prop="dsName" label="数据源" width="140" />
          <el-table-column prop="jobName" label="作业" min-width="170" />
          <el-table-column label="表 / 字段" width="120" align="center">
            <template #default="{ row }">{{ row.totalTables }} / {{ row.totalColumns }}</template>
          </el-table-column>
          <el-table-column label="成功 / 失败" width="110" align="center">
            <template #default="{ row }">
              <span :class="row.failCount ? 'warn' : ''">{{ row.successCount }} / {{ row.failCount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 || row.status === '1' ? 'success' : 'info'">
                {{ row.status === 1 || row.status === '1' ? '成功' : row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="开始时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <div class="split">
      <div class="sources">
        <div class="panel" v-for="s in sources" :key="s.id">
          <div class="src-head">
            <div>
              <div class="src-name">
                {{ s.name }}
                <el-tag size="small" :type="s.status === 'CONNECTED' ? 'success' : s.status === 'ERROR' ? 'danger' : 'info'">
                  {{ s.status === 'CONNECTED' ? '已连接' : s.status === 'ERROR' ? '连接失败' : '未连接' }}
                </el-tag>
              </div>
              <div class="src-sub mono">{{ s.type }} · {{ s.host }}:{{ s.port }}/{{ s.dbName }}</div>
            </div>
            <el-dropdown trigger="click">
              <el-icon class="more"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="openSource(s)">编辑</el-dropdown-item>
                  <el-dropdown-item divided @click="removeSource(s)">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <p class="src-desc">{{ s.description }}</p>
          <div class="src-foot">
            <span>{{ s.realTableCount ?? s.tableCount ?? 0 }} 张表</span>
            <span>·</span>
            <span>上次同步 {{ fmtTime(s.lastSyncAt) }}</span>
            <div class="spacer"></div>
            <el-button size="small" text @click="test(s)">测试连接</el-button>
            <el-button size="small" text :loading="enriching === s.id" @click="aiEnrich(s)">AI 注释</el-button>
            <el-button size="small" type="primary" plain :loading="syncing === s.id" @click="sync(s)">同步元数据</el-button>
          </div>
        </div>
      </div>

      <div class="tables">
        <div class="panel">
          <div class="table-toolbar">
            <el-input v-model="keyword" placeholder="搜索表名 / 表注释" clearable :prefix-icon="Search"
                      style="width: 240px" @keyup.enter="loadTables" @clear="loadTables" />
            <el-select v-model="sourceId" placeholder="全部数据源" clearable style="width: 170px" @change="loadTables">
              <el-option v-for="s in sources" :key="s.id" :label="s.name" :value="s.id" />
            </el-select>
            <div class="spacer"></div>
            <span class="muted">{{ tables.length }} 张表</span>
          </div>

          <el-table :data="tables" size="small" max-height="560" @row-click="showTable">
            <el-table-column prop="tableName" label="表名" min-width="170">
              <template #default="{ row }">
                <span class="mono">{{ row.tableName }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="tableComment" label="表注释" min-width="170" />
            <el-table-column prop="sourceName" label="数据源" width="140" />
            <el-table-column label="字段" width="70">
              <template #default="{ row }">{{ row.columnCount }}</template>
            </el-table-column>
            <el-table-column label="数据量" width="100">
              <template #default="{ row }">{{ fmtNum(row.rowCount) }}</template>
            </el-table-column>
            <el-table-column label="资产" width="110">
              <template #default="{ row }">
                <el-tag v-if="row.assetCount" size="small" type="success" effect="plain">已登记</el-tag>
                <el-button v-else size="small" text type="primary" @click.stop="register(row)">登记为资产</el-button>
              </template>
            </el-table-column>
            <el-table-column label="采集时间" width="150">
              <template #default="{ row }">{{ fmtTime(row.collectedAt) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

    <!-- 表结构 -->
    <el-dialog v-model="tableDialog" :title="current?.tableName" width="880px">
      <div class="dialog-sub" v-if="current">
        <span class="mono">{{ current.schemaName }}.{{ current.tableName }}</span>
        <span>· {{ fmtNum(current.rowCount) }} 行</span>
        <el-tag size="small">{{ current.sourceName }}</el-tag>
        <span class="tbl-comment">表描述：{{ current.tableComment || '（无）' }}</span>
        <el-icon class="edit-ic" title="编辑表描述" @click="editTableComment"><Edit /></el-icon>
      </div>
      <el-table :data="columns" size="small" max-height="460">
        <el-table-column prop="ordinal" label="#" width="50" />
        <el-table-column prop="columnName" label="字段" min-width="170">
          <template #default="{ row }">
            <span class="mono">{{ row.columnName }}</span>
            <el-tag v-if="row.primaryKey" size="small" type="warning" effect="plain" style="margin-left:6px">PK</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="140">
          <template #default="{ row }">{{ row.dataType }}{{ row.length ? `(${row.length})` : '' }}</template>
        </el-table-column>
        <el-table-column label="可空" width="70">
          <template #default="{ row }">{{ row.nullable ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="注释" min-width="200">
          <template #default="{ row }">
            <span :class="{ 'no-comment': !row.columnComment }">{{ row.columnComment || '（无）' }}</span>
            <el-icon class="edit-ic" title="编辑注释" @click="editColumnComment(row)"><Edit /></el-icon>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 数据源表单 -->
    <el-dialog v-model="sourceDialog" :title="form.id ? '编辑数据源' : '新增数据源'" width="520px">
      <el-form :model="form" label-width="86px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如：HIS 生产库" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width:100%" @change="onTypeChange">
            <el-option label="PostgreSQL" value="POSTGRESQL" />
            <el-option label="MySQL" value="MYSQL" />
            <el-option label="InterSystems IRIS" value="IRIS" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机">
          <el-input v-model="form.host" placeholder="localhost" />
        </el-form-item>
        <el-form-item label="端口">
          <el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" style="width:100%" />
        </el-form-item>
        <el-form-item :label="form.type === 'IRIS' ? '命名空间' : '数据库'">
          <el-input v-model="form.dbName" :placeholder="form.type === 'IRIS' ? 'IRIS 命名空间，如 USER / PACS' : ''" />
        </el-form-item>
        <el-form-item label="Schema" v-if="form.type !== 'IRIS'">
          <el-input v-model="form.schemaName" placeholder="public" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sourceDialog = false">取消</el-button>
        <el-button type="primary" @click="saveSource">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, MoreFilled, Edit } from '@element-plus/icons-vue'
import { api, fmtTime, fmtNum } from '../api'

const sources = ref([])
const stats = ref({})
const tables = ref([])
const columns = ref([])
const keyword = ref('')
const sourceId = ref(null)
const syncing = ref(null)
const enriching = ref(null)
const tableDialog = ref(false)
const sourceDialog = ref(false)
const current = ref(null)
const assetCount = ref(0)
const dam = ref({})

const form = reactive({
  id: null, name: '', type: 'POSTGRESQL', host: 'localhost', port: 5432,
  dbName: '', schemaName: 'public', username: '', password: '', description: ''
})

async function loadSources() {
  sources.value = await api.sources()
}

async function loadStats() {
  stats.value = await api.metadataStats()
}

async function loadTables() {
  tables.value = await api.tables({ sourceId: sourceId.value || undefined, keyword: keyword.value || undefined })
  const res = await api.assets({ page: 1, size: 1 })
  assetCount.value = res.total
}

async function sync(s) {
  syncing.value = s.id
  try {
    const r = await api.syncSource(s.id)
    ElMessage.success(r.message)
    await Promise.all([loadSources(), loadStats(), loadTables()])
  } finally {
    syncing.value = null
  }
}

async function test(s) {
  const r = await api.testSource(s.id)
  r.connected ? ElMessage.success('连接正常') : ElMessage.error('连接失败，请检查主机、账号与网络')
}

/** AI 补全中文注释：覆盖已有注释 或 仅补空注释 */
async function aiEnrich(s) {
  let overwrite = false
  try {
    await ElMessageBox.confirm(
      '让 AI 识别表与字段的业务含义并生成中文注释。已有注释如何处理？',
      `AI 智能注释 · ${s.name}`,
      { confirmButtonText: '覆盖已有注释', cancelButtonText: '仅补全空注释', distinguishCancelAndClose: true, type: 'info' }
    )
    overwrite = true
  } catch (action) {
    if (action !== 'cancel') return
  }
  enriching.value = s.id
  try {
    const r = await api.aiEnrich(s.id, { overwrite })
    ElMessage.success(`AI 已补全 ${r.tablesEnriched} 张表描述 / ${r.columnsEnriched} 个字段注释`)
    loadTables()
  } finally {
    enriching.value = null
  }
}

async function showTable(row) {
  current.value = row
  const d = await api.tableDetail(row.id)
  columns.value = d.columns || []
  tableDialog.value = true
}

/** 编辑表描述（人工注释优先级最高，AI 补全只填空、永不覆盖人工注释） */
async function editTableComment() {
  const { value } = await ElMessageBox.prompt('请输入表的中文业务描述', '编辑表描述', {
    inputValue: current.value.tableComment || '',
    inputPlaceholder: '如：门诊患者挂号主表',
    inputValidator: (v) => (v && v.trim() ? true : '描述不能为空')
  })
  await api.updateTableComment(current.value.id, value.trim())
  current.value.tableComment = value.trim()
  ElMessage.success('表描述已更新')
  loadTables()
}

/** 编辑字段中文注释 */
async function editColumnComment(row) {
  const { value } = await ElMessageBox.prompt(`字段 ${row.columnName} 的中文注释`, '编辑字段注释', {
    inputValue: row.columnComment || '',
    inputPlaceholder: '写清业务含义，如：患者就诊号（门诊号）',
    inputValidator: (v) => (v && v.trim() ? true : '注释不能为空')
  })
  await api.updateColumnComment(row.id, value.trim())
  row.columnComment = value.trim()
  ElMessage.success('字段注释已更新')
}

async function register(row) {
  const r = await api.fromTable(row.id)
  ElMessage.success(r.message || '已登记')
  loadTables()
}

const DEFAULT_PORTS = { POSTGRESQL: 5432, MYSQL: 3306, IRIS: 1972 }

function onTypeChange(type) {
  if (DEFAULT_PORTS[type]) form.port = DEFAULT_PORTS[type]
}

function openSource(s) {
  if (s) {
    Object.assign(form, s)
  } else {
    Object.assign(form, {
      id: null, name: '', type: 'POSTGRESQL', host: 'localhost', port: 5432,
      dbName: '', schemaName: 'public', username: '', password: '', description: ''
    })
  }
  sourceDialog.value = true
}

async function saveSource() {
  if (!form.name.trim()) return ElMessage.warning('请填写名称')
  const payload = { ...form }
  delete payload.id
  if (form.id) await api.updateSource(form.id, payload)
  else await api.createSource(payload)
  ElMessage.success('已保存')
  sourceDialog.value = false
  await Promise.all([loadSources(), loadStats()])
}

async function removeSource(s) {
  await ElMessageBox.confirm(`删除数据源「${s.name}」会同时删除已采集的元数据，确定？`, '提示', { type: 'warning' })
  await api.deleteSource(s.id)
  ElMessage.success('已删除')
  await Promise.all([loadSources(), loadStats(), loadTables()])
}

onMounted(async () => {
  await Promise.all([loadSources(), loadStats()])
  await loadTables()
  api.damOverview().then((d) => { dam.value = d }).catch(() => {})
})
</script>

<style scoped>
/* dam 元数据层 */
.dam-block { padding: 16px 18px; margin-bottom: 14px; }
.dam-block-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.dam-block-head .muted { color: #98a2b3; font-size: 12px; }
.panel-title { margin: 0; }

.dam-schema-row { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 10px; }

.dam-schema-card {
  background: linear-gradient(135deg, #f6f9ff, #fbfdff);
  border: 1px solid #e6edf9; border-radius: 10px; padding: 10px 14px;
}
.ds-name { font-size: 13.5px; font-weight: 700; color: #1f2a3d; }
.ds-meta { display: flex; gap: 12px; font-size: 11.5px; color: #8a94a6; margin-top: 5px; }
.ds-meta b { color: #34405a; font-weight: 600; }
.ds-comment { font-size: 11px; color: #a2abb9; margin-top: 4px; }

.dam-coll { margin-top: 14px; border-top: 1px dashed #eef1f7; padding-top: 12px; }
.coll-title { font-size: 12px; color: #7a8699; font-weight: 600; margin-bottom: 8px; }
.warn { color: #e6a23c; font-weight: 600; }

.split {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 14px;
  align-items: start;
}

.sources {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sources .panel {
  padding: 14px 16px;
}

.src-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.src-name {
  font-size: 13.5px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.src-sub {
  color: #98a2b3;
  font-size: 11px;
  margin-top: 5px;
}

.src-desc {
  color: #667085;
  font-size: 11.5px;
  margin: 10px 0;
  line-height: 1.6;
}

.src-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #98a2b3;
  font-size: 11px;
  padding-top: 8px;
  border-top: 1px dashed #eef1f7;
}

.more {
  cursor: pointer;
  color: #98a2b3;
}

.spacer {
  flex: 1;
}

.table-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.dialog-sub {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #667085;
  font-size: 12px;
  margin-bottom: 12px;
}

.tbl-comment {
  color: #34405a;
  font-weight: 500;
}

.edit-ic {
  cursor: pointer;
  color: #98a2b3;
  font-size: 13px;
}

.edit-ic:hover {
  color: #2f7bff;
}

.no-comment {
  color: #c0c8d6;
}
</style>
