<template>
  <div class="config-page">
    <div class="config-header">
      <h2>系统配置</h2>
      <div class="header-actions">
        <el-button size="small" @click="showOverview = true"><el-icon><DataAnalysis /></el-icon> 配置总览</el-button>
        <el-button size="small" @click="showGuide = true"><el-icon><QuestionFilled /></el-icon> 使用指南</el-button>
      </div>
    </div>
    <el-row :gutter="12" style="flex:1; overflow: hidden;">
      <!-- 左侧：配置类型列表 -->
      <el-col :span="5" style="height: calc(100vh - 110px);">
        <el-card style="height: 100%; overflow-y: auto;">
          <template #header><span style="font-weight: 600;">配置类型</span></template>
          <div v-for="t in configTypes" :key="t.type" class="type-item" :class="{ active: currentType === t.type }" @click="switchType(t.type)">
            <el-icon><component :is="t.icon" /></el-icon>
            <span>{{ t.name }}</span>
          </div>
        </el-card>
      </el-col>
      <!-- 右侧：配置树 -->
      <el-col :span="19" style="height: calc(100vh - 110px);">
        <el-card style="height: 100%; display: flex; flex-direction: column;">
          <!-- 字典管理（sys_dict_type / sys_dict_data） -->
          <template v-if="currentType === '__dict__'">
            <div class="toolbar">
              <span style="font-weight: 600; font-size: 14px;">字典类型</span>
              <span style="color: #909399; font-size: 12px; margin-left: 8px;">共 {{ dictTypeTotal }} 个，点击行查看字典数据</span>
              <div style="flex:1;" />
              <el-input v-model="dictTypeKeyword" placeholder="搜索编码/名称" clearable style="width: 200px; margin-right: 8px;"
                @keyup.enter="searchDictTypes" @clear="searchDictTypes" />
              <el-button size="small" type="primary" @click="openDictTypeForm(null)"><el-icon><Plus /></el-icon> 新增类型</el-button>
            </div>
            <div style="flex:1; overflow-y: auto; display: flex; flex-direction: column;">
              <el-table :data="dictTypes" size="small" stripe highlight-current-row max-height="calc(100vh - 260px)" @row-click="openDictDataDrawer" style="cursor: pointer;">
                <el-table-column prop="dictTypeCode" label="类型编码" min-width="150" show-overflow-tooltip />
                <el-table-column prop="dictTypeName" label="类型名称" min-width="130" show-overflow-tooltip />
                <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
                <el-table-column label="状态" width="70">
                  <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template>
                </el-table-column>
                <el-table-column label="操作" width="140">
                  <template #default="{ row }">
                    <el-button size="small" text class="dict-op-btn" @click.stop="openDictTypeForm(row)">编辑</el-button>
                    <el-button size="small" text type="danger" class="dict-op-btn" @click.stop="removeDictType(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div style="margin-top: 12px; display: flex; justify-content: flex-end;">
                <el-pagination v-model:current-page="dictTypePage" v-model:page-size="dictTypePageSize" :total="dictTypeTotal"
                  layout="total, prev, pager, next, sizes" :page-sizes="[10, 20, 50]" background @change="loadDictTypesPage" />
              </div>
              <el-empty v-if="!dictTypes.length" description="暂无字典类型" :image-size="60" />
            </div>
          </template>
          <!-- 配置树（sys_config） -->
          <template v-else>
            <div class="toolbar">
              <span style="font-weight: 600; font-size: 14px;">{{ currentTypeName }}</span>
              <div style="flex:1;" />
              <el-button size="small" type="primary" @click="openAddDialog(null)"><el-icon><Plus /></el-icon> 新增</el-button>
            </div>
            <div style="flex:1; overflow-y: auto;">
              <el-tree
                ref="treeRef"
                :data="treeData"
                node-key="id"
                :props="{ label: 'configName', children: 'children' }"
                highlight-current
                default-expand-all
              >
                <template #default="{ node, data }">
                  <div class="tree-node">
                    <img v-if="isImageIcon(data.icon)" :src="iconSrc(data.icon)" class="tree-icon-img" />
                    <el-icon v-else><component :is="data.icon || 'Folder'" /></el-icon>
                    <span>{{ data.configName }}</span>
                    <span v-if="data.configCode" class="node-code">({{ data.configCode }})</span>
                    <el-tag v-if="data.status === 0" size="small" type="info">停用</el-tag>
                    <div class="node-actions">
                      <el-button size="small" text @click.stop="openAddDialog(data)"><el-icon><Plus /></el-icon></el-button>
                      <el-button size="small" text @click.stop="openEditDialog(data)"><el-icon><Edit /></el-icon></el-button>
                      <el-button size="small" text type="danger" @click.stop="handleDelete(data)"><el-icon><Delete /></el-icon></el-button>
                    </div>
                  </div>
                </template>
              </el-tree>
              <el-empty v-if="!loading && treeData.length === 0" description="暂无配置项" :image-size="60" />
            </div>
          </template>
        </el-card>
      </el-col>
    </el-row>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="showForm" :title="isEdit ? '编辑配置' : '新增配置'" width="500px" top="10vh">
      <el-form :model="form" label-width="100px" size="small">
        <el-form-item label="配置类型"><el-input :model-value="currentTypeName" disabled /></el-form-item>
        <el-form-item label="父节点"><el-input :model-value="form._parentName || '（顶级）'" disabled /></el-form-item>
        <el-form-item label="节点编码" required>
          <el-input v-model="form.configCode" placeholder="如 database_table" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="显示名称" required>
          <el-input v-model="form.configName" placeholder="如 数据库表" />
        </el-form-item>
        <el-form-item label="图标">
          <div class="icon-field">
            <el-input v-model="form.icon" placeholder="如 Folder, Table, Cpu" style="flex:1;" />
            <el-button size="small" @click="showIconPicker = true">图标库</el-button>
          </div>
        </el-form-item>
        <el-form-item label="配置值">
          <el-input v-model="form.configValue" type="textarea" :rows="4" :placeholder="configValuePlaceholder" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="用途说明（可选）" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form._enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 字典类型维护弹窗 -->
    <el-dialog v-model="showDictTypeForm" :title="isEditDictType ? '编辑字典类型' : '新增字典类型'" width="460px" top="15vh">
      <el-form :model="dictTypeForm" label-width="90px" size="small">
        <el-form-item label="类型编码" required>
          <el-input v-model="dictTypeForm.dictTypeCode" placeholder="如 asset_type、ticket_status" :disabled="isEditDictType" />
        </el-form-item>
        <el-form-item label="类型名称" required>
          <el-input v-model="dictTypeForm.dictTypeName" placeholder="如 资产类型、工单状态" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dictTypeForm.remark" type="textarea" :rows="2" placeholder="说明该字典被哪些字段引用" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="dictTypeForm._enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDictTypeForm = false">取消</el-button>
        <el-button type="primary" @click="submitDictTypeForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 字典数据抽屉（点击字典类型后弹出，分页展示该类型的数据） -->
    <el-drawer v-model="dictDataDrawerVisible" :title="`字典数据：${currentDictType?.dictTypeName || ''}`" size="55%" class="dict-data-drawer" @close="handleDictDataDrawerClose">
      <div class="dict-drawer-body">
        <div class="dict-drawer-header">
          <el-tag size="small" type="info">{{ currentDictType?.dictTypeCode }}</el-tag>
          <span v-if="currentDictType?.remark" style="margin-left: 8px; color: #909399; font-size: 13px;">{{ currentDictType.remark }}</span>
          <div style="flex:1;" />
          <el-button size="small" type="primary" @click="openDictDataForm(null)"><el-icon><Plus /></el-icon> 新增数据</el-button>
        </div>
        <div class="dict-drawer-table">
          <el-table :data="dictDataList" size="small" stripe height="100%" v-loading="dictDataLoading">
            <el-table-column prop="dictLabel" label="标签" :width="dictLabelWidth" min-width="110" show-overflow-tooltip />
            <el-table-column prop="dictValue" label="值" :width="dictValueWidth" min-width="90" show-overflow-tooltip />
            <el-table-column prop="dictExtra" label="扩展值" :width="dictExtraWidth" min-width="80" show-overflow-tooltip />
            <el-table-column prop="sortOrder" label="排序" width="60" />
            <el-table-column label="默认" width="60"><template #default="{ row }">{{ row.isDefault ? '是' : '' }}</template></el-table-column>
            <el-table-column label="状态" width="70">
              <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button size="small" text class="dict-op-btn" @click.stop="openDictDataForm(row)">编辑</el-button>
                <el-button size="small" text type="danger" class="dict-op-btn" @click.stop="removeDictData(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="dict-drawer-footer">
          <el-pagination v-model:current-page="dictDataPage" v-model:page-size="dictDataPageSize" :total="dictDataTotal"
            layout="total, prev, pager, next, sizes" :page-sizes="[10, 20, 50]" background @change="loadDictDataPage" />
        </div>
      </div>
    </el-drawer>

    <!-- 字典数据维护弹窗 -->
    <el-dialog v-model="showDictDataForm" :title="isEditDictData ? '编辑字典数据' : '新增字典数据'" width="460px" top="15vh">
      <el-form :model="dictDataForm" label-width="90px" size="small">
        <el-form-item label="所属类型"><el-input :model-value="currentDictType?.dictTypeName || '-'" disabled /></el-form-item>
        <el-form-item label="字典标签" required>
          <el-input v-model="dictDataForm.dictLabel" placeholder="界面展示名称，如 待处理" />
        </el-form-item>
        <el-form-item label="字典值" required>
          <el-input v-model="dictDataForm.dictValue" placeholder="代码存储的枚举值，如 0" />
        </el-form-item>
        <el-form-item label="扩展值">
          <el-input v-model="dictDataForm.dictExtra" placeholder="样式类名/颜色等 UI 辅助信息（可选）" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="dictDataForm.sortOrder" :min="0" :max="9999" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="默认值">
          <el-switch v-model="dictDataForm.isDefault" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="dictDataForm._enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dictDataForm.remarks" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDictDataForm = false">取消</el-button>
        <el-button type="primary" @click="submitDictDataForm">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="showOverview" title="配置总览" size="420px">
      <el-card shadow="never" style="margin-bottom: 12px;" v-if="overviewData">
        <div style="display: flex; gap: 16px; text-align: center;">
          <div style="flex:1;"><div style="font-size: 28px; color: #409EFF;">{{ overviewData.total }}</div><div>总配置数</div></div>
          <div style="flex:1;"><div style="font-size: 28px; color: #67C23A;">{{ overviewData.enabled }}</div><div>已启用</div></div>
          <div style="flex:1;"><div style="font-size: 28px; color: #F56C6C;">{{ overviewData.disabled }}</div><div>已停用</div></div>
        </div>
      </el-card>
    </el-drawer>

    <el-dialog v-model="showGuide" title="使用指南" width="500px" top="15vh">
      <div style="line-height: 1.8;">
        <p><b>系统配置</b> 管理平台中所有可配置项，包括左侧导航菜单、安全等级、资产类型等。</p>
        <ul>
          <li>左侧选择配置类型，右侧显示树形结构</li>
          <li>点击节点前的 <b>+</b> 可添加子节点</li>
          <li><b>导航菜单</b> 类型直接控制左侧菜单栏的显示内容</li>
          <li>节点编码创建后不可修改</li>
        </ul>
      </div>
    </el-dialog>

    <!-- 图标选择器 -->
    <el-dialog v-model="showIconPicker" title="选择图标" width="640px" top="5vh">
      <el-tabs v-model="iconTab">
        <el-tab-pane label="图片图标" name="image">
          <el-input v-model="iconKeyword" placeholder="搜索图标名称（支持中文/英文/文件名）" clearable size="small" style="margin-bottom: 8px;" />
          <div v-for="g in imageIconGroups" :key="g.group" class="icon-group">
            <div class="icon-group-title">{{ g.group }}（{{ g.items.length }}）</div>
            <div class="icon-grid">
              <div v-for="it in g.items" :key="it.path" class="icon-item" :class="{ active: form.icon === it.path }" :title="it.path" @click="selectImageIcon(it.path)">
                <img :src="iconSrc(it.path)" class="icon-img" />
                <span class="icon-name">{{ it.name }}</span>
              </div>
            </div>
          </div>
          <el-empty v-if="!imageIcons.length" description="未找到图标图片，请确认 public/image 下已生成 index.json" :image-size="60" />
        </el-tab-pane>
        <el-tab-pane label="组件图标" name="component">
          <div class="icon-grid">
            <div v-for="icon in iconList" :key="icon" class="icon-item" :class="{ active: form.icon === icon }" @click="selectIcon(icon)">
              <el-icon :size="20"><component :is="icon" /></el-icon>
              <span class="icon-name">{{ icon }}</span>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { configApi, catalogApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const configTypes = configApi.getTypes()
const currentType = ref('navigation')
const currentTypeName = computed(() => configTypes.find(t => t.type === currentType.value)?.name || '')
// 数据源类型的配置值为连接参数 JSON，给出模板提示
const configValuePlaceholder = computed(() => currentType.value === 'source_type'
  ? '连接参数 JSON，如 {"driverClass":"org.postgresql.Driver","urlTemplate":"jdbc:postgresql://%s:%d/%s","defaultPort":5432,"catalogMode":"database","pkStrategy":"pg_catalog","systemSchemas":"information_schema,pg_catalog"}'
  : '配置值（可选）')
const treeData = ref<any[]>([])
const loading = ref(false)

const showForm = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const form = ref<any>({})

const showOverview = ref(false)
const overviewData = ref<any>(null)
const showGuide = ref(false)
const showIconPicker = ref(false)
const iconTab = ref('image')
const iconKeyword = ref('')
const imageIcons = ref<{ group: string; name: string; path: string }[]>([])

const iconList = ['FolderOpened', 'Folder', 'Document', 'DataBoard', 'Connection', 'Cpu', 'Table', 'File', 'Setting', 'Search', 'Edit', 'Delete', 'Plus', 'Refresh', 'Download', 'Upload', 'View', 'CopyDocument', 'QuestionFilled', 'DataAnalysis', 'List', 'Menu', 'HomeFilled', 'Bell', 'User', 'Link', 'ChatDotSquare', 'Timer', 'Collection', 'Tickets', 'InfoFilled', 'Warning', 'SuccessFilled', 'CircleClose', 'Help', 'Magnet', 'Coin', 'Lock', 'SwitchButton', 'ArrowDown', 'Loading', 'Phone', 'Star', 'Share', 'Files', 'Postcard', 'Reading', 'Notebook', 'Monitor', 'Search', 'EditPen', 'Remove', 'ZoomIn', 'ZoomOut']

let flatMap = new Map<number, any>()

onMounted(() => { loadTree() })

// 打开图标库时加载离线图标信息（public/image 下的 index.json）
watch(showIconPicker, (v) => { if (v) loadImageIcons() })

async function loadImageIcons() {
  if (imageIcons.value.length) return
  const list: { group: string; name: string; path: string }[] = []
  try {
    const el = await fetch('/image/element-icons/index.json', { cache: 'no-cache' }).then(r => r.json())
    for (const it of el) list.push({ group: 'Element Plus 图标', name: it.name, path: 'element-icons/' + it.file })
  } catch (_e) { /* 图标库未生成时忽略 */ }
  try {
    const ds = await fetch('/image/ds-types/index.json', { cache: 'no-cache' }).then(r => r.json())
    for (const it of ds) list.push({ group: '数据源类型图标', name: it.name, path: 'ds-types/' + it.file })
  } catch (_e) { /* 图标库未生成时忽略 */ }
  imageIcons.value = list
}

/** 按分组+关键字过滤图片图标 */
const imageIconGroups = computed(() => {
  const kw = iconKeyword.value.trim().toLowerCase()
  const groups: { group: string; items: any[] }[] = []
  for (const it of imageIcons.value) {
    if (kw && !it.name.toLowerCase().includes(kw) && !it.path.toLowerCase().includes(kw)) continue
    let g = groups.find(x => x.group === it.group)
    if (!g) { g = { group: it.group, items: [] }; groups.push(g) }
    g.items.push(it)
  }
  return groups
})

/** 判断 icon 值是否为图片路径（否则视为 Element Plus 组件名，保持兼容） */
function isImageIcon(icon?: string) {
  return !!icon && (icon.includes('/') || /\.(svg|png|ico|webp)$/i.test(icon))
}

/** 将存储的图标路径转为可访问的 src */
function iconSrc(icon: string) {
  if (!icon) return ''
  return icon.startsWith('/') ? icon : '/image/' + icon
}

function selectImageIcon(path: string) {
  form.value.icon = path
  showIconPicker.value = false
}

async function loadTree() {
  loading.value = true
  try {
    const res: any = await configApi.getTree(currentType.value)
    treeData.value = res.data || []
    // 构建ID映射
    flatMap.clear()
    function walk(list: any[]) {
      for (const item of list) {
        flatMap.set(item.id, item)
        if (item.children?.length) walk(item.children)
      }
    }
    walk(treeData.value)
  } catch (_e) { treeData.value = [] }
  finally { loading.value = false }
}

function switchType(type: string) {
  currentType.value = type
  if (type === '__dict__') loadDictTypesPage()
  else loadTree()
}

function openAddDialog(parent: any) {
  const siblings = parent ? (parent.children || []) : treeData.value
  const maxSort = Math.max(0, ...siblings.map((i: any) => i.sortOrder || 0))
  form.value = {
    configType: currentType.value, configCode: '', configName: '',
    configValue: '', description: '',
    icon: 'Folder', sortOrder: maxSort + 10, status: 1, _enabled: true,
    parentId: parent?.id || null, _parentName: parent?.configName || ''
  }
  isEdit.value = false; showForm.value = true
}

function openEditDialog(row: any) {
  form.value = { ...row, _enabled: row.status !== 0, _parentName: row.parentId ? flatMap.get(row.parentId)?.configName : '' }
  isEdit.value = true; showForm.value = true
}

async function submitForm() {
  if (!form.value.configCode || !form.value.configName) { ElMessage.warning('请填写编码和名称'); return }
  saving.value = true
  try {
    const data = { ...form.value, status: form.value._enabled ? 1 : 0 }
    if (isEdit.value) { await configApi.update(data); ElMessage.success('更新成功') }
    else { await configApi.create(data); ElMessage.success('创建成功') }
    showForm.value = false; await loadTree()
  } catch (e: any) { ElMessage.error(e.message || '操作失败') }
  finally { saving.value = false }
}

async function handleDelete(row: any) {
  if (row.children?.length > 0) {
    ElMessage.warning(`该节点下有 ${row.children.length} 个子节点，请先删除子节点`)
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除「${row.configName}」？`, '提示', { type: 'warning' })
    await configApi.delete(row.id)
    ElMessage.success('删除成功'); await loadTree()
  } catch (e: any) { if (e?.message) ElMessage.error(e.message) }
}

watch(showOverview, async (v) => { if (v) { const r: any = await configApi.getStatistics(); overviewData.value = r.data } })

// ==================== 字典管理（sys_dict_type / sys_dict_data） ====================
const dictTypes = ref<any[]>([])
const dictTypeKeyword = ref('')
const dictTypePage = ref(1)
const dictTypePageSize = ref(10)
const dictTypeTotal = ref(0)
const currentDictType = ref<any>(null)
const dictDataList = ref<any[]>([])
const dictDataPage = ref(1)
const dictDataPageSize = ref(10)
const dictDataTotal = ref(0)
const dictDataLoading = ref(false)
const dictDataDrawerVisible = ref(false)
const showDictTypeForm = ref(false)
const isEditDictType = ref(false)
const dictTypeForm = ref<any>({})
const showDictDataForm = ref(false)
const isEditDictData = ref(false)
const dictDataForm = ref<any>({})

// 字典数据内容列宽（按当前页内容动态计算，超长由 show-overflow-tooltip 兜底）
const dictLabelWidth = ref(120)
const dictValueWidth = ref(100)
const dictExtraWidth = ref(80)

/** 估算文本渲染宽度（汉字/全角按 14px，ASCII 按 8.5px，另加单元格左右 padding） */
function estimateTextWidth(text: any) {
  if (text == null) return 0
  let w = 0
  for (const ch of String(text)) {
    w += ch.charCodeAt(0) > 255 ? 14 : 8.5
  }
  return Math.ceil(w) + 28
}

/** 根据当前页数据与表头宽度计算内容列宽（设上下限，内容超长由 tooltip 展示全文） */
function calcDictColWidths() {
  let label = estimateTextWidth('标签')
  let value = estimateTextWidth('值')
  let extra = estimateTextWidth('扩展值')
  for (const d of dictDataList.value) {
    label = Math.max(label, estimateTextWidth(d.dictLabel))
    value = Math.max(value, estimateTextWidth(d.dictValue))
    extra = Math.max(extra, estimateTextWidth(d.dictExtra))
  }
  dictLabelWidth.value = Math.min(360, Math.max(110, label))
  dictValueWidth.value = Math.min(340, Math.max(90, value))
  dictExtraWidth.value = Math.min(260, Math.max(80, extra))
}

/** 搜索字典类型：重置到第1页后按关键词加载（按编码/名称模糊匹配） */
function searchDictTypes() {
  dictTypePage.value = 1
  loadDictTypesPage()
}

/** 分页加载字典类型列表（含禁用，支持关键词搜索） */
async function loadDictTypesPage() {
  try {
    const res: any = await catalogApi.getDictTypesPage(dictTypeKeyword.value, dictTypePage.value, dictTypePageSize.value)
    dictTypes.value = res.data?.records || []
    dictTypeTotal.value = res.data?.total || 0
  } catch (_e) { dictTypes.value = []; dictTypeTotal.value = 0 }
}

/** 分页加载当前字典类型的数据（含禁用，供抽屉维护） */
async function loadDictDataPage() {
  if (!currentDictType.value) { dictDataList.value = []; dictDataTotal.value = 0; return }
  dictDataLoading.value = true
  try {
    const res: any = await catalogApi.getDictDataPage(currentDictType.value.dictTypeCode, dictDataPage.value, dictDataPageSize.value)
    dictDataList.value = res.data?.records || []
    dictDataTotal.value = res.data?.total || 0
    // 按内容动态适配列宽（短内容列收窄、长内容列加宽）
    calcDictColWidths()
  } catch (_e) { dictDataList.value = []; dictDataTotal.value = 0 }
  finally { dictDataLoading.value = false }
}

/** 点击字典类型行：右侧抽屉弹出该类型的字典数据（分页） */
function openDictDataDrawer(row: any) {
  if (!row) return
  currentDictType.value = row
  dictDataPage.value = 1
  dictDataDrawerVisible.value = true
  loadDictDataPage()
}

/** 关闭抽屉时重置分页状态 */
function handleDictDataDrawerClose() {
  dictDataList.value = []
  dictDataTotal.value = 0
  dictDataPage.value = 1
}

function openDictTypeForm(row: any) {
  isEditDictType.value = !!row
  dictTypeForm.value = row ? { ...row, _enabled: row.status !== 0 } : { dictTypeCode: '', dictTypeName: '', remark: '', status: 1, _enabled: true }
  showDictTypeForm.value = true
}

async function submitDictTypeForm() {
  if (!dictTypeForm.value.dictTypeCode || !dictTypeForm.value.dictTypeName) {
    ElMessage.warning('请填写类型编码和名称')
    return
  }
  try {
    const data = { ...dictTypeForm.value, status: dictTypeForm.value._enabled ? 1 : 0 }
    if (isEditDictType.value) { await catalogApi.updateDictType(data); ElMessage.success('更新成功') }
    else { await catalogApi.createDictType(data); ElMessage.success('创建成功') }
    showDictTypeForm.value = false
    await loadDictTypesPage()
  } catch (e: any) { ElMessage.error(e.message || '操作失败') }
}

async function removeDictType(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除字典类型「${row.dictTypeName}」及其全部数据？`, '提示', { type: 'warning' })
    await catalogApi.deleteDictType(row.id)
    ElMessage.success('删除成功')
    currentDictType.value = null
    // 删除后当前页为空且非第一页时回退一页
    if (dictTypes.value.length === 1 && dictTypePage.value > 1) {
      dictTypePage.value -= 1
    }
    await loadDictTypesPage()
  } catch (e: any) { if (e?.message) ElMessage.error(e.message) }
}

