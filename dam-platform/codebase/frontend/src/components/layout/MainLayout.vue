<template>
  <div class="layout">
    <!-- 深色渐变侧边栏 -->
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">DA</div>
        <div class="brand-text">
          <div class="brand-title">数据资产管理平台</div>
          <div class="brand-sub">DATA ASSET PLATFORM</div>
        </div>
      </div>

      <el-menu
        :default-active="activeMenuIndex"
        router
        class="nav"
        background-color="transparent"
        text-color="#A6B4CC"
        active-text-color="#FFFFFF"
      >
        <!-- 能力域窗口：只展示当前能力域及其模块，不使用全局统一入口 -->
        <template v-if="layoutMode === 'domain' && activeDomain">
          <div class="nav-group">当前能力域</div>
          <div class="module-summary">
            <span class="module-domain">CAPABILITY DOMAIN</span>
            <b class="module-name">{{ activeDomain.name }}</b>
          </div>
          <button type="button" class="back-portal domain-return" @click="goPortal">
            <el-icon><ArrowLeft /></el-icon>
            <span>返回产品中心</span>
          </button>
          <div class="nav-group">能力域模块</div>
          <el-menu-item
            v-for="module in activeDomainModules"
            :key="module.code"
            :index="module.route"
            :route="{ path: module.route, query: route.query }"
            :disabled="module.status === 'PLANNED'"
            :title="module.status === 'PLANNED' ? '规划中，暂未开放' : module.name"
          >
            <el-icon><component :is="resolvePortalIcon(module.icon)" /></el-icon>
            <span>{{ module.name }}</span>
          </el-menu-item>
        </template>

        <!-- 门户模式：按当前角色生成菜单（驾驶舱 / 产品中心 / 个人中心 / 系统配置） -->
        <template v-else-if="layoutMode === 'portal'">
          <template v-for="group in roleMenuGroups" :key="group.title">
            <div class="nav-group">{{ group.title }}</div>
            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
              <el-icon><component :is="resolvePortalIcon(item.icon)" /></el-icon>
              <span>{{ item.title }}</span>
            </el-menu-item>
          </template>
        </template>

        <!-- 模块模式：由当前路由推导，刷新后仍展示该模块的详细菜单 -->
        <template v-else-if="layoutMode === 'module' && activeModule">
          <div class="nav-group">当前模块</div>
          <div class="module-summary">
            <span v-if="activeModule.domainName" class="module-domain">{{ activeModule.domainName }}</span>
            <b class="module-name">{{ activeModule.name }}</b>
          </div>

          <el-menu-item index="/portal" class="back-portal">
            <el-icon><ArrowLeft /></el-icon>
            <span>返回产品中心</span>
          </el-menu-item>

          <div class="nav-group">模块菜单</div>
          <el-menu-item
            v-for="item in moduleMenuItems"
            :key="item.path"
            :index="item.target"
            :disabled="item.disabled"
            :title="item.hint || item.title"
          >
            <el-icon><component :is="resolvePortalIcon(item.icon)" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>

      <div class="sidebar-foot">
        <div class="avatar-sm">{{ initial }}</div>
        <div class="who">
          <b>{{ userStore.userInfo?.realName || userStore.userInfo?.username || '管理员' }}</b>
          <i>{{ sidebarDepartment }}</i>
        </div>
        <span class="online" title="服务运行中"></span>
      </div>
    </aside>

    <!-- 右侧主体 -->
    <main class="main">
      <header class="topbar">
        <div class="crumb">
          <!-- 门户/驾驶舱/系统页面：根节点 -> 当前页面 -->
          <template v-if="layoutMode === 'portal'">
            <span :class="portalCurrentTitle ? 'crumb-root' : 'crumb-current'">{{ portalRootLabel }}</span>
            <template v-if="portalCurrentTitle">
              <el-icon class="crumb-sep"><ArrowRight /></el-icon>
              <span class="crumb-current">{{ portalCurrentTitle }}</span>
            </template>
          </template>

          <!-- 能力域窗口：产品中心 -> 当前能力域 -->
          <template v-else-if="layoutMode === 'domain' && activeDomain">
            <button type="button" class="crumb-link" @click="goPortal">产品中心</button>
            <el-icon class="crumb-sep"><ArrowRight /></el-icon>
            <span class="crumb-current">{{ activeDomain.name }}</span>
          </template>

          <!-- 模块页面：产品中心 -> 能力域 -> 模块 -> 当前页面 -->
          <template v-else-if="activeModule">
            <button type="button" class="crumb-link" @click="goPortal">产品中心</button>
            <template v-if="activeModule.domainName">
              <el-icon class="crumb-sep"><ArrowRight /></el-icon>
              <button type="button" class="crumb-link" @click="goDomain">{{ activeModule.domainName }}</button>
            </template>
            <el-icon class="crumb-sep"><ArrowRight /></el-icon>
            <button type="button" class="crumb-link" @click="goModuleHome">{{ activeModule.name }}</button>
            <template v-if="showPageCrumb">
              <el-icon class="crumb-sep"><ArrowRight /></el-icon>
              <span class="crumb-current">{{ route.meta.title }}</span>
            </template>
          </template>

          <template v-else>
            <span class="crumb-root">产品中心</span>
            <template v-if="route.meta.title">
              <el-icon class="crumb-sep"><ArrowRight /></el-icon>
              <span class="crumb-current">{{ route.meta.title }}</span>
            </template>
          </template>
        </div>
        <div class="topbar-right">
          <el-input v-model="keyword" class="global-search" placeholder="搜索资产 / 表 / 字段 / 指标"
                    :prefix-icon="Search" clearable @keyup.enter="goSearch" />
          <el-button class="bell-btn" text @click="showNotice">
            <el-icon><Bell /></el-icon>
            <span class="bell-dot"></span>
          </el-button>
          <el-dropdown trigger="click" @command="handleHeaderCommand">
            <div class="user-inner">
              <el-avatar :size="30" class="avatar">{{ (userStore.userInfo?.realName || userStore.userInfo?.username || 'U').charAt(0).toUpperCase() }}</el-avatar>
              <div class="user-meta">
                <span>{{ userStore.userInfo?.realName || userStore.userInfo?.username }}</span>
                <i>{{ userRoleLabel }}</i>
              </div>
              <el-icon class="user-caret"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile"><el-icon><User /></el-icon>个人中心</el-dropdown-item>
                <el-dropdown-item v-if="showSystemConfig" command="system-config" divided><el-icon><Setting /></el-icon>系统配置</el-dropdown-item>
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
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Search, ArrowRight, ArrowDown, ArrowLeft, User, Setting, SwitchButton, Bell } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { authApi } from '@/api'
import AgentFloat from '@/components/agent/AgentFloat.vue'
import { canAccessModule, resolveRoleMenuActive } from '@/config/roles'
import {
  getModuleNavigation,
  isPortalLayoutPath,
  resolveModuleMenu
} from '@/config/navigation'
import { getPortalDomain, resolvePortalIcon } from '@/config/portal'

