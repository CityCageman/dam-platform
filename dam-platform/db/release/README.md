# 新 schema 发版迁移说明（v21 ~ v25）

> 适用范围：本次发版新增的指标 / 知识 / 报表 / 质量 / 血缘 / 生命周期 / 集成 / 调度 / 数据服务 schema 初始化。
> 原则：**只新增增量脚本，不修改 `db/create_schema_doc.sql`、`db/初始化.sql` 及任何既有迁移文件**。
> 目录：`db/release/`（本说明 + `apply_new_schemas.sh`）。

---

## 1. 本次迁移内容

| 顺序 | 脚本 | 内容 | 影响 schema | 类型 |
|---|---|---|---|---|
| 1 | `v21_metric_report_knowledge_schema.sql` | 指标、报表、知识从 `dam_asset` 迁出 | `dam_metric`、`dam_report`、`dam_knowledge` | 建 schema + 移动既有表/序列 |
| 2 | `v22_quality_lineage_lifecycle_schema.sql` | 质量、血缘、生命周期从 `dam_metadata` 迁出 | `dam_quality`、`dam_lineage`、`dam_lifecycle` | 建 schema + 移动既有表/序列 |
| 3 | `v23_integration_management.sql` | 数据集成任务表 | `dam_integration` | 建 schema + 建表/索引 |
| 4 | `v24_scheduler_management.sql` | 调度任务与运行记录表 | `dam_scheduler` | 建 schema + 建表/索引 |
| 5 | `v25_data_service_management.sql` | 数据 API 与订阅表 | `dam_data_service` | 建 schema + 建表/索引 |
| 6 | `v26_task_center.sql` | 任务定义/实例/日志 | `dam_task` | 建 schema + 建表/索引 |
| 7 | `v27_dev_task.sql` | 数据开发任务与版本 | `dam_dev` | 建 schema + 建表/索引 |
| 8 | `v28_integration_run.sql` | 集成运行记录 | `dam_integration` | 建表/索引 |
| 9 | `v29_scheduler_dispatch.sql` | 调度自动派发时间 | `dam_scheduler` | 加列/索引 |
| 10 | `v30_task_callback.sql` | TaskCenter 回调来源字段与幂等键 | `dam_task` | 加列/索引 |
| 11 | `v31_dev_datasource.sql` | 数据开发 SQL 执行数据源配置 | `dam_dev` | 加列 |
| 12 | `v32_scheduler_dag.sql` | 调度顺序 SQL DAG | `dam_scheduler` | 建表 |
| 13 | `v33_data_api_key.sql` | API Key 与调用审计 | `dam_data_service` | 建表 |
| 14 | `v34_scheduler_dolphin.sql` | DolphinScheduler 执行引擎字段 | `dam_scheduler` | 加列/索引 |
| 15 | `v35_config_to_dict.sql` | 枚举配置迁移为字典 | `dam_sys` | 数据迁移 |
| 16 | `v36_navigation.sql` | 按产品边界重建导航配置 | `dam_sys` | 数据替换 |
| 17 | `v37_data_access.sql` | 数据源迁入 dam_access，保留只读视图 | `dam_access`/`dam_metadata` | 表迁移 |
| 18 | `v38_role_scope.sql` | 角色数据范围、默认驾驶舱、超级管理员 | `dam_sys` | 加列/种子 |
| 19 | `v39_ticket_department.sql` | 工单部门字段 | `dam_ticket` | 加列/索引 |
| 20 | `v40_metric_quality_department.sql` | 指标/质量规则部门字段 | `dam_metric`/`dam_quality` | 加列/索引 |
| 21 | `v41_report_api_department.sql` | 报表/数据 API 部门字段 | `dam_report`/`dam_data_service` | 加列/索引 |

要点：

