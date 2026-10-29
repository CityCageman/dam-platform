<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">数据质量</h2>
        <p class="page-desc">按非空、唯一、值域、枚举、正则、时效性等规则周期性校验，执行结果可追溯到每条规则。</p>
      </div>
      <div>
        <el-button :icon="Plus" @click="openForm()">新建规则</el-button>
        <el-button type="primary" :icon="VideoPlay" :loading="runningAll" @click="runAll">全部执行</el-button>
      </div>
    </div>

    <div class="stat-grid" style="margin-bottom:14px">
      <div class="stat-card">
        <div class="stat-label">质量规则</div>
        <div class="stat-value">{{ summary.ruleCount || 0 }}</div>
        <div class="stat-foot">启用 {{ summary.enabledCount || 0 }} 条</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日执行</div>
        <div class="stat-value">{{ summary.todayRunCount || 0 }}</div>
        <div class="stat-foot">含手动与定时任务</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日平均通过率</div>
        <div class="stat-value">{{ summary.todayPassRate || 0 }}<span class="unit">%</span></div>
        <div class="stat-foot">按今日全部结果</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">未通过规则</div>
        <div class="stat-value danger">{{ summary.todayFailCount || 0 }}</div>
        <div class="stat-foot">需关注</div>
      </div>
    </div>

    <div class="panel" style="margin-bottom:14px">
      <div class="panel-head">
        <h3 class="panel-title">通过率趋势（近 14 天）</h3>
        <el-radio-group v-model="days" size="small" @change="loadTrend">
          <el-radio-button :value="7">7 天</el-radio-button>
          <el-radio-button :value="14">14 天</el-radio-button>
          <el-radio-button :value="30">30 天</el-radio-button>
        </el-radio-group>
      </div>
      <div ref="trendRef" class="chart"></div>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索规则名称" clearable :prefix-icon="Search" style="width: 240px"
                  @keyup.enter="load" @clear="load" />
        <el-select v-model="typeFilter" placeholder="规则类型" clearable style="width: 140px" @change="load">
          <el-option v-for="(v, k) in RULE_LABEL" :key="k" :label="v" :value="k" />
        </el-select>
        <div class="spacer"></div>
        <span class="muted">{{ rules.length }} 条规则</span>
      </div>

      <el-table :data="filtered" size="small">
        <el-table-column prop="name" label="规则名称" min-width="210" />
        <el-table-column prop="assetName" label="所属资产" min-width="140" />
        <el-table-column prop="columnName" label="校验字段" width="140">
          <template #default="{ row }"><span class="mono">{{ row.columnName || '整表' }}</span></template>
        </el-table-column>
        <el-table-column label="规则类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ RULE_LABEL[row.ruleType] || row.ruleType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="严重程度" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.severity === 'HIGH' ? 'danger' : row.severity === 'MEDIUM' ? 'warning' : 'info'">
              {{ row.severity === 'HIGH' ? '高' : row.severity === 'MEDIUM' ? '中' : '低' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="threshold" label="阈值" width="80" />
        <el-table-column label="最新通过率" width="120">
          <template #default="{ row }">
            <span v-if="row.lastPassRate != null" :class="row.lastPassRate >= row.threshold ? 'ok' : 'danger'">
              {{ row.lastPassRate }}%
            </span>
            <span v-else class="muted">未执行</span>
          </template>
        </el-table-column>
        <el-table-column label="最近执行" width="150">
          <template #default="{ row }">{{ fmtTime(row.lastRunAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" :loading="runningId === row.id" @click="run(row)">执行</el-button>
            <el-button size="small" text @click="showResults(row)">历史</el-button>
            <el-button size="small" text @click="openForm(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="resultDrawer" :title="`执行历史 · ${currentRule?.name || ''}`" size="620px">
      <el-table :data="results" size="small">
        <el-table-column label="执行时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.runAt) }}</template>
        </el-table-column>
        <el-table-column label="总量" width="90">
          <template #default="{ row }">{{ fmtNum(row.totalCount) }}</template>
        </el-table-column>
        <el-table-column label="通过" width="90">
          <template #default="{ row }">{{ fmtNum(row.passCount) }}</template>
        </el-table-column>
        <el-table-column label="通过率" width="100">
          <template #default="{ row }">
            <span :class="row.status === 'PASS' ? 'ok' : 'danger'">{{ row.passRate }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="数据来源" min-width="150">
          <template #default="{ row }">
            <el-tag size="small" :type="(row.detail || '').includes('REAL') ? 'success' : 'info'" effect="plain">
              {{ (row.detail || '').includes('REAL') ? '真实执行' : '模拟数据' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog v-model="dialog" :title="form.id ? '编辑规则' : '新建规则'" width="560px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="规则名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="所属资产">
          <el-select v-model="form.assetId" filterable style="width:100%">
            <el-option v-for="a in assets" :key="a.id" :label="`${a.name}（${a.assetCode}）`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="校验字段">
          <el-input v-model="form.columnName" placeholder="留空表示整表校验" />
        </el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="form.ruleType" style="width:100%">
            <el-option v-for="(v, k) in RULE_LABEL" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则表达式">
          <el-input v-model="form.ruleExpr" placeholder="如 ^\\d{17}[\\dXx]$ 或 amount > 0" />
        </el-form-item>
        <el-form-item label="通过率阈值">
          <el-input-number v-model="form.threshold" :min="0" :max="100" :precision="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="严重程度">
          <el-select v-model="form.severity" style="width:100%">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
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
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, VideoPlay } from '@element-plus/icons-vue'
import { api, RULE_LABEL, fmtTime, fmtNum } from '../api'

const rules = ref([])
const assets = ref([])
const summary = ref({})
const keyword = ref('')
const typeFilter = ref('')
const days = ref(7)
const runningId = ref(null)
const runningAll = ref(false)
const dialog = ref(false)
const resultDrawer = ref(false)
const results = ref([])
const currentRule = ref(null)
const trendRef = ref()
let chart = null

const form = reactive({
  id: null, name: '', assetId: null, columnName: '', ruleType: 'NOT_NULL',
  ruleExpr: '', threshold: 95, severity: 'MEDIUM'
})

const filtered = computed(() => rules.value.filter((r) => {
  const kw = keyword.value.trim()
  const okKw = !kw || r.name.includes(kw) || (r.assetName || '').includes(kw)
  const okType = !typeFilter.value || r.ruleType === typeFilter.value
  return okKw && okType
}))

async function load() {
  rules.value = await api.qualityRules()
  summary.value = await api.qualitySummary()
}

async function loadTrend() {
  const data = await api.qualityTrend(days.value)
  if (!trendRef.value) return
  if (!chart) chart = echarts.init(trendRef.value)
  chart.setOption({
    grid: { left: 42, right: 18, top: 20, bottom: 28 },
    tooltip: { trigger: 'axis', valueFormatter: (v) => v + '%' },
    xAxis: {
      type: 'category',
      data: data.map((d) => d.day),
      axisLine: { lineStyle: { color: '#e5e9f2' } },
      axisLabel: { color: '#8a94a6' }
    },
    yAxis: {
      type: 'value', min: 80, max: 100,
      splitLine: { lineStyle: { color: '#f0f2f7' } },
      axisLabel: { color: '#8a94a6', formatter: '{value}%' }
    },
    series: [
      {
        type: 'line', smooth: true, symbolSize: 7,
        data: data.map((d) => d.passRate),
        itemStyle: { color: '#2f7bff' },
        lineStyle: { width: 2.5 },
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: '#f04438', type: 'dashed' },
          label: { formatter: '阈值 95%', color: '#f04438', fontSize: 11 },
          data: [{ yAxis: 95 }]
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(47,123,255,.25)' },
            { offset: 1, color: 'rgba(47,123,255,0)' }
          ])
        }
      }
    ]
  })
}

