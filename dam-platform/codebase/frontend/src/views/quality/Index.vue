<template>
  <div class="page-container">
    <div class="page-header"><h2>数据质量管理</h2></div>

    <!-- 概览卡片 -->
    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="6" v-for="s in overviewStats" :key="s.label">
        <el-card shadow="hover">
          <div style="text-align: center; padding: 15px 0;">
            <div class="card-value">{{ s.value }}</div>
            <div class="card-label">{{ s.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="质量规则" name="rules">
        <el-card>
          <div class="search-bar">
            <el-input v-model="keyword" placeholder="搜索规则名称" clearable style="width: 280px;" @keyup.enter="search" />
            <el-select v-model="ruleType" placeholder="规则类型" clearable style="width: 160px;" @change="search">
              <el-option label="完整性" value="completeness" />
              <el-option label="准确性" value="accuracy" />
              <el-option label="一致性" value="consistency" />
              <el-option label="及时性" value="timeliness" />
              <el-option label="唯一性" value="uniqueness" />
              <el-option label="有效性" value="validity" />
            </el-select>
            <el-button type="primary" @click="search">查询</el-button>
            <el-button type="success" @click="showCreate = true">新建规则</el-button>
            <el-button :loading="executingAll" @click="handleExecuteAll">执行全部</el-button>
          </div>
          <el-table :data="ruleList" stripe>
            <el-table-column prop="ruleCode" label="规则编码" min-width="180" />
            <el-table-column prop="ruleType" label="规则类型" width="100">
              <template #default="{ row }"><el-tag size="small">{{ row.ruleType }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="ruleLevel" label="级别" width="100" />
            <el-table-column prop="severity" label="严重级别" width="100">
              <template #default="{ row }">
                <el-tag :type="row.severity === 'high' ? 'danger' : row.severity === 'medium' ? 'warning' : 'info'" size="small">{{ row.severity }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="expectValue" label="期望值" width="100" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }"><el-tag :type="Number(row.status) === 1 ? 'success' : 'info'" size="small">{{ Number(row.status) === 1 ? '启用' : '停用' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button size="small" text @click="viewChecks(row)">检测历史</el-button>
                <el-button size="small" text :loading="executingId === row.id" @click="handleExecute(row.id)">执行</el-button>
                <el-button size="small" text type="danger" @click="deleteRule(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="检测记录" name="checks">
        <el-card>
          <el-table :data="checkList" stripe>
            <el-table-column prop="ruleId" label="规则ID" width="100" />
            <el-table-column prop="checkTime" label="检测时间" width="180" />
            <el-table-column prop="totalCount" label="总记录数" width="120" />
            <el-table-column prop="errorCount" label="异常数" width="120" />
            <el-table-column prop="errorRate" label="异常率" width="120">
              <template #default="{ row }">{{ (row.errorRate * 100).toFixed(2) }}%</template>
            </el-table-column>
            <el-table-column prop="score" label="质量得分" width="120">
              <template #default="{ row }">{{ row.score?.toFixed(2) || '-' }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80" />
          </el-table>
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="质量趋势" name="trend">
        <el-card>
          <div ref="trendChartRef" style="height: 350px;"></div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showCreate" title="新建质量规则" width="500px">
      <el-form :model="ruleForm" label-width="100px">
        <el-form-item label="规则编码"><el-input v-model="ruleForm.ruleCode" /></el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="ruleForm.ruleType">
            <el-option label="完整性" value="completeness" /><el-option label="准确性" value="accuracy" />
            <el-option label="一致性" value="consistency" /><el-option label="唯一性" value="uniqueness" />
            <el-option label="有效性" value="validity" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则级别">
          <el-select v-model="ruleForm.ruleLevel">
            <el-option label="关键" value="critical" /><el-option label="重要" value="important" /><el-option label="一般" value="normal" />
          </el-select>
        </el-form-item>
        <el-form-item label="严重级别">
          <el-select v-model="ruleForm.severity">
            <el-option label="高" value="high" /><el-option label="中" value="medium" /><el-option label="低" value="low" />
          </el-select>
        </el-form-item>
        <el-form-item label="期望值"><el-input v-model="ruleForm.expectValue" /></el-form-item>
        <el-form-item label="校验SQL"><el-input v-model="ruleForm.ruleExpression" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="submitRule">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, shallowRef, watch } from 'vue'
import { qualityApi } from '@/api'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

const activeTab = ref('rules')
const keyword = ref('')
const ruleType = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const ruleList = ref<any[]>([])
const checkList = ref<any[]>([])
const showCreate = ref(false)
const executingAll = ref(false)
const executingId = ref<number>()
const ruleForm = ref<any>({ severity: 'medium' })
const trendChartRef = shallowRef<HTMLElement>()
const overviewStats = ref([
  { label: '总规则数', value: '-' },
  { label: '启用规则', value: '-' },
  { label: '平均评分', value: '-' },
  { label: '最后检测', value: '-' }
])

onMounted(() => {
  loadRules()
  loadOverview()
})

async function loadRules() {
  const res: any = await qualityApi.searchRules({
    keyword: keyword.value || undefined,
    ruleType: ruleType.value || undefined,
    page: page.value,
    pageSize: pageSize.value
  })
  ruleList.value = res.data.records || []
  total.value = res.data.total || 0
}

function search() { page.value = 1; loadRules() }

async function viewChecks(row: any) {
  const res: any = await qualityApi.getCheckHistory(row.id)
  checkList.value = res.data || []
  activeTab.value = 'checks'
}

async function deleteRule(id: number) {
  await qualityApi.deleteRule(id)
  ElMessage.success('删除成功')
  loadRules()
}

async function handleExecute(id: number) {
  executingId.value = id
  try {
    const res: any = await qualityApi.executeRule(id)
    ElMessage.success('检测完成, 得分: ' + (res.data?.score || '-'))
  } catch (e: any) {
    ElMessage.error('执行失败')
  } finally {
    executingId.value = undefined
  }
}

async function handleExecuteAll() {
  executingAll.value = true
  try {
    await qualityApi.executeAll()
    ElMessage.success('全部规则执行完成')
  } catch (e: any) {
    ElMessage.error('执行失败')
  } finally {
    executingAll.value = false
  }
}

async function submitRule() {
  await qualityApi.createRule(ruleForm.value)
  ElMessage.success('创建成功')
  showCreate.value = false
  ruleForm.value = { severity: 'medium' }
  loadRules()
}

async function loadOverview() {
  try {
    const res: any = await qualityApi.getOverview()
    if (res.data) {
      overviewStats.value = [
        { label: '总规则数', value: res.data.totalRules || 0 },
        { label: '启用规则', value: res.data.enabledRules || 0 },
        { label: '平均评分', value: res.data.avgScore ?? '-' },
        { label: '最后检测', value: res.data.lastCheckTime ? new Date(res.data.lastCheckTime).toLocaleDateString() : '-' }
      ]
    }
  } catch (e) { /* ignore */ }
}

// 监听tab切换加载趋势图
watch(activeTab, async (val) => {
  if (val === 'trend') {
    await nextTick()
    loadTrendChart()
  }
})

async function loadTrendChart() {
  if (!trendChartRef.value) return
  try {
    const res: any = await qualityApi.getTrend(undefined, 30)
    const data = res.data
    if (!data || !data.dates) return
    const chart = echarts.init(trendChartRef.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: data.dates, axisLabel: { rotate: 45 } },
      yAxis: { type: 'value', min: 0, max: 100 },
      series: [{
        name: '质量评分',
        type: 'line',
        data: data.scores,
        smooth: true,
        areaStyle: { opacity: 0.3 },
        itemStyle: { color: '#67C23A' }
      }]
    })
    window.addEventListener('resize', () => chart.resize())
  } catch (e) { /* ignore */ }
}
</script>
