# 产品化架构与独立部署手册

> 版本：1.0  
> 范围：DAM、DPM、DAP、DGP、DPP、DSP、DAN、DAPP、AI 能力包、platform-base  
> 依据：当前仓库实际代码、`products/*/product.yaml`、网关配置、各服务 `application.yml`

---

## 1. 总体结论

当前系统采用：

```text
共享平台基础库 platform-common
        +
platform-base（网关 + 认证）
        +
8 个独立产品包
        +
1 个 AI 能力包
```

产品之间：

```text
Maven 直接依赖：没有
Java 跨产品 import：没有
数据库实例：每个产品独立
运行时 API 依赖：仍然存在
```

产品包默认构建为 **standalone**，即产品自带：

```text
本产品服务
platform-auth
platform-gateway
本产品前端
本产品数据库 bootstrap
```

也支持共享底座模式：

```bash
STANDALONE=0 bash products/build-product.sh DAM 1.0.0
```

---

## 2. 总体架构

```text
                         ┌──────────────────────────────┐
                         │        Browser / PC          │
                         │  product frontend :3000/80   │
                         └──────────────┬───────────────┘
                                        │ /api
                         ┌──────────────▼───────────────┐
                         │   platform-gateway :8080      │
                         │  JWT 校验 + 路由 + CORS       │
                         └──────────────┬───────────────┘
             ┌──────────────────────────┼──────────────────────────┐
             │                          │                          │
   ┌─────────▼─────────┐      ┌─────────▼─────────┐      ┌─────────▼─────────┐
   │ platform-auth     │      │ asset-catalog      │      │ governance-*       │
   │ :8081             │      │ :8092              │      │ :8084-8093         │
   └─────────┬─────────┘      └─────────┬─────────┘      └─────────┬─────────┘
             │                          │                          │
   ┌─────────▼─────────┐      ┌─────────▼─────────┐      ┌─────────▼─────────┐
   │ dam_base /         │      │ dam_asset          │      │ dam_governance     │
   │ 产品库 dam_dpm     │      │ dam_dam + dam_dpm  │      │ dam_dgp + dam_dpm  │
   └───────────────────┘      └───────────────────┘      └───────────────────┘
```

每个产品数据库都带一份共享身份 schema：

```text
dam_dpm
```

用于：

```text
sys_user
sys_role
sys_user_role
sys_role_permission
sys_menu
sys_config
sys_dict_*
```

---

## 3. 服务与端口总表

| 模块 | 服务 | 端口 | 所属 |
|---|---|---:|---|
| platform-gateway | platform-gateway | 8080 | base |
| platform-auth | platform-auth | 8081 | base |
| platform-portal | platform-portal | 8091 | DPM |
| platform-system | platform-system | 8104 | DPM |
| platform-task-center | platform-task-center | 8099 | DPM |
| platform-scheduler | platform-scheduler | 8096 | DPM |
| platform-operations | platform-operations | 8088 | DPM |
| asset-catalog | asset-catalog | 8092 | DAM |
| asset-mdm | asset-mdm | 8106 | DAM |
| access-integration | access-integration | 8095 | DAP |
| governance-standard | governance-standard | 8084 | DGP |
| governance-quality | governance-quality | 8085 | DGP |
| governance-security | governance-security | 8086 | DGP |
| governance-lifecycle | governance-lifecycle | 8087 | DGP |
| governance-lineage | governance-lineage | 8093 | DGP |
| processing-develop | processing-develop | 8100 | DPP |
| processing-model | processing-model | 8105 | DPP |
| service-api | service-api | 8097 | DSP |
| analytics-metric | analytics-metric | 8094 | DAN |
| analytics-report | analytics-report | 8103 | DAN |
| app-studio | app-studio | 8107 | DAPP |
| app-market | app-market | 8108 | DAPP |
| ai-model-gateway | ai-model-gateway | 8089 | AI |
| ai-workbench | ai-workbench | 8090 | AI |
| ai-knowledge | ai-knowledge | 8098 | AI |

