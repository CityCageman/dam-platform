<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <div class="back-row">
          <el-button text size="small" @click="$router.back()"><el-icon><ArrowLeft /></el-icon>返回</el-button>
        </div>
        <h2 class="page-title">
          {{ asset.assetName || '资产详情' }}
          <el-tag v-if="assetData.isFavorited" type="warning" size="small" style="margin-left: 8px; cursor: pointer;" @click="toggleFavorite">
            <el-icon><StarFilled /></el-icon>已收藏
          </el-tag>
          <el-button v-else text size="small" @click="toggleFavorite"><el-icon><Star /></el-icon>收藏</el-button>
        </h2>
        <p class="page-desc">{{ asset.assetCode || '-' }} · {{ (asset.sourceMetadataType || asset.assetType || '-') }} · {{ asset.categoryPathText || '未分类' }}</p>
      </div>
      <div class="head-actions">
        <el-select v-model="assetData.status" size="small" style="width: 130px;" @change="updateStatus">
          <el-option v-for="s in assetStatuses" :key="s.configCode" :label="s.configName" :value="Number(s.configValue) || Number(s.configCode) || 1" />
        </el-select>
        <el-tag :type="asset.securityLevel === 'restricted' || asset.securityLevel === 'critical' ? 'danger' : 'info'" size="small">
          {{ asset.securityLevel || '未分级' }}
        </el-tag>
        <el-button size="small" @click="$router.push('/lineage')">查看血缘</el-button>
      </div>
    </div>

    <div class="stat-grid" style="margin-bottom: 14px;">
      <div class="stat-card">
        <div class="stat-label">字段数</div>
        <div class="stat-value">{{ fields.length }}</div>
        <div class="stat-foot">主键 {{ pkFields }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">标签</div>
        <div class="stat-value">{{ tags.length }}</div>
        <div class="stat-foot">业务标签</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">平均评分</div>
        <div class="stat-value">{{ assetData.avgRating || '-' }}</div>
        <div class="stat-foot">{{ assetData.ratingCount || 0 }} 次评价</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">访问次数</div>
        <div class="stat-value">{{ assetData.viewCount || 0 }}</div>
        <div class="stat-foot">记录数 {{ asset.recordCount?.toLocaleString() || '-' }}</div>
      </div>
    </div>

    <div class="panel">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="info">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="资产编码">{{ asset.assetCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="资产类型">{{ (asset.sourceMetadataType || asset.assetType || '-') }}</el-descriptions-item>
            <el-descriptions-item label="主题域/类目">{{ asset.categoryPathText || '-' }}</el-descriptions-item>
            <el-descriptions-item label="来源系统">{{ asset.sourceSystem || '-' }}</el-descriptions-item>
            <el-descriptions-item label="数据库">{{ asset.databaseName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="表名">{{ asset.tableName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="记录数">{{ asset.recordCount?.toLocaleString() || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ statusLabel(asset.status) }}</el-descriptions-item>
            <el-descriptions-item label="安全等级">{{ asset.securityLevel || '-' }}</el-descriptions-item>
            <el-descriptions-item label="业务描述" :span="3">{{ asset.description || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="字段列表" name="fields">
          <div class="tab-toolbar">
            <span class="muted">共 {{ fields.length }} 个字段，可编辑字段注释/类型/主键等</span>
          </div>
          <el-table :data="fields" stripe>
            <el-table-column prop="fieldName" label="字段名" width="170" show-overflow-tooltip />
            <el-table-column prop="fieldAlias" label="别名" width="130" show-overflow-tooltip />
            <el-table-column prop="dataType" label="类型" width="120" />
            <el-table-column prop="dataLength" label="长度" width="80" />
            <el-table-column label="主键" width="70">
              <template #default="{ row }">
                <el-tag v-if="row.isPrimaryKey" size="small" type="warning">PK</el-tag>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="可空" width="70">
              <template #default="{ row }">{{ row.isNullable ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column prop="comment" label="注释" min-width="200" show-overflow-tooltip />
            <el-table-column label="敏感" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.isSensitive" size="small" type="danger">{{ row.sensitiveType || '敏感' }}</el-tag>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="openFieldEdit(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="标签与评价" name="tags">
          <div class="tab-toolbar">
            <span class="muted">标签</span>
            <div class="tag-row">
              <el-tag v-for="tag in tags" :key="tag.id" size="small">{{ tag.tagName }}</el-tag>
              <span v-if="!tags.length" class="muted">暂无标签</span>
            </div>
          </div>
          <el-divider content-position="left">评价</el-divider>
          <div class="rating-form">
            <el-rate v-model="newScore" :max="5" show-score />
            <el-input v-model="newComment" placeholder="写评论..." style="width: 320px;" />
            <el-button type="primary" size="small" @click="submitRating">提交</el-button>
          </div>
          <div v-for="r in ratings" :key="r.id" class="rating-item">
            <el-rate :model-value="r.ratingScore" disabled size="small" />
            <span v-if="r.ratingComment" class="rating-text">{{ r.ratingComment }}</span>
          </div>
          <el-empty v-if="!ratings.length" description="暂无评价" :image-size="50" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="fieldEditVisible" :title="'编辑字段：' + (fieldEditForm.fieldName || '')" width="500px" top="20vh">
      <el-form :model="fieldEditForm" label-width="85px">
        <el-form-item label="字段名"><el-input v-model="fieldEditForm.fieldName" /></el-form-item>
        <el-form-item label="数据类型"><el-input v-model="fieldEditForm.dataType" /></el-form-item>
        <el-form-item label="长度"><el-input-number v-model="fieldEditForm.dataLength" :min="0" :max="999999" style="width: 100%;" /></el-form-item>
        <el-form-item label="主键"><el-switch v-model="fieldEditForm.isPrimaryKey" :active-value="true" :inactive-value="false" /></el-form-item>
        <el-form-item label="可空"><el-switch v-model="fieldEditForm.isNullable" :active-value="true" :inactive-value="false" /></el-form-item>
        <el-form-item label="默认值"><el-input v-model="fieldEditForm.defaultValue" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="fieldEditForm.sortOrder" :min="0" style="width: 100%;" /></el-form-item>
        <el-form-item label="注释"><el-input v-model="fieldEditForm.comment" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fieldEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="fieldSaving" @click="saveFieldEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { catalogApi } from '@/api'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Star, StarFilled } from '@element-plus/icons-vue'

const route = useRoute()
const assetData = ref<any>({})
const asset = ref<any>({})
const fields = ref<any[]>([])
const tags = ref<any[]>([])
const ratings = ref<any[]>([])
const newScore = ref(0)
const newComment = ref('')
const assetStatuses = ref<any[]>([])
const activeTab = ref('info')
const fieldEditVisible = ref(false)
const fieldSaving = ref(false)
const fieldEditForm = ref<any>({})

const pkFields = computed(() => fields.value.filter(f => f.isPrimaryKey).length)
const statusLabel = (v: any) => {
  const s = assetStatuses.value.find(x => Number(x.configValue) === Number(v) || Number(x.configCode) === Number(v))
  return s?.configName || v || '-'
}

onMounted(async () => {
  try {
    const res: any = await catalogApi.getConfig('asset_status')
    assetStatuses.value = res.data || []
  } catch (_e) { assetStatuses.value = [] }
  await loadAsset(Number(route.params.id))
})

async function loadAsset(id: number) {
  const res: any = await catalogApi.getAssetDetail(id)
  assetData.value = res.data
  asset.value = res.data.asset || {}
  fields.value = res.data.fields || []
  tags.value = res.data.tags || []
  ratings.value = await loadRatings(id)
}

async function updateStatus(val: string) {
  try {
    await catalogApi.updateAsset({ id: asset.value.id, status: val })
    ElMessage.success('状态已更新')
    await loadAsset(Number(route.params.id))
  } catch (e: any) { ElMessage.error(e.message || '更新失败') }
}

async function loadRatings(assetId: number) {
  const res: any = await catalogApi.getAssetRatings(assetId)
  return res.data || []
}

async function toggleFavorite() {
  const id = Number(route.params.id)
  if (assetData.value.isFavorited) {
    await catalogApi.removeFavorite(id)
    assetData.value.isFavorited = false
    ElMessage.success('已取消收藏')
  } else {
    await catalogApi.addFavorite(id)
    assetData.value.isFavorited = true
    ElMessage.success('已收藏')
  }
}

async function submitRating() {
  if (newScore.value === 0) { ElMessage.warning('请评分'); return }
  await catalogApi.rateAsset({ assetId: Number(route.params.id), ratingScore: newScore.value, ratingComment: newComment.value })
  ElMessage.success('评价成功')
  ratings.value = await loadRatings(Number(route.params.id))
  newScore.value = 0
  newComment.value = ''
}

function openFieldEdit(row: any) {
  fieldEditForm.value = { ...row }
  fieldEditVisible.value = true
}

async function saveFieldEdit() {
  if (fieldEditForm.value.id == null) return
  fieldSaving.value = true
  try {
    await catalogApi.updateField({
      id: fieldEditForm.value.id,
      fieldName: fieldEditForm.value.fieldName,
      dataType: fieldEditForm.value.dataType,
      dataLength: fieldEditForm.value.dataLength,
      isPrimaryKey: fieldEditForm.value.isPrimaryKey,
      isNullable: fieldEditForm.value.isNullable,
      defaultValue: fieldEditForm.value.defaultValue,
      sortOrder: fieldEditForm.value.sortOrder,
      comment: fieldEditForm.value.comment
    })
    ElMessage.success('保存成功')
    fieldEditVisible.value = false
    await loadAsset(Number(route.params.id))
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { fieldSaving.value = false }
}
</script>

<style scoped>
.back-row { margin-bottom: 4px; }
.muted { color: var(--med-text-light); font-size: 12px; }
.tab-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.tag-row { display: flex; gap: 6px; flex-wrap: wrap; }
.rating-form { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.rating-item { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--med-border-light); }
.rating-text { color: var(--med-text-secondary); font-size: 12.5px; }
</style>
