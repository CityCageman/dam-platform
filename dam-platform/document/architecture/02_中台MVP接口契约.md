# 中台 MVP 接口契约

> 字段级元数据同步、资产注册、Schema 变更、血缘、质量结果、策略下发

| 项目 | 内容 |
|---|---|
| 文档版本 | v0.2（已吸收 2026-11-04 决策） |
| 日期 | 2026-11-04 |
| 适用系统 | 数据中台（MVP）、DAM 数据资产管理平台 |
| 接口版本 | `/api/integration/v1` |
| 状态 | 待评审 |

---

## 1. 目的

定义数据中台与 DAM 在 MVP 阶段的集成契约，保证：

- 中台建表/加工后，DAM 能自动登记资产；
- Schema 变更能通知 DAM 做影响分析；
- 中台产生血缘、质量结果，DAM 能展示；
- DAM 的标准、质量规则、安全策略、生命周期策略能下发给中台执行；
- 两边不直接读写对方数据库。

---

## 2. 范围

### 2.1 MVP 包含

- 字段级元数据同步（全量 + 增量）；
- 资产注册/更新；
- Schema 变更通知；
- 表级/字段级血缘上报；
- 质量执行结果上报；
- 生命周期执行结果上报（二期）；
- 数据服务目录登记（可选）；
- 质量规则、安全策略下发（MVP）；标准只读参考；生命周期策略二期；
- 幂等、重试、对账。

### 2.2 MVP 不包含

- DAM 向中台部署模型/DDL；
- DAM 执行 ETL/清洗；
- 中台向 DAM 传业务明细数据；
- 实时事件总线（先用 REST + Outbox）；
- 复杂审批流引擎；
- BI 报表取数接口。

> 模型设计、DDL、物理属性由中台负责；DAM 只登记模型资产和治理信息。

---

## 3. 参与方与部署

```text
┌──────────────────┐        REST/HTTPS        ┌──────────────────┐
│ 数据中台          │ ───────────────────────▶ │ DAM              │
│ 采集/调度/质量执行 │ ◀─────────────────────── │ 资产/标准/策略    │
└──────────────────┘       策略拉取/回执        └──────────────────┘
```

| 系统 | 基础地址示例 |
|---|---|
| DAM | `https://dam.internal/api/integration/v1` |
| 中台 | `https://data-platform.internal/api/integration/v1` |

部署要求：

- 仅内网/VPN；
- HTTPS 或 mTLS；
- 服务账号认证，不使用用户 token；
- IP 白名单；
- 双方都记录审计日志。

---

## 4. 通用约定

### 4.1 协议

- HTTP/1.1 或 HTTP/2；
- `Content-Type: application/json; charset=utf-8`；
- 时间统一 ISO 8601 UTC，如 `2026-11-04T08:00:00Z`；
- 金额/比率用字符串或定点数，避免浮点误差。

### 4.2 认证

```http
Authorization: Bearer <service-token>
X-Request-Id: <uuid>
X-Client: data-platform
```

推荐同时启用 mTLS；服务令牌定期轮换。

### 4.3 幂等

写接口必须支持：

```http
Idempotency-Key: <业务唯一键>
```

- 同一 key + 同一请求体：返回首次结果；
- 同一 key + 不同请求体：返回 `409`；
- 幂等记录至少保留 7 天。

### 4.4 批量限制

| 对象 | 上限 |
|---|---|
| 资产字段 | 1000 个/次 |
| 血缘边 | 1000 条/次 |
| 策略变更 | 200 条/页 |
| 质量样例 | 20 条/次 |

### 4.5 统一响应

```json
{
  "code": "0",
  "message": "success",
  "requestId": "req-20261104-0001",
  "data": {}
}
```

失败：

```json
{
  "code": "DAM-409-001",
  "message": "externalKey 已存在但 assetId 不一致",
  "requestId": "req-20261104-0001",
  "details": []
}
```

---

## 5. 对象与 ID