function openDictDataForm(row: any) {
  if (!currentDictType.value) return
  isEditDictData.value = !!row
  dictDataForm.value = row
    ? { ...row, _enabled: row.status !== 0 }
    : {
        dictTypeId: currentDictType.value.id, dictLabel: '', dictValue: '', dictExtra: '',
        // 默认排序号 = 当前已加载数据的最大排序号 + 10（步长预留便于中间插入，且新数据排到现有数据之后）
        sortOrder: Math.max(0, ...dictDataList.value.map((i: any) => i.sortOrder || 0)) + 10,
        isDefault: false, remarks: '', status: 1, _enabled: true
      }
  showDictDataForm.value = true
}

async function submitDictDataForm() {
  if (!dictDataForm.value.dictLabel || !dictDataForm.value.dictValue) {
    ElMessage.warning('请填写字典标签和值')
    return
  }
  try {
    const data = { ...dictDataForm.value, status: dictDataForm.value._enabled ? 1 : 0 }
    if (isEditDictData.value) { await catalogApi.updateDictData(data); ElMessage.success('更新成功') }
    else { await catalogApi.createDictData(data); ElMessage.success('创建成功') }
    showDictDataForm.value = false
    await loadDictDataPage()
  } catch (e: any) { ElMessage.error(e.message || '操作失败') }
}

