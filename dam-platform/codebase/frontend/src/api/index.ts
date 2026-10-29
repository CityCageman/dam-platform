import request from '@/utils/request'
import { streamPost, type SseCallbacks } from '@/utils/sse'

export const agentApi = {
  /** 流式对话（独立聊天窗口） */
  streamChat(messages: any[], callbacks: SseCallbacks, signal?: AbortSignal) {
    return streamPost('/api/agent/chat', { messages }, callbacks, signal)
  },
  /** 一次性完成（业务嵌入，如生成资产描述/工单摘要；agentType: chat=对话, code=编码） */
  complete(messages: any[], params?: { agentType?: string; model?: string; temperature?: number; maxTokens?: number }) {
    // 编码模型批量生成（如字段注释）耗时较长，单独放宽超时至 120s（匹配后端超时）
    return request.post('/agent/complete', { messages, ...params }, { timeout: 120000 })
  }
}

export const reportApi = {
  /** 根据自然语言需求生成报表 SQL（后端检索表结构 + 调用编码智能体，耗时较长） */
  generate(data: { requirement: string; dataSourceId: number }) {
    return request.post('/catalog/report/generate', data, { timeout: 150000 })
  },
  /** 执行报表 SQL（仅 SELECT，后端安全校验后只读执行） */
  execute(data: { dataSourceId: number; sql: string; limit?: number }) {
    return request.post('/catalog/report/execute', data, { timeout: 120000 })
  }
}

export const authApi = {
  login(data: { username: string; password: string }) {
    return request.post('/auth/login', data)
  },
  logout() {
    return request.post('/auth/logout')
  },
  getCurrentUser() {
    return request.get('/auth/me')
  }
}

