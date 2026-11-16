# Catalog 模块化阶段 2 清单

## 1. 目标与边界

阶段 2 只做 **dam-catalog 内部包级模块化**：

- 不拆部署；
- 不迁数据库；
- 不改对外 API；
- 不改变现有页面和路由；
- 先消除模块之间的直接 Mapper 依赖和重复表所有权。

目标包结构：

```text
com.dam.catalog
├── asset
├── metadata
├── datasource
├── metric
├── report
├── knowledge
├── system
├── ai
└── shared
```

---

## 2. 当前模块与主要类

### 2.1 asset 资产目录

Controller：

```text
CatalogController（当前同时承载资产、分类、标签、数据源、扫描等，后续拆分）
```

Service：

```text
CatalogService
VersionService
ImportService
```

Entity/Mapper：

```text
DamAsset
DamCategory
AssetCategoryRel
DamTag
DamAssetTag
DamFavorite
DamRating
AssetUsageLog
AssetStatistics
```

归属：

```text
dam_asset.asset_info
dam_asset.asset_category
dam_asset.asset_category_rel
dam_asset.asset_tag
dam_asset.asset_tag_rel
dam_asset.asset_favorite
dam_asset.asset_rating
dam_asset.asset_usage_log
dam_asset.asset_statistics
```

### 2.2 metadata 技术元数据

Controller：

```text
MetadataController
```

Service：

```text
MetadataService
TechnicalMetadataService
DiscoveryService
```

Entity/Mapper：

```text
MetadataTable
MetadataColumn
MetadataSchema
MetadataManagement
DamMetadataExt（与 MetadataManagement 同表）
DamMetadataCollection
DamScanJob（与 DamMetadataCollection 同表）
DamMetadataVersion
DamSourceSystem
```

归属：

```text
dam_metadata.metadata_table
dam_metadata.metadata_column
dam_metadata.metadata_schema
dam_metadata.metadata_management
dam_metadata.metadata_collection
dam_metadata.metadata_version
dam_metadata.source_system
```

### 2.3 datasource 数据源

Entity/Mapper：

```text
DamDataSource
DamDataSourceType
```

当前表：

```text
dam_metadata.metadata_datasource
dam_metadata.dam_data_source_type
```

阶段 2 可先保留在 metadata 包，阶段 4 再归 dam-integration。

### 2.4 metric 指标

Controller：

```text
MetricController
```

Service：

```text
MetricService
```

Entity/Mapper：

```text
MetricDefinition
MetricValue
```

当前表：

```text
dam_asset.metric_definition
dam_asset.metric_value
```

### 2.5 report 报表

Controller：

```text
ReportController
ReportCatalogController
```

Service：

```text
ReportService
ReportCatalogService
```

Entity/Mapper：

```text
ReportCatalog
```

当前表：

```text
dam_asset.report_catalog
```

### 2.6 knowledge 知识

Controller：

```text
KnowledgeController
WeKnoraController
```

Service：

```text
KnowledgeService
WeKnoraKnowledgeService
WeKnoraClient
```

Entity/Mapper：

```text
KnowledgeCategory
KnowledgeEntry
KnowledgeSyncConfig
```

当前表：

```text
dam_asset.knowledge_category
dam_asset.knowledge_entry
dam_asset.knowledge_sync_config
```

### 2.7 system 系统配置

Controller/Service：

```text
MenuController
MenuService
DictService
ConfigService
```

Entity/Mapper：

```text
SysMenu
SysDictType
SysDictData
SysRoleSubjectDomain
DamConfig
```

当前表：

```text
dam_sys.sys_menu
dam_sys.sys_dict_type
dam_sys.sys_dict_data
dam_sys.sys_role_subject_domain
dam_sys.sys_config
```

### 2.8 ai 领域 AI 工具

```text
MetadataAiService
AssetClassifyAiService
LineageAiService
AiWorkbenchTaskClient
InternalAiToolController
```

这些是 catalog 提供给 AI 工作台的领域工具，不应放入 asset/metadata 业务包。

---

## 3. 重复表和重复实体