前端开发服务器不是生产端口：

```text
Vite dev: 3000
生产建议: Nginx 80/443 -> 静态 frontend + /api 反代 gateway
```

---

## 4. 产品清单与数据库

| 产品 | 产品代码 | 数据库实例 | 必需 schema |
|---|---|---|---|
| 数据资产与主数据 | DAM | `dam_asset` | `dam_dam`, `dam_dpm` |
| 数据中台管理 | DPM | `dam_platform` | `dam_dpm` |
| 数据接入 | DAP | `dam_access` | `dam_dap`, `dam_dpm` |
| 数据治理 | DGP | `dam_governance` | `dam_dgp`, `dam_dpm` |
| 数据处理 | DPP | `dam_processing` | `dam_dpp`, `dam_dpm` |
| 数据服务 | DSP | `dam_service` | `dam_dsp`, `dam_dpm` |
| 数据分析 | DAN | `dam_analytics` | `dam_dan`, `dam_dimension`, `dam_dpm` |
| 数据应用 | DAPP | `dam_app` | `dam_dapp`, `dam_dpm` |
| 平台底座 | BASE | `dam_base` | `dam_dpm` |

> `dam_dpm` 是共享身份/基础表。每个产品数据库保留一份，用于认证、角色、菜单、配置。

---

## 5. 各产品架构

## 5.1 DAM 数据资产与主数据

### 服务

```text
asset-catalog   8092
asset-mdm       8106
```

### 前端

产品代码：

```text
VITE_PRODUCT_CODE=DAM
```

显示路由：

```text
/login
/catalog               资产目录
/catalog-detail/:id    资产详情
/metadata              元数据管理
/profile               个人中心
```

前端源码：

```text
products/asset/frontend-src
```

### 网关 API

```text
/api/catalog/**        -> asset-catalog:8092
/api/mdm/**            -> asset-mdm:8106
/api/auth/**           -> platform-auth:8081
```

注意：

```text
/api/catalog/models/**      属于 DPP
/api/catalog/reports/**     属于 DAN
/api/catalog/menus/**       属于 DPM
```

这些更具体的路由会先于 `/api/catalog/**` 匹配。

### 数据库

```text
dam_asset
  dam_dam
    asset_*
    metadata_*
    source_system
    mdm_*
  dam_dpm
    sys_*
```

### 运行时依赖

```text
无必需产品依赖
可选：DGP、DAN
```

### 主要 API

```text
GET  /api/catalog/assets/search
GET  /api/catalog/assets/{id}
POST /api/catalog/assets
PUT  /api/catalog/assets
GET  /api/catalog/assets/{id}/fields
GET  /api/catalog/metadata...
GET  /api/mdm/status
```

---

## 5.2 DPM 数据中台管理

### 服务

```text
platform-portal         8091
platform-system         8104
platform-task-center    8099
platform-scheduler      8096
platform-operations     8088
```

### 前端

```text
VITE_PRODUCT_CODE=DPM
```

显示路由（产品首页 `/operations`）：

```text
/portal
/task-center
/operations      <- DPM 产品首页
/system-config
/system-config/SYS
/cockpit/*
```

### 网关 API

```text
/api/portal/**          -> platform-portal:8091
/api/catalog/menus/**   -> platform-system:8104
/api/task-center/**     -> platform-task-center:8099
/api/scheduler/**       -> platform-scheduler:8096
/api/operations/**      -> platform-operations:8088
```

### 数据库

```text
dam_platform
  dam_dpm
    sys_*
    ticket_*
    task_*
    schedule_*
```

### 运行时依赖

```text
DAM：运维看板/资产统计 API
```

### 主要 API

```text
GET  /api/portal/status
GET  /api/operations/dashboard
GET  /api/operations/tickets
POST /api/operations/tickets
GET  /api/operations/usage-stats
GET  /api/task-center/**
GET  /api/scheduler/**
GET  /api/catalog/menus
```

