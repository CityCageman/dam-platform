<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">安全分级</h2>
        <p class="page-desc">按分级标准给资产与字段定级，配置脱敏方式与授权角色，敏感资产访问全程留痕。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openForm()">新增策略</el-button>
    </div>

    <div class="levels">
      <div class="level-card" v-for="l in levels" :key="l.code" :style="{ borderTopColor: l.color }">
        <div class="level-head">
          <span class="level-code" :style="{ background: l.color }">{{ l.code }}</span>
          <span class="level-name">{{ l.name }}</span>
        </div>
        <div class="level-count">{{ l.assetCount }}<span class="unit"> 个资产</span></div>
        <div class="level-desc">{{ l.description }}</div>
      </div>
    </div>

    <div class="split">
      <div class="panel">
        <div class="toolbar">
          <h3 class="panel-title" style="margin:0">脱敏策略</h3>
          <div class="spacer"></div>
          <el-select v-model="desensitizeFilter" placeholder="脱敏方式" clearable style="width: 130px">
            <el-option label="不脱敏" value="NONE" />
            <el-option label="掩码" value="MASK" />
            <el-option label="部分隐藏" value="PARTIAL" />
            <el-option label="哈希" value="HASH" />
          </el-select>
        </div>

        <el-table :data="filteredPolicies" size="small" max-height="480">
          <el-table-column prop="assetName" label="资产" min-width="140" />
          <el-table-column label="字段" width="130">
            <template #default="{ row }"><span class="mono">{{ row.columnName || '整表' }}</span></template>
          </el-table-column>
          <el-table-column label="分级" width="90">
            <template #default="{ row }">
              <el-tag size="small" effect="dark" :color="LEVEL_META[row.levelCode]?.color" style="border:none">
                {{ row.levelCode }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="脱敏方式" width="110">
            <template #default="{ row }">
              <span :class="row.desensitize === 'NONE' ? 'muted' : 'mask'">{{ desensitizeLabel(row.desensitize) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="authorizedRoles" label="授权角色" min-width="170" show-overflow-tooltip />
          <el-table-column label="审计" width="70">
            <template #default="{ row }">
              <el-tag size="small" :type="row.auditEnabled ? 'success' : 'info'" effect="plain">
                {{ row.auditEnabled ? '开' : '关' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="openForm(row)">编辑</el-button>
              <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="panel">
        <h3 class="panel-title">敏感资产（L3 / L4）</h3>
        <div class="sensitive" v-for="a in summary.sensitiveAssets" :key="a.id">
          <div class="sensitive-head">
            <span class="sensitive-name">{{ a.name }}</span>
            <el-tag size="small" effect="dark" :color="a.color" style="border:none">{{ a.levelName }}</el-tag>
          </div>
          <div class="sensitive-meta">
            <span class="mono">{{ a.assetCode }}</span>
            <span>· 脱敏字段 {{ a.maskedColumns }} 个</span>
          </div>
        </div>
        <el-empty v-if="!summary.sensitiveAssets?.length" description="暂无敏感资产" :image-size="70" />
      </div>
    </div>

    <div class="panel preview-panel">
      <div class="toolbar">
        <h3 class="panel-title" style="margin:0">脱敏引擎预览（真实查询）</h3>
        <span class="preview-hint">对本机数据源发起真实 SQL 查询，对照展示原始值与按策略脱敏后的值</span>
        <div class="spacer"></div>
        <el-select v-model="previewAssetId" filterable placeholder="选择资产" style="width: 240px">
          <el-option v-for="a in previewableAssets" :key="a.id" :label="`${a.name}（${a.assetCode}）`" :value="a.id" />
        </el-select>
        <el-button type="primary" :loading="previewing" :disabled="!previewAssetId" @click="runPreview">
          执行预览
        </el-button>
      </div>

      <template v-if="previewData">
        <div class="preview-tags">
          <el-tag v-for="c in previewData.maskedColumns" :key="c" size="small" type="warning" effect="plain"
                  class="mono">{{ c }} 已脱敏</el-tag>
          <el-tag v-if="!previewData.maskedColumns?.length" size="small" type="info" effect="plain">
            该资产无脱敏列
          </el-tag>
        </div>
        <el-table :data="previewPairs" size="small" class="mono" border>
          <el-table-column prop="column" label="字段" width="140" />
          <el-table-column prop="raw" label="原始值" min-width="200" />
          <el-table-column prop="masked" label="脱敏后" min-width="200">
            <template #default="{ row }">
              <span :class="{ changed: row.changed }">{{ row.masked }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="strategy" label="策略" width="110">
            <template #default="{ row }">
              <el-tag v-if="row.strategy && row.strategy !== 'NONE'" size="small" type="warning" effect="plain">
                {{ desensitizeLabel(row.strategy) }}
              </el-tag>
              <span v-else class="muted">不脱敏</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else description="选择绑定本机数据源的资产后执行预览" :image-size="60" />
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑策略' : '新增策略'" width="560px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="资产">
          <el-select v-model="form.assetId" filterable style="width:100%" :disabled="!!form.id">
            <el-option v-for="a in assets" :key="a.id" :label="`${a.name}（${a.assetCode}）`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="字段">
          <el-input v-model="form.columnName" placeholder="留空表示整表策略" />
        </el-form-item>
        <el-form-item label="安全等级">
          <el-select v-model="form.levelCode" style="width:100%">
            <el-option v-for="l in levels" :key="l.code" :label="`${l.code} ${l.name}`" :value="l.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="脱敏方式">
          <el-select v-model="form.desensitize" style="width:100%">
            <el-option label="不脱敏" value="NONE" />
            <el-option label="掩码（张*）" value="MASK" />
            <el-option label="部分隐藏（110***********4521）" value="PARTIAL" />
            <el-option label="哈希（不可逆摘要）" value="HASH" />
          </el-select>
        </el-form-item>
        <el-form-item label="授权角色">
          <el-input v-model="form.authorizedRoles" placeholder="逗号分隔，如：病案室,信息中心" />
        </el-form-item>
        <el-form-item label="访问审计">
          <el-switch v-model="form.auditEnabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { api, LEVEL_META } from '../api'

const levels = ref([])
const policies = ref([])
const summary = ref({})
const assets = ref([])
const dialog = ref(false)
const desensitizeFilter = ref('')

const previewAssetId = ref(null)
const previewing = ref(false)
const previewData = ref(null)

const previewableAssets = computed(() => assets.value)
const previewPairs = computed(() => {
  if (!previewData.value) return []
  const strategyByColumn = {}
  previewData.value.policies.forEach((p) => { strategyByColumn[p.columnName] = p.desensitize })
  const raw = previewData.value.raw || []
  const masked = previewData.value.masked || []
  const columns = raw.length ? Object.keys(raw[0]) : []
  return columns.map((c) => ({
    column: c,
    raw: raw.map((r) => r[c] ?? '').join('  |  '),
    masked: masked.map((r) => r[c] ?? '').join('  |  '),
    strategy: strategyByColumn[c] || 'NONE',
    changed: JSON.stringify(raw.map((r) => r[c])) !== JSON.stringify(masked.map((r) => r[c]))
  }))
})

async function runPreview() {
  previewing.value = true
  try {
    previewData.value = await api.maskPreview(previewAssetId.value, 5)
  } finally {
    previewing.value = false
  }
}

const form = reactive({
  id: null, assetId: null, columnName: '', levelCode: 'L3',
  desensitize: 'MASK', authorizedRoles: '', auditEnabled: true
})

const filteredPolicies = computed(() =>
  policies.value.filter((p) => !desensitizeFilter.value || p.desensitize === desensitizeFilter.value)
)

function desensitizeLabel(v) {
  return { NONE: '不脱敏', MASK: '掩码', PARTIAL: '部分隐藏', HASH: '哈希' }[v] || v
}

async function load() {
  const [lv, pl, sm] = await Promise.all([api.securityLevels(), api.securityPolicies(), api.securitySummary()])
  levels.value = lv
  policies.value = pl
  summary.value = sm
}

function openForm(row) {
  if (row) Object.assign(form, row)
  else Object.assign(form, {
    id: null, assetId: null, columnName: '', levelCode: 'L3',
    desensitize: 'MASK', authorizedRoles: '', auditEnabled: true
  })
  dialog.value = true
}

async function save() {
  if (!form.assetId) return ElMessage.warning('请选择资产')
  const payload = { ...form }
  delete payload.id
  if (form.id) await api.updatePolicy(form.id, payload)
  else await api.createPolicy(payload)
  ElMessage.success('已保存')
  dialog.value = false
  load()
}

async function remove(row) {
  await ElMessageBox.confirm('确定删除该脱敏策略？', '提示', { type: 'warning' })
  await api.deletePolicy(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  const res = await api.assets({ page: 1, size: 200 })
  assets.value = res.items
  // 默认选中演示真实数据资产
  const demo = assets.value.find((a) => a.assetCode === 'AST-0015')
  if (demo) previewAssetId.value = demo.id
  await load()
})
</script>

<style scoped>
.levels {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}

.level-card {
  background: #fff;
  border: 1px solid #e9edf5;
  border-top: 3px solid #ccc;
  border-radius: 12px;
  padding: 14px 16px;
}

.level-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.level-code {
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  border-radius: 4px;
  padding: 2px 6px;
}

.level-name {
  font-size: 13px;
  font-weight: 600;
}

.level-count {
  font-size: 24px;
  font-weight: 700;
  margin: 10px 0 4px;
}

.unit {
  font-size: 12px;
  font-weight: 400;
  color: #8a94a6;
}

.level-desc {
  color: #8a94a6;
  font-size: 11.5px;
  line-height: 1.6;
  min-height: 36px;
}

.split {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 14px;
  align-items: start;
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

.mask {
  color: #fa8c16;
  font-weight: 600;
}

.sensitive {
  padding: 10px 0;
  border-bottom: 1px dashed #eef1f7;
}

.sensitive-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.sensitive-name {
  font-size: 13px;
  font-weight: 500;
}

.sensitive-meta {
  color: #98a2b3;
  font-size: 11.5px;
  margin-top: 4px;
  display: flex;
  gap: 6px;
}

.preview-panel { margin-bottom: 14px; }

.preview-hint { color: #98a2b3; font-size: 11.5px; margin-left: 10px; }

.preview-tags { display: flex; gap: 8px; margin: 10px 0; }

.changed { color: #fa8c16; font-weight: 600; }

.muted { color: #c3ccd9; }

.mono { font-family: monospace; }
</style>
