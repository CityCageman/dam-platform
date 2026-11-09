<template>
  <div class="login-wrap">
    <!-- 左侧品牌区：深紫 + 四色光晕 -->
    <div class="login-left">
      <div class="login-orbs">
        <span class="orb orb-purple"></span>
        <span class="orb orb-teal"></span>
        <span class="orb orb-gold"></span>
        <span class="orb orb-green"></span>
      </div>

      <div class="brand-kicker">HOSPITAL DATA GOVERNANCE</div>
      <div class="logo-row">
        <div class="logo-mark">
          <span class="lm-dot d1"></span><span class="lm-dot d2"></span>
          <span class="lm-dot d3"></span><span class="lm-dot d4"></span>
        </div>
        <div>
          <div class="logo-title">数据资产管理平台</div>
          <div class="logo-sub">DATA ASSET PLATFORM</div>
        </div>
      </div>

      <h1 class="slogan">让医院的数据资产<br /><span>看得见</span>、<span>管得住</span>、<span>用得好</span></h1>

      <ul class="features">
        <li><i class="f-dot purple"></i><div><b>资产可见</b><span>元数据采集 · 资产目录 · 数据血缘</span></div></li>
        <li><i class="f-dot teal"></i><div><b>治理可控</b><span>数据标准 · 质量稽核 · 安全分级</span></div></li>
        <li><i class="f-dot gold"></i><div><b>智能可用</b><span>数据服务 · AI 报表 · 指标与知识库</span></div></li>
      </ul>

      <div class="trust-row">
        <span><i class="f-dot green"></i>内网部署</span>
        <span><i class="f-dot purple"></i>统一治理</span>
        <span><i class="f-dot teal"></i>AI 增强</span>
      </div>
    </div>

    <!-- 右侧登录区 -->
    <div class="login-right">
      <div class="login-glow"></div>
      <div class="login-card">
        <div class="card-kicker">WELCOME BACK</div>
        <h2 class="card-title">登录</h2>
        <p class="card-sub">请使用平台账号登录</p>
        <el-form :model="form" :rules="rules" ref="formRef" @keyup.enter="handleLogin">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" size="large" :prefix-icon="User" autocomplete="username" autofocus />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码" size="large"
                      :prefix-icon="Lock" show-password autocomplete="current-password" />
          </el-form-item>
          <el-button class="submit" type="primary" size="large" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form>
        <div class="login-foot">内网部署 · 数据资产管理平台 v1.0.0</div>
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
  grid-template-columns: 1.15fr 1fr;
  background: #F7F2F7;
}

.login-left {
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(130% 100% at 0% 0%, #5B1354 0%, #3A0A36 50%, #250622 100%);
  color: #fff;
  padding: 58px 68px;
  display: flex;
  flex-direction: column;
}

.login-left::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(255,255,255,.035) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.035) 1px, transparent 1px);
  background-size: 52px 52px;
  mask-image: linear-gradient(180deg, rgba(0,0,0,.65), transparent 82%);
  pointer-events: none;
}