---

## 5.3 DAP 数据接入

### 服务

```text
access-integration  8095
```

### 前端

```text
VITE_PRODUCT_CODE=DAP
```

显示路由：

```text
/integration
/data-sources
```

### 网关 API

```text
/api/integration/** -> access-integration:8095
/api/access/**      -> access-integration:8095
```

### 数据库

```text
dam_access
  dam_dap
    access_*
    integration_*
  dam_dpm
    sys_*
```

### 运行时依赖

```text
无必需产品依赖
```

### 主要 API

```text
GET  /api/integration/jobs
POST /api/integration/jobs
GET  /api/access/...
```

---

## 5.4 DGP 数据治理

### 服务

```text
governance-standard  8084
governance-quality   8085
governance-security  8086
governance-lifecycle 8087
governance-lineage   8093
```

### 前端

```text
VITE_PRODUCT_CODE=DGP
```

显示路由：

```text
/standard
/quality
/security
/lifecycle
/lineage
```

### 网关 API

```text
/api/standard/**   -> governance-standard:8084
/api/quality/**    -> governance-quality:8085
/api/security/**   -> governance-security:8086
/api/lifecycle/**  -> governance-lifecycle:8087
/api/lineage/**    -> governance-lineage:8093
```

### 数据库

```text
dam_governance
  dam_dgp
    standard_*
    quality_*
    security_*
    lifecycle_*
    lineage_*
  dam_dpm
    sys_*
```

### 运行时依赖

```text
DAM：血缘、质量目标表名、资产 API
DPM：整改工单 API
```

### 与其他产品的接口调用

```text
governance-lineage
  -> DAM /api/catalog/lineage-data/table-layers
  -> DAM /api/catalog/lineage-data/source-systems
  -> DAM /api/catalog/lineage-data/search-*

governance-quality
  -> DAM /api/catalog/lineage-data/table-name/{id}
  -> DPM /api/operations/tickets

governance-lifecycle
  -> DAM /api/catalog/assets/{id}
  -> DAM /api/catalog/assets
  -> DPM /api/operations/usage-stats
```

### 主要 API

```text
GET  /api/standard/**
GET  /api/quality/**
GET  /api/security/**
GET  /api/lifecycle/**
GET  /api/lineage/**
```

---

## 5.5 DPP 数据处理

### 服务

```text
processing-develop  8100
processing-model    8105
```

### 前端

```text
VITE_PRODUCT_CODE=DPP
```

显示路由：

```text
/models
/develop
/scheduler
```

### 网关 API

```text
/api/dev/**              -> processing-develop:8100
/api/catalog/models/**   -> processing-model:8105  order=-1
```

### 数据库

```text
dam_processing
  dam_dpp
    model_*
    dev_*
  dam_dpm
    sys_*
```

### 运行时依赖

```text
DAM：模型与资产字段对比 API
```

### 接口调用

```text
processing-model -> DAM /api/catalog/assets/search
processing-model -> DAM /api/catalog/assets/{id}
```

---

## 5.6 DSP 数据服务

### 服务

```text
service-api  8097
```

### 前端

```text
VITE_PRODUCT_CODE=DSP
```

显示路由：

```text
/data-service
```

### 网关 API

```text
/api/data-service/** -> service-api:8097
/open-api/**         -> service-api:8097
```

### 数据库

```text
dam_service
  dam_dsp
    data_api
    api_subscription
    data_api_key
    data_api_call_log
  dam_dpm
    sys_*
```

### 运行时依赖

```text
无必需产品依赖
```

---

## 5.7 DAN 数据分析

### 服务

```text
analytics-metric  8094
analytics-report  8103
```

### 前端

```text
VITE_PRODUCT_CODE=DAN
```

显示路由：

```text
/metrics
/reports
```

### 网关 API

