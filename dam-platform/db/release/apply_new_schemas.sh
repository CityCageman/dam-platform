#!/usr/bin/env bash
# ============================================================================
# 数据资产管理平台 - 新 schema 增量迁移执行脚本（v21 ~ v25）
#
# 用途：
#   在「已有基础 schema 的库」上按顺序执行 v21~v25，补齐
#   dam_metric / dam_report / dam_knowledge / dam_quality / dam_lineage /
#   dam_lifecycle / dam_integration / dam_scheduler / dam_data_service。
#   全新库请先执行 db/create_schema_doc.sql、db/初始化.sql 及历史增量 v2~v20，
#   再执行本脚本（详见 db/release/README.md）。
#
# 用法：
#   export PGPASSWORD='***'          # 密码只走环境变量或 ~/.pgpass，不要写在命令行
#   bash db/release/apply_new_schemas.sh -h 10.162.3.16 -p 11001 -d damplatform -U dam
#   bash db/release/apply_new_schemas.sh -n -h ...   # 仅检查连接/文件并打印计划
#
# 参数（未指定时取 PG* 环境变量或默认值）：
#   -h HOST      数据库主机（默认 127.0.0.1）
#   -p PORT      数据库端口（默认 5432）
#   -d DBNAME    数据库名（默认 damplatform）
#   -U USER      数据库用户名（默认 dam）
#   -n           只做存在性/连接检查并打印计划，不执行迁移
#
# 说明：
#   - set -euo pipefail；任一脚本失败立即中止；
#   - 每个迁移文件使用 psql --single-transaction 单独提交，失败自动回滚该文件；
#   - v21~v25 均为 IF NOT EXISTS / IF EXISTS 写法，可重复执行；
#   - 执行前务必按 README 备份，本脚本不负责备份。
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

PGHOST="${PGHOST:-127.0.0.1}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-damplatform}"
PGUSER="${PGUSER:-dam}"

DRY_RUN=0

# 迁移顺序固定：v21 指标/报表/知识 -> v22 质量/血缘/生命周期 -> v23~v25 新服务表
MIGRATIONS=(
  "v21_metric_report_knowledge_schema.sql"
  "v22_quality_lineage_lifecycle_schema.sql"
  "v23_integration_management.sql"
  "v24_scheduler_management.sql"
  "v25_data_service_management.sql"
  "v26_task_center.sql"
  "v27_dev_task.sql"
  "v28_integration_run.sql"
  "v29_scheduler_dispatch.sql"
  "v30_task_callback.sql"
  "v31_dev_datasource.sql"
  "v32_scheduler_dag.sql"
  "v33_data_api_key.sql"
  "v34_scheduler_dolphin.sql"
  "v35_config_to_dict.sql"
  "v36_navigation.sql"
  "v37_data_access.sql"
  "v38_role_scope.sql"
  "v39_ticket_department.sql"
  "v40_metric_quality_department.sql"
)

# 迁移完成后必须存在的 schema
TARGET_SCHEMAS="dam_metric dam_report dam_knowledge dam_quality dam_lineage dam_lifecycle dam_integration dam_scheduler dam_data_service dam_task dam_dev"

# v23~v25 自身建表，迁移后必须存在；缺失即视为失败
REQUIRED_TABLES="
dam_integration.integration_job
dam_scheduler.schedule_job
dam_scheduler.schedule_run
dam_data_service.data_api
dam_data_service.api_subscription
dam_task.task_definition
dam_task.task_instance
dam_task.task_log
dam_dev.dev_task
dam_dev.dev_task_version
dam_integration.integration_run
"

# v21/v22 为「移动既有表」，新库未执行历史增量时可能不存在；缺失只告警不失败
MOVED_TABLES="
dam_metric.metric_definition
dam_metric.metric_value
dam_report.report_catalog
dam_knowledge.knowledge_category
dam_knowledge.knowledge_entry
dam_knowledge.knowledge_sync_config
dam_quality.metadata_quality_rule
dam_quality.metadata_quality_check_result
dam_lineage.metadata_lineage
dam_lineage.metadata_column_lineage
dam_lineage.metadata_lineage_hierarchy
dam_lineage.metadata_lineage_source
dam_lineage.metadata_report_sql_parser
dam_lineage.metadata_column_calc_dependency
dam_lifecycle.metadata_lifecycle_policy
dam_lifecycle.metadata_lifecycle_execution
dam_lifecycle.metadata_destruction_record
"

usage() {
  cat <<'EOF'
用法: apply_new_schemas.sh [-n] [-h HOST] [-p PORT] [-d DBNAME] [-U USER]

  -h HOST      数据库主机（默认取 PGHOST，否则 127.0.0.1）
  -p PORT      数据库端口（默认取 PGPORT，否则 5432）
  -d DBNAME    数据库名（默认取 PGDATABASE，否则 damplatform）
  -U USER      数据库用户名（默认取 PGUSER，否则 dam）
  -n           仅检查并打印执行计划，不实际执行
  --help       显示本帮助

密码：export PGPASSWORD='***' 或使用 ~/.pgpass，禁止写入命令行/脚本。

示例：
  export PGPASSWORD='***'
  bash db/release/apply_new_schemas.sh -h 10.162.3.16 -p 11001 -d damplatform -U dam
EOF
}

die() {
  echo "[错误] $*" >&2
  exit 1
}

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-?" ]; then
  usage
  exit 0
fi

