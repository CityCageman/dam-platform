<template>
  <div class="page-container">
    <el-tabs v-model="activeTab">
      <!-- ==================== Tab1 血缘列表 ==================== -->
      <el-tab-pane label="血缘列表" name="list">
        <el-card style="margin-bottom: 12px;">
          <div class="search-bar">
            <el-tree-select
              v-model="filterCategoryId"
              :data="categoryTree"
              :props="{ label: 'categoryName', children: 'children' }"
              node-key="id"
              check-strictly
              clearable
              placeholder="按分类筛选"
              style="width: 200px;"
              @change="onCategoryFilter"
            />
            <el-input v-model="sourceKeyword" placeholder="搜索节点名称/编码" clearable style="width: 260px;" @keyup.enter="loadSources" />
            <el-button type="primary" @click="loadSources">查询</el-button>
            <el-button @click="loadSources">刷新</el-button>
            <span style="color: #909399; margin-left: auto;">共 {{ sources.length }} 个节点</span>
          </div>
        </el-card>
        <el-card>
          <el-table :data="sources" stripe @row-click="openLineageDrawer" style="cursor: pointer;">
            <el-table-column prop="sourceName" label="名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ row.description || '-' }}</template>
            </el-table-column>
            <el-table-column label="数据源类型" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="nodeTypeTag(row.sourceTypeId)">{{ nodeTypeLabel(row.sourceTypeId) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="所属分类" width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ categoryMap[row.categoryId] || '-' }}</template>
            </el-table-column>
            <el-table-column label="所属系统" width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.sourceSystemName || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button size="small" text type="primary" @click.stop="openSourceDialog(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!sources.length" description="暂无血缘节点，可到「SQL分析」解析报表 SQL 或「手动维护」注册节点" :image-size="60" />
        </el-card>
      </el-tab-pane>

      <!-- ==================== Tab2 SQL 分析 ==================== -->
      <el-tab-pane label="SQL分析" name="sql">
        <el-row :gutter="16">
          <el-col :span="10">
            <el-card>
              <template #header><span>报表 SQL 解析</span></template>
              <el-form :model="reportForm" label-width="80px">
                <el-form-item label="报表编码"><el-input v-model="reportForm.reportCode" placeholder="唯一标识（可空）" /></el-form-item>
                <el-form-item label="报表名称"><el-input v-model="reportForm.reportName" placeholder="如 门诊工作量报表" /></el-form-item>
                <el-form-item label="类型">
                  <el-select v-model="reportForm.reportType" style="width: 100%;">
                    <el-option label="报表" value="REPORT" />
                    <el-option label="视图" value="VIEW" />
                    <el-option label="存储过程" value="PROCEDURE" />
                    <el-option label="ETL作业" value="ETL_JOB" />
                    <el-option label="即席查询" value="ADHOC" />
                  </el-select>
                </el-form-item>
                <el-form-item label="分类">
                  <el-tree-select
                    v-model="reportForm.categoryId"
                    :data="categoryTree"
                    :props="{ label: 'categoryName', children: 'children' }"
                    node-key="id"
                    check-strictly
                    clearable
                    placeholder="报表归入分类（可选）"
                    style="width: 100%;"
                  />
                </el-form-item>
                <el-form-item label="SQL">
                  <el-input v-model="reportForm.sql" type="textarea" :rows="10" placeholder="SELECT ... FROM ... JOIN ..." />
                </el-form-item>
                <el-form-item label="覆盖更新">
                  <el-switch v-model="reportForm.replace" />
                  <span style="color: #909399; font-size: 12px; margin-left: 6px;">重新解析时先清空该报表旧血缘</span>
                </el-form-item>
                <el-button type="primary" style="width: 100%;" :loading="parsing" @click="handleParseReportSql">
                  解析并生成血缘
                </el-button>
              </el-form>
            </el-card>
          </el-col>
          <el-col :span="14">
            <el-card>
              <template #header>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <span>解析记录</span>
                  <el-button size="small" text type="primary" @click="loadReportParsers">刷新</el-button>
                </div>
              </template>
              <el-table :data="reportParsers" stripe max-height="620" @row-click="viewParserSql">
                <el-table-column prop="reportName" label="报表名称" min-width="140" show-overflow-tooltip />
                <el-table-column label="类型" width="90">
                  <template #default="{ row }">{{ nodeTypeLabel(row.reportTypeId) }}</template>
                </el-table-column>
                <el-table-column label="表数" width="70">
                  <template #default="{ row }">{{ parseJsonLen(row.parsedTables) }}</template>
                </el-table-column>
                <el-table-column label="字段数" width="70">
                  <template #default="{ row }">{{ parseJsonLen(row.parsedColumns) }}</template>
                </el-table-column>
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="row.parseStatusId === 'SUCCESS' ? 'success' : row.parseStatusId === 'FAILED' ? 'danger' : 'info'" size="small">
                      {{ row.parseStatusId || 'PENDING' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="parseTime" label="解析时间" width="160" />
              </el-table>
              <el-empty v-if="!reportParsers.length" description="暂无解析记录" :image-size="60" />
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- ==================== Tab2.5 AI 血缘识别 ==================== -->
      <el-tab-pane label="AI 识别" name="ai">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-card>
              <template #header><span>AI 智能识别血缘</span></template>
              <el-form label-width="70px">
                <el-form-item label="数据源">
                  <el-select v-model="aiForm.dataSourceId" clearable filterable placeholder="限定表结构范围（可选）" style="width: 100%;">
                    <el-option v-for="ds in aiDataSources" :key="ds.id" :label="ds.dsName" :value="ds.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="SQL">
                  <el-input v-model="aiForm.sql" type="textarea" :rows="14" placeholder="粘贴 SQL，AI 将识别其中的表级与字段级血缘" />
                </el-form-item>
                <el-button type="primary" style="width: 100%;" :loading="aiRecognizing" @click="handleAiRecognize">
                  AI 识别血缘
                </el-button>
                <el-button type="primary" plain style="width: 100%; margin-top: 8px;" :loading="aiRecognizing" @click="handleAiRecognizeTables">
                  识别表间关联（按数据源）
                </el-button>
                <div style="margin-top: 8px; color: #909399; font-size: 12px;">
                  识别原则：业务语义优先于外键字典，_DR 指针字段是表间关联的重要线索。
                </div>
              </el-form>
            </el-card>
          </el-col>
          <el-col :span="16">
            <el-card>
              <template #header>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <span>识别结果预览</span>
                  <el-button
                    v-if="aiTableLineages.length || aiColumnLineages.length"
                    size="small" type="primary" :loading="aiSaving" @click="handleAiSave">
                    确认落库
                  </el-button>
                </div>
              </template>
              <el-tabs v-model="aiPreviewTab">
                <el-tab-pane :label="`表级血缘（${aiTableLineages.length}）`" name="table">
                  <LineageGraph
                    v-if="aiTableLineages.length"
                    :external-nodes="aiGraphNodes"
                    :external-edges="aiGraphEdges"
                    :height="340"
                  />
                  <el-table :data="aiTableLineages" stripe max-height="280">
                    <el-table-column width="50" align="center">
                      <template #default="{ row }"><el-checkbox v-model="row.checked" /></template>
                    </el-table-column>
                    <el-table-column prop="sourceTable" label="源表" min-width="140" show-overflow-tooltip />
                    <el-table-column label="流向" width="50">→</el-table-column>
                    <el-table-column prop="targetTable" label="目标表" min-width="140" show-overflow-tooltip />
                    <el-table-column label="关系" width="90">
                      <template #default="{ row }">{{ relationTypeLabel(row.relationType) }}</template>
                    </el-table-column>
                    <el-table-column prop="transformRule" label="说明" min-width="160" show-overflow-tooltip />
                  </el-table>
                  <el-empty v-if="!aiTableLineages.length" description="识别后将在此展示表级血缘图谱" :image-size="60" />
                </el-tab-pane>
                <el-tab-pane :label="`字段级血缘（${aiColumnLineages.length}）`" name="column">
                  <el-table :data="aiColumnLineages" stripe max-height="460">
                    <el-table-column width="50" align="center">
                      <template #default="{ row }"><el-checkbox v-model="row.checked" /></template>
                    </el-table-column>
                    <el-table-column prop="sourceTable" label="源表" min-width="120" show-overflow-tooltip />
                    <el-table-column prop="sourceColumn" label="源字段" min-width="120" show-overflow-tooltip />
                    <el-table-column label="流向" width="50">→</el-table-column>
                    <el-table-column prop="targetTable" label="目标表" min-width="120" show-overflow-tooltip />
                    <el-table-column prop="targetColumn" label="目标字段" min-width="120" show-overflow-tooltip />
                    <el-table-column prop="mappingType" label="映射" width="80" />
                    <el-table-column prop="transformExpression" label="转换" min-width="130" show-overflow-tooltip />
                  </el-table>
                  <el-empty v-if="!aiColumnLineages.length" description="识别后将在此展示字段级血缘映射" :image-size="60" />
                </el-tab-pane>
              </el-tabs>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- ==================== Tab3 手动维护 ==================== -->
      <el-tab-pane label="手动维护" name="manual">
        <el-row :gutter="16">
          <el-col :span="10">
            <el-card style="margin-bottom: 12px;">
              <template #header><span>手动创建血缘关系</span></template>
              <el-form :model="manualForm" label-width="80px">
                <el-form-item label="源节点" required>
                  <el-select v-model="manualForm.sourceId" filterable placeholder="选择源节点" style="width: 100%;">
                    <el-option v-for="s in sources" :key="s.id" :label="s.sourceName" :value="s.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="目标节点" required>
                  <el-select v-model="manualForm.targetId" filterable placeholder="选择目标节点" style="width: 100%;">
                    <el-option v-for="s in sources" :key="s.id" :label="s.sourceName" :value="s.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="关系类型">
                  <el-select v-model="manualForm.relationType" style="width: 100%;">
                    <el-option label="数据流向" value="FLOW" />
                    <el-option label="复制" value="COPY" />
                    <el-option label="转换" value="TRANSFORM" />
                    <el-option label="依赖" value="DEPEND" />
                    <el-option label="引用" value="REFERENCE" />
                    <el-option label="生成" value="GENERATE" />
                  </el-select>
                </el-form-item>
                <el-form-item label="转换规则">
                  <el-input v-model="manualForm.transformRule" type="textarea" :rows="3" placeholder="可选，填写转换 SQL 或说明" />
                </el-form-item>
                <el-button type="primary" style="width: 100%;" @click="handleCreateLineage">创建血缘关系</el-button>
              </el-form>
            </el-card>
            <el-card style="margin-bottom: 12px;">
              <template #header><span>字段级引用血缘（业务表外键 → 主数据表）</span></template>
              <el-form :model="fkForm" label-width="90px">
                <el-form-item label="引用表" required>
                  <el-select v-model="fkForm.sourceId" filterable placeholder="如 就诊表" style="width: 100%;">
                    <el-option v-for="s in sources" :key="s.id" :label="s.sourceName" :value="s.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="引用字段" required>
                  <el-input v-model="fkForm.sourceColumn" placeholder="如 doctor_id" />
                </el-form-item>
                <el-form-item label="被引用表" required>
                  <el-select v-model="fkForm.targetId" filterable placeholder="如 医护人员表" style="width: 100%;">
                    <el-option v-for="s in sources" :key="s.id" :label="s.sourceName" :value="s.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="被引用字段" required>
                  <el-input v-model="fkForm.targetColumn" placeholder="如 id" />
                </el-form-item>
                <el-form-item label="关联条件">
                  <el-input v-model="fkForm.joinCondition" placeholder="如 outpatient_visit.doctor_id = doctor.id（留空自动生成）" />
                </el-form-item>
                <el-button type="primary" plain style="width: 100%;" @click="handleCreateFk">创建引用血缘</el-button>
              </el-form>
            </el-card>
            <el-card>
              <template #header>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <span>血缘节点管理</span>
                  <el-button type="primary" size="small" text @click="openSourceDialog()">+ 注册</el-button>
                </div>
              </template>
              <el-table :data="sources" stripe max-height="380">
                <el-table-column label="类型" width="90">
                  <template #default="{ row }">
                    <el-tag size="small" :type="nodeTypeTag(row.sourceTypeId)">{{ nodeTypeLabel(row.sourceTypeId) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="sourceName" label="名称" min-width="130" show-overflow-tooltip />
                <el-table-column label="操作" width="110">
                  <template #default="{ row }">
                    <el-button size="small" text @click="openSourceDialog(row)">编辑</el-button>
                    <el-button size="small" text type="danger" @click="removeSource(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!sources.length" description="暂无节点" :image-size="60" />
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>

    <!-- ==================== 血缘图谱抽屉 ==================== -->
    <el-drawer v-model="drawerVisible" :title="drawerNodeName || '血缘图谱'" size="70%" append-to-body>
      <el-tabs v-model="drawerTab">
        <el-tab-pane label="血缘图谱" name="graph">
      <div style="margin-bottom: 12px; display: flex; align-items: center; gap: 8px;">
        <el-button type="primary" size="small" @click="reloadGraph">刷新图谱</el-button>
        <el-button size="small" @click="loadImpactAnalysis">影响分析</el-button>
      </div>
      <el-card style="margin-bottom: 12px;">
        <LineageGraph v-if="selectedSourceId" :key="selectedSourceId" ref="lineageGraphRef" :node-id="selectedSourceId" :node-name="selectedSourceName" :height="380" @node-click="onGraphNodeClick" />
      </el-card>
      <el-card v-if="impactResult" style="margin-bottom: 12px;">
        <template #header><span>影响分析结果</span></template>
        <el-tag type="warning" style="margin-right: 12px;">下游直接影响: {{ impactResult.directDownstream }}</el-tag>
        <el-tag type="danger" style="margin-right: 12px;">下游总影响: {{ impactResult.totalDownstream }}</el-tag>
        <el-tag type="info" style="margin-right: 12px;">上游直接来源: {{ impactResult.directUpstream }}</el-tag>
        <el-tag type="info">上游总来源: {{ impactResult.totalUpstream }}</el-tag>
      </el-card>
      <el-card style="margin-bottom: 12px;">
        <template #header><span>血缘关系列表</span></template>
        <el-table :data="lineageList" stripe max-height="240">
          <el-table-column label="源节点" min-width="150">
            <template #default="{ row }">
              <div>{{ row.sourceNodeName || row.sourceId }}</div>
              <div style="color: #909399; font-size: 12px;">ID: {{ row.sourceId }} · {{ row.sourceType }}</div>
            </template>
          </el-table-column>
          <el-table-column label="流向" width="50">→</el-table-column>
          <el-table-column label="目标" min-width="150">
            <template #default="{ row }">
              <div>{{ row.targetNodeName || row.targetId }}</div>
              <div style="color: #909399; font-size: 12px;">ID: {{ row.targetId }} · {{ row.targetType }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="transformRule" label="转换规则" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.transformRule?.substring(0, 60) || '-' }}</template>
          </el-table-column>
          <el-table-column prop="relationType" label="关系" width="80" />
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="loadNodeHierarchy(row.sourceId)">上游</el-button>
              <el-button size="small" text type="success" @click="loadNodeHierarchy(row.targetId)">下游</el-button>
              <el-button size="small" text type="danger" @click="removeLineage(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!lineageList.length" description="暂无血缘数据" :image-size="60" />
      </el-card>
      <el-card>
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <span>分层血缘明细（精细）</span>
            <el-button v-if="selectedSourceId" size="small" text type="primary" @click="loadNodeHierarchy(selectedSourceId)">刷新</el-button>
          </div>
        </template>
        <el-table :data="hierarchyList" stripe max-height="280">
          <el-table-column label="层级" width="70">
            <template #default="{ row }">{{ layerLabel(row.layerLevel) }}</template>
          </el-table-column>
          <el-table-column label="上游" min-width="140">
            <template #default="{ row }">
              <el-tag size="small">{{ row.upstreamTypeId }}</el-tag>
              <span style="margin-left: 4px;">{{ sourceNameMap[row.upstreamSourceId] || row.upstreamSourceId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="下游" min-width="140">
            <template #default="{ row }">
              <el-tag size="small">{{ row.downstreamTypeId }}</el-tag>
              <span style="margin-left: 4px;">{{ sourceNameMap[row.downstreamSourceId] || row.downstreamSourceId }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="relationTypeId" label="关系" width="80" />
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="showColumnLineage(row.id)">字段血缘</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!hierarchyList.length" description="点击血缘列表「上游/下游」查看分层血缘" :image-size="60" />
      </el-card>
        </el-tab-pane>
        <el-tab-pane label="字段级血缘" name="fields">
          <div style="margin-bottom: 8px; display: flex; align-items: center; gap: 8px;">
            <span v-if="fieldGraphNodeId" style="font-weight: 600; font-size: 13px;">{{ fieldGraphNodeName || fieldGraphNodeId }} 的字段级血缘</span>
            <el-input v-model="fieldKeyword" placeholder="搜索字段名" clearable size="small" style="width: 220px;" />
          </div>
          <el-card style="margin-bottom: 12px;">
            <FieldLineageGraph :fields="nodeFields" />
          </el-card>
          <el-table :data="filteredNodeFields" stripe max-height="360">
            <el-table-column label="字段名" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.targetColumnName || row.sourceColumnName || '-' }}</template>
            </el-table-column>
            <el-table-column label="来源字段" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.sourceColumnName || '-' }}</template>
            </el-table-column>
            <el-table-column label="来源表" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.sourceTableName || sourceNameMap[row.sourceTableId] || row.sourceTableId }}</template>
            </el-table-column>
            <el-table-column label="映射类型" width="100">
              <template #default="{ row }">{{ mappingTypeLabel(row.mappingTypeId) }}</template>
            </el-table-column>
            <el-table-column prop="transformExpression" label="转换表达式" min-width="160" show-overflow-tooltip />
            <el-table-column prop="joinCondition" label="关联条件" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button v-if="row.mappingTypeId !== 'DIRECT'" size="small" text type="warning" @click="showCalcDependency(row.id)">计算依赖</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!filteredNodeFields.length" description="该节点暂无字段血缘信息" :image-size="60" />
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <!-- 节点注册/编辑弹窗 -->
    <el-dialog v-model="sourceDialogVisible" :title="sourceForm.id ? '编辑血缘节点' : '注册血缘节点'" width="480px" top="20vh">
      <el-form :model="sourceForm" label-width="80px">
        <el-form-item label="节点编码" required><el-input v-model="sourceForm.sourceCode" placeholder="唯一标识（表名/报表编码/作业名）" /></el-form-item>
        <el-form-item label="节点名称"><el-input v-model="sourceForm.sourceName" /></el-form-item>
        <el-form-item label="节点类型" required>
          <el-select v-model="sourceForm.sourceTypeId" style="width: 100%;">
            <el-option v-for="(label, value) in nodeTypes" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属分类">
          <el-tree-select
            v-model="sourceForm.categoryId"
            :data="categoryTree"
            :props="{ label: 'categoryName', children: 'children' }"
            node-key="id"
            check-strictly
            clearable
            placeholder="选择分类（可选）"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="所属系统">
          <el-select v-model="sourceForm.sourceSystemId" clearable filterable placeholder="选择来源系统（可选）" style="width: 100%;">
            <el-option v-for="s in sourceSystemList" :key="s.id" :label="s.systemName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="节点路径"><el-input v-model="sourceForm.sourcePath" placeholder="报表文件路径/SQL脚本路径" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="sourceForm.sourceOwner" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="sourceForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sourceDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSource">保存</el-button>
      </template>
    </el-dialog>

    <!-- SQL 查看弹窗 -->
    <el-dialog v-model="parserSqlVisible" title="SQL 内容" width="760px" top="15vh" append-to-body>
      <pre style="white-space: pre-wrap; word-break: break-all; background: #f5f7fa; border-radius: 4px; padding: 12px; max-height: 60vh; overflow-y: auto;">{{ parserSqlContent || '无' }}</pre>
    </el-dialog>

    <!-- 字段级血缘弹窗 -->
    <el-dialog v-model="columnLineageVisible" title="字段级血缘" width="760px" top="15vh" append-to-body>
      <el-table :data="columnLineageList" stripe max-height="420">
        <el-table-column prop="sourceColumnName" label="来源字段" min-width="110" />
        <el-table-column label="来源表" min-width="120">
          <template #default="{ row }">{{ row.sourceTableName || sourceNameMap[row.sourceTableId] || row.sourceTableId }}</template>
        </el-table-column>
        <el-table-column label="流向" width="50">→</el-table-column>
        <el-table-column prop="targetColumnName" label="目标字段" min-width="110" />
        <el-table-column label="目标" min-width="120">
          <template #default="{ row }">{{ row.targetTableName || sourceNameMap[row.targetTableId] || row.targetTableId }}</template>
        </el-table-column>
        <el-table-column label="映射类型" width="90">
          <template #default="{ row }">{{ mappingTypeLabel(row.mappingTypeId) }}</template>
        </el-table-column>
        <el-table-column prop="transformExpression" label="转换表达式" min-width="160" show-overflow-tooltip />
        <el-table-column prop="joinCondition" label="关联条件" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button v-if="row.mappingTypeId !== 'DIRECT'" size="small" text type="warning" @click="showCalcDependency(row.id)">计算依赖</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!columnLineageList.length" description="该分层边暂无字段级血缘" :image-size="60" />
    </el-dialog>

    <!-- 计算依赖弹窗 -->
    <el-dialog v-model="calcDependencyVisible" title="计算字段依赖" width="640px" top="20vh" append-to-body>
      <el-table :data="calcDependencyList" stripe max-height="360">
        <el-table-column prop="targetColumnName" label="计算字段" width="110" />
        <el-table-column label="计算类型" width="100">
          <template #default="{ row }">{{ calcTypeLabel(row.calcTypeId) }}</template>
        </el-table-column>
        <el-table-column prop="calcExpression" label="计算表达式" min-width="180" show-overflow-tooltip />
        <el-table-column label="依赖字段" min-width="160">
          <template #default="{ row }">{{ (row.dependsOnColumnNames || []).join(', ') }}</template>
        </el-table-column>
        <el-table-column prop="dependencyOperatorId" label="操作符" width="70" />
        <el-table-column prop="calcOrder" label="顺序" width="60" />
      </el-table>
      <el-empty v-if="!calcDependencyList.length" description="该字段无计算依赖" :image-size="60" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, computed, watch } from 'vue'