- **v21 / v22 是「移动表」**：通过 `ALTER TABLE IF EXISTS ... SET SCHEMA` 把既有表从 `dam_asset` / `dam_metadata` 移到新 schema，**依赖基础表已经存在**。表不存在时该条语句静默跳过（新库未执行历史增量时即如此）。
- **v23 ~ v25 是「新建表」**：只使用 `CREATE SCHEMA/TABLE/INDEX IF NOT EXISTS`，可直接在空 schema 上执行。
- 所有脚本均可重复执行；重复执行时 v21/v22 因源表已迁走而成为空操作，v23~v29 因 `IF NOT EXISTS` 跳过。
- 迁移只改结构（含序列），**不删除、不重写业务数据**；行数应与迁移前一致。

---

## 2. 前置条件

1. PostgreSQL 10+（生产 TBase / PG10 内核兼容）。脚本只使用 `CREATE SCHEMA IF NOT EXISTS`、`ALTER TABLE IF EXISTS`、`DO $$`、`to_regclass` 等 PG 9.4+ 能力。
2. 执行账号需满足：
   - 对 `dam_asset` / `dam_metadata` 中待移动的表拥有 **owner 权限**（`ALTER TABLE ... SET SCHEMA` 要求表 owner，仅有 DML 权限会报 `must be owner of table`）；
   - 拥有 `CREATE` 权限以创建新 schema（生产一般直接用 `dam` 账号执行）。
3. 已按第 4 节完成备份。
4. 建议在**停写窗口/低峰期**执行：v21/v22 的 `ALTER TABLE ... SET SCHEMA` 需要 `ACCESS EXCLUSIVE` 锁，会短暂阻塞相关表读写。
5. 应用侧：先执行数据库迁移，再启动/重启本次新增服务（metric、knowledge、integration、scheduler、data-service），避免服务启动后找不到 schema。

---

## 3. 执行顺序

### 3.1 已有库（生产/测试环境，如 `10.162.3.16:11001`）

推荐顺序：

```text
备份 → 只读预检（-n） → 执行 v21 → v22 → v23 → v24 → v25 → 验证 SQL → 授权 → 启动新服务
```

```bash
export PGPASSWORD='***'          # 只走环境变量或 ~/.pgpass，禁止写入命令行/脚本

# 1) 只读预检：检查 psql、迁移文件、连接、基础 schema，并打印计划
bash db/release/apply_new_schemas.sh -n \
  -h 10.162.3.16 -p 11001 -d damplatform -U dam

# 2) 正式执行（脚本内部按 v21→v25 顺序、单文件事务执行，失败即中止）
bash db/release/apply_new_schemas.sh \
  -h 10.162.3.16 -p 11001 -d damplatform -U dam
```

脚本行为：

- `set -euo pipefail`；psql 使用 `-v ON_ERROR_STOP=1`，单文件 `--single-transaction`，失败自动回滚该文件并退出；
- 执行前检查 `psql`、5 个 SQL 文件是否存在且非空、连接是否可用、`dam_asset`/`dam_metadata` 是否存在；
- 执行后校验 9 个目标 schema 与 v23~v25 必建表；v21/v22 移动表缺失时只告警（通常是全新库未执行历史增量）；
- 不硬编码密码，也不在输出中打印密码。

如需完全手工执行（例如受限环境无法运行脚本）：

```bash
export PGPASSWORD='***'
for f in v21_metric_report_knowledge_schema.sql \
         v22_quality_lineage_lifecycle_schema.sql \
         v23_integration_management.sql \
         v24_scheduler_management.sql \
         v25_data_service_management.sql; do
  echo ">>> $f"
  psql -X -v ON_ERROR_STOP=1 --single-transaction \
    -h 10.162.3.16 -p 11001 -U dam -d damplatform -f "db/$f"
done
```

### 3.2 新库（全新数据库实例）

全新库没有 `dam_asset` / `dam_metadata` 基础表，**不能直接执行 v21~v25**。完整顺序：

```text
1) db/create_schema_doc.sql          # 基础表结构（dam_system/dam_metadata/dam_asset/...）
2) db/初始化.sql                      # 种子数据（菜单、字典、演示账号等）
3) db/v2 ~ db/v20 历史增量（按编号顺序，仅主脚本；跳过 v6 的 *_precheck/*_rollback/*_verify）  # 指标/知识/血缘/生命周期/AI 任务等表由 v4~v20 创建
4) db/v21 ~ db/v25（本目录脚本）        # 本次发版增量
```

