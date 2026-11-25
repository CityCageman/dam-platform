<template>
  <div v-if="items.length" class="ck-bars">
    <div v-for="item in items" :key="item.label" class="ck-bar-row">
      <span class="ck-bar-label" :style="{ width: labelWidth, flexBasis: labelWidth }" :title="item.label">{{ item.label }}</span>
      <div class="ck-bar-track">
        <i class="ck-bar-fill" :style="{ width: widthOf(item.value) }"></i>
      </div>
      <span class="ck-bar-value">{{ item.value }}</span>
    </div>
  </div>
  <div v-else class="ck-empty-block">
    <el-empty :description="emptyText" :image-size="50" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface MiniBarItem {
  label: string
  value: number
}

const props = withDefaults(defineProps<{
  items?: MiniBarItem[]
  emptyText?: string
  labelWidth?: string
}>(), {
  items: () => [],
  emptyText: '暂无数据',
  labelWidth: '54px'
})

const max = computed(() => Math.max(...props.items.map(item => Number(item.value) || 0), 1))

function widthOf(value: number): string {
  const safe = Math.max(0, Number(value) || 0)
  return `${Math.max(safe > 0 ? 4 : 0, Math.round((safe / max.value) * 100))}%`
}
</script>