import { catalogApi, lineageApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import LineageGraph from '@/components/lineage/LineageGraph.vue'
import FieldLineageGraph from '@/components/lineage/FieldLineageGraph.vue'

// ==================== 常量 ====================
const nodeTypes: Record<string, string> = {
  REPORT: '报表', VIEW: '视图', PROCEDURE: '存储过程', TABLE: '数据表', DICT: '基础字典表', ETL_JOB: 'ETL作业', CTE: 'CTE'
}
const layerMap: Record<number, string> = { 1: '应用层', 2: '逻辑层', 3: '物理层', 4: '字典层' }
const mappingMap: Record<string, string> = { DIRECT: '直接映射', CALC: '计算映射', JOIN: '关联映射', FILTER: '过滤映射', AGGREGATE: '聚合映射', FUNCTION: '函数转换', CASE: '条件判断' }
const calcMap: Record<string, string> = { ARITHMETIC: '算术运算', AGGREGATE: '聚合计算', CASE: '条件判断', FUNCTION: '函数转换', CONCAT: '字符串拼接' }
const relationTypeMap: Record<string, string> = { REFERENCE: '外键', FLOW: '流转', COPY: '复制', TRANSFORM: '转换', DEPEND: '依赖', GENERATE: '生成' }
function relationTypeLabel(t: string) { return relationTypeMap[t] || t || '-' }

// ==================== 状态 ====================
const activeTab = ref('list')
const graphTitle = ref('')
const lineageList = ref<any[]>([])
const lineageGraphRef = ref<any>()
const parsing = ref(false)
const impactResult = ref<any>(null)
const drawerVisible = ref(false)
const drawerNodeName = ref('')
const drawerTab = ref('graph')
// 字段级血缘图谱（点击表节点后下钻查看）
const fieldGraphNodeId = ref<any>()
const fieldGraphNodeName = ref('')
const nodeFields = ref<any[]>([])
// 字段列表过滤（支持从元数据管理跳转后定位到指定字段）
const fieldKeyword = ref('')
const filteredNodeFields = computed(() => {
  const kw = fieldKeyword.value?.trim()
  if (!kw) return nodeFields.value
  return nodeFields.value.filter((f: any) =>
    (f.sourceColumnName || '').includes(kw) || (f.targetColumnName || '').includes(kw))
})

// 血缘分类
const categoryTree = ref<any[]>([])
const categoryMap = ref<Record<number, string>>({})
const currentCategoryIds = ref<number[]>([])
const filterCategoryId = ref<number>()

// 来源系统
const sourceSystemList = ref<any[]>([])

// 血缘节点
const sources = ref<any[]>([])
const sourceKeyword = ref('')
const selectedSourceId = ref<number>()
const selectedSourceName = ref('')
const sourceDialogVisible = ref(false)
const sourceForm = ref<any>({})
const sourceNameMap = ref<Record<number, string>>({})

// 分层血缘与字段级钻取
const hierarchyList = ref<any[]>([])
const columnLineageVisible = ref(false)
const columnLineageList = ref<any[]>([])
const calcDependencyVisible = ref(false)
const calcDependencyList = ref<any[]>([])

// 报表SQL解析
const reportForm = ref<any>({ reportCode: '', reportName: '', reportType: 'REPORT', sql: '', categoryId: undefined, replace: false })
const reportParsers = ref<any[]>([])
const parserSqlVisible = ref(false)
const parserSqlContent = ref('')

// 手动维护
const manualForm = ref<any>({ sourceId: undefined, targetId: undefined, relationType: 'FLOW', transformRule: '' })
// 字段级引用血缘（外键）
const fkForm = ref<any>({ sourceId: undefined, sourceColumn: '', targetId: undefined, targetColumn: '', joinCondition: '' })

// AI 血缘识别
const aiForm = ref<any>({ sql: '', dataSourceId: undefined })
const aiDataSources = ref<any[]>([])
const aiRecognizing = ref(false)
const aiSaving = ref(false)
const aiTableLineages = ref<any[]>([])
const aiColumnLineages = ref<any[]>([])
const aiPreviewTab = ref('table')
// AI 识别表级血缘 → 复用 LineageGraph 组件（与元数据管理血缘图谱一致）
const aiGraphNodes = computed(() => {
  const nameSet = new Set<string>()
  for (const tl of aiTableLineages.value) {
    if (tl.sourceTable) nameSet.add(tl.sourceTable)
    if (tl.targetTable) nameSet.add(tl.targetTable)
  }
  return Array.from(nameSet).map(name => ({ id: name, label: name, type: 'TABLE' }))
})
const aiGraphEdges = computed(() => aiTableLineages.value
  .filter(tl => tl.sourceTable && tl.targetTable)
  .map(tl => ({ source: tl.sourceTable, target: tl.targetTable, relationType: tl.relationType || 'FLOW' })))

const route = useRoute()

onMounted(async () => {
  await loadCategories()
  await loadSourceSystems()
  await loadSources()
  await loadReportParsers()
  loadAiDataSources()
})

// 支持从元数据管理"前往血缘管理/点击血缘"带参跳转：自动打开对应节点的血缘图谱
// 用 watch（immediate）而非 onMounted，确保页面已挂载（组件复用）时也能响应路由参数变化
watch(() => route.query.nodeId, async (val) => {
  if (!val) return
  const nodeId = Number(val)
  if (!nodeId) return
  const name = String(route.query.nodeName || '')
  await openLineageDrawer({ id: nodeId, sourceName: name, sourceCode: name })
  // 定位到字段级血缘：切换抽屉到"字段列表"Tab 并过滤指定字段
  if (route.query.tab === 'fields') {
    drawerTab.value = 'fields'
    if (route.query.field) fieldKeyword.value = String(route.query.field)
  }
}, { immediate: true })

// ==================== 血缘分类 ====================
async function loadCategories() {
  try {
    const res: any = await catalogApi.getCategories()
    categoryTree.value = buildTree(res.data || [])
    const map: Record<number, string> = {}
    ;(res.data || []).forEach((c: any) => { map[c.id] = c.categoryName })
    categoryMap.value = map
  } catch (_e) {
    categoryTree.value = []
  }
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

function collectChildIds(node: any, ids: number[]) {
  ids.push(node.id)
  ;(node.children || []).forEach((c: any) => collectChildIds(c, ids))
}

function findCategoryNode(nodes: any[], id: number): any {
  for (const n of nodes) {
    if (n.id === id) return n
    if (n.children?.length) {
      const found = findCategoryNode(n.children, id)
      if (found) return found
    }
  }
  return null
}

function onCategoryFilter(val: any) {
  if (!val) {
    currentCategoryIds.value = []
  } else {
    const ids: number[] = []
    collectChildIds(findCategoryNode(categoryTree.value, val), ids)
    currentCategoryIds.value = ids
  }
  loadSources()
}

// ==================== 来源系统 ====================
async function loadSourceSystems() {
  try {
    const res: any = await catalogApi.getSourceSystems()
    sourceSystemList.value = res.data || []
  } catch (_e) {
    sourceSystemList.value = []
  }
}

// ==================== 血缘节点 ====================
async function loadSources() {
  const res: any = await lineageApi.listSources({
    typeId: undefined,
    keyword: sourceKeyword.value || undefined,
    categoryIds: currentCategoryIds.value.length ? currentCategoryIds.value.join(',') : undefined,
    page: 1,
    pageSize: 100
  })
  sources.value = res.data?.records || []
  const map: Record<number, string> = {}
  sources.value.forEach((s: any) => { map[s.id] = s.sourceName || s.sourceCode })
  sourceNameMap.value = map
}

function openSourceDialog(row?: any) {
  sourceForm.value = row ? { ...row } : { sourceTypeId: 'TABLE' }
  sourceDialogVisible.value = true
}

async function saveSource() {
  if (!sourceForm.value.sourceCode) { ElMessage.warning('请填写节点编码'); return }
  try {
    if (sourceForm.value.id) {
      await lineageApi.updateSource(sourceForm.value)
    } else {
      await lineageApi.createSource(sourceForm.value)
    }
    ElMessage.success('保存成功')
    sourceDialogVisible.value = false
    await loadSources()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  }
}

async function removeSource(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除血缘节点「${row.sourceName}」？`, '删除确认', { type: 'warning' })
  } catch (_e) {
    return
  }
  try {
    await lineageApi.deleteSource(row.id)
    ElMessage.success('已删除节点')
    await loadSources()
  } catch (e: any) {
    ElMessage.error('删除失败: ' + (e?.response?.data?.message || e.message || '未知错误'))
  }
}

// ==================== 血缘图谱（抽屉） ====================
async function openLineageDrawer(row: any) {
  selectedSourceId.value = row.id
  selectedSourceName.value = row.sourceName || row.sourceCode
  drawerNodeName.value = row.sourceName || row.sourceCode
  drawerTab.value = 'graph'
  fieldGraphNodeId.value = row.id
  fieldGraphNodeName.value = row.sourceName || row.sourceCode
  impactResult.value = null
  hierarchyList.value = []
  lineageList.value = []
  nodeFields.value = []
  drawerVisible.value = true
  await nextTick()
  await Promise.all([
    loadNodeHierarchy(row.id),
    searchLineage(),
    loadNodeFields(row.id)
  ])
}

/** 加载节点的字段列表（节点作为源或目标的字段级血缘） */
async function loadNodeFields(sourceId: number) {
  try {
    const res: any = await lineageApi.getNodeFields(sourceId)
    nodeFields.value = res.data || []
  } catch (e: any) {
    nodeFields.value = []
  }
}

/** 点击表节点 → 下钻查看该表的字段级血缘图谱 */
function onGraphNodeClick(payload: any) {
  if (!payload || payload.type === 'FIELD') return
  fieldGraphNodeId.value = payload.id
  fieldGraphNodeName.value = payload.name
  drawerTab.value = 'fields'
  loadNodeFields(payload.id)
}

async function searchLineage() {
  const nodeId = selectedSourceId.value
  if (!nodeId) return
  const res: any = await lineageApi.getLineage(nodeId)
  lineageList.value = res.data || []
}

/** 刷新图谱（由公共组件自管数据） */
function reloadGraph() {
  lineageGraphRef.value?.reload?.()
}

async function loadImpactAnalysis() {
  const nodeId = selectedSourceId.value
  if (!nodeId) return
  const res: any = await lineageApi.impactAnalysis(nodeId)
  impactResult.value = res.data
}

async function removeLineage(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除血缘关系：${row.sourceId} → ${row.targetId}？`, '删除确认', { type: 'warning' })
  } catch (_e) {
    return
  }
  try {
    await lineageApi.deleteLineage(row.id)
    ElMessage.success('已删除血缘关系')
    await searchLineage()
  } catch (e: any) {
    ElMessage.error('删除失败: ' + (e?.response?.data?.message || e.message || '未知错误'))
  }
}