说明与注意：

- **v21/v22 依赖 v10/v11 等历史增量创建的表**（例如 `dam_asset.metric_definition`、`dam_asset.knowledge_entry`）。Docker 首次初始化已把 v2~v20 挂在 v21 之前；若走手工/已有库升级，请先确认这些表存在，否则 v21/v22 会静默跳过，metric/knowledge/quality/lineage 服务将缺表。
- `db/init-all.sql` 是历史整库脚本，与当前代码 schema 存在已知差异（见 `deploy/生产部署方案_2台_麒麟V10.md` 风险章节），**不作为本流程推荐基线**；新库请使用 01+02+历史增量+本次增量。
- 如果新环境只使用本次新增的 integration/scheduler/data-service（v23~v25），也必须先有 `dam_asset`/`dam_metadata` 基础 schema（脚本会检查），但可不执行 v21/v22 所依赖的指标/知识历史增量。
- AI 工作台使用的 `dam_ai` schema 由历史增量 `v19_ai_tasks_schema.sql` 创建，**不在本次 v21~v27 范围内**；Docker 首次初始化已包含 v19，手工升级的新库请一并执行 v19（或确认库中已存在 `dam_ai.ai_task`）。

---

## 4. 备份（执行前必做）

```bash
export PGPASSWORD='***'
TS=$(date +%Y%m%d%H%M)

# 全库自定义格式备份（推荐，支持 pg_restore 选择性恢复）
pg_dump -h 10.162.3.16 -p 11001 -U dam -d damplatform \
  -Fc -f "damplatform_before_v21_${TS}.dump"

# 仅结构备份（便于对比 schema 变化）
pg_dump -h 10.162.3.16 -p 11001 -U dam -d damplatform \
  --schema-only -f "damplatform_schema_before_v21_${TS}.sql"

# 记录待迁移表迁移前所在 schema 与行数（验证用，结果留档）
psql -h 10.162.3.16 -p 11001 -U dam -d damplatform -P pager=off -c "
SELECT schemaname, tablename
FROM pg_tables
WHERE tablename IN ('metric_definition','metric_value','report_catalog',
                    'knowledge_category','knowledge_entry','knowledge_sync_config',
                    'metadata_quality_rule','metadata_quality_check_result',
                    'metadata_lineage','metadata_column_lineage',
                    'metadata_lifecycle_policy','metadata_lifecycle_execution',
                    'metadata_destruction_record')
ORDER BY 1,2;"
```

备份文件不要放在容器卷内；按现场备份规范落到独立目录并校验可读。

---

## 5. 权限与账号

若应用使用独立账号（推荐按服务隔离），迁移完成后按最小权限授权。以下示例中 `dam` 为应用账号，按实际账号替换：

```sql
-- 1) schema 使用权限（无 USAGE 时应用报 "permission denied for schema"）
GRANT USAGE ON SCHEMA
  dam_metric, dam_report, dam_knowledge, dam_quality, dam_lineage,
  dam_lifecycle, dam_integration, dam_scheduler, dam_data_service
TO dam;

-- 2) 现有表读写
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA
  dam_metric, dam_report, dam_knowledge, dam_quality, dam_lineage,
  dam_lifecycle, dam_integration, dam_scheduler, dam_data_service
TO dam;

-- 3) 序列（bigserial 自增必需）
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA
  dam_metric, dam_report, dam_knowledge, dam_quality, dam_lineage,
  dam_lifecycle, dam_integration, dam_scheduler, dam_data_service
TO dam;

-- 4) 后续新建表/序列自动继承（由当前迁移账号执行后才生效，建议由建表账号执行）
ALTER DEFAULT PRIVILEGES IN SCHEMA
  dam_metric, dam_report, dam_knowledge, dam_quality, dam_lineage,
  dam_lifecycle, dam_integration, dam_scheduler, dam_data_service
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO dam;

ALTER DEFAULT PRIVILEGES IN SCHEMA
  dam_metric, dam_report, dam_knowledge, dam_quality, dam_lineage,
  dam_lifecycle, dam_integration, dam_scheduler, dam_data_service
  GRANT USAGE, SELECT ON SEQUENCES TO dam;

-- 5) 只读/审计账号（可选）
-- GRANT USAGE ON SCHEMA dam_metric, dam_knowledge TO dam_ro;
-- GRANT SELECT ON ALL TABLES IN SCHEMA dam_metric, dam_knowledge TO dam_ro;
```

