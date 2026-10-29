<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">用户管理</h2>
        <p class="page-desc">平台账号与角色分配：管理员（全部权限）、数据管家（治理 + 服务配置）、数据分析师（只读）。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openForm()">新增用户</el-button>
    </div>

    <div class="panel">
      <el-table :data="users" size="small">
        <el-table-column label="用户名" width="140">
          <template #default="{ row }">
            <span class="mono">{{ row.username }}</span>
            <el-tag v-if="auth.user?.id === row.id" size="small" style="margin-left:6px">我</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="displayName" label="姓名" width="120" />
        <el-table-column label="角色" width="130">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :color="ROLE_META[row.role]?.color + '18'"
                    :style="{ color: ROLE_META[row.role]?.color, borderColor: ROLE_META[row.role]?.color }">
              {{ ROLE_META[row.role]?.name || row.role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="部门" width="150" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近登录" width="150">
          <template #default="{ row }">{{ row.lastLoginAt ? fmtTime(row.lastLoginAt) : '从未登录' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openForm(row)">编辑</el-button>
            <el-button size="small" text type="warning" @click="resetPwd(row)">重置密码</el-button>
            <el-button size="small" text :type="row.status === 'ACTIVE' ? 'warning' : 'success'" @click="toggle(row)">
              {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑用户' : '新增用户'" width="480px">
      <el-form :model="form" label-width="86px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="初始密码" v-if="!form.id">
          <el-input v-model="form.password" placeholder="留空默认 123456" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width:100%">
            <el-option v-for="(m, r) in ROLE_META" :key="r" :label="m.name" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="form.department" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { api, fmtTime } from '../api'
import { auth, ROLE_META } from '../store'

const users = ref([])
const dialog = ref(false)
const form = reactive({ id: null, username: '', password: '', displayName: '', role: 'ANALYST', department: '' })

async function load() {
  users.value = await api.users()
}

function openForm(row) {
  if (row) Object.assign(form, { ...row, password: '' })
  else Object.assign(form, { id: null, username: '', password: '', displayName: '', role: 'ANALYST', department: '' })
  dialog.value = true
}

async function save() {
  if (!form.username) return ElMessage.warning('用户名必填')
  const payload = { ...form }
  delete payload.id
  delete payload.lastLoginAt
  delete payload.createdAt
  delete payload.status
  if (form.id) {
    delete payload.password
    await api.updateUser(form.id, payload)
  } else {
    await api.createUser(payload)
  }
  ElMessage.success('已保存')
  dialog.value = false
  load()
}

async function resetPwd(row) {
  const { value } = await ElMessageBox.prompt(`重置 ${row.username} 的密码`, '提示', { inputValue: '123456' })
  await api.updateUser(row.id, { password: value || '123456' })
  ElMessage.success('密码已重置')
}

async function toggle(row) {
  await api.updateUser(row.id, { status: row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' })
  ElMessage.success('已更新')
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除用户 ${row.username}？`, '提示', { type: 'warning' })
  await api.deleteUser(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 14px; }
.page-title { margin: 0 0 6px; font-size: 19px; }
.page-desc { margin: 0; color: #8a94a6; font-size: 12.5px; }
.panel { background: #fff; border: 1px solid #e9edf5; border-radius: 12px; padding: 14px 16px; }
.mono { font-family: monospace; }
</style>