// ==================== 报表 SQL 解析 ====================
async function handleParseReportSql() {
  if (!reportForm.value.sql?.trim()) { ElMessage.warning('请填写 SQL'); return }
  parsing.value = true
  try {
    const res: any = await lineageApi.parseReportSql({
      reportCode: reportForm.value.reportCode || reportForm.value.reportName || 'REPORT_' + Date.now(),
      reportName: reportForm.value.reportName || reportForm.value.reportCode || '未命名报表',
      reportType: reportForm.value.reportType,
      sql: reportForm.value.sql,
      categoryId: reportForm.value.categoryId,
      replace: reportForm.value.replace === true,
      sourceSystem: 'MANUAL'
    })
    const d = res.data || {}
    ElMessage.success(`解析完成：表 ${d.tablesParsed || 0}，字段 ${d.columnsParsed || 0}，血缘边 ${d.hierarchyEdges || 0}，计算依赖 ${d.calcDependencies || 0}${d.replaced ? '（已覆盖旧血缘）' : ''}`)
    await loadSources()
    await loadReportParsers()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '解析失败')
  } finally {
    parsing.value = false
  }
}

async function loadReportParsers() {
  try {
    const res: any = await lineageApi.listReportParsers({ keyword: undefined, status: undefined })
    reportParsers.value = res.data || []
  } catch (_e) {
    reportParsers.value = []
  }
}

