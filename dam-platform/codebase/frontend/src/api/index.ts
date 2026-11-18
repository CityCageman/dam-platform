import request from '@/utils/request'
import { streamPost, type SseCallbacks } from '@/utils/sse'

export const agentApi = {
  /** 流式对话（独立聊天窗口） */
  streamChat(messages: any[], callbacks: SseCallbacks, signal?: AbortSignal) {
    return streamPost('/api/model-gateway/chat', { messages }, callbacks, signal)
  },
  /** 一次性完成（业务嵌入，如生成资产描述/工单摘要；agentType: chat=对话, code=编码） */
  complete(messages: any[], params?: { agentType?: string; model?: string; temperature?: number; maxTokens?: number }) {
    // 编码模型批量生成（如字段注释）耗时较长，单独放宽超时至 120s（匹配后端超时）
    return request.post('/model-gateway/complete', { messages, ...params }, { timeout: 120000 })
  }
}

export const reportApi = {
  /** 根据自然语言需求生成报表 SQL（后端检索表结构 + 调用编码智能体，耗时较长） */
  generate(data: { requirement: string; dataSourceId: number }) {
    return request.post('/ai-workbench/reports/generate', data, { timeout: 300000 })
  },
  /** 异步生成报表：创建 REPORT 任务，后台生成并登记到报表体系 */
  generateAsync(data: {
    requirement: string
    dataSourceId: number
    mode?: 'NEW' | 'MODIFY'
    reportId?: number
    baseSql?: string
    history?: string
    displayRequirement?: string
  }) {
    return request.post('/ai-workbench/reports/generate/async', data, { timeout: 30000 })
  },
  /** 执行报表 SQL（仅 SELECT，后端安全校验后只读执行） */
  execute(data: { dataSourceId: number; sql: string; limit?: number; params?: Record<string, any> }) {
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
  // ===== AI 任务中心 =====
  listAiTasks(params: any) { return request.get('/ai-workbench/ai/tasks', { params }) },
  getAiTaskStats() { return request.get('/ai-workbench/ai/tasks/stats') },
  getAiTask(id: number) { return request.get(`/ai-workbench/ai/tasks/${id}`) },
  createAiTask(data: any) { return request.post('/ai-workbench/ai/tasks', data) },
  deleteAiTask(id: number) { return request.delete(`/ai-workbench/ai/tasks/${id}`) },
  cancelAiTask(id: number) { return request.post(`/ai-workbench/ai/tasks/${id}/cancel`) },
  updateAiTaskItem(taskId: number, itemId: number, draftContent: string) { return request.put(`/ai-workbench/ai/tasks/${taskId}/items/${itemId}`, { draftContent }) },
  updateAiTaskStatus(id: number, data: any) { return request.post(`/ai-workbench/ai/tasks/${id}/status`, data) },
  getAiTaskItems(id: number, params: any) { return request.get(`/ai-workbench/ai/tasks/${id}/items`, { params }) },
  reviewAiTaskItems(id: number, data: any) { return request.post(`/ai-workbench/ai/tasks/${id}/items/batch-review`, data) },
  reviewAiTaskItem(taskId: number, itemId: number, accept: boolean, reviewer?: string) {
    return request.post(`/ai-workbench/ai/tasks/${taskId}/items/${itemId}/review`, null, { params: { accept, reviewer } })
  },
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
    /** true=只生成草稿，不直接写资产 */
    draft?: boolean
    /** 草稿所属任务ID */
    taskId?: number
  }) {
    return request.post('/catalog/assets/ai-classify', payload, { timeout: 180000 })
  },
  /** 资产分类（异步）：创建任务后立即返回，前端轮询任务状态 */
  aiClassifyAsync(payload: { dataSourceId?: number; pendingOnly?: boolean; taskId: number }) {
    return request.post('/catalog/assets/ai-classify/async', payload, { timeout: 30000 })
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
// 基于 dam_config 表管理配置（树形结构）。UI 仅保留 navigation（菜单维护）入口；
// 安全等级、资产类型、资产状态、数据源类型、敏感级别、技术元数据类型统一由字典维护。

/**
 * 旧配置中心类型入口。
 * 安全等级、资产类型/状态、数据源类型、敏感级别、技术元数据类型均已收敛为字典，
 * 由「字典维护」统一入口维护；此处仅保留导航菜单（菜单维护）与字典维护。
 */
const CONFIG_TYPES = [
  { type: 'navigation', name: '菜单维护', icon: 'Menu' },
  { type: '__dict__', name: '字典维护', icon: 'Tickets' }
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
  /** 配置总览统计（字典维护不是 dam_config 类型，跳过避免无效请求） */
  async getStatistics() {
    let total = 0; let enabled = 0; const byType: Record<string, number> = {}
    for (const t of CONFIG_TYPES) {
      if (t.type === '__dict__') continue
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
  getLineageGraph(nodeId: number | string, depth = 1, includeFields = false, layers?: string, crossOnly?: boolean, direction?: string) { return request.get(`/lineage/graph/${nodeId}`, { params: { depth, includeFields, layers, crossOnly, direction } }) },
  /** 按中文描述检索血缘（字段注释/表注释/字段名/表名） */
  searchLineage(keyword: string, limit = 20) { return request.get('/lineage/search', { params: { keyword, limit } }) },
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
  updateStandardValue(id: number, data: any) { return request.put(`/standard/values/${id}`, data) },
  deleteStandardValue(id: number) { return request.delete(`/standard/values/${id}`) },
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
  getPolicyCandidates(id: number) { return request.get(`/lifecycle/policies/${id}/candidates`) },
  getExecutions(params: any) { return request.get('/lifecycle/executions', { params }) },
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

export const knowledgeApi = {
  categories() { return request.get('/knowledge/categories') },
  stats() { return request.get('/knowledge/stats') },
  createCategory(data: any) { return request.post('/knowledge/categories', data) },
  updateCategory(id: number, data: any) { return request.put(`/knowledge/categories/${id}`, data) },
  deleteCategory(id: number) { return request.delete(`/knowledge/categories/${id}`) },
  entries(params: any) { return request.get('/knowledge/entries', { params }) },
  createEntry(data: any) { return request.post('/knowledge/entries', data) },
  updateEntry(id: number, data: any) { return request.put(`/knowledge/entries/${id}`, data) },
  deleteEntry(id: number) { return request.delete(`/knowledge/entries/${id}`) },
  sources(params: any) { return request.get('/knowledge/sources', { params }) },
  sync(data: any) { return request.post('/knowledge/sync', data) },
  reindex(id: number) { return request.post(`/knowledge/entries/${id}/vector-reindex`) },
  externalStatus() { return request.get('/knowledge/external/status') },
  initializeExternal() { return request.post('/knowledge/external/initialize', null, { timeout: 60000 }) },
  refreshExternal(data?: any) { return request.post('/knowledge/external/refresh', data || {}, { timeout: 120000 }) },
  externalEntryStatus(id: number) { return request.get(`/knowledge/external/entries/${id}/status`) },
  migrateExternal() { return request.post('/knowledge/external/migrate', null, { timeout: 600000 }) },
  externalSearch(data: any) { return request.post('/knowledge/external/search', data, { timeout: 60000 }) }
}

export const metricApi = {
  list(params: any) { return request.get('/metrics', { params }) },
  get(id: number) { return request.get(`/metrics/${id}`) },
  create(data: any) { return request.post('/metrics', data) },
  update(id: number, data: any) { return request.put(`/metrics/${id}`, data) },
  remove(id: number) { return request.delete(`/metrics/${id}`) },
  aiSql(id: number) { return request.post(`/metrics/${id}/ai-sql`, null, { timeout: 300000 }) },
  values(id: number) { return request.get(`/metrics/${id}/values`) },
  addValue(id: number, data: any) { return request.post(`/metrics/${id}/values`, data) },
  removeValue(id: number, valueId: number) { return request.delete(`/metrics/${id}/values/${valueId}`) }
}

// ==================== 数据集成 / 调度 / 数据服务 ====================
/** 统一分页响应（dam-common PageResult） */
export interface PageResult<T> {
  page: number
  pageSize: number
  total: number
  totalPages: number
  records: T[]
}

/** 统一响应包装（dam-common Result） */
export interface ApiResult<T> {
  code: number
  message?: string
  data: T
}

/** 列表通用查询参数 */
export interface PageQuery {
  keyword?: string
  status?: string
  page?: number
  pageSize?: number
}

// ==================== 系统配置：用户 / 角色 / 授权 / 菜单 ====================

/** 系统用户（dam_sys.sys_user，敏感字段由后端裁剪） */
export interface SystemUser {
  id: number
  username: string
  realName?: string
  email?: string
  phone?: string
  department?: string
  /** 0-停用，1-启用（兼容后端字符串状态） */
  status?: number | string
  lastLoginAt?: string
  createdAt?: string
  updatedAt?: string
  [key: string]: unknown
}

/** 新增/编辑用户入参：password 仅新增时使用，重置密码走独立接口 */
export interface SystemUserPayload {
  username: string
  password?: string
  realName?: string
  email?: string
  phone?: string
  department?: string
  status?: number
}

/** 系统角色（dam_sys.sys_role） */
export interface SystemRole {
  id: number
  roleCode?: string
  roleName?: string
  description?: string
  status?: number | string
  createdAt?: string
  updatedAt?: string
  [key: string]: unknown
}

/** 新增/编辑角色入参 */
export interface SystemRolePayload {
  roleCode: string
  roleName: string
  description?: string
  status?: number
}

/** 系统菜单（/auth/menus；兼容树形与平铺两种返回结构） */
export interface SystemMenu {
  id: number
  menuName?: string
  name?: string
  parentId?: number | null
  children?: SystemMenu[]
  [key: string]: unknown
}

/** 用户角色授权返回值：兼容 ID 数组、角色数组与包装对象 */
export type RoleAssignmentPayload =
  | number[]
  | SystemRole[]
  | { roleIds?: number[]; roles?: SystemRole[] }

/** 角色菜单授权返回值：兼容 ID 数组、菜单数组与包装对象 */
export type MenuAssignmentPayload =
  | number[]
  | SystemMenu[]
  | { menuIds?: number[]; menus?: SystemMenu[] }

/**
 * 系统配置接口：用户 / 角色 / 授权 / 菜单。
 * baseURL 已为 /api，因此实际请求路径为 /api/auth/...
 */
export const systemApi = {
  users: {
    /** 分页查询用户 */
    list(params: PageQuery = {}) {
      return request.get<unknown, ApiResult<PageResult<SystemUser> | SystemUser[]>>('/auth/users', { params })
    },
    create(data: SystemUserPayload) {
      return request.post<unknown, ApiResult<SystemUser>>('/auth/users', data)
    },
    update(id: number, data: SystemUserPayload) {
      return request.put<unknown, ApiResult<SystemUser>>(`/auth/users/${id}`, data)
    },
    remove(id: number) {
      return request.delete<unknown, ApiResult<void>>(`/auth/users/${id}`)
    },
    /** 重置密码：password 由调用方按 auth 服务公钥加密后传入 */
    resetPassword(id: number, data: { password: string }) {
      return request.post<unknown, ApiResult<void>>(`/auth/users/${id}/reset-password`, data)
    }
  },
  roles: {
    /** 分页查询角色 */
    list(params: PageQuery = {}) {
      return request.get<unknown, ApiResult<PageResult<SystemRole> | SystemRole[]>>('/auth/roles', { params })
    },
    create(data: SystemRolePayload) {
      return request.post<unknown, ApiResult<SystemRole>>('/auth/roles', data)
    },
    update(id: number, data: SystemRolePayload) {
      return request.put<unknown, ApiResult<SystemRole>>(`/auth/roles/${id}`, data)
    },
    remove(id: number) {
      return request.delete<unknown, ApiResult<void>>(`/auth/roles/${id}`)
    }
  },
  authorizations: {
    /** 查询用户已分配角色 */
    getUserRoles(userId: number) {
      return request.get<unknown, ApiResult<RoleAssignmentPayload>>(`/auth/users/${userId}/roles`)
    },
    /** 全量保存用户角色 */
    saveUserRoles(userId: number, roleIds: number[]) {
      return request.put<unknown, ApiResult<void>>(`/auth/users/${userId}/roles`, { roleIds })
    },
    /** 查询角色已分配菜单 */
    getRoleMenus(roleId: number) {
      return request.get<unknown, ApiResult<MenuAssignmentPayload>>(`/auth/roles/${roleId}/menus`)
    },
    /** 全量保存角色菜单 */
    saveRoleMenus(roleId: number, menuIds: number[]) {
      return request.put<unknown, ApiResult<void>>(`/auth/roles/${roleId}/menus`, { menuIds })
    }
  },
  menus: {
    /** 菜单树（角色授权时选择菜单） */
    list() {
      return request.get<unknown, ApiResult<SystemMenu[]>>('/auth/menus')
    }
  }
}

/** 数据集成任务（dam_integration.integration_job） */
export interface IntegrationJob {
  id: number
  jobCode: string
  jobName: string
  sourceType?: string
  targetType?: string
  syncMode?: string
  status: string
  lastRunAt?: string
  lastError?: string
  createdAt?: string
  createdBy?: string
  updatedAt?: string
}

/** 调度任务（dam_scheduler.schedule_job） */
export interface SchedulerJob {
  id: number
  jobCode: string
  jobName: string
  cronExpression?: string
  dagCode?: string
  status: string
  description?: string
  createdAt?: string
  createdBy?: string
  updatedAt?: string
}

/** 数据 API 定义（dam_data_service.data_api） */
export interface DataApiDefinition {
  id: number
  apiCode: string
  apiName: string
  path: string
  method: string
  status: string
  description?: string
  upstreamType?: string
  createdAt?: string
  createdBy?: string
  updatedAt?: string
}

export const integrationApi = {
  /** 分页查询集成任务 */
  listJobs(params: PageQuery = {}) {
    return request.get<unknown, ApiResult<PageResult<IntegrationJob>>>('/integration/jobs', { params })
  },
  getJob(id: number) {
    return request.get<unknown, ApiResult<IntegrationJob>>(`/integration/jobs/${id}`)
  },
  /** 启用/停用集成任务 */
  changeJobStatus(id: number, status: string) {
    return request.post<unknown, ApiResult<IntegrationJob>>(`/integration/jobs/${id}/status`, { status })
  },
  /** 触发集成任务（后端当前仅标记 RUNNING） */
  triggerJob(id: number) {
    return request.post<unknown, ApiResult<Record<string, unknown>>>(`/integration/jobs/${id}/trigger`)
  }
}

export const schedulerApi = {
  /** 分页查询调度任务 */
  listJobs(params: PageQuery = {}) {
    return request.get<unknown, ApiResult<PageResult<SchedulerJob>>>('/scheduler/jobs', { params })
  },
  getJob(id: number) {
    return request.get<unknown, ApiResult<SchedulerJob>>(`/scheduler/jobs/${id}`)
  },
  /** 启用/停用调度任务 */
  changeJobStatus(id: number, status: string) {
    return request.post<unknown, ApiResult<SchedulerJob>>(`/scheduler/jobs/${id}/status`, { status })
  },
  /** 触发一次调度任务（后端当前仅落 PENDING 运行记录） */
  triggerJob(id: number) {
    return request.post<unknown, ApiResult<Record<string, unknown>>>(`/scheduler/jobs/${id}/trigger`)
  }
}

export const dataServiceApi = {
  /** 分页查询数据服务 API */
  listApis(params: PageQuery = {}) {
    return request.get<unknown, ApiResult<PageResult<DataApiDefinition>>>('/data-service/apis', { params })
  },
  getApi(id: number) {
    return request.get<unknown, ApiResult<DataApiDefinition>>(`/data-service/apis/${id}`)
  },
  publishApi(id: number) {
    return request.post<unknown, ApiResult<DataApiDefinition>>(`/data-service/apis/${id}/publish`)
  },
  offlineApi(id: number) {
    return request.post<unknown, ApiResult<DataApiDefinition>>(`/data-service/apis/${id}/offline`)
  }
}

// ==================== 任务中心 / 数据开发 ====================

/** 任务中心-任务定义 */
export interface TaskCenterDefinition {
  id: number
  code?: string
  definitionCode?: string
  taskCode?: string
  name?: string
  definitionName?: string
  taskName?: string
  type?: string
  taskType?: string
  status?: string
  description?: string
  cronExpression?: string
  datasourceId?: number
  createdAt?: string
  updatedAt?: string
  [key: string]: unknown
}

/** 任务中心-运行实例 */
export interface TaskCenterInstance {
  id: number
  definitionId?: number
  definitionCode?: string
  definitionName?: string
  taskCode?: string
  taskName?: string
  status?: string
  triggerType?: string
  startTime?: string
  endTime?: string
  durationMs?: number
  errorMessage?: string
  createdAt?: string
  updatedAt?: string
  [key: string]: unknown
}

/** 任务中心-实例日志行 */
export interface TaskCenterLog {
  id?: number
  instanceId?: number
  level?: string
  message?: string
  content?: string
  logContent?: string
  logMessage?: string
  timestamp?: string
  createdAt?: string
  [key: string]: unknown
}

/** 日志接口兼容纯文本、数组与分页包装三种返回 */
export type TaskCenterLogPayload =
  | string
  | TaskCenterLog[]
  | {
      records?: TaskCenterLog[]
      rows?: TaskCenterLog[]
      list?: TaskCenterLog[]
      logs?: TaskCenterLog[]
      content?: string
      [key: string]: unknown
    }

/** 任务中心接口：任务定义 / 触发 / 实例 / 日志 */
export const taskCenterApi = {
  /** 任务定义列表 */
  getDefinitions() {
    return request.get<unknown, ApiResult<TaskCenterDefinition[] | PageResult<TaskCenterDefinition>>>('/task-center/definitions')
  },
  /** 立即触发一次任务定义 */
  triggerDefinition(id: number) {
    return request.post<unknown, ApiResult<TaskCenterInstance | Record<string, unknown>>>(`/task-center/definitions/${id}/trigger`)
  },
  /** 运行实例列表 */
  getInstances() {
    return request.get<unknown, ApiResult<TaskCenterInstance[] | PageResult<TaskCenterInstance>>>('/task-center/instances')
  },
  /** 实例日志 */
  getInstanceLogs(id: number) {
    return request.get<unknown, ApiResult<TaskCenterLogPayload>>(`/task-center/instances/${id}/logs`)
  }
}

/** 数据开发任务 */
export interface DevTask {
  id: number
  code?: string
  name?: string
  type?: string
  content?: string
  datasourceId?: number | null
  status?: string
  version?: number
  currentVersion?: number
  latestVersion?: number
  createdAt?: string
  updatedAt?: string
  createdBy?: string
  [key: string]: unknown
}

/** 数据开发任务新建/编辑入参 */
export interface DevTaskPayload {
  code: string
  name: string
  type: string
  content: string
  datasourceId?: number | null
}

/** 数据开发任务版本 */
export interface DevTaskVersion {
  id?: number
  taskId?: number
  version?: number
  versionNo?: number
  versionNumber?: number
  content?: string
  changeLog?: string
  remark?: string
  createdBy?: string
  createdAt?: string
  [key: string]: unknown
}

/** 数据开发接口：任务 CRUD / 发布下线 / 版本 */
export const devApi = {
  /** 任务列表 */
  getTasks() {
    return request.get<unknown, ApiResult<DevTask[] | PageResult<DevTask>>>('/dev/tasks')
  },
  /** 新建任务 */
  createTask(data: DevTaskPayload) {
    return request.post<unknown, ApiResult<DevTask>>('/dev/tasks', data)
  },
  /** 编辑任务 */
  updateTask(id: number, data: DevTaskPayload) {
    return request.put<unknown, ApiResult<DevTask>>(`/dev/tasks/${id}`, data)
  },
  /** 发布任务 */
  publishTask(id: number) {
    return request.post<unknown, ApiResult<DevTask>>(`/dev/tasks/${id}/publish`)
  },
  /** 下线任务 */
  offlineTask(id: number) {
    return request.post<unknown, ApiResult<DevTask>>(`/dev/tasks/${id}/offline`)
  },
  /** 任务历史版本 */
  getVersions(id: number) {
    return request.get<unknown, ApiResult<DevTaskVersion[] | PageResult<DevTaskVersion>>>(`/dev/tasks/${id}/versions`)
  }
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
  deleteModelField(fieldId: number) { return request.delete(`/catalog/models/fields/${fieldId}`) },
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
  drill(id: number, params: Record<string, any>, dataSourceId?: number) {
    return request.post(`/catalog/reports/${id}/drill`, { params, dataSourceId }, { timeout: 120000 })
  },
  run(id: number, params: Record<string, any>, dataSourceId?: number) {
    return request.post(`/catalog/reports/${id}/run`, { params, dataSourceId }, { timeout: 120000 })
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
    /** true=只生成草稿，不直接写表/资产 */
    draft?: boolean
    /** 草稿所属任务ID */
    taskId?: number
  }) {
    return request.post('/catalog/metadata/ai-annotate', payload, { timeout: 180000 })
  },
  /** 数据源级 AI 注释（异步）：创建任务后立即返回，前端轮询任务状态 */
  aiAnnotateAsync(payload: { dataSourceId?: number; pendingOnly?: boolean; taskId: number }) {
    return request.post('/catalog/metadata/ai-annotate/async', payload, { timeout: 30000 })
  },
  /** 单表 AI 注释草稿（任务中心单行入口） */
  aiAnnotateTable(tableId: number, taskId: number) {
    return request.post(`/catalog/metadata/tables/${tableId}/ai-annotate`, { taskId }, { timeout: 180000 })
  }
}