.login-orbs .orb {
  position: absolute;
  border-radius: 999px;
  filter: blur(8px);
  opacity: .5;
  pointer-events: none;
}
.orb-purple { width: 280px; height: 280px; right: -90px; top: -70px; background: #A33394; }
.orb-teal { width: 180px; height: 180px; right: 80px; bottom: 90px; background: #2FA6A1; opacity: .22; }
.orb-gold { width: 120px; height: 120px; left: 58%; top: 32%; background: #D99A1C; opacity: .16; }
.orb-green { width: 150px; height: 150px; left: -60px; bottom: -50px; background: #5BAE47; opacity: .18; }

.brand-kicker {
  position: relative;
  z-index: 1;
  font-size: 10px;
  letter-spacing: 2.6px;
  color: #C9A5C4;
  margin-bottom: 34px;
}

.logo-row { position: relative; z-index: 1; display: flex; align-items: center; gap: 13px; }

.logo-mark {
  width: 46px; height: 46px; border-radius: 13px;
  background: rgba(255,255,255,.1);
  border: 1px solid rgba(255,255,255,.18);
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 3px;
  padding: 10px;
  box-shadow: 0 10px 26px rgba(0,0,0,.22);
}
.lm-dot { border-radius: 5px; display: block; }
.lm-dot.d1 { background: #B33AA4; }
.lm-dot.d2 { background: #2FA6A1; }
.lm-dot.d3 { background: #D99A1C; }
.lm-dot.d4 { background: #5BAE47; }

.logo-title { font-size: 17px; font-weight: 600; letter-spacing: .3px; }
.logo-sub { font-size: 10px; color: #C7A3C2; letter-spacing: 2.2px; margin-top: 3px; }

.slogan {
  position: relative;
  z-index: 1;
  margin-top: 11vh;
  font-size: 37px;
  line-height: 1.55;
  font-weight: 700;
  letter-spacing: .8px;
}
.slogan span { color: #E8B94F; }
.slogan span:nth-child(2) { color: #62C3BE; }
.slogan span:nth-child(3) { color: #8CCB77; }

.features {
  position: relative;
  z-index: 1;
  margin-top: 34px;
  list-style: none;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 13px;
  color: #CDB5C8;
}
.features li { display: flex; align-items: flex-start; gap: 10px; }
.features b { display: block; color: #F5EAF3; font-size: 13px; font-weight: 500; margin-bottom: 2px; }
.features span { font-size: 12px; line-height: 1.5; color: #B99AB4; }
.f-dot { width: 7px; height: 7px; border-radius: 50%; display: inline-block; flex: 0 0 7px; margin-top: 5px; }
.f-dot.purple { background: #B33AA4; }
.f-dot.teal { background: #2FA6A1; }
.f-dot.gold { background: #D99A1C; }
.f-dot.green { background: #5BAE47; }

.trust-row {
  position: relative;
  z-index: 1;
  margin-top: auto;
  display: flex;
  gap: 18px;
  padding-top: 28px;
  color: #B99AB4;
  font-size: 10.5px;
}
.trust-row span { display: inline-flex; align-items: center; gap: 6px; }
.trust-row .f-dot { margin-top: 0; width: 6px; height: 6px; flex-basis: 6px; }

.login-right {
  position: relative;
  display: flex; align-items: center; justify-content: center;
  background: #F7F2F7;
  overflow: hidden;
}
.login-glow {
  position: absolute;
  width: 520px; height: 520px; border-radius: 50%;
  background: radial-gradient(circle, rgba(114,21,106,.10), rgba(47,166,161,.06) 45%, transparent 70%);
  top: 50%; left: 50%; transform: translate(-50%, -50%);
}
.login-card {
  position: relative;
  width: 390px;
  background: rgba(255,255,255,.94);
  border: 1px solid #EEE3EC;
  border-radius: 20px;
  padding: 34px 34px 28px;
  box-shadow: 0 24px 70px rgba(74,13,69,.13);
  backdrop-filter: blur(14px);
}
.login-card::before {
  content: '';
  position: absolute; left: 34px; right: 34px; top: 0; height: 3px;
  border-radius: 0 0 4px 4px;
  background: linear-gradient(90deg, #72156A, #2FA6A1 55%, #D99A1C 80%, #5BAE47);
}
.card-kicker { font-size: 9px; letter-spacing: 2.4px; color: var(--med-text-light); margin-bottom: 12px; }
.card-title { margin: 0; font-size: 25px; color: var(--med-text); letter-spacing: .5px; }
.card-sub { color: #8A7F8C; font-size: 13px; margin: 7px 0 24px; }
.submit { width: 100%; margin-top: 4px; font-size: 15px; letter-spacing: 6px; height: 44px; }
.login-foot { margin-top: 18px; color: var(--med-text-light); font-size: 10.5px; text-align: center; letter-spacing: .3px; }
.demo-accounts { margin-top: 24px; border-top: 1px dashed #EADDE8; padding-top: 14px; }
.demo-title { font-size: 10.5px; color: #A095A2; margin-bottom: 7px; }
.demo-row { display: flex; align-items: center; gap: 10px; padding: 6px 8px; margin: 2px -8px; border-radius: 8px; cursor: pointer; font-size: 12px; color: #4E4552; }
.demo-row:hover { background: #FAF3F9; }
.demo-role { font-size: 10.5px; border: 1px solid; border-radius: 4px; padding: 1px 6px; }
.mono { font-family: 'JetBrains Mono', Consolas, 'Courier New', monospace; }

@media (max-width: 900px) {
  .login-wrap { grid-template-columns: 1fr; }
  .login-left { display: none; }
}
</style>