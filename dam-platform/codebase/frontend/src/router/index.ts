import { createRouter, createWebHistory } from 'vue-router'
import { getModuleNavigation } from '@/config/navigation'
import { SYSTEM_CONFIG_ROLES, rolesForModule } from '@/config/roles'
import { useUserStore } from '@/store/user'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    icon?: string
    hidden?: boolean
    /** 允许访问的角色编码；未声明时所有登录用户可访问，super_admin 始终放行 */
    roles?: string[]
    /** 模块编码：MainLayout 据此切换为模块详细菜单，刷新后依然有效 */
    moduleCode?: string
    /** 模块名称，便于调试与面包屑兜底 */
    moduleName?: string
    /** 模块详细菜单编码（对应 config/navigation.ts 中的映射） */
    moduleMenu?: string
  }
}

/** 为模块路由生成统一 meta，避免模块编码、名称与导航配置不一致 */
function moduleMeta(code: string, title: string, icon?: string, roles: string[] = rolesForModule(code)) {
  const navigation = getModuleNavigation(code)
  return {
    title,
    ...(icon ? { icon } : {}),
    moduleCode: code,
    moduleName: navigation?.name || code,
    moduleMenu: code,
    roles
  }
}

/** 驾驶舱标题与图标统一在这里声明，routes 与页面保持同一份文案 */
const cockpitMeta = (title: string, icon: string, roles: string[]) => ({ title, icon, roles })

const routes = [
  { path: '/login', component: () => import('@/views/auth/Login.vue'), meta: { title: '登录' } },
  {
    path: '/',
    component: () => import('@/components/layout/MainLayout.vue'),
    // 默认首页由角色决定；守卫会在 /auth/me 返回后再次校准
    redirect: () => useUserStore().homePath,
    children: [
      // 角色驾驶舱
      {
        path: 'cockpit/super',
        component: () => import('@/views/cockpit/SuperCockpit.vue'),
        meta: cockpitMeta('超级管理驾驶舱', 'Setting', ['super_admin'])
      },
      {
        path: 'cockpit/executive',
        component: () => import('@/views/cockpit/ExecutiveCockpit.vue'),
        meta: cockpitMeta('院领导驾驶舱', 'TrendCharts', ['executive'])
      },
      {
        path: 'cockpit/department',
        component: () => import('@/views/cockpit/DepartmentCockpit.vue'),
        meta: cockpitMeta('科室驾驶舱', 'DataBoard', ['department_head'])
      },
      {
        path: 'cockpit/ops',
        component: () => import('@/views/cockpit/OpsCockpit.vue'),
        meta: cockpitMeta('平台驾驶舱', 'Monitor', ['data_ops'])
      },
      {
        path: 'cockpit/developer',
        component: () => import('@/views/cockpit/DeveloperCockpit.vue'),
        meta: cockpitMeta('开发工作台', 'Cpu', ['developer'])
      },
      {
        path: 'cockpit/governance',
        component: () => import('@/views/cockpit/PlaceholderCockpit.vue'),
        props: { variant: 'governance' },
        meta: cockpitMeta('数据管家驾驶舱', 'Histogram', ['governor'])
      },
      {
        path: 'cockpit/business',
        component: () => import('@/views/cockpit/PlaceholderCockpit.vue'),
        props: { variant: 'business' },
        meta: cockpitMeta('业务驾驶舱', 'User', ['business'])
      },
      {
        path: 'cockpit/security',
        component: () => import('@/views/cockpit/PlaceholderCockpit.vue'),
        props: { variant: 'security' },
        meta: cockpitMeta('安全驾驶舱', 'Lock', ['security'])
      },
      // 兼容入口：旧首页 /dashboard 按角色重定向到默认驾驶舱（路由守卫中兜底）
      { path: 'dashboard', redirect: () => useUserStore().homePath, meta: { title: '首页' } },
      // 产品中心（原统一门户）：能力域总览与模块入口，单窗口内跳转
      { path: 'portal', component: () => import('@/views/portal/PortalHome.vue'), meta: { title: '产品中心', icon: 'Grid' } },
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
      {
        path: 'system-config',
        component: () => import('@/views/system/SystemConfig.vue'),
        meta: { title: '系统配置', icon: 'Setting', roles: SYSTEM_CONFIG_ROLES }
      },
      {
        path: 'system-config/SYS',
        component: () => import('@/views/system/SystemConfig.vue'),
        meta: moduleMeta('SYS', '系统配置', 'Setting', SYSTEM_CONFIG_ROLES)
      },
      {
        path: 'system-config/MGW',
        component: () => import('@/views/system/SystemConfig.vue'),
        meta: moduleMeta('MGW', '系统配置', 'Setting', SYSTEM_CONFIG_ROLES)
      }
    ]
  },
  // 未匹配的地址回到角色默认首页，避免空白页或死链；未登录时由守卫统一转到登录页
  { path: '/:pathMatch(.*)*', redirect: () => useUserStore().homePath }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const token = localStorage.getItem('token')
  // 登录页保持可用（已登录时也允许访问，便于切换账号）
  if (to.path === '/login') return true
  if (!token) {
    return {
      path: '/login',
      query: to.fullPath && to.fullPath !== '/' ? { redirect: to.fullPath } : undefined
    }
  }

  const userStore = useUserStore()
  // 每次进入应用首次导航时刷新 /auth/me，确保角色/defaultCockpit 与后端一致
  await userStore.ensureProfile()

  // 旧首页兼容：/ 与 /dashboard 按角色默认驾驶舱跳转
  if (to.path === '/' || to.path === '/dashboard') {
    return userStore.homePath
  }

  // meta.roles 路由级授权：未授权回到自己的默认驾驶舱，super_admin 始终放行
  if (!userStore.canAccess(to.meta.roles)) {
    const homePath = userStore.homePath
    if (to.path === homePath) return true
    return homePath
  }

  return true
})

export default router
