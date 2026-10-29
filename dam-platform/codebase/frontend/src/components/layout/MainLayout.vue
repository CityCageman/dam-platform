<template>
  <div class="layout">
    <!-- 深色渐变侧边栏 -->
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">DA</div>
        <div class="brand-text">
          <div class="brand-title">数据资产管理平台</div>
          <div class="brand-sub">Data Asset Platform</div>
        </div>
      </div>

      <el-menu
        :default-active="defaultActive"
        router
        class="nav"
        background-color="transparent"
        text-color="#A6B4CC"
        active-text-color="#FFFFFF"
      >
        <template v-for="g in moduleGroups" :key="g.title">
          <div class="nav-group">{{ g.title }}</div>
          <template v-for="item in g.items" :key="item.configCode">
            <el-sub-menu v-if="item.children?.length" :index="item.configCode">
              <template #title>
                <img v-if="isImageIcon(item.icon)" :src="iconSrc(item.icon)" class="menu-icon-img" />
                <el-icon v-else><component :is="item.icon" /></el-icon>
                <span>{{ item.configName }}</span>
              </template>
              <el-menu-item v-for="child in item.children" :key="child.configCode" :index="navIndex(child)">
                <img v-if="isImageIcon(child.icon)" :src="iconSrc(child.icon)" class="menu-icon-img" />
                <el-icon v-else><component :is="child.icon" /></el-icon>
                <span>{{ child.configName }}</span>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="routePath(item)">
              <img v-if="isImageIcon(item.icon)" :src="iconSrc(item.icon)" class="menu-icon-img" />
              <el-icon v-else><component :is="item.icon" /></el-icon>
              <span>{{ item.configName }}</span>
            </el-menu-item>
          </template>
        </template>
      </el-menu>

      <div class="sidebar-foot">
        <div class="dot"></div>
        <span>服务运行中</span>
      </div>
    </aside>

    <!-- 右侧主体 -->
    <main class="main">
      <header class="topbar">
        <div class="crumb">
          <span class="crumb-root">数据治理</span>
          <el-icon class="crumb-sep"><ArrowRight /></el-icon>
          <span class="crumb-current">{{ route.meta.title }}</span>
        </div>
        <div class="topbar-right">
          <el-input v-model="keyword" class="global-search" placeholder="搜索资产名称 / 编码 / 标签"
                    :prefix-icon="Search" clearable @keyup.enter="goSearch" />
          <el-dropdown trigger="click" @command="handleHeaderCommand">
            <div class="user-inner">
              <el-avatar :size="30" class="avatar">{{ (userStore.userInfo?.realName || userStore.userInfo?.username || 'U').charAt(0).toUpperCase() }}</el-avatar>
              <div class="user-meta">
                <span>{{ userStore.userInfo?.realName || userStore.userInfo?.username }}</span>
              </div>
              <el-icon class="user-caret"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile"><el-icon><User /></el-icon>个人中心</el-dropdown-item>
                <el-dropdown-item command="system-config" divided><el-icon><Setting /></el-icon>系统配置</el-dropdown-item>
                <el-dropdown-item command="logout" divided><el-icon><SwitchButton /></el-icon>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <section class="content">
        <router-view />
      </section>
    </main>
    <AgentFloat />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Search, ArrowRight, ArrowDown, User, Setting, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { authApi, catalogApi } from '@/api'
import AgentFloat from '@/components/agent/AgentFloat.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const keyword = ref('')
const navConfig = ref<any[]>([])
const menuItems = router.options.routes.find(r => r.path === '/')?.children || []
const routeMap = computed(() => {
  const map = new Map<string, any>()
  menuItems.forEach(m => map.set(m.path, m))
  return map
})

