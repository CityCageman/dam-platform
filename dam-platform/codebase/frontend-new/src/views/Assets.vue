<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">资产目录</h2>
        <p class="page-desc">按业务域浏览已登记的资产，查看字段、标准映射、质量结果与安全策略。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">登记资产</el-button>
    </div>

    <div class="catalog">
      <aside class="tree-panel">
        <div class="tree-head">业务域</div>
        <el-tree :data="domains" node-key="id" :props="{ label: 'name', children: 'children' }"
                 highlight-current default-expand-all :expand-on-click-node="false" @node-click="pickDomain">
          <template #default="{ data }">
            <span class="tree-node">
              <span>{{ data.name }}</span>
              <span class="tree-count">{{ data.assetCount }}</span>
            </span>
          </template>
        </el-tree>
        <div class="tree-all" :class="{ active: !filters.domainId }" @click="clearDomain">全部资产</div>
      </aside>

      <div class="list">
        <div class="toolbar">
          <el-input v-model="filters.keyword" placeholder="搜索名称 / 编码 / 描述 / 标签" clearable
                    :prefix-icon="Search" style="width: 280px" @keyup.enter="load" @clear="load" />
          <el-select v-model="filters.type" placeholder="资产类型" clearable style="width: 130px" @change="load">
            <el-option v-for="(v, k) in TYPE_LABEL" :key="k" :label="v" :value="k" />
          </el-select>
          <el-select v-model="filters.level" placeholder="安全等级" clearable style="width: 130px" @change="load">
            <el-option v-for="(v, k) in LEVEL_META" :key="k" :label="`${k} ${v.name}`" :value="k" />
          </el-select>
          <div class="spacer"></div>
          <span class="muted">共 {{ total }} 个资产</span>
        </div>

        <div class="cards" v-loading="loading">
          <div class="asset-card" v-for="a in items" :key="a.id" @click="openDetail(a.id)">
            <div class="card-top">
              <div class="card-title">
                <span class="level-bar" :style="{ background: LEVEL_META[a.securityLevel]?.color }"></span>
                <span class="name">{{ a.name }}</span>
              </div>
              <el-tag size="small" effect="plain">{{ TYPE_LABEL[a.assetType] || a.assetType }}</el-tag>
            </div>
            <div class="code mono">{{ a.assetCode }}</div>
            <div class="desc">{{ a.description || '暂无描述' }}</div>
            <div class="tags" v-if="a.tags">
              <span class="tag" v-for="t in a.tags.split(/[,，]/)" :key="t">{{ t }}</span>
            </div>
            <div class="card-foot">
              <span>{{ a.domainName || '未分类' }}</span>
              <span>·</span>
              <span>{{ a.owner || '-' }}</span>
              <div class="spacer"></div>
              <span :title="'上游'">↑{{ a.upstreamCount || 0 }}</span>
              <span :title="'下游'">↓{{ a.downstreamCount || 0 }}</span>
              <span :title="'浏览量'">👁 {{ a.viewCount || 0 }}</span>
            </div>
          </div>
          <el-empty v-if="!loading && !items.length" description="没有匹配的资产" />
        </div>

        <el-pagination class="pager" background layout="prev, pager, next, total" :total="total"
                       :page-size="filters.size" :current-page="filters.page" @current-change="turnPage" />
      </div>
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawer" size="62%" :with-header="false">
      <div v-if="detail" class="detail">
        <div class="detail-head">
          <div>
            <div class="detail-title">
              <span>{{ detail.name }}</span>
              <el-tag size="small" :color="LEVEL_META[detail.securityLevel]?.color" effect="dark" style="border:none">
                {{ detail.securityLevel }} {{ LEVEL_META[detail.securityLevel]?.name }}
              </el-tag>
              <el-tag size="small" effect="plain">{{ TYPE_LABEL[detail.assetType] }}</el-tag>
            </div>
            <div class="detail-sub mono">{{ detail.assetCode }} · {{ detail.domainName || '未分类' }}</div>
          </div>
          <div class="detail-actions">
            <el-button :type="detail.favorite ? 'warning' : 'default'" :icon="Star" @click="toggleFavorite">
              {{ detail.favorite ? '已收藏' : '收藏' }}
            </el-button>
            <el-button @click="drawer = false">关闭</el-button>
          </div>
        </div>

        <el-tabs v-model="tab" class="detail-tabs">
          <el-tab-pane label="概览" name="overview">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="负责人">{{ detail.owner || '-' }}</el-descriptions-item>
              <el-descriptions-item label="归属部门">{{ detail.department || '-' }}</el-descriptions-item>
              <el-descriptions-item label="关联表">
                {{ detail.tableName ? `${detail.schemaName}.${detail.tableName}` : '无（指标/报表/接口）' }}
              </el-descriptions-item>
              <el-descriptions-item label="数据源">{{ detail.sourceName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="数据量">{{ fmtNum(detail.rowCount) }} 行</el-descriptions-item>
              <el-descriptions-item label="字段数">{{ detail.columnCount || detail.columns?.length || 0 }}</el-descriptions-item>
              <el-descriptions-item label="质量分">{{ detail.qualityScore ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ fmtTime(detail.updatedAt) }}</el-descriptions-item>
              <el-descriptions-item label="描述" :span="2">{{ detail.description || '-' }}</el-descriptions-item>
            </el-descriptions>
            <div class="sub-title">标签</div>
            <div class="tags">
              <span class="tag" v-for="t in (detail.tags || '').split(/[,，]/).filter(Boolean)" :key="t">{{ t }}</span>
              <span v-if="!detail.tags" class="muted">无标签</span>
            </div>
          </el-tab-pane>

          <el-tab-pane :label="`字段 (${detail.columns?.length || 0})`" name="columns">
            <el-table :data="detail.columns" size="small" max-height="520">
              <el-table-column prop="columnName" label="字段" min-width="150">
                <template #default="{ row }">
                  <span class="mono">{{ row.columnName }}</span>
                  <el-tag v-if="row.primaryKey" size="small" type="warning" effect="plain" style="margin-left:6px">PK</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="dataType" label="类型" width="120" />
              <el-table-column label="可空" width="70">
                <template #default="{ row }">{{ row.nullable ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column prop="columnComment" label="注释" min-width="160" />
              <el-table-column label="标准" min-width="140">
                <template #default="{ row }">
                  <el-tag v-if="row.standardName" size="small" effect="plain"
                          :type="row.conformStatus === 'CONFORM' ? 'success' : row.conformStatus === 'DEVIATE' ? 'danger' : 'info'">
                    {{ row.standardName }}
                  </el-tag>
                  <span v-else class="muted">未映射</span>
                </template>
              </el-table-column>
              <el-table-column label="脱敏" width="100">
                <template #default="{ row }">
                  <span v-if="row.desensitize && row.desensitize !== 'NONE'" class="mask-tag">{{ row.desensitize }}</span>
                  <span v-else class="muted">-</span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`质量 (${detail.qualityRules?.length || 0})`" name="quality">
            <el-table :data="detail.qualityRules" size="small">
              <el-table-column prop="name" label="规则" min-width="200" />
              <el-table-column label="类型" width="110">
                <template #default="{ row }">{{ RULE_LABEL[row.ruleType] || row.ruleType }}</template>
              </el-table-column>
              <el-table-column prop="threshold" label="阈值" width="90" />
              <el-table-column label="最新通过率" width="120">
                <template #default="{ row }">
                  <span v-if="row.lastPassRate != null"
                        :class="row.lastPassRate >= row.threshold ? 'ok' : 'danger'">{{ row.lastPassRate }}%</span>
                  <span v-else class="muted">未执行</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag v-if="row.lastStatus" size="small" :type="row.lastStatus === 'PASS' ? 'success' : 'danger'">
                    {{ row.lastStatus === 'PASS' ? '通过' : '未通过' }}
                  </el-tag>
                  <span v-else class="muted">-</span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`安全策略 (${detail.policies?.length || 0})`" name="security">
            <el-table :data="detail.policies" size="small">
              <el-table-column label="字段" min-width="140">
                <template #default="{ row }">
                  <span class="mono">{{ row.columnName || '整表' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="分级" width="110">
                <template #default="{ row }">
                  <el-tag size="small" effect="dark" :color="LEVEL_META[row.levelCode]?.color" style="border:none">
                    {{ row.levelCode }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="desensitize" label="脱敏方式" width="120" />
              <el-table-column prop="authorizedRoles" label="授权角色" min-width="200" />
              <el-table-column label="审计" width="80">
                <template #default="{ row }">{{ row.auditEnabled ? '开' : '关' }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="血缘" name="lineage">
            <div class="lineage-actions">
              <el-button size="small" @click="goLineage(detail.id)">在血缘图中查看</el-button>
            </div>
            <div class="lineage-block">
              <div class="sub-title">上游（{{ detail.lineage?.upstream?.length || 0 }}）</div>
              <div class="lineage-item" v-for="e in detail.lineage?.upstream || []" :key="'u' + e.id">
                <el-icon><Back /></el-icon>
                <span>{{ e.sourceName }}</span>
                <span class="mono muted">{{ e.jobName }}</span>
              </div>
              <div v-if="!detail.lineage?.upstream?.length" class="muted">无上游依赖</div>
            </div>
            <div class="lineage-block">
              <div class="sub-title">下游（{{ detail.lineage?.downstream?.length || 0 }}）</div>
              <div class="lineage-item" v-for="e in detail.lineage?.downstream || []" :key="'d' + e.id">
                <el-icon><Right /></el-icon>
                <span>{{ e.targetName }}</span>
                <span class="mono muted">{{ e.jobName }}</span>
              </div>
              <div v-if="!detail.lineage?.downstream?.length" class="muted">无下游消费</div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <el-dialog v-model="createDialog" title="登记资产" width="520px">
      <el-form :model="form" label-width="88px">
        <el-form-item label="资产名称" required>
          <el-input v-model="form.name" placeholder="如：门诊量日报" />
        </el-form-item>
        <el-form-item label="所属业务域">
          <el-select v-model="form.domainId" placeholder="选择业务域" style="width:100%">
            <el-option v-for="d in flatDomains" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="资产类型">
          <el-select v-model="form.assetType" style="width:100%">
            <el-option v-for="(v, k) in TYPE_LABEL" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="安全等级">
          <el-select v-model="form.securityLevel" style="width:100%">
            <el-option v-for="(v, k) in LEVEL_META" :key="k" :label="`${k} ${v.name}`" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.owner" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="逗号分隔，如：门诊,挂号" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Search, Star, Back, Right } from '@element-plus/icons-vue'
import { api, LEVEL_META, TYPE_LABEL, RULE_LABEL, fmtTime, fmtNum } from '../api'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const domains = ref([])
const items = ref([])
const total = ref(0)
const drawer = ref(false)
const detail = ref(null)
const tab = ref('overview')
const createDialog = ref(false)

const filters = reactive({
  domainId: null,
  keyword: route.query.q || '',
  type: '',
  level: '',
  page: 1,
  size: 12
})

const form = reactive({
  name: '',
  domainId: null,
  assetType: 'TABLE',
  securityLevel: 'L2',
  owner: '',
  tags: '',
  description: ''
})

const flatDomains = computed(() => {
  const out = []
  const walk = (list, prefix = '') => {
    list.forEach((d) => {
      out.push({ id: d.id, name: prefix + d.name })
      if (d.children?.length) walk(d.children, prefix + d.name + ' / ')
    })
  }
  walk(domains.value)
  return out
})

async function load() {
  loading.value = true
  try {
    const res = await api.assets({
      domainId: filters.domainId || undefined,
      keyword: filters.keyword || undefined,
      type: filters.type || undefined,
      level: filters.level || undefined,
      page: filters.page,
      size: filters.size
    })
    items.value = res.items
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function pickDomain(node) {
  filters.domainId = node.id
  filters.page = 1
  load()
}

function clearDomain() {
  filters.domainId = null
  filters.page = 1
  load()
}

function turnPage(p) {
  filters.page = p
  load()
}

async function openDetail(id) {
  drawer.value = true
  tab.value = 'overview'
  const d = await api.asset(id)
  detail.value = d
  api.viewAsset(id)
}

async function toggleFavorite() {
  const res = await api.favorite(detail.value.id)
  detail.value.favorite = res.favorite
}

function goLineage(id) {
  drawer.value = false
  router.push({ path: '/lineage', query: { assetId: id } })
}

function openCreate() {
  Object.assign(form, { name: '', domainId: null, assetType: 'TABLE', securityLevel: 'L2', owner: '', tags: '', description: '' })
  createDialog.value = true
}

async function submitCreate() {
  if (!form.name.trim()) return ElMessage.warning('请填写资产名称')
  await api.createAsset({ ...form })
  ElMessage.success('资产已登记')
  createDialog.value = false
  load()
}

onMounted(async () => {
  domains.value = await api.domains()
  await load()
  if (route.query.id) openDetail(Number(route.query.id))
})
</script>

<style scoped>
.catalog {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.tree-panel {
  width: 214px;
  flex: 0 0 214px;
  background: #fff;
  border: 1px solid #e9edf5;
  border-radius: 12px;
  padding: 14px 10px;
  position: sticky;
  top: 80px;
}

.tree-head {
  font-size: 12px;
  color: #8a94a6;
  letter-spacing: .5px;
  padding: 0 8px 10px;
  border-bottom: 1px solid #f0f2f7;
  margin-bottom: 8px;
}

.tree-node {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  font-size: 12.5px;
}

.tree-count {
  color: #98a2b3;
  font-size: 11px;
  background: #f2f5fa;
  border-radius: 8px;
  padding: 0 6px;
}

.tree-all {
  margin-top: 8px;
  padding: 7px 10px;
  border-radius: 6px;
  font-size: 12.5px;
  color: #5b6b85;
  cursor: pointer;
}

.tree-all.active,
.tree-all:hover {
  background: #eef4ff;
  color: #2f7bff;
}

.list {
  flex: 1;
  min-width: 0;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.spacer {
  flex: 1;
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(292px, 1fr));
  gap: 12px;
  min-height: 200px;
}

.asset-card {
  background: #fff;
  border: 1px solid #e9edf5;
  border-radius: 12px;
  padding: 14px 16px;
  cursor: pointer;
  transition: all .18s;
  display: flex;
  flex-direction: column;
}

.asset-card:hover {
  border-color: #bcd3ff;
  box-shadow: 0 8px 22px rgba(47, 123, 255, .1);
  transform: translateY(-2px);
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.level-bar {
  width: 3px;
  height: 14px;
  border-radius: 2px;
}

.name {
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.code {
  color: #98a2b3;
  font-size: 11px;
  margin: 6px 0 8px;
}

.desc {
  color: #667085;
  font-size: 12px;
  line-height: 1.6;
  height: 38px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.tag {
  background: #f2f5fa;
  color: #5b6b85;
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 4px;
}

.card-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #98a2b3;
  font-size: 11.5px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #eef1f7;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

/* 抽屉 */
.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding-bottom: 14px;
  border-bottom: 1px solid #eef1f7;
}

.detail-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 700;
}

.detail-sub {
  color: #8a94a6;
  font-size: 12px;
  margin-top: 6px;
}

.detail-actions {
  display: flex;
  gap: 8px;
}

.detail-tabs {
  margin-top: 6px;
}

.sub-title {
  font-size: 13px;
  font-weight: 600;
  margin: 18px 0 10px;
  color: #34405a;
}

.mask-tag {
  background: #fff1f0;
  color: #f5222d;
  border: 1px solid #ffccc7;
  border-radius: 4px;
  font-size: 11px;
  padding: 1px 6px;
}

.ok {
  color: #12b76a;
  font-weight: 600;
}

.danger {
  color: #f5222d;
  font-weight: 600;
}

.lineage-actions {
  margin-bottom: 10px;
}

.lineage-block {
  margin-bottom: 16px;
}

.lineage-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: #f7f9fc;
  border-radius: 8px;
  margin-bottom: 6px;
  font-size: 12.5px;
}
</style>