| 对象 | ID | 分配方 | 说明 |
|---|---|---|---|
| 数据源 | `data_source_id` | 中台 | 中台唯一 |
| 物理表 | `table_id` | 中台 | 中台唯一 |
| 资产 | `asset_id` | DAM | DAM 全局唯一 |
| 外部键 | `external_key` | 中台 | 幂等注册键 |
| 模型 | `model_id` | 中台 | 模型设计 |
| 部署 | `deployment_id` | 中台 | 模型部署实例 |
| 作业 | `job_id` | 中台 | 调度作业 |
| 运行 | `run_id` | 中台 | 单次运行 |
| 质量规则 | `rule_id` | DAM | DAM 定义 |
| 安全策略 | `policy_id` | DAM | DAM 定义 |
| 生命周期策略 | `policy_id` | DAM | DAM 定义 |
| 血缘边 | `edge_key` | 中台 | 幂等键 |
| 变更 | `change_id` | 中台 | Schema 变更 |

### 5.1 external_key 建议格式

```text
dp:{data_source_id}:{schema}:{table}
```

示例：

```text
dp:12:ods:ods_his_order
```

同一物理表永远使用同一个 `external_key`。

---

## 6. 接口总览

### 6.1 中台 → DAM

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/metadata/sync` | 字段级元数据批量同步（MVP 主入口） |
| POST | `/assets/register` | 单表资产注册/更新 |
| POST | `/assets/{assetId}/schema-changes` | Schema 变更 |
| POST | `/lineage/edges` | 血缘上报 |
| POST | `/quality/results` | 质量结果 |
| POST | `/lifecycle/results` | 生命周期执行结果 |
| POST | `/services/publish` | 数据服务目录（P1） |

### 6.2 DAM → 中台

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/policies/changes` | 拉取标准/质量/安全/生命周期策略 |
| POST | `/policies/acks` | 策略应用回执 |
| GET | `/assets/changes` | 资产对账（可选，中台提供） |

> 策略采用“中台拉取”而不是 DAM 主动推送，降低网络和重试复杂度；后续可增加事件推送。

---

## 7. 中台 → DAM 接口

### 7.1 字段级元数据同步（批量，MVP 必做）

中台是技术元数据权威，DAM 不再维护扫描器。中台采集器按全量/增量把表、字段同步到 DAM。

```http
POST /metadata/sync
```

请求：

```json
{
  "syncId": "SYNC-20261104-0001",
  "mode": "INCREMENTAL",
  "snapshotId": null,
  "checkpoint": "2026-11-04T08:00:00Z",
  "page": 1,
  "pageSize": 50,
  "tables": [
    {
      "externalKey": "dp:12:dws:dws_outpatient_visit",
      "dataSourceId": 12,
      "sourceSystem": "HIS",
      "schemaName": "dws",
      "tableName": "dws_outpatient_visit",
      "layer": "DWS",
      "businessDomain": "门诊",
      "tableType": "TABLE",
      "description": "门诊就诊汇总",
      "owner": "chen.zhiyuan",
      "schemaHash": "sha256:...",
      "columns": [
        {
          "name": "dept_id",
          "dataType": "varchar",
          "dataLength": 64,
          "nullable": false,
          "primaryKey": true,
          "ordinal": 1,
          "comment": "科室ID"
        },
        {
          "name": "visit_cnt",
          "dataType": "int4",
          "nullable": true,
          "primaryKey": false,
          "ordinal": 2,
          "comment": "就诊人次"
        }
      ]
    }
  ]
}
```

字段说明：

| 字段 | 必填 | 说明 |
|---|---|---|
| syncId | 是 | 同步批次幂等键 |
| mode | 是 | FULL/INCREMENTAL |
| snapshotId | FULL 时必填 | 快照标识 |
| checkpoint | 否 | 增量水位 |
| tables | 是 | 表列表，单批最多 50 张 |
| columns | 是 | 字段级元数据，单批最多 5000 个 |
| layer | 是 | ODS/DWD/DIM/DWS/ADS；不含 BIZ |
| businessDomain | DWS/ADS 必填 | 业务域，如门诊/住院/财务 |
| schemaHash | 否 | 表结构指纹，用于漂移检测 |

