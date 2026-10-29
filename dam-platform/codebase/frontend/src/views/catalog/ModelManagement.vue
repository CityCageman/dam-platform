<template>
  <div class="page-container">
    <div class="page-header">
      <h2>模型管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="showForm = true; isEdit = false; form = { domainName: '', domainCode: '', description: '' }">
          <el-icon><Plus /></el-icon> 新建模型
        </el-button>
      </div>
    </div>
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card style="height: calc(100vh - 160px); overflow-y: auto;">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <span>数据模型</span>
              <el-tag size="small" type="info">{{ models.length }} 个</el-tag>
            </div>
          </template>
          <el-tree-select
            v-model="filterCategoryId"
            :data="categories"
            node-key="id"
            :props="{ label: 'categoryName', children: 'children' }"
            placeholder="按分类筛选"
            clearable
            check-strictly
            style="width: 100%; margin-bottom: 8px;"
            @change="filterByCategory"
          />
          <div v-if="models.length">
            <div
              v-for="m in models"
              :key="m.id"
              class="model-item"
              :class="{ active: selectedModelId === m.id }"
              @click="selectModel(m)"
            >
              <div style="flex:1; min-width:0;">
                <div class="model-name">{{ m.domainName }}</div>
                <div class="model-meta">
                  <el-tag :type="Number(m.status) === 1 ? 'success' : 'info'" size="small" style="margin-right: 4px;">
                    {{ Number(m.status) === 1 ? '已发布' : '已归档' }}
                  </el-tag>
                </div>
              </div>
              <el-dropdown @click.stop @command="(cmd: string) => handleModelAction(cmd, m)">
                <el-button text><el-icon><MoreFilled /></el-icon></el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit" :disabled="Number(m.status) !== 1"><el-icon><Edit /></el-icon> 编辑</el-dropdown-item>
                    <el-dropdown-item v-if="Number(m.status) !== 1" command="publish"><el-icon><Upload /></el-icon> 发布</el-dropdown-item>
                    <el-dropdown-item v-if="Number(m.status) === 1" command="archive"><el-icon><FolderDelete /></el-icon> 归档</el-dropdown-item>
                    <el-dropdown-item divided command="delete"><el-icon><Delete /></el-icon> 删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
          <el-empty v-else description="暂无模型，请先创建" />
        </el-card>
      </el-col>
      <el-col :span="16">
        <template v-if="selectedModel">
          <el-card style="margin-bottom: 12px;">
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <div>
                <b>{{ selectedModel.domainName }}</b>
                <el-tag :type="Number(selectedModel.status) === 1 ? 'success' : 'info'" size="small" style="margin-left: 8px;">
                  {{ Number(selectedModel.status) === 1 ? '已发布' : '已归档' }}
                </el-tag>
                <span style="margin-left: 8px; color: #909399; font-size: 13px;">{{ selectedModel.domainCode }}</span>
              </div>
            </div>
            <div v-if="selectedModel.description" style="margin-top: 8px; color: #909399; font-size: 13px;">{{ selectedModel.description }}</div>
          </el-card>
          <el-card style="height: calc(100vh - 280px); overflow-y: auto;">
            <el-tabs v-model="rightTab">
              <el-tab-pane label="字段定义" name="fields">
                <div style="margin-bottom: 8px; display: flex; justify-content: flex-end;">
                  <el-button size="small" type="primary" @click="showFieldForm = true; fieldForm = { modelId: selectedModel.id, targetColumnName: '', targetColumnType: 'VARCHAR', isPrimaryKey: 0, isForeignKey: 0, transformRule: '', sortOrder: modelFields.length + 1 }">
                    <el-icon><Plus /></el-icon> 添加字段
                  </el-button>
                </div>
                <el-table :data="modelFields" stripe max-height="420">
                  <el-table-column prop="sortOrder" label="排序" width="55" />
                  <el-table-column prop="targetColumnName" label="目标字段" min-width="150" />
                  <el-table-column prop="targetColumnType" label="类型" width="90" />
                  <el-table-column label="主键" width="55">
                    <template #default="{ row }">{{ Number(row.isPrimaryKey) === 1 ? '是' : '否' }}</template>
                  </el-table-column>
                  <el-table-column label="外键" width="55">
                    <template #default="{ row }">{{ Number(row.isForeignKey) === 1 ? '是' : '否' }}</template>
                  </el-table-column>
                  <el-table-column prop="transformRule" label="转换规则" min-width="140" show-overflow-tooltip />
                  <el-table-column label="操作" width="55" fixed="right">
                    <template #default="{ row }">
                      <el-button text @click="editField(row)">
                        <el-icon><Edit /></el-icon>
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <el-empty v-if="!modelFields.length" description="暂无字段定义，请添加字段" />
              </el-tab-pane>
              <el-tab-pane label="对比资产" name="compare">
                <div style="margin-bottom: 12px; display: flex; gap: 8px; align-items: center;">
                  <span style="font-size: 13px; color: #606266;">选择资产：</span>
                  <el-select v-model="compareAssetId" placeholder="选择要对比的资产" style="width: 260px;" @change="doCompare">
                    <el-option v-for="a in comparableAssets" :key="a.id" :label="a.assetName + ' (' + a.tableName + ') - ' + a.fieldCount + '字段'" :value="a.id" />
                  </el-select>
                </div>
                <template v-if="compareResult">
                  <el-alert
                    :title="'合规率: ' + compareResult.summary.complianceRate + '%'"
                    :type="compareResult.summary.complianceRate >= 80 ? 'success' : compareResult.summary.complianceRate >= 50 ? 'warning' : 'error'"
                    :description="'模型共 ' + compareResult.summary.totalModelFields + ' 个字段，资产有 ' + compareResult.summary.totalAssetFields + ' 个字段。匹配 ' + compareResult.summary.matched + '，缺失 ' + compareResult.summary.missing + '，多余 ' + compareResult.summary.extra + '，类型/长度不一致 ' + compareResult.summary.mismatched"
                    show-icon
                    style="margin-bottom: 12px;"
                  />
                  <el-descriptions :column="2" border size="small" style="margin-bottom: 12px;">
                    <el-descriptions-item label="模型">{{ compareResult.modelName }}</el-descriptions-item>
                    <el-descriptions-item label="资产">{{ compareResult.assetName }}</el-descriptions-item>
                  </el-descriptions>
                  <el-table v-if="compareResult.missing.length" :data="compareResult.missing" stripe max-height="200" style="margin-bottom: 12px;">
                    <el-table-column type="danger" label="缺失字段" min-width="160">
                      <template #header><span style="color: #F56C6C;">❌ 缺失字段（模型中有，资产中无）</span></template>
                      <template #default="{ row }">{{ row.fieldName }}</template>
                    </el-table-column>
                    <el-table-column prop="expectedType" label="期望类型" width="100" />
                    <el-table-column prop="expectedLength" label="期望长度" width="80" />
                  </el-table>
                  <el-table v-if="compareResult.mismatched.length" :data="compareResult.mismatched" stripe max-height="200" style="margin-bottom: 12px;">
                    <el-table-column label="类型/长度不一致" min-width="160">
                      <template #header><span style="color: #E6A23C;">⚠️ 类型/长度不一致</span></template>
                      <template #default="{ row }">{{ row.fieldName }}</template>
                    </el-table-column>
                    <el-table-column label="差异" min-width="200">
                      <template #default="{ row }">
                        <div v-for="d in row.differences" :key="d.property" style="font-size: 13px;">
                          {{ d.property }}: {{ d.expected }} → {{ d.actual }}
                        </div>
                      </template>
                    </el-table-column>
                  </el-table>
                  <el-table v-if="compareResult.extra.length" :data="compareResult.extra" stripe max-height="200">
                    <el-table-column label="多余字段" min-width="160">
                      <template #header><span style="color: #909399;">📎 多余字段（资产中有，模型中无）</span></template>
                      <template #default="{ row }">{{ row.fieldName }}</template>
                    </el-table-column>
                    <el-table-column prop="dataType" label="类型" width="100" />
                    <el-table-column prop="dataLength" label="长度" width="80" />
                  </el-table>
                  <el-empty v-if="!compareResult.missing.length && !compareResult.mismatched.length && !compareResult.extra.length" description="完全匹配！所有字段都符合模型标准" />
                </template>
              </el-tab-pane>
            </el-tabs>
          </el-card>
        </template>
        <template v-else>
          <el-card style="height: calc(100vh - 160px); display: flex; align-items: center; justify-content: center;">
            <span style="color: #909399;">请从左侧选择一个模型</span>
          </el-card>
        </template>
      </el-col>
    </el-row>

    <!-- 新建/编辑模型对话框 -->
    <el-dialog v-model="showForm" :title="isEdit ? '编辑模型' : '新建模型'" width="500px" top="25vh">
      <el-form :model="form" label-width="90px">
        <el-form-item label="模型名称"><el-input v-model="form.domainName" placeholder="例如：患者核心模型" /></el-form-item>
        <el-form-item label="模型编码"><el-input v-model="form.domainCode" placeholder="例如：PATIENT_CORE" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button type="primary" @click="submitModel">确认</el-button>
      </template>
    </el-dialog>

    <!-- 添加/编辑字段对话框 -->
    <el-dialog v-model="showFieldForm" :title="isEditField ? '编辑字段' : '添加字段'" width="500px" top="25vh">
      <el-form :model="fieldForm" label-width="100px">
        <el-form-item label="目标字段"><el-input v-model="fieldForm.targetColumnName" placeholder="例如：patient_name" /></el-form-item>
        <el-form-item label="数据类型">
          <el-select v-model="fieldForm.targetColumnType">
            <el-option label="VARCHAR" value="VARCHAR" /><el-option label="CHAR" value="CHAR" />
            <el-option label="INT" value="INT" /><el-option label="BIGINT" value="BIGINT" />
            <el-option label="DECIMAL" value="DECIMAL" /><el-option label="FLOAT" value="FLOAT" />
            <el-option label="DATE" value="DATE" /><el-option label="DATETIME" value="DATETIME" />
            <el-option label="TEXT" value="TEXT" /><el-option label="BLOB" value="BLOB" />
          </el-select>
        </el-form-item>
        <el-form-item label="主键">
          <el-switch v-model="fieldForm.isPrimaryKey" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="外键">
          <el-switch v-model="fieldForm.isForeignKey" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="转换规则"><el-input v-model="fieldForm.transformRule" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFieldForm = false">取消</el-button>
        <el-button type="primary" @click="submitField">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { modelApi, catalogApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const models = ref<any[]>([])
const categories = ref<any[]>([])
const filterCategoryId = ref<number>()
const selectedModelId = ref<number>(0)
const selectedModel = ref<any>(null)
const modelFields = ref<any[]>([])
const rightTab = ref('fields')

// 模型表单
const showForm = ref(false)
const isEdit = ref(false)
const form = ref<any>({})

// 字段表单
const showFieldForm = ref(false)
const isEditField = ref(false)
const fieldForm = ref<any>({})

// 对比
const comparableAssets = ref<any[]>([])
const compareAssetId = ref<number>()
const compareResult = ref<any>(null)

onMounted(() => {
  loadModels()
  loadCategories()
})

async function loadModels() {
  const res: any = await modelApi.getModels(undefined, filterCategoryId.value)
  models.value = res.data || []
  if (selectedModelId.value) {
    const found = models.value.find((m: any) => m.id === selectedModelId.value)
    if (found) {
      selectedModel.value = found
      await loadFields(found.id)
    } else {
      selectedModelId.value = 0
      selectedModel.value = null
      modelFields.value = []
    }
  }
}

async function loadCategories() {
  try {
    const res: any = await catalogApi.getCategories()
    categories.value = buildTree(res.data || [])
  } catch (_e) {
    categories.value = []
  }
}

function buildTree(list: any[]): any[] {
  const map = new Map<number, any>()
  const tree: any[] = []
  list.forEach((item: any) => map.set(item.id, { ...item, children: [] }))
  list.forEach((item: any) => {
    if (item.parentId && map.has(item.parentId)) {
      map.get(item.parentId)!.children.push(map.get(item.id))
    } else {
      tree.push(map.get(item.id))
    }
  })
  return tree
}

function filterByCategory(categoryId?: number) {
  filterCategoryId.value = categoryId
  loadModels()
}

function getCategoryName(id: number | null | undefined): string {
  if (!id) return ''
  function find(list: any[]): string {
    for (const item of list) {
      if (item.id === id) return item.categoryName
      if (item.children && item.children.length > 0) {
        const found = find(item.children)
        if (found) return found
      }
    }
    return ''
  }
  return find(categories.value)
}

async function selectModel(m: any) {
  selectedModelId.value = m.id
  selectedModel.value = m
  rightTab.value = 'fields'
  compareResult.value = null
  compareAssetId.value = undefined
  await loadFields(m.id)
  await loadComparableAssets()
}

async function loadFields(modelId: number) {
  const res: any = await modelApi.getModelFields(modelId)
  modelFields.value = res.data || []
}

async function loadComparableAssets() {
  try {
    const res: any = await modelApi.getComparableAssets()
    comparableAssets.value = res.data || []
  } catch (_e) {
    comparableAssets.value = []
  }
}

async function doCompare() {
  if (!compareAssetId.value) return
  try {
    const res: any = await modelApi.compareModelWithAsset(selectedModelId.value, compareAssetId.value)
    compareResult.value = res.data
    rightTab.value = 'compare'
  } catch (e: any) {
    ElMessage.error(e.message || '对比失败')
  }
}

async function submitModel() {
  if (!form.value.domainName?.trim()) {
    ElMessage.warning('请输入模型名称')
    return
  }
  try {
    if (isEdit.value) {
      await modelApi.updateModel(form.value)
      ElMessage.success('编辑成功')
    } else {
      await modelApi.createModel(form.value)
      ElMessage.success('创建成功')
    }
    showForm.value = false
    await loadModels()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function handleModelAction(cmd: string, m: any) {
  if (cmd === 'edit') {
    isEdit.value = true
    form.value = { ...m }
    showForm.value = true
  } else if (cmd === 'publish') {
    try {
      await modelApi.publishModel(m.id)
      ElMessage.success('发布成功')
      await loadModels()
    } catch (e: any) {
      ElMessage.error(e.message || '发布失败')
    }
  } else if (cmd === 'archive') {
    try {
      await modelApi.archiveModel(m.id)
      ElMessage.success('已归档')
      await loadModels()
    } catch (e: any) {
      ElMessage.error(e.message || '归档失败')
    }
  } else if (cmd === 'delete') {
    try {
      await ElMessageBox.confirm(`确认删除模型「${m.domainName}」？关联的字段定义也将一并删除。`, '提示', { type: 'warning' })
      await modelApi.deleteModel(m.id)
      ElMessage.success('删除成功')
      if (selectedModelId.value === m.id) {
        selectedModelId.value = 0
        selectedModel.value = null
        modelFields.value = []
      }
      await loadModels()
    } catch (_e) { /* 取消 */ }
  }
}

function editField(row: any) {
  isEditField.value = true
  fieldForm.value = { ...row }
  showFieldForm.value = true
}

async function submitField() {
  if (!fieldForm.value.targetColumnName?.trim() || !fieldForm.value.targetColumnType?.trim()) {
    ElMessage.warning('字段名和数据类型不能为空')
    return
  }
  try {
    if (isEditField.value) {
      await modelApi.updateModelField(fieldForm.value)
      ElMessage.success('更新成功')
    } else {
      fieldForm.value.sortOrder = modelFields.value.length + 1
      await modelApi.createModelField(selectedModelId.value, fieldForm.value)
      ElMessage.success('添加成功')
    }
    showFieldForm.value = false
    await loadFields(selectedModelId.value)
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}
</script>

<style scoped>
.model-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  border-radius: 6px;
  margin: 2px 0;
  transition: all 0.2s;
}
.model-item:hover { background-color: #ecf5ff; }
.model-item.active { background-color: #ecf5ff; color: #409EFF; font-weight: 600; }
.model-name { font-size: 14px; color: #303133; }
.model-meta { font-size: 12px; color: #909399; margin-top: 2px; display: flex; align-items: center; gap: 4px; }
.model-item.active .model-name { color: #409EFF; }
</style>
