import axios from 'axios'
import { ElMessage } from 'element-plus'
import { auth, clearLogin } from './store'

const http = axios.create({
  baseURL: '/api',
  timeout: 20000
})

http.interceptors.request.use((config) => {
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    if (err?.response?.status === 401) {
      clearLogin()
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
      return Promise.reject(err)
    }
    const msg = err?.response?.data?.message || err.message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export const api = {
  // 认证
  login: (d) => http.post('/auth/login', d),
  me: () => http.get('/auth/me'),
  logout: () => http.post('/auth/logout'),
  users: () => http.get('/auth/users'),
  createUser: (d) => http.post('/auth/users', d),
  updateUser: (id, d) => http.put(`/auth/users/${id}`, d),
  deleteUser: (id) => http.delete(`/auth/users/${id}`),

  overview: () => http.get('/overview'),

  // 元数据
  sources: () => http.get('/datasources'),
  createSource: (d) => http.post('/datasources', d),
  updateSource: (id, d) => http.put(`/datasources/${id}`, d),
  deleteSource: (id) => http.delete(`/datasources/${id}`),
  syncSource: (id) => http.post(`/datasources/${id}/sync`, null, { timeout: 300000 }),
  testSource: (id) => http.post(`/datasources/${id}/test`),
  metadataStats: () => http.get('/metadata/stats'),
  tables: (params) => http.get('/metadata/tables', { params }),
  tableDetail: (id) => http.get(`/metadata/tables/${id}`),
  updateTableComment: (id, comment) => http.put(`/metadata/tables/${id}/comment`, { comment }),
  updateColumnComment: (id, comment) => http.put(`/metadata/columns/${id}/comment`, { comment }),

  // 资产
  domains: () => http.get('/domains'),
  createDomain: (d) => http.post('/domains', d),
  assets: (params) => http.get('/assets', { params }),
  asset: (id) => http.get(`/assets/${id}`),
  createAsset: (d) => http.post('/assets', d),
  updateAsset: (id, d) => http.put(`/assets/${id}`, d),
  deleteAsset: (id) => http.delete(`/assets/${id}`),
  favorite: (id) => http.post(`/assets/${id}/favorite`),
  viewAsset: (id) => http.post(`/assets/${id}/view`),
  fromTable: (tableId) => http.post(`/assets/from-table/${tableId}`),
  tags: () => http.get('/assets/tags'),

  // 血缘
  lineageGraph: (assetId, depth = 2) => http.get('/lineage/graph', { params: { assetId, depth } }),
  lineageEdges: (assetId) => http.get('/lineage/edges', { params: { assetId } }),
  createEdge: (d) => http.post('/lineage/edges', d),
  deleteEdge: (id) => http.delete(`/lineage/edges/${id}`),

  // 标准
  standards: (params) => http.get('/standards', { params }),
  standardCategories: () => http.get('/standards/categories'),
  createStandard: (d) => http.post('/standards', d),
  updateStandard: (id, d) => http.put(`/standards/${id}`, d),
  deleteStandard: (id) => http.delete(`/standards/${id}`),
  conformance: () => http.get('/standards/conformance'),

  // 标准码表与代码对照
  codeTables: (params) => http.get('/standards/code-tables', { params }),
  codeValues: (id) => http.get(`/standards/code-tables/${id}/values`),
  crosswalk: (params) => http.get('/standards/crosswalk', { params }),
  crosswalkSources: () => http.get('/standards/crosswalk/sources'),
  createCrosswalk: (d) => http.post('/standards/crosswalk', d),
  updateCrosswalk: (id, d) => http.put(`/standards/crosswalk/${id}`, d),
  deleteCrosswalk: (id) => http.delete(`/standards/crosswalk/${id}`),

  // 质量
  qualityRules: (params) => http.get('/quality/rules', { params }),
  createRule: (d) => http.post('/quality/rules', d),
  updateRule: (id, d) => http.put(`/quality/rules/${id}`, d),
  deleteRule: (id) => http.delete(`/quality/rules/${id}`),
  runRule: (id) => http.post(`/quality/rules/${id}/run`),
  runAll: () => http.post('/quality/run-all'),
  qualityResults: (params) => http.get('/quality/results', { params }),
  qualitySummary: () => http.get('/quality/summary'),
  qualityTrend: (days = 7) => http.get('/quality/trend', { params: { days } }),

  // 安全
  securityLevels: () => http.get('/security/levels'),
  securityPolicies: (params) => http.get('/security/policies', { params }),
  createPolicy: (d) => http.post('/security/policies', d),
  updatePolicy: (id, d) => http.put(`/security/policies/${id}`, d),
  deletePolicy: (id) => http.delete(`/security/policies/${id}`),
  securitySummary: () => http.get('/security/summary'),
  maskPreview: (assetId, limit = 5) => http.get('/security/mask-preview', { params: { assetId, limit } }),

  // 数据服务网关
  dsSummary: () => http.get('/dataservice/summary'),
  dsApis: (params) => http.get('/dataservice/apis', { params }),
  createApi: (d) => http.post('/dataservice/apis', d),
  updateApi: (id, d) => http.put(`/dataservice/apis/${id}`, d),
  publishApi: (id, status = 'PUBLISHED') => http.post(`/dataservice/apis/${id}/publish`, null, { params: { status } }),
  deleteApi: (id) => http.delete(`/dataservice/apis/${id}`),
  debugApi: (id, params) => http.post(`/dataservice/apis/${id}/debug`, params || {}),
  apiDoc: (id) => http.get(`/dataservice/apis/${id}/doc`),
  dsClients: () => http.get('/dataservice/clients'),
  createClient: (d) => http.post('/dataservice/clients', d),
  updateClient: (id, d) => http.put(`/dataservice/clients/${id}`, d),
  deleteClient: (id) => http.delete(`/dataservice/clients/${id}`),
  regenKey: (id) => http.post(`/dataservice/clients/${id}/regen-key`),
  dsLogs: (params) => http.get('/dataservice/logs', { params }),

  // 调度

  // ---------- 三期 v2（dam / hospital_demo schema） ----------
  // 数据目录
  domainTree: () => http.get('/v2/catalog/domain-tree'),
  categoryTree: () => http.get('/v2/catalog/category-tree'),
  categoryAssets: (id) => http.get(`/v2/catalog/categories/${id}/assets`),
  createDomain: (d) => http.post('/v2/catalog/domains', d),
  updateDomain: (id, d) => http.put(`/v2/catalog/domains/${id}`, d),
  removeDomain: (id) => http.delete(`/v2/catalog/domains/${id}`),
  createFact: (d) => http.post('/v2/catalog/facts', d),
  updateFact: (id, d) => http.put(`/v2/catalog/facts/${id}`, d),
  removeFact: (id) => http.delete(`/v2/catalog/facts/${id}`),
  createDimension: (d) => http.post('/v2/catalog/dimensions', d),
  updateDimension: (id, d) => http.put(`/v2/catalog/dimensions/${id}`, d),
  removeDimension: (id) => http.delete(`/v2/catalog/dimensions/${id}`),

  // 患者 360

  // 全域血缘
  lineageV2: (params) => http.get('/v2/lineage/graph', { params }),

  // SQL 血缘（Druid 确定性解析）
  sqlLineageParse: (d) => http.post('/v2/lineage/sql/parse', d),
  sqlLineageSave: (edges) => http.post('/v2/lineage/sql/save', { edges }),

  // AI 血缘推断 / AI 中文注释补全（LLM 调用慢，长超时）
  aiInferLineage: (d) => http.post('/v2/lineage/ai/infer', d, { timeout: 600000 }),
  aiEnrich: (id, d) => http.post(`/datasources/${id}/ai-enrich`, d || {}, { timeout: 600000 }),

  // 总览（dam 资产域指标）
  damOverview: () => http.get('/v2/overview'),

  // PowerJob 调度中心

  // ---------- HIS→中台 数据采集 ----------
}

export const LEVEL_META = {
  L1: { name: '公开', color: '#52C41A' },
  L2: { name: '内部', color: '#1677FF' },
  L3: { name: '敏感', color: '#FA8C16' },
  L4: { name: '核心', color: '#F5222D' }
}

export const TYPE_LABEL = {
  TABLE: '数据表',
  VIEW: '视图',
  API: '数据接口',
  REPORT: '报表',
  INDICATOR: '指标'
}

export const RULE_LABEL = {
  NOT_NULL: '非空校验',
  UNIQUE: '唯一性',
  RANGE: '值域范围',
  ENUM: '枚举值',
  REGEX: '正则匹配',
  FRESHNESS: '时效性',
  SQL: '自定义 SQL'
}

export function fmtTime(v) {
  if (!v) return '-'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return v
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function fmtNum(n) {
  if (n === null || n === undefined) return '-'
  if (typeof n !== 'number') return n
  if (n >= 100000000) return (n / 100000000).toFixed(2) + ' 亿'
  if (n >= 10000) return (n / 10000).toFixed(1) + ' 万'
  return n.toLocaleString()
}
