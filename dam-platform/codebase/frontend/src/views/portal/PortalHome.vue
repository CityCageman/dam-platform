<template>
  <div class="portal-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">统一门户</h2>
        <p class="page-desc">按能力域汇聚平台模块，从统一入口快速抵达日常数据工作台；点击能力域将在新窗口打开。</p>
      </div>
      <div class="head-actions">
        <el-button @click="goDashboard">
          <el-icon><DataAnalysis /></el-icon>运营看板
        </el-button>
      </div>
    </div>

    <!-- 门户概览 -->
    <section class="portal-hero">
      <div class="hero-main">
        <div class="hero-eyebrow">DATA ASSET PLATFORM</div>
        <h3>一个入口，连接数据全生命周期</h3>
        <p>从资产、标准、治理到 AI 与平台运维，以能力域组织平台能力，减少跨模块查找成本。</p>
      </div>
      <div class="hero-stats">
        <div class="hero-stat">
          <b>{{ portalDomains.length }}</b>
          <span>能力域</span>
        </div>
        <div class="hero-stat">
          <b>{{ portalModuleTotal }}</b>
          <span>模块</span>
        </div>
        <div class="hero-stat">
          <b>{{ portalAvailableTotal }}</b>
          <span>已上线</span>
        </div>
      </div>
    </section>

    <!-- 8 个能力域 -->
    <section class="domain-grid" aria-label="能力域列表">
      <article
        v-for="domain in portalDomains"
        :key="domain.code"
        class="domain-card"
        role="link"
        tabindex="0"
        :aria-label="`在新窗口进入${domain.name}能力域`"
        @click="enterDomain(domain)"
        @keyup.enter="enterDomain(domain)"
      >
        <div class="domain-top">
          <div class="domain-icon">
            <el-icon :size="24"><component :is="resolvePortalIcon(domain.icon)" /></el-icon>
          </div>
          <el-tag size="small" effect="plain" class="domain-count">{{ domain.modules.length }} 个模块</el-tag>
        </div>

        <h3 class="domain-name">{{ domain.name }}</h3>
        <p class="domain-desc">{{ domain.description }}</p>

        <div class="domain-modules">
          <span
            v-for="module in domain.modules"
            :key="module.code"
            class="module-chip"
            :class="{ 'is-planned': module.status === 'PLANNED' }"
          >
            {{ module.code }}
          </span>
        </div>

        <div class="domain-foot">
          <span class="domain-availability">
            <i class="dot"></i>{{ availableCount(domain) }}/{{ domain.modules.length }} 已上线
          </span>
          <button type="button" class="domain-enter" @click.stop="enterDomain(domain)">
            进入能力域
            <span class="domain-enter-arrow" aria-hidden="true">→</span>
          </button>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { DataAnalysis } from '@element-plus/icons-vue'
import {
  portalAvailableTotal,
  portalDomains,
  portalModuleTotal,
  resolvePortalIcon
} from '@/config/portal'
import type { PortalDomain } from '@/config/portal'

const router = useRouter()

function availableCount(domain: PortalDomain): number {
  return domain.modules.filter(module => module.status === 'AVAILABLE').length
}

function enterDomain(domain: PortalDomain) {
  // 能力域页面在新浏览器窗口打开，当前统一门户保持不变
  const href = router.resolve({ path: domain.route, query: { from: 'portal' } }).href
  window.open(href, '_blank', 'noopener')
}

