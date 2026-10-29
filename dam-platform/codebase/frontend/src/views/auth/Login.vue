<template>
  <div class="login-wrap">
    <!-- 左侧品牌区：深色渐变 -->
    <div class="login-left">
      <div class="logo-row">
        <div class="logo-mark">DA</div>
        <div>
          <div class="logo-title">数据资产管理平台</div>
          <div class="logo-sub">DATA ASSET PLATFORM</div>
        </div>
      </div>
      <h1 class="slogan">让医院的数据资产<br />看得见、管得住、用得好</h1>
      <ul class="features">
        <li>元数据采集 · 资产目录 · 数据血缘</li>
        <li>数据标准 · 质量稽核 · 安全分级</li>
        <li>数据服务 API 网关 · 定时调度 · 动态脱敏</li>
      </ul>
    </div>

    <!-- 右侧登录区 -->
    <div class="login-right">
      <div class="login-card">
        <h2 class="card-title">登录</h2>
        <p class="card-sub">请使用平台账号登录</p>
        <el-form :model="form" :rules="rules" ref="formRef" @keyup.enter="handleLogin">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" size="large" :prefix-icon="User" autofocus />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码" size="large"
                      :prefix-icon="Lock" show-password />
          </el-form-item>
          <el-button class="submit" type="primary" size="large" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form>
        <div class="demo-accounts">
          <div class="demo-title">演示账号（点击填充）</div>
          <div class="demo-row" v-for="d in demoAccounts" :key="d.u" @click="fill(d)">
            <span class="demo-role" :style="{ color: d.color, borderColor: d.color }">{{ d.role }}</span>
            <span class="mono">{{ d.u }} / {{ d.p }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { authApi } from '@/api'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { encryptPassword } from '@/utils/crypto'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: 'admin', password: 'admin123' })

const demoAccounts = [
  { u: 'admin', p: 'admin123', role: '数据管理员', color: '#F5222D' },
  { u: 'steward', p: 'steward123', role: '数据管家', color: '#FA8C16' },
  { u: 'analyst', p: 'analyst123', role: '数据分析师', color: '#1677FF' }
]

function fill(d: any) {
  form.username = d.u
  form.password = d.p
}

const rules = {
  username: [{ required: true, message: '请输入用户名' }],
  password: [{ required: true, message: '请输入密码' }]
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate()
  loading.value = true
  try {
    // 登录密码使用 auth 服务 RSA 公钥加密后传输
    const res: any = await authApi.login({
      username: form.username,
      password: await encryptPassword(form.password, 'auth')
    })
    userStore.setToken(res.data.token)
    userStore.setUserInfo(res.data.userInfo)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e: any) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  background: #0c1a30;
}

.login-left {
  background: radial-gradient(130% 100% at 0% 0%, #1d4b8f 0%, #10294c 55%, #0c1a30 100%);
  color: #fff;
  padding: 64px 72px;
  display: flex;
  flex-direction: column;
}

.logo-row { display: flex; align-items: center; gap: 12px; }

.logo-mark {
  width: 44px; height: 44px; border-radius: 12px;
  background: linear-gradient(135deg, #2f7bff, #16c2c2);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-weight: 700; font-size: 15px;
  box-shadow: 0 8px 20px rgba(47, 123, 255, .4);
}

.logo-title { font-size: 16px; font-weight: 600; }
.logo-sub { font-size: 10.5px; color: #7f97bb; letter-spacing: 2px; margin-top: 2px; }

.slogan {
  margin-top: 12vh;
  font-size: 34px;
  line-height: 1.5;
  font-weight: 700;
  letter-spacing: 1px;
}

.features { margin-top: 28px; list-style: none; padding: 0; color: #9fb4d4; font-size: 13.5px; line-height: 2.2; }

.login-right {
  display: flex; align-items: center; justify-content: center;
  background: #f4f6fa;
}

.login-card {
  width: 380px;
  background: #fff;
  border-radius: 16px;
  padding: 36px 34px 30px;
  box-shadow: 0 18px 48px rgba(16, 41, 76, .12);
}

.card-title { margin: 0; font-size: 22px; color: var(--med-text); }
.card-sub { color: #8a94a6; font-size: 13px; margin: 6px 0 22px; }

.submit { width: 100%; margin-top: 4px; font-size: 15px; letter-spacing: 6px; }

.demo-accounts {
  margin-top: 26px;
  border-top: 1px dashed #e8ecf3;
  padding-top: 16px;
}

.demo-title { font-size: 11.5px; color: #98a2b3; margin-bottom: 8px; }

.demo-row {
  display: flex; align-items: center; gap: 10px;
  padding: 6px 8px; margin: 2px -8px; border-radius: 8px;
  cursor: pointer; font-size: 12.5px; color: #34405a;
}

.demo-row:hover { background: #f4f6fa; }

.demo-role {
  font-size: 11px; border: 1px solid; border-radius: 4px; padding: 1px 6px;
}

.mono { font-family: 'JetBrains Mono', Consolas, 'Courier New', monospace; }

@media (max-width: 900px) {
  .login-wrap { grid-template-columns: 1fr; }
  .login-left { display: none; }
}
</style>