function viewParserSql(row: any) {
  parserSqlContent.value = row.sqlContent || ''
  parserSqlVisible.value = true
}

function parseJsonLen(jsonStr: string): number {
  if (!jsonStr) return 0
  try {
    const arr = JSON.parse(jsonStr)
    return Array.isArray(arr) ? arr.length : 0
  } catch (_e) {
    return 0
  }
}

// ==================== 手动创建血缘 ====================
async function handleCreateLineage() {
  if (!manualForm.value.sourceId || !manualForm.value.targetId) { ElMessage.warning('请选择源节点与目标节点'); return }
  const src = sources.value.find((s: any) => s.id === manualForm.value.sourceId)
  const tgt = sources.value.find((s: any) => s.id === manualForm.value.targetId)
  if (!src || !tgt) { ElMessage.warning('源/目标节点不存在'); return }
  try {
    await lineageApi.createLineage({
      sourceType: src.sourceTypeId || 'TABLE',
      sourceId: src.id,
      targetType: tgt.sourceTypeId || 'TABLE',
      targetId: tgt.id,
      relationType: manualForm.value.relationType,
      transformRule: manualForm.value.transformRule || 'MANUAL',
      dependencyLevel: 1,
      sourceSystem: 'MANUAL',
      collectionId: 0,
      discoveredBy: 'MANUAL'
    })
    ElMessage.success('血缘关系创建成功')
    manualForm.value = { sourceId: undefined, targetId: undefined, relationType: 'FLOW', transformRule: '' }
  } catch (e: any) {
    ElMessage.error('创建失败: ' + (e?.response?.data?.message || e.message || '未知错误'))
  }
}

