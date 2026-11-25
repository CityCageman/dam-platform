<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">知识中心</h2>
        <p class="page-desc">沉淀数据标准、指标口径与治理经验，形成可检索、可同步的组织知识资产。</p>
      </div>
      <div class="head-actions">
        <el-tag size="small" effect="plain" type="info">知识库统一管理</el-tag>
      </div>
    </div>

    <KnowledgeBase />
  </div>
</template>

<script setup lang="ts">
import KnowledgeBase from '@/components/knowledge/KnowledgeBase.vue'
</script>