响应：

```json
{
  "code": "0",
  "data": {
    "syncId": "SYNC-20261104-0001",
    "tables": [
      {
        "externalKey": "dp:12:dws:dws_outpatient_visit",
        "assetId": 10002,
        "action": "UPDATED",
        "fieldCount": 18,
        "addedFields": 2,
        "modifiedFields": 1,
        "removedFields": 0,
        "status": "ACTIVE"
      }
    ],
    "nextCheckpoint": "2026-11-04T08:05:00Z",
    "hasMore": false
  }
}
```

规则：

- `FULL`：按 `snapshotId` 记录快照；本次未出现的表不能立即删除，标记 `PENDING_VERIFY`，人工确认后 `DEPRECATED`；
- `INCREMENTAL`：只同步变更表，字段差异由 DAM 计算；
- 字段新增/删除/改类型必须生成变更记录；
- 字段级同步必须包含：字段名、类型、长度/精度、可空、主键、默认值、注释、序号；
- `businessDomain` 是 DWS/ADS 的业务域属性，不是 `layer`；
- 单表调试可继续使用 `/assets/register`。

### 7.2 单表资产注册/更新

```http
POST /assets/register
```

请求：

```json
{
  "externalKey": "dp:12:ods:ods_his_order",
  "dataSourceId": 12,
  "sourceSystem": "HIS",
  "schemaName": "ods",
  "tableName": "ods_his_order",
  "layer": "ODS",
  "businessDomain": null,
  "tableType": "TABLE",
  "description": "HIS 医嘱主表贴源",
  "owner": "wang.guanjia",
  "modelId": null,
  "deploymentId": null,
  "jobId": "JOB-HIS-ODS-001",
  "schemaHash": "sha256:...",
  "columns": [
    {
      "name": "order_id",
      "type": "varchar(64)",
      "nullable": false,
      "primaryKey": true,
      "comment": "医嘱ID"
    },
    {
      "name": "patient_id",
      "type": "varchar(64)",
      "nullable": true,
      "primaryKey": false,
      "comment": "患者ID"
    }
  ]
}
```

字段说明：

| 字段 | 必填 | 说明 |
|---|---|---|
| externalKey | 是 | 幂等键 |
| dataSourceId | 是 | 中台数据源 |
| schemaName | 是 | 物理 Schema |
| tableName | 是 | 物理表 |
| layer | 是 | ODS/DWD/DIM/DWS/ADS；不含 BIZ |
| businessDomain | DWS/ADS 必填 | 业务域，如门诊/住院/财务 |
| tableType | 否 | TABLE/VIEW |
| columns | 是 | 字段列表 |
| schemaHash | 否 | 结构指纹 |
| modelId/deploymentId | 否 | 模型来源 |
| jobId | 否 | 首次加工作业 |