/** 创建字段级引用血缘（业务表外键 → 主数据表） */
async function handleCreateFk() {
  if (!fkForm.value.sourceId || !fkForm.value.sourceColumn.trim()
      || !fkForm.value.targetId || !fkForm.value.targetColumn.trim()) {
    ElMessage.warning('请填写引用表/字段与被引用表/字段')
    return
  }
  try {
    await lineageApi.createColumnLineage({
      sourceId: fkForm.value.sourceId,
      sourceColumn: fkForm.value.sourceColumn.trim(),
      targetId: fkForm.value.targetId,
      targetColumn: fkForm.value.targetColumn.trim(),
      mappingType: 'JOIN',
      joinCondition: fkForm.value.joinCondition || undefined,
      relationType: 'REFERENCE'
    })
    ElMessage.success('引用血缘创建成功')
    fkForm.value = { sourceId: undefined, sourceColumn: '', targetId: undefined, targetColumn: '', joinCondition: '' }
  } catch (e: any) {
    ElMessage.error('创建失败: ' + (e?.response?.data?.message || e.message || '未知错误'))
  }
}

// ==================== AI 血缘识别 ====================
async function loadAiDataSources() {
  try {
    const res: any = await catalogApi.getDataSources()
    aiDataSources.value = (res.data || []).filter((d: any) => d.status === 1)
  } catch (_e) { aiDataSources.value = [] }
}