| 表 | 重复实体 | 当前问题 | 建议 |
|---|---|---|---|
| metadata_quality_rule | catalog.DamQualityRule / quality.DamQualityRule | 两个服务映射同一张表 | quality 为唯一写方，catalog 删除实体/Mapper |
| metadata_quality_check_result | catalog.DamQualityCheck / quality.DamQualityCheck | 同上 | quality 唯一写方 |
| metadata_lineage | catalog.DamLineage / lineage.DamLineage | 同上 | lineage 唯一写方 |
| source_system | catalog.DamSourceSystem / lineage.SourceSystem | 同上 | metadata 唯一写方 |
| asset_statistics | catalog.AssetStatistics / operations.DamUsageStats | 两个服务共用 | asset 唯一写方，operations 只读 API |
| metadata_collection | DamMetadataCollection / DamScanJob | 两个实体同表 | 合并为一个实体 |
| metadata_management | DamMetadataExt / MetadataManagement | 两个实体同表 | 合并为一个实体 |
| dam_asset.metric_* | MetricDefinition/MetricValue | 在资产 schema | 阶段 3 迁 dam_metric |
| dam_asset.report_* | ReportCatalog | 在资产 schema | 阶段 3 迁 dam_report |
| dam_asset.knowledge_* | Knowledge* | 在资产 schema | 阶段 3 迁 dam_knowledge |

阶段 2 先删除 catalog 中 quality/lineage 的重复实体和 Mapper，改为调用 quality/lineage 的接口或暂时只读。

---

## 4. 目标包结构

```text
com.dam.catalog
├── asset
│   ├── controller
│   ├── service
│   ├── entity
│   └── repository
├── metadata
│   ├── controller
│   ├── service
│   ├── entity
│   └── repository
├── datasource
│   ├── service
│   ├── entity
│   └── repository
├── metric
│   ├── controller
│   ├── service
│   ├── entity
│   └── repository
├── report
│   ├── controller
│   ├── service
│   ├── entity
│   └── repository
├── knowledge
│   ├── controller
│   ├── service
│   ├── entity
│   └── repository
├── system
│   ├── controller
│   ├── service
│   ├── entity
│   └── repository
├── ai
│   ├── service
│   └── controller
└── shared
```

### 包依赖方向

```text
asset → metadata
metadata → datasource
metric → shared
report → metric / knowledge 接口
knowledge → metric 接口
system → shared
ai → 各领域 Service 接口
```

禁止：

```text
asset Mapper → 直接调用 report Mapper
report → 直接调用 knowledge Mapper
metric → 直接调用 report Mapper
```

跨模块调用统一走 Service 接口。

---

## 5. 移动顺序

### 第一步：asset 基础数据

移动：

```text
DamAsset
DamCategory
AssetCategoryRel
DamTag
DamAssetTag
DamFavorite
DamRating
AssetUsageLog
AssetStatistics
```

对应 Mapper 同步移动。

先不改 `CatalogService`，只改 import。

验收：

- 编译通过；
- 资产接口、分类接口、标签接口不回退。

### 第二步：metadata 和 datasource

移动：

```text
MetadataTable
MetadataColumn
MetadataSchema
MetadataManagement
DamMetadataVersion
DamMetadataCollection
DamSourceSystem
DamDataSource
DamDataSourceType
```

合并：

```text
DamMetadataExt → MetadataManagement
DamScanJob → DamMetadataCollection
```

验收：

- 元数据同步、扫描、表详情正常；
- 表注释和字段注释写入正常。

### 第三步：metric

移动：

```text
MetricDefinition
MetricValue
MetricService
MetricController
```

依赖处理：

```text
MetricService 调用 ReportService 生成 SQL
```

阶段 2 通过 `ReportGenerationPort` 接口隔离，不要让 metric 直接依赖 report 包。

### 第四步：report

移动：

```text
ReportCatalog
ReportService
ReportCatalogService
ReportController
ReportCatalogController
```

依赖处理：

```text
ReportService 依赖知识检索
```

抽出：

```text
KnowledgeLookupPort
```

