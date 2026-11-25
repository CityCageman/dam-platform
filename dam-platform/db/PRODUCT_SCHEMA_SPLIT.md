# 产品级 Schema 拆分（v44）

## 目标

每个产品一个 schema，不再把产品表混放在 `dam_asset` / `dam_sys` 等旧 schema 中。

| 产品 | 目标 schema | 主要表 |
|---|---|---|
| DAM 数据资产与主数据 | `dam_dam` | asset_*、metadata_*、source_system、mdm_* |
| DPM 数据中台管理 | `dam_dpm` | sys_*、ticket_*、task_*、scheduler_* |
| DAP 数据接入 | `dam_dap` | access_*、integration_* |
| DGP 数据治理 | `dam_dgp` | standard_*、quality_*、security_*、lifecycle_*、lineage_* |
| DPP 数据处理 | `dam_dpp` | model_*、dev_* |
| DSP 数据服务 | `dam_dsp` | data_api、api_* |
| DAN 数据分析 | `dam_dan` | metric_*、report_catalog |
| DAPP 数据应用 | `dam_dapp` | app_*（待建表） |
| AI 能力 | `dam_ai` | ai_*、knowledge_* |
| 报表维度库 | `dam_dimension` | report_dimension、report_dimension_item |

## 执行顺序

```text
1. 备份数据库
2. 执行 db/v44_product_schema_split.sql
3. 重启所有后端服务
4. 验证各产品只使用自己的 schema
5. 验证报表维度查询使用 dam_dimension
```

`v44` 只执行 `ALTER TABLE ... SET SCHEMA`，不删除旧 schema，便于回滚和兼容视图。
旧 schema 保留为迁移期兼容区，不再作为代码归属。

## 报表维度独立

```text
dam_asset.report_dimension       -> dam_dimension.report_dimension
dam_asset.report_dimension_item  -> dam_dimension.report_dimension_item
```

报表维度 Mapper 已改为：

```sql
dam_dimension.report_dimension
dam_dimension.report_dimension_item
```

## 注意

当前是“一个 PostgreSQL 数据库 + 每产品一个 schema”。
如果后续要求每个产品一个独立数据库实例，还需要为每个服务配置独立 JDBC URL 和独立迁移流水线。
