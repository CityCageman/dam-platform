<template>
  <div class="page-container">
    <div class="page-header">
      <h2>数据源管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="showForm = true; isEdit = false; form = { dsName: '', dsType: 'MYSQL', dsIp: 'localhost', dsPort: 3306, databaseName: '', username: 'root', password: '', description: '' }">
          <el-icon><Plus /></el-icon> 新增数据源
        </el-button>
      </div>
    </div>
    <el-card>
      <el-table :data="dataSources" stripe highlight-current-row @row-click="editRow" style="width: 100%; cursor: pointer;">
        <el-table-column prop="dsName" label="数据源名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="dsType" label="类型" width="90" />
        <el-table-column prop="dsIp" label="主机" width="140" />
        <el-table-column prop="dsPort" label="端口" width="65" />
        <el-table-column prop="databaseName" label="数据库" width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="65">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启停" width="70">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :loading="row._toggling"
              inline-prompt
              active-text="开"
              inactive-text="关"
              @click.stop
              @change="(val) => toggleStatus(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ row.createdAt?.replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="55" fixed="right">
          <template #default="{ row }">
            <el-button text @click.stop="editRow(row)">
              <el-icon><Edit /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑数据源对话框 -->
    <el-dialog v-model="showForm" :title="isEdit ? '编辑数据源' : '新增数据源'" width="520px" top="10vh">
      <el-form :model="form" label-width="100px">
        <el-form-item label="数据源名称"><el-input v-model="form.dsName" placeholder="例如: 生产HIS库" /></el-form-item>
        <el-form-item label="数据源类型">
          <el-select v-model="form.dsType">
            <el-option v-for="t in sourceTypes" :key="t.configCode" :label="t.configName" :value="t.configCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机地址"><el-input v-model="form.dsIp" placeholder="localhost" /></el-form-item>
        <el-form-item label="端口"><el-input-number v-model="form.dsPort" :min="1" :max="65535" /></el-form-item>
        <el-form-item label="数据库名"><el-input v-model="form.databaseName" /></el-form-item>
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" :placeholder="isEdit ? '留空则不修改原密码' : '请输入密码'" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button :loading="testing" @click="testConnection">测试连接</el-button>
        <el-button type="primary" @click="submitForm">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { catalogApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { encryptPassword } from '@/utils/crypto'

const dataSources = ref<any[]>([])
const showForm = ref(false)
const isEdit = ref(false)
const form = ref<any>({})
const testing = ref(false)

/** 已有密码时的掩码占位符（提交时会被过滤，表示不修改） */
const PWD_MASK = '******'

const sourceTypes = ref<any[]>([])

onMounted(() => {
  loadDict()
  load()
})

async function loadDict() {
  try {
    const res: any = await catalogApi.getDataSourceTypeSelect()
    sourceTypes.value = res.data || []
  } catch (_e) {
    // 降级到旧配置
    try {
      const res: any = await catalogApi.getConfig('source_type')
      sourceTypes.value = res.data || []
    } catch (_e2) {
      sourceTypes.value = []
    }
  }
}

async function load() {
  const res: any = await catalogApi.getDataSources()
  dataSources.value = res.data || []
}

function editRow(row: any) {
  isEdit.value = true
  form.value = { ...row }
  // dsPort 为字符串类型，转为数字供 el-input-number 绑定
  if (form.value.dsPort != null) form.value.dsPort = Number(form.value.dsPort)
  // 已有密码时用掩码占位表示"已设置"，不泄露真实密码；留空保存=不修改
  form.value.password = row.hasPassword ? PWD_MASK : ''
  showForm.value = true
}

async function deleteRow(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该数据源？', '提示')
    await catalogApi.deleteDataSource(id)
    ElMessage.success('删除成功')
    load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

async function toggleStatus(row: any, newVal: string | number | boolean) {
  const newStatus = newVal ? 1 : 0
  const label = newStatus === 1 ? '启用' : '停用'
  row._toggling = true
  try {
    await catalogApi.updateDataSource({ id: row.id, status: newStatus })
    row.status = newStatus
    ElMessage.success(`${label}成功`)
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    row._toggling = false
  }
}

async function testConnection() {
  if (!form.value.dsIp || !form.value.databaseName) {
    ElMessage.warning('请先填写主机地址和数据库名')
    return
  }
  testing.value = true
  try {
    if (isEdit.value && form.value.id && form.value.password === PWD_MASK) {
      // 编辑且未修改密码：凭据由后端按ID安全读取，不传密码
      await catalogApi.testDataSourceConnection({ dataSourceId: form.value.id })
    } else {
      await catalogApi.testDataSourceConnection({
        dsType: form.value.dsType,
        dsIp: form.value.dsIp,
        dsPort: form.value.dsPort,
        databaseName: form.value.databaseName,
        username: form.value.username,
        password: await encryptPassword(form.value.password)
      })
    }
    ElMessage.success('连接成功！')
  } catch (e: any) {
    ElMessage.error(e.message || '连接失败')
  } finally {
    testing.value = false
  }
}

async function submitForm() {
  try {
    // 密码用 catalog 服务 RSA 公钥加密传输；掩码/空表示不修改
    const payload: any = { ...form.value }
    if (payload.password && payload.password !== PWD_MASK) {
      payload.password = await encryptPassword(payload.password)
    } else if (isEdit.value) {
      delete payload.password
    }
    if (isEdit.value) {
      await catalogApi.updateDataSource(payload)
    } else {
      await catalogApi.createDataSource(payload)
    }
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    showForm.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}
</script>
