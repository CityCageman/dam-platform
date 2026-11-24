# 8 产品独立部署架构

> 结论：建议采用“共享平台底座 + 8 个独立产品包”，而不是把 8 个产品做成 8 个互相依赖的巨石，也不是每个产品重复打包一套登录和网关。

## 一、独立部署的定义

每个产品必须满足：

```text
独立构建
独立版本
独立部署
独立升级
独立回滚
独立卸载
独立数据库迁移
独立前端产物
独立路由注册
独立配置
```

同时允许依赖一个版本化的共享平台底座：

```text
dam-gateway
dam-auth
dam-shell
dam-common
```

推荐部署关系：

```text
                    ┌────────────────────┐
                    │  共享平台底座       │
                    │  gateway / auth     │
                    │  shell / common     │
                    └─────────┬──────────┘
                              │
   ┌──────────┬──────────┬────┴─────┬──────────┬──────────┐
   │ DAM      │ DPM      │ DAP      │ DGP      │ DPP      │
   │ 数据资产 │ 平台管理 │ 数据接入 │ 数据治理 │ 数据处理 │
   └──────────┴──────────┴──────────┴──────────┴──────────┘
   ┌──────────┬──────────┬──────────┐
   │ DSP      │ DAN      │ DAPP     │
   │ 数据服务 │ 数据分析 │ 数据应用 │
   └──────────┴──────────┴──────────┘
```

如果要求“产品完全不依赖共享平台”，则每个产品都要自带 gateway/auth/shell，部署成本、升级成本和账号体系会成倍增加。除非明确要求，否则不推荐。

## 二、8 个产品包

每个产品包统一包含：

```text
product.yaml
backend/
frontend/
db/
deploy/
tests/
README.md
```

### 1. DAM 数据资产与主数据

```text
产品编码：DAM
后端：
  dam-catalog（资产、元数据）
  dam-mdm（主数据，新建）
前端：
  资产目录、资产 360、元数据、MDM
数据库：
  dam_asset
  dam_metadata
  dam_mdm
```

### 2. DPM 数据中台管理

```text
产品编码：DPM
后端：
  dam-portal（产品中心、驾驶舱）
  dam-scheduler
  dam-task-center
  dam-operations
  dam-system（从 catalog 拆出）
  dam-observability（新建）
前端：
  产品中心、驾驶舱、调度、任务、工单、系统配置、可观测
数据库：
  dam_sys
  dam_scheduler
  dam_task
  dam_ticket
  dam_observability
```

### 3. DAP 数据接入

```text
产品编码：DAP
后端：
  dam-integration（含 access）
前端：
  数据源、数据集成
数据库：
  dam_access
  dam_integration
```

### 4. DGP 数据治理

```text
产品编码：DGP
后端：
  dam-standard
  dam-quality
  dam-security
  dam-lifecycle
  dam-lineage
前端：
  标准、质量、安全、生命周期、血缘
数据库：
  dam_standard
  dam_quality
  dam_security
  dam_lifecycle
  dam_lineage
```

### 5. DPP 数据处理

```text
产品编码：DPP
后端：
  dam-develop
  dam-model（从 catalog 拆出）
  dam-storage（新建）
前端：
  数据开发、模型管理、存储计算
数据库：
  dam_dev
  dam_model
  dam_storage
```

### 6. DSP 数据服务

```text
产品编码：DSP
后端：
  dam-data-service
前端：
  API 管理、OpenAPI、订阅、API Key
数据库：
  dam_data_service
```

### 7. DAN 数据分析

```text
产品编码：DAN
后端：
  dam-metric
  dam-report（从 catalog 拆出）
  dam-analysis（可选）
前端：
  指标、报表、分析
数据库：
  dam_metric
  dam_report
  dam_analysis
```

### 8. DAPP 数据应用

```text
产品编码：DAPP
后端：
  dam-app（新建）
  dam-market（新建）
前端：
  应用中心、数据市场、应用运行时
数据库：
  dam_app
  dam_market
```

## 三、AI 工作台的归属

AI 是横切能力，不建议作为第 9 个产品。

```text
dam-ai-workbench
dam-model-gateway
dam-knowledge
```

部署方式二选一：

### 方案 A：AI 能力包（推荐）

```text
ai-capability/
  dam-ai-workbench
  dam-model-gateway
  dam-knowledge
```

被 DAM、DGP、DAN、DAPP 共享；每个产品按需启用。

### 方案 B：AI 独立产品包

如果商业上要单独售卖 AI：

```text
AIP AI 产品
```

但这会变成 9 个产品，不是 8 个。

## 四、前端独立部署

