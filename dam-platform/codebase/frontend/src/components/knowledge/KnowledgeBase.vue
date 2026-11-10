<template>
  <div class="kb-wrap">
    <aside class="kb-side panel">
      <div class="kb-side-head">
        <span>知识分类</span>
        <el-button text type="primary" size="small" @click="openCategory()">+ 新增</el-button>
      </div>
      <div class="kb-cat-list">
        <div v-for="c in categories" :key="c.id" class="kb-cat" :class="{ active: selectedCategoryId === c.id }" @click="selectCategory(c.id)">
          <div class="kb-cat-main">
            <b>{{ c.categoryName }} <em class="kb-cat-count">{{ categoryCount(c.id) }}</em></b>
            <span>{{ c.description || c.categoryCode }}</span>
          </div>
          <div class="kb-cat-actions" @click.stop>
            <el-button text size="small" @click="openCategory(c)">编辑</el-button>
            <el-button text size="small" type="danger" @click="removeCategory(c)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="!categories.length" description="暂无分类" :image-size="46" />
      </div>
    </aside>

    <section class="kb-main panel">
      <div class="kb-overview">
        <div class="kb-stat"><span>知识总数</span><b>{{ stats.total || 0 }}</b><em>可维护条目</em></div>
        <div class="kb-stat pending"><span>待索引</span><b>{{ stats.pending || 0 }}</b><em>等待向量化</em></div>
        <div class="kb-stat synced"><span>已索引</span><b>{{ stats.synced || 0 }}</b><em>向量库可用</em></div>
        <div class="kb-stat failed"><span>索引失败</span><b>{{ stats.failed || 0 }}</b><em>可重新索引</em></div>
      </div>
      <div class="kb-toolbar">
        <div>
          <h3 class="kb-title">{{ currentCategory?.categoryName || '全部知识' }}
            <el-tag v-if="external.externalKbId" size="small" type="success" style="margin-left: 8px;">Agent 知识库已连接</el-tag>
            <el-tag v-else-if="external.configured" size="small" type="info" style="margin-left: 8px;">Agent 知识库待迁移</el-tag>
            <el-tag v-else size="small" type="warning" style="margin-left: 8px;">Agent 知识库未配置</el-tag>
          </h3>
          <p class="kb-desc">{{ currentCategory?.description || '可维护的知识条目，支持从指标、元数据、标准选择性同步。' }}</p>
        </div>
        <div class="kb-actions">
          <el-input v-model="keyword" placeholder="搜索标题/内容" clearable style="width: 190px;" @keyup.enter="loadEntries" />
          <el-select v-model="sourceFilter" placeholder="来源" clearable style="width: 110px;" @change="loadEntries">
            <el-option label="手工" value="MANUAL" />
            <el-option label="元数据" value="METADATA" />
            <el-option label="指标" value="METRIC" />
            <el-option label="标准" value="STANDARD" />
          </el-select>
          <el-button type="primary" @click="openEntry()">新增知识</el-button>
          <el-button @click="openSync">从其他模块同步</el-button>
          <el-button type="success" plain :loading="migrating" :disabled="!external.configured" @click="migrateExternal">
            迁移到 Agent 知识库
          </el-button>
        </div>
      </div>

      <el-table :data="entries" stripe v-loading="loading">
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="来源" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="sourceTag(row.sourceType)">{{ sourceLabel(row.sourceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="向量" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="vectorTag(row.embeddingStatus)">{{ vectorLabel(row.embeddingStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Agent 同步" width="105">
          <template #default="{ row }">
            <el-tag size="small" :type="syncTag(row.syncStatus)">{{ syncLabel(row.syncStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="150"><template #default="{ row }">{{ formatTime(row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="primary" @click="openEntry(row)">编辑</el-button>
            <el-button text size="small" @click="reindex(row)">重新索引</el-button>
            <el-button text size="small" type="danger" @click="removeEntry(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !entries.length" description="这个分类还没有知识，可手工新增或从其他模块同步" :image-size="60" />
    </section>

    <!-- 分类 -->
    <el-dialog v-model="showCategory" :title="categoryForm.id ? '编辑知识分类' : '新增知识分类'" width="480px">
      <el-form :model="categoryForm" label-width="90px">
        <el-form-item label="分类编码" required><el-input v-model="categoryForm.categoryCode" placeholder="如 DOMAIN_GLOSSARY" /></el-form-item>
        <el-form-item label="分类名称" required><el-input v-model="categoryForm.categoryName" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="categoryForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="categoryForm.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="categoryForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showCategory = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveCategory">保存</el-button></template>
    </el-dialog>

    <!-- 条目 -->
    <el-dialog v-model="showEntry" :title="entryForm.id ? '编辑知识' : '新增知识'" width="680px" top="8vh">
      <el-form :model="entryForm" label-width="80px">
        <el-form-item label="分类" required>
          <el-select v-model="entryForm.categoryId" style="width: 100%;">
            <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required><el-input v-model="entryForm.title" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="entryForm.content" type="textarea" :rows="8" placeholder="业务定义、口径、字段映射、案例等" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="entryForm.tagsText" placeholder="逗号分隔" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showEntry = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveEntry">保存</el-button></template>
    </el-dialog>

    <!-- 同步 -->
    <el-dialog v-model="showSync" title="从其他模块同步知识" width="820px" top="6vh">
      <div class="sync-bar">
        <el-select v-model="syncSourceType" style="width: 140px;" @change="loadCandidates">
          <el-option label="元数据表" value="METADATA" />
          <el-option label="指标定义" value="METRIC" />
          <el-option label="数据标准" value="STANDARD" />
        </el-select>
        <el-select v-model="syncCategoryId" placeholder="同步到分类" style="width: 180px;">
          <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
        </el-select>
        <el-input v-model="syncKeyword" placeholder="搜索候选" clearable style="flex: 1;" @keyup.enter="loadCandidates" />
        <el-button @click="loadCandidates">查询</el-button>
      </div>
      <el-table :data="candidates" stripe v-loading="candidateLoading" max-height="420" @selection-change="onSyncSelection">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="title" label="来源对象" min-width="220" show-overflow-tooltip />
        <el-table-column prop="description" label="说明" min-width="300" show-overflow-tooltip />
      </el-table>
      <div class="sync-tip">只同步勾选项，不会自动把全部元数据/指标放入知识库。</div>
      <template #footer>
        <span class="sync-count">已选 {{ selectedSources.length }} 项</span>
        <el-button @click="showSync = false">取消</el-button>
        <el-button type="primary" :loading="syncing" @click="doSync">同步到知识库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { knowledgeApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const categories = ref<any[]>([])
const entries = ref<any[]>([])
const stats = ref<any>({})
const external = ref<any>({ configured: false })
const migrating = ref(false)
const selectedCategoryId = ref<number | undefined>(undefined)
const keyword = ref('')
const sourceFilter = ref('')
const loading = ref(false)
const saving = ref(false)
const showCategory = ref(false)
const showEntry = ref(false)
const showSync = ref(false)
const categoryForm = ref<any>({ status: 1, sortOrder: 0 })
const entryForm = ref<any>({})
const syncSourceType = ref('METADATA')
const syncCategoryId = ref<number | undefined>(undefined)
const syncKeyword = ref('')
const candidates = ref<any[]>([])
const selectedSources = ref<any[]>([])
const candidateLoading = ref(false)
const syncing = ref(false)

const currentCategory = computed(() => categories.value.find(c => c.id === selectedCategoryId.value))
const categoryCount = (id: number) => Number(stats.value.byCategory?.[String(id)] || 0)
const sourceLabel = (v: string) => ({ MANUAL: '手工', METADATA: '元数据', METRIC: '指标', STANDARD: '标准' } as any)[v] || v || '手工'
const sourceTag = (v: string) => (v === 'METRIC' ? 'success' : v === 'METADATA' ? 'primary' : v === 'STANDARD' ? 'warning' : 'info')
const vectorLabel = (v: string) => ({ PENDING: '待索引', SYNCED: '已索引', FAILED: '失败' } as any)[v] || v || '待索引'
const vectorTag = (v: string) => (v === 'SYNCED' ? 'success' : v === 'FAILED' ? 'danger' : 'info')
const syncLabel = (v: string) => ({ LOCAL_ONLY: '未同步', SYNCED: '已同步', FAILED: '失败' } as any)[v] || '未同步'
const syncTag = (v: string) => (v === 'SYNCED' ? 'success' : v === 'FAILED' ? 'danger' : 'info')
const formatTime = (v: string) => v ? String(v).replace('T', ' ').slice(0, 16) : '-'

async function loadCategories() {
  try {
    const res: any = await knowledgeApi.categories()
    categories.value = res.data || []
    if (!selectedCategoryId.value && categories.value.length) selectedCategoryId.value = categories.value[0].id
  } catch (e: any) { ElMessage.error(e.message || '加载知识分类失败') }
}

async function loadStats() {
  try {
    const res: any = await knowledgeApi.stats()
    stats.value = res.data || {}
  } catch { stats.value = {} }
}

async function loadExternal() {
  try {
    const res: any = await knowledgeApi.externalStatus()
    external.value = res.data || { configured: false }
  } catch { external.value = { configured: false } }
}

async function loadAll() {
  await loadCategories()
  await Promise.all([loadStats(), loadEntries(), loadExternal()])
}

async function loadEntries() {
  loading.value = true
  try {
    const res: any = await knowledgeApi.entries({
      categoryId: selectedCategoryId.value,
      keyword: keyword.value || undefined,
      sourceType: sourceFilter.value || undefined,
      limit: 300
    })
    entries.value = res.data || []
  } catch (e: any) { ElMessage.error(e.message || '加载知识条目失败'); entries.value = [] }
  finally { loading.value = false }
}

function selectCategory(id: number) { selectedCategoryId.value = id; loadEntries() }

function openCategory(c?: any) {
  categoryForm.value = c ? { ...c } : { status: 1, sortOrder: 0 }
  showCategory.value = true
}
async function saveCategory() {
  if (!categoryForm.value.categoryCode?.trim() || !categoryForm.value.categoryName?.trim()) {
    ElMessage.warning('分类编码和名称不能为空'); return
  }
  saving.value = true
  try {
    if (categoryForm.value.id) await knowledgeApi.updateCategory(categoryForm.value.id, categoryForm.value)
    else await knowledgeApi.createCategory(categoryForm.value)
    ElMessage.success('分类已保存')
    showCategory.value = false
    await Promise.all([loadCategories(), loadStats()])
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { saving.value = false }
}
async function removeCategory(c: any) {
  try {
    await ElMessageBox.confirm(`确认删除分类「${c.categoryName}」？`, '删除分类', { type: 'warning' })
    await knowledgeApi.deleteCategory(c.id)
    ElMessage.success('已删除')
    if (selectedCategoryId.value === c.id) selectedCategoryId.value = undefined
    await Promise.all([loadCategories(), loadStats(), loadEntries()])
  } catch (_e) { /* cancel */ }
}

function openEntry(row?: any) {
  entryForm.value = row ? { ...row, tagsText: parseTags(row.tags) } : { categoryId: selectedCategoryId.value }
  showEntry.value = true
}
function parseTags(v: any): string {
  if (!v) return ''
  try { const a = JSON.parse(v); return Array.isArray(a) ? a.join(', ') : String(v) } catch { return String(v) }
}
async function saveEntry() {
  if (!entryForm.value.title?.trim()) { ElMessage.warning('标题不能为空'); return }
  saving.value = true
  try {
    const payload = { ...entryForm.value, tags: entryForm.value.tagsText ? entryForm.value.tagsText.split(/[,，]/).map((s: string) => s.trim()).filter(Boolean) : [] }
    delete payload.tagsText
    if (entryForm.value.id) await knowledgeApi.updateEntry(entryForm.value.id, payload)
    else await knowledgeApi.createEntry(payload)
    ElMessage.success('知识已保存')
    showEntry.value = false
    await Promise.all([loadEntries(), loadStats()])
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { saving.value = false }
}
async function removeEntry(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.title}」？`, '删除知识', { type: 'warning' })
    await knowledgeApi.deleteEntry(row.id)
    ElMessage.success('已删除')
    await Promise.all([loadEntries(), loadStats()])
  } catch (_e) { /* cancel */ }
}
async function reindex(row: any) {
  try {
    await knowledgeApi.reindex(row.id)
    ElMessage.success('已重新加入向量索引队列')
    await Promise.all([loadEntries(), loadStats()])
  } catch (e: any) { ElMessage.error(e.message || '操作失败') }
}

function openSync() {
  syncCategoryId.value = selectedCategoryId.value
  if (!syncCategoryId.value && categories.value.length) syncCategoryId.value = categories.value[0].id
  showSync.value = true
  loadCandidates()
}
async function loadCandidates() {
  candidateLoading.value = true
  selectedSources.value = []
  try {
    const res: any = await knowledgeApi.sources({ sourceType: syncSourceType.value, keyword: syncKeyword.value || undefined, limit: 200 })
    candidates.value = res.data || []
  } catch (e: any) { ElMessage.error(e.message || '加载候选失败'); candidates.value = [] }
  finally { candidateLoading.value = false }
}
function onSyncSelection(rows: any[]) { selectedSources.value = rows }
async function doSync() {
  if (!syncCategoryId.value) { ElMessage.warning('请选择同步到的分类'); return }
  if (!selectedSources.value.length) { ElMessage.warning('请勾选要同步的内容'); return }
  syncing.value = true
  try {
    const res: any = await knowledgeApi.sync({
      categoryId: syncCategoryId.value,
      sourceType: syncSourceType.value,
      sources: selectedSources.value
    })
    ElMessage.success(`已同步 ${res.data?.synced || selectedSources.value.length} 条`)
    showSync.value = false
    selectedCategoryId.value = syncCategoryId.value
    await Promise.all([loadEntries(), loadStats()])
  } catch (e: any) { ElMessage.error(e.message || '同步失败') }
  finally { syncing.value = false }
}

async function migrateExternal() {
  if (!external.value.configured) { ElMessage.warning('请先配置 WeKnora API Key'); return }
  try {
    await ElMessageBox.confirm('将把全部分类和知识条目迁移到 Agent 知识库；已迁移条目会更新，是否继续？', '迁移知识库', { type: 'warning' })
  } catch { return }
  migrating.value = true
  try {
    const res: any = await knowledgeApi.migrateExternal()
    const d = res.data || {}
    ElMessage.success(`迁移完成：成功 ${d.synced || 0} 条，失败 ${d.failed || 0} 条`)
    await Promise.all([loadStats(), loadEntries(), loadExternal()])
  } catch (e: any) { ElMessage.error(e.message || '迁移失败') }
  finally { migrating.value = false }
}

onMounted(loadAll)
</script>

<style scoped>
.kb-wrap { display: grid; grid-template-columns: 230px minmax(0, 1fr); gap: 12px; align-items: start; }
.kb-side { padding: 12px; }
.kb-side-head { display: flex; align-items: center; justify-content: space-between; font-size: 12px; font-weight: 600; margin-bottom: 8px; }
.kb-cat { padding: 8px; border-radius: 7px; cursor: pointer; margin-bottom: 4px; }
.kb-cat:hover { background: #F8FAFC; }
.kb-cat.active { background: #EFF6FF; }
.kb-cat-main b { display: block; font-size: 12px; color: #334155; }
.kb-cat-main span { display: block; font-size: 10.5px; color: #94A3B8; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.kb-cat-actions { display: none; margin-top: 2px; }
.kb-cat.active .kb-cat-actions, .kb-cat:hover .kb-cat-actions { display: flex; }
.kb-main { padding: 14px; min-width: 0; }
.kb-overview { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; margin-bottom: 14px; }
.kb-stat { border: 1px solid var(--med-border); border-radius: 10px; padding: 9px 11px; background: #FCF9FC; border-top: 2px solid var(--med-primary); }
.kb-stat.pending { border-top-color: var(--med-gold); }
.kb-stat.synced { border-top-color: var(--med-green); }
.kb-stat.failed { border-top-color: var(--med-danger); }
.kb-stat span { display: block; font-size: 10px; color: var(--med-text-light); }
.kb-stat b { display: block; font-size: 20px; line-height: 1.4; color: var(--med-text); }
.kb-stat em { font-style: normal; font-size: 9.5px; color: var(--med-text-light); }
.kb-cat-count { display: inline-block; font-style: normal; font-size: 9px; min-width: 16px; text-align: center; padding: 1px 4px; border-radius: 8px; background: var(--med-primary-lighter); color: var(--med-primary); margin-left: 4px; }
.kb-toolbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.kb-title { margin: 0; font-size: 14px; color: #1E293B; }
.kb-desc { margin: 3px 0 0; font-size: 11px; color: #94A3B8; }
.kb-actions { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.sync-bar { display: flex; gap: 8px; align-items: center; margin-bottom: 10px; flex-wrap: wrap; }
.sync-tip { margin-top: 8px; font-size: 11px; color: #94A3B8; }
.sync-count { float: left; font-size: 11px; color: #64748B; line-height: 32px; }
</style>