type LayoutMode = 'portal' | 'domain' | 'module'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const initial = computed(() => (userStore.userInfo?.realName || userStore.userInfo?.username || '管').charAt(0).toUpperCase())
const sidebarDepartment = computed(() => userStore.department || userStore.userInfo?.department || '未设置科室')
const userRoleLabel = computed(() => userStore.roleNames.join(' / '))
function showNotice() { ElMessage.info('暂无新的待处理通知') }

const keyword = ref('')

/** 左侧菜单按角色生成，超管为全量菜单 */
const roleMenuGroups = computed(() => userStore.roleMenu)
const showSystemConfig = computed(() =>
  roleMenuGroups.value.some(group => group.items.some(item => item.path === '/system-config'))
)

/** 去除末尾斜杠后的当前路径，避免 /catalog/ 之类地址导致高亮丢失 */
const currentPath = computed(() => route.path.replace(/\/+$/, '') || '/')

/** 当前路由对应的模块编码（模块路由的 meta 在 router/index.ts 中统一生成） */
const routeModuleCode = computed(() => {
  const metaCode = route.meta.moduleCode
  if (typeof metaCode === 'string' && metaCode) return metaCode
  const paramCode = route.params.moduleCode
  if (Array.isArray(paramCode)) return paramCode[0] || ''
  return paramCode || ''
})

/** 当前能力域窗口（/domains/:domainCode） */
const activeDomain = computed(() => {
  if (!currentPath.value.startsWith('/domains/')) return undefined
  const code = Array.isArray(route.params.domainCode) ? route.params.domainCode[0] : route.params.domainCode
  return getPortalDomain(code)
})

/** 能力域窗口只展示当前角色可访问的模块，避免点入后被路由守卫弹回 */
const activeDomainModules = computed(() =>
  activeDomain.value
    ? activeDomain.value.modules.filter(module => canAccessModule(module.code, userStore.roles))
    : []
)

const activeModule = computed(() => getModuleNavigation(routeModuleCode.value))

/**
 * 布局模式完全由当前路由推导：
 * - /cockpit/*、/portal、/domains/*、/profile、/system-config 为门户模式（展示角色菜单）；
 * - 其余带 moduleCode 的路由为模块模式；
 * 因此刷新模块页面不会丢失左侧详细菜单。
 */
