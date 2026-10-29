import { createRouter, createWebHistory } from 'vue-router'
import { auth } from './store'

const routes = [
  { path: '/login', name: 'login', component: () => import('./views/Login.vue'), meta: { title: '登录', public: true } },
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'dashboard', component: () => import('./views/Dashboard.vue'), meta: { title: '总览', perm: 'dashboard' } },
  { path: '/assets', name: 'assets', component: () => import('./views/Assets.vue'), meta: { title: '资产目录', perm: 'assets' } },
  { path: '/catalog', name: 'catalog', component: () => import('./views/Catalog.vue'), meta: { title: '数据目录', perm: 'catalog' } },
  { path: '/metadata', name: 'metadata', component: () => import('./views/Metadata.vue'), meta: { title: '元数据管理', perm: 'metadata' } },
  { path: '/lineage', name: 'lineage', component: () => import('./views/Lineage.vue'), meta: { title: '数据血缘', perm: 'lineage' } },
  { path: '/standards', name: 'standards', component: () => import('./views/Standards.vue'), meta: { title: '数据标准', perm: 'standards' } },
  { path: '/quality', name: 'quality', component: () => import('./views/Quality.vue'), meta: { title: '数据质量', perm: 'quality' } },
  { path: '/security', name: 'security', component: () => import('./views/Security.vue'), meta: { title: '安全分级', perm: 'security' } },
  { path: '/dataservice', name: 'dataservice', component: () => import('./views/DataService.vue'), meta: { title: '数据服务', perm: 'dataservice' } },
  { path: '/users', name: 'users', component: () => import('./views/Users.vue'), meta: { title: '用户管理', perm: 'users' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!auth.token) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.perm && auth.permissions.length && !auth.permissions.includes(to.meta.perm)) {
    return { path: '/dashboard' }
  }
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title || ''} · 数据资产管理平台`
})

export default router