补充：

- 当前 `deploy/docker-compose.*.yml` 默认所有连库服务使用同一 `dam` 账号，迁移账号即应用账号，通常无需额外授权。
- 若为 `dam-ai-workbench` 使用独立 `dam_ai` 账号（`DAM_AI_DB_*`），还需单独授权 `dam_ai` schema；该 schema 不在本次迁移范围。
- `ALTER TABLE ... SET SCHEMA` 需要表 owner；若迁移由 DBA 账号执行、应用账号非 owner，请确保应用账号对新 schema 有上述 USAGE/CRUD/SEQUENCE 权限。

---

## 6. Docker 首次初始化挂载

`deploy/docker-compose.backend.yml`、`deploy/docker-compose.yml`、`deploy/docker-compose.db.yml` 的 postgres 卷已按执行顺序挂载（共 26 个脚本）：

| 容器内文件 | 宿主机脚本 | 说明 |
|---|---|---|
| `01_create_schema.sql` | `db/create_schema_doc.sql` | 基础表结构 |
| `02_init_data.sql` | `db/初始化.sql` | 种子数据 |
| `03_v2_...` ~ `21_v20_...` | `db/v2_...sql` ~ `db/v20_...sql` | 历史增量（指标/知识/血缘/生命周期/AI 任务等表），v21/v22 依赖它们 |
| `22_v21_metric_report_knowledge_schema.sql` | `db/v21_...sql` | 指标/报表/知识 schema |
| `23_v22_quality_lineage_lifecycle_schema.sql` | `db/v22_...sql` | 质量/血缘/生命周期 schema |
| `24_v23_integration_management.sql` | `db/v23_...sql` | 集成管理表 |
| `25_v24_scheduler_management.sql` | `db/v24_...sql` | 调度管理表 |
| `26_v25_data_service_management.sql` | `db/v25_...sql` | 数据服务表 |

说明：v21~v25 在 v2~v20 之后执行（容器内编号 22~26），以保证 v21/v22 要移动的指标/知识/质量/血缘表已由历史增量创建。

注意：

1. `/docker-entrypoint-initdb.d` **只在 `pgdata` 卷为空（首次初始化）时执行一次**。已有卷/已有库不会自动执行，必须用第 3 节的脚本手工升级。
2. 如需重建本地库：`docker compose -f deploy/docker-compose.backend.yml down -v`（**会删除数据卷，仅限本地/测试**）后再 `up -d`。
3. 全新 Docker 库首次初始化覆盖 01/02 + v2~v25（26 个脚本），可直接得到指标/知识/质量/血缘/集成/调度/数据服务以及 `dam_ai` 的完整 schema；启动后仍建议按第 7 节 SQL 校验。
4. 已有库也可以在容器内用挂载文件手工执行（迁移文件以只读方式挂载）：

```bash
docker compose -f deploy/docker-compose.backend.yml exec -T postgres \
  psql -U dam -d damplatform -v ON_ERROR_STOP=1 --single-transaction \
  -f /docker-entrypoint-initdb.d/22_v21_metric_report_knowledge_schema.sql
```

更推荐在宿主机用 `db/release/apply_new_schemas.sh` 统一执行并校验，避免漏文件。

---

## 7. 验证 SQL

迁移完成后逐项执行（全部在目标库）：

