<template>
  <div class="ck-page" v-loading="loading">
    <div class="ck-head">
      <div>
        <h2 class="ck-title">{{ title }}</h2>
        <p v-if="description" class="ck-desc">{{ description }}</p>
        <slot name="meta" />
      </div>
      <div class="ck-head-actions">
        <slot name="actions" />
        <el-button :icon="Refresh" size="small" @click="emit('refresh')">刷新</el-button>
      </div>
    </div>
    <slot />
    <p v-if="updatedAt" class="ck-updated">数据更新时间：{{ updatedAt }}</p>
  </div>
</template>

<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'

withDefaults(defineProps<{
  title: string
  description?: string
  loading?: boolean
  updatedAt?: string
}>(), {
  description: '',
  loading: false,
  updatedAt: ''
})

const emit = defineEmits<{ (event: 'refresh'): void }>()
</script>

