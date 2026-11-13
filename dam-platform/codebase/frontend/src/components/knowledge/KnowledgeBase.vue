<template>
  <div class="kb-wrap">
    <aside class="kb-side panel">
      <div class="kb-side-head">
        <span>知识分类</span>
        <el-button text type="primary" size="small" @click="openCategory()">+ 新增</el-button>
      </div>
      <div class="kb-cat-list">
        <div v-for="c in categories" :key="c.id" class="kb-cat"
             :class="{ active: selectedCategoryId === c.id }"
             :style="{ marginLeft: ((c.categoryLevel || 1) - 1) * 14 + 'px' }"
             @click="selectCategory(c.id)">
          <div class="kb-cat-main">
            <b>{{ c.categoryName }} <em class="kb-cat-count">{{ categoryCount(c.id) }}</em></b>
            <span>{{ c.description || c.categoryPath || c.categoryCode }}</span>
          </div>
          <div class="kb-cat-actions" @click.stop>
            <el-button text size="small" @click="openCategory(c)">编辑</el-button>
            <el-button text size="small" @click="openCategory(null, c.id)">子分类</el-button>
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
          <el-dropdown trigger="click" @command="handleAddCommand">
            <el-button type="primary">
              新增知识<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="manual">手工新增</el-dropdown-item>
                <el-dropdown-item command="source">从其他模块同步</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button type="success" :loading="agentSyncing" :disabled="!external.configured" @click="agentSync">
            Agent 同步
          </el-button>
        </div>
      </div>

      <el-table :data="entries" stripe v-loading="loading">
        <el-table-column label="标题" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openEntryDetail(row)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="95">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ entryTypeLabel(row.entryType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="definition" label="解释" min-width="240" show-overflow-tooltip />
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
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="primary" @click="openEntry(row)">编辑</el-button>
            <el-button text size="small" @click="refreshEntryStatus(row)">状态</el-button>
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
        <el-form-item label="父分类">
          <el-select v-model="categoryForm.parentId" clearable placeholder="不选则为一级分类" style="width: 100%;">
            <el-option v-for="c in parentOptions" :key="c.id" :label="c.categoryPath || c.categoryName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类路径"><el-input :model-value="categoryForm.categoryPath" disabled placeholder="保存后自动生成" /></el-form-item>
        <el-form-item label="分类说明">
          <el-input v-model="categoryForm.description" type="textarea" :rows="3"
                    placeholder="解释这个分类的边界。例如：业务术语用于解释病人号、就诊号等；指标解释用于说明指标口径。" />
        </el-form-item>
        <div class="form-tip">分类说明会显示在左侧分类下，帮助业务人员理解这个分类放什么内容。</div>
        <el-form-item label="排序"><el-input-number v-model="categoryForm.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="categoryForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showCategory = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveCategory">保存</el-button></template>
    </el-dialog>

    <!-- 条目 -->
    <el-dialog v-model="showEntry" class="knowledge-entry-dialog" width="960px" top="4vh">
      <template #header>
        <div class="entry-dialog-header">
          <span class="entry-dialog-title">{{ entryForm.id ? '编辑知识' : '新增知识' }}</span>
          <div class="entry-dialog-actions">
            <el-button v-if="entryForm.title" :loading="aiClassifying" @click="aiClassifyEntry">AI 归类</el-button>
            <el-button @click="showEntry = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="saveEntry">保存</el-button>
          </div>
        </div>
      </template>
      <el-form :model="entryForm" label-width="80px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="分类" required>
              <el-select v-model="entryForm.categoryId" style="width: 100%;">
                <el-option v-for="c in categories" :key="c.id" :label="c.categoryPath || c.categoryName" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="知识类型">
              <el-select v-model="entryForm.entryType" style="width: 100%;">
                <el-option label="业务术语" value="TERM" />
                <el-option label="指标解释" value="METRIC" />
                <el-option label="标准与编码" value="STANDARD" />
                <el-option label="案例与变更" value="CASE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="标题" required><el-input v-model="entryForm.title" placeholder="如：病人号" /></el-form-item>
        <el-form-item v-if="entryForm.entryType === 'METRIC'" label="归属指标"
                      :required="entryForm.publishStatus === 'PUBLISHED'">
          <el-select v-model="entryForm.metricId" filterable clearable style="width: 100%;"
                     placeholder="选择指标库中的指标">
            <el-option v-for="m in metricOptions" :key="m.id" :label="metricLabel(m)" :value="m.id" />
          </el-select>
          <div class="field-help">
            指标解释在发布前必须归属指标库；草稿可以先不关联。
            公式、来源表、SQL 和取数规则以指标库为准，知识库只维护解释和上下文。
          </div>
        </el-form-item>
        <el-alert v-if="entryForm.entryType === 'METRIC'" class="metric-boundary-tip" type="warning" :closable="false" show-icon
                  title="当前是“指标解释”，不是指标定义：这里不维护公式、SQL 和取数规则。" />
        <el-alert v-if="entryForm.entryType === 'METRIC' && selectedMetric" class="metric-authority" type="success" :closable="false" show-icon>
          <template #title>
            指标库口径：{{ selectedMetric.metricName }}
            <span v-if="selectedMetric.formula">｜公式：{{ selectedMetric.formula }}</span>
            <span v-if="selectedMetric.unit">｜单位：{{ selectedMetric.unit }}</span>
          </template>
          <div v-if="selectedMetric.sourceTables" class="metric-detail">来源表：{{ selectedMetric.sourceTables }}</div>
          <div v-if="selectedMetric.sqlExpression" class="metric-detail">参考 SQL 来自指标库，知识库不单独维护。</div>
        </el-alert>
        <el-form-item label="解释">
          <el-input v-model="entryForm.definition" type="textarea"
                    :autosize="{ minRows: 4, maxRows: 18 }"
                    placeholder="如：病人号是患者唯一号，也叫主索引 ID；与病人相关的内容都需要通过病人号关联。" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="别名"><el-input v-model="entryForm.aliasesText" placeholder="逗号分隔，如：患者唯一号、主索引 ID" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="来源系统"><el-input v-model="entryForm.sourceSystem" placeholder="如：HIS / 患者主索引" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="字段映射">
          <el-input v-model="entryForm.fieldMappingsText" type="textarea"
                    :autosize="{ minRows: 2, maxRows: 10 }"
                    placeholder="一行一个，如：PA_PatMas.PAPMI_No → 病人号" />
          <div class="field-help">
            说明业务术语/指标对应哪张表、哪个字段，以及关联关系。上面示例表示：
            PA_PatMas 表的 PAPMI_No 字段就是“病人号”。
          </div>
        </el-form-item>
        <el-form-item v-if="entryForm.entryType !== 'METRIC'" label="参考 SQL">
          <el-input v-model="entryForm.sqlExample" type="textarea"
                    :autosize="{ minRows: 2, maxRows: 16 }"
                    placeholder="可选：维护该指标/术语的准确取数 SQL，AI 报表会优先沿用其表、关联和口径" />
        </el-form-item>
        <el-collapse v-if="entryForm.entryType !== 'METRIC'" class="rule-collapse">
          <el-collapse-item title="取数规则（业务口径，AI 报表按此生成）" name="rule">
            <el-alert class="rule-tip" type="info" :closable="false" show-icon
                      title="不需要直接写 SQL：先描述统计粒度、去重方式、必用表和条件，可点“AI 起草取数规则”。" />
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="规则状态">
                  <el-select v-model="entryForm.reportSpec.ruleStatus" style="width: 100%;">
                    <el-option label="草稿" value="DRAFT" />
                    <el-option label="待审核" value="REVIEW" />
                    <el-option label="已发布" value="PUBLISHED" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12"><el-form-item label="统计粒度"><el-input v-model="entryForm.reportSpec.statisticalGrain" placeholder="如：就诊号 + 日期 + 科室" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12"><el-form-item label="去重键"><el-input v-model="entryForm.reportSpec.dedupKeys" placeholder="如：PAADM_RowId" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="必用表"><el-input v-model="entryForm.reportSpec.requiredTables" placeholder="如：PA_Adm、DHC_PatBillOrder" /></el-form-item></el-col>
            </el-row>
            <el-form-item label="业务条件">
              <el-input v-model="entryForm.reportSpec.conditions" type="textarea"
                        :autosize="{ minRows: 2, maxRows: 8 }" placeholder="如：有费用产生；排除测试患者；排除测试科室" />
            </el-form-item>
            <el-form-item label="主报表 SQL">
              <el-input v-model="entryForm.reportSpec.mainSql" type="textarea"
                        :autosize="{ minRows: 2, maxRows: 14 }" placeholder="可选：主报表模板，AI 会优先沿用" />
            </el-form-item>
            <el-form-item label="下钻 SQL">
              <el-input v-model="entryForm.reportSpec.drillSql" type="textarea"
                        :autosize="{ minRows: 2, maxRows: 14 }" placeholder="可选：下钻明细模板" />
            </el-form-item>
            <el-button size="small" :loading="aiClassifying" @click="aiDraftReportSpec">AI 起草取数规则</el-button>
          </el-collapse-item>
        </el-collapse>
        <el-form-item label="使用说明">
          <el-input v-model="entryForm.usageNote" type="textarea"
                    :autosize="{ minRows: 2, maxRows: 10 }"
                    placeholder="如：患者、就诊、费用等数据关联时使用该字段。" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="相关指标"><el-input v-model="entryForm.relatedMetricsText" placeholder="其他相关指标，逗号分隔，如：门诊量、住院人次" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="负责人"><el-input v-model="entryForm.owner" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="知识状态">
              <el-select v-model="entryForm.publishStatus" style="width: 100%;">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="已发布" value="PUBLISHED" />
                <el-option label="已废弃" value="DEPRECATED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="知识版本"><el-input-number v-model="entryForm.version" :min="1" style="width: 100%;" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="标签"><el-input v-model="entryForm.tagsText" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="补充说明">
          <el-input v-model="entryForm.content" type="textarea"
                    :autosize="{ minRows: 3, maxRows: 12 }" placeholder="案例、注意事项等补充内容" />
        </el-form-item>
      </el-form>
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

    <el-drawer v-model="entryDetailVisible" title="知识详情" size="540px">
      <template v-if="entryDetail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="标题">{{ entryDetail.title }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ entryTypeLabel(entryDetail.entryType) }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ categoryName(entryDetail.categoryId) }}</el-descriptions-item>
          <el-descriptions-item label="解释">{{ entryDetail.definition || '-' }}</el-descriptions-item>
          <el-descriptions-item label="别名">{{ prettyJson(entryDetail.aliases) }}</el-descriptions-item>
          <el-descriptions-item label="字段映射">{{ prettyJson(entryDetail.fieldMappings) }}</el-descriptions-item>
          <el-descriptions-item label="参考 SQL">
            <pre v-if="entryDetail.sqlExample" class="detail-json">{{ entryDetail.sqlExample }}</pre>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="取数规则">
            <pre v-if="entryDetail.reportSpec" class="detail-json">{{ prettyJson(entryDetail.reportSpec) }}</pre>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="使用说明">{{ entryDetail.usageNote || '-' }}</el-descriptions-item>
          <el-descriptions-item label="相关指标">{{ prettyJson(entryDetail.relatedMetrics) }}</el-descriptions-item>
          <el-descriptions-item label="来源系统">{{ entryDetail.sourceSystem || '-' }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ entryDetail.owner || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发布状态">{{ entryDetail.publishStatus || '-' }}</el-descriptions-item>
          <el-descriptions-item label="DAM 内容">
            <div class="detail-pre">{{ entryDetail.content || '-' }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="WeKnora 摘要">{{ entryDetail.externalSummary || '-' }}</el-descriptions-item>
          <el-descriptions-item label="WeKnora 标签">
            <pre class="detail-json">{{ prettyJson(entryDetail.externalTags) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item label="WeKnora 元数据">
            <pre class="detail-json">{{ prettyJson(entryDetail.externalMetadata) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item label="解析状态">
            <el-tag size="small" :type="syncTag(entryDetail.syncStatus)">{{ syncLabel(entryDetail.syncStatus) }}</el-tag>
            <span v-if="entryDetail.externalParseStatus" class="detail-muted">（{{ entryDetail.externalParseStatus }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="最近错误">{{ entryDetail.lastError || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-actions">
          <el-button size="small" @click="refreshEntryStatus(entryDetail)">刷新 Agent 状态</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { knowledgeApi, agentApi, metricApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'

const categories = ref<any[]>([])
const metricOptions = ref<any[]>([])
const entries = ref<any[]>([])
const stats = ref<any>({})
const external = ref<any>({ configured: false })
const agentSyncing = ref(false)
const aiClassifying = ref(false)
const entryDetailVisible = ref(false)
const entryDetail = ref<any>(null)
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
let statusTimer: ReturnType<typeof setTimeout> | null = null

const currentCategory = computed(() => categories.value.find(c => c.id === selectedCategoryId.value))
const categoryCount = (id: number) => Number(stats.value.byCategory?.[String(id)] || 0)
const parentOptions = computed(() => categories.value.filter(c =>
  !categoryForm.value.id || (c.id !== categoryForm.value.id && !String(c.categoryPath || '').startsWith(String(categoryForm.value.categoryPath || '') + '/'))
))
function handleAddCommand(command: string) {
  if (command === 'manual') openEntry()
  else if (command === 'source') openSync()
}
const sourceLabel = (v: string) => ({ MANUAL: '手工', METADATA: '元数据', METRIC: '指标', STANDARD: '标准' } as any)[v] || v || '手工'
const sourceTag = (v: string) => (v === 'METRIC' ? 'success' : v === 'METADATA' ? 'primary' : v === 'STANDARD' ? 'warning' : 'info')
const vectorLabel = (v: string) => ({ PENDING: '待索引', SYNCED: '已索引', FAILED: '失败' } as any)[v] || v || '待索引'
const vectorTag = (v: string) => (v === 'SYNCED' ? 'success' : v === 'FAILED' ? 'danger' : 'info')
const syncLabel = (v: string) => ({ LOCAL_ONLY: '未同步', PENDING: '解析中', SYNCED: '已同步', FAILED: '失败' } as any)[v] || '未同步'
const syncTag = (v: string) => (v === 'SYNCED' ? 'success' : v === 'FAILED' ? 'danger' : 'info')
const entryTypeLabel = (v: string) => ({ TERM: '业务术语', METRIC: '指标解释', STANDARD: '标准与编码', CASE: '案例与变更' } as any)[v] || '业务术语'
const metricLabel = (m: any) => m ? `${m.metricName || m.metricCode}${m.metricCode ? '（' + m.metricCode + '）' : ''}` : ''
const selectedMetric = computed(() => metricOptions.value.find(m => m.id === entryForm.value.metricId))
const formatTime = (v: string) => v ? String(v).replace('T', ' ').slice(0, 16) : '-'

async function loadMetrics() {
  try {
    const res: any = await metricApi.list({ page: 1, pageSize: 500 })
    metricOptions.value = res.data?.records || res.data || []
  } catch { metricOptions.value = [] }
}

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
    scheduleStatusPoll()
  } catch (e: any) { ElMessage.error(e.message || '加载知识条目失败'); entries.value = [] }
  finally { loading.value = false }
}

/** 有解析中的条目时自动刷新列表，避免页面一直停在“解析中” */
function scheduleStatusPoll() {
  if (statusTimer) clearTimeout(statusTimer)
  statusTimer = null
  if (!entries.value.some(e => e.syncStatus === 'PENDING')) return
  statusTimer = setTimeout(async () => {
    await Promise.all([loadEntries(), loadStats()])
  }, 15000)
}

function selectCategory(id: number) { selectedCategoryId.value = id; loadEntries() }

function openCategory(c?: any, parentId?: number) {
  categoryForm.value = c
    ? { ...c }
    : { status: 1, sortOrder: 0, parentId: parentId ?? null }
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
  entryForm.value = row
    ? {
        ...row,
        tagsText: parseTags(row.tags),
        aliasesText: parseTags(row.aliases),
        fieldMappingsText: parseTags(row.fieldMappings),
        relatedMetricsText: parseTags(row.relatedMetrics),
        reportSpec: parseObject(row.reportSpec),
        metricId: row.metricId || parseMetricId(row.sourceKey)
      }
    : {
        categoryId: selectedCategoryId.value, entryType: 'TERM', publishStatus: 'PUBLISHED', version: 1,
        reportSpec: { ruleStatus: 'DRAFT' }
      }
  showEntry.value = true
}
function parseMetricId(sourceKey: any): number | undefined {
  const m = String(sourceKey || '').match(/^metric:(\d+)$/)
  return m ? Number(m[1]) : undefined
}

function parseObject(v: any): any {
  if (!v) return { ruleStatus: 'DRAFT' }
  if (typeof v === 'object') return v
  try { return JSON.parse(v) } catch { return { ruleStatus: 'DRAFT' } }
}

function parseTags(v: any): string {
  if (!v) return ''
  try { const a = JSON.parse(v); return Array.isArray(a) ? a.join(', ') : String(v) } catch { return String(v) }
}
async function aiDraftReportSpec() {
  if (!entryForm.value.title?.trim()) { ElMessage.warning('请先填写标题和解释'); return }
  aiClassifying.value = true
  try {
    const prompt = `你是医院数据治理取数规则助手。请根据知识条目生成可执行的报表取数规则草案，不要编造知识中没有的表和字段。\n\n标题：${entryForm.value.title}\n解释：${entryForm.value.definition || ''}\n字段映射：${entryForm.value.fieldMappingsText || ''}\n参考 SQL：${entryForm.value.sqlExample || ''}\n\n只输出 JSON：\n{\n "statisticalGrain": "统计粒度，如 就诊号+日期+科室",\n "dedupKeys": "去重键",\n "requiredTables": "必用表，逗号分隔",\n "conditions": "业务条件",\n "mainSql": "主报表 SQL 模板，不确定留空",\n "drillSql": "下钻 SQL 模板，不确定留空",\n "explanation": "用业务语言解释口径"\n}`
    const res: any = await agentApi.complete([{ role: 'user', content: prompt }], { agentType: 'code' })
    const raw = String(res?.data?.content || '').replace(/```json/gi, '').replace(/```/g, '').trim()
    const json = JSON.parse(raw)
    entryForm.value.reportSpec = {
      ...entryForm.value.reportSpec,
      ...json,
      ruleStatus: entryForm.value.reportSpec?.ruleStatus || 'DRAFT'
    }
    ElMessage.success('已生成取数规则草稿，请核对后保存')
  } catch (e: any) { ElMessage.error(e.message || 'AI 起草规则失败') }
  finally { aiClassifying.value = false }
}

async function aiClassifyEntry() {
  if (!entryForm.value.title?.trim()) { ElMessage.warning('请先填写标题'); return }
  if (!categories.value.length) { ElMessage.warning('请先维护知识分类'); return }
  aiClassifying.value = true
  try {
    const categoryLines = categories.value.map(c =>
      `- id=${c.id}；路径=${c.categoryPath || c.categoryName}；说明=${c.description || '无'}`
    ).join('\n')
    const prompt = `你是医院数据治理知识库分类专家。请根据知识条目内容，从下面已有分类中选择最合适的一个，并提取结构化字段。

【已有分类】
${categoryLines}

【知识条目】
标题：${entryForm.value.title}
当前内容：${entryForm.value.definition || entryForm.value.content || ''}
字段映射：${entryForm.value.fieldMappingsText || ''}

请只输出 JSON，不要解释，不要 Markdown 代码块：
{
  "categoryId": 数字，必须是上面已有分类的 id，不确定填 null,
  "entryType": "TERM|METRIC|STANDARD|CASE",
  "aliases": ["别名1"],
  "fieldMappings": ["PA_PatMas.PAPMI_No → 病人号"],
  "relatedMetrics": ["门诊量"],
  "usageNote": "使用说明",
  "reason": "推荐理由"
}
业务术语选 TERM，指标解释选 METRIC，标准/码表选 STANDARD，案例/变更选 CASE。`
    const res: any = await agentApi.complete([{ role: 'user', content: prompt }], { agentType: 'code' })
    const raw = String(res?.data?.content || '').replace(/```json/gi, '').replace(/```/g, '').trim()
    const json = JSON.parse(raw)
    const categoryId = Number(json.categoryId)
    const matched = categories.value.find(c => c.id === categoryId)
    if (!matched) throw new Error('AI 没有返回有效分类，请重试或手工选择')
    entryForm.value.categoryId = matched.id
    entryForm.value.entryType = ['TERM', 'METRIC', 'STANDARD', 'CASE'].includes(json.entryType) ? json.entryType : entryForm.value.entryType
    if (Array.isArray(json.aliases)) entryForm.value.aliasesText = json.aliases.join('、')
    if (Array.isArray(json.fieldMappings)) entryForm.value.fieldMappingsText = json.fieldMappings.join('\n')
    if (Array.isArray(json.relatedMetrics)) entryForm.value.relatedMetricsText = json.relatedMetrics.join('、')
    if (json.usageNote && !entryForm.value.usageNote) entryForm.value.usageNote = json.usageNote
    ElMessage.success(`AI 推荐：${matched.categoryPath || matched.categoryName}${json.reason ? '（' + json.reason + '）' : ''}`)
  } catch (e: any) {
    ElMessage.error(e.message || 'AI 归类失败')
  } finally { aiClassifying.value = false }
}

async function saveEntry() {
  if (!entryForm.value.title?.trim()) { ElMessage.warning('标题不能为空'); return }
  if (entryForm.value.entryType === 'METRIC' && entryForm.value.publishStatus === 'PUBLISHED' && !entryForm.value.metricId) {
    ElMessage.warning('指标解释发布前必须关联指标库中的指标'); return
  }
  saving.value = true
  try {
    const payload = {
      ...entryForm.value,
      tags: entryForm.value.tagsText ? entryForm.value.tagsText.split(/[,，]/).map((s: string) => s.trim()).filter(Boolean) : [],
      aliases: entryForm.value.aliasesText ? entryForm.value.aliasesText.split(/[,，]/).map((s: string) => s.trim()).filter(Boolean) : [],
      relatedMetrics: entryForm.value.relatedMetricsText ? entryForm.value.relatedMetricsText.split(/[,，]/).map((s: string) => s.trim()).filter(Boolean) : [],
      fieldMappings: entryForm.value.fieldMappingsText ? entryForm.value.fieldMappingsText.split(/\n/).map((s: string) => s.trim()).filter(Boolean) : []
    }
    delete payload.tagsText
    delete payload.aliasesText
    delete payload.relatedMetricsText
    delete payload.fieldMappingsText
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

async function agentSync() {
  if (!external.value.configured) { ElMessage.warning('请先配置 WeKnora API Key'); return }
  try {
    await ElMessageBox.confirm('将同步全部分类和知识条目到 Agent 知识库，并刷新解析状态；是否继续？', 'Agent 同步', { type: 'warning' })
  } catch { return }
  agentSyncing.value = true
  try {
    const res: any = await knowledgeApi.migrateExternal()
    const d = res.data || {}
    ElMessage.success(`Agent 同步完成：已同步 ${d.synced || 0} 条，解析中 ${d.pending || 0} 条，失败 ${d.failed || 0} 条`)
    await Promise.all([loadStats(), loadEntries(), loadExternal()])
  } catch (e: any) { ElMessage.error(e.message || 'Agent 同步失败') }
  finally { agentSyncing.value = false }
}

function openEntryDetail(row: any) {
  entryDetail.value = { ...row }
  entryDetailVisible.value = true
}

function categoryName(id: number) {
  return categories.value.find(c => c.id === id)?.categoryName || '-'
}

function prettyJson(value: any) {
  if (!value) return '-'
  if (typeof value === 'object') return JSON.stringify(value, null, 2)
  try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return String(value) }
}

async function refreshEntryStatus(row: any) {
  try {
    const res: any = await knowledgeApi.externalEntryStatus(row.id)
    const d = res.data || {}
    row.syncStatus = d.syncStatus
    row.embeddingStatus = d.embeddingStatus
    row.lastError = d.lastError
    if (entryDetail.value?.id === row.id) {
      entryDetail.value = { ...row, externalParseStatus: d.externalParseStatus }
    }
    ElMessage.success(`Agent 状态：${syncLabel(d.syncStatus)}`)
  } catch (e: any) { ElMessage.error(e.message || '获取状态失败') }
}

onBeforeUnmount(() => { if (statusTimer) clearTimeout(statusTimer) })

onMounted(() => { loadAll(); loadMetrics() })
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
.detail-pre { white-space: pre-wrap; line-height: 1.7; font-size: 12px; }
.detail-json { margin: 0; white-space: pre-wrap; word-break: break-all; font-size: 11px; color: var(--med-text-secondary); max-height: 180px; overflow: auto; }
.detail-muted { font-size: 11px; color: var(--med-text-light); margin-left: 4px; }
.detail-actions { display: flex; justify-content: flex-end; margin-top: 14px; }
.form-tip { margin: -6px 0 12px 90px; font-size: 11px; line-height: 1.6; color: var(--med-text-light); }
.field-help { margin-top: 5px; font-size: 11px; line-height: 1.6; color: var(--med-text-light); }
.rule-collapse { margin: 8px 0 16px; }
.rule-tip { margin: 0 0 12px; }
.metric-authority { margin: 0 0 14px 90px; }
.metric-boundary-tip { margin: 0 0 14px 90px; }
.metric-detail { margin-top: 4px; font-size: 11px; line-height: 1.6; }
.entry-dialog-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding-right: 18px; }
.entry-dialog-title { font-size: 16px; font-weight: 600; color: var(--med-text-primary); }
.entry-dialog-actions { display: flex; align-items: center; gap: 8px; }
.knowledge-entry-dialog .el-dialog__body { max-height: calc(92vh - 110px); overflow-y: auto; padding-top: 8px; }
.knowledge-entry-dialog .el-dialog__header { margin-right: 0; padding-bottom: 8px; }
.knowledge-entry-dialog .el-collapse-item__content { padding-bottom: 8px; }
</style>