async function handleAiRecognize() {
  if (!aiForm.value.sql?.trim()) { ElMessage.warning('请填写 SQL'); return }
  aiRecognizing.value = true
  try {
    const res: any = await lineageApi.aiRecognize({
      sql: aiForm.value.sql.trim(),
      dataSourceId: aiForm.value.dataSourceId
    })
    const d = res.data || {}
    aiTableLineages.value = (d.tableLineages || []).map((x: any) => ({ ...x, checked: true }))
    aiColumnLineages.value = (d.columnLineages || []).map((x: any) => ({ ...x, checked: true }))
    aiPreviewTab.value = 'table'
    ElMessage.success(`识别完成：表级血缘 ${aiTableLineages.value.length} 条，字段级血缘 ${aiColumnLineages.value.length} 条`)
  } catch (e: any) {
    ElMessage.error(e.message || '识别失败')
  } finally {
    aiRecognizing.value = false
  }
}

/** 数据源维度：AI 识别表间关联（无需 SQL，依据 _DR 指针 + 业务语义） */
async function handleAiRecognizeTables() {
  if (!aiForm.value.dataSourceId) { ElMessage.warning('请选择数据源'); return }
  aiRecognizing.value = true
  try {
    const res: any = await lineageApi.aiRecognizeTables({ dataSourceId: aiForm.value.dataSourceId })
    const d = res.data || {}
    aiTableLineages.value = (d.tableLineages || []).map((x: any) => ({ ...x, checked: true }))
    aiColumnLineages.value = (d.columnLineages || []).map((x: any) => ({ ...x, checked: true }))
    aiPreviewTab.value = 'table'
    ElMessage.success(`识别完成：表级 ${aiTableLineages.value.length} 条，字段级 ${aiColumnLineages.value.length} 条`)
  } catch (e: any) {
    ElMessage.error(e.message || '识别失败')
  } finally {
    aiRecognizing.value = false
  }
}

