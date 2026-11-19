<template>
  <CockpitShell
    :title="config.title"
    :description="config.description"
    :loading="loading"
    :updated-at="updatedAt"
    @refresh="load"
  >
    <div class="ck-stat-grid">
      <div v-for="stat in stats" :key="stat.label" class="ck-stat" :class="stat.tone ? `is-${stat.tone}` : ''">
        <div class="ck-stat-label">{{ stat.label }}</div>
        <div class="ck-stat-value">{{ stat.value }}</div>
        <div class="ck-stat-foot">{{ stat.foot }}</div>
      </div>
    </div>

    <div class="ck-grid ck-grid-main">
      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">{{ config.roleName }}工作台</h3>
          <span class="ck-card-sub">角色默认入口</span>
        </div>
        <div class="ck-entry-grid">
          <router-link v-for="entry in config.entries" :key="entry.path" :to="entry.path" class="ck-entry-card">
            <div class="ck-entry-icon">
              <el-icon :size="18"><component :is="resolvePortalIcon(entry.icon)" /></el-icon>
            </div>
            <div>
              <div class="ck-entry-name">{{ entry.title }}</div>
              <div class="ck-entry-desc">{{ entry.desc }}</div>
            </div>
          </router-link>
        </div>
      </section>

      <section class="ck-card">
        <div class="ck-card-head">
          <h3 class="ck-card-title">驾驶舱建设说明</h3>
        </div>
        <div class="ck-empty-block">
          <el-empty :description="config.emptyText" :image-size="64" />
        </div>
        <div class="ck-kv">
          <div class="ck-kv-row"><span>质量达标率</span><b>{{ qualityRate }}</b></div>
          <div class="ck-kv-row"><span>最近检测</span><b style="font-size: 13px;">{{ formatDateTime(quality.lastCheckTime) }}</b></div>
        </div>
      </section>
    </div>

    <section class="ck-card">
      <div class="ck-card-head">
        <h3 class="ck-card-title">{{ config.roleName }}待办与风险</h3>
        <span class="ck-card-sub">通用数据概览，后续按角色细化</span>
      </div>
      <div class="ck-list">
        <router-link to="/operations" class="ck-list-item">
          <div><b>待处理工单</b><span>数据申请与质量问题</span></div>
          <em>{{ pendingTickets }}</em>
        </router-link>
        <router-link to="/security" class="ck-list-item">
          <div><b>待审批数据申请</b><span>数据访问安全审批</span></div>
          <em>{{ pendingRequests.length }}</em>
        </router-link>
        <router-link to="/quality" class="ck-list-item">
          <div><b>质量检测</b><span>规则执行与异常样例</span></div>
          <em>进入</em>
        </router-link>
      </div>
    </section>
  </CockpitShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { dataServiceApi, operationsApi, qualityApi, securityApi } from '@/api'
import { resolvePortalIcon } from '@/config/portal'
import CockpitShell from './components/CockpitShell.vue'
import {
  arrayOf,
  display,
  formatDateTime,
  nowText,
  percentText,
  recordsOf,
  settledValue,
  toNumber,
  totalOf,
  unwrap
} from './shared'

type Variant = 'governance' | 'business' | 'security'

interface PlaceholderEntry {
  title: string
  desc: string
  path: string
  icon: string
}

interface VariantConfig {
  title: string
  roleName: string
  description: string
  emptyText: string
  entries: PlaceholderEntry[]
}

const props = defineProps<{ variant?: Variant }>()

