<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">个人中心</h2>
        <p class="page-desc">查看当前账号信息、角色与安全提示。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="refreshing" @click="refreshProfile"><el-icon><Refresh /></el-icon>刷新资料</el-button>
        <el-button type="danger" plain @click="logout">退出登录</el-button>
      </div>
    </div>

    <div class="profile-grid">
      <div class="panel profile-card">
        <div class="avatar">{{ initial }}</div>
        <div class="profile-name">{{ info.realName || info.username || '用户' }}</div>
        <div class="profile-role">{{ info.role || '未分配角色' }}</div>
        <div class="profile-tags">
          <el-tag size="small" type="info">{{ info.department || '未设置部门' }}</el-tag>
          <el-tag size="small" type="success">正常</el-tag>
        </div>
        <div class="profile-foot">账号 ID：{{ info.userId || info.id || '-' }}</div>
      </div>

      <div class="panel">
        <h4 class="panel-title">账号信息</h4>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="用户名">{{ info.username || '-' }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ info.realName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ info.role || '-' }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ info.department || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ info.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ info.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最近登录" :span="2">{{ formatTime(info.lastLoginTime) }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="panel">
        <h4 class="panel-title">安全提示</h4>
        <ul class="tips">
          <li>演示账号（admin/admin123 等）建议上线后立即修改密码；</li>
          <li>不要把 token、数据库口令发到聊天工具或提交到仓库；</li>
          <li>离开电脑时请退出登录，避免他人使用当前会话；</li>
          <li>账号权限变更请联系系统管理员。</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { authApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

const userStore = useUserStore()
const router = useRouter()
const refreshing = ref(false)
const info = computed(() => userStore.userInfo || {})
const initial = computed(() => (info.value.realName || info.value.username || '用').charAt(0).toUpperCase())

function formatTime(v: string) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
}

async function refreshProfile() {
  refreshing.value = true
  try {
    const res: any = await authApi.getCurrentUser()
    if (res.data) userStore.setUserInfo(res.data)
    ElMessage.success('资料已刷新')
  } catch (e: any) {
    ElMessage.error(e.message || '刷新失败')
  } finally { refreshing.value = false }
}

async function logout() {
  try {
    await ElMessageBox.confirm('确认退出登录？', '退出确认', { type: 'warning' })
    await authApi.logout().catch(() => {})
    userStore.logout()
    router.push('/login')
  } catch (_e) { /* 取消 */ }
}
</script>

<style scoped>
.profile-grid { display: grid; grid-template-columns: 300px minmax(0, 1fr); gap: 14px; align-items: start; }
.profile-card { text-align: center; padding: 26px 20px; }
.avatar { width: 72px; height: 72px; border-radius: 50%; margin: 0 auto 12px; display: flex; align-items: center; justify-content: center; font-size: 26px; font-weight: 700; color: #fff; background: linear-gradient(135deg, #3b82f6, #0ea5e9); }
.profile-name { font-size: 18px; font-weight: 700; color: var(--med-text); }
.profile-role { color: var(--med-text-light); font-size: 12px; margin-top: 4px; }
.profile-tags { display: flex; gap: 6px; justify-content: center; margin-top: 12px; flex-wrap: wrap; }
.profile-foot { margin-top: 18px; padding-top: 12px; border-top: 1px solid var(--med-border-light); color: var(--med-text-light); font-size: 11px; }
.tips { margin: 0; padding-left: 18px; color: var(--med-text-secondary); font-size: 12.5px; line-height: 2; }
@media (max-width: 900px) { .profile-grid { grid-template-columns: 1fr; } }
</style>
