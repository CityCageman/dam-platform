<template>
  <div class="page-container">
    <el-button text @click="$router.back()" style="margin-bottom: 12px;"><el-icon><ArrowLeft /></el-icon> 返回</el-button>
    <el-card v-if="assetData">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <div style="display: flex; align-items: center; gap: 12px;">
            <span style="font-size: 18px; font-weight: bold;">{{ assetData.assetName }}</span>
            <el-tag v-if="assetData.isFavorited" type="warning" @click="toggleFavorite"><el-icon><StarFilled /></el-icon></el-tag>
            <el-tag v-else type="info" @click="toggleFavorite" style="cursor: pointer;"><el-icon><Star /></el-icon></el-tag>
          </div>
          <div style="display: flex; align-items: center; gap: 8px;">
            <el-select v-model="assetData.status" size="small" @change="updateStatus" style="width: 120px;">
              <el-option v-for="s in assetStatuses" :key="s.configCode" :label="s.configName" :value="Number(s.configValue) || Number(s.configCode) || 1" />
            </el-select>
            <el-tag :type="assetData.securityLevel === 'restricted' ? 'danger' : 'info'">{{ assetData.securityLevel }}</el-tag>
          </div>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="资产编码">{{ asset.assetCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="资产类型">{{ (asset.sourceMetadataType || asset.assetType || '-').toLowerCase() }}</el-descriptions-item>
        <el-descriptions-item label="主题域 / 类目">{{ asset.categoryPathText || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源系统">{{ asset.sourceSystem || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数据库">{{ asset.databaseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="表名">{{ asset.tableName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="记录数">{{ asset.recordCount?.toLocaleString() || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ asset.status }}</el-descriptions-item>
        <el-descriptions-item label="平均评分">{{ assetData.avgRating || '-' }} ({{ assetData.ratingCount || 0 }}次)</el-descriptions-item>
        <el-descriptions-item label="访问次数">{{ assetData.viewCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="业务描述" :span="2">{{ asset.description || '-' }}</el-descriptions-item>
      </el-descriptions>
      <!-- 标签 -->
      <div v-if="tags.length > 0" style="margin-top: 16px;">
        <span style="font-weight: bold; margin-right: 8px;">标签:</span>
        <el-tag v-for="tag in tags" :key="tag.id" size="small" style="margin-right: 6px;">{{ tag.tagName }}</el-tag>
      </div>
    </el-card>
    <el-card style="margin-top: 16px;">
      <template #header><span>字段列表</span></template>
      <el-table :data="fields" stripe>
        <el-table-column prop="fieldName" label="字段名" width="160" />
        <el-table-column prop="fieldAlias" label="别名" width="120" />
        <el-table-column prop="dataType" label="数据类型" width="120" />
        <el-table-column prop="dataLength" label="长度" width="80" />
        <el-table-column prop="isPrimaryKey" label="主键" width="60">
          <template #default="{ row }">{{ row.isPrimaryKey ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="isNullable" label="可空" width="60">
          <template #default="{ row }">{{ row.isNullable ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="comment" label="注释" min-width="200" />
        <el-table-column prop="isSensitive" label="敏感" width="60">
          <template #default="{ row }">{{ row.isSensitive ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="sensitiveType" label="敏感类型" width="120" />
        <el-table-column label="操作" width="70" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" text @click="openFieldEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <!-- 字段编辑弹窗 -->
      <el-dialog v-model="fieldEditVisible" :title="'编辑字段：' + (fieldEditForm.fieldName || '')" width="480px" top="25vh">
        <el-form :model="fieldEditForm" label-width="80px">
          <el-form-item label="字段名"><el-input v-model="fieldEditForm.fieldName" /></el-form-item>
          <el-form-item label="数据类型"><el-input v-model="fieldEditForm.dataType" /></el-form-item>
          <el-form-item label="长度"><el-input-number v-model="fieldEditForm.dataLength" :min="0" :max="999999" style="width: 100%;" /></el-form-item>
          <el-form-item label="主键"><el-switch v-model="fieldEditForm.isPrimaryKey" :active-value="true" :inactive-value="false" /></el-form-item>
          <el-form-item label="可空"><el-switch v-model="fieldEditForm.isNullable" :active-value="true" :inactive-value="false" /></el-form-item>
          <el-form-item label="默认值"><el-input v-model="fieldEditForm.defaultValue" /></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="fieldEditForm.sortOrder" :min="0" style="width: 100%;" /></el-form-item>
          <el-form-item label="注释"><el-input v-model="fieldEditForm.comment" type="textarea" :rows="3" placeholder="补充字段注释..." /></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="fieldEditVisible = false">取消</el-button>
          <el-button type="primary" :loading="fieldSaving" @click="saveFieldEdit">保存</el-button>
        </template>
      </el-dialog>
    </el-card>
    <!-- 评价 -->
    <el-card style="margin-top: 16px;">
      <template #header><span>评价</span></template>
      <div style="margin-bottom: 12px; display: flex; gap: 12px; align-items: center;">
        <el-rate v-model="newScore" :max="5" show-score />
        <el-input v-model="newComment" placeholder="写评论..." style="width: 300px;" />
        <el-button type="primary" size="small" @click="submitRating">提交</el-button>
      </div>
      <div v-for="r in ratings" :key="r.id" style="border-bottom: 1px solid #eee; padding: 8px 0;">
        <el-rate :model-value="r.ratingScore" disabled size="small" />
        <span v-if="r.ratingComment" style="margin-left: 8px; color: #606266;">{{ r.ratingComment }}</span>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { catalogApi } from '@/api'
import { ElMessage } from 'element-plus'

const route = useRoute()
const assetData = ref<any>({})
const asset = ref<any>({})
const fields = ref<any[]>([])
const tags = ref<any[]>([])
const ratings = ref<any[]>([])
const newScore = ref(0)
const newComment = ref('')
const assetStatuses = ref<any[]>([])
const fieldEditVisible = ref(false)
const fieldSaving = ref(false)
const fieldEditForm = ref<any>({})

onMounted(async () => {
  const id = Number(route.params.id)
  try {
    const res = await catalogApi.getConfig('asset_status')
    assetStatuses.value = res.data || []
  } catch (_e) {
    assetStatuses.value = []
  }
  await loadAsset(id)
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
  } catch (e: any) {
    ElMessage.error(e.message || '更新失败')
  }
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
  if (newScore.value === 0) {
    ElMessage.warning('请评分')
    return
  }
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
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    fieldSaving.value = false
  }
}
</script>