async function handleAiSave() {
  const tables = aiTableLineages.value.filter(t => t.checked)
  const columns = aiColumnLineages.value.filter(c => c.checked)
  if (!tables.length && !columns.length) { ElMessage.warning('请至少勾选一条要落库的血缘'); return }
  aiSaving.value = true
  try {
    const res: any = await lineageApi.saveAiLineage({
      tableLineages: tables,
      columnLineages: columns,
      sourceSystem: 'AI'
    })
    const d = res.data || {}
    ElMessage.success(`落库完成：表级 ${d.tableCreated || 0} 条，字段级 ${d.columnCreated || 0} 条，跳过 ${d.skipped || 0} 条`)
    await loadSources()
  } catch (e: any) {
    ElMessage.error(e.message || '落库失败')
  } finally {
    aiSaving.value = false
  }
}

// ==================== 分层血缘钻取 ====================
async function loadNodeHierarchy(sourceId: number) {
  if (!sourceId) return
  const res: any = await lineageApi.getHierarchy(sourceId)
  hierarchyList.value = res.data || []
}

async function showColumnLineage(hierarchyId: number) {
  if (!hierarchyId) { ElMessage.warning('缺少分层边ID'); return }
  try {
    const res: any = await lineageApi.getColumnLineage(hierarchyId)
    columnLineageList.value = res.data || []
    columnLineageVisible.value = true
  } catch (e: any) {
    ElMessage.error('字段血缘加载失败: ' + (e?.response?.data?.message || e.message || '未知错误'))
  }
}