响应：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "assetId": 10001,
    "tableId": 501,
    "action": "CREATED",
    "status": "ACTIVE",
    "version": 1
  }
}
```

行为：

1. 按 `externalKey` 查找；
2. 不存在：创建资产，分配 `asset_id`；
3. 存在：更新元数据；`action=UPDATED`；
4. 结构无变化：`action=UNCHANGED`；
5. 字段新增/删除/改类型：DAM 记录变更并返回影响分析提示；
6. 如果 `assetId` 与 `externalKey` 不匹配：返回 `409`。

#### 7.3 Schema 变更

```http
POST /assets/{assetId}/schema-changes
```

请求：

```json
{
  "changeId": "CHG-20261104-001",
  "assetId": 10001,
  "schemaHash": "sha256:...",
  "detectedAt": "2026-11-04T08:00:00Z",
  "changes": [
    {
      "type": "ADD_COLUMN",
      "columnName": "dept_id",
      "dataType": "varchar(64)",
      "nullable": true,
      "comment": "科室ID"
    },
    {
      "type": "MODIFY_COLUMN",
      "columnName": "amount",
      "oldType": "numeric(18,2)",
      "newType": "numeric(20,2)"
    }
  ]
}
```

响应：

```json
{
  "code": "0",
  "data": {
    "changeId": "CHG-20261104-001",
    "status": "RECORDED",
    "approvalRequired": true,
    "impact": {
      "downstreamAssets": 3,
      "downstreamReports": 2,
      "qualityRules": 1
    }
  }
}
```

规则：

- `DROP_COLUMN`/`MODIFY_COLUMN` 默认 `approvalRequired=true`；
- 中台在 DAM 审批前不得破坏性变更生产表；
- 加列可自动通过，但必须记录。

#### 7.4 血缘上报

```http
POST /lineage/edges
```

请求：

```json
{
  "jobId": "JOB-HIS-DWD-001",
  "runId": "RUN-20261104-0001",
  "edges": [
    {
      "edgeKey": "12:ods.ods_his_order:dw.dwd_order",
      "sourceAssetId": 10001,
      "targetAssetId": 10002,
      "sourceTable": "ods.ods_his_order",
      "targetTable": "dwd.dwd_order",
      "relationType": "TRANSFORM",
      "confidence": 0.98,
      "status": "CONFIRMED"
    },
    {
      "edgeKey": "12:ods.ods_his_order.amount:dw.dwd_order.amount",
      "sourceAssetId": 10001,
      "targetAssetId": 10002,
      "sourceColumn": "amount",
      "targetColumn": "amount",
      "relationType": "AGGREGATE",
      "transform": "SUM(amount)",
      "confidence": 0.95,
      "status": "CONFIRMED"
    }
  ]
}
```

字段：

| 字段 | 说明 |
|---|---|
| sourceAssetId/targetAssetId | DAM 资产 ID |
| sourceColumn/targetColumn | 字段级血缘，可空 |
| relationType | DIRECT/TRANSFORM/AGGREGATE/JOIN/REFERENCE |
| confidence | 0–1 |
| status | CONFIRMED/INFERRED |
| edgeKey | 幂等键 |

响应：

```json
{
  "code": "0",
  "data": {
    "accepted": 2,
    "duplicated": 0,
    "rejected": 0
  }
}
```

#### 7.5 质量结果

```http
POST /quality/results
```

请求：

```json
{
  "ruleId": "QR-001",
  "ruleVersion": 3,
  "assetId": 10001,
  "runId": "QRUN-20261104-001",
  "status": "FAIL",
  "totalRows": 120000,
  "failedRows": 36,
  "passRate": 0.9997,
  "startedAt": "2026-11-04T01:00:00Z",
  "finishedAt": "2026-11-04T01:00:12Z",
  "samples": [
    {"order_id": "A1001", "reason": "patient_id is null"}
  ]
}
```

响应：

```json
{
  "code": "0",
  "data": {
    "resultId": "QRES-20261104-001",
    "status": "RECORDED"
  }
}
```

规则：

- `ruleId` 必须存在于 DAM；
- 规则版本不一致时记录 `warning`，不丢弃结果；
- 样例中避免完整敏感信息。

#### 7.6 生命周期执行结果

```http
POST /lifecycle/results
```

请求：

```json
{
  "policyId": "LC-001",
  "assetId": 10001,
  "action": "ARCHIVE",
  "status": "SUCCESS",
  "executedAt": "2026-11-04T02:00:00Z",
  "message": "已归档到 cold 存储"
}
```

#### 7.7 数据服务目录（P1）

```http
POST /services/publish
```

请求：

```json
{
  "serviceId": "API-OP-001",
  "serviceName": "门诊工作量查询",
  "owner": "chen.zhiyuan",
  "assets": [10002, 10003],
  "endpoint": "/api/open/outpatient/workload",
  "method": "GET",
  "status": "PUBLISHED"
}
```

---

## 8. 中台拉取 DAM 策略与回执

### 8.1 拉取策略变更

```http
GET /policies/changes?since=2026-11-01T00:00:00Z&types=STANDARD,QUALITY_RULE,SECURITY_POLICY,LIFECYCLE_POLICY&page=1&pageSize=200
```

响应：

```json
{
  "code": "0",
  "data": {
    "items": [
      {
        "policyId": "STD-AMOUNT",
        "type": "STANDARD",
        "version": 2,
        "status": "PUBLISHED",
        "updatedAt": "2026-11-04T08:00:00Z",
        "payload": {
          "code": "STD-AMOUNT",
          "name": "费用金额标准",
          "values": []
        }
      },
      {
        "policyId": "QR-001",
        "type": "QUALITY_RULE",
        "version": 3,
        "status": "PUBLISHED",
        "updatedAt": "2026-11-04T08:10:00Z",
        "payload": {
          "assetId": 10001,
          "ruleType": "NOT_NULL",
          "expression": "patient_id IS NOT NULL",
          "threshold": 0.99,
          "severity": "HIGH"
        }
      }
    ],
    "nextSince": "2026-11-04T08:10:00Z",
    "hasMore": false
  }
}
```

策略类型：

| type | 说明 | MVP |
|---|---|---|
| `STANDARD` | 数据标准/码值 | 只读参考，暂不强制下发 |
| `QUALITY_RULE` | 质量规则、阈值、SQL | **MVP 必做** |
| `SECURITY_POLICY` | 分级、敏感类型、脱敏、行级权限 | **MVP 必做** |
| `LIFECYCLE_POLICY` | 保留、归档、销毁 | 二期 |

> MVP 只启用质量规则和安全策略；标准和生命周期策略可以先在 DAM 维护，后续再接入执行。

#### 8.2 策略应用回执

```http
POST /policies/acks
```

请求：

```json
{
  "items": [
    {
      "policyId": "QR-001",
      "version": 3,
      "status": "APPLIED",
      "appliedAt": "2026-11-04T08:15:00Z",
      "message": "已应用到 JOB-HIS-DWD-001"
    }
  ]
}
```

`status`：

- `APPLIED`：已应用；
- `REJECTED`：不适用/校验失败；
- `FAILED`：执行失败。

#### 8.3 资产对账（可选）

中台提供：

```http
GET /assets/changes?since=2026-11-01T00:00:00Z&page=1&pageSize=200
```

DAM 每日对账，修复遗漏的资产注册。

---

## 9. 事件（P1）

MVP 用 REST + 对账；后续可上事件总线。

事件类型：

| 事件 | 方向 |
|---|---|
| `asset.registered` | 中台 → DAM |
| `asset.schema.changed` | 中台 → DAM |
| `lineage.emitted` | 中台 → DAM |
| `quality.completed` | 中台 → DAM |
| `lifecycle.completed` | 中台 → DAM |
| `policy.published` | DAM → 中台 |
| `policy.acknowledged` | 中台 → DAM |

事件信封：

```json
{
  "eventId": "evt-...",
  "eventType": "asset.registered",
  "eventVersion": "1.0",
  "occurredAt": "2026-11-04T08:00:00Z",
  "source": "data-platform",
  "traceId": "...",
  "payload": {}
}
```

---

## 10. 状态机

### 资产

```text
PENDING → ACTIVE → DEPRECATED
```

### 部署

```text
PENDING → DEPLOYED → FAILED
```

### 质量结果

```text
PASS / FAIL / ERROR
```

### 变更

```text
PENDING → APPROVED → APPLIED
        └→ REJECTED