function goDashboard() {
  router.push('/dashboard')
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

/* ===== 门户概览 ===== */
.portal-hero {
  position: relative;
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 26px 30px;
  margin-bottom: 18px;
  border-radius: 18px;
  overflow: hidden;
  color: #fff;
  background:
    radial-gradient(120% 160% at 100% 0%, rgba(217, 154, 28, 0.28) 0%, transparent 52%),
    linear-gradient(118deg, var(--med-primary-dark, #560e50) 0%, var(--med-primary, #72156a) 52%, var(--med-accent, #2fa6a1) 128%);
  box-shadow: 0 12px 30px rgba(86, 14, 80, 0.18);
}
.portal-hero::after {
  content: '';
  position: absolute;
  right: -56px;
  bottom: -92px;
  width: 240px;
  height: 240px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.16);
  box-shadow: 0 0 0 22px rgba(255, 255, 255, 0.04);
  pointer-events: none;
}
.hero-main {
  position: relative;
  z-index: 1;
  min-width: 0;
  flex: 1;
}
.hero-eyebrow {
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 1.8px;
  color: rgba(255, 255, 255, 0.68);
}
.hero-main h3 {
  margin: 8px 0 7px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.3px;
}
.hero-main p {
  margin: 0;
  max-width: 620px;
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
  min-width: 76px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.16);
  text-align: center;
  backdrop-filter: blur(6px);
}
.hero-stat b {
  display: block;
  font-size: 24px;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.hero-stat span {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.72);
}

/* ===== 能力域卡片 ===== */
.domain-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.domain-card {
  --domain-accent: var(--med-primary, #72156a);
  --domain-soft: rgba(114, 21, 106, 0.08);
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 20px;
  border-radius: 16px;
  border: 1px solid var(--portal-border);
  border-top: 3px solid var(--domain-accent);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
  outline: none;
}
.domain-card:nth-child(8n + 2) {
  --domain-accent: var(--med-accent, #2fa6a1);
  --domain-soft: rgba(47, 166, 161, 0.1);
}
.domain-card:nth-child(8n + 3) {
  --domain-accent: var(--med-gold, #d99a1c);
  --domain-soft: rgba(217, 154, 28, 0.12);
}
.domain-card:nth-child(8n + 4) {
  --domain-accent: var(--med-green, #5bae47);
  --domain-soft: rgba(91, 174, 71, 0.1);
}
.domain-card:nth-child(8n + 5) {
  --domain-accent: var(--med-accent, #2fa6a1);
  --domain-soft: rgba(47, 166, 161, 0.1);
}
.domain-card:nth-child(8n + 6) {
  --domain-accent: var(--med-gold, #d99a1c);
  --domain-soft: rgba(217, 154, 28, 0.12);
}
.domain-card:nth-child(8n + 7) {
  --domain-accent: var(--med-green, #5bae47);
  --domain-soft: rgba(91, 174, 71, 0.1);
}
.domain-card:nth-child(8n + 8) {
  --domain-accent: var(--med-primary-light, #8d2c82);
  --domain-soft: rgba(141, 44, 130, 0.1);
}
.domain-card:hover,
.domain-card:focus-visible {
  transform: translateY(-3px);
  border-color: var(--domain-accent);
  box-shadow: 0 14px 30px rgba(57, 25, 52, 0.12);
}
.domain-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}
.domain-icon {
  width: 46px;
  height: 46px;
  flex: 0 0 46px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--domain-accent);
  background: var(--domain-soft);
}
.domain-count {
  color: var(--portal-text-light);
  border-color: var(--portal-border);
  background: var(--portal-surface-2);
}
.domain-name {
  margin: 15px 0 6px;
  font-size: 16px;
  font-weight: 650;
  line-height: 1.35;
  color: var(--portal-text);
}
.domain-desc {
  margin: 0;
  min-height: 40px;
  font-size: 12px;
  line-height: 1.65;
  color: var(--portal-text-secondary);
}
.domain-modules {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 14px;
}
.module-chip {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.3px;
  color: var(--domain-accent);
  background: var(--domain-soft);
}
.module-chip.is-planned {
  color: var(--portal-text-light);
  background: var(--portal-surface-2);
  border: 1px dashed var(--portal-border);
}
.domain-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--portal-border-light);
}
.domain-availability {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--portal-text-light);
}
.domain-availability .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--med-green, #5bae47);
}
.domain-enter {
  position: relative;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex: 0 0 auto;
  min-width: 84px;
  padding: 6px 8px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--portal-primary);
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
}
.domain-enter:hover { background: var(--domain-soft); }
.domain-enter-arrow { font-size: 14px; line-height: 1; }

@media (max-width: 1100px) {
  .portal-hero {
    align-items: flex-start;
    flex-direction: column;
    gap: 18px;
  }
  .hero-stats {
    width: 100%;
  }
  .hero-stat {
    flex: 1;
  }
}
@media (max-width: 640px) {
  .portal-hero {
    padding: 20px;
  }
  .hero-main h3 {
    font-size: 19px;
  }
  .domain-grid {
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
