<template>
  <div class="page-container">
    <div class="page-header">
      <h2>资产目录</h2>
      <div class="header-actions">
        <el-button @click="showSourceSystemDialog = true">
          <el-icon><Setting /></el-icon> 来源系统
        </el-button>
        <el-button type="success" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon> 注册资产
        </el-button>
        <el-button @click="refreshRecordCounts">
          <el-icon><Refresh /></el-icon> 同步记录数
        </el-button>
        <el-button @click="showImportDialog = true">
          <el-icon><Upload /></el-icon> 批量导入
        </el-button>
      </div>
    </div>
    <el-row :gutter="16">
      <el-col :span="4">
        <el-card style="height: calc(100vh - 160px); overflow-y: auto;">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <span>主题域 / 类目</span>
              <el-button size="small" type="primary" text @click="openCategoryManager">管理</el-button>
            </div>
          </template>
          <!-- 分类标准选择器 -->
          <el-select v-model="currentType" placeholder="选择分类标准" size="small" style="width: 100%; margin-bottom: 8px;" @change="onTypeChange">
            <el-option v-for="t in categoryTypes" :key="t.type" :label="t.name" :value="t.type" />
          </el-select>
          <el-tree
            ref="categoryTreeRef"
            :data="categories"
            :props="{ label: 'categoryName', children: 'children' }"
            node-key="id"
            @node-click="onCategoryClick"
            default-expand-all
            highlight-current
          />
        </el-card>
      </el-col>
      <el-col :span="20">
        <el-card>
          <div class="search-bar">
            <el-cascader
              ref="cascaderRef"
              v-model="searchCategoryPath"
              :options="categories"
              :props="{ label: 'categoryName', value: 'id', children: 'children', checkStrictly: true, emitPath: false }"
              placeholder="按分类筛选"
              clearable
              style="width: 200px;"
              @change="onCascaderChange"
            />
            <el-select v-model="assetType" placeholder="资产类型" clearable style="width: 130px;" @change="search">
              <el-option v-for="t in assetTypes" :key="t.configCode" :label="t.configName" :value="t.configCode" />
            </el-select>
            <el-select v-model="securityLevel" placeholder="数据分级" clearable style="width: 160px;" @change="search">
              <el-option v-for="s in securityLevels" :key="s.configCode" :label="s.configName" :value="s.configCode" />
            </el-select>
            <el-input v-model="keyword" placeholder="搜索资产名称/描述" clearable style="width: 260px;" @keyup.enter="search" />
            <el-button type="primary" @click="search">查询</el-button>
            <el-button @click="resetSearch">重置</el-button>
          </div>
          <el-table :data="assetList" stripe @row-click="goDetail" style="cursor: pointer;">
            <el-table-column label="主题域 / 类目" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.categoryPathText || getCategoryName(row.categoryId) || '-' }}</template>
            </el-table-column>
            <el-table-column prop="assetName" label="资产名称" min-width="200" />
            <el-table-column prop="sourceMetadataType" label="类型" width="80">
              <template #default="{ row }">
                <el-tag :type="(row.sourceMetadataType || '').toLowerCase() === 'table' ? 'primary' : (row.sourceMetadataType || '').toLowerCase() === 'api' ? 'success' : 'warning'" size="small">{{ (row.sourceMetadataType || '').toLowerCase() }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sourceSystem" label="来源系统" width="140" />
            <el-table-column label="数据分级" width="150">
              <template #default="{ row }">
                <el-tag :type="getSecurityTagType(row.securityLevel)" size="small" effect="plain">
                  {{ getSecurityLevelLabel(row.securityLevel) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="recordCount" label="记录数" width="110">
              <template #default="{ row }">{{ row.recordCount?.toLocaleString() || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button text @click.stop="openDomainAssign(row)" title="主题域归属">
                  <el-icon><FolderOpened /></el-icon>
                </el-button>
                <el-button text @click.stop="editAsset(row)">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top: 16px; display: flex; justify-content: flex-end;">
            <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total" layout="total, prev, pager, next" @change="loadAssets" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 分类管理对话框 -->
    <el-dialog v-model="showCategoryDialog" title="分类管理" width="600px" top="3vh" class="category-mgr-dialog">
      <div class="category-mgr-content">
      <div class="mgr-header-bar">
        <div class="mgr-header-left">
          <el-icon :size="18" color="#409EFF"><FolderOpened /></el-icon>
          <span>主题域 → 类目（最多两级）</span>
        </div>
        <div>
          <el-tag size="small" type="info" effect="plain" round>共 {{ totalNodeCount }} 个节点</el-tag>
          <el-button size="small" style="margin-left: 8px;" @click="openRoleDomainConfig">
            角色可见
          </el-button>
          <el-button size="small" type="primary" style="margin-left: 8px;" @click="handleAddRootCategory">
            <el-icon><Plus /></el-icon> 新增主题域
          </el-button>
        </div>
      </div>
        <el-tree
          :data="mgCategories"
          :props="{ label: 'categoryName', children: 'children' }"
          default-expand-all
          highlight-current
          node-key="id"
          :expand-on-click-node="false"
        >
          <template #default="{ node, data }">
            <div class="custom-tree-node" :class="'depth-' + Math.min(node.level, 3)">
              <div class="node-left">
                <el-icon v-if="node.level === 1" :size="16" color="#E6A23C"><FolderOpened /></el-icon>
                <el-icon v-else-if="node.level === 2" :size="14" color="#409EFF"><Folder /></el-icon>
                <el-icon v-else :size="12" color="#909399"><Document /></el-icon>
                <span class="node-label">{{ node.label }}</span>
              </div>
              <div class="node-actions">
                <el-button size="small" type="primary" link @click.stop="handleEditCategory(data)">
                  <el-icon><Edit /></el-icon> 编辑
                </el-button>
                <el-button v-if="node.level === 1" size="small" type="primary" link @click.stop="handleAddCategory(data)">
                  <el-icon><Plus /></el-icon> 添加类目
                </el-button>
                <el-button size="small" type="danger" link @click.stop="handleDeleteCategory(data)">
                  <el-icon><Delete /></el-icon> 删除
                </el-button>
              </div>
            </div>
          </template>
        </el-tree>
      </div>
      <template #footer>
        <el-button @click="showCategoryDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 主题域归属编辑对话框 -->
    <el-dialog v-model="showDomainDialog" title="主题域归属" width="540px" top="10vh">
      <div v-if="domainAssignAsset" style="margin-bottom: 12px; color: #606266; font-size: 13px;">
        资产：<b>{{ domainAssignAsset.assetName }}</b>
      </div>
      <el-form label-width="150px">
        <el-form-item v-for="d in categories.filter((x: any) => !x.parentId)" :key="d.id" :label="d.categoryName">
          <el-select v-model="domainAssignForm[d.id]" clearable placeholder="未归类" style="width: 100%;">
            <el-option v-for="c in (d.children || [])" :key="c.id" :label="c.categoryName" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <div style="color: #909399; font-size: 12px;">
        同一主题域下只能选择一个类目；同一张表可在不同主题域下分别归类。
      </div>
      <template #footer>
        <el-button @click="showDomainDialog = false">取消</el-button>
        <el-button type="primary" :loading="domainAssignSaving" @click="submitDomainAssign">保存</el-button>
      </template>
    </el-dialog>

    <!-- 角色可见主题域配置 -->
    <el-dialog v-model="showRoleDomainDialog" title="角色可见主题域" width="560px" top="8vh">
      <el-table :data="roleList" stripe max-height="420">
        <el-table-column prop="roleName" label="角色" width="130" />
        <el-table-column label="可见主题域">
          <template #default="{ row }">
            <el-select v-model="roleDomainMap[row.id]" multiple clearable placeholder="未配置=全部可见" style="width: 100%;">
              <el-option v-for="d in categories.filter((x: any) => !x.parentId)" :key="d.id" :label="d.categoryName" :value="d.id" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <div style="color: #909399; font-size: 12px; margin-top: 8px;">
        未配置任何主题域的角色默认可见全部；admin 管理员始终可见全部。
      </div>
      <template #footer>
        <el-button @click="showRoleDomainDialog = false">取消</el-button>
        <el-button type="primary" :loading="roleDomainSaving" @click="saveRoleDomainConfig">保存</el-button>
      </template>
    </el-dialog>

    <!-- 编辑分类对话框 -->
    <el-dialog v-model="showEditDialog" title="编辑分类" width="420px" top="30vh">
      <el-form :model="editForm" label-width="85px">
        <el-form-item label="分类名称">
          <el-input v-model="editForm.categoryName" placeholder="请输入分类名称" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmEditCategory">确认</el-button>
      </template>
    </el-dialog>

    <!-- 添加分类对话框 -->
    <el-dialog v-model="showAddDialog" title="添加分类" width="420px" top="30vh">
      <el-form :model="addForm" label-width="85px">
        <el-form-item label="所属上级">
          <el-input :model-value="addForm.parentPath" disabled>
            <template #prefix>
              <el-icon><FolderOpened /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="分类名称">
          <el-input v-model="addForm.name" placeholder="请输入分类名称" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmAddCategory">确认添加</el-button>
      </template>
    </el-dialog>

    <!-- 创建/编辑资产对话框 -->
    <el-dialog v-model="showCreateDialog" :title="isEdit ? '编辑资产' : '注册资产'" width="600px">
      <el-form :model="assetForm" label-width="100px">
        <el-form-item label="资产名称"><el-input v-model="assetForm.assetName" /></el-form-item>
        <el-form-item label="资产类型">
          <el-select v-model="assetForm.sourceMetadataType">
            <el-option v-for="t in assetTypes" :key="t.configCode" :label="t.configName" :value="t.configCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="资产分类">
          <el-tree-select
            v-model="assetForm.categoryId"
            :data="categories"
            node-key="id"
            :props="{ label: 'categoryName', children: 'children', disabled: isDomainNode }"
            placeholder="选择类目（需先在主题域下建类目）"
            clearable
            check-strictly
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="来源系统">
          <el-select v-model="assetForm.sourceSystemId" placeholder="选择来源系统" clearable>
            <el-option v-for="s in sourceSystemList" :key="s.id" :label="s.systemName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="表名"><el-input v-model="assetForm.tableName" /></el-form-item>
        <el-form-item label="业务描述"><el-input v-model="assetForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="submitAsset">确认</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入对话框 -->
    <el-dialog v-model="showImportDialog" title="批量导入资产" width="500px">
      <el-form :model="importForm" label-width="85px">
        <el-form-item label="资产分类">
          <el-tree-select
            v-model="importForm.categoryId"
            :data="categories"
            node-key="id"
            :props="{ label: 'categoryName', children: 'children', disabled: isDomainNode }"
            placeholder="选择类目"
            clearable
            check-strictly
          />
        </el-form-item>
        <el-form-item label="来源系统">
          <el-select v-model="importForm.sourceSystem" placeholder="选择来源系统" clearable>
            <el-option v-for="s in sourceSystemList" :key="s.id" :label="s.systemName" :value="s.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-upload
        drag
        :auto-upload="false"
        :on-change="handleImportFile"
        accept=".csv,.xlsx,.xls"
        :limit="1"
        style="margin-top: 12px;">
        <el-icon :size="40" color="#409EFF"><UploadFilled /></el-icon>
        <div style="margin-top: 8px;">拖拽文件到此处，或点击上传</div>
        <div style="color: #909399; font-size: 12px; margin-top: 4px;">支持 CSV、Excel (.xlsx) 格式</div>
      </el-upload>
      <div v-if="importResults.length > 0" style="margin-top: 16px;">
        <h4>导入结果：</h4>
        <div v-for="r in importResults" :key="r" style="font-size: 13px; padding: 2px 0;">{{ r }}</div>
      </div>
      <template #footer>
        <el-button @click="showImportDialog = false; importResults = []">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 来源系统管理对话框 -->
    <el-dialog v-model="showSourceSystemDialog" title="来源系统管理" width="550px" top="5vh">
      <div style="margin-bottom: 12px; display: flex; justify-content: flex-end;">
        <el-button type="primary" size="small" @click="handleAddSourceSystem">
          <el-icon><Plus /></el-icon> 新增
        </el-button>
      </div>
      <el-table :data="sourceSystemList" stripe max-height="400" style="width: 100%;">
        <el-table-column prop="systemCode" label="编码" width="100" />
        <el-table-column prop="systemName" label="名称" min-width="180" />
        <el-table-column label="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="65">
          <template #default="{ row, $index }">
            <el-button
              size="small"
              :type="row.status === 1 ? 'warning' : 'success'"
              text
              @click="handleToggleSourceSystem($index)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 新增/编辑来源系统对话框 -->
    <el-dialog v-model="showSourceSystemFormDialog" :title="isEditSourceSystem ? '编辑来源系统' : '新增来源系统'" width="420px" top="30vh">
      <el-form :model="sourceSystemForm" label-width="80px">
        <el-form-item label="系统编码">
          <el-input v-model="sourceSystemForm.systemCode" placeholder="如 HIS、LIS" />
        </el-form-item>
        <el-form-item label="系统名称">
          <el-input v-model="sourceSystemForm.systemName" placeholder="如 医院信息系统" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="sourceSystemForm.description" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="sourceSystemForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSourceSystemFormDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmSaveSourceSystem">确认</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { catalogApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TagProps } from 'element-plus'
import {
  Delete,
  Document,
  Edit,
  Folder,
  FolderOpened,
  Plus,
  Refresh,
  Setting,
  Upload,
  UploadFilled
} from "@element-plus/icons-vue";

const router = useRouter()
const keyword = ref('')
const assetType = ref('')
const securityLevel = ref('')
const categoryId = ref<number>()
const searchCategoryPath = ref<number>()
const cascaderRef = ref()
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const assetList = ref<any[]>([])
const categories = ref<any[]>([])
/** 分类管理用全量主题域树（不按角色过滤） */
const mgCategories = ref<any[]>([])
const categoryTypes = ref<any[]>([])
const currentType = ref('')

// 来源系统（从后端动态加载）
const sourceSystemList = ref<any[]>([])

// 数据字典（从后端动态加载）
const assetTypes = ref<any[]>([])
const securityLevels = ref<any[]>([])

// 来源系统管理
const showSourceSystemDialog = ref(false)
const showSourceSystemFormDialog = ref(false)
const isEditSourceSystem = ref(false)
const sourceSystemForm = ref<any>({})
const editingSourceIndex = ref(-1)

function handleAddSourceSystem() {
  isEditSourceSystem.value = false
  sourceSystemForm.value = { systemCode: '', systemName: '', description: '', status: 1 }
  showSourceSystemFormDialog.value = true
}

function handleEditSourceSystem(index: number) {
  isEditSourceSystem.value = true
  editingSourceIndex.value = index
  sourceSystemForm.value = { ...sourceSystemList.value[index] }
  showSourceSystemFormDialog.value = true
}

async function confirmSaveSourceSystem() {
  const data = sourceSystemForm.value
  if (!data.systemCode || !data.systemName) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  try {
    if (isEditSourceSystem.value) {
      await catalogApi.updateSourceSystem(data)
    } else {
      await catalogApi.createSourceSystem(data)
    }
    ElMessage.success(isEditSourceSystem.value ? '更新成功' : '创建成功')
    showSourceSystemFormDialog.value = false
    await loadSourceSystems()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function handleToggleSourceSystem(index: number) {
  const item = sourceSystemList.value[index]
  const newStatus = item.status === 1 ? 0 : 1
  const label = newStatus === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认${label}来源系统「${item.systemName}」？`, '提示')
    await catalogApi.updateSourceSystem({ id: item.id, status: newStatus })
    ElMessage.success(`${label}成功`)
    await loadSourceSystems()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

// 分类管理
const showCategoryDialog = ref(false)
const categoryTreeRef = ref()

const showCreateDialog = ref(false)
const showImportDialog = ref(false)
const isEdit = ref(false)
const assetForm = ref<any>({})
const importResults = ref<string[]>([])
const importForm = ref<{ categoryId: number | undefined; sourceSystem: string }>({ categoryId: undefined, sourceSystem: '' })

// 编辑分类
const showEditDialog = ref(false)
const editForm = ref<{ id: number | null; categoryName: string }>({ id: null, categoryName: '' })

function handleEditCategory(data: any) {
  editForm.value = { id: data.id, categoryName: data.categoryName || data.name }
  showEditDialog.value = true
}

async function confirmEditCategory() {
  const name = editForm.value.categoryName?.trim()
  if (!name) {
    ElMessage.warning('请输入分类名称')
    return
  }
  try {
    await catalogApi.updateCategory({ id: editForm.value.id, categoryName: name })
    ElMessage.success('编辑成功')
    showEditDialog.value = false
    await loadAllCategories()
    await loadCategories()
    calcTotalNodes()
  } catch (e: any) {
    ElMessage.error(e.message || '编辑失败')
  }
}

function handleAddRootCategory() {
  addForm.value = {
    parentPath: '（一级主题域）',
    name: '',
    parentData: null
  }
  showAddDialog.value = true
}

// 添加分类
const showAddDialog = ref(false)
const addForm = ref<{ parentPath: string; name: string; parentData: any }>({
  parentPath: '',
  name: '',
  parentData: null
})

const totalNodeCount = ref(0)

function calcTotalNodes() {
  let count = 0
  function walk(list: any[]) {
    for (const item of list) {
      count++
      if (item.children && item.children.length > 0) {
        walk(item.children)
      }
    }
  }
  walk(mgCategories.value)
  totalNodeCount.value = count
}

function handleAddCategory(data: any) {
  addForm.value = {
    parentPath: data.categoryName || data.name,
    name: '',
    parentData: data
  }
  showAddDialog.value = true
}

async function confirmAddCategory() {
  const name = addForm.value.name?.trim()
  if (!name) {
    ElMessage.warning('请输入分类名称')
    return
  }
  const parent = addForm.value.parentData
  try {
    await catalogApi.createCategory({
      categoryName: name,
      parentId: parent?.id || null,
      sortOrder: 1
    })
    ElMessage.success('添加成功')
    showAddDialog.value = false
    await loadAllCategories()
    await loadCategories()
    calcTotalNodes()
  } catch (e: any) {
    ElMessage.error(e.message || '添加失败')
  }
}

async function handleDeleteCategory(data: any) {
  const hasChildren = data.children && data.children.length > 0
  let msg = `确认删除分类「${data.categoryName || data.name}」？`
  if (hasChildren) {
    msg += ' 该分类下有子分类，删除后子分类也将被移除。'
  }
  try {
    await ElMessageBox.confirm(msg, '提示', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await catalogApi.deleteCategory(data.id)
    ElMessage.success('删除成功')
    await loadAllCategories()
    await loadCategories()
    calcTotalNodes()
  } catch (_e) {
    // 取消操作
  }
}

onMounted(async () => {
  loadDicts()
  await loadCategoryTypes()
  await loadCategories()
  loadSourceSystems()
  loadAssets()
})

async function loadDicts() {
  try {
    const [assetRes, levelRes] = await Promise.all([
      catalogApi.getConfig('asset_type'),
      catalogApi.getConfig('security_level')
    ])
    assetTypes.value = assetRes.data || []
    securityLevels.value = levelRes.data || []
  } catch (_e) {
    assetTypes.value = []
    securityLevels.value = []
  }
}

async function loadSourceSystems() {
  try {
    const res: any = await catalogApi.getSourceSystems()
    sourceSystemList.value = res.data || []
  } catch (_e) {
    sourceSystemList.value = []
  }
}

async function loadCategoryTypes() {
  try {
    const res: any = await catalogApi.getCategoryTypes()
    categoryTypes.value = res.data || []
    if (categoryTypes.value.length > 0) {
      currentType.value = categoryTypes.value[0].type
    }
  } catch (_e) {
    categoryTypes.value = []
  }
}

function onTypeChange() {
  loadCategories()
}

async function loadCategories() {
  // 主题域→类目两级树，按当前登录用户角色过滤可见主题域
  try {
    const res: any = await catalogApi.getCategoryDomains()
    categories.value = buildTree(res.data || [])
  } catch (_e) {
    categories.value = []
  }
}

/** 分类管理：加载全部主题域/类目（不受角色限制） */
async function loadAllCategories() {
  try {
    const res: any = await catalogApi.getAllCategoryDomains()
    mgCategories.value = buildTree(res.data || [])
  } catch (_e) {
    mgCategories.value = []
  }
}

/** 一级主题域不可直接作为资产分类选择，必须选其下类目 */
function isDomainNode(data: any): boolean {
  return !data.parentId
}

function buildTree(list: any[]): any[] {
  const map = new Map<number, any>()
  const tree: any[] = []
  list.forEach(item => map.set(item.id, { ...item, children: [] }))
  list.forEach(item => {
    if (item.parentId && map.has(item.parentId)) {
      map.get(item.parentId)!.children.push(map.get(item.id))
    } else {
      tree.push(map.get(item.id))
    }
  })
  return tree
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

// 数据分级映射（从数据字典动态加载）
const securityLevelMap: Record<string, string> = {}

// el-tag type 允许的取值集合
type TagType = NonNullable<TagProps['type']>

const securityTagTypeMap: Record<string, TagType> = {
  'public': 'info',
  'internal': 'info',
  'confidential': 'warning',
  'restricted': 'danger',
  'critical': 'danger'
}

function getSecurityLevelLabel(level: string | null | undefined): string {
  if (!level) return '-'
  if (Object.keys(securityLevelMap).length === 0 && securityLevels.value.length > 0) {
    securityLevels.value.forEach(s => { securityLevelMap[s.configCode] = s.configName })
  }
  return securityLevelMap[level] || level
}

function getSecurityTagType(level: string | null | undefined): TagType {
  return securityTagTypeMap[level || ''] || 'info'
}

async function openCategoryManager() {
  showCategoryDialog.value = true
  await loadAllCategories()
  await loadCategories()
  calcTotalNodes()
}

// ==================== 资产主题域归属 ====================
const showDomainDialog = ref(false)
const domainAssignAsset = ref<any>(null)
/** 主题域ID → 选中的类目ID */
const domainAssignForm = ref<Record<number, number | undefined>>({})
const domainAssignSaving = ref(false)

async function openDomainAssign(row: any) {
  domainAssignAsset.value = row
  domainAssignForm.value = {}
  try {
    const res: any = await catalogApi.getAssetCategories(row.id)
    for (const it of (res.data || [])) {
      domainAssignForm.value[it.subjectDomainId] = it.categoryId
    }
  } catch (_e) { /* 无归属时留空 */ }
  showDomainDialog.value = true
}

async function submitDomainAssign() {
  const assetId = domainAssignAsset.value?.id
  if (!assetId) return
  domainAssignSaving.value = true
  try {
    // 原归属，用于判断哪些主题域被取消
    const originRes: any = await catalogApi.getAssetCategories(assetId)
    const originMap: Record<number, number> = {}
    for (const it of (originRes.data || [])) originMap[it.subjectDomainId] = it.categoryId

    const domains = categories.value.filter((x: any) => !x.parentId)
    for (const d of domains) {
      const catId = domainAssignForm.value[d.id]
      if (catId) {
        await catalogApi.saveAssetCategory(assetId, { subjectDomainId: d.id, categoryId: catId })
      } else if (originMap[d.id]) {
        // 传空 categoryId 表示取消该主题域归属
        await catalogApi.saveAssetCategory(assetId, { subjectDomainId: d.id })
      }
    }
    ElMessage.success('主题域归属已保存')
    showDomainDialog.value = false
    loadAssets()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    domainAssignSaving.value = false
  }
}

// ==================== 角色可见主题域配置 ====================
const showRoleDomainDialog = ref(false)
const roleList = ref<any[]>([])
const roleDomainMap = ref<Record<number, number[]>>({})
const roleDomainSaving = ref(false)

async function openRoleDomainConfig() {
  try {
    const res: any = await catalogApi.getRoles()
    roleList.value = res.data || []
    const map: Record<number, number[]> = {}
    for (const r of roleList.value) {
      const d: any = await catalogApi.getRoleSubjectDomains(r.id)
      map[r.id] = d.data || []
    }
    roleDomainMap.value = map
    showRoleDomainDialog.value = true
  } catch (e: any) {
    ElMessage.error(e.message || '加载角色失败')
  }
}

async function saveRoleDomainConfig() {
  roleDomainSaving.value = true
  try {
    for (const r of roleList.value) {
      await catalogApi.saveRoleSubjectDomains(r.id, roleDomainMap.value[r.id] || [])
    }
    ElMessage.success('角色可见主题域已保存')
    showRoleDomainDialog.value = false
    await loadCategories()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    roleDomainSaving.value = false
  }
}

async function loadAssets() {
  const res: any = await catalogApi.searchAssets({
    keyword: keyword.value || undefined,
    assetType: assetType.value || undefined,
    categoryId: categoryId.value || undefined,
    securityLevel: securityLevel.value || undefined,
    // 资产目录按当前角色可见主题域收敛（元数据管理等其他页面不传，不受影响）
    enforceDomainPermission: true,
    page: page.value,
    pageSize: pageSize.value
  })
  assetList.value = res.data.records || []
  total.value = res.data.total || 0
}

function search() { page.value = 1; loadAssets() }

function resetSearch() {
  keyword.value = ''
  assetType.value = ''
  securityLevel.value = ''
  searchCategoryPath.value = undefined
  categoryId.value = undefined
  search()
}

function onCascaderChange(val: any) {
  categoryId.value = val || undefined
  search()
}

function onCategoryClick(data: any) {
  categoryId.value = data.id
  searchCategoryPath.value = undefined
  if (cascaderRef.value && typeof cascaderRef.value.clearCheckedNodes === 'function') {
    cascaderRef.value.clearCheckedNodes()
  }
  search()
}

function goDetail(row: any) {
  router.push(`/catalog-detail/${row.id}`)
}

function editAsset(row: any) {
  isEdit.value = true
  assetForm.value = {
    ...row,
    sourceMetadataType: (row.sourceMetadataType || '').toLowerCase()
  }
  showCreateDialog.value = true
}

async function submitAsset() {
  // 新表结构：资产类型存 source_metadata_type（大写枚举 TABLE/FILE/API/REPORT）
  if (assetForm.value.sourceMetadataType) {
    assetForm.value.sourceMetadataType = assetForm.value.sourceMetadataType.toUpperCase()
  }
  let savedId: number | undefined
  if (isEdit.value) {
    const res: any = await catalogApi.updateAsset(assetForm.value)
    savedId = res?.data?.id || assetForm.value.id
  } else {
    const res: any = await catalogApi.createAsset(assetForm.value)
    savedId = res?.data?.id
  }
  // 同步主题域归属（所选分类为二级类目时自动归入对应主题域）
  if (savedId && assetForm.value.categoryId) {
    try {
      await catalogApi.saveAssetCategory(savedId, { categoryId: assetForm.value.categoryId })
    } catch (_e) { /* 归属同步失败不回滚资产保存 */ }
  }
  ElMessage.success(isEdit.value ? '编辑成功' : '注册成功')
  showCreateDialog.value = false
  assetForm.value = {}
  isEdit.value = false
  loadAssets()
}

async function refreshRecordCounts() {
  ElMessage.info('正在同步记录数（估算值）...')
  try {
    const res: any = await catalogApi.refreshCounts()
    ElMessage.success('已同步 ' + (res.data?.updatedCount || 0) + ' 个资产的记录数')
    loadAssets()
  } catch (e: any) {
    ElMessage.error(e.message || '同步失败')
  }
}

async function handleImportFile(uploadFile: any) {
  const file = uploadFile.raw
  if (!file) return
  const isExcel = file.name.endsWith('.xlsx') || file.name.endsWith('.xls')
  try {
    const res: any = isExcel
      ? await catalogApi.importExcel(file, importForm.value.categoryId, importForm.value.sourceSystem || undefined)
      : await catalogApi.importCsv(file, importForm.value.categoryId, importForm.value.sourceSystem || undefined)
    importResults.value = res.data || []
    if (importResults.value.length > 0) loadAssets()
  } catch (e: any) {
    importResults.value = ['导入失败: ' + (e.message || '未知错误')]
  }
}
</script>

<style scoped>
/* 页面头部 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 搜索栏 */
.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* 分类管理对话框 */
.category-mgr-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.category-mgr-content {
  max-height: 60vh;
  overflow-y: auto;
  padding: 0 0 4px 0;
}

.mgr-header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  background: linear-gradient(135deg, #f0f5ff 0%, #e6f7ff 100%);
  border-bottom: 1px solid #e8e8e8;
  position: sticky;
  top: 0;
  z-index: 1;
}

.mgr-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

/* 树节点样式 */
.custom-tree-node {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex: 1;
  padding: 4px 8px 4px 4px;
  border-radius: 6px;
  transition: background-color 0.2s;
}

.custom-tree-node:hover {
  background-color: #ecf5ff;
}

.custom-tree-node:hover .el-button {
  opacity: 1;
}

.custom-tree-node .el-button {
  opacity: 0;
  transition: opacity 0.2s;
}

.node-left {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.node-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.node-label {
  font-size: 13px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 不同层级节点样式 */
.custom-tree-node.depth-1 .node-label {
  font-weight: 600;
  font-size: 14px;
  color: #E6A23C;
}

.custom-tree-node.depth-2 .node-label {
  font-weight: 500;
  font-size: 13.5px;
}

/* Element Tree 调整 */
.category-mgr-content :deep(.el-tree-node__content) {
  height: auto;
  padding: 6px 0;
  border-bottom: 1px solid transparent;
}

.category-mgr-content :deep(.el-tree-node__content:hover) {
  background-color: transparent;
}

.category-mgr-content :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: transparent;
}
</style>