```

### 策略

```text
DRAFT → PUBLISHED → RETIRED
```

---

## 11. 幂等、重试、对账

### 重试

- 中台写 DAM 失败：指数退避，最多 6 次；
- 4xx 不重试（除 409 需人工处理）；
- 5xx/超时重试；
- 仍失败进入死信/人工补偿。

### 对账

- 每日 02:00 资产对账；
- 每日 03:00 血缘/质量结果对账；
- 每周策略版本对账；
- 差异输出报表，人工确认后补偿。

### 幂等键

| 接口 | key |
|---|---|
| 资产注册 | externalKey |
| Schema 变更 | changeId |
| 血缘 | edgeKey |
| 质量结果 | runId + ruleId |
| 生命周期 | policyId + assetId + executedAt |
| 策略回执 | policyId + version |

---

## 12. 错误码

| HTTP | code 示例 | 说明 |
|---|---|---|
| 400 | DAM-400-001 | 参数缺失/格式错误 |
| 401 | DAM-401-001 | 服务令牌无效 |
| 403 | DAM-403-001 | 无接口权限 |
| 404 | DAM-404-001 | 资产/规则不存在 |
| 409 | DAM-409-001 | 幂等冲突/ID 不一致 |
| 422 | DAM-422-001 | 业务校验失败（如 layer 非法） |
| 429 | DAM-429-001 | 限流 |
| 500 | DAM-500-001 | 内部错误 |
| 503 | DAM-503-001 | 依赖服务不可用 |

---

## 13. 安全要求

1. 只在内网/VPN；
2. HTTPS 或 mTLS；
3. 服务令牌，不使用用户 token；
4. IP 白名单；
5. 不传源库账号口令；
6. 质量样例中脱敏；
7. 全链路审计：requestId、调用方、对象、结果；
8. 令牌 90 天轮换；
9. 接口限流；
10. 失败日志不记录敏感数据。

---

## 14. 版本与兼容

- URL 版本：`/v1`；
- 新增字段向后兼容；
- 删除/改语义必须升级 `/v2`；
- 旧版本至少保留 90 天；
- 事件增加 `eventVersion`；
- 发布前双方跑契约测试。

---

## 15. 关键时序

### 15.1 新表注册

```text
中台建表/加工
  → POST /assets/register
  → DAM 返回 assetId
  → 中台保存 assetId
  → 中台 POST /lineage/edges
  → DAM 展示资产和血缘
