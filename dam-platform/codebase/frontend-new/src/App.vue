<template>
  <!-- 登录页全屏独立，不带主布局 -->
  <router-view v-if="route.meta.public" />

  <div class="layout" v-else>
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">DA</div>
        <div class="brand-text">
          <div class="brand-title">数据资产管理平台</div>
          <div class="brand-sub">Data Asset Platform</div>
        </div>
      </div>

      <el-menu :default-active="route.path" router class="nav" background-color="transparent"
               text-color="#A6B4CC" active-text-color="#FFFFFF" v-if="auth.user">
        <el-menu-item index="/dashboard" v-if="show('dashboard')">
          <el-icon><DataBoard /></el-icon><span>总览</span>
        </el-menu-item>
        <div class="nav-group">数据资产</div>
        <el-menu-item index="/assets" v-if="show('assets')">
          <el-icon><Files /></el-icon><span>资产目录</span>
        </el-menu-item>
        <el-menu-item index="/catalog" v-if="show('catalog')">
          <el-icon><FolderOpened /></el-icon><span>数据目录</span>
        </el-menu-item>
        <el-menu-item index="/metadata" v-if="show('metadata')">
          <el-icon><Coin /></el-icon><span>元数据管理</span>
        </el-menu-item>
        <el-menu-item index="/lineage" v-if="show('lineage')">
          <el-icon><Share /></el-icon><span>数据血缘</span>
        </el-menu-item>
        <div class="nav-group">治理能力</div>
        <el-menu-item index="/standards" v-if="show('standards')">
          <el-icon><Collection /></el-icon><span>数据标准</span>
        </el-menu-item>
        <el-menu-item index="/quality" v-if="show('quality')">
          <el-icon><CircleCheck /></el-icon><span>数据质量</span>
        </el-menu-item>
        <el-menu-item index="/security" v-if="show('security')">
          <el-icon><Lock /></el-icon><span>安全分级</span>
        </el-menu-item>
        <div class="nav-group">平台管理</div>
        <el-menu-item index="/dataservice" v-if="show('dataservice')">
          <el-icon><Connection /></el-icon><span>数据服务</span>
        </el-menu-item>
        <el-menu-item index="/users" v-if="show('users')">
          <el-icon><UserFilled /></el-icon><span>用户管理</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-foot">
        <div class="dot"></div>
        <span>服务运行中 · PostgreSQL 17.11</span>
      </div>
    </aside>

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
          <el-tooltip content="接口文档 Swagger" placement="bottom">
            <a class="icon-btn" href="http://localhost:8080/swagger-ui/index.html" target="_blank">
              <el-icon><Document /></el-icon>
            </a>
          </el-tooltip>
          <div class="user" v-if="auth.user">
            <el-dropdown trigger="click" @command="onUserCommand">
              <div class="user-inner">
                <div class="avatar">{{ auth.user.displayName?.charAt(0) }}</div>
                <div class="user-meta">
                  <span>{{ auth.user.displayName }}</span>
                  <span class="user-role" :style="{ color: ROLE_META[auth.user.role]?.color }">
                    {{ ROLE_META[auth.user.role]?.name || auth.user.role }}
                  </span>
                </div>
                <el-icon class="user-caret"><CaretBottom /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item disabled>
                    {{ auth.user.username }} · {{ auth.user.department || '-' }}
                  </el-dropdown-item>
                  <el-dropdown-item divided command="logout">
                    <el-icon><SwitchButton /></el-icon>退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </header>

      <section class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, CaretBottom, SwitchButton, FolderOpened, User } from '@element-plus/icons-vue'
import { api } from './api'
import { auth, clearLogin, hasPermission, ROLE_META } from './store'

const route = useRoute()
const router = useRouter()
const keyword = ref('')

function show(code) {
  return hasPermission(code)
}

async function onUserCommand(cmd) {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' })
    try { await api.logout() } catch { /* 忽略 */ }
    clearLogin()
    ElMessage.success('已退出')
    router.push('/login')
  }
}

function goSearch() {
  if (!keyword.value.trim()) return
  router.push({ path: '/assets', query: { q: keyword.value.trim() } })
}
</script>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
  background: #f4f6fa;
}

.sidebar {
  width: 232px;
  flex: 0 0 232px;
  background: radial-gradient(120% 80% at 0% 0%, #16305a 0%, #0c1a30 55%, #0a1526 100%);
  display: flex;
  flex-direction: column;
  padding: 18px 12px;
  box-sizing: border-box;
  position: sticky;
  top: 0;
  height: 100vh;
}

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
}

.brand-title {
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: .3px;
}

.brand-sub {
  color: #6f86a8;
  font-size: 11px;
  margin-top: 2px;
  letter-spacing: .4px;
}

.nav {
  border-right: none;
  flex: 1;
}

.nav-group {
  color: #5c7292;
  font-size: 11px;
  letter-spacing: 1px;
  padding: 14px 12px 6px;
}

:deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
  border-radius: 8px;
  margin: 2px 0;
  font-size: 13px;
}

:deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, .06) !important;
}

:deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(47, 123, 255, .9), rgba(47, 123, 255, .55)) !important;
  box-shadow: 0 6px 14px rgba(47, 123, 255, .25);
}

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

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: 60px;
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

.crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #8a94a6;
}

.crumb-current {
  color: #1f2a3d;
  font-weight: 600;
}

.crumb-sep {
  font-size: 12px;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.global-search {
  width: 260px;
}

.icon-btn {
  color: #5b6b85;
  display: flex;
  align-items: center;
  font-size: 18px;
}

.user {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #34405a;
}

.user-inner {
  display: flex;
  align-items: center;
  gap: 9px;
  cursor: pointer;
  padding: 5px 8px;
  border-radius: 9px;
}

.user-inner:hover { background: #f4f6fa; }

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.user-role { font-size: 11px; }

.user-caret { color: #98a2b3; font-size: 12px; }

.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2f7bff, #7aa7ff);
  color: #fff;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.content {
  padding: 20px 24px 32px;
  flex: 1;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity .15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