report 只依赖接口，不直接依赖 knowledge Mapper。

### 第五步：knowledge

移动：

```text
KnowledgeCategory
KnowledgeEntry
KnowledgeSyncConfig
KnowledgeService
WeKnoraKnowledgeService
WeKnoraClient
KnowledgeController
WeKnoraController
```

依赖处理：

```text
KnowledgeService 依赖 metricMapper
```

改为 `MetricLookupPort`，只读取指标只读信息。

### 第六步：system 和 ai

移动：

```text
SysMenu / SysDict / SysConfig
MenuService / DictService / ConfigService
MetadataAiService / AssetClassifyAiService / LineageAiService
AiWorkbenchTaskClient / InternalAiToolController
```

system 归 `system` 包；领域 AI 归 `ai` 包。

### 第七步：拆 CatalogController

最后再拆：

```text
CatalogController
  → AssetController
  → AssetCategoryController
  → AssetTagController
  → DataSourceController
```

阶段 2 可以先不拆 Controller，避免 API 路径变化。

---

## 6. 接口边界

建议新增内部接口（阶段 2 先 Java 接口，阶段 4 再 HTTP）：

```text
MetricLookupPort
  findById
  findByCode
  listByDomain

KnowledgeLookupPort
  search
  listByMetric

ReportGenerationPort
  generateSql
  generateTitle

MetadataLookupPort
  findTable
  findColumn
  listTables

AssetLookupPort
  findAsset
  listAssets
```

所有跨模块依赖只允许调用 Port，不允许调用对方 Mapper。

---

## 7. 风险

| 风险 | 处理 |
|---|---|
| import 大量变更导致编译失败 | 每步只移动一个模块 |
| Spring Bean 扫描不到 | 保持 `com.dam.catalog` 根包 |
| Mapper XML/扫描 | 检查 `@MapperScan` 和 XML namespace |
| 事务跨模块 | 先保持同事务，阶段 4 再拆 |
| 旧 API 路径变化 | 阶段 2 不改 Controller 路径 |
| 重复实体删除导致旧代码失效 | 先改引用，再删类 |
| ReportService 与 Knowledge 耦合 | 抽 KnowledgeLookupPort |
| MetricService 与 Report 耦合 | 抽 ReportGenerationPort |
| 构建产物 target 混入提交 | 只提交 src/pom，不提交 target |

---

## 8. 验收标准

- `mvn -pl dam-catalog -am compile` 通过；
- 原资产、元数据、指标、报表、知识 API 路径不变；
- 没有新增跨模块 Mapper 注入；
- quality/lineage/source_system/asset_statistics 重复实体已处理；
- 每个模块有独立包和明确依赖方向；
- 阶段 2 不迁表、不拆服务。

## 9. 结论

先动 **asset、metadata、metric、report、knowledge** 五个包。

暂缓：

```text
CatalogController 拆分
表迁移
服务拆分
ReportService 深度重构
```

阶段 2 的目标不是“拆干净”，而是先把代码边界立起来，为阶段 3 的表迁移和阶段 4 的服务拆分铺路。

---

# 10. 实施进展

## 已完成

### 2026-11-16

已实际提取以下 catalog 包：

```text
com.dam.catalog.metric
com.dam.catalog.knowledge
com.dam.catalog.report
```

同步迁移：

- Controller；
- Service；
- Entity；
- Repository/Mapper。

已更新：

- 跨模块 import；
- `@MapperScan` 增加 metric/knowledge/report repository 包；
- 全量后端构建通过。

## 阶段 3 第一小步

已完成 metric/report/knowledge 的 schema 边界迁移脚本：

```text
db/v21_metric_report_knowledge_schema.sql
```

目标：

```text
dam_asset.metric_definition       → dam_metric.metric_definition
dam_asset.metric_value            → dam_metric.metric_value
dam_asset.report_catalog          → dam_report.report_catalog
dam_asset.knowledge_category      → dam_knowledge.knowledge_category
dam_asset.knowledge_entry         → dam_knowledge.knowledge_entry
dam_asset.knowledge_sync_config   → dam_knowledge.knowledge_sync_config
```