```

### 15.2 策略下发

```text
DAM 发布标准/质量/安全/生命周期策略
  → 中台 GET /policies/changes
  → 中台应用
  → 中台 POST /policies/acks
  → DAM 更新策略应用状态
```

### 15.3 Schema 变更

```text
中台检测变更
  → POST /assets/{assetId}/schema-changes
  → DAM 影响分析
  → DAM 审批
  → 中台执行/回滚
  → 中台再注册新版本
```

### 15.4 质量执行

```text
DAM 规则 → 中台拉取
  → 中台执行
  → POST /quality/results
  → DAM 展示趋势/异常
```

---

## 16. MVP 验收清单

- [ ] 一张 ODS 表自动注册成功；
- [ ] 一张 DIM 表、一张 DWS/ADS 业务域表自动注册成功；
- [ ] `externalKey` 重复调用返回同一 `assetId`；
- [ ] 字段级元数据全量/增量同步成功，新增/删除/改类型可识别；
- [ ] Schema 加列/删列能通知 DAM；
- [ ] 表级/字段级血缘能展示；
- [ ] DWS/ADS 资产带业务域属性；
- [ ] 质量结果能回写；
- [ ] 质量规则、安全策略能拉取并回执（标准和生命周期二期）；
- [ ] 无跨库直接写入；
- [ ] 失败重试/对账可运行；
- [ ] 审计日志完整；
- [ ] 双方契约测试通过。

---

## 17. 待确认问题

已确认：数据源/扫描、模型/DDL、质量执行归中台；BIZ 为业务域；MVP 必须字段级元数据同步。

剩余待确认：

1. 策略下发采用“中台拉取”还是“DAM 推送”？本文默认拉取。
2. 是否引入 Kafka/事件总线，还是 MVP 先用 REST + Outbox？
3. 资产注册的 `owner` 由中台传还是 DAM 补？
4. DWD 是否建设，以及 DWD 是否承载业务域属性；
5. 接口认证用服务令牌还是 mTLS，或两者都上；
6. 资产对账由 DAM 拉中台，还是中台定时全量推送；
7. FULL 快照中缺失的表，`PENDING_VERIFY` 的人工确认流程由谁负责。