const layoutMode = computed<LayoutMode>(() => {
  if (activeDomain.value) return 'domain'
  if (isPortalLayoutPath(route.path)) return 'portal'
  return activeModule.value ? 'module' : 'portal'
})

const moduleMenuItems = computed(() =>
  activeModule.value ? resolveModuleMenu(activeModule.value, route) : []
)

const activeMenuIndex = computed(() => {
  if (layoutMode.value === 'domain') return ''
  if (layoutMode.value === 'portal') return resolveRoleMenuActive(route.path, roleMenuGroups.value)
  return moduleMenuItems.value.find(item => item.active && !item.disabled)?.target || ''
})

/** 门户模式面包屑根节点：驾驶舱 / 个人中心 / 系统配置 / 产品中心 */
const portalRootLabel = computed(() => {
  if (currentPath.value.startsWith('/cockpit/')) return '驾驶舱'
  if (currentPath.value === '/profile') return '个人中心'
  if (currentPath.value === '/system-config') return '系统配置'
  return '产品中心'
})

/** 门户页面顶部面包屑的当前节点 */
const portalCurrentTitle = computed(() => {
  if (currentPath.value.startsWith('/cockpit/')) return String(route.meta.title || '')
  if (currentPath.value.startsWith('/domains/')) {
    return getPortalDomain(String(route.params.domainCode || ''))?.name || '能力域'
  }
  if (currentPath.value === '/portal' || currentPath.value === '/profile' || currentPath.value === '/system-config') return ''
  return String(route.meta.title || '')
})

/** 模块首页与模块名称重名时不重复展示当前页节点 */
const showPageCrumb = computed(() => {
  const title = String(route.meta.title || '')
  return !!title && title !== activeModule.value?.name
})

function goPortal() {
  // 单窗口模式：能力域/模块页面统一在当前窗口返回产品中心
  router.push('/portal')
}

function goDomain() {
  if (!activeModule.value?.domainCode) return
  router.push(`/domains/${activeModule.value.domainCode}`)
}

