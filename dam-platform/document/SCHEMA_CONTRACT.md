# 权威 Schema 契约 V1

## 一、原则

```text
一个业务域一个 schema
一个 schema 一个 owner 服务
一个表只允许一个写服务
跨服务只读通过 API 或明确只读视图
新功能不得引用 legacy 表
```

## 二、权威 schema

| Schema | Owner 服务 | 说明 |
|---|---|---|
| dam_sys | dam-auth / dam-portal | 用户、角色、菜单、字典、产品注册 |
| dam_asset | dam-catalog | 资产、分类、标签、收藏、评分、统计 |
| dam_metadata | dam-catalog | 数据源只读视图、Schema、表、字段、采集、血缘技术元数据 |
| dam_access | dam-integration | 数据源可写归属 |
| dam_mdm | 未来 dam-mdm | 主数据 |
| dam_standard | dam-standard | 标准、码表、映射、偏差 |
| dam_quality | dam-quality | 质量规则、检测结果 |
| dam_security | dam-security | 敏感规则、脱敏、权限、审计、申请 |
| dam_lifecycle | dam-lifecycle | 生命周期策略、执行、销毁 |
| dam_lineage | dam-lineage | 血缘、字段级血缘、解析 |
| dam_metric | dam-metric | 指标定义、指标值 |
| dam_report | dam-catalog/未来 dam-report | 报表目录 |
| dam_model | dam-catalog/未来 dam-model | 数据模型 |
| dam_ticket | dam-operations | 工单、评论、流转 |
| dam_task | dam-task-center | 任务定义、实例、日志 |
| dam_scheduler | dam-scheduler | 调度任务、运行、DAG |
| dam_dev | dam-develop | 开发任务、版本 |
| dam_integration | dam-integration | 集成任务、运行 |
| dam_data_service | dam-data-service | API、订阅、Key、调用日志 |
| dam_ai | dam-ai-workbench | AI 任务、明细 |
| dam_knowledge | dam-knowledge | 知识分类、条目、同步配置 |

## 三、必须修复的归属

| 旧位置 | 新位置 | 处理 |
|---|---|---|
| dam_metadata.metadata_quality_* | dam_quality.* | 数据迁移 + 兼容视图 |
| dam_asset.ai_task* | dam_ai.* | 数据迁移 + 兼容视图 |
| dam_asset.report_catalog | dam_report.report_catalog | 迁移 + 视图 |
| dam_system.* | dam_sys.* | 旧脚本归档 |
| dam_security_management.* | dam_security.* | 旧脚本归档 |
| dam_ticket_center.* | dam_ticket.* | 旧脚本归档 |
| dam_metadata.metadata_datasource | dam_access.metadata_datasource | 已迁，DAM 只读视图 |

## 四、产品注册表

```text
dam_sys.product_definition
dam_sys.product_module
dam_sys.role_product
dam_sys.role_module
dam_sys.role_cockpit
dam_sys.cockpit_widget
```

## 五、MDM 新表

```text
dam_mdm.md_domain
dam_mdm.md_entity
dam_mdm.md_attribute
dam_mdm.md_record
dam_mdm.md_golden_record
dam_mdm.md_version
dam_mdm.md_distribution
```

## 六、迁移规则

```text
1. 先建新表/新列
2. 双写或视图过渡
3. 回填数据
4. 切换读路径
5. 验证行数与关键字段
6. 下线旧入口
7. 最后归档旧表
```

禁止直接：

```text
DROP 表
RENAME 生产表后不建兼容视图
一次性迁移所有 schema
```

## 七、契约测试

每个服务至少校验：

```text
@TableName 的 schema/table 存在
实体字段对应列存在
关键唯一索引存在
迁移脚本可重复执行
```