```sql
-- 7.1 目标 schema 是否齐全（应返回 9 行）
SELECT nspname
FROM pg_namespace
WHERE nspname IN ('dam_metric','dam_report','dam_knowledge','dam_quality','dam_lineage',
                  'dam_lifecycle','dam_integration','dam_scheduler','dam_data_service')
ORDER BY 1;

-- 7.2 v23~v25 必建表（全部应返回非空）
SELECT to_regclass('dam_integration.integration_job') AS integration_job,
       to_regclass('dam_scheduler.schedule_job')      AS schedule_job,
       to_regclass('dam_scheduler.schedule_run')      AS schedule_run,
       to_regclass('dam_data_service.data_api')       AS data_api,
       to_regclass('dam_data_service.api_subscription') AS api_subscription;

-- 7.3 v21/v22 移动结果：这些表应只出现在新 schema，旧 schema 无残留
SELECT schemaname, tablename
FROM pg_tables
WHERE tablename IN ('metric_definition','metric_value','report_catalog',
                    'knowledge_category','knowledge_entry','knowledge_sync_config',
                    'metadata_quality_rule','metadata_quality_check_result',
                    'metadata_lineage','metadata_column_lineage','metadata_lineage_hierarchy',
                    'metadata_lineage_source','metadata_report_sql_parser',
                    'metadata_column_calc_dependency',
                    'metadata_lifecycle_policy','metadata_lifecycle_execution',
                    'metadata_destruction_record')
ORDER BY 1,2;

-- 7.4 序列是否跟随迁移（应位于新 schema）
SELECT sequence_schema, sequence_name
FROM information_schema.sequences
WHERE sequence_schema IN ('dam_metric','dam_report','dam_knowledge','dam_quality',
                          'dam_lineage','dam_lifecycle')
ORDER BY 1,2;

-- 7.5 行数抽查（与迁移前记录对比，应一致）
SELECT 'dam_metric.metric_definition' AS t, count(*) FROM dam_metric.metric_definition
UNION ALL SELECT 'dam_metric.metric_value', count(*) FROM dam_metric.metric_value
UNION ALL SELECT 'dam_report.report_catalog', count(*) FROM dam_report.report_catalog
UNION ALL SELECT 'dam_knowledge.knowledge_entry', count(*) FROM dam_knowledge.knowledge_entry
UNION ALL SELECT 'dam_quality.metadata_quality_rule', count(*) FROM dam_quality.metadata_quality_rule
UNION ALL SELECT 'dam_lineage.metadata_lineage', count(*) FROM dam_lineage.metadata_lineage
UNION ALL SELECT 'dam_lifecycle.metadata_lifecycle_policy', count(*) FROM dam_lifecycle.metadata_lifecycle_policy;

-- 7.6 索引/约束随表迁移
SELECT schemaname, tablename, indexname
FROM pg_indexes
WHERE schemaname IN ('dam_metric','dam_report','dam_knowledge','dam_quality',
                     'dam_lineage','dam_lifecycle','dam_integration',
                     'dam_scheduler','dam_data_service')
ORDER BY 1,2,3;

-- 7.7 应用账号权限
SELECT has_schema_privilege('dam','dam_metric','USAGE')            AS metric_schema_usage,
       has_schema_privilege('dam','dam_knowledge','USAGE')         AS knowledge_schema_usage,
       has_table_privilege('dam','dam_integration.integration_job','SELECT') AS integration_select,
       has_table_privilege('dam','dam_scheduler.schedule_job','INSERT')      AS scheduler_insert;
```

服务侧冒烟（按实际网关地址）：

```bash
# 网关路由是否可达（token 由登录接口获取）
curl -s -o /dev/null -w '%{http_code}\n' http://<网关IP>:8080/api/metrics/definitions
curl -s -o /dev/null -w '%{http_code}\n' http://<网关IP>:8080/api/knowledge/entries
curl -s -o /dev/null -w '%{http_code}\n' http://<网关IP>:8080/api/integration/jobs
curl -s -o /dev/null -w '%{http_code}\n' http://<网关IP>:8080/api/scheduler/jobs
curl -s -o /dev/null -w '%{http_code}\n' http://<网关IP>:8080/api/data-service/apis
```

> 401 表示网关门禁生效（JWT 校验正常），404/502/500 才需要继续排查路由与服务。

---

## 8. 回滚

优先使用备份恢复（最可靠）：

