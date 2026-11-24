# 产品化命名规范

## 一、总规则

代码、模块、镜像、前端 remote、配置前缀不再统一使用 `dam-`。

统一格式：

```text
<产品前缀>-<模块>
```

示例：

```text
asset-catalog
asset-mdm
platform-gateway
governance-quality
analytics-report
ai-workbench
```

禁止：

```text
dam-catalog
dam-quality
dam-ai-workbench
```

数据库 schema 暂不跟代码同步改名，避免把代码重构和数据迁移绑死。schema 迁移单独立项。

## 二、产品前缀

| 产品 | 产品编码 | 代码前缀 | 说明 |
|---|---|---|---|
| 数据资产与主数据 | DAM | `asset` | 资产、元数据、MDM |
| 数据中台管理 | DPM | `platform` | 门户、调度、任务、系统、运维 |
| 数据接入 | DAP | `access` | 数据源、集成 |
| 数据治理 | DGP | `governance` | 标准、质量、安全、生命周期、血缘 |
| 数据处理 | DPP | `processing` | 开发、模型、存储 |
| 数据服务 | DSP | `service` | API 服务 |
| 数据分析 | DAN | `analytics` | 指标、报表、分析 |
| 数据应用 | DAPP | `app` | 应用、市场 |
| AI 能力 | AI | `ai` | 工作台、模型网关、知识 |
| 共享平台 | PLATFORM | `platform` | 网关、认证、Shell、公共库 |

## 三、Maven 模块映射

| 当前模块 | 新模块 | 产品 |
|---|---|---|
| dam-common | platform-common | 共享平台 |
| dam-gateway | platform-gateway | 共享平台 |
| dam-auth | platform-auth | 共享平台 |
| dam-portal | platform-portal | DPM |
| dam-catalog（asset/metadata） | asset-catalog | DAM |
| dam-catalog（report） | analytics-report | DAN |
| dam-catalog（model） | processing-model | DPP |
| dam-catalog（system） | platform-system | DPM |
| 新建 | asset-mdm | DAM |
| dam-integration | access-integration | DAP |
| dam-standard | governance-standard | DGP |
| dam-quality | governance-quality | DGP |
| dam-security | governance-security | DGP |
| dam-lifecycle | governance-lifecycle | DGP |
| dam-lineage | governance-lineage | DGP |
| dam-develop | processing-develop | DPP |
| 新建 | processing-storage | DPP |
| dam-data-service | service-api | DSP |
| dam-metric | analytics-metric | DAN |
| 新建 | analytics-analysis | DAN |
| dam-task-center | platform-task-center | DPM |
| dam-scheduler | platform-scheduler | DPM |
| dam-operations | platform-operations | DPM |
| 新建 | platform-observability | DPM |
| dam-ai-workbench | ai-workbench | AI |
| dam-model-gateway | ai-model-gateway | AI |
| dam-knowledge | ai-knowledge | AI |
| 新建 | app-studio | DAPP |
| 新建 | app-market | DAPP |

## 四、Java 包名

根包仍然是：

```text
com.dam
```

产品子包：

```text
com.dam.asset
com.dam.platform
com.dam.access
com.dam.governance
com.dam.processing
com.dam.service
com.dam.analytics
com.dam.app
com.dam.ai
```

示例：

```text
com.dam.catalog.asset      -> com.dam.asset.catalog
com.dam.catalog.metadata   -> com.dam.asset.metadata
com.dam.catalog.report     -> com.dam.analytics.report
com.dam.catalog.system     -> com.dam.platform.system
com.dam.quality            -> com.dam.governance.quality
com.dam.integration        -> com.dam.access.integration
com.dam.aiworkbench        -> com.dam.ai.workbench
```

禁止跨产品引用：

```text
com.dam.asset.* 不能 import com.dam.governance.*
com.dam.platform.* 不能 import com.dam.asset.*
```

需要协作只能通过 API 客户端。

## 五、Jar 名

Maven `artifactId` 直接决定 jar 前缀：

```text
asset-catalog-1.0.0-exec.jar
asset-mdm-1.0.0-exec.jar
platform-gateway-1.0.0-exec.jar
governance-quality-1.0.0-exec.jar
analytics-metric-1.0.0-exec.jar
ai-workbench-1.0.0-exec.jar
```

不允许：

```text
dam-*.jar
```

## 六、Docker 镜像

```text
asset-catalog:1.0.0
asset-mdm:1.0.0
platform-gateway:1.0.0
governance-quality:1.0.0
```

命名空间可按环境加：

```text
registry.example.com/dam/asset-catalog:1.0.0
```

## 七、前端

产品前端独立：

```text
products/asset/frontend
products/governance/frontend
products/analytics/frontend
```

remote 名称：

```text
asset-ui
platform-ui
access-ui
governance-ui
processing-ui
service-ui
analytics-ui
app-ui
ai-ui
```

禁止：

```text
dam-dam-ui
dam-ui
```

共享 Shell：

```text
platform-shell
```

## 八、目录

目标：

```text
products/
  asset/
    product.yaml
    backend/
      asset-catalog/
      asset-mdm/
    frontend/
      asset-ui/
    db/
    deploy/

  governance/
    backend/
      governance-standard/
      governance-quality/
      governance-security/
      governance-lifecycle/
      governance-lineage/
    frontend/
      governance-ui/

platform/
  platform-gateway/
  platform-auth/
  platform-shell/
  platform-common/
```

## 九、环境变量

共享平台保持：

```text
PLATFORM_GATEWAY_PORT
PLATFORM_AUTH_*
```

产品使用产品前缀：

```text
ASSET_DB_URL
ASSET_DB_USERNAME
ASSET_DB_PASSWORD

GOVERNANCE_QUALITY_URL
GOVERNANCE_SECURITY_URL

ANALYTICS_METRIC_URL
AI_WORKBENCH_URL
```

兼容期保留旧变量别名，但新代码只读新变量。

## 十、数据库 schema

代码命名与数据库解耦。

第一阶段继续使用：

```text
dam_asset
dam_metadata
dam_mdm
dam_quality
...
```

第二阶段再评估是否改成：

```text
asset_core
asset_metadata
asset_mdm
governance_quality
```

不在代码重命名阶段同时改 schema。

## 十一、产品包清单

`product.yaml`：

```yaml
code: DAM
name: 数据资产与主数据
version: 1.0.0

services:
  - asset-catalog
  - asset-mdm

frontend:
  remote: asset-ui
  entry: /products/asset/remoteEntry.js

schemas:
  - dam_asset
  - dam_metadata
  - dam_mdm

routes:
  - /api/asset/**
  - /api/catalog/**
```

## 十二、执行策略

不要在未拆分的单体上做全仓库改名。

执行顺序：

```text
1. 新目录/新模块直接使用新命名
2. 每拆出一个产品，就把该产品模块和包名一起改
3. 旧模块在迁移期保留兼容 jar
4. 旧前端入口保留 redirect
5. 所有调用切换完成后删除旧模块
```

禁止：

```text
一次性 sed 全仓库改包名
先改 Java 包再拆模块
边改包名边改数据库 schema
```

## 十三、验收

```text
Maven 模块和 jar 不再出现 dam- 前缀
Java 根包按产品拆分
前端 remote 不再使用 dam-
Docker 镜像不再使用 dam-
product.yaml 与实际服务名一致
旧命名只存在于兼容配置，不出现在新代码
```
