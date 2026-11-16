# dam-metric 独立服务拆分验收

## 1. 目标

将指标定义、指标值和指标 SQL 生成从 dam-catalog 拆出，指标表由 dam-metric 独占。

## 2. 新服务

```text
模块：dam-metric
端口：8094
包名：com.dam.metric
schema：dam_metric
```

表沿用 v21 迁移后的：

```text
dam_metric.metric_definition
dam_metric.metric_value
```

本服务不新增表，不需要额外 SQL。

## 3. API

```text
GET    /api/metrics/status
GET    /api/metrics
GET    /api/metrics/{id}
POST   /api/metrics
PUT    /api/metrics/{id}
DELETE /api/metrics/{id}
POST   /api/metrics/{id}/ai-sql
GET    /api/metrics/{id}/values
POST   /api/metrics/{id}/values
DELETE /api/metrics/{id}/values/{valueId}
```

catalog 内部只读接口：

```text
GET /api/metrics/internal/active?limit=500
GET /api/metrics/internal/by-data-source/{dataSourceId}
```

## 4. catalog 改造

已删除：

```text
metric/controller/MetricController
metric/service/MetricService
metric/entity/MetricValue
metric/repository/MetricDefinitionMapper
metric/repository/MetricValueMapper
```

保留：

```text
com.dam.catalog.metric.entity.MetricDefinition  # 跨服务 DTO
MetricLookupPort
MetricLookupHttpAdapter
```

Knowledge、WeKnora、Report 通过：

```text
MetricLookupPort
```

读取指标，不再访问 dam_metric 表。

## 5. AI SQL 调用链

```text
前端 /api/metrics/{id}/ai-sql
  → dam-metric MetricService
  → CatalogMetricAiClient
  → POST catalog /api/catalog/internal/ai/metrics/sql
  → catalog ReportService.generateMetricSql
  → 返回 SQL
  → dam-metric 写回 metric_definition.sql_expression
```

## 6. 网关与前端

网关：

```text
/api/metrics/** → 8094
```

前端：

```text
metricApi 路径由 /catalog/metrics 改为 /metrics
axios baseURL=/api，实际请求 /api/metrics
```

## 7. 配置

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SA_TOKEN_JWT_SECRET_KEY
DAM_CATALOG_URL=http://catalog:8092
DAM_METRIC_URL=http://metric:8094   # catalog 侧
```

## 8. 验证

```text
后端全量 Maven package：通过
dam-catalog 编译：通过
dam-metric 编译：通过
前端 vue-tsc：通过
前端 vite build：通过
```

## 9. 风险

1. 未连接真实数据库启动 dam-metric；部署前必须执行 v21。
2. catalog 依赖 dam-metric 内部接口，服务不可用时 Knowledge/Report 的指标读取会失败或降级；Report 已捕获异常并记录警告。
3. dam-metric 的 AI SQL 依赖 catalog 内部接口，两个服务必须同时可访问。
4. 前端已切到 /api/metrics，网关未部署时不会回退到旧 catalog 指标接口。

## 10. 回滚

```text
1. 前端 metricApi 改回 /catalog/metrics
2. 网关移除 /api/metrics/**
3. 恢复 catalog metric Controller/Service/Mapper
4. 保留 dam_metric schema，不执行反向 DDL
```