const CONFIG: Record<Variant, VariantConfig> = {
  governance: {
    title: '数据管家驾驶舱',
    roleName: '数据管家',
    description: '数据质量、标准与治理协同概览。',
    emptyText: '数据管家驾驶舱正在细化建设，当前展示质量与治理通用概览',
    entries: [
      { title: '数据质量', desc: '规则配置与稽核结果', path: '/quality', icon: 'Histogram' },
      { title: '数据标准', desc: '标准、码表与偏差', path: '/standard', icon: 'Document' },
      { title: '数据血缘', desc: '表级 / 字段级链路', path: '/lineage', icon: 'Share' },
      { title: '生命周期', desc: '冷热分层与归档', path: '/lifecycle', icon: 'Odometer' }
    ]
  },
  business: {
    title: '业务驾驶舱',
    roleName: '业务用户',
    description: '可申请资产、数据服务与个人待办概览。',
    emptyText: '业务驾驶舱正在细化建设，当前展示资产与服务通用概览',
    entries: [
      { title: '产品中心', desc: '按能力域查找数据产品', path: '/portal', icon: 'Grid' },
      { title: '资产目录', desc: '搜索与申请数据资产', path: '/catalog', icon: 'FolderOpened' },
      { title: '数据服务', desc: '查看可用数据 API', path: '/data-service', icon: 'Link' },
      { title: '指标管理', desc: '查看业务指标口径', path: '/metrics', icon: 'TrendCharts' }
    ]
  },
  security: {
    title: '安全驾驶舱',
    roleName: '安全管理员',
    description: '数据访问审批、安全规则与审计概览。',
    emptyText: '安全驾驶舱正在细化建设，当前展示审批与安全通用概览',
    entries: [
      { title: '安全管控', desc: '分级分类与脱敏策略', path: '/security', icon: 'Lock' },
      { title: '工单中心', desc: '数据申请与处理进度', path: '/operations', icon: 'Tools' },
      { title: '系统配置', desc: '用户、角色与授权', path: '/system-config', icon: 'Setting' },
      { title: '数据质量', desc: '质量规则与异常', path: '/quality', icon: 'Histogram' }
    ]
  }
}

const variant = computed<Variant>(() => props.variant && CONFIG[props.variant] ? props.variant : 'governance')
const config = computed(() => CONFIG[variant.value])

const loading = ref(false)
const updatedAt = ref('')
const overview = ref<any>({})
const quality = ref<any>({})
const apiTotal = ref(0)
const pendingRequests = ref<any[]>([])
const sensitiveTotal = ref(0)
const auditTotal = ref(0)

const assetTotal = computed(() => toNumber(overview.value.totalAssets, 0))
const pendingTickets = computed(() => toNumber(overview.value.pendingTickets, 0))
const qualityRate = computed(() => percentText(quality.value.avgScore))

const stats = computed(() => {
  if (variant.value === 'security') {
    return [
      { label: '待审批申请', value: String(pendingRequests.value.length), foot: '数据访问申请', tone: 'danger' },
      { label: '敏感规则', value: String(sensitiveTotal.value), foot: '敏感数据识别规则', tone: 'gold' },
      { label: '审计日志', value: String(auditTotal.value), foot: '数据访问审计记录', tone: '' },
      { label: '资产总数', value: String(assetTotal.value), foot: '已登记数据资产', tone: 'accent' }
    ]
  }
  if (variant.value === 'business') {
    return [
      { label: '资产总数', value: String(assetTotal.value), foot: '可检索数据资产', tone: 'accent' },
      { label: '数据服务', value: String(apiTotal.value), foot: '可用数据 API', tone: 'green' },
      { label: '待办工单', value: String(pendingTickets.value), foot: '数据申请与质量问题', tone: 'gold' },
      { label: '质量达标率', value: qualityRate.value, foot: '最近检测平均通过率', tone: '' }
    ]
  }
  return [
    { label: '质量规则', value: display(quality.value.totalRules), foot: `已启用 ${display(quality.value.enabledRules)}`, tone: 'accent' },
    { label: '质量达标率', value: qualityRate.value, foot: '最近检测平均通过率', tone: 'green' },
    { label: '资产总数', value: String(assetTotal.value), foot: '已登记数据资产', tone: 'gold' },
    { label: '待办工单', value: String(pendingTickets.value), foot: '数据申请与质量问题', tone: 'danger' }
  ]
})

async function load() {
  loading.value = true
  try {
    const results = await Promise.allSettled([
      operationsApi.getDashboard(),
      qualityApi.getOverview(),
      dataServiceApi.listApis({ page: 1, pageSize: 1 }),
      securityApi.getPendingRequests(),
      securityApi.getSensitiveRules(),
      securityApi.searchAuditLogs({ page: 1, pageSize: 1 })
    ])

    overview.value = unwrap(settledValue(results[0])) || {}
    quality.value = unwrap(settledValue(results[1])) || {}
    apiTotal.value = totalOf(settledValue(results[2]))
    pendingRequests.value = arrayOf(unwrap(settledValue(results[3])))
    sensitiveTotal.value = recordsOf(settledValue(results[4])).length
    auditTotal.value = totalOf(settledValue(results[5]))
  } finally {
    loading.value = false
    updatedAt.value = nowText()
  }
}

onMounted(load)
</script>