```text
/api/metrics/**                    -> analytics-metric:8094
/api/catalog/reports/**            -> analytics-report:8103  order=-1
/api/catalog/report/**             -> analytics-report:8103  order=-1
/api/catalog/internal/ai/reports/** -> analytics-report:8103
/api/catalog/internal/ai/metrics/** -> analytics-report:8103
```

### 数据库

```text
dam_analytics
  dam_dan
    metric_*
    report_catalog
  dam_dimension
    report_dimension
    report_dimension_item
  dam_dpm
    sys_*
```

### 运行时依赖

```text
DAM：报表数据源、资产、元数据上下文
```

### 接口调用

```text
analytics-report -> DAM /api/catalog/report-data/data-source/{id}
analytics-report -> DAM /api/catalog/report-data/asset-comments
analytics-report -> DAM /api/catalog/report-data/tables
analytics-report -> DAM /api/catalog/report-data/visible-domains
analytics-report -> DAM /api/catalog/report-data/assets
analytics-report -> DAM /api/catalog/report-data/assets/upsert
analytics-report -> DAM /api/catalog/report-data/metrics
analytics-report -> DAM /api/catalog/report-data/knowledge/search
```

---

## 5.8 DAPP 数据应用

### 服务

```text
app-studio  8107
app-market  8108
```

### 前端

```text
VITE_PRODUCT_CODE=DAPP
```

显示路由（产品首页 `/app-home`）：

```text
/app-home      <- DAPP 产品首页
/portal
```

> DAPP 的后端仍是服务骨架；`/app-home` 是独立产品首页。

### 网关 API

```text
/api/app-studio/** -> app-studio:8107
/api/app-market/** -> app-market:8108
```

### 数据库

```text
dam_app
  dam_dapp
  dam_dpm
    sys_*
```

### 运行时依赖

```text
无必需产品依赖
```

---

## 5.9 AI 能力包

AI 不是第 9 个产品，是能力包：

```text
ai-model-gateway  8089
ai-workbench      8090
ai-knowledge      8098
```

网关：

```text
/api/model-gateway/** -> ai-model-gateway:8089
/api/agent/**         -> ai-model-gateway:8089
/api/ai-workbench/**  -> ai-workbench:8090
/api/knowledge/**     -> ai-knowledge:8098
```

AI 的运行时依赖：

```text
ai-knowledge -> DAM metadata API
ai-knowledge -> DGP standard API
```

---

## 6. 前端架构

### 6.1 产品前端源码

每个产品：

```text
products/<product>/frontend-src/
  package.json
  vite.config.ts
  index.html
  src/
  .env.production
```

`.env.production`：

```text
VITE_PRODUCT_CODE=DAM
```

### 6.2 产品路由白名单

文件：

```text
src/config/product.ts
```

配置：

```text
DAM  /catalog /catalog-detail /metadata
DPM  /portal /task-center /operations /system-config /cockpit
DAP  /integration /data-sources
DGP  /standard /quality /security /lifecycle /lineage
DPP  /models /develop /scheduler
DSP  /data-service
DAN  /metrics /reports
DAPP /portal
```

不在白名单的路径会回到产品首页。

### 6.2.1 登录页与产品首页

每个产品构建都包含 `/login`，但登录页会按产品注入品牌：

```text
数据资产与主数据
数据中台管理
数据接入
数据治理
数据处理
数据服务
数据分析
数据应用
```

登录成功后跳转产品首页：

| 产品 | 首页 |
|---|---|
| DAM | `/catalog` |
| DPM | `/operations` |
| DAP | `/integration` |
| DGP | `/quality` |
| DPP | `/models` |
| DSP | `/data-service` |
| DAN | `/reports` |
| DAPP | `/app-home` |

登录页组件本身复用同一套布局，但品牌名、页面标题和登录后首页按产品代码区分。

### 6.3 产品构建

```bash
cd products/asset/frontend-src
npm install
npm run build
```

或批量：

```bash
bash products/build-frontend.sh
```

产物：

```text
codebase/frontend/dist-products/DAM
...
```