async function run(row) {
  runningId.value = row.id
  try {
    const r = await api.runRule(row.id)
    ElMessage[r.status === 'PASS' ? 'success' : 'warning'](`执行完成：通过率 ${r.passRate}%（${r.status}）`)
    await load()
    await loadTrend()
  } finally {
    runningId.value = null
  }
}

async function runAll() {
  runningAll.value = true
  try {
    const r = await api.runAll()
    ElMessage.success(`已执行 ${r.ruleCount} 条规则，耗时 ${r.costMs}ms`)
    await load()
    await loadTrend()
  } finally {
    runningAll.value = false
  }
}

async function showResults(row) {
  currentRule.value = row
  results.value = await api.qualityResults({ ruleId: row.id, limit: 30 })
  resultDrawer.value = true
}

function openForm(row) {
  if (row) Object.assign(form, row)
  else Object.assign(form, {
    id: null, name: '', assetId: null, columnName: '', ruleType: 'NOT_NULL',
    ruleExpr: '', threshold: 95, severity: 'MEDIUM'
  })
  dialog.value = true
}

async function save() {
  if (!form.name.trim()) return ElMessage.warning('请填写规则名称')
  const payload = { ...form }
  delete payload.id
  if (form.id) await api.updateRule(form.id, payload)
  else await api.createRule(payload)
  ElMessage.success('已保存')
  dialog.value = false
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除规则「${row.name}」及其历史结果？`, '提示', { type: 'warning' })
  await api.deleteRule(row.id)
  ElMessage.success('已删除')
  load()
}

const onResize = () => chart?.resize()

onMounted(async () => {
  const res = await api.assets({ page: 1, size: 200 })
  assets.value = res.items
  await load()
  await loadTrend()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<style scoped>
.unit {
  font-size: 13px;
  margin-left: 2px;
  color: #8a94a6;
}

.danger {
  color: #f5222d;
  font-weight: 600;
}

.ok {
  color: #12b76a;
  font-weight: 600;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart {
  height: 210px;
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