实体 schema 已同步改为：

```text
dam_metric
dam_report
dam_knowledge
```

下一步：

1. 在测试库执行 v21；
2. 验证指标、报表、知识接口；
3. 再拆 asset/metadata/system 包；
4. 再处理 quality/lineage/lifecycle 的表所有权。

## 2026-11-16 追加进展

已完成：

```text
metric 包
knowledge 包
report 包
system 包
```

`dam-security` 对 `DamConfig` 的跨模块 import 已同步更新。

已完成 v21 的第一批 schema 迁移：

```text
dam_metric
dam_report
dam_knowledge
```

实体 schema 已同步。

待完成：

```text
asset 包
metadata 包
ai 包
quality/lineage/lifecycle 的表所有权
```

注意：部署新代码前必须先执行 v21，否则实体指向的新 schema 不存在。

## 2026-11-16 第二批进展

已完成包提取：

```text
asset
metadata
system
ai
```

并修正跨模块引用：

```text
dam-security
dam-operations
dam-standard
dam-lifecycle
```

这些模块不再引用旧的 `com.dam.catalog.entity.DamAsset` / `repository.DamAssetMapper`。

当前 catalog 包结构：

```text
asset
metadata
metric
knowledge
report
system
ai
shared（待建）
```

剩余：

```text
CatalogController / CatalogService 仍承载跨模块入口
quality/lineage/lifecycle 共享表尚未迁移
服务级拆分尚未开始
```

## 2026-11-16 第三批进展

已完成 quality/lineage/lifecycle 的表归属迁移脚本：

```text
db/v22_quality_lineage_lifecycle_schema.sql
```

迁移：

```text
dam_metadata.metadata_quality_*      → dam_quality
dam_metadata.metadata_lineage*       → dam_lineage
dam_metadata.metadata_column_*       → dam_lineage
dam_metadata.metadata_report_sql_*   → dam_lineage
dam_metadata.metadata_lifecycle_*    → dam_lifecycle
dam_metadata.metadata_destruction_*  → dam_lifecycle
```

对应实体 schema 已更新：

```text
dam-quality
dam-lineage
dam-lifecycle
catalog 中保留的只读质量/血缘实体
```

注意：

```text
v21、v22 必须在部署新代码前执行。
```

下一步：

```text
1. 在测试库执行 v21、v22
2. 验证质量、血缘、生命周期接口
3. 删除 catalog 中重复的 quality/lineage 实体和 Mapper
4. 拆分 CatalogController / CatalogService
5. 抽取独立服务
```

## 2026-11-16 第四批进展

已删除 catalog 中重复的质量/血缘实体和 Mapper：

```text
DamQualityRule
DamQualityCheck
DamQualityRuleMapper
DamQualityCheckMapper
DamLineage
DamLineageMapper
```

catalog 元数据聚合改为调用服务 API：

```text
GET /api/quality/assets/{tableId}/summary
GET /api/lineage/{assetId}
```

新增客户端：

```text
com.dam.catalog.shared.client.QualityReadClient
com.dam.catalog.shared.client.LineageReadClient
```

质量、血缘、周期表由各自服务独占写权限。

## 2026-11-16 第五批进展

已完成 asset/metadata 的 Service/Controller 迁移：

```text
CatalogService       → com.dam.catalog.asset.service
ImportService        → com.dam.catalog.asset.service
CatalogController    → com.dam.catalog.asset.controller

MetadataService      → com.dam.catalog.metadata.service
DiscoveryService     → com.dam.catalog.metadata.service
TechnicalMetadataService → com.dam.catalog.metadata.service
DataSourceTypeService    → com.dam.catalog.metadata.service
VersionService       → com.dam.catalog.metadata.service
MetadataController   → com.dam.catalog.metadata.controller
```

catalog 根目录只剩：

```text
CryptoService
ModelService
ModelController
DamField / model / sysUserRef
```

全量后端构建通过。下一步可以开始按服务拆分 metric/report/knowledge。
