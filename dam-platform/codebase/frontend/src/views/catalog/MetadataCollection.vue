<template>
  <div class="page-container">
    <!-- 页面头部（frontend-new 风格） -->
    <div class="page-head">
      <div>
        <h2 class="page-title">元数据管理</h2>
        <p class="page-desc">采集数据源表结构与字段，按数仓层级分类查看，并支持 AI 智能标注。</p>
      </div>
      <div class="head-actions">
        <el-button v-if="isMetaType('technical') && isTableType && !selectedDsId" type="primary" @click="addDataSource">
          <el-icon><Plus /></el-icon> 新增数据源
        </el-button>
      </div>
    </div>
    <el-row :gutter="16">
      <!-- 右侧：主内容（全宽） -->
      <el-col :span="24">
        <template v-if="loading">
          <el-card style="height: calc(100vh - 160px); display: flex; align-items: center; justify-content: center;">
            <el-icon class="is-loading" :size="24"><Loading /></el-icon>
            <span style="margin-left: 8px; color: #909399;">加载中...</span>
          </el-card>
        </template>
        <template v-if="isMetaType('technical')">
          <el-tabs v-model="selectedTechType" @tab-change="selectTechTypeByCode" style="margin-bottom: 8px;">
            <el-tab-pane v-for="t in techTypes" :key="t.configCode" :label="t.configName" :name="t.configCode" />
          </el-tabs>
          <!-- 数据表（技术元数据 TABLE）：数据源列表 / 数据源下表列表 -->
          <template v-if="isTableType">
          <template v-if="!selectedDsId">
            <!-- 层级概况：源库 / ODS / DWD / DWS / ADS / DIM -->
            <div class="stat-grid" style="margin-bottom: 14px;">
              <div class="stat-card layer-stat" :class="{ active: activeLayer === 'ALL' }" @click="activeLayer = 'ALL'">
                <div class="stat-label">全部</div>
                <div class="stat-value">{{ dataSources.length }}</div>
                <div class="stat-foot">{{ totalTableCount }} 张表</div>
              </div>
              <div class="stat-card layer-stat" v-for="l in layerOverview" :key="l.layer"
                   :class="{ active: activeLayer === l.layer }" @click="activeLayer = l.layer">
                <div class="stat-label">{{ layerName(l.layer) }}</div>
                <div class="stat-value">{{ l.sourceCount }}</div>
                <div class="stat-foot">{{ l.tableCount }} 张表 · {{ l.columnCount }} 字段</div>
              </div>
            </div>
            <div class="panel">
              <div class="panel-toolbar">
                <h4 class="panel-title" style="margin:0;">数据源列表</h4>
                <div class="toolbar-spacer"></div>
                <el-button :icon="Refresh" @click="loadDataSources">刷新</el-button>
              </div>
              <el-table :data="filteredDataSources" stripe @row-click="onDsRowClick" v-loading="dsLoading">
                <el-table-column type="expand" width="40">
                  <template #default="{ row }">
                    <div class="schema-panel">
                      <template v-if="schemaStats[row.id]?.schemas?.length">
                        <div class="schema-item" v-for="s in schemaStats[row.id].schemas" :key="s.schemaId">
                          <span class="schema-name">{{ s.schemaName || 'public' }}</span>
                          <span class="schema-count">{{ s.tableCount }} 张表</span>
                        </div>
                      </template>
                      <span v-else class="schema-empty">暂无 Schema，请先扫描并同步</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="dsName" label="数据源名称" min-width="160" show-overflow-tooltip />
                <el-table-column prop="dsType" label="数据库类型" width="120" />
                <el-table-column label="所属层级" width="110">
                  <template #default="{ row }">
                    <el-tag size="small" :type="dataLayerTagType(row.dataLayer)">{{ dataLayerLabel(row.dataLayer) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="Schema 数" width="90">
                  <template #default="{ row }">{{ schemaStats[row.id]?.schemaCount ?? '-' }}</template>
                </el-table-column>
                <el-table-column label="表数量" width="90">
                  <template #default="{ row }">{{ schemaStats[row.id]?.tableCount ?? row._tableCount ?? '-' }}</template>
                </el-table-column>
                <el-table-column label="数据库大小" width="110">
                  <template #default="{ row }">{{ row._storageSize ? formatStorageSize(row._storageSize) : '-' }}</template>
                </el-table-column>
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
                      @click.stop
                      @change="(val) => toggleDsStatus(row, val)"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="70" fixed="right">
                  <template #default="{ row }">
                    <el-button text @click.stop="editDataSource(row)">
                      <el-icon><Edit /></el-icon>
                    </el-button>
                  </template>
                </el-table-column>
                <el-table-column label="扫描" width="70" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      v-if="row.status === 1"
                      text
                      type="primary"
                      @click.stop="openScanDialog(row)"
                    >
                      <el-icon><Search /></el-icon>
                    </el-button>
                  </template>
                </el-table-column>
                <el-table-column label="AI 标注" width="150" fixed="right">
                  <template #default="{ row }">
                    <template v-if="row.status === 1">
                      <el-button text type="primary" :loading="row._aiLoading" @click.stop="aiAnnotate(row, true)">
                        补标注
                      </el-button>
                      <el-button text type="primary" :disabled="row._aiLoading" @click.stop="aiAnnotate(row, false)">
                        全量
                      </el-button>
                    </template>
                  </template>
                </el-table-column>
                <el-table-column label="AI 分类" width="95" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="row.status === 1" text type="primary" :loading="row._aiClassifyLoading" @click.stop="aiClassifyDs(row, true)">
                      AI 分类
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!filteredDataSources.length" description="暂无数据源" :image-size="60" />
            </div>
          </template>
          <template v-else>
            <!-- 该数据源下的表列表（frontend-new 风格） -->
            <div class="panel" style="margin-bottom: 14px;">
              <div class="panel-toolbar">
                <el-button text class="back-btn" @click="backToDataSourceList">
                  <el-icon><ArrowLeft /></el-icon> 返回
                </el-button>
                <h4 class="panel-title" style="margin:0 0 0 4px;">{{ selectedDs?.dsName }}</h4>
                <span class="ds-sub">{{ selectedDs?.databaseName }}</span>
                <div class="toolbar-spacer"></div>
                <el-button :disabled="!!selectedDs?._aiClassifyLoading" :loading="!!selectedDs?._aiClassifyLoading" @click="aiClassifyDs(selectedDs, true)">AI 分类</el-button>
                <el-button :disabled="!!selectedDs?._aiLoading" :loading="!!selectedDs?._aiLoading" @click="aiAnnotate(selectedDs, true)">AI 补标注</el-button>
                <el-button type="primary" plain :disabled="!!selectedDs?._aiLoading" @click="aiAnnotate(selectedDs, false)">AI 全量标注</el-button>
                <el-button type="primary" plain :icon="Search" @click="openScanDialog(selectedDs)">扫描表</el-button>
              </div>
              <div class="filter-row">
                <el-input v-model="dbTableKeyword" placeholder="搜索表名/注释..." clearable style="width: 240px;" @keyup.enter="loadDsTables" />
                <el-select v-model="dbTableSubjectDomain" placeholder="主题域" clearable filterable style="width: 170px;" @change="loadDsTables">
                  <el-option v-for="s in subjectDomainOptions" :key="s.value" :label="`${s.value}-${s.label}`" :value="s.value" />
                  <el-option label="未设置" value="NONE" />
                </el-select>
                <el-button type="primary" @click="loadDsTables">查询</el-button>
                <el-button @click="loadDsTables">刷新</el-button>
                <div class="toolbar-spacer"></div>
                <span class="ds-sub">共 {{ dbTableTotal }} 张表</span>
              </div>
            </div>
            <div class="panel">
              <el-table :data="dbTableAssets" stripe @row-click="openDbTableDetail" style="cursor: pointer;" v-loading="dbTableLoading">
                <el-table-column prop="assetName" label="表名" min-width="160" show-overflow-tooltip />
                <el-table-column label="数据源" width="130" show-overflow-tooltip>
                  <template #default="{ row }">{{ getDataSourceName(row.databaseName) }}</template>
                </el-table-column>
                <el-table-column label="数据库/模式" width="150" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.databaseName || '-' }}<span v-if="row.schemaName">/{{ row.schemaName }}</span></template>
                </el-table-column>
                <el-table-column label="主题域" width="130" show-overflow-tooltip>
                  <template #default="{ row }">{{ subjectDomainLabel(row.subjectDomain) }}</template>
                </el-table-column>
                <el-table-column prop="description" label="表注释" min-width="180" show-overflow-tooltip />
                <el-table-column label="列数" width="65">
                  <template #default="{ row }">{{ row._fieldCount ?? '-' }}</template>
                </el-table-column>
                <el-table-column label="存储大小" width="100">
                  <template #default="{ row }">{{ formatStorageSize(row.storageSize) }}</template>
                </el-table-column>
                <el-table-column prop="sourceSystem" label="所属系统" width="120" />
                <el-table-column label="质量状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="row._qualityStatus === 'failed' ? 'danger' : row._qualityStatus === 'pass' ? 'success' : 'info'" size="small">{{ row._qualityLabel || '-' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="75" fixed="right">
                  <template #default="{ row }">
                    <el-button text @click.stop="openEditDbTable(row)">
                      <el-icon><Edit /></el-icon>
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div class="pagination-bar">
                <el-pagination v-model:current-page="dbTablePage" v-model:page-size="dbTablePageSize" :total="dbTableTotal" layout="total, prev, pager, next" @change="loadDsTables" />
              </div>
              <el-empty v-if="!dbTableTotal" description="暂无数据库表资产" :image-size="60" />
            </div>
          </template>
          </template>
          <!-- 其他技术类型：资产列表 -->
          <template v-if="!isTableType">
            <div class="panel">
              <el-table :data="techAssets" stripe @row-click="goToAssetDetail">
                <el-table-column prop="assetName" label="资产名称" min-width="200" />
                <el-table-column prop="sourceMetadataType" label="类型" width="100" />
                <el-table-column prop="sourceSystem" label="来源系统" width="120" />
                <el-table-column prop="databaseName" label="数据库" width="150" />
                <el-table-column prop="tableName" label="表名" min-width="150" />
                <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
              </el-table>
              <el-empty v-if="!techAssets.length" description="暂无该类资产" :image-size="60" />
            </div>
          </template>
        </template>
        <!-- 业务元数据：业务定义、归属、分级、标签、评分 -->
        <template v-if="isMetaType('business')">
          <el-card style="margin-bottom: 12px;">
            <div class="search-bar">
              <el-select v-model="bizSourceSystem" placeholder="来源系统" clearable style="width: 150px;" @change="searchBusinessAssets">
                <el-option v-for="s in sourceSystemList" :key="s.systemCode" :label="s.systemName" :value="s.systemCode" />
              </el-select>
              <el-select v-model="bizTagFilter" placeholder="按标签筛选" clearable style="width: 150px;" @change="searchBusinessAssets">
                <el-option v-for="t in bizTags" :key="t.id" :label="t.tagName" :value="t.id" />
              </el-select>
              <el-input v-model="bizKeyword" placeholder="搜索资产名称/描述" clearable style="width: 260px;" @keyup.enter="searchBusinessAssets" />
              <el-button type="primary" @click="searchBusinessAssets">查询</el-button>
            </div>
          </el-card>
          <el-card>
            <el-table :data="businessAssets" stripe @row-click="showBusinessDetail">
              <el-table-column label="资产分类" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">{{ getCategoryName(row.categoryId) || '-' }}</template>
              </el-table-column>
              <el-table-column prop="assetName" label="资产名称" min-width="180" />
              <el-table-column prop="sourceMetadataType" label="类型" width="70" />
              <el-table-column prop="sourceSystem" label="来源系统" width="120" />
              <el-table-column label="数据分级" width="130">
                <template #default="{ row }">
                  <el-tag :type="row.securityLevel === 'public' ? 'info' : row.securityLevel === 'internal' ? 'info' : row.securityLevel === 'confidential' ? 'warning' : 'danger'" size="small">{{ getSecurityLevelName(row.securityLevel) || row.securityLevel || '-' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="description" label="业务描述" min-width="200" show-overflow-tooltip />
            </el-table>
            <div style="margin-top: 12px; display: flex; justify-content: flex-end;">
              <el-pagination v-model:current-page="bizPage" v-model:page-size="bizPageSize" :total="bizTotal" layout="total, prev, pager, next" @change="searchBusinessAssets" />
            </div>
          </el-card>
          <!-- 业务元数据详情对话框 -->
          <el-dialog v-model="showBizDetail" title="业务元数据详情" width="700px" top="5vh">
            <template v-if="selectedBizAsset">
              <el-descriptions :column="2" border size="small" style="margin-bottom: 12px;">
                <el-descriptions-item label="资产名称" :span="2">{{ selectedBizAsset.assetName }}</el-descriptions-item>
                <el-descriptions-item label="资产分类">{{ getCategoryName(selectedBizAsset.categoryId) || '-' }}</el-descriptions-item>
                <el-descriptions-item label="资产类型">{{ selectedBizAsset.sourceMetadataType }}</el-descriptions-item>
                <el-descriptions-item label="来源系统">{{ selectedBizAsset.sourceSystem || '-' }}</el-descriptions-item>
                <el-descriptions-item label="数据库" :span="2">{{ selectedBizAsset.databaseName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="表名" :span="2">{{ selectedBizAsset.tableName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="数据分级" :span="2">{{ getSecurityLevelName(selectedBizAsset.securityLevel) || selectedBizAsset.securityLevel || '-' }}</el-descriptions-item>
                <el-descriptions-item label="业务描述" :span="2">{{ selectedBizAsset.description || '-' }}</el-descriptions-item>
              </el-descriptions>
              <template v-if="bizMetaData">
                <h4 style="margin-bottom: 8px;">业务指标</h4>
                <el-descriptions :column="2" border size="small" style="margin-bottom: 12px;">
                  <el-descriptions-item label="上游血缘数">{{ bizMetaData.upstreamCount || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="下游血缘数">{{ bizMetaData.downstreamCount || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="质量规则数">{{ bizMetaData.qualityRuleCount || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="平均评分">{{ bizMetaData.avgRating || '-' }} / 5</el-descriptions-item>
                  <el-descriptions-item label="收藏次数">{{ bizMetaData.favoriteCount || 0 }}</el-descriptions-item>
                </el-descriptions>
                <div v-if="bizMetaData.tags?.length" style="margin-bottom: 12px;">
                  <span style="color: #909399; font-size: 13px; margin-right: 8px;">标签：</span>
                  <el-tag v-for="t in bizMetaData.tags" :key="t.id" size="small" style="margin-right: 4px;">{{ t.tagName }}</el-tag>
                </div>
                <h4 style="margin-bottom: 8px;">业务影响分析</h4>
                <div v-if="bizImpactData">
                  <el-descriptions :column="2" border size="small" style="margin-bottom: 12px;">
                    <el-descriptions-item label="上游影响资产数">{{ bizImpactData.upstreamCount || 0 }}</el-descriptions-item>
                    <el-descriptions-item label="下游影响资产数">{{ bizImpactData.downstreamCount || 0 }}</el-descriptions-item>
                  </el-descriptions>
                  <template v-if="bizImpactData.upstream?.length">
                    <span style="color: #909399; font-size: 13px;">上游链路：</span>
                    <div v-for="(item, i) in bizImpactData.upstream" :key="i" style="padding: 4px 0; font-size: 13px;">
                      <el-tag size="small" type="info">{{ item.assetName || '资产#' + item.assetId }}</el-tag>
                      <span v-if="item.transformRule" style="margin-left: 8px; color: #909399;">{{ item.transformRule }}</span>
                    </div>
                  </template>
                </div>
                <el-empty v-else description="暂无影响分析数据" :image-size="40" />
              </template>
              <el-empty v-else description="暂无业务指标数据" :image-size="60" />
            </template>
            <template #footer>
              <el-button @click="showBizDetail = false">关闭</el-button>
            </template>
          </el-dialog>
        </template>

    <!-- 数据库表详情抽屉 -->
    <el-drawer v-model="showDbDetailDrawer" :title="selectedTable?.tableName || '表详情'" size="70%" @close="handleDrawerClose">
      <template v-if="selectedTable">
        <el-tabs v-model="techTab">
          <el-tab-pane label="基础结构与定义" name="structure">
            <el-descriptions :column="3" border size="small" style="margin-bottom: 12px;">
              <el-descriptions-item label="表名">{{ selectedTable?.tableName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="数据库/模式">{{ selectedTable?.databaseName || '-' }}<span v-if="selectedTable?.schemaName">/{{ selectedTable?.schemaName }}</span></el-descriptions-item>
              <el-descriptions-item label="主题域">
                <el-select v-model="selectedTable.subjectDomain" size="small" style="width: 180px;" filterable :loading="subjectDomainSaving" placeholder="选择主题域" @change="saveTableSubjectDomain">
                  <el-option v-for="s in subjectDomainOptions" :key="s.value" :label="`${s.value}-${s.label}`" :value="s.value" />
                  <el-option label="未设置" value="" />
                </el-select>
              </el-descriptions-item>
            </el-descriptions>
            <div style="margin-bottom: 8px; display: flex; justify-content: space-between; align-items: center;">
              <span style="color: #909399; font-size: 13px;">共 {{ technicalData?.fields?.length || 0 }} 个字段</span>
              <div style="display: flex; gap: 8px;">
                <el-button size="small" type="primary" plain :loading="generatingComments" @click="generateFieldComments">
                  <el-icon><MagicStick /></el-icon> AI 生成注释
                </el-button>
                <el-button size="small" type="primary" plain :loading="generatingClassify" @click="generateFieldClassify">
                  <el-icon><MagicStick /></el-icon> AI 生成分级
                </el-button>
                <el-button size="small" type="primary" :loading="syncingFields" @click="syncFields">
                  <el-icon><Refresh /></el-icon> 同步字段
                </el-button>
              </div>
            </div>
            <el-table :data="technicalData?.fields || []" stripe max-height="calc(100vh - 240px)">
              <el-table-column prop="fieldName" label="字段名" min-width="160" />
              <el-table-column prop="dataType" label="类型" width="100" />
              <el-table-column prop="dataLength" label="长度" width="65" />
              <el-table-column label="主键" width="55"><template #default="{ row }">{{ row.isPrimaryKey ? '是' : '' }}</template></el-table-column>
              <el-table-column label="可空" width="55"><template #default="{ row }">{{ row.isNullable ? '是' : '' }}</template></el-table-column>
              <el-table-column prop="defaultValue" label="默认值" width="100" />
              <el-table-column prop="comment" label="注释" min-width="180" show-overflow-tooltip />
              <el-table-column label="安全等级" width="120">
                <template #default="{ row }">
                  <el-tag v-if="row.securityLevel" :type="securityLevelTagType(row.securityLevel)" size="small">{{ securityLevelLabel(row.securityLevel) }}</el-tag>
                  <span v-else style="color: #c0c4cc;">-</span>
                </template>
              </el-table-column>
              <el-table-column label="敏感类型" width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ sensitiveTypeLabel(row.sensitiveType) || '-' }}</template>
              </el-table-column>
              <el-table-column label="关联表" width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-tag v-if="row.referenceTableName" size="small" type="info">{{ row.referenceTableName }}</el-tag>
                  <span v-else style="color: #c0c4cc;">-</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="primary" text @click="openFieldEdit(row)">编辑</el-button>
                </template>
              </el-table-column>
            </el-table>

            <!-- AI 生成注释预览（勾选确认后落库） -->
            <el-dialog v-model="showCommentPreview" title="AI 生成注释预览" width="760px" top="8vh" append-to-body>
              <div style="margin-bottom: 8px; display: flex; align-items: center; gap: 8px;">
                <span style="color: #909399; font-size: 13px;">以下注释由 AI 生成，可对比原注释并直接修改；仅勾选的字段会被保存（无注释字段默认勾选）。</span>
                <div style="flex: 1;" />
                <el-button size="small" text type="primary" @click="toggleSelectAllComments">{{ allCommentsChecked ? '取消全选' : '全选' }}</el-button>
              </div>
              <el-table :data="pendingComments" size="small" border max-height="52vh">
                <el-table-column width="50" align="center">
                  <template #default="{ row }">
                    <el-checkbox v-model="row.checked" />
                  </template>
                </el-table-column>
                <el-table-column prop="fieldName" label="字段名" width="150" />
                <el-table-column prop="dataType" label="类型" width="85" />
                <el-table-column label="原注释" width="150" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.oldComment || '-' }}</template>
                </el-table-column>
                <el-table-column label="AI 生成" min-width="220">
                  <template #default="{ row }">
                    <el-input v-model="row.comment" size="small" placeholder="注释" />
                  </template>
                </el-table-column>
              </el-table>
              <template #footer>
                <el-button @click="showCommentPreview = false">取消</el-button>
                <el-button type="primary" :loading="savingComments" @click="confirmCommentUpdate">更新所选</el-button>
              </template>
            </el-dialog>

            <!-- AI 生成安全分级预览（勾选确认后落库） -->
            <el-dialog v-model="showClassifyPreview" title="AI 生成安全分级预览" width="860px" top="8vh" append-to-body>
              <div style="margin-bottom: 8px; display: flex; align-items: center; gap: 8px;">
                <span style="color: #909399; font-size: 13px;">以下分级由 AI 推断，可修改；仅勾选的字段会被保存。</span>
                <div style="flex: 1;" />
                <el-button size="small" text type="primary" @click="toggleSelectAllClassify">{{ allClassifyChecked ? '取消全选' : '全选' }}</el-button>
              </div>
              <el-table :data="pendingClassify" size="small" border max-height="52vh">
                <el-table-column width="50" align="center">
                  <template #default="{ row }">
                    <el-checkbox v-model="row.checked" />
                  </template>
                </el-table-column>
                <el-table-column prop="fieldName" label="字段名" width="150" />
                <el-table-column label="安全等级" width="180">
                  <template #default="{ row }">
                    <el-select v-model="row.level" size="small" clearable placeholder="等级" style="width: 100%;">
                      <el-option v-for="o in securityLevelOptions" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="敏感类型" width="160">
                  <template #default="{ row }">
                    <el-select v-model="row.type" size="small" clearable placeholder="类型" style="width: 100%;">
                      <el-option v-for="o in sensitiveTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column prop="dataType" label="字段类型" width="90" />
              </el-table>
              <template #footer>
                <el-button @click="showClassifyPreview = false">取消</el-button>
                <el-button type="primary" :loading="savingClassify" @click="confirmClassifyUpdate">更新所选</el-button>
              </template>
            </el-dialog>
          </el-tab-pane>
          <el-tab-pane label="索引" name="indexes">
            <div style="margin-bottom: 8px; display: flex; justify-content: space-between; align-items: center;">
              <span style="color: #909399; font-size: 13px;">共 {{ tableIndexes.length || 0 }} 个索引</span>
              <el-button size="small" type="primary" :loading="syncingFields" @click="syncFields">
                <el-icon><Refresh /></el-icon> 刷新索引
              </el-button>
            </div>
            <el-table :data="tableIndexes" stripe max-height="calc(100vh - 240px)">
              <el-table-column prop="indexName" label="索引名" min-width="200" />
              <el-table-column label="唯一" width="55"><template #default="{ row }">{{ row.unique ? '是' : '' }}</template></el-table-column>
              <el-table-column prop="type" label="类型" width="80" />
              <el-table-column label="包含列" min-width="200">
                <template #default="{ row }">{{ (row.columns || []).join(', ') }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="数据处理与加工" name="etl">
            <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
              <span style="color: #909399;">ETL作业、转换逻辑、调度频率</span>
              <el-button size="small" type="primary" @click="editExtCategory('etl')"><el-icon><Edit /></el-icon> 编辑</el-button>
            </div>
            <template v-if="extData.etl.length">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item v-for="item in extData.etl" :key="item.key" :label="item.key">{{ item.value || '-' }}</el-descriptions-item>
              </el-descriptions>
            </template>
            <el-empty v-else description="暂无数据" />
          </el-tab-pane>
          <el-tab-pane label="数据血缘与依赖" name="lineage">
            <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
              <span style="color: #909399;">真实血缘关系（上下游依赖、字段级血缘）</span>
              <div style="display: flex; gap: 8px;">
                <el-button size="small" type="primary" plain @click="openFkEdit">+ 新增引用血缘</el-button>
                <el-button size="small" type="primary" @click="goLineagePage">前往血缘管理</el-button>
              </div>
            </div>
            <template v-if="lineageNodeInfo">
              <el-descriptions :column="3" border size="small" style="margin-bottom: 12px;">
                <el-descriptions-item label="节点名称">{{ lineageNodeInfo.sourceName }}</el-descriptions-item>
                <el-descriptions-item label="类型">{{ nodeTypeLabel(lineageNodeInfo.sourceTypeId) }}</el-descriptions-item>
                <el-descriptions-item label="所属系统">{{ lineageNodeInfo.sourceSystemName || '-' }}</el-descriptions-item>
              </el-descriptions>
            </template>
            <el-alert v-else-if="selectedTable" type="info" :closable="false" style="margin-bottom: 12px;"
                      title="未匹配到血缘节点"
                      description="前往血缘管理解析报表 SQL 或注册节点后，此处将展示该表的血缘关系。" />
            <el-tabs v-model="lineageSubTab" type="card" style="margin-bottom: 12px;">
              <!-- Tab1 表级血缘 -->
              <el-tab-pane :label="`表级血缘（${lineageTotal}）`" name="edges">
                <template v-if="lineageEdges.length">
                  <el-table :data="lineageEdges" size="small" stripe max-height="calc(100vh - 300px)" @row-click="goLineageEdge" style="cursor: pointer;">
                    <el-table-column label="源节点" min-width="110">
                      <template #default="{ row }">{{ row.sourceNodeName || row.sourceId }}</template>
                    </el-table-column>
                    <el-table-column label="流向" width="50">→</el-table-column>
                    <el-table-column label="目标节点" min-width="110">
                      <template #default="{ row }">{{ row.targetNodeName || row.targetId }}</template>
                    </el-table-column>
                    <el-table-column prop="transformRule" label="转换规则" min-width="280" show-overflow-tooltip>
                      <template #default="{ row }">{{ row._count > 1 ? `${row._count} 条关联条件` : (row.transformRule || '-') }}</template>
                    </el-table-column>
                    <el-table-column label="关系" width="120">
                      <template #default="{ row }">{{ relationTypeLabel(row.relationType) }}</template>
                    </el-table-column>
                    <el-table-column label="操作" width="70">
                      <template #default="{ row }">
                        <el-button size="small" text type="danger" @click.stop="removeAssetLineage(row)">删除</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                  <el-pagination
                    v-if="lineageTotal > lineagePageSize"
                    style="margin-top: 8px; justify-content: flex-end;"
                    background
                    layout="total, prev, pager, next, sizes"
                    :total="lineageTotal"
                    :current-page="lineagePage"
                    :page-size="lineagePageSize"
                    :page-sizes="[10, 20, 50]"
                    @current-change="(p: number) => { lineagePage = p; loadAssetLineage() }"
                    @size-change="(s: number) => { lineagePageSize = s; lineagePage = 1; loadAssetLineage() }"
                  />
                </template>
                <el-empty v-else description="暂无表级血缘" :image-size="50" />
              </el-tab-pane>
              <!-- Tab2 字段级血缘 -->
              <el-tab-pane :label="`字段级血缘（${fieldTotal}）`" name="fields">
                <template v-if="assetNodeFields.length">
                  <el-table :data="assetNodeFields" size="small" stripe max-height="calc(100vh - 300px)" @row-click="goLineageField" style="cursor: pointer;">
                    <el-table-column label="字段名" min-width="120">
                      <template #default="{ row }">{{ row.targetColumnName || row.sourceColumnName || '-' }}</template>
                    </el-table-column>
                    <el-table-column prop="sourceColumnName" label="来源字段" min-width="110" />
                    <el-table-column label="来源表" min-width="120">
                      <template #default="{ row }">{{ row.sourceTableName || nodeNameMap[row.sourceTableId] || row.sourceTableId }}</template>
                    </el-table-column>
                    <el-table-column label="映射类型" width="90">
                      <template #default="{ row }">{{ mappingTypeLabel(row.mappingTypeId) }}</template>
                    </el-table-column>
                    <el-table-column prop="transformExpression" label="转换表达式" min-width="140" show-overflow-tooltip />
                  </el-table>
                  <el-pagination
                    v-if="fieldTotal > fieldPageSize"
                    style="margin-top: 8px; justify-content: flex-end;"
                    background
                    layout="total, prev, pager, next, sizes"
                    :total="fieldTotal"
                    :current-page="fieldPage"
                    :page-size="fieldPageSize"
                    :page-sizes="[10, 20, 50]"
                    @current-change="(p: number) => { fieldPage = p; loadAssetLineage() }"
                    @size-change="(s: number) => { fieldPageSize = s; fieldPage = 1; loadAssetLineage() }"
                  />
                </template>
                <el-empty v-else description="暂无字段级血缘" :image-size="50" />
              </el-tab-pane>
            </el-tabs>
            <template v-if="extData.lineage.length">
              <h4 style="margin: 12px 0 8px;">血缘备注</h4>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item v-for="item in extData.lineage" :key="item.key" :label="item.key">{{ item.value || '-' }}</el-descriptions-item>
              </el-descriptions>
            </template>
          </el-tab-pane>
          <el-tab-pane label="存储与物理" name="storage">
            <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
              <span style="color: #909399;">存储路径、文件格式、分区信息、生命周期</span>
              <el-button size="small" type="primary" @click="editExtCategory('storage')"><el-icon><Edit /></el-icon> 编辑</el-button>
            </div>
            <template v-if="extData.storage.length">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item v-for="item in extData.storage" :key="item.key" :label="item.key">{{ item.value || '-' }}</el-descriptions-item>
              </el-descriptions>
            </template>
            <el-empty v-else description="暂无数据" />
          </el-tab-pane>
          <el-tab-pane label="数据质量与校验" name="quality">
            <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
              <span style="color: #909399;">校验规则、质量阈值、异常记录处理</span>
              <el-button size="small" type="primary" @click="editExtCategory('quality')"><el-icon><Edit /></el-icon> 编辑</el-button>
            </div>
            <template v-if="extData.quality.length">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item v-for="item in extData.quality" :key="item.key" :label="item.key">{{ item.value || '-' }}</el-descriptions-item>
              </el-descriptions>
            </template>
            <el-empty v-else description="暂无数据" />
          </el-tab-pane>
          <el-tab-pane label="系统与环境" name="system">
            <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
              <span style="color: #909399;">数据源连接、集群资源、接口协议</span>
              <el-button size="small" type="primary" @click="editExtCategory('system')"><el-icon><Edit /></el-icon> 编辑</el-button>
            </div>
            <template v-if="extData.system.length">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item v-for="item in extData.system" :key="item.key" :label="item.key">{{ item.value || '-' }}</el-descriptions-item>
              </el-descriptions>
            </template>
            <el-empty v-else description="暂无数据" />
          </el-tab-pane>
          <el-tab-pane label="业务元数据" name="bizMeta">
            <template v-if="businessData">
              <el-descriptions :column="1" border size="small" style="margin-bottom: 12px;">
                <el-descriptions-item label="上游血缘数">{{ businessData.upstreamCount || 0 }}</el-descriptions-item>
                <el-descriptions-item label="下游血缘数">{{ businessData.downstreamCount || 0 }}</el-descriptions-item>
                <el-descriptions-item label="质量规则数">{{ businessData.qualityRuleCount || 0 }}</el-descriptions-item>
                <el-descriptions-item label="平均评分">{{ businessData.avgRating || '-' }} / 5</el-descriptions-item>
                <el-descriptions-item label="收藏次数">{{ businessData.favoriteCount || 0 }}</el-descriptions-item>
              </el-descriptions>
              <div v-if="businessData.tags?.length" style="margin-top: 8px;">
                <span style="color: #909399;">标签：</span>
                <el-tag v-for="t in businessData.tags" :key="t.id" size="small" style="margin-right: 4px;">{{ t.tagName }}</el-tag>
              </div>
            </template>
            <el-empty v-else description="暂无数据" />
          </el-tab-pane>
          <el-tab-pane label="质量检测" name="qualityChecks">
            <template v-if="relatedData?.qualityRules?.length">
              <el-table :data="relatedData.qualityRules" stripe max-height="calc(100vh - 240px)">
                <el-table-column prop="ruleCode" label="规则编码" min-width="180" />
                <el-table-column prop="ruleType" label="类型" width="100" />
                <el-table-column prop="severity" label="严重级别" width="80" />
                <el-table-column label="最新分数" width="80"><template #default="{ row }">{{ row.lastScore ?? '-' }}</template></el-table-column>
                <el-table-column prop="status" label="状态" width="70" />
              </el-table>
            </template>
            <el-empty v-else description="暂无质量检测规则" />
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <!-- 血缘图谱弹框（点击血缘关系/字段级血缘时在当前页展示，不跳转） -->
    <el-dialog v-model="lineageGraphVisible" :title="`${lineageGraphTitle} 的血缘图谱`" width="70%" top="8vh" append-to-body>
      <LineageGraph v-if="lineageGraphNodeId" :key="lineageGraphNodeId" :node-id="lineageGraphNodeId" :node-name="lineageGraphTitle" :height="360" @node-click="onLineageGraphNodeClick" />
      <template v-if="fieldLineageNodeId">
        <div style="margin: 12px 0 8px; font-weight: 600; font-size: 14px;">{{ fieldLineageNodeName }} 的字段级血缘</div>
        <FieldLineageGraph :fields="fieldLineageFields" />
      </template>
    </el-dialog>

    <!-- 血缘关系详情弹框（聚合行的字段级关联明细） -->
    <el-dialog v-model="lineageDetailVisible" title="血缘关系详情" width="680px" top="12vh" append-to-body>
      <template v-if="lineageDetailRow">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 12px;">
          <el-descriptions-item label="关系">{{ relationTypeLabel(lineageDetailRow.relationType) }}</el-descriptions-item>
          <el-descriptions-item label="关联数量">{{ lineageDetailRow._count }} 条</el-descriptions-item>
          <el-descriptions-item label="源节点">{{ lineageDetailRow.sourceNodeName || lineageDetailRow.sourceId }}</el-descriptions-item>
          <el-descriptions-item label="目标节点">{{ lineageDetailRow.targetNodeName || lineageDetailRow.targetId }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="lineageDetailRow._relations || []" size="small" stripe max-height="360">
          <el-table-column label="序号" width="60">
            <template #default="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column prop="transformRule" label="字段关联条件" min-width="240" show-overflow-tooltip />
          <el-table-column label="操作" width="70">
            <template #default="{ row }">
              <el-button size="small" text type="danger" @click="removeAssetLineage(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="lineageDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新增引用血缘弹窗（业务表外键 → 主数据表） -->
    <el-dialog v-model="fkEditVisible" title="新增引用血缘（业务表外键 → 主数据表）" width="520px" top="20vh" append-to-body>
      <el-form :model="fkEditForm" label-width="90px">
        <el-form-item label="引用表" required>
          <el-select v-model="fkEditForm.sourceId" filterable placeholder="如 就诊表" style="width: 100%;">
            <el-option v-for="s in lineageNodeOptions" :key="s.id" :label="s.sourceName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="引用字段" required>
          <el-input v-model="fkEditForm.sourceColumn" placeholder="如 doctor_id" />
        </el-form-item>
        <el-form-item label="被引用表" required>
          <el-select v-model="fkEditForm.targetId" filterable placeholder="如 医护人员表" style="width: 100%;">
            <el-option v-for="s in lineageNodeOptions" :key="s.id" :label="s.sourceName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="被引用字段" required>
          <el-input v-model="fkEditForm.targetColumn" placeholder="如 id" />
        </el-form-item>
        <el-form-item label="关联条件">
          <el-input v-model="fkEditForm.joinCondition" placeholder="如 outpatient_visit.doctor_id = doctor.id（留空自动生成）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fkEditVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAssetFk">保存</el-button>
      </template>
    </el-dialog>

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
        <el-form-item label="安全等级">
          <el-select v-model="fieldEditForm.securityLevel" clearable placeholder="选择安全等级" style="width: 100%;">
            <el-option v-for="o in securityLevelOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="敏感类型">
          <el-select v-model="fieldEditForm.sensitiveType" clearable placeholder="选择敏感类型" style="width: 100%;">
            <el-option v-for="o in sensitiveTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联表">
          <el-select v-model="fieldEditForm.referenceTableName" filterable remote clearable allow-create
                     :remote-method="searchReferenceTables" :loading="referenceTableLoading"
                     placeholder="输入关键字检索关联表（可手动输入）" style="width: 100%;">
            <el-option v-for="t in referenceTableOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fieldEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="fieldSaving" @click="saveFieldEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 字段变更提示对话框 -->
    <el-dialog v-model="showFieldChangesDialog" title="字段变更" width="550px" top="20vh">
      <div style="margin-bottom: 12px; color: #E6A23C;">检测到 {{ fieldChanges.length }} 个字段变更：</div>
      <el-table :data="fieldChanges" stripe max-height="360">
        <el-table-column label="类型" width="60">
          <template #default="{ row }">
            <el-tag :type="row.type === '新增' ? 'success' : row.type === '删除' ? 'danger' : 'warning'" size="small">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fieldName" label="字段名" min-width="140" />
        <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button @click="showFieldChangesDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmFieldSync">确认同步</el-button>
      </template>
    </el-dialog>

        <!-- 管理元数据：审计追踪、质量监控、生命周期、合规安全 -->
        <template v-if="isMetaType('management')">
          <el-card>
            <el-tabs v-model="mgmtTab">
              <el-tab-pane label="审计日志" name="audit">
                <div style="margin-bottom: 8px; display: flex; gap: 8px;">
                  <el-select v-model="mgmtAuditAction" placeholder="操作类型" clearable style="width: 150px;">
                    <el-option label="登录" value="login" />
                    <el-option label="查询" value="query" />
                    <el-option label="导出" value="export" />
                    <el-option label="修改" value="modify" />
                    <el-option label="删除" value="delete" />
                    <el-option label="管理" value="admin" />
                  </el-select>
                  <el-button type="primary" @click="searchMgmtAuditLogs">查询</el-button>
                </div>
                <template v-if="mgmtAuditLogs.length">
                  <el-table :data="mgmtAuditLogs" stripe max-height="500">
                    <el-table-column prop="operatorId" label="用户" width="100" />
                    <el-table-column prop="operationType" label="操作" width="80" />
                    <el-table-column prop="assetName" label="对象" width="140" show-overflow-tooltip />
                    <el-table-column prop="sqlContent" label="SQL/详情" min-width="200" show-overflow-tooltip />
                    <el-table-column prop="operatorIp" label="IP" width="130" />
                    <el-table-column label="时间" width="165">
                      <template #default="{ row }">{{ (row.operationTime || row.createdAt || '').replace('T', ' ') }}</template>
                    </el-table-column>
                  </el-table>
                </template>
                <el-empty v-else description="暂无审计日志" :image-size="60" />
              </el-tab-pane>
              <el-tab-pane label="质量监控" name="quality" lazy>
                <template v-if="mgmtQualityOverview">
                  <div style="display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 12px;">
                    <el-card shadow="never" style="flex: 1; min-width: 120px;"><div style="text-align: center;"><div style="font-size: 24px; color: #409EFF;">{{ mgmtQualityOverview.totalRules || 0 }}</div><div style="color: #909399; font-size: 13px;">规则总数</div></div></el-card>
                    <el-card shadow="never" style="flex: 1; min-width: 120px;"><div style="text-align: center;"><div style="font-size: 24px; color: #67C23A;">{{ mgmtQualityOverview.passedRules || 0 }}</div><div style="color: #909399; font-size: 13px;">通过规则</div></div></el-card>
                    <el-card shadow="never" style="flex: 1; min-width: 120px;"><div style="text-align: center;"><div style="font-size: 24px; color: #E6A23C;">{{ mgmtQualityOverview.failedRules || 0 }}</div><div style="color: #909399; font-size: 13px;">异常规则</div></div></el-card>
                    <el-card shadow="never" style="flex: 1; min-width: 120px;"><div style="text-align: center;"><div style="font-size: 24px; color: #909399;">{{ mgmtQualityOverview.avgScore || '-' }}</div><div style="color: #909399; font-size: 13px;">平均得分</div></div></el-card>
                  </div>
                </template>
                <el-empty v-else description="暂无质量监控数据" :image-size="60" />
              </el-tab-pane>
              <el-tab-pane label="生命周期" name="lifecycle" lazy>
                <div style="margin-bottom: 8px; font-weight: 600;">存储分层</div>
                <div v-if="mgmtLifecycleTiers" style="margin-bottom: 16px;">
                  <el-descriptions :column="3" border size="small">
                    <el-descriptions-item label="热数据">{{ mgmtLifecycleTiers.hotPercentage || '-' }}%</el-descriptions-item>
                    <el-descriptions-item label="温数据">{{ mgmtLifecycleTiers.warmPercentage || '-' }}%</el-descriptions-item>
                    <el-descriptions-item label="冷数据">{{ mgmtLifecycleTiers.coldPercentage || '-' }}%</el-descriptions-item>
                  </el-descriptions>
                </div>
                <el-empty v-else description="暂未获取存储分层信息" :image-size="40" />
                <div style="margin-bottom: 8px; margin-top: 12px; font-weight: 600;">合规策略</div>
                <div v-if="mgmtPolicies.length">
                  <el-table :data="mgmtPolicies" stripe max-height="300">
                    <el-table-column prop="policyName" label="策略名称" min-width="160" />
                    <el-table-column prop="archiveDays" label="归档触发(天)" width="100" />
                    <el-table-column label="自动执行" width="90">
                      <template #default="{ row }">{{ row.autoExecute ? '是' : '否' }}</template>
                    </el-table-column>
                    <el-table-column prop="status" label="状态" width="70" />
                  </el-table>
                </div>
                <el-empty v-else description="暂无合规策略" :image-size="40" />
              </el-tab-pane>
              <el-tab-pane label="敏感规则" name="sensitive" lazy>
                <template v-if="mgmtSensitiveRules.length">
                  <el-table :data="mgmtSensitiveRules" stripe max-height="500">
                    <el-table-column prop="ruleName" label="规则名称" min-width="180" />
                    <el-table-column prop="ruleType" label="规则类型" width="120" />
                    <el-table-column prop="sensitivityLevel" label="级别" width="80">
                      <template #default="{ row }">
                        <el-tag :type="(row.sensitivityLevel || '').toLowerCase() === 'high' ? 'danger' : (row.sensitivityLevel || '').toLowerCase() === 'medium' ? 'warning' : 'info'" size="small">{{ row.sensitivityLevel }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="status" label="状态" width="70" />
                  </el-table>
                </template>
                <el-empty v-else description="暂无敏感规则" :image-size="60" />
              </el-tab-pane>
            </el-tabs>
          </el-card>
        </template>
      </el-col>
    </el-row>

    <!-- 扩展属性编辑对话框 -->
    <el-dialog v-model="showExtEdit" :title="'编辑 - ' + extCategoryLabels[editingCategory] || editingCategory" width="600px" top="5vh">
      <el-table :data="editProps" stripe max-height="400">
        <el-table-column prop="key" label="属性名" width="180">
          <template #default="{ row, $index }">
            <el-input v-model="row.key" size="small" :placeholder="'例如: ' + getKeyPlaceholder(editingCategory, $index)" />
          </template>
        </el-table-column>
        <el-table-column prop="value" label="属性值" min-width="240">
          <template #default="{ row }">
            <el-input v-model="row.value" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="55">
          <template #default="{ $index }">
            <el-button text type="danger" @click="editProps.splice($index, 1)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 8px;">
        <el-button size="small" @click="editProps.push({ key: '', value: '' })">
          <el-icon><Plus /></el-icon> 添加一行
        </el-button>
      </div>
      <template #footer>
        <el-button @click="showExtEdit = false">取消</el-button>
        <el-button type="primary" @click="confirmExtEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 数据扫描对话框 -->
    <el-dialog v-model="showScanDialog" :title="'数据扫描 - ' + (selectedDs?.dsName || '')" width="900px" top="5vh">
      <div v-if="!scanResult">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 16px;">
          <el-descriptions-item label="数据源">{{ selectedDs?.dsName }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ selectedDs?.dsType }}</el-descriptions-item>
          <el-descriptions-item label="主机">{{ selectedDs?.dsIp }}:{{ selectedDs?.dsPort }}</el-descriptions-item>
          <el-descriptions-item label="数据库">{{ selectedDs?.databaseName }}</el-descriptions-item>
        </el-descriptions>
        <el-form :model="scanForm" label-width="100px">
          <el-form-item label="来源系统">
            <el-select v-model="scanForm.sourceSystem" placeholder="选择来源系统" clearable style="width: 100%;">
              <el-option v-for="s in sourceSystemList" :key="s.systemCode" :label="s.systemName" :value="s.systemCode" />
            </el-select>
          </el-form-item>
          <!-- schema 选择 -->
          <el-form-item label="选择模式" v-if="scanSchemas.length > 0">
            <el-select
              v-model="selectedSchemas"
              multiple
              collapse-tags
              collapse-tags-tooltip
              filterable
              clearable
              placeholder="搜索并选择要扫描的模式"
              style="width: 100%;"
              :loading="schemaLoading"
              @change="onSchemaChange"
            >
              <el-option
                key="__all__"
                label="全选"
                value="__all__"
                @click="handleSchemaCheckAll"
              />
              <el-option
                v-for="s in scanSchemas"
                :key="s"
                :label="s"
                :value="s"
              />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      <div v-else>
        <div style="margin-bottom: 12px; color: #909399; font-size: 13px;">
          共发现 {{ scanResult.length }} 张表（
          <span style="color: #67c23a;">新增 {{ scanResult.filter((t: any) => !t.existing).length }}</span>、
          <span style="color: #e6a23c;">更新 {{ scanResult.filter((t: any) => t.existing).length }}</span>
          ），未指定分类的表将归入「未分类」，注册后仍可修改分类
        </div>
        <el-table :data="pagedScanResult" stripe max-height="400" style="width: 100%;">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="tableName" label="表名" min-width="160" />
          <el-table-column prop="tableType" label="类型" width="70" />
          <el-table-column label="状态" width="70">
            <template #default="{ row }">
              <el-tag :type="row.existing ? 'warning' : 'success'" size="small">{{ row.existing ? '更新' : '新增' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remarks" label="注释" min-width="140" show-overflow-tooltip />
          <el-table-column label="字段数" width="70">
            <template #default="{ row }">{{ row.columns?.length || 0 }}</template>
          </el-table-column>
          <el-table-column label="资产分类" min-width="220">
            <template #default="{ row }">
              <el-tree-select
                v-model="row.categoryId"
                :data="categories"
                node-key="id"
                :props="{ label: 'categoryName', children: 'children', disabled: isDomainNode }"
                placeholder="选择类目"
                clearable check-strictly style="width: 100%;"
              />
            </template>
          </el-table-column>
        </el-table>
        <div style="margin-top: 12px; display: flex; justify-content: center;">
          <el-pagination
            v-model:current-page="scanPage"
            :page-size="scanPageSize"
            :total="scanResult.length"
            layout="total, prev, pager, next, jumper"
            small
            background
          />
        </div>
      </div>
      <template #footer>
        <template v-if="!scanResult">
          <el-button @click="showScanDialog = false; scanResult = null">取消</el-button>
          <el-button type="primary" :loading="scanning" @click="startScan">开始扫描</el-button>
        </template>
        <template v-else>
          <el-button @click="showScanDialog = false; scanResult = null">关闭</el-button>
          <el-button type="primary" :loading="registering" @click="batchRegister">批量注册</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 新增/编辑数据源对话框 -->
    <el-dialog v-model="showDsEdit" :title="isDsCreate ? '新增数据源' : '编辑数据源'" width="520px" top="10vh">
      <el-form :model="dsEditForm" label-width="100px">
        <el-form-item label="数据源名称"><el-input v-model="dsEditForm.dsName" placeholder="例如: 生产HIS库" /></el-form-item>
        <el-form-item label="数据源类型">
          <el-select v-model="dsEditForm.dsType">
            <el-option v-for="t in dsSourceTypes" :key="t.configCode" :label="t.configName" :value="t.configCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属层级">
          <el-select v-model="dsEditForm.dataLayer" placeholder="选择数仓层级" style="width: 100%;">
            <el-option v-for="l in dataLayerOptions" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机地址"><el-input v-model="dsEditForm.dsIp" placeholder="localhost" /></el-form-item>
        <el-form-item label="端口"><el-input-number v-model="dsEditForm.dsPort" :min="1" :max="65535" /></el-form-item>
        <el-form-item label="数据库名"><el-input v-model="dsEditForm.databaseName" /></el-form-item>
        <el-form-item label="用户名"><el-input v-model="dsEditForm.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="dsEditForm.password" type="password" :placeholder="isDsCreate ? '请输入密码' : '留空则不修改原密码'" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="dsEditForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDsEdit = false">取消</el-button>
        <el-button :loading="dsTesting" @click="testDsConnection">测试连接</el-button>
        <el-button type="primary" :loading="dsSaving" @click="submitDsEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 变更详情对话框 -->
    <el-dialog v-model="showChangesDialog" title="结构变更详情" width="700px" top="5vh">
      <div v-if="changesData" style="max-height: 500px; overflow-y: auto;">
        <el-alert
          :title="'共 ' + changesData.changeCount + ' 项变更'"
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 12px;"
        />
        <div v-for="(change, idx) in changesData.changes" :key="idx" style="padding: 6px 0; border-bottom: 1px solid #f0f0f0; font-size: 13px;">
          <el-tag :type="changeTagType(change.changeType)" size="small" style="margin-right: 8px;">{{ change.changeType }}</el-tag>
          <span>{{ change.detail }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="showChangesDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 技术类型分类管理对话框 -->
    <el-dialog v-model="showTechTypeManager" title="技术元数据分类管理" width="550px" top="5vh">
      <div style="margin-bottom: 12px; display: flex; justify-content: flex-end;">
        <el-button type="primary" size="small" @click="handleAddTechType">
          <el-icon><Plus /></el-icon> 新增分类
        </el-button>
      </div>
      <el-table :data="techTypes" stripe max-height="400" style="width: 100%;">
        <el-table-column prop="code" label="编码" width="140" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="sortOrder" label="排序" width="55" />
        <el-table-column label="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row, $index }">
            <el-button size="small" type="primary" text @click="handleEditTechType(row)">编辑</el-button>
            <el-button size="small" type="danger" text @click="handleDeleteTechType(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 新增/编辑技术类型对话框 -->
    <el-dialog v-model="showTechTypeForm" :title="isEditTechType ? '编辑分类' : '新增分类'" width="420px" top="30vh">
      <el-form :model="techTypeForm" label-width="80px">
        <el-form-item label="类型编码">
          <el-input v-model="techTypeForm.configCode" placeholder="如 database_table" :disabled="isEditTechType" />
        </el-form-item>
        <el-form-item label="类型名称">
          <el-input v-model="techTypeForm.configName" placeholder="如 数据库表" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="techTypeForm.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="techTypeForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTechTypeForm = false">取消</el-button>
        <el-button type="primary" @click="confirmSaveTechType">确认</el-button>
      </template>
    </el-dialog>

    <!-- 编辑数据库表对话框 -->
    <el-dialog v-model="showEditDbTable" title="编辑数据库表" width="550px" top="5vh">
      <el-form :model="editDbForm" label-width="100px">
        <el-form-item label="表名"><el-input v-model="editDbForm.assetName" /></el-form-item>
        <el-form-item label="表注释"><el-input v-model="editDbForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="数据库"><el-input v-model="editDbForm.databaseName" /></el-form-item>
        <el-form-item label="模式"><el-input v-model="editDbForm.schemaName" /></el-form-item>
        <el-form-item label="主题域">
          <el-select v-model="editDbForm.subjectDomain" placeholder="选择主题域" filterable style="width: 100%;">
            <el-option v-for="s in subjectDomainOptions" :key="s.value" :label="`${s.value}-${s.label}`" :value="s.value" />
            <el-option label="未设置" value="" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属系统">
          <el-select v-model="editDbForm.sourceSystem" placeholder="选择来源系统" clearable style="width: 100%;">
            <el-option v-for="s in sourceSystemList" :key="s.systemCode" :label="s.systemName" :value="s.systemCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="存储大小">
          <el-input-number v-model="editDbForm.storageSize" :min="0" :max="999999999999" style="width: 100%;" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDbTable = false">取消</el-button>
        <el-button type="primary" @click="confirmEditDbTable">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { catalogApi, metadataApi, qualityApi, securityApi, lifecycleApi, lineageApi, agentApi } from '@/api'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import type { TabPaneName, TagProps } from 'element-plus'
import {Search, Refresh, ArrowLeft, Plus, Edit, Delete} from '@element-plus/icons-vue'
import { encryptPassword } from '@/utils/crypto'
import LineageGraph from '@/components/lineage/LineageGraph.vue'
import FieldLineageGraph from '@/components/lineage/FieldLineageGraph.vue'

const route = useRoute()
const router = useRouter()

function isMetaType(type: string) { return metaType.value === type }

const dataSources = ref<any[]>([])
const dsLoading = ref(false)

// 数据源所属数仓层级（与 metadata_datasource.data_layer 列枚举一致）
// 元数据层级（固定 6 层）：源库 → ODS → DWD → DWS → ADS → DIM
const dataLayerOptions = [
  { value: 'SOURCE', label: '源库' },
  { value: 'ODS', label: '操作数据存储层' },
  { value: 'DWD', label: '明细数据层' },
  { value: 'DWS', label: '汇总数据层' },
  { value: 'ADS', label: '应用数据层' },
  { value: 'DIM', label: '维度层' }
]
function dataLayerLabel(code: any) {
  // 展示格式：编码-名称，如 ODS-操作数据存储层（未设置时按默认层级 SOURCE 展示）
  const key = code || 'SOURCE'
  const item = dataLayerOptions.find((l: any) => l.value === key)
  return item ? `${item.value}-${item.label}` : key
}
// el-tag type 允许的取值集合
type TagType = NonNullable<TagProps['type']>

function dataLayerTagType(code: any): TagType {
  const map: Record<string, TagType> = {
    SOURCE: 'info', ODS: 'primary', DWD: 'success', DWS: 'warning', DIM: 'danger', ADS: 'info', DWA: 'danger'
  }
  return map[code || 'SOURCE'] || 'info'
}

// 通用业务主题域（内置，非配置驱动）
const subjectDomainCommon = [
  { value: 'PATIENT', label: '患者域' },
  { value: 'CLINICAL', label: '临床域' },
  { value: 'TREATMENT', label: '诊疗域' },
  { value: 'SURGERY', label: '手术域' },
  { value: 'MEDICATION', label: '药品域' },
  { value: 'FINANCE', label: '费用域' },
  { value: 'ADMIN', label: '管理域' },
  { value: 'REFERENCE', label: '基础数据域' },
  { value: 'LOG', label: '日志域' },
  { value: 'WORKFLOW', label: '流程域' }
]
// 表主题域选项：合并加载两个字典类型的数据（均在“系统配置-字典管理”中维护）
// - dict_dim_type：基础字典主题域（DIM_*，如 DIM_Pharmacy-药品与药事管理域）
// - dict_biz_type：业务主题域（如 PATIENT-患者域）
// 展示格式：编码-名称；两个字典均为空时兑底使用内置通用业务主题域。
const subjectDomainOptions = ref<any[]>([])
async function loadSubjectDomainOptions() {
  const all: any[] = []
  const seen = new Set<string>()
  const pushDict = (list: any[]) => {
    for (const d of list || []) {
      if (d?.dictValue == null) continue
      const key = String(d.dictValue)
      if (seen.has(key)) continue
      seen.add(key)
      all.push({ value: d.dictValue, label: d.dictLabel })
    }
  }
  // 两个字典独立加载互不影响，任一失败不影响另一字典展示
  try { pushDict((await catalogApi.getDictData('dict_dim_type')).data) } catch (_e) {}
  try { pushDict((await catalogApi.getDictData('dict_biz_type')).data) } catch (_e) {}
  subjectDomainOptions.value = all.length ? all : [...subjectDomainCommon]
}
function subjectDomainLabel(code: any) {
  if (!code) return '-'
  const item = subjectDomainOptions.value.find((l: any) => l.value === code)
  return item ? `${item.value}-${item.label}` : code
}
const selectedDsId = ref<number>(0)
const selectedDs = ref<any>(null)
const collections = ref<any[]>([])
const tables = ref<any[]>([])
const tableSearch = ref('')
const selectedTable = ref<any>(null)
const loading = ref(false)
const technicalData = ref<any>(null)
const businessData = ref<any>(null)
const relatedData = ref<any>(null)
const techTab = ref('structure')

// 血缘管理视图（表详情抽屉"数据血缘与依赖"Tab）
const lineageNodeInfo = ref<any>(null)
const lineageEdges = ref<any[]>([])
const assetNodeFields = ref<any[]>([])
const nodeNameMap = ref<Record<number, string>>({})
// 血缘边分页状态（大表血缘过多时分页加载）
const lineagePage = ref(1)
const lineagePageSize = ref(20)
const lineageTotal = ref(0)
// 字段级血缘分页状态
const fieldPage = ref(1)
const fieldPageSize = ref(20)
const fieldTotal = ref(0)
// 血缘子 Tab：edges=表级血缘，fields=字段级血缘
const lineageSubTab = ref('edges')

// 元数据类型切换
const metaType = ref<string>('technical')

const metaTypeLabel = computed(() => {
  const map: Record<string, string> = { technical: '技术元数据', business: '业务元数据', management: '管理元数据' }
  return map[metaType.value] || '元数据管理'
})

// 技术元数据类型
const techTypes = ref<any[]>([])
const selectedTechType = ref<string>('TABLE')

// 是否“数据表”技术类型（数据源采集/表列表挂在该类型下）
const isTableType = computed(() => String(selectedTechType.value || '').toUpperCase() === 'TABLE')
const showTechTypeManager = ref(false)
const showTechTypeForm = ref(false)
const isEditTechType = ref(false)
const techTypeForm = ref<any>({})

function techTypeIcon(code: string) {
  const map: Record<string, any> = {
    database_table: 'Connection',
    file: 'Document',
    api: 'Link',
    view: 'View',
    procedure: 'Cpu',
    message_queue: 'ChatDotSquare',
    schedule_task: 'Timer',
    report_dashboard: 'DataBoard'
  }
  return map[code] || 'Folder'
}

async function loadTechTypes() {
  try {
    const res: any = await catalogApi.getConfig('tech_asset_type')
    const list: any[] = res.data || []
    // 技术元数据类型完全来自配置中心 tech_asset_type（数据表/视图/文件/API/报表）
    techTypes.value = list.length ? list : [{ configCode: 'TABLE', configName: '数据表' }]
    if (!selectedTechType.value) {
      selectedTechType.value = techTypes.value[0].configCode
    }
  } catch (_e) {
    techTypes.value = [{ configCode: 'TABLE', configName: '数据表' }]
  }
}

async function selectTechType(t: any) {
  selectedTechType.value = t.configCode
  // 重置右边内容
  selectedTable.value = null
  technicalData.value = null
  tables.value = []
  if (String(t.configCode || '').toUpperCase() === 'TABLE') {
    loadDbTableAssets()
  } else {
    searchTechAssets(t.configCode)
  }
}

// 供 el-tabs @tab-change 使用（TabPaneName 可能为 string | number，需转为字符串）
function selectTechTypeByCode(code: TabPaneName) {
  const codeStr = String(code)
  selectedTechType.value = codeStr
  selectedTable.value = null
  technicalData.value = null
  tables.value = []
  if (codeStr.toUpperCase() === 'TABLE') {
    loadDbTableAssets()
  } else {
    searchTechAssets(codeStr)
  }
}

// 数据库表清单
const dbTableKeyword = ref('')
// 主题域筛选（NONE=未设置主题域）
const dbTableSubjectDomain = ref('')
const dbTableAssets = ref<any[]>([])
const dbTablePage = ref(1)
const dbTablePageSize = ref(20)
const dbTableTotal = ref(0)
const dbTableLoading = ref(false)
const dsNameMap = ref<Record<string, string>>({})
const dsIdMap = ref<Record<string, number>>({})

function getDataSourceName(databaseName: string): string {
  return dsNameMap.value[databaseName] || databaseName || '-'
}

function formatStorageSize(bytes: number | null | undefined): string {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

async function loadDbTableAssets() {
  dbTableLoading.value = true
  try {
    // 构建数据源名称映射
    const dsRes: any = await catalogApi.getDataSources()
    const map: Record<string, string> = {}
    const idMap: Record<string, number> = {}
    ;(dsRes.data || []).forEach((ds: any) => {
      map[ds.databaseName] = ds.dsName
      if (ds.id) idMap[ds.databaseName] = ds.id
    })
    dsNameMap.value = map
    dsIdMap.value = idMap

    const res: any = await catalogApi.searchAssets({
      keyword: dbTableKeyword.value || undefined,
      assetType: 'table',
      subjectDomain: dbTableSubjectDomain.value || undefined,
      page: dbTablePage.value,
      pageSize: dbTablePageSize.value
    })
    const list: any[] = res.data?.records || []
    dbTableTotal.value = res.data?.total || 0

    // 批量加载字段数
    for (const asset of list) {
      try {
        const fields: any = await catalogApi.getFields(asset.id)
        asset._fieldCount = fields.data?.length || 0
      } catch (_e) { asset._fieldCount = 0 }
    }
    dbTableAssets.value = list
  } catch (_e) { dbTableAssets.value = [] }
  finally { dbTableLoading.value = false }
}

// 编辑数据库表
const showEditDbTable = ref(false)
const editDbForm = ref<any>({})
// 主题域保存状态
const subjectDomainSaving = ref(false)

/** 保存表主题域（后端同步更新 metadata_table.subject_domain） */
async function saveTableSubjectDomain(val: string) {
  const assetId = selectedTable.value?.assetId || syncAssetId.value
  if (!assetId) {
    ElMessage.warning('未找到资产ID，无法保存主题域')
    return
  }
  subjectDomainSaving.value = true
  try {
    await catalogApi.updateAsset({ id: assetId, subjectDomain: val || '' })
    ElMessage.success('主题域已更新')
    // 同步更新列表行展示
    const row = dbTableAssets.value.find((t: any) => t.id === assetId)
    if (row) row.subjectDomain = val || ''
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    subjectDomainSaving.value = false
  }
}

function openEditDbTable(row: any) {
  editDbForm.value = { ...row }
  showEditDbTable.value = true
}

async function confirmEditDbTable() {
  try {
    await catalogApi.updateAsset(editDbForm.value)
    ElMessage.success('保存成功')
    showEditDbTable.value = false
    // 若当前处于“数据源下的表列表”，用 loadDsTables 刷新（带 dataSourceId/排序），否则刷新全库资产列表
    if (selectedDs.value?.id) {
      loadDsTables()
    } else {
      loadDbTableAssets()
    }
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
}

function openDbTableDetail(row: any) {
  selectedTable.value = {
    tableName: row.assetName || row.tableName,
    remarks: row.description,
    tableType: 'table',
    schemaName: row.schemaName || undefined,
    databaseName: row.databaseName || undefined,
    subjectDomain: row.subjectDomain || '',
    assetId: row.id
  }
  // 保存 assetId 供同步字段使用
  syncAssetId.value = row.id
  // 设置数据源ID（供同步字段使用）：优先通过 databaseName 映射，否则搜索匹配的数据源
  selectedDs.value = resolveDataSource(row.databaseName, row.schemaName)
  businessData.value = null
  relatedData.value = null
  showDbDetailDrawer.value = true
  loading.value = true
  Promise.all([
    metadataApi.getTechnicalMetadata(row.id),
    metadataApi.getBusinessMetadata(row.id),
    metadataApi.getRelatedMetadata(row.id)
  ]).then(([tech, biz, rel]) => {
    technicalData.value = tech.data
    businessData.value = biz.data
    relatedData.value = rel.data
  }).catch(() => {
    technicalData.value = { fields: [], extended: {} }
  }).finally(() => { loading.value = false })
  loadAssetLineage()
  techTab.value = 'structure'
}

/** 根据 databaseName 和 schemaName 查找匹配的数据源 */
function resolveDataSource(databaseName?: string, schemaName?: string): any {
  if (databaseName && dsIdMap.value[databaseName]) {
    return { id: dsIdMap.value[databaseName], name: dsNameMap.value[databaseName] }
  }
  // 如果 databaseName 映射不到，尝试遍历所有数据源模糊匹配
  if (databaseName) {
    for (const ds of dataSources.value) {
      if (ds.databaseName === databaseName || (ds.databaseName && databaseName.includes(ds.databaseName))) {
        if (ds.id) return { id: ds.id, name: ds.dsName }
      }
    }
  }
  return null
}

// ==================== AI 生成字段中文注释 ====================

/** 从模型输出中稳健解析 JSON（去除 markdown 代码块/多余文字） */
function parseCommentJson(content: string): Record<string, string> {
  if (!content) return {}
  let text = content.trim().replace(/```(?:json)?/gi, '').trim()
  try {
    const obj = JSON.parse(text)
    if (obj && typeof obj === 'object' && !Array.isArray(obj)) return obj as Record<string, string>
  } catch (_e) { /* 继续尝试提取 */ }
  const start = text.indexOf('{')
  const end = text.lastIndexOf('}')
  if (start >= 0 && end > start) {
    try {
      const obj = JSON.parse(text.substring(start, end + 1))
      if (obj && typeof obj === 'object' && !Array.isArray(obj)) return obj as Record<string, string>
    } catch (_e) { /* 解析失败返回空 */ }
  }
  return {}
}

/** 字段名归一化：小写并去除非字母/数字/中文的字符，用于跨表关联相似字段（patient_id / patientId / PATIENT_ID → patientid） */
function normalizeFieldName(name: string): string {
  return (name || '').toLowerCase().replace(/[^a-z0-9\u4e00-\u9fa5]/g, '')
}

/** 两个归一化字段名的相似度（0~1）：包含关系视为高相似，其余用 bigram 重合度 */
function fieldNameSimilarity(a: string, b: string): number {
  if (!a || !b) return 0
  if (a === b) return 1
  if (a.includes(b) || b.includes(a)) {
    return 0.6 + 0.4 * (Math.min(a.length, b.length) / Math.max(a.length, b.length))
  }
  const grams = (s: string) => {
    const set = new Set<string>()
    for (let i = 0; i < s.length - 1; i++) set.add(s.slice(i, i + 2))
    return set
  }
  const ga = grams(a)
  const gb = grams(b)
  let inter = 0
  ga.forEach((g) => { if (gb.has(g)) inter++ })
  const union = ga.size + gb.size - inter
  return union === 0 ? 0 : inter / union
}

/** 调用编码智能体，为字段批量生成中文注释，返回 {字段名: 中文注释}。与平台已添加注释关联：归一化命中直接复用，未命中按相似度挑术语子集 + 同表已有注释做关联联想，避免逐字直译 */
async function askCommentForFields(
  list: { name: string; type: string }[],
  tableName: string,
  tableContext?: Record<string, string>,
  tableComment?: string,
  forceRegenerate = false
): Promise<Record<string, string>> {
  if (!list.length) return {}
  // 1. 加载全库已有字段注释字典（字段名 → 最高频中文注释）
  let dict: Record<string, string> = {}
  try {
    const dictRes: any = await catalogApi.getFieldCommentDict()
    dict = dictRes?.data || {}
  } catch (_e) { /* 字典加载失败则退化为纯 AI 生成 */ }

  const result: Record<string, string> = {}
  const needAi: { name: string; type: string }[] = []
  // 归一化字典：归一化字段名 → 中文注释（同名复用最高频注释）
  const normDict = new Map<string, string>()
  const normEntries: { name: string; comment: string; norm: string }[] = []
  for (const [name, comment] of Object.entries(dict)) {
    const norm = normalizeFieldName(name)
    if (norm && comment) {
      normEntries.push({ name, comment, norm })
      if (!normDict.has(norm)) normDict.set(norm, comment)
    }
  }

  // 2. 归一化后精确命中已有注释 → 直接复用，保证术语一致（patient_id / patientId 等视为同义）
  //    forceRegenerate 为 true 时跳过复用，全部字段交 AI 重新生成（用于「重新生成」覆盖旧注释场景）
  for (const f of list) {
    const hit = forceRegenerate ? undefined : normDict.get(normalizeFieldName(f.name))
    if (hit) {
      result[f.name] = hit
    } else {
      needAi.push(f)
    }
  }

  // 3. 未命中的字段调编码智能体：参考「同表已有注释 + 全库相似字段术语」做关联联想（而非逐字直译）
  if (needAi.length) {
    const tableLines = Object.entries(tableContext || {})
      .filter(([k, v]) => k && v)
      .map(([k, v]) => `${k}: ${v}`)
      .join('\n')
    const BATCH_SIZE = 12
    for (let i = 0; i < needAi.length; i += BATCH_SIZE) {
      const batch = needAi.slice(i, i + BATCH_SIZE)
      // 为整批字段挑选最相关的全库术语（相似度>0 取前若干，去重），避免无关术语噪声
      const related = new Map<string, string>()
      for (const f of batch) {
        const fn = normalizeFieldName(f.name)
        normEntries
          .map((e) => ({ name: e.name, comment: e.comment, score: fieldNameSimilarity(fn, e.norm) }))
          .filter((x) => x.score > 0)
          .sort((a, b) => b.score - a.score)
          .slice(0, 10)
          .forEach((r) => { if (!related.has(r.name)) related.set(r.name, `${r.name}: ${r.comment}`) })
      }
      const dictLines = Array.from(related.values()).join('\n')
      const lines = batch.map((f) => `- ${f.name} (${f.type || '未知类型'})`).join('\n')
      const tableDesc = tableComment ? `（${tableComment}）` : ''
      const prompt = `你是医疗行业数据治理专家。请为数据库表「${tableName}」${tableDesc}的以下字段生成简洁、准确的中文注释。\n\n当前表已有注释（同表业务口径，优先参考）：\n${tableLines || '（无）'}\n\n平台其他表相似字段注释（术语一致参考）：\n${dictLines || '（无）'}\n\n待生成注释的字段列表：\n${lines}\n\n要求：\n1. 若字段名与上述注释相同或含义相近，直接复用或参考其注释，不要逐字直译\n2. 结合当前表「${tableName}」${tableDesc}的业务语境推断字段含义，使用医疗行业通用术语\n3. 注释需体现字段所属表的业务归属，格式为「表注释/表名+字段含义」，例如表 PA_Adm（就诊记录表）的主键字段注释为「就诊记录表主键」、指针字段注释为「就诊记录表指针」；若字段本身已能独立表达业务含义则不必强行加表名前缀\n4. 每个注释不超过 30 个字\n5. 只输出 JSON 对象，格式：{"字段名":"中文注释"}，不要输出任何其他内容`
      const res: any = await agentApi.complete([{ role: 'user', content: prompt }], { agentType: 'code' })
      Object.assign(result, parseCommentJson(res?.data?.content || ''))
    }
  }
  return result
}

/** 已同步表：为全部字段重新生成中文注释（含已有注释字段），预览后由用户勾选覆盖 */
async function generateFieldComments() {
  const fields = technicalData.value?.fields || []
  if (fields.length === 0) {
    ElMessage.info('暂无字段')
    return
  }
  generatingComments.value = true
  try {
    const tableName = selectedTable.value?.tableName || ''
    const tableComment = selectedTable.value?.tableComment || selectedTable.value?.remarks || selectedTable.value?.description || ''
    // 同表已有注释作为关联上下文
    const tableContext: Record<string, string> = {}
    for (const f of fields) {
      if (f.comment && f.comment.trim()) tableContext[f.fieldName] = f.comment.trim()
    }
    const map = await askCommentForFields(
      fields.map((f: any) => ({ name: f.fieldName, type: f.dataType })),
      tableName,
      tableContext,
      tableComment,
      true // 强制重新生成，覆盖旧注释
    )
    // 生成后先预览，由用户勾选确认后再更新（空注释字段默认勾选，已有注释字段默认不勾选）
    pendingComments.value = fields
      .filter((f: any) => map[f.fieldName] && f.id != null)
      .map((f: any) => ({
        id: f.id,
        fieldName: f.fieldName,
        dataType: f.dataType,
        oldComment: f.comment || '',
        comment: map[f.fieldName],
        checked: !(f.comment && f.comment.trim())
      }))
    if (pendingComments.value.length === 0) {
      ElMessage.warning('未生成到有效注释，请稍后重试')
      return
    }
    showCommentPreview.value = true
  } catch (e: any) {
    ElMessage.error('生成注释失败: ' + (e.message || '未知错误'))
  } finally {
    generatingComments.value = false
  }
}

/** 确认勾选的注释并批量保存 */
async function confirmCommentUpdate() {
  const checked = pendingComments.value.filter(p => p.checked)
  if (checked.length === 0) {
    ElMessage.warning('请至少勾选一个要更新的字段')
    return
  }
  savingComments.value = true
  try {
    let updated = 0
    for (const p of checked) {
      await catalogApi.updateField({ id: p.id, comment: p.comment })
      const f = (technicalData.value?.fields || []).find((x: any) => x.id === p.id)
      if (f) f.comment = p.comment
      updated++
    }
    ElMessage.success(`已更新 ${updated} 个字段注释`)
    showCommentPreview.value = false
  } catch (e: any) {
    ElMessage.error('保存注释失败: ' + (e.message || '未知错误'))
  } finally {
    savingComments.value = false
  }
}

/** 安全等级展示名 */
function securityLevelLabel(level: string | null | undefined): string {
  if (!level) return ''
  const o = securityLevelOptions.value.find(x => x.value === level)
  return o ? o.label : level
}

/** 安全等级标签颜色 */
function securityLevelTagType(level: string): 'info' | 'warning' | 'danger' {
  if (level === 'critical' || level === 'restricted') return 'danger'
  if (level === 'confidential') return 'warning'
  return 'info'
}

/** 敏感类型展示名 */
function sensitiveTypeLabel(type: string | null | undefined): string {
  if (!type) return ''
  const o = sensitiveTypeOptions.value.find(x => x.value === type)
  return o ? o.label : type
}

/** 从模型输出解析分级 JSON：{"字段名":{"level":"...","type":"..."}} */
function parseClassifyJson(content: string): Record<string, { level: string; type: string }> {
  if (!content) return {}
  let text = content.trim().replace(/```(?:json)?/gi, '').trim()
  const parse = (s: string) => {
    try {
      const obj = JSON.parse(s)
      return obj && typeof obj === 'object' && !Array.isArray(obj) ? obj : null
    } catch { return null }
  }
  let obj = parse(text)
  if (!obj) {
    const start = text.indexOf('{')
    const end = text.lastIndexOf('}')
    if (start >= 0 && end > start) obj = parse(text.substring(start, end + 1))
  }
  if (!obj) return {}
  const result: Record<string, { level: string; type: string }> = {}
  for (const [k, v] of Object.entries(obj)) {
    if (v && typeof v === 'object') {
      result[k] = { level: String((v as any).level || ''), type: String((v as any).type || '') }
    } else if (typeof v === 'string') {
      const [level, type] = v.split('|')
      result[k] = { level: (level || '').trim(), type: (type || '').trim() }
    }
  }
  return result
}

/** 调用编码智能体，为字段批量推断安全等级与敏感类型 */
async function askClassifyForFields(
  list: { name: string; type: string; comment?: string }[],
  tableName: string
): Promise<Record<string, { level: string; type: string }>> {
  if (!list.length) return {}
  const result: Record<string, { level: string; type: string }> = {}
  const BATCH_SIZE = 15
  const levelDesc = securityLevelOptions.value.map(o => `${o.value}(${o.label})`).join('、')
  const typeDesc = sensitiveTypeOptions.value.map(o => `${o.value}(${o.label})`).join('、')
  for (let i = 0; i < list.length; i += BATCH_SIZE) {
    const batch = list.slice(i, i + BATCH_SIZE)
    const lines = batch.map(f => `- ${f.name} (${f.type || '未知类型'})${f.comment ? ` # ${f.comment}` : ''}`).join('\n')
    const prompt = `你是医疗行业数据治理专家。请为数据库表「${tableName}」的以下字段推断数据安全等级与敏感类型。\n\n可选安全等级（5级）：${levelDesc}\n可选敏感类型（可留空）：${typeDesc}\n\n字段列表：\n${lines}\n\n要求：\n1. 根据字段名与注释判断是否含敏感个人信息，给出安全等级和敏感类型\n2. 非敏感字段安全等级用 public 或 internal，敏感类型留空\n3. 只输出 JSON 对象，格式：{"字段名":{"level":"安全等级","type":"敏感类型或空字符串"}}，不要输出任何其他内容`
    const res: any = await agentApi.complete([{ role: 'user', content: prompt }], { agentType: 'code' })
    Object.assign(result, parseClassifyJson(res?.data?.content || ''))
  }
  return result
}

/** 已同步表：为全部字段 AI 推断安全分级，预览后由用户勾选确认 */
async function generateFieldClassify() {
  const fields = technicalData.value?.fields || []
  if (fields.length === 0) {
    ElMessage.info('暂无字段')
    return
  }
  generatingClassify.value = true
  try {
    const tableName = selectedTable.value?.tableName || ''
    const map = await askClassifyForFields(
      fields.map((f: any) => ({ name: f.fieldName, type: f.dataType, comment: f.comment })),
      tableName
    )
    pendingClassify.value = fields
      .filter((f: any) => map[f.fieldName] && f.id != null)
      .map((f: any) => ({
        id: f.id,
        fieldName: f.fieldName,
        dataType: f.dataType,
        level: map[f.fieldName].level || '',
        type: map[f.fieldName].type || '',
        checked: true
      }))
    if (pendingClassify.value.length === 0) {
      ElMessage.warning('未生成到有效分级，请稍后重试')
      return
    }
    showClassifyPreview.value = true
  } catch (e: any) {
    ElMessage.error('生成分级失败: ' + (e.message || '未知错误'))
  } finally {
    generatingClassify.value = false
  }
}

/** 确认勾选的字段分级并批量保存 */
async function confirmClassifyUpdate() {
  const checked = pendingClassify.value.filter(p => p.checked)
  if (checked.length === 0) {
    ElMessage.warning('请至少勾选一个要更新的字段')
    return
  }
  savingClassify.value = true
  try {
    let updated = 0
    for (const p of checked) {
      await catalogApi.updateField({ id: p.id, securityLevel: p.level || '', sensitiveType: p.type || '' })
      const f = (technicalData.value?.fields || []).find((x: any) => x.id === p.id)
      if (f) { f.securityLevel = p.level || ''; f.sensitiveType = p.type || '' }
      updated++
    }
    ElMessage.success(`已更新 ${updated} 个字段分级`)
    showClassifyPreview.value = false
  } catch (e: any) {
    ElMessage.error('保存分级失败: ' + (e.message || '未知错误'))
  } finally {
    savingClassify.value = false
  }
}

/** 同步流程：为远程列中缺失注释的字段自动填充中文注释（失败不阻断同步） */
async function autoFillSyncComments() {
  const columns = syncColumns.value || []
  const emptyCols = columns.filter((c: any) => !(c.comment && c.comment.trim()))
  if (emptyCols.length === 0) return
  try {
    const tableName = selectedTable.value?.tableName || ''
    const tableComment = selectedTable.value?.tableComment || selectedTable.value?.remarks || selectedTable.value?.description || ''
    // 同表已有注释作为关联上下文
    const tableContext: Record<string, string> = {}
    for (const c of columns) {
      if (c.comment && c.comment.trim()) tableContext[c.name] = c.comment.trim()
    }
    const map = await askCommentForFields(
      emptyCols.map((c: any) => ({ name: c.name, type: c.type })),
      tableName,
      tableContext,
      tableComment
    )
    let filled = 0
    for (const c of columns) {
      if (!(c.comment && c.comment.trim()) && map[c.name]) {
        c.comment = map[c.name]
        filled++
      }
    }
    if (filled > 0) ElMessage.success(`已自动为 ${filled} 个字段生成中文注释`)
  } catch (_e) {
    // 注释生成失败不影响字段同步主流程
  }
}

// 按需同步字段
async function syncFields() {
  if (!selectedTable.value) return
  if (!selectedDs.value) {
    ElMessage.warning('未找到该表对应的数据源，请先在数据源列表中确认连接信息')
    return
  }
  syncingFields.value = true
  try {
    console.log('[syncFields] 发送请求参数:', {
      dataSourceId: selectedDs.value.id,
      assetId: syncAssetId.value,
      schemaName: selectedTable.value.schemaName,
      tableName: selectedTable.value.tableName
    })
    const res: any = await catalogApi.syncTableFields({
      dataSourceId: selectedDs.value.id,
      assetId: syncAssetId.value,
      schemaName: selectedTable.value.schemaName,
      tableName: selectedTable.value.tableName
    })
    const data = res.data || {}
    console.log('[syncFields] 返回结果:', data)
    syncColumns.value = data.columns || []
    syncAssetId.value = data.assetId
    tableIndexes.value = data.indexes || []
    syncedFkCount.value = data.fkCount || 0
    const changes: any[] = data.changes || []
    
    if (changes.length > 0) {
      // 有变更时仅展示对话框，等待用户确认后再保存
      fieldChanges.value = changes
      showFieldChangesDialog.value = true
    } else {
      // 无变更时直接保存并刷新
      if (syncAssetId.value && syncColumns.value.length > 0) {
        console.log('[syncFields] 无变更，直接保存字段')
        // 同步时为缺失注释的字段自动生成中文注释
        await autoFillSyncComments()
        await catalogApi.confirmSyncFields({
          assetId: syncAssetId.value,
          columns: syncColumns.value
        })
        const fields: any = await catalogApi.getFields(syncAssetId.value)
        console.log('[syncFields] 保存后查询字段:', fields.data)
        if (technicalData.value) {
          technicalData.value.fields = fields.data || []
        }
      } else {
        console.log('[syncFields] 跳过保存: assetId=', syncAssetId.value, 'columns.length=', syncColumns.value.length)
      }
      ElMessage.success('字段已是最新' + (syncedFkCount.value > 0 ? `，同步 ${syncedFkCount.value} 条外键引用血缘` : ''))
    }
  } catch (e: any) {
    ElMessage.error('同步字段失败: ' + (e.message || '未知错误'))
  } finally {
    syncingFields.value = false
  }
}

async function confirmFieldSync() {
  try {
    console.log('[confirmFieldSync] 确认保存字段: assetId=', syncAssetId.value, 'columns=', syncColumns.value)
    // 确认保存前，为缺失注释的字段自动生成中文注释
    await autoFillSyncComments()
    await catalogApi.confirmSyncFields({
      assetId: syncAssetId.value,
      columns: syncColumns.value
    })
    // 刷新字段列表
    if (syncAssetId.value) {
      const fields: any = await catalogApi.getFields(syncAssetId.value)
      console.log('[confirmFieldSync] 保存后查询字段:', fields.data)
      if (technicalData.value) {
        technicalData.value.fields = fields.data || []
      }
    }
    showFieldChangesDialog.value = false
    ElMessage.success('字段同步完成' + (syncedFkCount.value > 0 ? `，同步 ${syncedFkCount.value} 条外键引用血缘` : ''))
  } catch (e: any) {
    ElMessage.error('保存字段失败: ' + (e.message || '未知错误'))
  }
}

function handleDrawerClose() {
  selectedTable.value = null
  technicalData.value = null
  businessData.value = null
  relatedData.value = null
  lineageNodeInfo.value = null
  lineageEdges.value = []
  assetNodeFields.value = []
  nodeNameMap.value = {}
  lineagePage.value = 1
  lineagePageSize.value = 20
  lineageTotal.value = 0
  fieldPage.value = 1
  fieldPageSize.value = 20
  fieldTotal.value = 0
  lineageSubTab.value = 'edges'
}

// ==================== 表血缘视图（数据血缘与依赖 Tab） ====================

/** 节点类型标签（血缘节点类型字典） */
const lineageNodeTypes: Record<string, string> = {
  REPORT: '报表', VIEW: '视图', PROCEDURE: '存储过程', TABLE: '数据表', DICT: '基础字典表', ETL_JOB: 'ETL作业', CTE: 'CTE'
}
function nodeTypeLabel(t: string) { return lineageNodeTypes[t] || t || '未知' }

/** 字段映射类型标签 */
const lineageMappingMap: Record<string, string> = {
  DIRECT: '直接映射', CALC: '计算映射', JOIN: '关联映射', FILTER: '过滤映射', AGGREGATE: '聚合映射', FUNCTION: '函数转换', CASE: '条件判断'
}
function mappingTypeLabel(t: string) { return lineageMappingMap[t] || t || '-' }

/** 血缘关系类型标签（relation_type 字典：FLOW/COPY/TRANSFORM/DEPEND/REFERENCE/GENERATE），展示格式：编码-中文名，如 REFERENCE-外键 */
const lineageRelationMap: Record<string, string> = {
  REFERENCE: '外键', FLOW: '流转', COPY: '复制', TRANSFORM: '转换', DEPEND: '依赖', GENERATE: '生成'
}
function relationTypeLabel(t: string) {
  if (!t) return '-'
  return lineageRelationMap[t] ? `${t}-${lineageRelationMap[t]}` : t
}

/**
 * 血缘关系聚合：同一 源节点→目标节点 + 关系类型 的多条字段引用边合并为一条。
 * 例：SS_User→PA_Adm 的 PAADM_UpdateUser_DR/PAADM_CreateUser 两条 REFERENCE 边合并为一条，
 * 聚合行携带 _relations 明细（原始边列表，供点击详情展开具体字段关系）。
 */
function aggregateLineageEdges(edges: any[]) {
  const grouped: Record<string, any> = {}
  const order: any[] = []
  for (const e of edges || []) {
    const key = `${e.sourceId}->${e.targetId}->${(e.relationType || '')}`
    if (!grouped[key]) {
      const agg = { ...e, _relations: [], _count: 0 }
      agg.transformRule = ''
      grouped[key] = agg
      order.push(agg)
    }
    const agg = grouped[key]
    // 关联条件去重合并（transformRule 承载 "表A.字段 = 表B.字段"，多条以分号连接）
    if (e.transformRule) {
      const rules = agg.transformRule ? agg.transformRule.split(';').filter((s: string) => s.trim()) : []
      if (!rules.includes(e.transformRule)) rules.push(e.transformRule)
      agg.transformRule = rules.join(';')
    }
    agg._relations.push(e)
    agg._count++
  }
  return order
}

/**
 * 加载当前表的血缘关系（通过表名匹配血缘节点 → 血缘边 + 字段级血缘）
 */
async function loadAssetLineage() {
  lineageNodeInfo.value = null
  lineageEdges.value = []
  assetNodeFields.value = []
  nodeNameMap.value = {}
  const tableName = selectedTable.value?.tableName
  if (!tableName) return
  try {
    // 1. 按表名/资产名模糊搜索血缘节点
    const res: any = await lineageApi.listSources({ keyword: tableName, page: 1, pageSize: 10 })
    const nodes: any[] = res.data?.records || []
    // 优先精确匹配 source_code，否则取第一个
    const matched = nodes.find((n: any) => n.sourceCode === tableName) || nodes[0]
    if (!matched) return
    lineageNodeInfo.value = matched
    const nameMap: Record<number, string> = { [matched.id]: matched.sourceName || matched.sourceCode }
    // 2. 分页加载血缘边 + 分页加载字段级血缘
    const [edgesRes, fieldsRes]: any[] = await Promise.all([
      lineageApi.getLineagePage(matched.id, lineagePage.value, lineagePageSize.value),
      lineageApi.getNodeFieldsPage(matched.id, fieldPage.value, fieldPageSize.value)
    ])
    lineageEdges.value = aggregateLineageEdges(edgesRes.data?.records || [])
    lineageTotal.value = edgesRes.data?.total || 0
    assetNodeFields.value = fieldsRes.data?.records || []
    fieldTotal.value = fieldsRes.data?.total || 0
    // 节点名称映射：血缘边两端 + 字段级血缘两端（后端已回填 sourceTableName/targetTableName，此处兼底）
    ;(lineageEdges.value).forEach((e: any) => {
      if (e.sourceNodeName) nameMap[e.sourceId] = e.sourceNodeName
      if (e.targetNodeName) nameMap[e.targetId] = e.targetNodeName
    })
    ;(assetNodeFields.value).forEach((f: any) => {
      if (f.sourceTableName) nameMap[f.sourceTableId] = f.sourceTableName
      if (f.targetTableName) nameMap[f.targetTableId] = f.targetTableName
    })
    nodeNameMap.value = nameMap
  } catch (_e) {
    // 忽略：无血缘数据时不打扰
  }
}

/** 跳转到独立血缘管理页（带节点参数，自动打开该表的血缘图谱） */
function goLineagePage() {
  if (lineageNodeInfo.value?.id) {
    const n = lineageNodeInfo.value
    router.push({
      path: '/lineage',
      query: { nodeId: n.id, nodeName: n.sourceName || n.sourceCode || '' }
    })
  } else {
    router.push('/lineage')
  }
}

/** 点击血缘边 → 聚合行弹出字段关系详情；单条边弹出另一端节点的血缘图谱（顺藤摸瓜查看上下游） */
function goLineageEdge(row: any) {
  if (row._relations && row._relations.length > 1) {
    lineageDetailRow.value = row
    lineageDetailVisible.value = true
    return
  }
  const curId = lineageNodeInfo.value?.id
  const target = row.sourceId === curId
    ? { id: row.targetId, name: row.targetNodeName }
    : { id: row.sourceId, name: row.sourceNodeName }
  if (!target.id) return
  openLineageGraph(target.id, target.name || '节点')
}

/** 点击字段级血缘 → 在当前页弹出来源表节点的血缘图谱 */
function goLineageField(row: any) {
  if (!row.sourceTableId) return
  openLineageGraph(row.sourceTableId, row.sourceTableName || '节点')
}

// ==================== 血缘图谱弹框（当前页展示，不跳转） ====================
const lineageGraphVisible = ref(false)
const lineageGraphTitle = ref('')
const lineageGraphNodeId = ref<number | string>()
// 字段级血缘下钻（点击图谱表节点后展示）
const fieldLineageNodeId = ref<any>()
const fieldLineageNodeName = ref('')
const fieldLineageFields = ref<any[]>([])
// 血缘关系详情弹框（聚合行的字段级关联明细）
const lineageDetailVisible = ref(false)
const lineageDetailRow = ref<any>(null)

/** 打开血缘图谱弹框（点击血缘关系/字段级血缘时在当前页展示，不跳转；图谱由公共组件自管） */
function openLineageGraph(nodeId: number | string, nodeName: string) {
  lineageGraphNodeId.value = nodeId
  lineageGraphTitle.value = nodeName || `节点 #${nodeId}`
  fieldLineageNodeId.value = undefined
  fieldLineageFields.value = []
  lineageGraphVisible.value = true
}

/** 点击图节点：下钻查看该表的字段级血缘图谱 */
function onLineageGraphNodeClick(payload: { id: any; name: string; type: string }) {
  if (payload.type === 'FIELD') return
  fieldLineageNodeId.value = payload.id
  fieldLineageNodeName.value = payload.name
  loadFieldLineage(payload.id)
}

/** 加载指定节点的字段级血缘 */
async function loadFieldLineage(nodeId: any) {
  try {
    const res: any = await lineageApi.getNodeFields(nodeId)
    fieldLineageFields.value = res.data || []
  } catch (_e) {
    fieldLineageFields.value = []
  }
}

// 引用血缘编辑（新增/删除）
const fkEditVisible = ref(false)
const fkEditForm = ref<any>({ sourceId: undefined, sourceColumn: '', targetId: undefined, targetColumn: '', joinCondition: '' })
const lineageNodeOptions = ref<any[]>([])

/** 打开"新增引用血缘"弹窗（加载节点列表） */
async function openFkEdit() {
  try {
    const res: any = await lineageApi.listSources({ page: 1, pageSize: 100 })
    lineageNodeOptions.value = res.data?.records || []
  } catch (_e) {
    lineageNodeOptions.value = []
  }
  fkEditForm.value = { sourceId: undefined, sourceColumn: '', targetId: undefined, targetColumn: '', joinCondition: '' }
  fkEditVisible.value = true
}

/** 保存引用血缘（字段级） */
async function saveAssetFk() {
  if (!fkEditForm.value.sourceId || !fkEditForm.value.sourceColumn.trim()
      || !fkEditForm.value.targetId || !fkEditForm.value.targetColumn.trim()) {
    ElMessage.warning('请填写引用表/字段与被引用表/字段')
    return
  }
  try {
    await lineageApi.createColumnLineage({
      sourceId: fkEditForm.value.sourceId,
      sourceColumn: fkEditForm.value.sourceColumn.trim(),
      targetId: fkEditForm.value.targetId,
      targetColumn: fkEditForm.value.targetColumn.trim(),
      mappingType: 'JOIN',
      joinCondition: fkEditForm.value.joinCondition || undefined,
      relationType: 'REFERENCE'
    })
    ElMessage.success('引用血缘创建成功')
    fkEditVisible.value = false
    await loadAssetLineage()
  } catch (e: any) {
    ElMessage.error('创建失败: ' + (e?.response?.data?.message || e.message || '未知错误'))
  }
}

/** 删除血缘关系 */
async function removeAssetLineage(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除血缘关系：${row.sourceId} → ${row.targetId}？`, '删除确认', { type: 'warning' })
  } catch (_e) {
    return
  }
  try {
    await lineageApi.deleteLineage(row.id)
    ElMessage.success('已删除血缘关系')
    // 详情弹窗内删除后关闭弹窗
    lineageDetailVisible.value = false
    // 当前页删除后为空且非第一页时回退一页
    if (lineageEdges.value.length === 1 && lineagePage.value > 1) {
      lineagePage.value -= 1
    }
    await loadAssetLineage()
  } catch (e: any) {
    ElMessage.error('删除失败: ' + (e?.response?.data?.message || e.message || '未知错误'))
  }
}

// 按技术类型搜索资产
const techAssets = ref<any[]>([])
const techAssetsTotal = ref(0)

async function searchTechAssets(typeCode: string) {
  try {
    const res: any = await catalogApi.searchAssets({
      assetType: typeCode,
      pageSize: 50
    })
    techAssets.value = res.data?.records || []
    techAssetsTotal.value = res.data?.total || 0
  } catch (_e) { techAssets.value = [] }
}

// 技术类型管理
function openTechTypeManager() {
  showTechTypeManager.value = true
}

function handleAddTechType() {
  isEditTechType.value = false
  techTypeForm.value = { configType: 'tech_asset_type', configCode: '', configName: '', sortOrder: 1, status: 1 }
  showTechTypeForm.value = true
}

function handleEditTechType(row: any) {
  isEditTechType.value = true
  techTypeForm.value = { ...row }
  showTechTypeForm.value = true
}

async function handleDeleteTechType(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除分类「${row.configName}」？`, '提示')
    await catalogApi.deleteConfig(row.id)
    ElMessage.success('删除成功')
    await loadTechTypes()
    if (selectedTechType.value === row.configCode) {
      selectedTechType.value = techTypes.value[0]?.configCode || ''
    }
  } catch (_e) {}
}

async function confirmSaveTechType() {
  const data = techTypeForm.value
  if (!data.configCode || !data.configName) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  try {
    if (isEditTechType.value) {
      await catalogApi.updateConfig(data)
    } else {
      await catalogApi.createConfig(data)
    }
    ElMessage.success(isEditTechType.value ? '更新成功' : '创建成功')
    showTechTypeForm.value = false
    await loadTechTypes()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

// 业务元数据
const businessAssets = ref<any[]>([])
const bizCategoryId = ref<number>()
const bizSourceSystem = ref('')
const bizKeyword = ref('')
const bizPage = ref(1)
const bizPageSize = ref(20)
const bizTotal = ref(0)
const showBizDetail = ref(false)
const selectedBizAsset = ref<any>(null)
const bizMetaData = ref<any>(null)
const bizTagFilter = ref<number>()
const bizTags = ref<any[]>([])
const bizImpactData = ref<any>(null)
const securityLevelMap = ref<Record<string, string>>({})

// 管理元数据
const mgmtTab = ref('audit')
const mgmtAuditAction = ref('')
const mgmtAuditLogs = ref<any[]>([])
const mgmtQualityOverview = ref<any>(null)
const mgmtLifecycleTiers = ref<any>(null)
const mgmtPolicies = ref<any[]>([])
const mgmtSensitiveRules = ref<any>(null)

// 扩展属性编辑
const showExtEdit = ref(false)
const editingCategory = ref('')
const editProps = ref<any[]>([])

const extCategoryLabels: Record<string, string> = {
  etl: '数据处理与加工',
  storage: '存储与物理',
  quality: '数据质量与校验',
  system: '系统与环境'
}

const extDefaultKeys: Record<string, string[]> = {
  etl: ['作业名称', '调度频率', '执行引擎', '转换脚本', '依赖关系', 'Git提交版本'],
  storage: ['存储路径', '文件格式', '压缩算法', '分区字段', '数据保留天数', '存储大小'],
  quality: ['非空检查', '唯一性检查', '值域范围', '最大空值率', '数据量波动警戒线', '异常处理方式'],
  system: ['主机地址', '端口', '服务名', 'JDBC驱动', '接口协议', 'Yarn队列', 'Executor内存']
}

// 数据扫描
const showScanDialog = ref(false)
const scanForm = ref({ sourceSystem: '' })
const scanning = ref(false)
const scanResult = ref<any>(null)
const scanPage = ref(1)
const scanPageSize = ref(50)

// 扫描结果前端分页（解决5738行渲染卡顿）
const pagedScanResult = computed(() => {
  if (!scanResult.value) return []
  const start = (scanPage.value - 1) * scanPageSize.value
  return scanResult.value.slice(start, start + scanPageSize.value)
})
let scanId: string | undefined = undefined
const registering = ref(false)
const sourceSystemList = ref<any[]>([])
const categories = ref<any[]>([])

// schema 选择
const scanSchemas = ref<string[]>([])
const selectedSchemas = ref<string[]>([])
const schemaLoading = ref(false)

function handleSchemaCheckAll() {
  // 如果当前已全选，则取消全选；否则全选
  if (selectedSchemas.value.length === scanSchemas.value.length) {
    selectedSchemas.value = []
  } else {
    selectedSchemas.value = [...scanSchemas.value]
  }
}

function onSchemaChange(val: string[]) {
  // 处理全选项：选中全选时替换为所有 schema
  if (val.includes('__all__')) {
    selectedSchemas.value = [...scanSchemas.value]
  }
}

// 数据源编辑
const showDsEdit = ref(false)
const dsEditForm = ref<any>({})
const dsTesting = ref(false)
const dsSaving = ref(false)
const dsSourceTypes = ref<any[]>([])
const isDsCreate = ref(false)

/** 已有密码时的掩码占位符（提交时会被过滤，表示不修改） */
const PWD_MASK = '******'

// 变更展示
const showChangesDialog = ref(false)
const showDbDetailDrawer = ref(false)
const syncingFields = ref(false)
const generatingComments = ref(false)
// AI 生成注释预览（用户勾选确认后再落库）
const showCommentPreview = ref(false)
const savingComments = ref(false)
const pendingComments = ref<{ id: number; fieldName: string; dataType: string; oldComment: string; comment: string; checked: boolean }[]>([])
// 字段分类分级（安全等级 + 敏感类型）
const securityLevelOptions = ref<{ value: string; label: string }[]>([])
const sensitiveTypeOptions = ref<{ value: string; label: string }[]>([])
const generatingClassify = ref(false)
const showClassifyPreview = ref(false)
const savingClassify = ref(false)
const pendingClassify = ref<{ id: number; fieldName: string; dataType: string; level: string; type: string; checked: boolean }[]>([])
// 预览全选/取消全选
const allCommentsChecked = computed(() => pendingComments.value.length > 0 && pendingComments.value.every(p => p.checked))
const allClassifyChecked = computed(() => pendingClassify.value.length > 0 && pendingClassify.value.every(p => p.checked))
function toggleSelectAllComments() {
  const target = !allCommentsChecked.value
  pendingComments.value.forEach(p => { p.checked = target })
}
function toggleSelectAllClassify() {
  const target = !allClassifyChecked.value
  pendingClassify.value.forEach(p => { p.checked = target })
}
const fieldChanges = ref<any[]>([])
const showFieldChangesDialog = ref(false)
const syncColumns = ref<any[]>([])
const syncAssetId = ref<number | null>(null)
const tableIndexes = ref<any[]>([])
/** 本次同步字段检测到的外键引用数量（用于血缘同步提示） */
const syncedFkCount = ref(0)
const changesData = ref<any>(null)
const fieldEditVisible = ref(false)
const fieldSaving = ref(false)
const fieldEditForm = ref<any>({})

function openFieldEdit(row: any) {
  fieldEditForm.value = { ...row }
  // 远程搜索模式下，确保已有关联表出现在选项中（避免显示空白）
  if (row.referenceTableName && !referenceTableOptions.value.includes(row.referenceTableName)) {
    referenceTableOptions.value = [row.referenceTableName, ...referenceTableOptions.value]
  }
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
      comment: fieldEditForm.value.comment,
      securityLevel: fieldEditForm.value.securityLevel || '',
      sensitiveType: fieldEditForm.value.sensitiveType || '',
      referenceTableName: fieldEditForm.value.referenceTableName || ''
    })
    ElMessage.success('保存成功')
    fieldEditVisible.value = false
    // 刷新技术元数据字段列表
    if (technicalData.value?.asset?.id) {
      const res: any = await metadataApi.getTechnicalMetadata(technicalData.value.asset.id)
      technicalData.value = res.data
    }
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    fieldSaving.value = false
  }
}

// 关联表下拉选项（远程搜索已同步表，支持手动输入）
const referenceTableOptions = ref<string[]>([])
const referenceTableLoading = ref(false)
async function searchReferenceTables(keyword: string) {
  const kw = (keyword || '').trim()
  if (!kw) { referenceTableOptions.value = []; return }
  referenceTableLoading.value = true
  try {
    const res: any = await catalogApi.searchAssets({ keyword: kw, assetType: 'table', page: 1, pageSize: 50 })
    const list: any[] = res.data?.records || []
    referenceTableOptions.value = list.map((a: any) => a.tableName || a.assetName).filter(Boolean)
  } catch (_e) { referenceTableOptions.value = [] }
  finally { referenceTableLoading.value = false }
}

onMounted(() => {
  loadTechTypes()
  loadDataSources()
  loadSourceSystems()
  loadSourceTypes()
  loadCategories()
  loadBizTags()
  // 基础字典主题域从系统配置 base_dict 动态加载
  loadSubjectDomainOptions()
  // 预加载安全等级名称映射 + 分级选项
  catalogApi.getConfig('security_level').then((res: any) => {
    const list = res.data || []
    list.forEach((s: any) => { securityLevelMap.value[s.configCode] = s.configName })
    securityLevelOptions.value = list.map((s: any) => ({ value: s.configValue, label: s.configName }))
  }).catch(() => {})
  // 敏感类型字典（可在「系统配置-字典管理」中维护）
  catalogApi.getAllDictData('sensitive_type').then((res: any) => {
    sensitiveTypeOptions.value = (res.data || []).map((d: any) => ({ value: d.dictValue, label: d.dictLabel }))
  }).catch(() => {})
  // 处理导航子菜单传入的 type 参数
  const typeParam = useRoute().query.type as string
  if (typeParam && ['technical', 'business', 'management'].includes(typeParam)) {
    metaType.value = typeParam
    switchMetaType(typeParam)
  }
})

function switchMetaType(type: string) {
  metaType.value = type
  if (type === 'business' && !businessAssets.value.length) {
    searchBusinessAssets()
  }
  if (type === 'management') {
    loadMgmtAuditLogs()
    loadMgmtQualityOverview()
    loadMgmtLifecycle()
    loadMgmtSensitiveRules()
  }
}

function getSecurityLevelName(code: string | null | undefined): string {
  if (!code) return ''
  if (Object.keys(securityLevelMap.value).length === 0) {
    catalogApi.getConfig('security_level').then((res: any) => {
      (res.data || []).forEach((s: any) => { securityLevelMap.value[s.configCode] = s.configName })
    }).catch(() => {})
  }
  return securityLevelMap.value[code] || code
}

// 业务元数据：按条件搜索资产
async function searchBusinessAssets() {
  try {
    const res: any = await catalogApi.searchAssets({
      keyword: bizKeyword.value || undefined,
      categoryId: bizCategoryId.value || undefined,
      page: bizPage.value,
      pageSize: bizPageSize.value
    })
    businessAssets.value = res.data?.records || []
    bizTotal.value = res.data?.total || 0
  } catch (_e) { businessAssets.value = [] }
}

// 业务元数据：展示资产详情
async function showBusinessDetail(row: any) {
  selectedBizAsset.value = row
  showBizDetail.value = true
  bizImpactData.value = null
  try {
    const res: any = await metadataApi.getBusinessMetadata(row.id)
    bizMetaData.value = res.data
  } catch (_e) { bizMetaData.value = null }
  // 加载业务影响分析
  try {
    const impact: any = await lineageApi.impactAnalysis(row.id)
    bizImpactData.value = impact.data
  } catch (_e) {}
}

// 业务元数据：加载标签列表
async function loadBizTags() {
  try {
    const res: any = await catalogApi.getTags()
    bizTags.value = res.data || []
  } catch (_e) { bizTags.value = [] }
}

// 管理元数据：搜索审计日志
async function searchMgmtAuditLogs() {
  try {
    const res: any = await securityApi.searchAuditLogs({
      action: mgmtAuditAction.value || undefined,
      pageSize: 50
    })
    mgmtAuditLogs.value = res.data?.records || res.data || []
  } catch (_e) { mgmtAuditLogs.value = [] }
}

// 管理元数据：加载审计日志
async function loadMgmtAuditLogs() {
  if (mgmtAuditLogs.value.length) return
  await searchMgmtAuditLogs()
}

// 管理元数据：加载质量监控概览
async function loadMgmtQualityOverview() {
  try {
    const res: any = await qualityApi.getOverview()
    mgmtQualityOverview.value = res.data
  } catch (_e) { mgmtQualityOverview.value = null }
}

// 管理元数据：加载生命周期和合规策略
async function loadMgmtLifecycle() {
  try {
    const [tierRes, policyRes] = await Promise.all([
      lifecycleApi.getTierDistribution(),
      lifecycleApi.getPolicies()
    ])
    mgmtLifecycleTiers.value = tierRes.data
    mgmtPolicies.value = policyRes.data || []
  } catch (_e) { mgmtLifecycleTiers.value = null; mgmtPolicies.value = [] }
}

// 管理元数据：加载敏感规则
async function loadMgmtSensitiveRules() {
  try {
    const res: any = await securityApi.getSensitiveRules()
    mgmtSensitiveRules.value = res.data
  } catch (_e) { mgmtSensitiveRules.value = [] }
}

function changeTagType(type: string) {
  if (type === '新增表' || type === '新增字段') return 'success'
  if (type === '删除表' || type === '删除字段') return 'danger'
  return 'warning'
}

function goToAssetDetail(row: any) {
  selectedBizAsset.value = row
  showBizDetail.value = true
  bizImpactData.value = null
  try {
    metadataApi.getBusinessMetadata(row.id).then((res: any) => { bizMetaData.value = res.data }).catch(() => {})
    lineageApi.impactAnalysis(row.id).then((res: any) => { bizImpactData.value = res.data }).catch(() => {})
  } catch (_e) {}
}

async function loadDataSources() {
  dsLoading.value = true
  try {
    const res: any = await catalogApi.getDataSources()
    dataSources.value = (res.data || []).map((ds: any) => ({ ...ds, _toggling: false }))
    // 异步加载每个数据源的统计信息
    loadDataSourceStats()
    loadLayerOverview()
    loadSchemaStats()
  } catch (_e) { dataSources.value = [] }
  finally { dsLoading.value = false }
}

async function loadDataSourceStats() {
  try {
    const assetRes: any = await catalogApi.searchAssets({ assetType: 'table', page: 1, pageSize: 9999 })
    const assets: any[] = assetRes.data?.records || []
    // 按 dataSourceId 精确分组统计（资产持久化列，不受 databaseName 派生字段解析差异影响）
    const stats: Record<string, { count: number; storage: number }> = {}
    for (const a of assets) {
      const key = String(a.dataSourceId ?? 'default')
      if (!stats[key]) stats[key] = { count: 0, storage: 0 }
      stats[key].count++
      stats[key].storage += (a.storageSize || 0)
    }
    for (const ds of dataSources.value) {
      const s = stats[String(ds.id)]
      if (s) {
        ds._tableCount = s.count
        ds._storageSize = s.storage
      }
    }
  } catch (_e) {}
}

// ===== 元数据层级概况与 Schema 统计（参考 frontend-new） =====
const layerOverview = ref<any[]>([])
const schemaStats = ref<Record<string, any>>({})
const activeLayer = ref('ALL')

/** 按所选层级过滤数据源 */
const filteredDataSources = computed(() => {
  if (activeLayer.value === 'ALL') return dataSources.value
  return dataSources.value.filter((ds: any) => (ds.dataLayer || 'SOURCE') === activeLayer.value)
})

/** 全部表数（优先用 Schema 统计） */
const totalTableCount = computed(() =>
  dataSources.value.reduce((sum: number, ds: any) =>
    sum + (schemaStats.value[ds.id]?.tableCount || ds._tableCount || 0), 0)
)

const LAYER_NAME: Record<string, string> = {
  SOURCE: '源库', ODS: 'ODS', DWD: 'DWD', DWS: 'DWS', ADS: 'ADS', DIM: 'DIM'
}
function layerName(code: string) {
  return LAYER_NAME[code] || code
}

/** 加载各层级概况（数据源数/表数/字段数） */
async function loadLayerOverview() {
  try {
    const res: any = await metadataApi.getLayerOverview()
    layerOverview.value = (res.data || []).filter((l: any) =>
      ['SOURCE', 'ODS', 'DWD', 'DWS', 'ADS', 'DIM'].includes(l.layer))
  } catch (_e) { layerOverview.value = [] }
}

/** 加载数据源 Schema 统计（Schema 数 + 每个 Schema 表数） */
async function loadSchemaStats() {
  try {
    const res: any = await metadataApi.getDataSourceSchemaStats()
    const map: Record<string, any> = {}
    for (const it of (res.data || [])) map[String(it.dataSourceId)] = it
    schemaStats.value = map
  } catch (_e) { schemaStats.value = {} }
}

/** AI 自动标注：表注释 + 业务主题域 + 安全分级。按主键游标分批循环调用，直到覆盖全部表 */
async function aiAnnotate(row: any, pendingOnly = true) {
  if (!row || !row.id || row._aiLoading) return
  row._aiLoading = true
  const actionLabel = pendingOnly ? 'AI 补标注（仅未标注的表）' : 'AI 全量标注'
  const loading = ElLoading.service({ lock: true, text: `${actionLabel}中…`, background: 'rgba(0,0,0,0.25)' })
  let cursor: number | undefined
  let scanned = 0
  let annotated = 0
  let domain = 0
  let level = 0
  let total = 0
  let batches = 0
  try {
    // 后端每批 40 张表（避免 Prompt 过大）、游标翻页；最多 100 批（4000 张表）防死循环
    while (batches < 100) {
      batches++
      const res: any = await metadataApi.aiAnnotate({
        dataSourceId: row.id,
        pendingOnly,
        afterId: cursor,
        batchSize: 40
      })
      const d = res?.data || {}
      if (!total) total = d.total || 0
      scanned += d.processed || 0
      annotated += d.annotated || 0
      domain += d.domainAnnotated || 0
      level += d.levelAnnotated || 0
      loading.setText(
        `${actionLabel}中… 已处理 ${scanned}${total ? ' / ' + total : ''} 张表，已标注注释 ${annotated} 张，剩余 ${d.remaining ?? 0} 张`
      )
      cursor = d.nextCursor ?? undefined
      if (!d.hasMore || !d.processed) break
    }
    if (scanned === 0) {
      ElMessage.warning(pendingOnly ? '没有需要补标注的表' : '暂无可标注的表，请先同步表结构')
    } else {
      ElMessage.success(
        `${pendingOnly ? '补标注' : '全量标注'}完成：处理 ${scanned} 张表，写入注释 ${annotated} 张，主题域 ${domain} 张，安全分级 ${level} 张`
      )
    }
    await loadSchemaStats()
    loadLayerOverview()
    if (selectedDsId.value === row.id) await loadDsTables()
  } catch (e: any) {
    ElMessage.error((e.message || 'AI 标注失败') + (scanned ? `（已完成 ${scanned} 张，可重试「补标注」继续）` : ''))
  } finally {
    row._aiLoading = false
    loading.close()
  }
}

/** AI 智能识别资产分类：按资产主键游标分批循环，直到覆盖全部未分类资产 */
async function aiClassifyDs(row: any, pendingOnly = true) {
  if (!row || !row.id || row._aiClassifyLoading) return
  row._aiClassifyLoading = true
  const loading = ElLoading.service({ lock: true, text: 'AI 正在识别资产分类…', background: 'rgba(0,0,0,0.25)' })
  let cursor: number | undefined
  let scanned = 0
  let classified = 0
  let total = 0
  let batches = 0
  try {
    while (batches < 100) {
      batches++
      const res: any = await catalogApi.aiClassify({
        dataSourceId: row.id,
        pendingOnly,
        afterId: cursor,
        batchSize: 40
      })
      const d = res?.data || {}
      if (!total) total = d.total || 0
      scanned += d.processed || 0
      classified += d.classified || 0
      loading.setText(
        `AI 识别资产分类中… 已处理 ${scanned}${total ? ' / ' + total : ''} 个资产，已归类 ${classified} 个，剩余 ${d.remaining ?? 0} 个`
      )
      cursor = d.nextCursor ?? undefined
      if (!d.hasMore || !d.processed) break
    }
    if (scanned === 0) {
      ElMessage.warning('没有需要识别的资产（可能均已分类）')
    } else {
      ElMessage.success(`AI 分类完成：处理 ${scanned} 个资产，归类 ${classified} 个`)
    }
    if (selectedDsId.value === row.id) await loadDsTables()
  } catch (e: any) {
    ElMessage.error((e.message || 'AI 分类失败') + (scanned ? `（已处理 ${scanned} 个，可重试）` : ''))
  } finally {
    row._aiClassifyLoading = false
    loading.close()
  }
}

/** 数据源行点击：点展开箭头只展开 Schema，其余进入表列表 */
function onDsRowClick(row: any, column: any) {
  if (column && column.type === 'expand') return
  selectDataSource(row)
}

async function selectDataSource(ds: any) {
  selectedDsId.value = ds.id
  selectedDs.value = ds
  selectedTable.value = null
  technicalData.value = null
  dbTablePage.value = 1
  loadDsTables()
}

async function loadDsTables() {
  if (!selectedDs.value) return
  dbTableLoading.value = true
  try {
    // 构建数据源名称映射
    const dsRes: any = await catalogApi.getDataSources()
    const map: Record<string, string> = {}
    ;(dsRes.data || []).forEach((d: any) => { map[d.databaseName] = d.dsName })
    dsNameMap.value = map

    const res: any = await catalogApi.searchAssets({
      keyword: dbTableKeyword.value || undefined,
      assetType: 'table',
      dataSourceId: selectedDs.value.id,
      subjectDomain: dbTableSubjectDomain.value || undefined,
      sortBy: 'name',
      page: dbTablePage.value,
      pageSize: dbTablePageSize.value
    })
    const list: any[] = res.data?.records || []
    dbTableTotal.value = res.data?.total || 0

    for (const asset of list) {
      try {
        const fields: any = await catalogApi.getFields(asset.id)
        asset._fieldCount = fields.data?.length || 0
      } catch (_e) { asset._fieldCount = 0 }
    }
    dbTableAssets.value = list
  } catch (_e) { dbTableAssets.value = [] }
  finally { dbTableLoading.value = false }
}

function backToDataSourceList() {
  selectedDsId.value = 0
  selectedDs.value = null
  selectedTable.value = null
  dbTableAssets.value = []
  dbTableTotal.value = 0
}

async function toggleDsStatus(row: any, newVal: string | number | boolean) {
  const newStatus = newVal ? 1 : 0
  row._toggling = true
  try {
    await catalogApi.updateDataSource({ id: row.id, status: newStatus })
    row.status = newStatus
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    row._toggling = false
  }
}

function editDataSource(row: any) {
  isDsCreate.value = false
  dsEditForm.value = { ...row }
  // dsPort 为字符串类型，转为数字供 el-input-number 绑定
  if (dsEditForm.value.dsPort != null) dsEditForm.value.dsPort = Number(dsEditForm.value.dsPort)
  // 已有密码时用掩码占位表示"已设置"，不泄露真实密码；留空保存=不修改
  dsEditForm.value.password = row.hasPassword ? PWD_MASK : ''
  showDsEdit.value = true
}

function addDataSource() {
  isDsCreate.value = true
  dsEditForm.value = { dsName: '', dsType: 'POSTGRESQL', dsIp: '', dsPort: 5432, databaseName: '', username: '', password: '', description: '', status: 1, dataLayer: 'SOURCE' }
  showDsEdit.value = true
}

async function testDsConnection() {
  if (!dsEditForm.value.dsIp || !dsEditForm.value.databaseName) {
    ElMessage.warning('请先填写主机地址和数据库名')
    return
  }
  dsTesting.value = true
  try {
    if (!isDsCreate.value && dsEditForm.value.id && dsEditForm.value.password === PWD_MASK) {
      // 编辑且未修改密码：凭据由后端按ID安全读取，不传密码
      await catalogApi.testDataSourceConnection({ dataSourceId: dsEditForm.value.id })
    } else {
      await catalogApi.testDataSourceConnection({
        dsType: dsEditForm.value.dsType,
        dsIp: dsEditForm.value.dsIp,
        dsPort: dsEditForm.value.dsPort,
        databaseName: dsEditForm.value.databaseName,
        username: dsEditForm.value.username,
        password: await encryptPassword(dsEditForm.value.password)
      })
    }
    ElMessage.success('连接成功！')
  } catch (e: any) {
    ElMessage.error(e.message || '连接失败')
  } finally {
    dsTesting.value = false
  }
}

async function submitDsEdit() {
  if (!dsEditForm.value.dsName) {
    ElMessage.warning('请填写数据源名称')
    return
  }
  dsSaving.value = true
  try {
    // 密码用 catalog 服务 RSA 公钥加密传输；掩码/空表示不修改
    const payload: any = { ...dsEditForm.value }
    if (payload.password && payload.password !== PWD_MASK) {
      payload.password = await encryptPassword(payload.password)
    } else if (!isDsCreate.value) {
      delete payload.password
    }
    if (isDsCreate.value) {
      await catalogApi.createDataSource(payload)
    } else {
      await catalogApi.updateDataSource(payload)
    }
    ElMessage.success(isDsCreate.value ? '新增成功' : '保存成功')
    showDsEdit.value = false
    loadDataSources()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    dsSaving.value = false
  }
}

async function showCollectionTables(row: any) {
  if (row.status !== 'success') return
  try {
    const detail: any = await catalogApi.getCollectionDetail(row.id)
    // 如果有变更数据且当前集合有 changesJson，显示变更详情
    if (row.changesJson && detail.data?.changes) {
      changesData.value = detail.data.changes
      showChangesDialog.value = true
    }
    tables.value = detail.data?.tables || []
  } catch (_e) { tables.value = [] }
}

async function selectTable(row: any) {
  selectedTable.value = row
  businessData.value = null
  relatedData.value = null
  // 查找对应的资产ID
  const searchRes: any = await catalogApi.searchAssets({ keyword: row.tableName, pageSize: 1 })
  const assets = searchRes.data?.records || []
  if (assets.length) {
    const assetId = assets[0].id
    const [tech, biz, rel] = await Promise.all([
      metadataApi.getTechnicalMetadata(assetId),
      metadataApi.getBusinessMetadata(assetId),
      metadataApi.getRelatedMetadata(assetId)
    ])
    technicalData.value = tech.data
    businessData.value = biz.data
    relatedData.value = rel.data
  } else {
    technicalData.value = { fields: row.columns || [], extended: {} }
  }
  techTab.value = 'structure'
}

function getKeyPlaceholder(category: string, index: number): string {
  const keys = extDefaultKeys[category]
  return keys ? keys[index % keys.length] : ''
}

function editExtCategory(category: string) {
  editingCategory.value = category
  const current = (technicalData.value?.extended?.[category] || []) as any[]
  editProps.value = current.length ? current.map((p: any) => ({ key: p.key, value: p.value })) : [{ key: '', value: '' }]
  showExtEdit.value = true
}

async function saveExtCategory(category: string, newProps: any[]) {
  // 批量保存
  if (!technicalData.value?.asset?.id) return
  try {
    await metadataApi.saveExtProperties(technicalData.value.asset.id, category, newProps)
    ElMessage.success('保存成功')
    // 刷新
    const res: any = await metadataApi.getTechnicalMetadata(technicalData.value.asset.id)
    technicalData.value = res.data
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function confirmExtEdit() {
  const validProps = editProps.value.filter((p: any) => p.key?.trim())
  if (!validProps.length) {
    ElMessage.warning('请至少填写一个属性')
    return
  }
  await saveExtCategory(editingCategory.value, validProps)
  showExtEdit.value = false
}

async function loadSourceSystems() {
  try {
    const res: any = await catalogApi.getSourceSystems()
    sourceSystemList.value = res.data || []
  } catch (_e) { sourceSystemList.value = [] }
}

async function loadSourceTypes() {
  try {
    // 数据源类型为后端内置配置（DataSourceTypeService.BUILTIN_TYPES），
    // 不走 sys_config 配置表，避免库中无 source_type 配置时下拉为空
    const res: any = await catalogApi.getDataSourceTypeSelect()
    dsSourceTypes.value = res.data || []
  } catch (_e) { dsSourceTypes.value = [] }
}

async function loadCategories() {
  try {
    // 主题域→类目两级树（按角色过滤可见主题域）
    const res: any = await catalogApi.getCategoryDomains()
    categories.value = buildTree(res.data || [])
  } catch (_e) { categories.value = [] }
}

/** 一级主题域不可直接作为资产分类选择，必须选其下类目 */
function isDomainNode(data: any): boolean {
  return !data.parentId
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

function openScanDialog(ds: any) {
  selectedDs.value = ds
  selectedDsId.value = ds.id
  scanForm.value = { sourceSystem: '' }
  scanResult.value = null
  scanSchemas.value = []
  selectedSchemas.value = []
  showScanDialog.value = true
  // 异步加载 schema 列表
  loadScanSchemas(ds)
}

async function loadScanSchemas(ds: any) {
  if (!ds) return
  schemaLoading.value = true
  try {
    // 凭据由后端按 dataSourceId 安全读取，前端不传密码
    const res: any = await catalogApi.listSchemas({
      dataSourceId: ds.id
    })
    const list: string[] = res.data || []
    // 排除系统 schema
    scanSchemas.value = list.filter((s: string) => !s.startsWith('pg_') && !s.startsWith('_') && s !== 'information_schema')
    // 默认全选
    selectedSchemas.value = [...scanSchemas.value]
  } catch (_e) {
    // 获取 schema 失败不影响扫描，只是没有 schema 选择
    scanSchemas.value = []
  } finally {
    schemaLoading.value = false
  }
}

async function startScan() {
  const ds = selectedDs.value
  if (!ds) return
  scanning.value = true
  try {
    const res: any = await catalogApi.scanDataSource({
      dataSourceId: ds.id,
      schemas: selectedSchemas.value.length > 0 ? selectedSchemas.value : undefined
    })
    scanId = res.data?.scanId
    const tables: any[] = res.data?.tables || []
    const existingSource = tables.find((t: any) => t.sourceSystem)
    if (existingSource && !scanForm.value.sourceSystem) {
      scanForm.value.sourceSystem = existingSource.sourceSystem
    }
    scanResult.value = tables
  } catch (e: any) {
    ElMessage.error('扫描失败: ' + (e.message || '未知错误'))
  } finally {
    scanning.value = false
  }
}

async function batchRegister() {
  registering.value = true
  try {
    const res: any = await catalogApi.batchRegister({
      tables: scanResult.value,
      sourceSystem: scanForm.value.sourceSystem || undefined,
      databaseName: selectedDs.value?.databaseName,
      securityLevel: 'internal',
      dataSourceId: selectedDs.value?.id,
      scanId
    })
    ElMessage.success('成功注册 ' + (res.data?.length || 0) + ' 个资产')
    showScanDialog.value = false
    scanResult.value = null
    await selectDataSource(selectedDs.value)
    // 同步完成后：先 AI 标注表注释/主题域/分级，再 AI 智能识别资产分类（可重复触发，只处理未标注/未分类）
    if (selectedDs.value) {
      const ds = selectedDs.value
      ElMessage.info('正在后台 AI 标注表注释与分级…')
      await aiAnnotate(ds, true)
      ElMessage.info('正在 AI 智能识别资产分类…')
      await aiClassifyDs(ds, true)
    }
  } catch (e: any) {
    ElMessage.error('注册失败: ' + (e.message || '未知错误'))
  } finally {
    registering.value = false
  }
}

// 计算过滤后的表
const filteredTables = computed(() => {
  if (!tableSearch.value) return tables.value
  return tables.value.filter((t: any) => t.tableName?.toLowerCase().includes(tableSearch.value.toLowerCase()))
})

// 计算扩展属性数据
const extData = computed(() => {
  const categories = ['etl', 'lineage', 'business', 'storage', 'quality', 'system']
  const result: Record<string, any[]> = {}
  for (const cat of categories) {
    result[cat] = (technicalData.value?.extended?.[cat] || []).map((p: any) => ({
      key: p.key,
      value: p.value,
      sortOrder: p.sortOrder
    }))
  }
  return result
})

</script>

<style scoped>
.search-bar {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px;
}

.ds-item {
  display: flex; align-items: center; gap: 8px; padding: 10px 12px;
  cursor: pointer; border-radius: 6px; margin: 2px 0; transition: all 0.2s;
}
.ds-item:hover { background-color: #ecf5ff; }
.ds-item.active { background-color: #ecf5ff; color: #409EFF; font-weight: 600; }
.ds-name { font-size: 14px; color: #303133; }
.ds-meta { font-size: 12px; color: #909399; margin-top: 2px; }
.ds-item.active .ds-name { color: #409EFF; }

/* 页面头部操作区 */
.head-actions { display: flex; gap: 8px; }
/* 面板工具栏 */
.panel-toolbar { display: flex; align-items: center; margin-bottom: 14px; }
.toolbar-spacer { flex: 1; }
.ds-sub { color: #8a94a6; font-size: 12px; margin-left: 8px; }
.filter-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.back-btn { padding-left: 0; }
/* 层级概况卡片（沿用 stat-card，可点击筛选） */
.layer-stat { cursor: pointer; transition: all .2s; }
.layer-stat:hover { border-color: var(--med-primary-soft); box-shadow: 0 4px 12px rgba(16, 41, 76, .06); }
.layer-stat.active { border-color: var(--med-primary); background: var(--med-primary-lighter); }
/* 数据源行展开的 Schema 统计 */
.schema-panel { display: flex; flex-wrap: wrap; gap: 10px; padding: 6px 16px 10px 48px; }
.schema-item { display: flex; align-items: center; gap: 8px; padding: 6px 12px; background: var(--med-bg-level2); border: 1px solid var(--med-border); border-radius: 8px; font-size: 12px; }
.schema-name { font-family: Consolas, monospace; color: var(--med-text); }
.schema-count { color: var(--med-primary); font-weight: 600; }
.schema-empty { color: var(--med-text-light); font-size: 12px; }
</style>
