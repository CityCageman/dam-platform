<template>
  <div class="flg-wrap">
    <div class="flg-toolbar">
      <el-input v-model="keyword" placeholder="搜索字段/表/表达式" clearable size="small" style="width: 220px;" />
      <el-select v-model="sourceTable" placeholder="来源表" clearable filterable size="small" style="width: 180px;">
        <el-option v-for="t in sourceTables" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select v-model="targetTable" placeholder="目标表" clearable filterable size="small" style="width: 180px;">
        <el-option v-for="t in targetTables" :key="t" :label="t" :value="t" />
      </el-select>
      <span class="flg-count">{{ filtered.length }} 条映射</span>
      <el-button size="small" text type="primary" @click="resetFilters">重置</el-button>
    </div>

    <div v-if="filtered.length" class="flg-list">
      <div v-for="group in grouped" :key="group.table" class="flg-group">
        <div class="flg-group-head">
          <span class="flg-group-title">{{ group.table }}</span>
          <span class="flg-group-count">{{ group.items.length }} 条</span>
        </div>
        <div class="flg-grid">
          <div v-for="(f, i) in group.items" :key="i" class="flg-card">
            <div class="flg-side">
              <b :title="sourceTableName(f)">{{ f.sourceColumnName || '-' }}</b>
              <span :title="sourceTableName(f)">{{ sourceTableName(f) }}</span>
            </div>
            <div class="flg-flow">
              <span class="flg-map">{{ mappingLabel(f.mappingTypeId) }}</span>
              <span class="flg-arrow">→</span>
              <span v-if="f.transformExpression" class="flg-expr" :title="f.transformExpression">{{ f.transformExpression }}</span>
              <span v-else class="flg-expr muted">直接映射</span>
            </div>
            <div class="flg-side target">
              <b :title="targetTableName(f)">{{ f.targetColumnName || '-' }}</b>
              <span :title="targetTableName(f)">{{ targetTableName(f) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else description="没有匹配的字段级血缘，请调整筛选条件" :image-size="56" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const props = defineProps<{ fields: any[] }>()

const keyword = ref('')
const sourceTable = ref('')
const targetTable = ref('')

const mappingLabels: Record<string, string> = {
  DIRECT: '直接映射', CALC: '计算映射', JOIN: '关联映射', FILTER: '过滤映射',
  AGGREGATE: '聚合映射', FUNCTION: '函数转换', CASE: '条件判断'
}

function mappingLabel(v: string) {
  return mappingLabels[v] || v || '映射'
}

function sourceTableName(f: any) {
  return f.sourceTableName || (f.sourceTableId ? `表#${f.sourceTableId}` : '未知来源')
}

function targetTableName(f: any) {
  return f.targetTableName || (f.targetTableId ? `表#${f.targetTableId}` : '未知目标')
}

const sourceTables = computed(() => Array.from(new Set((props.fields || []).map(sourceTableName))).sort())
const targetTables = computed(() => Array.from(new Set((props.fields || []).map(targetTableName))).sort())

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return (props.fields || []).filter(f => {
    if (sourceTable.value && sourceTableName(f) !== sourceTable.value) return false
    if (targetTable.value && targetTableName(f) !== targetTable.value) return false
    if (!kw) return true
    return [f.sourceColumnName, f.targetColumnName, sourceTableName(f), targetTableName(f), f.transformExpression]
      .some(v => String(v || '').toLowerCase().includes(kw))
  })
})

const grouped = computed(() => {
  const map = new Map<string, any[]>()
  for (const f of filtered.value) {
    const key = sourceTableName(f)
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(f)
  }
  return Array.from(map.entries()).map(([table, items]) => ({ table, items }))
})

function resetFilters() {
  keyword.value = ''
  sourceTable.value = ''
  targetTable.value = ''
}
</script>

<style scoped>
.flg-wrap { width: 100%; }
.flg-toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.flg-count { margin-left: auto; font-size: 11px; color: var(--med-text-light); }
.flg-list { max-height: 480px; overflow-y: auto; padding-right: 4px; }
.flg-group { margin-bottom: 14px; }
.flg-group-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.flg-group-title { font-size: 12px; font-weight: 600; color: var(--med-text-secondary); font-family: ui-monospace, Menlo, monospace; }
.flg-group-count { font-size: 10px; color: var(--med-text-light); background: #F1F5F9; border-radius: 9px; padding: 1px 7px; }
.flg-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 8px; }
.flg-card { display: grid; grid-template-columns: minmax(90px, 1fr) auto minmax(90px, 1fr); gap: 8px; align-items: center; border: 1px solid var(--med-border); border-radius: 9px; background: #fff; padding: 9px 10px; }
.flg-side { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.flg-side b { font-size: 11.5px; color: var(--med-text); font-family: ui-monospace, Menlo, monospace; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.flg-side span { font-size: 9.5px; color: var(--med-text-light); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.flg-side.target { text-align: right; align-items: flex-end; }
.flg-flow { display: flex; flex-direction: column; align-items: center; gap: 2px; min-width: 70px; }
.flg-map { font-size: 9px; color: #7C3AED; background: #F5F3FF; border-radius: 4px; padding: 1px 5px; white-space: nowrap; }
.flg-arrow { color: #94A3B8; font-size: 14px; line-height: 1; }
.flg-expr { font-size: 9px; color: var(--med-text-light); max-width: 150px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.flg-expr.muted { color: #CBD5E1; }
</style>
