# dam-knowledge 独立服务拆分验收

## 1. 服务

```text
模块：dam-knowledge
端口：8098
包名：com.dam.knowledge
schema：dam_knowledge
```

表沿用 v21 迁移后的：

```text
dam_knowledge.knowledge_category
dam_knowledge.knowledge_entry
dam_knowledge.knowledge_sync_config
```

不新增表。

## 2. API

```text
/api/knowledge/categories
/api/knowledge/stats
/api/knowledge/entries
/api/knowledge/sources
/api/knowledge/sync
/api/knowledge/vector/**
/api/knowledge/external/**
```

原 `/api/catalog/knowledge/**` 不再使用。

## 3. catalog 改造

已删除整个 `com.dam.catalog.knowledge` 包。

新增：

```text
KnowledgeSearchPort
KnowledgeSearchHttpAdapter
```

catalog 报表通过：

```text
POST /api/knowledge/external/search
```

读取知识，服务不可用时保留原有降级逻辑。

## 4. 知识服务读取指标

`MetricReadClient` 调用：

```text
GET /api/metrics/internal/active?limit=500
GET /api/metrics/{id}
```

指标表仍由 dam-metric 独占。

## 5. 网关

```text
/api/metrics/**    → 8094
/api/knowledge/**  → 8098
```

## 6. 配置

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SA_TOKEN_JWT_SECRET_KEY
DAM_METRIC_URL=http://metric:8094
DAM_KNOWLEDGE_URL=http://knowledge:8098   # catalog 侧
DAM_WEKNORA_BASE_URL
DAM_WEKNORA_API_KEY
```

## 7. 验证

```text
后端全量 Maven package：通过
dam-knowledge 编译：通过
dam-catalog 编译：通过
前端 vue-tsc：通过
前端 vite build：通过
```

## 8. 风险

1. 未连接真实数据库启动 dam-knowledge。
2. 知识服务依赖 dam-metric 和 WeKnora；外部服务不可用时对应能力降级或报错。
3. 知识检索移出 catalog 后，报表生成依赖 dam-knowledge 可用；已有 try/catch 降级。
4. 旧前端缓存请求 `/api/catalog/knowledge/**` 会 404。
