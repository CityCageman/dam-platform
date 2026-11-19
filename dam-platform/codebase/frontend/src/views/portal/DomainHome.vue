<template>
  <div class="portal-page">
    <div class="page-head">
      <div>
        <el-button text class="back-link" @click="goPortal">
          <el-icon><ArrowLeft /></el-icon>统一门户
        </el-button>
        <h2 class="page-title">{{ domain ? domain.name : '能力域' }}</h2>
        <p class="page-desc">
          {{ domain ? domain.description : '未找到对应的能力域，请返回统一门户重新选择。' }}
        </p>
      </div>
      <div v-if="domain" class="head-actions">
        <el-tag size="small" effect="plain" type="success">{{ availableCount }} 个已上线</el-tag>
        <el-tag v-if="plannedCount" size="small" effect="plain" type="info">{{ plannedCount }} 个规划中</el-tag>
        <el-button @click="goPortal">
          <el-icon><Back /></el-icon>返回统一门户
        </el-button>
      </div>
    </div>

    <template v-if="domain">
      <!-- 能力域概览 -->
      <section class="domain-hero">
        <div class="domain-hero-icon">
          <el-icon :size="30"><component :is="resolvePortalIcon(domain.icon)" /></el-icon>
        </div>
        <div class="hero-main">
          <div class="hero-eyebrow">CAPABILITY DOMAIN · {{ domain.code.toUpperCase() }}</div>
          <h3>{{ domain.name }}</h3>
          <p>{{ domain.description }}</p>
        </div>
        <div class="hero-stats">
          <div class="hero-stat">
            <b>{{ domain.modules.length }}</b>
            <span>模块总数</span>
          </div>
          <div class="hero-stat">
            <b>{{ availableCount }}</b>
            <span>已上线</span>
          </div>
          <div class="hero-stat">
            <b>{{ plannedCount }}</b>
            <span>规划中</span>
          </div>
        </div>
      </section>

      <div class="module-head">
        <h3 class="module-title">能力域模块</h3>
        <span class="module-sub">已上线模块可直接进入，规划中模块暂不开放</span>
      </div>

      <section class="module-grid" aria-label="能力域模块列表">
        <article
          v-for="module in domain.modules"
          :key="module.code"
          class="module-card"
          :class="{ 'is-planned': module.status === 'PLANNED' }"
          :aria-disabled="module.status === 'PLANNED'"
        >
          <div class="module-top">
            <div class="module-icon">
              <el-icon :size="21"><component :is="resolvePortalIcon(module.icon)" /></el-icon>
            </div>
            <span class="module-code">{{ module.code }}</span>
            <el-tag
              size="small"
              effect="plain"
              :type="module.status === 'AVAILABLE' ? 'success' : 'info'"
              class="module-status"
            >
              {{ module.status === 'AVAILABLE' ? '已上线' : '规划中' }}
            </el-tag>
          </div>

          <h4 class="module-name">{{ module.name }}</h4>
          <p class="module-desc">{{ module.description }}</p>

          <div class="module-foot">
            <span class="module-target">
              <el-icon><component :is="module.status === 'AVAILABLE' ? Link : Clock" /></el-icon>
              {{ module.status === 'AVAILABLE' ? module.route : '即将开放' }}
            </span>
            <el-button
              :type="module.status === 'AVAILABLE' ? 'primary' : 'info'"
              :disabled="module.status === 'PLANNED'"
              text
              @click="enterModule(module)"
            >
              {{ module.status === 'AVAILABLE' ? '进入模块' : '规划中' }}
              <el-icon v-if="module.status === 'AVAILABLE'" class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
          </div>
        </article>
      </section>
    </template>

    <el-result
      v-else
      icon="warning"
      title="能力域不存在"
      sub-title="请从统一门户选择有效的能力域。"
    >
      <template #extra>
        <el-button type="primary" @click="goPortal">返回统一门户</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Back, Clock, Link } from '@element-plus/icons-vue'
import { getPortalDomain, resolvePortalIcon } from '@/config/portal'
import type { PortalModule } from '@/config/portal'

const route = useRoute()
const router = useRouter()

const domain = computed(() => getPortalDomain(String(route.params.domainCode || '')))
const availableCount = computed(
  () => domain.value?.modules.filter(module => module.status === 'AVAILABLE').length ?? 0
)
const plannedCount = computed(
  () => domain.value?.modules.filter(module => module.status === 'PLANNED').length ?? 0
)

function enterModule(module: PortalModule) {
  if (module.status !== 'AVAILABLE') return
  router.push({ path: module.route, query: route.query })
}

function goPortal() {
  // 从统一门户卡片打开的能力域窗口：关闭当前窗口，回到原来的统一门户。
  if (route.query.from === 'portal' && window.opener !== undefined) {
    window.close()
    // 浏览器不允许脚本关闭时，退化为当前窗口返回门户，避免停在空白页。
    window.setTimeout(() => {
      if (!window.closed) router.push('/portal')
    }, 120)
    return
  }
  router.push('/portal')
}
</script>