while getopts ":h:p:d:U:n" opt; do
  case "${opt}" in
    h) PGHOST="${OPTARG}" ;;
    p) PGPORT="${OPTARG}" ;;
    d) PGDATABASE="${OPTARG}" ;;
    U) PGUSER="${OPTARG}" ;;
    n) DRY_RUN=1 ;;
    \?)
      usage
      die "未知选项：-${OPTARG}"
      ;;
    :)
      usage
      die "选项 -${OPTARG} 缺少参数"
      ;;
  esac
done

export PGHOST PGPORT PGDATABASE PGUSER

PSQL_OPTS=(-X -q -v ON_ERROR_STOP=1)

# ---------- 1. 存在性检查：psql 与迁移文件 ----------
command -v psql >/dev/null 2>&1 \
  || die "未找到 psql，请安装 PostgreSQL 客户端（postgresql-client）或将其加入 PATH"

for file in "${MIGRATIONS[@]}"; do
  [ -s "${DB_DIR}/${file}" ] || die "迁移脚本不存在或为空：${DB_DIR}/${file}"
done

# ---------- 2. 连接与基础 schema 检查 ----------
log "目标库：${PGUSER}@${PGHOST}:${PGPORT}/${PGDATABASE}"

if [ -z "${PGPASSWORD:-}" ] && [ ! -f "${HOME}/.pgpass" ]; then
  log "提示：未检测到 PGPASSWORD 或 ~/.pgpass，psql 可能交互式提示输入密码"
fi

psql "${PSQL_OPTS[@]}" -A -t -c "SELECT 1;" >/dev/null \
  || die "无法连接数据库 ${PGDATABASE}，请检查主机/端口/账号/密码/网络"

BASE_SCHEMAS="$(psql "${PSQL_OPTS[@]}" -A -t \
  -c "SELECT nspname FROM pg_namespace WHERE nspname IN ('dam_asset','dam_metadata');")"
for schema in dam_asset dam_metadata; do
  printf '%s\n' "${BASE_SCHEMAS}" | grep -qx "${schema}" \
    || die "缺少基础 schema ${schema}：请先执行 db/create_schema_doc.sql 与 db/初始化.sql（新库还需按 README 执行 v2~v20 历史增量）"
done

log "基础 schema 检查通过（dam_asset / dam_metadata 均存在）"

# ---------- 3. 打印计划（dry-run 到此结束） ----------
log "待执行迁移（共 ${#MIGRATIONS[@]} 个，按顺序）："
index=1
for file in "${MIGRATIONS[@]}"; do
  echo "  ${index}. ${file}"
  index=$((index + 1))
done

if [ "${DRY_RUN}" -eq 1 ]; then
  log "dry-run：仅检查，不执行 SQL。"
  exit 0
fi

# ---------- 4. 顺序执行 v21 ~ v25 ----------
index=1
for file in "${MIGRATIONS[@]}"; do
  log "(${index}/${#MIGRATIONS[@]}) 执行 ${file}"
  psql "${PSQL_OPTS[@]}" --single-transaction -f "${DB_DIR}/${file}"
  log "完成 ${file}"
  index=$((index + 1))
done

# ---------- 5. 结果校验 ----------
log "校验目标 schema ..."
PRESENT_SCHEMAS="$(psql "${PSQL_OPTS[@]}" -A -t \
  -c "SELECT nspname FROM pg_namespace WHERE nspname IN ('dam_metric','dam_report','dam_knowledge','dam_quality','dam_lineage','dam_lifecycle','dam_integration','dam_scheduler','dam_data_service');")"

for schema in ${TARGET_SCHEMAS}; do
  printf '%s\n' "${PRESENT_SCHEMAS}" | grep -qx "${schema}" \
    || die "目标 schema ${schema} 不存在，迁移未完成，请检查上面的 psql 输出"
done

log "校验 v23~v25 建表结果 ..."
for table in ${REQUIRED_TABLES}; do
  exists="$(psql "${PSQL_OPTS[@]}" -A -t -c "SELECT to_regclass('${table}') IS NOT NULL;")"
  [ "${exists}" = "t" ] || die "表 ${table} 不存在，迁移未完成"
done

log "校验 v21/v22 迁移表（存在性仅告警） ..."
MISSING_TABLES=""
for table in ${MOVED_TABLES}; do
  exists="$(psql "${PSQL_OPTS[@]}" -A -t -c "SELECT to_regclass('${table}') IS NOT NULL;")"
  if [ "${exists}" != "t" ]; then
    MISSING_TABLES="${MISSING_TABLES} ${table}"
  fi
done
if [ -n "${MISSING_TABLES}" ]; then
  log "警告：以下表不存在（多为全新库尚未执行历史增量 v2~v20，v21/v22 会跳过）："
  for table in ${MISSING_TABLES}; do
    echo "  - ${table}"
  done
  log "如需指标/知识/质量/血缘功能，请按 db/release/README.md 先补齐历史增量后重跑本脚本。"
else
  log "v21/v22 迁移表检查通过"
fi

log "各 schema 表数量："
psql "${PSQL_OPTS[@]}" -P pager=off -c "
SELECT n.nspname AS schema_name,
       count(c.oid) FILTER (WHERE c.relkind = 'r') AS table_count
FROM pg_namespace n
LEFT JOIN pg_class c ON c.relnamespace = n.oid
WHERE n.nspname IN ('dam_metric','dam_report','dam_knowledge','dam_quality','dam_lineage',
                    'dam_lifecycle','dam_integration','dam_scheduler','dam_data_service')
GROUP BY n.nspname
ORDER BY 1;"

log "v21~v25 执行完成。请按 db/release/README.md 的验证 SQL 与权限章节继续操作。"
