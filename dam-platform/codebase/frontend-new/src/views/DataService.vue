<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">数据服务</h2>
        <p class="page-desc">把资产封装成可调用的查询 API：AppKey 鉴权、参数白名单、限流留痕，敏感字段出网关前自动脱敏。</p>
      </div>
      <div class="stat-row">
        <div class="stat"><b>{{ summary.publishedCount ?? 0 }} / {{ summary.apiCount ?? 0 }}</b><span>已发布</span></div>
        <div class="stat"><b>{{ fmtNum(summary.callTotal) }}</b><span>累计调用</span></div>
        <div class="stat fail"><b>{{ summary.failCount ?? 0 }}</b><span>失败调用</span></div>
      </div>
    </div>

    <el-tabs v-model="tab">
      <!-- ============ API 服务 ============ -->
      <el-tab-pane label="API 服务" name="apis">
        <div class="toolbar">
          <el-input v-model="q" placeholder="搜索 API 名称 / 编码" clearable style="width: 240px"
                    :prefix-icon="Search" @input="loadApis" />
          <div class="spacer"></div>
          <el-button type="primary" :icon="Plus" @click="openApiForm()">新建 API</el-button>
        </div>
        <el-table :data="apis" size="small">
          <el-table-column label="编码" width="150">
            <template #default="{ row }"><span class="mono">{{ row.apiCode }}</span></template>
          </el-table-column>
          <el-table-column prop="name" label="名称" min-width="130" />
          <el-table-column prop="assetName" label="绑定资产" min-width="130" />
          <el-table-column label="状态" width="92">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'PUBLISHED' ? 'success' : row.status === 'DISABLED' ? 'danger' : 'info'">
                {{ { PUBLISHED: '已发布', DRAFT: '草稿', DISABLED: '已下线' }[row.status] || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="限流" width="76">
            <template #default="{ row }">{{ row.rateLimit }}/分</template>
          </el-table-column>
          <el-table-column prop="callCount" label="调用" width="76" />
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="openDebug(row)">调试</el-button>
              <el-button size="small" text type="primary" @click="showDoc(row)">文档</el-button>
              <el-button size="small" text type="primary" @click="openApiForm(row)">编辑</el-button>
              <el-button size="small" text :type="row.status === 'PUBLISHED' ? 'warning' : 'success'"
                         @click="publish(row)">
                {{ row.status === 'PUBLISHED' ? '下线' : '发布' }}
              </el-button>
              <el-button size="small" text type="danger" @click="removeApi(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ============ 客户端密钥 ============ -->
      <el-tab-pane label="客户端密钥" name="clients">
        <div class="toolbar">
          <div class="hint">调用网关时携带请求头 <span class="mono">X-App-Id</span> 与 <span class="mono">X-App-Key</span></div>
          <div class="spacer"></div>
          <el-button type="primary" :icon="Plus" @click="clientDialog = true">注册应用</el-button>
        </div>
        <el-table :data="clients" size="small">
          <el-table-column label="AppId" width="160">
            <template #default="{ row }"><span class="mono">{{ row.appId }}</span></template>
          </el-table-column>
          <el-table-column label="AppKey" min-width="200">
            <template #default="{ row }">
              <span class="mono">{{ row._reveal ? row.appKey : 'ak-••••••••••••••••••••' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="appName" label="应用名称" min-width="140" />
          <el-table-column prop="contact" label="负责人" width="90" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="调用次数" width="90" prop="callCount" />
          <el-table-column label="最近调用" width="150">
            <template #default="{ row }">{{ row.lastCallAt ? fmtTime(row.lastCallAt) : '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="row._reveal = !row._reveal">
                {{ row._reveal ? '隐藏' : '显示' }}
              </el-button>
              <el-button size="small" text type="warning" @click="regen(row)">换 Key</el-button>
              <el-button size="small" text :type="row.status === 'ACTIVE' ? 'warning' : 'success'" @click="toggleClient(row)">
                {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
              </el-button>
              <el-button size="small" text type="danger" @click="removeClient(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ============ 调用日志 ============ -->
      <el-tab-pane label="调用日志" name="logs">
        <div class="toolbar">
          <el-select v-model="logApiCode" placeholder="全部 API" clearable style="width: 220px" @change="loadLogs">
            <el-option v-for="a in apis" :key="a.id" :label="a.apiCode + ' ' + a.name" :value="a.apiCode" />
          </el-select>
          <div class="spacer"></div>
          <el-button :icon="Refresh" circle @click="loadLogs" />
        </div>
        <el-table :data="logs" size="small" max-height="520">
          <el-table-column label="时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.calledAt) }}</template>
          </el-table-column>
          <el-table-column label="API" width="150">
            <template #default="{ row }"><span class="mono">{{ row.apiCode }}</span></template>
          </el-table-column>
          <el-table-column label="调用方" width="140">
            <template #default="{ row }"><span class="mono">{{ row.appId }}</span></template>
          </el-table-column>
          <el-table-column prop="params" label="参数" min-width="150" show-overflow-tooltip />
          <el-table-column label="结果" width="96">
            <template #default="{ row }">
              <el-tag size="small" :type="row.success ? 'success' : 'danger'">
                {{ row.success ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="行数" width="70" prop="rowCount" />
          <el-table-column label="耗时" width="80">
            <template #default="{ row }">{{ row.durationMs }}ms</template>
          </el-table-column>
          <el-table-column label="脱敏字段" min-width="130">
            <template #default="{ row }">
              <span class="mask mono" v-if="row.maskedColumns">{{ row.maskedColumns }}</span>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="errorMsg" label="错误信息" min-width="150" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建/编辑 API -->
    <el-dialog v-model="apiDialog" :title="apiForm.id ? '编辑 API' : '新建 API'" width="640px">
      <el-form :model="apiForm" label-width="86px">
        <el-form-item label="编码">
          <el-input v-model="apiForm.apiCode" placeholder="如 API-PATIENT-003" :disabled="!!apiForm.id" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="apiForm.name" />
        </el-form-item>
        <el-form-item label="绑定资产">
          <el-select v-model="apiForm.assetId" filterable style="width:100%">
            <el-option v-for="a in assets" :key="a.id" :label="`${a.name}（${a.assetCode}）`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="查询 SQL">
          <el-input v-model="apiForm.sqlText" type="textarea" :rows="5" class="mono"
                    placeholder="仅允许单条 SELECT，命名参数写法 :paramName" />
        </el-form-item>
        <el-form-item label="参数定义">
          <el-input v-model="apiForm.paramsJson" type="textarea" :rows="3" class="mono"
                    placeholder='[{"name":"patientId","type":"number","required":true,"desc":"患者ID","example":1}]' />
        </el-form-item>
        <el-form-item label="限流/分">
          <el-input-number v-model="apiForm.rateLimit" :min="1" :max="6000" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="apiForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="apiDialog = false">取消</el-button>
        <el-button type="primary" @click="saveApi">保存</el-button>
      </template>
    </el-dialog>

    <!-- 在线调试 -->
    <el-dialog v-model="debugDialog" :title="`在线调试 · ${debugApi?.apiCode || ''}`" width="760px">
      <div class="debug-req mono">POST /openapi/v1/{{ debugApi?.apiCode }}</div>
      <el-form label-width="120px" style="margin-top:14px">
        <el-form-item v-for="p in debugParams" :key="p.name" :label="p.name + (p.required ? ' *' : '')">
          <el-input v-model="debugForm[p.name]" :placeholder="p.desc + (p.example !== undefined ? `，示例：${p.example}` : '')" />
        </el-form-item>
        <el-form-item v-if="!debugParams.length" label="参数">
          <span class="muted">该 API 无参数</span>
        </el-form-item>
      </el-form>
      <el-button type="primary" :loading="debugging" @click="doDebug">发起调用</el-button>
      <div class="debug-res mono" v-if="debugRes">
{{ debugRes }}
      </div>
    </el-dialog>

    <!-- 接口文档 -->
    <el-dialog v-model="docDialog" title="接口文档" width="640px">
      <pre class="doc-pre mono" v-if="docText">{{ docText }}</pre>
    </el-dialog>

    <!-- 注册应用 -->
    <el-dialog v-model="clientDialog" title="注册接入应用" width="460px">
      <el-form :model="clientForm" label-width="86px">
        <el-form-item label="AppId"><el-input v-model="clientForm.appId" placeholder="如 miniapp-h5" /></el-form-item>
        <el-form-item label="应用名称"><el-input v-model="clientForm.appName" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="clientForm.contact" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="clientDialog = false">取消</el-button>
        <el-button type="primary" @click="saveClient">注册并生成 Key</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { api, fmtNum, fmtTime } from '../api'

const tab = ref('apis')
const summary = ref({})
const apis = ref([])
const clients = ref([])
const logs = ref([])
const assets = ref([])
const q = ref('')
const logApiCode = ref('')

const apiDialog = ref(false)
const apiForm = reactive({ id: null, apiCode: '', name: '', assetId: null, sqlText: '', paramsJson: '', rateLimit: 60, description: '' })

const debugDialog = ref(false)
const debugApi = ref(null)
const debugParams = ref([])
const debugForm = reactive({})
const debugging = ref(false)
const debugRes = ref('')

const docDialog = ref(false)
const docText = ref('')

const clientDialog = ref(false)
const clientForm = reactive({ appId: '', appName: '', contact: '' })

async function loadSummary() { summary.value = await api.dsSummary() }
async function loadApis() { apis.value = await api.dsApis(q.value ? { q: q.value } : {}) }
async function loadClients() { clients.value = await api.dsClients() }
async function loadLogs() { logs.value = await api.dsLogs(logApiCode.value ? { apiCode: logApiCode.value } : {}) }

function openApiForm(row) {
  if (row) Object.assign(apiForm, row, { id: row.id })
  else Object.assign(apiForm, { id: null, apiCode: '', name: '', assetId: null, sqlText: '', paramsJson: '', rateLimit: 60, description: '' })
  apiDialog.value = true
}

async function saveApi() {
  if (!apiForm.apiCode || !apiForm.name || !apiForm.sqlText) return ElMessage.warning('编码 / 名称 / SQL 必填')
  const payload = { ...apiForm }
  delete payload.id
  delete payload.assetName
  if (apiForm.id) await api.updateApi(apiForm.id, payload)
  else await api.createApi(payload)
  ElMessage.success('已保存')
  apiDialog.value = false
  loadApis()
}

async function publish(row) {
  const next = row.status === 'PUBLISHED' ? 'DISABLED' : 'PUBLISHED'
  await api.publishApi(row.id, next)
  ElMessage.success(next === 'PUBLISHED' ? '已发布，网关即刻生效' : '已下线')
  loadApis()
}

async function removeApi(row) {
  await ElMessageBox.confirm(`确定删除 API ${row.apiCode}？`, '提示', { type: 'warning' })
  await api.deleteApi(row.id)
  ElMessage.success('已删除')
  loadApis()
}

async function openDebug(row) {
  debugApi.value = row
  debugRes.value = ''
  Object.keys(debugForm).forEach((k) => delete debugForm[k])
  const doc = await api.apiDoc(row.id)
  debugParams.value = doc.params || []
  debugDialog.value = true
}

async function doDebug() {
  debugging.value = true
  try {
    const res = await api.debugApi(debugApi.value.id, { ...debugForm })
    debugRes.value = JSON.stringify(res, null, 2)
    loadSummary(); loadLogs()
  } finally {
    debugging.value = false
  }
}

async function showDoc(row) {
  const doc = await api.apiDoc(row.id)
  docText.value = JSON.stringify({
    url: `POST http://localhost:8080/openapi/v1/${row.apiCode}`,
    headers: { 'X-App-Id': '<接入应用 AppId>', 'X-App-Key': '<AppKey>' },
    body: Object.fromEntries((doc.params || []).map((p) => [p.name, p.example])),
    response: { code: 0, message: 'success', data: '...', meta: { durationMs: 12, rowCount: 1, maskedColumns: ['id_card', 'phone'] } }
  }, null, 2)
  docDialog.value = true
}

async function saveClient() {
  if (!clientForm.appId) return ElMessage.warning('AppId 必填')
  const res = await api.createClient(clientForm)
  clientDialog.value = false
  ElMessageBox.alert(`应用注册成功，AppKey：${res.appKey}\n请立即保存，之后只可重置不可查看历史。`, '已生成密钥', { type: 'success' })
  Object.assign(clientForm, { appId: '', appName: '', contact: '' })
  loadClients()
}

async function regen(row) {
  await ElMessageBox.confirm(`重置 ${row.appId} 的 AppKey？旧 Key 立即失效。`, '提示', { type: 'warning' })
  const res = await api.regenKey(row.id)
  ElMessageBox.alert(`新 AppKey：${res.appKey}`, '已重置', { type: 'success' })
  loadClients()
}

async function toggleClient(row) {
  await api.updateClient(row.id, { status: row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' })
  loadClients()
}

async function removeClient(row) {
  await ElMessageBox.confirm(`确定删除应用 ${row.appId}？`, '提示', { type: 'warning' })
  await api.deleteClient(row.id)
  loadClients()
}

onMounted(async () => {
  const res = await api.assets({ page: 1, size: 200 })
  assets.value = res.items
  await Promise.all([loadSummary(), loadApis(), loadClients(), loadLogs()])
})
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 10px; }
.page-title { margin: 0 0 6px; font-size: 19px; }
.page-desc { margin: 0; color: #8a94a6; font-size: 12.5px; }

.stat-row { display: flex; gap: 12px; }
.stat {
  background: #fff; border: 1px solid #e9edf5; border-radius: 10px;
  padding: 8px 18px; text-align: center; min-width: 92px;
}
.stat b { display: block; font-size: 20px; }
.stat span { font-size: 11px; color: #8a94a6; }
.stat.fail b { color: #F5222D; }

.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.spacer { flex: 1; }
.hint { color: #8a94a6; font-size: 12px; }
.mono { font-family: monospace; }
.mask { color: #fa8c16; }
.muted { color: #c3ccd9; }

.debug-req {
  background: #0c1a30; color: #7ee2b8; padding: 10px 14px; border-radius: 8px; font-size: 13px;
}
.debug-res {
  margin-top: 12px; background: #f7f9fc; border: 1px solid #e8ecf3;
  padding: 12px 14px; border-radius: 8px; font-size: 12px;
  max-height: 320px; overflow: auto; white-space: pre;
}
.doc-pre {
  background: #f7f9fc; border: 1px solid #e8ecf3; padding: 14px;
  border-radius: 8px; font-size: 12px; overflow: auto;
}
</style>
