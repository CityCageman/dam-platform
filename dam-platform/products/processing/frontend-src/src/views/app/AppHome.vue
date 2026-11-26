<template>
  <div class="app-home">
    <div class="page-head">
      <div>
        <h2 class="page-title">{{ title }}</h2>
        <p class="page-desc">数据应用产品的统一入口，应用开发与市场能力将在此逐步开放。</p>
      </div>
      <el-tag type="success" effect="plain">产品独立部署</el-tag>
    </div>
    <div class="cards">
      <el-card shadow="never">
        <template #header>应用开发</template>
        <p>低代码/配置化构建数据应用。</p>
        <el-button type="primary" plain disabled>即将开放</el-button>
      </el-card>
      <el-card shadow="never">
        <template #header>应用市场</template>
        <p>上架、订阅和复用数据应用。</p>
        <el-button type="primary" plain disabled>即将开放</el-button>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { productTitle } from '@/config/product'
const title = productTitle()
</script>

<style scoped>
.app-home { padding: 4px 0; }
.cards { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
@media (max-width: 900px) { .cards { grid-template-columns: 1fr; } }
</style>