async function showCalcDependency(columnLineageId: number) {
  if (!columnLineageId) { ElMessage.warning('缺少字段血缘ID'); return }
  try {
    const res: any = await lineageApi.getCalcDependency(columnLineageId)
    calcDependencyList.value = res.data || []
    calcDependencyVisible.value = true
  } catch (e: any) {
    ElMessage.error('计算依赖加载失败: ' + (e?.response?.data?.message || e.message || '未知错误'))
  }
}

// ==================== 展示辅助 ====================
function nodeTypeLabel(t: string) { return nodeTypes[t] || t || '未知' }
function nodeTypeTag(t: string) {
  switch (t) {
    case 'REPORT': return 'warning'
    case 'VIEW': return 'success'
    case 'PROCEDURE': return 'danger'
    case 'DICT': return 'info'
    default: return 'primary'
  }
}
function layerLabel(l: number) { return layerMap[l] || '层' + l }
function mappingTypeLabel(t: string) { return mappingMap[t] || t }
function calcTypeLabel(t: string) { return calcMap[t] || t }

// ==================== 血缘图谱（公共组件 LineageGraph 承载，逻辑见 components/lineage/LineageGraph.vue） ====================
</script>

<style scoped>
.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.empty-text { color: #909399; font-size: 13px; }
</style>
