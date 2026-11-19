import { createRouter, createWebHistory } from 'vue-router'
import { getModuleNavigation } from '@/config/navigation'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    icon?: string
    hidden?: boolean
    /** 模块编码：MainLayout 据此切换为模块详细菜单，刷新后依然有效 */
    moduleCode?: string
    /** 模块名称，便于调试与面包屑兜底 */
    moduleName?: string
    /** 模块详细菜单编码（对应 config/navigation.ts 中的映射） */
    moduleMenu?: string
  }
}

/** 为模块路由生成统一 meta，避免模块编码、名称与导航配置不一致 */
function moduleMeta(code: string, title: string, icon?: string) {
  const navigation = getModuleNavigation(code)
  return {
    title,
    ...(icon ? { icon } : {}),
    moduleCode: code,
    moduleName: navigation?.name || code,
    moduleMenu: code
  }
}

const routes = [
  { path: '/login', component: () => import('@/views/auth/Login.vue'), meta: { title: '登录' } },
  {
    path: '/',
    component: () => import('@/components/layout/MainLayout.vue'),
    // 登录后默认进入运营看板；左侧固定展示 运营看板 / 统一门户 / 个人中心 / 系统配置
    redirect: '/dashboard',
    children: [
      // 运营看板：门户模式固定入口（不展示模块详细菜单）
      { path: 'dashboard', component: () => import('@/views/dashboard/Index.vue'), meta: { title: '运营看板', icon: 'DataAnalysis' } },
      // 统一门户：能力域总览与能力域模块入口
      { path: 'portal', component: () => import('@/views/portal/PortalHome.vue'), meta: { title: '统一门户', icon: 'Grid' } },
      { path: 'domains/:domainCode', component: () => import('@/views/portal/DomainHome.vue'), meta: { title: '能力域', icon: 'Grid', hidden: true } },
      // 数据资产模块：资产目录 / 元数据管理 / 资产详情
      { path: 'catalog', component: () => import('@/views/catalog/AssetList.vue'), meta: moduleMeta('DAM', '资产目录', 'FolderOpened') },
      { path: 'catalog-detail/:id', component: () => import('@/views/catalog/AssetDetail.vue'), meta: moduleMeta('DAM', '资产详情', 'Document') },
      { path: 'metadata', component: () => import('@/views/catalog/MetadataCollection.vue'), meta: moduleMeta('DAM', '元数据管理', 'DataBoard') },
      { path: 'data-sources', component: () => import('@/views/catalog/DataSourceList.vue'), meta: moduleMeta('INT', '数据源管理', 'Connection') },
      // 标准与模型
      { path: 'models', component: () => import('@/views/catalog/ModelManagement.vue'), meta: moduleMeta('MDL', '模型管理', 'Collection') },
      { path: 'standard', component: () => import('@/views/standard/Index.vue'), meta: moduleMeta('STD', '数据标准', 'Document') },
      // 数据工程
      { path: 'integration', component: () => import('@/views/integration/IntegrationManagement.vue'), meta: moduleMeta('INT', '数据集成', 'Connection') },
      { path: 'develop', component: () => import('@/views/develop/DataDevelopment.vue'), meta: moduleMeta('DEV', '数据开发', 'Cpu') },
      { path: 'scheduler', component: () => import('@/views/scheduler/SchedulerManagement.vue'), meta: moduleMeta('SCH', '调度中心', 'Timer') },
      // 数据治理
      { path: 'lineage', component: () => import('@/views/lineage/Index.vue'), meta: moduleMeta('LIN', '数据血缘', 'Share') },
      { path: 'quality', component: () => import('@/views/quality/Index.vue'), meta: moduleMeta('DQ', '数据质量', 'Histogram') },
      { path: 'security', component: () => import('@/views/security/Index.vue'), meta: moduleMeta('SEC', '安全管控', 'Lock') },
      { path: 'lifecycle', component: () => import('@/views/lifecycle/Index.vue'), meta: moduleMeta('LIF', '生命周期管理', 'Odometer') },
      // 指标与分析
      { path: 'metrics', component: () => import('@/views/metric/MetricManagement.vue'), meta: moduleMeta('MET', '指标管理', 'TrendCharts') },
      { path: 'reports', component: () => import('@/views/report/ReportCenter.vue'), meta: moduleMeta('RPT', '报表体系', 'DataAnalysis') },
      // 数据服务
      { path: 'data-service', component: () => import('@/views/dataService/DataServiceManagement.vue'), meta: moduleMeta('DS', '数据服务', 'Link') },
      // AI 与知识：知识中心独立路由，内部复用 AI 工作台同一套知识库组件
      { path: 'knowledge', component: () => import('@/views/knowledge/KnowledgePage.vue'), meta: moduleMeta('KNO', '知识中心', 'Reading') },
      { path: 'agent', component: () => import('@/views/agent/AgentWorkbench.vue'), meta: moduleMeta('AI', 'AI 工作台', 'MagicStick') },
      // 兼容旧入口：AI 任务中心页面继续保留（不在初始菜单展示）
      { path: 'agent/tasks', component: () => import('@/views/agent/AiTaskCenter.vue'), meta: { ...moduleMeta('AI', 'AI 任务中心', 'Tickets'), hidden: true } },
      // 平台与运维
      { path: 'task-center', component: () => import('@/views/taskCenter/TaskCenter.vue'), meta: moduleMeta('TC', '任务中心', 'Tickets') },
      { path: 'operations', component: () => import('@/views/operations/Index.vue'), meta: moduleMeta('OPS', '工单中心', 'Tickets') },
      // 固定入口：个人中心 / 系统配置始终使用门户模式
      { path: 'profile', component: () => import('@/views/system/Profile.vue'), meta: { title: '个人中心', hidden: true } },
      // /system-config 是固定入口（门户模式）；系统管理 / 模型网关使用带模块编码的路径，
      // 刷新后仍由路由 meta 恢复模块详细菜单。
      { path: 'system-config', component: () => import('@/views/system/SystemConfig.vue'), meta: { title: '系统配置', icon: 'Setting' } },
      { path: 'system-config/SYS', component: () => import('@/views/system/SystemConfig.vue'), meta: moduleMeta('SYS', '系统配置', 'Setting') },
      { path: 'system-config/MGW', component: () => import('@/views/system/SystemConfig.vue'), meta: moduleMeta('MGW', '系统配置', 'Setting') },
    ]
  },
  // 未匹配的地址回到默认首页，避免空白页或死链；未登录时由守卫统一转到登录页
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