export const catalogApi = {
  // 分类标准类型
  getCategoryTypes() { return request.get('/catalog/category-types') },
  // 分类
  getCategories(type?: string) { return request.get('/catalog/categories', { params: { type } }) },
  /** 主题域-类目树（按当前登录用户角色过滤可见主题域） */
  getCategoryDomains() { return request.get('/catalog/category-domains') },
  /** 全部主题域-类目（分类管理用） */
  getAllCategoryDomains() { return request.get('/catalog/category-domains/all') },
  /** 资产的多主题域归属 */
  getAssetCategories(assetId: number) { return request.get(`/catalog/assets/${assetId}/categories`) },
  /** 设置资产在某主题域下的类目（categoryId 传空=取消该域归属） */
  saveAssetCategory(assetId: number, data: any) { return request.put(`/catalog/assets/${assetId}/categories`, data) },
  /** 启用角色列表 */
  getRoles() { return request.get('/catalog/roles') },
  /** 角色可见主题域 */
  getRoleSubjectDomains(roleId: number) { return request.get(`/catalog/roles/${roleId}/subject-domains`) },
  /** 保存角色可见主题域（全量覆盖） */
  saveRoleSubjectDomains(roleId: number, subjectDomainIds: number[]) {
    return request.put(`/catalog/roles/${roleId}/subject-domains`, { subjectDomainIds })
  },
  createCategory(data: any) { return request.post('/catalog/categories', data) },
  updateCategory(data: any) { return request.put('/catalog/categories', data) },
  deleteCategory(id: number) { return request.delete(`/catalog/categories/${id}`) },
  // 扫描作业
  getScanJobs(dataSourceId?: number, status?: string) { return request.get('/catalog/scan-jobs', { params: { dataSourceId, status } }) },
  getScanJobDetail(id: number) { return request.get(`/catalog/scan-jobs/${id}`) },
  // 资产
  searchAssets(params: any) { return request.get('/catalog/assets/search', { params }) },
  getAssetDetail(id: number) { return request.get(`/catalog/assets/${id}`) },
  createAsset(data: any) { return request.post('/catalog/assets', data) },
  updateAsset(data: any) { return request.put('/catalog/assets', data) },
  deleteAsset(id: number) { return request.delete(`/catalog/assets/${id}`) },
  // 批量导入
  importCsv(file: File, categoryId?: number, sourceSystem?: string) {
    const fd = new FormData(); fd.append('file', file);
    if (categoryId) fd.append('categoryId', String(categoryId));
    if (sourceSystem) fd.append('sourceSystem', sourceSystem);
    return request.post('/catalog/assets/import/csv', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  importExcel(file: File, categoryId?: number, sourceSystem?: string) {
    const fd = new FormData(); fd.append('file', file);
    if (categoryId) fd.append('categoryId', String(categoryId));
    if (sourceSystem) fd.append('sourceSystem', sourceSystem);
    return request.post('/catalog/assets/import/excel', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  // 自动发现（扫描大数据库可能耗时较长，超时设为5分钟）
  scanDataSource(data: any) { return request.post('/catalog/discovery/scan', data, { timeout: 300000 }) },
  listSchemas(data: any) { return request.post('/catalog/discovery/schemas', data, { timeout: 60000 }) },
  batchRegister(data: any) { return request.post('/catalog/discovery/register', data, { timeout: 300000 }) },
  // 按需同步字段
  syncTableFields(data: any) { return request.post('/catalog/discovery/sync-fields', data, { timeout: 60000 }) },
  confirmSyncFields(data: any) { return request.post('/catalog/discovery/sync-fields/confirm', data) },
  // 字段
  getFields(assetId: number) { return request.get(`/catalog/assets/${assetId}/fields`) },
  updateField(data: any) { return request.put('/catalog/fields', data) },
  /** 字段中文注释字典（字段名→已有注释），供 AI 生成注释做关联联想 */
  getFieldCommentDict() { return request.get('/catalog/fields/comment-dict') },
  // 标签
  getTags() { return request.get('/catalog/tags') },
  createTag(data: any) { return request.post('/catalog/tags', data) },
  deleteTag(id: number) { return request.delete(`/catalog/tags/${id}`) },
  getAssetTags(assetId: number) { return request.get(`/catalog/assets/${assetId}/tags`) },
  assignTag(assetId: number, tagId: number) { return request.post(`/catalog/assets/${assetId}/tags`, { tagId }) },
  removeTag(assetId: number, tagId: number) { return request.delete(`/catalog/assets/${assetId}/tags/${tagId}`) },
  // 收藏
  addFavorite(assetId: number) { return request.post(`/catalog/favorites/${assetId}`) },
  removeFavorite(assetId: number) { return request.delete(`/catalog/favorites/${assetId}`) },
  getMyFavorites() { return request.get('/catalog/favorites') },
  // 评价
  rateAsset(data: any) { return request.post('/catalog/ratings', data) },
  getAssetRatings(assetId: number) { return request.get(`/catalog/assets/${assetId}/ratings`) },
  // 版本
  getVersions(assetId: number) { return request.get(`/catalog/assets/${assetId}/versions`) },
  compareVersions(assetId: number, versionA: number, versionB: number) {
    return request.get(`/catalog/assets/${assetId}/versions/compare`, { params: { versionA, versionB } })
  },
  // 元数据采集
  getCollections(sourceType?: string) { return request.get('/catalog/collections', { params: { sourceType } }) },
  createCollection(data: any) { return request.post('/catalog/collections', data) },
  getCollectionDetail(id: number) { return request.get(`/catalog/collections/${id}`) },
  compareCollections(idA: number, idB: number) { return request.get(`/catalog/collections/compare/${idA}/${idB}`) },
  // 来源系统
  getSourceSystems() { return request.get('/catalog/source-systems') },
  createSourceSystem(data: any) { return request.post('/catalog/source-systems', data) },
  updateSourceSystem(data: any) { return request.put('/catalog/source-systems', data) },
  deleteSourceSystem(id: number) { return request.delete(`/catalog/source-systems/${id}`) },
  // 数据源
  getDataSources() { return request.get('/catalog/data-sources') },
  getDataSourceDetail(id: number) { return request.get(`/catalog/data-sources/${id}`) },
  createDataSource(data: any) { return request.post('/catalog/data-sources', data) },
  updateDataSource(data: any) { return request.put('/catalog/data-sources', data) },
  deleteDataSource(id: number) { return request.delete(`/catalog/data-sources/${id}`) },
  testDataSourceConnection(data: any) { return request.post('/catalog/data-sources/test-connection', data) },
  // 数据源类型管理
  getDataSourceTypes() { return request.get('/catalog/data-source-types') },
  createDataSourceType(data: any) { return request.post('/catalog/data-source-types', data) },
  updateDataSourceType(data: any) { return request.put('/catalog/data-source-types', data) },
  deleteDataSourceType(id: number) { return request.delete(`/catalog/data-source-types/${id}`) },
  getDataSourceTypeSelect() { return request.get('/catalog/data-source-types/select') },
  // 安全等级
  recalcSecurity(assetId?: number) {
    return request.post('/catalog/assets/recalc-security', assetId ? { assetId } : {})
  },
  // 刷新记录数
  refreshCounts(assetId?: number) {
    return request.post('/catalog/assets/refresh-counts', assetId ? { assetId } : {})
  },
  /** AI 智能识别资产分类（按资产主键游标分批，前端循环调用） */
  aiClassify(payload: {
    dataSourceId?: number
    /** true=仅处理“未分类”资产 */
    pendingOnly?: boolean
    /** 上一批返回的 nextCursor */
    afterId?: number
    batchSize?: number
  }) {
    return request.post('/catalog/assets/ai-classify', payload, { timeout: 180000 })
  },
  getExactCount(assetId: number) {
    return request.post(`/catalog/assets/exact-count/${assetId}`)
  },
  // 基础配置
  getConfig(configType: string) { return request.get(`/catalog/config/${configType}`) },
  getConfigTree(configType: string) { return request.get(`/catalog/config/${configType}/tree`) },
  getNavigation() { return request.get('/catalog/navigation') },
  /** 数据库菜单树（dam_sys.sys_menu） */
  getMenus() { return request.get('/catalog/menus') },
  createConfig(data: any) { return request.post('/catalog/config', data) },
  updateConfig(data: any) { return request.put('/catalog/config', data) },
  deleteConfig(id: number) { return request.delete(`/catalog/config/${id}`) },
  // 通用字典（sys_dict_type / sys_dict_data）
  getDictData(typeCode: string) { return request.get(`/catalog/dict/${typeCode}`) },
  getAllDictData(typeCode: string) { return request.get(`/catalog/dict/${typeCode}/all`) },
  getDictDataPage(typeCode: string, page: number, pageSize: number) { return request.get(`/catalog/dict/${typeCode}/page`, { params: { page, pageSize } }) },
  getDictTypes() { return request.get('/catalog/dict-types') },
  getDictTypesPage(keyword: string, page: number, pageSize: number) { return request.get('/catalog/dict-types/page', { params: { keyword: keyword || undefined, page, pageSize } }) },
  createDictType(data: any) { return request.post('/catalog/dict/type', data) },
  updateDictType(data: any) { return request.put('/catalog/dict/type', data) },
  deleteDictType(id: number) { return request.delete(`/catalog/dict/type/${id}`) },
  createDictData(data: any) { return request.post('/catalog/dict/data', data) },
  updateDictData(data: any) { return request.put('/catalog/dict/data', data) },
  deleteDictData(id: number) { return request.delete(`/catalog/dict/data/${id}`) }
}

// ==================== 配置中心 ====================
// 基于 dam_config 表管理所有配置（树形结构），支持导航菜单、安全等级、资产类型等

const CONFIG_TYPES = [
  { type: 'navigation', name: '导航菜单', icon: 'Menu' },
  { type: 'security_level', name: '安全等级', icon: 'Lock' },
  { type: 'asset_type', name: '资产类型', icon: 'Collection' },
  { type: 'asset_status', name: '资产状态', icon: 'List' },
  { type: 'source_type', name: '数据源类型', icon: 'Connection' },
  { type: 'sensitive_level', name: '敏感级别', icon: 'Warning' },
  { type: 'tech_asset_type', name: '技术元数据类型', icon: 'Cpu' },
  // 字典管理：统一维护 sys_dict_type / sys_dict_data 两张通用字典表
  { type: '__dict__', name: '字典管理', icon: 'Tickets' }
]

function flatToTree(list: any[]): any[] {
  const map = new Map<number, any>()
  list.forEach((item: any) => map.set(item.id, { ...item, children: [] }))
  const tree: any[] = []
  list.forEach((item: any) => {
    const mapped = map.get(item.id)
    if (item.parentId && map.has(item.parentId)) {
      map.get(item.parentId)!.children.push(mapped)
    } else if (!item.parentId) {
      tree.push(mapped)
    }
  })
  return tree
}

export const configApi = {
  getTypes() { return CONFIG_TYPES },
  /** 获取某类型的配置树 */
  async getTree(configType: string) {
    const res: any = await catalogApi.getConfigTree(configType)
    return { code: 200, data: flatToTree(res.data || []), message: 'success' }
  },
  async getList(configType: string) {
    const res: any = await catalogApi.getConfig(configType)
    return { code: 200, data: res.data || [], message: 'success' }
  },
  async create(data: any) { const res: any = await catalogApi.createConfig(data); return { code: 200, data: res.data, message: 'success' } },
  async update(data: any) { const res: any = await catalogApi.updateConfig(data); return { code: 200, data: res.data, message: 'success' } },
  async delete(id: number) { await catalogApi.deleteConfig(id); return { code: 200, message: 'success' } },
  /** 配置总览统计 */
  async getStatistics() {
    let total = 0; let enabled = 0; const byType: Record<string, number> = {}
    for (const t of CONFIG_TYPES) {
      try {
        const res: any = await catalogApi.getConfig(t.type)
        const items = res.data || []; total += items.length
        enabled += items.filter((c: any) => c.status !== 0).length
        byType[t.type] = items.length
      } catch (_e) { byType[t.type] = 0 }
    }
    return { code: 200, data: { total, enabled, disabled: total - enabled, byType, recent: [] }, message: 'success' }
  }
}

export const lineageApi = {
  getLineage(assetId: number) { return request.get(`/lineage/${assetId}`) },
  getLineagePage(nodeId: number, page: number, pageSize: number) { return request.get(`/lineage/page/${nodeId}`, { params: { page, pageSize } }) },
  getLineageGraph(nodeId: number | string, depth = 1, includeFields = false) { return request.get(`/lineage/graph/${nodeId}`, { params: { depth, includeFields } }) },
  getNodeFieldsPage(sourceId: number, page: number, pageSize: number) { return request.get('/lineage/fields/page', { params: { sourceId, page, pageSize } }) },
  createLineage(data: any) { return request.post('/lineage', data) },
  deleteLineage(id: number) { return request.delete(`/lineage/${id}`) },
  parseSql(data: any) { return request.post('/lineage/parse-sql', data) },
  impactAnalysis(assetId: number) { return request.get(`/lineage/impact/${assetId}`) },
  // 血缘节点管理
  listSources(params: any) { return request.get('/lineage/sources', { params }) },
  createSource(data: any) { return request.post('/lineage/sources', data) },
  updateSource(data: any) { return request.put('/lineage/sources', data) },
  deleteSource(id: number) { return request.delete(`/lineage/sources/${id}`) },
  // 报表SQL入库解析（微观精细血缘）
  parseReportSql(data: any) { return request.post('/lineage/report-sql', data) },
  listReportParsers(params: any) { return request.get('/lineage/report-parsers', { params }) },
  // 精细血缘钻取
  getHierarchy(sourceId: number) { return request.get('/lineage/hierarchy', { params: { sourceId } }) },
  getColumnLineage(hierarchyId: number) { return request.get('/lineage/column-lineage', { params: { hierarchyId } }) },
  getNodeFields(sourceId: number) { return request.get('/lineage/fields', { params: { sourceId } }) },
  getCalcDependency(columnLineageId: number) { return request.get('/lineage/calc-dependency', { params: { columnLineageId } }) },
  // 字段级血缘维护
  createColumnLineage(data: any) { return request.post('/lineage/column-lineage', data) },
  saveFkLineage(data: any) { return request.post('/lineage/fk-lineage', data) },
  /** AI 识别 SQL 的数据血缘（表级+字段级分开，调用 catalog 服务，耗时较长） */
  aiRecognize(data: { sql: string; dataSourceId?: number }) {
    return request.post('/catalog/lineage/ai-recognize', data, { timeout: 150000 })
  },
  /** AI 识别数据源内各表之间的关联关系（无需 SQL） */
  aiRecognizeTables(data: { dataSourceId: number }) {
    return request.post('/catalog/lineage/ai-recognize-tables', data, { timeout: 150000 })
  },
  /** 批量落库 AI 识别的血缘（表级 + 字段级） */
  saveAiLineage(data: { tableLineages: any[]; columnLineages: any[]; sourceSystem?: string; collectionId?: number }) {
    return request.post('/lineage/ai-lineage', data, { timeout: 120000 })
  }
}

export const standardApi = {
  searchStandards(params: any) { return request.get('/standard/search', { params }) },
  getStandardDetail(id: number) { return request.get(`/standard/${id}`) },
  createStandard(data: any) { return request.post('/standard', data) },
  updateStandard(id: number, data: any) { return request.put(`/standard/${id}`, data) },
  deleteStandard(id: number) { return request.delete(`/standard/${id}`) },
  getStandardValues(stdId: number) { return request.get(`/standard/${stdId}/values`) },
  createStandardValue(data: any) { return request.post('/standard/values', data) },
  publishStandard(id: number) { return request.post(`/standard/${id}/publish`) },
  approveStandard(id: number, approved: boolean) { return request.post(`/standard/${id}/approve`, { approved }) },
  archiveStandard(id: number) { return request.post(`/standard/${id}/archive`) },
  // 映射
  getMappings(stdId: number) { return request.get(`/standard/${stdId}/mappings`) },
  createMapping(data: any) { return request.post('/standard/mappings', data) },
  removeMapping(id: number) { return request.delete(`/standard/mappings/${id}`) },
  getCoverage() { return request.get('/standard/coverage') },
  // 自动校验
  autoValidate() { return request.post('/standard/auto-validate') },
  getDeviations(params: any) { return request.get('/standard/deviations', { params }) },
  resolveDeviation(id: number) { return request.put(`/standard/deviations/${id}/resolve`) }
}

export const qualityApi = {
  searchRules(params: any) { return request.get('/quality/rules/search', { params }) },
  createRule(data: any) { return request.post('/quality/rules', data) },
  updateRule(data: any) { return request.put('/quality/rules', data) },
  deleteRule(id: number) { return request.delete(`/quality/rules/${id}`) },
  getCheckHistory(ruleId: number) { return request.get(`/quality/checks/${ruleId}`) },
  executeRule(ruleId: number) { return request.post(`/quality/rules/${ruleId}/execute`) },
  executeAll() { return request.post('/quality/rules/execute-all') },
  getTrend(ruleId?: number, days?: number) { return request.get('/quality/trend', { params: { ruleId, days } }) },
  getOverview() { return request.get('/quality/overview') }
}

export const securityApi = {
  getSensitiveRules() { return request.get('/security/sensitive-rules') },
  createSensitiveRule(data: any) { return request.post('/security/sensitive-rules', data) },
  getMaskingPolicies() { return request.get('/security/masking-policies') },
  createMaskingPolicy(data: any) { return request.post('/security/masking-policies', data) },
  searchAuditLogs(params: any) { return request.get('/security/audit-logs', { params }) },
  autoClassify() { return request.post('/security/classify') },
  scanSensitiveData(data: any) { return request.post('/security/scan-data', data) },
  // ABAC权限
  getPermissions() { return request.get('/security/permissions') },
  createPermission(data: any) { return request.post('/security/permissions', data) },
  updatePermission(data: any) { return request.put('/security/permissions', data) },
  deletePermission(id: number) { return request.delete(`/security/permissions/${id}`) },
  checkPermission(params: any) { return request.get('/security/check-permission', { params }) },
  // 数据使用申请
  searchAccessRequests(params: any) { return request.get('/security/access-requests', { params }) },
  createAccessRequest(data: any) { return request.post('/security/access-requests', data) },
  approveRequest(id: number, params: any) { return request.post(`/security/access-requests/${id}/approve`, null, { params }) },
  getPendingRequests() { return request.get('/security/pending-requests') },
  // 脱敏预览
  previewMasking(data: any) { return request.post('/security/masking/preview', data) },
  getMaskingAlgorithms() { return request.get('/security/masking/algorithms') }
}

export const lifecycleApi = {
  getPolicies() { return request.get('/lifecycle/policies') },
  createPolicy(data: any) { return request.post('/lifecycle/policies', data) },
  updatePolicy(data: any) { return request.put('/lifecycle/policies', data) },
  getDestructionRecords() { return request.get('/lifecycle/destructions') },
  createDestruction(data: any) { return request.post('/lifecycle/destructions', data) },
  approveDestruction(id: number, data: any) { return request.put(`/lifecycle/destructions/${id}/approve`, data) },
  // 存储分层
  executeTiering() { return request.post('/lifecycle/tiering/execute') },
  getTierDistribution() { return request.get('/lifecycle/tiering/distribution') }
}

export const operationsApi = {
  getDashboard() { return request.get('/operations/dashboard') },
  searchTickets(params: any) { return request.get('/operations/tickets', { params }) },
  createTicket(data: any) { return request.post('/operations/tickets', data) },
  updateTicketStatus(id: number, data: any) { return request.put(`/operations/tickets/${id}/status`, data) },
  assignTicket(id: number, data: any) { return request.put(`/operations/tickets/${id}/assign`, data) },
  // 评论
  getTicketComments(ticketId: number) { return request.get(`/operations/tickets/${ticketId}/comments`) },
  addComment(ticketId: number, data: any) { return request.post(`/operations/tickets/${ticketId}/comments`, data) },
  // SLA
  getSlaStatuses() { return request.get('/operations/tickets/sla') },
  getTicketSla(id: number) { return request.get(`/operations/tickets/${id}/sla`) },
  // 资产价值评估
  evaluateAll() { return request.post('/operations/valuation/evaluate-all') },
  getValueDistribution() { return request.get('/operations/valuation/distribution') },
  evaluateAsset(assetId: number) { return request.get(`/operations/valuation/${assetId}`) },
  // 用户行为
  getUserBehavior() { return request.get('/operations/user-behavior') }
}

export const modelApi = {
  // 模型
  getModels(keyword?: string, categoryId?: number) { return request.get('/catalog/models', { params: { keyword, categoryId } }) },
  getModelDetail(id: number) { return request.get(`/catalog/models/${id}`) },
  createModel(data: any) { return request.post('/catalog/models', data) },
  updateModel(data: any) { return request.put('/catalog/models', data) },
  deleteModel(id: number) { return request.delete(`/catalog/models/${id}`) },
  publishModel(id: number) { return request.post(`/catalog/models/${id}/publish`) },
  archiveModel(id: number) { return request.post(`/catalog/models/${id}/archive`) },
  // 模型字段
  getModelFields(modelId: number) { return request.get(`/catalog/models/${modelId}/fields`) },
  createModelField(modelId: number, data: any) { return request.post(`/catalog/models/${modelId}/fields`, data) },
  updateModelField(data: any) { return request.put(`/catalog/models/${data.modelId}/fields`, data) },
  deleteModelField(modelId: number, fieldId: number) { return request.delete(`/catalog/models/${modelId}/fields/${fieldId}`) },
  // 模型对比
  getComparableAssets() { return request.get('/catalog/models/compare/assets') },
  compareModelWithAsset(modelId: number, assetId: number) { return request.post('/catalog/models/compare', { modelId, assetId }) }
}

/** 报表体系（报表即资产） */
export const reportCatalogApi = {
  /** 维度树（按角色可见主题域过滤） */
  getDimensions() { return request.get('/catalog/reports/dimensions') },
  /** 卡片分页 */
  getCards(params: { dimensionCode?: string; dimensionValue?: string; keyword?: string; page?: number; pageSize?: number }) {
    return request.get('/catalog/reports/cards', { params })
  },
  getDetail(id: number) { return request.get(`/catalog/reports/${id}`) },
  create(data: any) { return request.post('/catalog/reports', data) },
  update(id: number, data: any) { return request.put(`/catalog/reports/${id}`, data) },
  remove(id: number) { return request.delete(`/catalog/reports/${id}`) },
  /** 在线执行（SQL/存储过程），超时放宽 */
  run(id: number, params: Record<string, any>) {
    return request.post(`/catalog/reports/${id}/run`, { params }, { timeout: 120000 })
  }
}

export const metadataApi = {
  getTechnicalMetadata(assetId: number) { return request.get(`/catalog/metadata/technical/${assetId}`) },
  getBusinessMetadata(assetId: number) { return request.get(`/catalog/metadata/business/${assetId}`) },
  getRelatedMetadata(assetId: number) { return request.get(`/catalog/metadata/related/${assetId}`) },
  saveExtProperty(data: any) { return request.post('/catalog/metadata/ext', data) },
  saveExtProperties(assetId: number, category: string, properties: any[]) {
    return request.post('/catalog/metadata/ext/batch', { assetId, category, properties })
  },
  deleteExtProperty(id: number) { return request.delete(`/catalog/metadata/ext/${id}`) },
  // 技术元数据浏览（dam_metadata 模块）
  getMetadataDataSources() { return request.get('/catalog/metadata/data-sources') },
  listTables(params: any) { return request.get('/catalog/metadata/tables', { params }) },
  listSchemas(dataSourceId?: number) { return request.get('/catalog/metadata/schemas', { params: { dataSourceId } }) },
  getTableDetail(tableId: number) { return request.get(`/catalog/metadata/tables/${tableId}`) },
  saveManagementMetadata(data: any) { return request.post('/catalog/metadata/management', data) },
  /** 元数据层级概况（源库/ODS/DWD/DWS/ADS/DIM） */
  getLayerOverview() { return request.get('/catalog/metadata/layer-overview') },
  /** 数据源 Schema 统计（Schema 数 + 每个 Schema 表数） */
  getDataSourceSchemaStats() { return request.get('/catalog/metadata/data-source-schema-stats') },
  /** AI 自动标注（表注释/主题域/安全分级），按主键游标分批，耗时较长 */
  aiAnnotate(payload: {
    dataSourceId?: number
    /** true=仅补标注缺注释/缺主题域/缺安全分级的表 */
    pendingOnly?: boolean
    /** 上一批返回的 nextCursor */
    afterId?: number
    batchSize?: number
  }) {
    return request.post('/catalog/metadata/ai-annotate', payload, { timeout: 180000 })
  }
}