// 构建菜单树
const menuTree = computed(() => {
  const list = navConfig.value.filter((n: any) => n.configCode !== 'data-sources')
  if (!list.length) {
    return menuItems.filter(m => !m.meta?.hidden && m.path !== 'data-sources').map(m => ({
      configCode: m.path, configName: m.meta?.title, icon: m.meta?.icon, children: [],
      sortOrder: 0, status: 1
    })).sort((a: any, b: any) => a.sortOrder - b.sortOrder)
  }
  const map = new Map<number, any>()
  list.forEach((item: any) => map.set(item.id, { ...item, children: [] }))
  const roots: any[] = []
  list.forEach((item: any) => {
    const node = map.get(item.id)
    if (item.parentId && map.has(item.parentId)) {
      const parent = map.get(item.parentId)
      node._parentCode = parent.configCode
      node._parentPath = parent.configValue || '/' + parent.configCode
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots.filter((n: any) => n.status !== 0).sort((a: any, b: any) => (a.sortOrder || 99) - (b.sortOrder || 99)).map((n: any) => {
    if (n.children?.length) {
      n.children = n.children.filter((c: any) => c.status !== 0).sort((a: any, b: any) => (a.sortOrder || 99) - (b.sortOrder || 99))
    }
    return n
  })
})

// ===== 平台大模块分组（整个侧边栏按大模块组织，参考 frontend-new） =====
const MODULE_GROUPS = [
  { title: '总览', keys: ['dashboard'] },
  { title: '数据资产', keys: ['catalog', 'catalog-detail', 'metadata', 'models', 'lineage'] },
  { title: '治理能力', keys: ['standard', 'quality', 'security', 'lifecycle'] },
  { title: '平台运营', keys: ['operations', 'agent'] }
]

/** 取菜单项对应的一级路径段（兼容配置中心 navigation 的 configValue） */
function groupKey(item: any): string {
  const p = String(item.configValue || item.configCode || '')
  return p.replace(/^\//, '').split('/')[0].split('?')[0]
}

/** 数据库菜单（sys_menu）转换后的分组 */
const apiMenuGroups = ref<{ title: string; items: any[] }[]>([])

/** 将 sys_menu 树节点规范化为前端菜单项结构 */
function normalizeMenu(n: any): any {
  return {
    ...n,
    configCode: n.path || String(n.id),
    configName: n.menuName || n.configName,
    configValue: n.path,
    icon: n.icon,
    children: (n.children || []).map(normalizeMenu)
  }
}

/** 按大模块分组后的菜单（数据库菜单优先，空时回退静态路由分组） */
const moduleGroups = computed(() => {
  if (apiMenuGroups.value.length) return apiMenuGroups.value
  const roots = menuTree.value
  const groups = MODULE_GROUPS.map(g => ({ title: g.title, items: [] as any[] }))
  const others: any[] = []
  for (const it of roots) {
    const k = groupKey(it)
    const idx = MODULE_GROUPS.findIndex(g => g.keys.includes(k))
    if (idx >= 0) groups[idx].items.push(it)
    else others.push(it)
  }
  const res = groups.filter(g => g.items.length)
  if (others.length) res.push({ title: '其他', items: others })
  return res
})

function navIndex(item: any): string {
  if (item._parentPath && item._parentCode && item.configCode.startsWith(item._parentCode + '-')) {
    const type = item.configCode.substring(item._parentCode.length + 1)
    return item._parentPath + '?type=' + type
  }
  return routePath(item)
}

function routePath(item: any): string {
  if (item.configValue) return item.configValue
  const found = routeMap.value.get(item.configCode)
  const p = found ? found.path : item.configCode
  return p.startsWith('/') ? p : '/' + p
}

/** 判断 icon 值是否为图片路径（否则视为 Element Plus 组件名，保持兼容） */
function isImageIcon(icon?: string) {
  return !!icon && (icon.includes('/') || /\.(svg|png|ico|webp)$/i.test(icon))
}

/** 将存储的图标路径转为可访问的 src */
function iconSrc(icon: string) {
  if (!icon) return ''
  return icon.startsWith('/') ? icon : '/image/' + icon
}

const defaultActive = computed(() => {
  if (route.query.type) {
    return '/' + (route.path.split('/')[1] || 'dashboard') + '?type=' + route.query.type
  }
  return '/' + (route.path.split('/')[1] || 'dashboard')
})

onMounted(async () => {
  // 1. 优先读取数据库菜单（dam_sys.sys_menu）
  try {
    const res: any = await catalogApi.getMenus()
    const tree = res.data || []
    if (tree.length) {
      apiMenuGroups.value = tree.map((n: any) => {
        const node = normalizeMenu(n)
        return { title: node.configName, items: node.children?.length ? node.children : [node] }
      })
    }
  } catch (_e) { /* 菜单接口不可用时回退 */ }

  // 2. 回退：配置中心 navigation（为空时 menuTree 再用静态路由兜底）
  if (!apiMenuGroups.value.length) {
    try {
      const res: any = await catalogApi.getNavigation()
      navConfig.value = res.data || []
    } catch (_e) {}
  }
})

function goSearch() {
  if (!keyword.value.trim()) return
  router.push({ path: '/catalog', query: { keyword: keyword.value.trim() } })
}

function handleHeaderCommand(command: string) {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'system-config') {
    router.push('/system-config')
  } else if (command === 'logout') {
    handleLogout()
  }
}

async function handleLogout() {
  await authApi.logout()
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
  background: var(--med-bg);
}

/* ===== 深色渐变侧边栏 ===== */
.sidebar {
  width: var(--med-sidebar-width);
  flex: 0 0 var(--med-sidebar-width);
  background: radial-gradient(120% 80% at 0% 0%, #16305a 0%, #0c1a30 55%, #0a1526 100%);
  display: flex;
  flex-direction: column;
  padding: 18px 12px;
  box-sizing: border-box;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
}
.sidebar::-webkit-scrollbar { width: 0; }
.menu-icon-img { width: 16px; height: 16px; object-fit: contain; margin-right: 6px; vertical-align: middle; }
.nav-group { color: #6f86a8; font-size: 11px; letter-spacing: .5px; padding: 14px 12px 6px; }

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px 20px;
}
.brand-mark {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #2f7bff, #16c2c2);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: .5px;
  box-shadow: 0 6px 16px rgba(47, 123, 255, .35);
  flex: 0 0 36px;
}
.brand-title { color: #fff; font-size: 14px; font-weight: 600; letter-spacing: .3px; }
.brand-sub { color: #6f86a8; font-size: 11px; margin-top: 2px; letter-spacing: .4px; }

.nav { border-right: none; flex: 1; }
:deep(.el-menu-item), :deep(.el-sub-menu__title) {
  height: 40px;
  line-height: 40px;
  border-radius: 8px;
  margin: 2px 0;
  font-size: 13px;
}
:deep(.el-menu-item:hover), :deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, .06) !important;
}
:deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(47, 123, 255, .9), rgba(47, 123, 255, .55)) !important;
  box-shadow: 0 6px 14px rgba(47, 123, 255, .25);
}
:deep(.el-sub-menu .el-menu) { background: transparent; }

.sidebar-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #5f7595;
  font-size: 11px;
  padding: 12px 10px 4px;
  border-top: 1px solid rgba(255, 255, 255, .06);
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #2ee6a8;
  box-shadow: 0 0 8px #2ee6a8;
}

/* ===== 右侧主体 ===== */
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: var(--med-header-height);
  background: #fff;
  border-bottom: 1px solid #e8ecf3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 10;
}

.crumb { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #8a94a6; }
.crumb-current { color: #1f2a3d; font-weight: 600; }
.crumb-sep { font-size: 12px; }

.topbar-right { display: flex; align-items: center; gap: 14px; }
.global-search { width: 260px; }

.user-inner {
  display: flex;
  align-items: center;
  gap: 9px;
  cursor: pointer;
  padding: 5px 8px;
  border-radius: 9px;
}
.user-inner:hover { background: #f4f6fa; }
.avatar { background: linear-gradient(135deg, #2f7bff, #7aa7ff); color: #fff; font-size: 13px; }
.user-meta { font-size: 13px; color: #34405a; }
.user-caret { color: #98a2b3; font-size: 12px; }

.content {
  padding: 20px 24px 32px;
  flex: 1;
  overflow-y: auto;
}
</style>
