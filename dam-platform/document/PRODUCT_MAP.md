# 产品、模块、服务、路由、数据归属映射

## 一、产品层

| 产品编码 | 产品 | 目标用户 | 核心价值 |
|---|---|---|---|
| DAM | 数据资产与主数据 | 数据管家、科主任、业务用户 | 资产可见、可信、可复用 |
| DPM | 数据中台管理 | 数据运维、平台管理员 | 平台稳定运行 |
| DAP | 数据接入 | 数据运维、数据开发 | 打通数据源 |
| DGP | 数据治理 | 数据管家、安全管理员 | 标准、质量、安全、合规 |
| DPP | 数据处理 | 数据开发 | 清洗、转换、建模 |
| DSP | 数据服务 | 数据开发、业务用户 | API 发布与使用 |
| DAN | 数据分析 | 院领导、科主任、业务用户 | 指标、报表、分析 |
| DAPP | 数据应用 | 业务用户、数据开发 | 场景应用与数据产品 |

AI 是横切能力，不单独占产品位：

```text
AI 工作台
模型网关
知识中心
```

## 二、模块映射

### DAM 数据资产与主数据

| 模块 | 路由 | 服务/未来服务 | Schema |
|---|---|---|---|
| 资产目录 | `/catalog` | dam-catalog | dam_asset |
| 资产详情 / 资产 360 | `/catalog-detail/:id` | dam-catalog | dam_asset |
| 元数据管理 | `/metadata` | dam-catalog | dam_metadata |
| 分类与标签 | 资产详情内 | dam-catalog | dam_asset |
| 质量评分 | 资产详情内 | dam-quality | dam_quality |
| 血缘 | `/lineage` | dam-lineage | dam_lineage |
| 主数据 MDM | `/mdm` | 未来 dam-mdm | dam_mdm |

### DPM 数据中台管理

| 模块 | 路由 | 服务 | Schema |
|---|---|---|---|
| 产品中心 | `/portal` | dam-portal | dam_sys |
| 超级驾驶舱 | `/cockpit/super` | dam-portal | dam_sys |
| 平台驾驶舱 | `/cockpit/ops` | dam-portal | dam_sys |
| 调度中心 | `/scheduler` | dam-scheduler | dam_scheduler |
| 任务中心 | `/task-center` | dam-task-center | dam_task |
| 工单中心 | `/operations` | dam-operations | dam_ticket |
| 系统配置 | `/system-config` | dam-catalog 系统模块 → 未来 dam-sys | dam_sys |
| 可观测 | `/observability` | 未来 dam-observability | dam_observability |

### DAP 数据接入

| 模块 | 路由 | 服务 | Schema |
|---|---|---|---|
| 数据源管理 | `/data-sources` | dam-integration/access | dam_access |
| 数据集成 | `/integration` | dam-integration | dam_integration |

### DGP 数据治理

| 模块 | 路由 | 服务 | Schema |
|---|---|---|---|
| 数据标准 | `/standard` | dam-standard | dam_standard |
| 数据质量 | `/quality` | dam-quality | dam_quality |
| 安全管控 | `/security` | dam-security | dam_security |
| 生命周期 | `/lifecycle` | dam-lifecycle | dam_lifecycle |
| 数据血缘 | `/lineage` | dam-lineage | dam_lineage |

### DPP 数据处理

| 模块 | 路由 | 服务 | Schema |
|---|---|---|---|
| 数据开发 | `/develop` | dam-develop | dam_dev |
| 模型管理 | `/models` | dam-catalog 模型模块 → 未来 dam-model | dam_model |
| 存储计算 | 未来 | 未来 dam-storage | dam_storage |

### DSP 数据服务

| 模块 | 路由 | 服务 | Schema |
|---|---|---|---|
| 数据服务 | `/data-service` | dam-data-service | dam_data_service |
| OpenAPI | `/open-api/v1/**` | dam-data-service | dam_data_service |

### DAN 数据分析

| 模块 | 路由 | 服务 | Schema |
|---|---|---|---|
| 指标管理 | `/metrics` | dam-metric | dam_metric |
| 报表体系 | `/reports` | dam-catalog 报表 → 未来 dam-report | dam_report |
| 知识中心 | `/knowledge` | dam-knowledge | dam_knowledge |

### DAPP 数据应用

| 模块 | 路由 | 服务 | Schema |
|---|---|---|---|
| 应用中心 | 未来 `/apps` | 未来 dam-app | dam_app |
| 数据市场 | 未来 `/market` | 未来 dam-market | dam_market |

### AI 横切能力

| 模块 | 路由 | 服务 | Schema |
|---|---|---|---|
| AI 工作台 | `/agent` | dam-ai-workbench | dam_ai |
| AI 任务中心 | `/agent/tasks` | dam-ai-workbench | dam_ai |
| 模型网关 | `/api/model-gateway/**` | dam-model-gateway | 无 |
| 知识库 | `/knowledge` | dam-knowledge | dam_knowledge |

## 三、平台基础服务

```text
dam-gateway      统一入口、JWT 校验、CORS
dam-auth         登录、用户、角色、权限
dam-common       公共组件
dam-portal       产品中心、驾驶舱、产品注册读取
```

## 四、产品注册数据

建议放在 `dam_sys`：

```text
product_definition
product_module
role_product
role_module
role_cockpit
cockpit_widget
```

## 五、边界规则

```text
产品不直接访问其他产品数据库
跨产品只通过 API
产品首页只展示授权模块
模块页面必须带 productCode + moduleCode
AI 通过内部 API 被其他产品调用
```
