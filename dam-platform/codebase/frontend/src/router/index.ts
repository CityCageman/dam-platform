import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('@/views/auth/Login.vue'), meta: { title: '登录' } },
  {
    path: '/',
    component: () => import('@/components/layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', component: () => import('@/views/dashboard/Index.vue'), meta: { title: '运营看板', icon: 'DataAnalysis' } },
      { path: 'catalog', component: () => import('@/views/catalog/AssetList.vue'), meta: { title: '资产目录', icon: 'Folder' } },
      { path: 'catalog-detail/:id', component: () => import('@/views/catalog/AssetDetail.vue'), meta: { title: '资产详情', hidden: true } },
      { path: 'metadata', component: () => import('@/views/catalog/MetadataCollection.vue'), meta: { title: '元数据管理', icon: 'DataBoard' } },
      // { path: 'data-sources', component: () => import('@/views/catalog/DataSourceList.vue'), meta: { title: '数据源管理', icon: 'Connection' } }, // 已合并到元数据管理
      { path: 'models', component: () => import('@/views/catalog/ModelManagement.vue'), meta: { title: '模型管理', icon: 'Collection' } },
      { path: 'lineage', component: () => import('@/views/lineage/Index.vue'), meta: { title: '数据血缘', icon: 'Connection' } },
      { path: 'reports', component: () => import('@/views/report/ReportCenter.vue'), meta: { title: '报表体系', icon: 'DataAnalysis' } },
      { path: 'standard', component: () => import('@/views/standard/Index.vue'), meta: { title: '数据标准', icon: 'Document' } },
      { path: 'quality', component: () => import('@/views/quality/Index.vue'), meta: { title: '数据质量', icon: 'List' } },
      { path: 'security', component: () => import('@/views/security/Index.vue'), meta: { title: '安全管控', icon: 'Lock' } },
      { path: 'lifecycle', component: () => import('@/views/lifecycle/Index.vue'), meta: { title: '生命周期', icon: 'Timer' } },
      { path: 'operations', component: () => import('@/views/operations/Index.vue'), meta: { title: '工单中心', icon: 'Tickets' } },
      { path: 'agent', component: () => import('@/views/agent/AgentChat.vue'), meta: { title: '智能助手', icon: 'ChatDotRound' } },
      { path: 'profile', component: () => import('@/views/system/Profile.vue'), meta: { title: '个人中心', hidden: true } },
      { path: 'system-config', component: () => import('@/views/system/SystemConfig.vue'), meta: { title: '系统配置', hidden: true } },
    ]
  }
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