```bash
export PGPASSWORD='***'
# 恢复到迁移前（会覆盖同名对象，执行前确认目标库）
pg_restore -h 10.162.3.16 -p 11001 -U dam -d damplatform \
  --clean --if-exists --no-owner "damplatform_before_v21_XXXX.dump"
```

分版本处理：

- **仅 v23~v25（新建表，无业务数据）**：停相关服务后可直接删除新建 schema：
  ```sql
  DROP SCHEMA IF EXISTS dam_integration  CASCADE;
  DROP SCHEMA IF EXISTS dam_scheduler    CASCADE;
  DROP SCHEMA IF EXISTS dam_data_service CASCADE;
  ```
  注意会连同表内数据一起删除，执行前确认。
- **v21/v22（移动表）**：不能简单 `DROP SCHEMA`（会丢业务表），需把表/序列移回原 schema，或直接用备份恢复：
  ```sql
  -- 示例：按需逐表回退（需表 owner，且原 schema 中无同名表）
  ALTER TABLE dam_metric.metric_definition SET SCHEMA dam_asset;
  ALTER TABLE dam_metric.metric_value      SET SCHEMA dam_asset;
  ALTER TABLE dam_report.report_catalog    SET SCHEMA dam_asset;
  ALTER TABLE dam_knowledge.knowledge_category SET SCHEMA dam_asset;
  ALTER TABLE dam_knowledge.knowledge_entry    SET SCHEMA dam_asset;
  -- 序列同理：ALTER SEQUENCE dam_metric.metric_definition_id_seq SET SCHEMA dam_asset;
  ```
  回退后需重启服务（旧代码）并重新验证。

---

## 9. 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| `psql: command not found` | 宿主机未装客户端 | 安装 `postgresql-client`，或改用容器内 `psql` |
| `permission denied for schema dam_xxx` | 应用账号无 USAGE | 执行第 5 节授权 |
| `must be owner of table xxx` | 迁移账号不是表 owner | 用 owner 账号（通常 `dam`）执行，或由 DBA 执行 |
| `relation "xxx" already exists` | 源/目标 schema 同时存在同名表 | 先确认哪份是有效数据，人工合并后再 `SET SCHEMA` |
| v21/v22 执行后新 schema 仍无表 | 全新库未执行 v2~v20，基础表不存在 | 按 3.2 节补齐历史增量后重跑 |
| Docker 初始化后没有新表 | `pgdata` 卷非空，initdb 脚本未执行 | 用第 3 节脚本手工升级（不要随意 `down -v`） |
| 迁移长时间等待 | `ALTER TABLE SET SCHEMA` 等锁 | 检查长事务/连接，必要时低峰停写重试 |
| 应用仍报找不到表 | 服务未重启或数据源 `currentSchema` 未生效 | 重启对应服务，检查 `SPRING_DATASOURCE_URL` / `hikari.schema` |

---

## 10. 约束与已知风险

- 本目录脚本**不修改** `db/create_schema_doc.sql`、`db/初始化.sql` 及 `v2`~`v20` 既有迁移。
- 本目录只覆盖 v21~v27；`dam_ai`（v19）、历史 v2~v20 不在范围内。
- `dam-ai-workbench` 的 `application.yml` 仍声明了数据源（`DAM_AI_DB_*`，缺省回退 `SPRING_DATASOURCE_*`），本次 compose 按其「不连库」部署模式未注入数据库变量；如需启用 AI 任务中心持久化，请显式提供 `DAM_AI_DB_URL/USERNAME/PASSWORD`，并确认 `dam_ai.ai_task`、`dam_ai.ai_task_item` 已存在（v5 + v19）。
- v21/v22 的幂等性依赖「源表已迁走」：若目标 schema 中已存在同名表（重复建表/人工拷贝），`SET SCHEMA` 会失败并回滚该文件。
- Docker 初始化挂载按 01/02 + v2~v25 的全量顺序执行（26 个脚本，见第 6 节）；`apply_new_schemas.sh` 只执行 v21~v25，仅适用于已有基础表的库，不能用于空库。
- 迁移完成前不要启动依赖新 schema 的新服务，否则会因缺表启动失败或接口报错。