<style scoped>
.portal-page {
  --portal-surface: var(--med-card-bg, #ffffff);
  --portal-surface-2: var(--med-bg-level2, #fcf9fc);
  --portal-border: var(--med-border, #eae2ea);
  --portal-border-light: var(--med-border-light, #f3edf3);
  --portal-text: var(--med-text, #2b2530);
  --portal-text-secondary: var(--med-text-secondary, #655d68);
  --portal-text-light: var(--med-text-light, #9b929f);
  --portal-primary: var(--med-primary, #72156a);
  --portal-accent: var(--med-accent, #2fa6a1);
  --portal-shadow: 0 3px 14px rgba(57, 25, 52, 0.05);
}

.back-link {
  padding: 0;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--portal-text-light);
}
.back-link:hover {
  color: var(--portal-primary);
}

/* ===== 能力域概览 ===== */
.domain-hero {
  position: relative;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px 28px;
  margin-bottom: 20px;
  border-radius: 18px;
  overflow: hidden;
  color: #fff;
  background:
    radial-gradient(110% 150% at 100% 0%, rgba(217, 154, 28, 0.24) 0%, transparent 54%),
    linear-gradient(118deg, var(--med-primary-dark, #560e50) 0%, var(--med-primary, #72156a) 54%, var(--med-accent, #2fa6a1) 132%);
  box-shadow: 0 12px 30px rgba(86, 14, 80, 0.18);
}
.domain-hero::after {
  content: '';
  position: absolute;
  right: -48px;
  bottom: -104px;
  width: 230px;
  height: 230px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.16);
  box-shadow: 0 0 0 22px rgba(255, 255, 255, 0.04);
  pointer-events: none;
}
.domain-hero-icon {
  position: relative;
  z-index: 1;
  width: 62px;
  height: 62px;
  flex: 0 0 62px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(6px);
}
.hero-main {
  position: relative;
  z-index: 1;
  flex: 1;
  min-width: 0;
}
.hero-eyebrow {
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 1.5px;
  color: rgba(255, 255, 255, 0.68);
}
.hero-main h3 {
  margin: 7px 0 6px;
  font-size: 21px;
  font-weight: 700;
  letter-spacing: 0.3px;
}
.hero-main p {
  margin: 0;
  max-width: 640px;
  font-size: 12.5px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.8);
}
.hero-stats {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 10px;
  flex: 0 0 auto;
}
.hero-stat {
  min-width: 74px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.16);
  text-align: center;
  backdrop-filter: blur(6px);
}
.hero-stat b {
  display: block;
  font-size: 23px;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.hero-stat span {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.72);
}

/* ===== 模块区 ===== */
.module-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}
.module-title {
  margin: 0;
  padding-left: 10px;
  border-left: 3px solid var(--med-primary, #72156a);
  font-size: 15px;
  font-weight: 650;
  line-height: 1.3;
  color: var(--portal-text);
}
.module-sub {
  font-size: 11.5px;
  color: var(--portal-text-light);
}
.module-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(270px, 1fr));
  gap: 14px;
}
.module-card {
  display: flex;
  flex-direction: column;
  padding: 18px;
  border-radius: 15px;
  border: 1px solid var(--portal-border);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}
.module-card:not(.is-planned):hover {
  transform: translateY(-2px);
  border-color: var(--med-primary-soft, #e9d2e6);
  box-shadow: 0 12px 26px rgba(57, 25, 52, 0.1);
}
.module-card.is-planned {
  background: var(--portal-surface-2);
  box-shadow: none;
  border-style: dashed;
}
.module-top {
  display: flex;
  align-items: center;
  gap: 9px;
}
.module-icon {
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  border-radius: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--med-primary, #72156a);
  background: var(--med-primary-lighter, #f7edf6);
}
.module-card.is-planned .module-icon {
  color: var(--portal-text-light);
  background: var(--portal-surface);
}
.module-code {
  padding: 2px 7px;
  border-radius: 5px;
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: var(--portal-text-secondary);
  background: var(--portal-surface-2);
  border: 1px solid var(--portal-border-light);
}
.module-status {
  margin-left: auto;
}
.module-name {
  margin: 13px 0 5px;
  font-size: 14.5px;
  font-weight: 650;
  color: var(--portal-text);
}
.module-desc {
  margin: 0;
  flex: 1;
  min-height: 38px;
  font-size: 12px;
  line-height: 1.65;
  color: var(--portal-text-secondary);
}
.module-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 15px;
  padding-top: 12px;
  border-top: 1px solid var(--portal-border-light);
}
.module-target {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  font-size: 10.5px;
  color: var(--portal-text-light);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.module-target .el-icon {
  flex: 0 0 auto;
}
.module-foot :deep(.el-button) {
  padding: 4px 2px;
  font-size: 12px;
  flex: 0 0 auto;
}
.module-card.is-planned .module-foot :deep(.el-button) {
  color: var(--portal-text-light);
}

@media (max-width: 960px) {
  .domain-hero {
    align-items: flex-start;
    flex-wrap: wrap;
    gap: 16px;
  }
  .hero-stats {
    width: 100%;
  }
  .hero-stat {
    flex: 1;
  }
}
@media (max-width: 640px) {
  .domain-hero {
    padding: 20px;
  }
  .domain-hero-icon {
    width: 52px;
    height: 52px;
    flex-basis: 52px;
  }
  .hero-main h3 {
    font-size: 18px;
  }
  .hero-main {
    flex-basis: calc(100% - 70px);
  }
  .module-grid {
    grid-template-columns: 1fr;
  }
}
</style>

<style>
/* 深浅色变量适配：由全局主题在 html.dark 下覆盖，未启用深色时沿用医疗紫色浅色主题 */
html.dark .portal-page {
  --portal-surface: #221b24;
  --portal-surface-2: #1b151d;
  --portal-border: rgba(255, 255, 255, 0.09);
  --portal-border-light: rgba(255, 255, 255, 0.06);
  --portal-text: #f2eaf1;
  --portal-text-secondary: #c4b6c2;
  --portal-text-light: #968797;
  --portal-shadow: 0 3px 16px rgba(0, 0, 0, 0.28);
}
</style>