function goModuleHome() {
  if (!activeModule.value) return
  router.push(activeModule.value.basePath)
}

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
  try {
    await authApi.logout()
  } catch {
    // 后端登出失败也要清理本地登录态，避免停留在无效会话
  } finally {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
/* ================= 壳层：236 侧栏 + 64 顶栏 ================= */
.layout {
  display: flex;
  min-height: 100vh;
  background: var(--med-bg);
}
.sidebar {
  width: var(--med-sidebar-width);
  flex: 0 0 var(--med-sidebar-width);
  background: radial-gradient(120% 80% at 0% 0%, #4A0D45 0%, #30082D 52%, #23061F 100%);
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

.brand { display: flex; align-items: center; gap: 10px; padding: 6px 8px 18px; }
.brand-mark {
  width: 36px; height: 36px; flex: 0 0 36px; border-radius: 10px;
  background: linear-gradient(135deg, #8E247F 0%, #72156A 50%, #2FA6A1 100%);
  color: #fff; font-weight: 700; font-size: 14px; letter-spacing: .5px;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 7px 18px rgba(114, 21, 106, .38);
}
.brand-title { color: #fff; font-size: 14px; font-weight: 600; letter-spacing: .2px; }
.brand-sub { color: #B99AB4; font-size: 9.5px; margin-top: 2px; letter-spacing: .8px; }


/* 导航 */
.nav { border-right: none; flex: 1; background: transparent; }
.nav-group {
  color: #AD8DA9;
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 1px;
  padding: 14px 12px 6px;
}
:deep(.el-menu) { border-right: none; background: transparent; }
:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  height: 38px;
  line-height: 38px;
  border-radius: 9px;
  margin: 2px 0;
  font-size: 13px;
  color: #E0D3DE;
  position: relative;
}
:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, .06) !important;
  color: #fff;
}
:deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(114, 21, 106, .96), rgba(47, 166, 161, .58)) !important;
  color: #fff !important;
  box-shadow: 0 6px 16px rgba(114, 21, 106, .28);
}
:deep(.el-menu-item.is-active)::before {
  content: "";
  position: absolute;
  left: 2px; top: 8px; bottom: 8px;
  width: 3px; border-radius: 3px; background: #D99A1C;
}
:deep(.el-menu-item.is-disabled) {
  opacity: .42;
  cursor: not-allowed;
}
:deep(.el-sub-menu .el-menu) { background: transparent; }
:deep(.el-sub-menu .el-menu-item) { padding-left: 44px !important; font-size: 12.5px; }
:deep(.el-menu-item .el-icon),
:deep(.el-sub-menu__title .el-icon) { font-size: 16px; }
.menu-icon-img { width: 16px; height: 16px; object-fit: contain; margin-right: 6px; vertical-align: middle; }

/* 当前模块信息与返回门户 */
.module-summary {
  padding: 2px 12px 10px;
  line-height: 1.35;
}
.module-domain {
  display: block;
  color: #AD8DA9;
  font-size: 10.5px;
  letter-spacing: .5px;
}
.module-name {
  display: block;
  margin-top: 3px;
  color: #F5EDF3;
  font-size: 13.5px;
  font-weight: 600;
}
.back-portal {
  margin-bottom: 4px;
  color: #D8C7D5 !important;
  border: 1px dashed rgba(255, 255, 255, .14);
}
.domain-return {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 9px 12px;
  border-radius: 9px;
  background: transparent;
  font: inherit;
  font-size: 12.5px;
  text-align: left;
  cursor: pointer;
}

/* 侧栏底部用户 */
.sidebar-foot {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 12px 8px 4px;
  margin-top: auto;
  border-top: 1px solid rgba(255, 255, 255, .06);
}
.avatar-sm {
  width: 28px; height: 28px; flex: 0 0 28px; border-radius: 50%;
  background: linear-gradient(135deg, #8E247F, #2FA6A1);
  color: #fff; font-size: 11px; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
}
.who { min-width: 0; line-height: 1.3; }
.who b { display: block; color: #F1E8EF; font-size: 12px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.who i { font-style: normal; color: #B99AB4; font-size: 10.5px; }
.online { margin-left: auto; width: 6px; height: 6px; border-radius: 50%; background: #22c55e; box-shadow: 0 0 8px rgba(91,174,71,.8); }

/* ================= 主区域 ================= */
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.topbar {
  height: var(--med-header-height);
  flex: 0 0 var(--med-header-height);
  background: rgba(255,255,255,.96);
  border-bottom: 1px solid var(--med-border);
  box-shadow: 0 2px 12px rgba(57,25,52,.035);
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 10;
}
.crumb { display: flex; align-items: center; gap: 8px; font-size: 12.5px; color: var(--med-text-light); white-space: nowrap; }
.crumb-root { color: var(--med-text-light); }
.crumb-current { color: var(--med-text); font-weight: 600; }
.crumb-sep { font-size: 11px; }
.crumb-link {
  padding: 0;
  border: 0;
  background: transparent;
  font: inherit;
  color: var(--med-text-light);
  cursor: pointer;
}
.crumb-link:hover { color: var(--med-primary); }

.topbar-right { display: flex; align-items: center; gap: 12px; margin-left: auto; }
.global-search { width: 300px; }
:deep(.global-search .el-input__wrapper) {
  background: #F8F3F7;
  box-shadow: none;
  border: 1px solid #EEE5EE;
  border-radius: 9px;
  height: 32px;
}
:deep(.global-search .el-input__wrapper.is-focus) { border-color: var(--med-primary); background: #fff; }

.bell-btn { position: relative; width: 32px; height: 32px; padding: 0; color: var(--med-text-secondary); }
.bell-btn:hover { background: #F8F3F7; }
.bell-dot {
  position: absolute; right: 6px; top: 6px;
  width: 6px; height: 6px; border-radius: 50%;
  background: #dc2626; border: 1.5px solid #fff;
}

.user-inner { display: flex; align-items: center; gap: 9px; cursor: pointer; padding: 4px 6px; border-radius: 9px; }
.user-inner:hover { background: #F8F3F7; }
.avatar { background: linear-gradient(135deg, #8E247F, #2FA6A1); color: #fff; font-size: 12px; font-weight: 600; }
.user-meta { font-size: 12px; color: var(--med-text); line-height: 1.3; }
.user-meta b { font-weight: 600; }
.user-meta i { display: block; font-style: normal; font-size: 10.5px; color: var(--med-text-light); }
.user-caret { color: var(--med-text-light); font-size: 12px; }

.content {
  padding: 24px;
  width: 100%;
  max-width: 1720px;
  margin: 0 auto;
  flex: 1;
}

@media (max-width: 1280px) {
  .global-search { width: 200px; }
  .user-meta { display: none; }
}
@media (max-width: 900px) {
  .brand-text, .nav-group, .sidebar-foot .who, .module-summary { display: none; }
  .sidebar { width: 72px; flex-basis: 72px; align-items: center; padding: 18px 8px; }
  :deep(.el-menu-item), :deep(.el-sub-menu__title) { justify-content: center; padding: 0 !important; }
  :deep(.el-menu-item span) { display: none; }
  .global-search { display: none; }
  .content { padding: 16px; }
}
</style>