产品包构建时复制：

```text
products/asset/dist/1.0.0/frontend
```

### 6.4 开发代理

`vite.config.ts`：

```ts
target: process.env.VITE_API_TARGET || 'http://localhost:8080'
```

本机演示：

```bash
VITE_PRODUCT_CODE=DAM VITE_API_TARGET=http://127.0.0.1:18080 npm run dev
```

### 6.5 生产前端

产品包：

```bash
bin/serve-frontend.sh
```

或使用 Nginx：

```nginx
location / {
    root /opt/dam-products/<product>/current/frontend;
    try_files $uri $uri/ /index.html;
}

location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

---

## 7. 鉴权设计

### 7.1 登录流程

```text
1. 前端 GET /api/auth/public-key
2. 前端 RSA 加密密码
3. 前端 POST /api/auth/login
4. platform-auth 解密密码，BCrypt 校验
5. 返回 JWT token
6. 前端保存 localStorage.token
7. 后续请求带 header: token
8. gateway 校验 JWT
9. 下游服务读取角色、dataScope、permissions
```

### 7.2 网关鉴权

网关 Sa-Token：

```text
SaReactorFilter
  包含：/**
  排除：
    /api/auth/login
    /api/auth/public-key
    /favicon.ico
  校验：
    /api/** 需要有效 token
```

JWT 配置：

```yaml
sa-token:
  token-name: token
  token-style: jwt
  jwt-secret-key: ${SA_TOKEN_JWT_SECRET_KEY}
```

### 7.3 所有服务必须一致

```text
SA_TOKEN_JWT_SECRET_KEY
```

必须先启动：

```text
platform-auth
platform-gateway
```

### 7.4 角色与数据范围

token 携带：

```text
userId
username
roles
dataScope
department
```

`dataScope`：

```text
ALL
DEPARTMENT
SELF
```

下游使用 `DataScopeContext` 过滤。

### 7.5 CORS

网关：

```yaml
allowedOriginPatterns:
  - ${DAM_CORS_ORIGINS:http://localhost:5173}
  - http://127.0.0.1:3000
  - http://localhost:3000
```

生产必须改成实际前端域名。

---

## 8. 前后端 API 调用关系

### 8.1 前端到网关

前端 Axios：

```ts
baseURL: '/api'
headers.token = localStorage.token
```

因此前端请求：

```text
/api/auth/login
/api/catalog/assets/search
/api/metrics/status
...
```

由 Nginx/Vite 代理到 gateway。

### 8.2 网关路由表

| 路径 | 目标 |
|---|---|
| `/api/auth/**` | platform-auth:8081 |
| `/api/portal/**` | platform-portal:8091 |
| `/api/catalog/menus/**` | platform-system:8104 |
| `/api/task-center/**` | platform-task-center:8099 |
| `/api/scheduler/**` | platform-scheduler:8096 |
| `/api/operations/**` | platform-operations:8088 |
| `/api/catalog/models/**` | processing-model:8105 |
| `/api/catalog/reports/**` | analytics-report:8103 |
| `/api/catalog/report/**` | analytics-report:8103 |
| `/api/catalog/internal/ai/reports/**` | analytics-report:8103 |
| `/api/catalog/internal/ai/metrics/**` | analytics-report:8103 |
| `/api/catalog/**` | asset-catalog:8092 |
| `/api/mdm/**` | asset-mdm:8106 |
| `/api/integration/**` | access-integration:8095 |
| `/api/access/**` | access-integration:8095 |
| `/api/standard/**` | governance-standard:8084 |
| `/api/quality/**` | governance-quality:8085 |
| `/api/security/**` | governance-security:8086 |
| `/api/lifecycle/**` | governance-lifecycle:8087 |
| `/api/lineage/**` | governance-lineage:8093 |
| `/api/dev/**` | processing-develop:8100 |
| `/api/data-service/**` | service-api:8097 |
| `/open-api/**` | service-api:8097 |
| `/api/metrics/**` | analytics-metric:8094 |
| `/api/app-studio/**` | app-studio:8107 |
| `/api/app-market/**` | app-market:8108 |
| `/api/model-gateway/**` | ai-model-gateway:8089 |
| `/api/agent/**` | ai-model-gateway:8089 |
| `/api/ai-workbench/**` | ai-workbench:8090 |
| `/api/knowledge/**` | ai-knowledge:8098 |

### 8.3 产品之间服务调用

```text
DGP lineage -> DAM /api/catalog/lineage-data/table-layers
DGP lineage -> DAM /api/catalog/lineage-data/search-columns
DGP lineage -> DAM /api/catalog/lineage-data/search-tables
DGP lineage -> DAM /api/catalog/lineage-data/source-systems
DGP quality -> DAM /api/catalog/lineage-data/table-name/{id}
DGP quality/lifecycle -> DPM operations API
DPP model -> DAM assets API
DAN report -> DAM report-data API
DPM operations -> DAM assets API
AI knowledge -> DAM metadata API
AI knowledge -> DGP standard API
```

这些依赖不进入 Maven，只通过 HTTP。

---

## 9. 配置项

### 9.1 所有服务公共

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SA_TOKEN_JWT_SECRET_KEY
SPRING_PROFILES_ACTIVE
SPRING_CLOUD_NACOS_DISCOVERY_ENABLED
SPRING_CLOUD_NACOS_CONFIG_ENABLED
```

### 9.2 数据源

```text
DAM       jdbc:postgresql://host:5432/dam_asset
DPM       jdbc:postgresql://host:5432/dam_platform
DAP       jdbc:postgresql://host:5432/dam_access
DGP       jdbc:postgresql://host:5432/dam_governance
DPP       jdbc:postgresql://host:5432/dam_processing
DSP       jdbc:postgresql://host:5432/dam_service
DAN       jdbc:postgresql://host:5432/dam_analytics
DAPP      jdbc:postgresql://host:5432/dam_app
BASE      jdbc:postgresql://host:5432/dam_base
```

### 9.3 产品间 URL

```text
DAM_ASSET_CATALOG_URL
DAM_OPERATIONS_URL
DAM_METRIC_URL
DAM_KNOWLEDGE_URL
DAM_MODEL_GATEWAY_URL
DAM_QUALITY_URL
DAM_LINEAGE_URL
DAM_TASK_CENTER_URL
```

### 9.4 安全配置

```text
SA_TOKEN_JWT_SECRET_KEY
DAM_SECURITY_CRYPTO_SECRET
DAM_CREDENTIAL_SECRET
DAM_CORS_ORIGINS
```

生产必须替换所有示例密钥。

---

## 10. 产品构建与独立部署

### 10.1 构建

```bash
bash products/build-product.sh DAM 1.0.0
```

默认 standalone：

```text
本产品 jar
platform-auth jar
platform-gateway jar
前端
数据库 bootstrap
systemd unit
```

共享底座模式：

```bash
STANDALONE=0 bash products/build-product.sh DAM 1.0.0
```

### 10.2 包结构

```text
products/<product>/dist/<version>/
  jars/
  frontend/
  frontend-src/
  db/bootstrap.sql
  db/migrate.sql
  deploy/gateway-routes.yaml
  systemd/
  bin/
    create-db.sh
    bootstrap-db.sh
    migrate.sh
    start.sh
    stop.sh
    health.sh
    serve-frontend.sh
    install-product.sh
    uninstall.sh
    install-systemd.sh
    uninstall-systemd.sh
    rollback.sh
```

### 10.3 数据库初始化

```bash
export SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:5432/dam_asset'
export SPRING_DATASOURCE_USERNAME=dam
export SPRING_DATASOURCE_PASSWORD='...'

bin/create-db.sh
bin/bootstrap-db.sh
```

### 10.4 安装

```bash
tar -xzf DAM-1.0.0.tar.gz
cd 1.0.0
sudo ./bin/install-product.sh /opt/dam-products/asset
```

安装后：

```text
/opt/dam-products/asset/current
/etc/dam-products/asset.env
/etc/dam-products/routes/asset.yaml
```

### 10.5 启动

```bash
/opt/dam-products/asset/current/bin/start.sh
/opt/dam-products/asset/current/bin/health.sh
```

### 10.6 systemd

```bash
sudo /opt/dam-products/asset/current/bin/install-systemd.sh
```

### 10.7 前端

```bash
/opt/dam-products/asset/current/bin/serve-frontend.sh
```

生产建议使用 Nginx 托管 `frontend/`。

### 10.8 回滚与卸载

```bash
bin/rollback.sh
bin/uninstall.sh
bin/uninstall-systemd.sh
```

卸载不会删除数据库。

---

## 11. 共享底座模式

```bash
STANDALONE=0 bash products/build-product.sh DAM 1.0.0
```

需要先安装：

```text
products/base
```

共享底座包含：

```text
platform-gateway 8080
platform-auth    8081
```

产品只包含自己的业务服务。

两种模式的区别：

| 模式 | 网关/认证 | 适用 |
|---|---|---|
| standalone | 产品包自带 | 单产品交付、完全隔离 |
| shared base | 外部底座 | 多产品共用一个门户 |

---

## 12. 当前依赖状态

### 已完成

```text
Maven 产品间直接依赖：0
Java 跨产品 import：0
产品独立数据库：8 个
产品独立前端产物：8 个
产品 standalone 包：可构建
DGP 不再直接访问 dam_dam
DPP 不再直接访问 dam_dam
```

### 仍然存在

```text
DGP -> DAM API
DGP -> DPM 工单 API
DPP -> DAM API
DAN -> DAM API
DPM -> DAM API
AI knowledge -> DAM/DGP API
每个产品库共享 dam_dpm 身份表
DGP lineage nodeMeta 目前为空实现，层级退回表名前缀推断
DAPP app-studio/app-market 仍是骨架
前端源码是同一 monorepo 物化的 8 份独立目录
```

---

## 13. 本地演示环境

本机演示使用：

```text
PostgreSQL 5432
platform-auth 8081
asset-catalog 8092
asset-mdm 8106
platform-gateway 18080
DAM 前端 dev 3000
```

访问：

```text
http://127.0.0.1:3000/login
```

演示账号：

```text
admin / Dam@2026
```

注意：演示使用 `damplatform` 库；生产产品应使用独立库。

---

## 14. 已知限制

```text
1. 8080 在本机被 nginx 占用，演示网关使用 18080。
2. 产品数据库 bootstrap 是 schema-only，生产首次管理员需要单独初始化。
3. systemd 未在真实 Linux systemd 环境执行验证。
4. DGP 血缘 nodeMeta 接口空实现，部分层级功能退化。
5. DAPP 只有服务骨架，没有应用开发/市场业务页面。
6. 产品前端源码虽独立成目录，但仍来自同一 monorepo。
7. 产品数据库仍共享 dam_dpm 身份 schema。
```

---

## 15. 验收命令

```bash
# 1. 构建
bash products/build-product.sh DAM 1.0.0

# 2. 建库
export SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:5432/dam_asset'
export SPRING_DATASOURCE_USERNAME=dam
export SPRING_DATASOURCE_PASSWORD='...'
products/asset/dist/1.0.0/bin/create-db.sh
products/asset/dist/1.0.0/bin/bootstrap-db.sh

# 3. 启动
products/asset/dist/1.0.0/bin/start.sh
products/asset/dist/1.0.0/bin/health.sh

# 4. 前端
FRONTEND_PORT=3000 products/asset/dist/1.0.0/bin/serve-frontend.sh
```

验收通过标准：

```text
产品服务端口全部 UP
gateway 可访问 /api/auth/login
admin 可登录
产品路由可访问
数据库只包含本产品 schema + dam_dpm
```