async function removeDictData(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除字典数据「${row.dictLabel}」？`, '提示', { type: 'warning' })
    await catalogApi.deleteDictData(row.id)
    ElMessage.success('删除成功')
    // 删除后当前页为空且非第一页时回退一页
    if (dictDataList.value.length === 1 && dictDataPage.value > 1) {
      dictDataPage.value -= 1
    }
    await loadDictDataPage()
  } catch (e: any) { if (e?.message) ElMessage.error(e.message) }
}

function selectIcon(icon: string) {
  form.value.icon = icon
  showIconPicker.value = false
}
</script>

<style scoped>
.config-page { height: 100vh; display: flex; flex-direction: column; padding: 0 16px; }
.config-header { display: flex; justify-content: space-between; align-items: center; padding: 12px 0; flex-shrink: 0; }
.header-actions { display: flex; gap: 8px; }
.type-item { display: flex; align-items: center; gap: 8px; padding: 8px 10px; cursor: pointer; border-radius: 6px; margin: 2px 0; }
.type-item:hover { background: #ecf5ff; }
.type-item.active { background: #ecf5ff; color: #409EFF; font-weight: 600; }
.toolbar { display: flex; align-items: center; padding: 0 0 8px 0; flex-shrink: 0; }
.tree-node { display: flex; align-items: center; gap: 4px; font-size: 13px; width: 100%; }
.node-code { color: #909399; font-size: 11px; margin-left: 4px; }
.node-actions { margin-left: auto; display: none; gap: 2px; }
.el-tree-node__content:hover .node-actions { display: flex; }

/* 字典数据抽屉：内容 flex 纵向布局，表格高度自适应抽屉剩余空间 */
.dict-data-drawer :deep(.el-drawer__body) {
  display: flex;
  flex-direction: column;
  height: calc(100% - 56px);
  padding: 16px 20px;
  overflow: hidden;
}
.dict-drawer-body {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.dict-drawer-header {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  flex-shrink: 0;
}
.dict-drawer-table {
  flex: 1;
  min-height: 0;
}
.dict-drawer-footer {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}

/* 操作列文字按钮紧凑排列：减小内边距与间距，强制单行不换行 */
.el-button.dict-op-btn {
  padding: 4px 6px;
  margin-left: 0;
  white-space: nowrap;
}
.el-button.dict-op-btn + .el-button.dict-op-btn {
  margin-left: 2px;
}

.icon-field { display: flex; gap: 4px; width: 100%; }
.icon-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 8px; max-height: 360px; overflow-y: auto; }
.icon-item { display: flex; flex-direction: column; align-items: center; padding: 8px; cursor: pointer; border-radius: 6px; font-size: 11px; color: #606266; }
.icon-group { margin-bottom: 12px; }
.icon-group-title { font-size: 12px; color: #909399; margin: 4px 0 8px; font-weight: 600; }
.icon-img { width: 20px; height: 20px; object-fit: contain; }
.icon-name { max-width: 72px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tree-icon-img { width: 16px; height: 16px; object-fit: contain; }
.icon-item:hover { background: #ecf5ff; }
.icon-item.active { background: #409eff; color: #fff; }
</style>