### 推荐：共享 Shell + 产品远程模块

```text
dam-shell
  登录
  角色驾驶舱
  产品中心
  布局
  权限
  远程加载产品前端
```

每个产品独立构建：

```text
dam-dam-ui
dam-dpm-ui
dam-dap-ui
dam-dgp-ui
dam-dpp-ui
dam-dsp-ui
dam-dan-ui
dam-dapp-ui
```

产品前端产物通过：

```text
Module Federation
或
产品 manifest + 动态 ESM 加载
```

注册到共享 Shell。

### 不推荐

```text
所有产品共用一个前端构建
一个产品发版必须重新打包整个前端
```

这不符合独立部署。

## 五、后端独立部署

每个产品拥有：

```text
自己的服务镜像/可执行 jar
自己的配置
自己的数据库账号
自己的迁移脚本
自己的健康检查
自己的版本号
```

禁止：

```text
产品 A 直接读写产品 B 的表
产品 A 依赖产品 B 的内部类
产品 A 和产品 B 共享一个可写 schema
```

允许：

```text
通过版本化 API 调用
通过只读视图做短期过渡
通过消息/任务中心异步协作
```

## 六、数据库独立

推荐每个产品一个数据库或一组独立 schema：

```text
dam_dam
dam_dpm
dam_dap
dam_dgp
dam_dpp
dam_dsp
dam_dan
dam_dapp
```

共享：

```text
dam_sys（账号、角色、产品授权）
```

跨产品数据不建外键，只保存业务 ID。

如果客户要求完全物理隔离，可以把每个产品数据库部署到不同 PostgreSQL 实例。

## 七、产品 Manifest

每个产品根目录必须有 `product.yaml`：

```yaml
code: DAM
name: 数据资产与主数据
version: 1.0.0
services:
  - dam-catalog
  - dam-mdm
frontend:
  remote: dam-dam-ui
  entry: /products/dam/remoteEntry.js
schemas:
  - dam_asset
  - dam_metadata
  - dam_mdm
routes:
  - /api/dam/**
  - /api/catalog/**
permissions:
  - asset:view
  - asset:manage
  - mdm:manage
requiresPlatform: ">=1.0.0"
optionalProducts: []
```

网关、Shell、部署脚本都读取这个 manifest，而不是硬编码产品。

## 八、独立部署验收

每个产品必须通过：

```text
1. 只安装平台底座 + 本产品，系统可启动
2. 本产品前端可独立构建
3. 本产品数据库可独立迁移
4. 本产品可以单独升级
5. 本产品可以单独回滚
6. 卸载本产品不影响其他产品
7. 只注册自己的路由
8. 不访问其他产品数据库
9. 产品未授权时菜单不可见
10. 产品服务异常时其他产品仍可用
```

## 九、实施顺序

### Phase 0：平台底座产品化

```text
dam-gateway 动态路由
dam-auth 产品授权
dam-shell 产品注册与加载
product.yaml 规范
产品注册表
```

### Phase 1：DAM 独立产品包

```text
dam-catalog 资产/元数据
dam-mdm 新建
dam-asset/dam-metadata/dam-mdm 独立数据库
DAM 前端独立产物
DAM 安装/升级/回滚脚本
```

### Phase 2：AI 能力包

```text
dam-ai-workbench
dam-model-gateway
dam-knowledge
被 DAM 调用
```

### Phase 3：DPM 独立产品包

```text
portal/scheduler/task/operations/system/observability
```

### Phase 4：其余产品

```text
DAP / DGP / DPP / DSP / DAN / DAPP
```

## 十、当前代码必须先拆的点

```text
dam-catalog：
  asset + metadata + model + report + system 混在一起
  需要拆成 DAM / DPP / DAN / DPM

dam-operations：
  直接读 dam_asset、dam_ticket
  需要改成产品 API 或明确共享只读视图

dam-quality：
  schema 归属不一致
  需要先确定 dam_quality

dam-ai-workbench：
  dam_ai 与 dam_asset.ai_task 归属冲突

dam-portal：
  当前是共享壳还是 DPM 产品，需要明确
  建议：shell 共享，产品中心归 DPM

gateway：
  路由静态写死，需要产品 manifest 动态注册
```

## 十一、需要先确认

1. 共享平台底座是否接受？
2. 每个产品独立数据库，还是独立 schema 即可？
3. AI 是能力包，还是第 9 个独立产品？
4. 产品前端用 Module Federation，还是独立 SPA + 链接？

默认建议：

```text
共享平台底座
每产品独立数据库
AI 作为能力包
产品前端用 manifest + 动态模块加载
```
