# 8 产品独立部署解决方案（定版）

## 一、最终决策

| 决策 | 结论 |
|---|---|
| 部署模式 | 共享平台底座 + 8 个独立产品包 |
| 完全隔离场景 | 产品包支持 standalone 模式，自带底座副本 |
| 数据库 | 每产品独立数据库；共享 `dam_sys` 账号/授权库 |
| 前端 | 共享 Shell + 产品前端独立产物动态加载 |
| 网关 | 按 `product.yaml` 动态注册路由 |
| AI | 能力包，不作为第 9 个产品 |
| 仓库 | 先保持 monorepo，按 `products/` 和 `platform/` 分层；后续可拆仓 |
| 迁移 | 绞杀者模式，不做一次性重写 |

## 二、两种部署模式

### 模式 A：共享底座（默认，推荐）

```text
platform-base
  dam-gateway
  dam-auth
  dam-shell
  dam-common
```

产品只部署自己的服务和前端：

```text
product-dam
  dam-catalog
  dam-mdm
```

优点：

```text
一套账号
一套网关
一套门户
产品独立升级
资源占用低
```

### 模式 B：Standalone

```text
product-dam-standalone
  platform-base
  dam-catalog
  dam-mdm
```

产品包自带底座副本，可独立装到一套环境中。

适合：

```text
客户要求完全隔离
单产品交付
演示/离线环境
```

Standalone 仍然是同一个产品包，只是安装 profile 不同。

## 三、产品包规范

每个产品根目录：

```text
products/dam/
  product.yaml
  backend/
    dam-catalog/
    dam-mdm/
  frontend/
    src/
    package.json
  db/
    migrations/
    bootstrap/
  deploy/
    docker-compose.yml
    helm/
    install.sh
    uninstall.sh
  tests/
    contract/
    smoke/
  README.md
```

产品包不能包含：

```text
其他产品的后端源码
其他产品的数据库迁移
其他产品的前端路由
```

产品包可以声明依赖：

```yaml
optionalProducts:
  - DGP
```

依赖不可用时，产品必须能启动，只是相关入口降级或隐藏。

## 四、product.yaml 规范

示例见：

```text
document/product-manifest.example.yaml
```

关键字段：

```text
code / name / version
services
schemas
routes
frontend.remote
permissions
requiresPlatform
optionalProducts
```

约束：

```text
code 全局唯一
version 语义化版本
services 必须属于本产品
schemas 必须属于本产品
routes 必须带产品前缀
frontend.remote 必须是独立构建产物
```

## 五、产品注册与授权

放在 `dam_sys`：

```text
product_definition
product_module
product_route
product_schema
product_installation
role_product
role_module
role_cockpit
```

安装产品时：

```text
读取 product.yaml
写入 product_definition/product_module
注册网关路由
注册前端 remote
写入产品安装记录
```

卸载产品时：

```text
停止本产品服务
注销路由
移除前端 remote
标记 installation=REMOVED
默认保留数据库
```

## 六、网关方案

网关不再硬编码产品路由。

启动/安装时加载：

```text
product.yaml -> /api/{product}/** -> 产品服务
```

兼容旧路由：

```text
/api/catalog/** -> DAM
/api/quality/** -> DGP
...
```

通过 manifest 的 `legacyRoutes` 声明，不允许在产品代码里改网关。

## 七、前端方案

共享 Shell：

```text
登录
角色驾驶舱
产品中心
布局
权限
产品加载器
```

产品前端独立构建：

```text
product-dam-ui
product-dpm-ui
...
```

产物：

```text
/products/dam/remoteEntry.js
```

Shell 读取产品注册表后动态加载。

约束：

```text
产品前端不能打包进 Shell
产品前端不能引用其他产品源码
Shell 不因产品发版重新构建
```

## 八、数据库方案

每个产品一个数据库：

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
dam_sys
```

规则：

```text
不跨库外键
不跨库 JOIN
不直接读写其他产品表
跨产品通过 API
过渡期允许只读视图
```

产品数据库账号只授权本库 + `dam_sys` 只读（如需要）。

## 九、产品边界与拆分

### DAM

```text
dam-catalog 资产/元数据
dam-mdm 新建
```

必须从 `dam-catalog` 拆出：

```text
report -> DAN
model -> DPP
system -> DPM
```

### DPM

```text
dam-portal
dam-scheduler
dam-task-center
dam-operations
dam-system
dam-observability
```

### DAP

```text
dam-integration（含 access）
```

### DGP

```text
dam-standard
dam-quality
dam-security
dam-lifecycle
dam-lineage
```

### DPP

```text
dam-develop
dam-model
dam-storage
```

### DSP

```text
dam-data-service
```

### DAN

```text
dam-metric
dam-report
dam-analysis
```

### DAPP

```text
dam-app
dam-market
```

### AI 能力包

```text
dam-ai-workbench
dam-model-gateway
dam-knowledge
```

不作为第 9 个产品；可单独安装为能力包。

## 十、跨产品协作

```text
同步：版本化 HTTP API
异步：任务中心 / 消息
禁止：跨库读写、跨服务 Mapper、共享可写表
```

内部 API：

```text
/internal/{product}/{module}/...
```

只允许服务间调用，不经过公网网关。

## 十一、版本兼容

平台底座版本：

```text
platformVersion: 1.x
```

产品声明：

```yaml
requiresPlatform: ">=1.0.0 <2.0.0"
```

升级产品不要求升级其他产品，但必须通过平台兼容测试。

## 十二、CI/CD

每个产品独立流水线：

```text
build backend
build frontend
run contract tests
build product image/bundle
publish product artifact
deploy to product namespace
smoke test
```

平台流水线：

```text
build platform
compatibility matrix
```

## 十三、Phase 0 验收

```text
产品清单定版
product.yaml 校验器
产品注册表设计
网关动态路由设计
Shell 产品加载设计
DAM 拆分清单
数据库归属清单
```

## 十四、Phase 1 验收：DAM 独立包

```text
只安装 platform-base + product-dam 可启动
DAM 前端独立构建
DAM 数据库独立迁移
DAM 可单独升级/回滚/卸载
不访问其他产品数据库
资产目录/资产 360/元数据/MDM 可用
```

## 十五、剩余风险

```text
dam-catalog 仍混合多个产品，必须先拆
dam-operations 直接读资产/工单表
dam-quality / dam-ai / dam-report schema 归属未定
前端仍是单体构建
网关仍是静态路由
真实库 schema 未盘点
```

## 十六、实施顺序

```text
1. 平台底座产品化
2. DAM 独立产品包
3. AI 能力包
4. DPM 独立产品包
5. 其余产品
6. 多租户/套餐/计量（按商业需要）
```

## 十七、完成定义

“8 个产品可完全独立部署”只有在以下条件全部满足时才算完成：

```text
每个产品有 product.yaml
每个产品可单独构建
每个产品可单独部署
每个产品可单独迁移数据库
每个产品可单独升级
每个产品可单独卸载
每个产品只注册自己的路由
每个产品不直接访问其他产品数据库
Shell 不因产品发版重新构建
平台底座故障范围可控
```
