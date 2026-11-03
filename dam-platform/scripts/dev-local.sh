#!/usr/bin/env bash
# =============================================================================
# DAM 平台本地一键启动脚本（开发/演示用）
#
# 用法：
#   ./scripts/dev-local.sh               首次引导配置并启动
#   ./scripts/dev-local.sh --setup       重新生成环境变量
#   ./scripts/dev-local.sh --no-build    跳过 Maven 打包（快速重启）
#   ./scripts/dev-local.sh --migrate     启动前执行 v4/v5/v7 迁移（需 psql）
#   ./scripts/dev-local.sh --migrate --yes  迁移不二次确认
#   ./scripts/dev-local.sh --no-open     不自动打开浏览器
#   ./scripts/dev-local.sh stop|status|logs|restart
#
# 说明：
#   - 环境变量保存在 ~/.dam-local.env（权限 600），不要提交 git；
#   - 仅管理本脚本启动的进程（PID 在 /tmp/dam-local/pids），不会动 systemd；
#   - 默认连接现有内网库；建议使用测试库/快照，避免误写生产数据。
# =============================================================================
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND="$ROOT/codebase/backend"
FRONTEND="$ROOT/codebase/frontend"
DB_DIR="$ROOT/db"
ENV_FILE="${DAM_LOCAL_ENV:-$HOME/.dam-local.env}"
RUN_DIR="${DAM_LOCAL_RUN_DIR:-/tmp/dam-local}"
LOG_DIR="$RUN_DIR/logs"
PID_DIR="$RUN_DIR/pids"

SERVICES=(auth gateway catalog lineage standard quality security lifecycle operations agent)
FRONTEND_PORT=3000

NO_BUILD=0
MIGRATE=0
YES=0
OPEN_BROWSER=1
ACTION="start"

mkdir -p "$LOG_DIR" "$PID_DIR"

msg()  { printf '\033[1;34m[dam]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[dam]\033[0m %s\n' "$*"; }
err()  { printf '\033[1;31m[dam]\033[0m %s\n' "$*" >&2; }
die()  { err "$*"; exit 1; }

usage() {
  cat <<'USAGE'
DAM 平台本地一键启动脚本（开发/演示用）

用法：
  ./scripts/dev-local.sh               首次引导配置并启动
  ./scripts/dev-local.sh --setup       重新生成环境变量
  ./scripts/dev-local.sh --no-build    跳过 Maven 打包（快速重启）
  ./scripts/dev-local.sh --migrate     启动前执行 v4/v5/v7 迁移（需 psql）
  ./scripts/dev-local.sh --migrate --yes  迁移不二次确认
  ./scripts/dev-local.sh --no-open     不自动打开浏览器
  ./scripts/dev-local.sh stop|status|logs|restart

说明：
  - 环境变量保存在 ~/.dam-local.env（权限 600），不要提交 git；
  - 仅管理本脚本启动的进程（PID 在 /tmp/dam-local/pids），不会动 systemd；
  - 默认连接现有内网库；建议使用测试库/快照，避免误写生产数据。
USAGE
  exit 0
}

for arg in "$@"; do
  case "$arg" in
    --help|-h) usage ;;
    --no-build) NO_BUILD=1 ;;
    --migrate) MIGRATE=1 ;;
    --yes|-y) YES=1 ;;
    --no-open) OPEN_BROWSER=0 ;;
    --setup) ACTION="setup" ;;
    start|stop|status|logs|restart|setup) ACTION="$arg" ;;
    *) die "未知参数：$arg（用 --help 查看）" ;;
  esac
done

# ---------------------------------------------------------------------------
# 环境变量
# ---------------------------------------------------------------------------
qq() { printf '%q' "$1"; }

setup_env() {
  if [ -f "$ENV_FILE" ] && [ "$ACTION" != "setup" ]; then
    return 0
  fi
  if [ -f "$ENV_FILE" ]; then
    warn "已存在 $ENV_FILE"
    read -r -p "覆盖重新生成？[y/N] " ans
    [ "$ans" = "y" ] || [ "$ans" = "Y" ] || return 0
  fi
  [ -t 0 ] || die "首次运行需要交互输入数据库信息，请在终端执行"

  local db_host db_port db_name db_user db_pass jwt chat_key code_key
  read -r -p "数据库 host [10.162.3.16]: " db_host; db_host="${db_host:-10.162.3.16}"
  read -r -p "数据库 port [11001]: " db_port; db_port="${db_port:-11001}"
  read -r -p "数据库名 [damplatform]: " db_name; db_name="${db_name:-damplatform}"
  read -r -p "数据库用户 [dam]: " db_user; db_user="${db_user:-dam}"
  read -r -s -p "数据库口令: " db_pass; echo
  [ -n "$db_pass" ] || die "数据库口令不能为空"

  if command -v openssl >/dev/null 2>&1; then
    jwt="$(openssl rand -base64 48 | tr -d '\n')"
  else
    jwt="$(head -c 48 /dev/urandom | base64 | tr -d '\n')"
  fi

  read -r -p "LLM Chat API Key（可留空，AI 功能不可用）: " chat_key
  read -r -p "LLM Code API Key（可留空）: " code_key

  {
    echo "# 本地开发环境变量（自动生成，不要提交 git）"
    echo "# 生成时间：$(date '+%Y-%m-%d %H:%M:%S')"
    echo "export TZ=Asia/Shanghai"
    echo "export SPRING_PROFILES_ACTIVE=dev"
    echo "export DAM_DB_HOST=$(qq "$db_host")"
    echo "export DAM_DB_PORT=$(qq "$db_port")"
    echo "export DAM_DB_NAME=$(qq "$db_name")"
    echo "export DAM_DB_USER=$(qq "$db_user")"
    echo "export DAM_DB_PASSWORD=$(qq "$db_pass")"
    echo "export SPRING_DATASOURCE_URL=$(qq "jdbc:postgresql://$db_host:$db_port/$db_name")"
    echo "export SPRING_DATASOURCE_USERNAME=$(qq "$db_user")"
    echo "export SPRING_DATASOURCE_PASSWORD=$(qq "$db_pass")"
    echo "export SA_TOKEN_JWT_SECRET_KEY=$(qq "$jwt")"
    echo "export DAM_SECURITY_CRYPTO_SECRET='dam-platform-crypto-secret-2026'"
    echo "export DAM_CORS_ORIGINS='http://*:3000'"
    echo "export SPRING_CLOUD_NACOS_DISCOVERY_ENABLED=false"
    echo "export SPRING_CLOUD_NACOS_CONFIG_ENABLED=false"
    echo "export DAM_ROUTE_AUTH=http://127.0.0.1:8081"
    echo "export DAM_ROUTE_CATALOG=http://127.0.0.1:8092"
    echo "export DAM_ROUTE_LINEAGE=http://127.0.0.1:8093"
    echo "export DAM_ROUTE_STANDARD=http://127.0.0.1:8084"
    echo "export DAM_ROUTE_QUALITY=http://127.0.0.1:8085"
    echo "export DAM_ROUTE_SECURITY=http://127.0.0.1:8086"
    echo "export DAM_ROUTE_LIFECYCLE=http://127.0.0.1:8087"
    echo "export DAM_ROUTE_OPERATIONS=http://127.0.0.1:8088"
    echo "export DAM_ROUTE_AGENT=http://127.0.0.1:8089"
    echo "export DAM_LINEAGE_URL=http://127.0.0.1:8093"
    echo "export DAM_AGENT_URL=http://127.0.0.1:8089"
    [ -n "$chat_key" ] && echo "export DAM_AGENT_CHAT_API_KEY=$(qq "$chat_key")"
    [ -n "$code_key" ] && echo "export DAM_AGENT_CODE_API_KEY=$(qq "$code_key")"
  } > "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  msg "环境变量已写入 $ENV_FILE"
}

validate_env() {
  [ -f "$ENV_FILE" ] || die "缺少 $ENV_FILE，请运行：$0 --setup"
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  [ -n "${SPRING_DATASOURCE_PASSWORD:-}" ] || die "SPRING_DATASOURCE_PASSWORD 未设置"
  [ -n "${SA_TOKEN_JWT_SECRET_KEY:-}" ] || die "SA_TOKEN_JWT_SECRET_KEY 未设置"
  [ "${#SA_TOKEN_JWT_SECRET_KEY}" -ge 32 ] || die "SA_TOKEN_JWT_SECRET_KEY 至少 32 个字符"
  export DAM_CORS_ORIGINS="${DAM_CORS_ORIGINS:-http://*:3000}"
}

check_tools() {
  command -v java >/dev/null 2>&1 || die "未找到 java，请安装 JDK 21"
  local major
  major="$(java -version 2>&1 | awk -F'[".]' '/version/{print $2; exit}')"
  [ "$major" = "21" ] || die "需要 JDK 21，当前 java 主版本：$major"
  command -v mvn >/dev/null 2>&1 || die "未找到 mvn，请安装 Maven"
  command -v node >/dev/null 2>&1 || die "未找到 node，请安装 Node.js 20+"
  local nmajor
  nmajor="$(node -v | sed 's/^v//' | cut -d. -f1)"
  [ "$nmajor" -ge 20 ] 2>/dev/null || die "需要 Node.js 20+，当前：$(node -v)"
  command -v npm >/dev/null 2>&1 || die "未找到 npm"
  command -v curl >/dev/null 2>&1 || die "未找到 curl"
}

port_for() {
  case "$1" in
    gateway) echo 8080 ;; auth) echo 8081 ;; catalog) echo 8092 ;; lineage) echo 8093 ;;
    standard) echo 8084 ;; quality) echo 8085 ;; security) echo 8086 ;;
    lifecycle) echo 8087 ;; operations) echo 8088 ;; agent) echo 8089 ;;
    *) echo 0 ;;
  esac
}

port_in_use() {
  local p="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"$p" -sTCP:LISTEN >/dev/null 2>&1
  elif command -v ss >/dev/null 2>&1; then
    ss -lnt 2>/dev/null | awk '{print $4}' | grep -q ":$p$"
  else
    (exec 3<>"/dev/tcp/127.0.0.1/$p") >/dev/null 2>&1
  fi
}

wait_http() {
  local url="$1" timeout="${2:-120}" i code
  for ((i=0; i<timeout; i+=2)); do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 "$url" || true)"
    [ "$code" = "200" ] && return 0
    sleep 2
  done
  return 1
}

pid_alive() { [ -f "$1" ] && kill -0 "$(cat "$1" 2>/dev/null)" 2>/dev/null; }

stop_pid() {
  local f="$1" pid i
  [ -f "$f" ] || return 0
  pid="$(cat "$f" 2>/dev/null || true)"
  [ -n "$pid" ] || { rm -f "$f"; return 0; }
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
    for ((i=0; i<10; i++)); do
      kill -0 "$pid" 2>/dev/null || break
      sleep 0.3
    done
    kill -9 "$pid" 2>/dev/null || true
  fi
  rm -f "$f"
}

stop_all() {
  stop_pid "$PID_DIR/frontend.pid"
  local s
  for s in "${SERVICES[@]}"; do
    stop_pid "$PID_DIR/dam-$s.pid"
  done
  msg "已停止本脚本启动的进程"
}

status_all() {
  local s p pid state port_state
  printf '%-14s %-6s %-8s %-10s %s\n' "服务" "端口" "进程" "监听" "HTTP"
  for s in "${SERVICES[@]}"; do
    p="$(port_for "$s")"
    pid="未运行"
    pid_alive "$PID_DIR/dam-$s.pid" && pid="运行中"
    port_state="未监听"
    port_in_use "$p" && port_state="已监听"
    local code="-"
    if [ "$s" = "gateway" ]; then
      code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 "http://127.0.0.1:$p/api/auth/public-key" || true)"
    fi
    printf '%-14s %-6s %-8s %-10s %s\n' "dam-$s" "$p" "$pid" "$port_state" "$code"
  done
  local fcode
  fcode="$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 "http://127.0.0.1:$FRONTEND_PORT/" || true)"
  printf '%-14s %-6s %-8s %-10s %s\n' "frontend" "$FRONTEND_PORT" "$(pid_alive "$PID_DIR/frontend.pid" && echo 运行中 || echo 未运行)" "$(port_in_use "$FRONTEND_PORT" && echo 已监听 || echo 未监听)" "$fcode"
  echo
  echo "日志：$LOG_DIR/"
}

build_backend() {
  if [ "$NO_BUILD" = "1" ]; then
    msg "跳过 Maven 打包（--no-build）"
  else
    msg "开始 Maven 打包（首次可能较慢）..."
    ( cd "$BACKEND" && mvn -DskipTests package )
  fi
  local s
  for s in "${SERVICES[@]}"; do
    [ -f "$BACKEND/dam-$s/target/dam-$s-1.0.0-exec.jar" ] || die "缺少 jar：dam-$s，请先打包"
  done
}

check_schema() {
  command -v psql >/dev/null 2>&1 || { warn "未找到 psql，跳过数据库 schema 检查"; return 0; }
  export PGPASSWORD="${DAM_DB_PASSWORD:-$SPRING_DATASOURCE_PASSWORD}"
  local layer ai
  layer="$(psql -h "${DAM_DB_HOST:-127.0.0.1}" -p "${DAM_DB_PORT:-11001}" -U "${DAM_DB_USER:-dam}" \
    -d "${DAM_DB_NAME:-damplatform}" -tAc \
    "SELECT count(*) FROM information_schema.columns WHERE table_schema='dam_metadata' AND table_name='metadata_table' AND column_name='layer'" 2>/dev/null || echo 0)"
  ai="$(psql -h "${DAM_DB_HOST:-127.0.0.1}" -p "${DAM_DB_PORT:-11001}" -U "${DAM_DB_USER:-dam}" \
    -d "${DAM_DB_NAME:-damplatform}" -tAc "SELECT to_regclass('dam_asset.ai_task')" 2>/dev/null || true)"
  [ "${layer:-0}" != "0" ] || warn "数据库缺少 v4 血缘层级列：请执行 db/v4_lineage_layers.sql（或用 --migrate）"
  [ -n "${ai:-}" ] || warn "数据库缺少 v5 AI 任务表：请执行 db/v5_ai_tasks.sql（或用 --migrate）"
  unset PGPASSWORD
}

run_migrations() {
  [ "$MIGRATE" = "1" ] || return 0
  command -v psql >/dev/null 2>&1 || die "未找到 psql，无法执行迁移"
  [ -n "${DAM_DB_HOST:-}" ] || die "未设置 DAM_DB_HOST，无法迁移"
  warn "即将向 ${DAM_DB_HOST}:${DAM_DB_PORT}/${DAM_DB_NAME} 执行 v4、v5、v7 迁移"
  if [ "$YES" != "1" ]; then
    [ -t 0 ] || die "非交互模式请加 --yes"
    read -r -p "确认执行？输入 yes 继续：" ans
    [ "$ans" = "yes" ] || die "已取消"
  fi
  export PGPASSWORD="$DAM_DB_PASSWORD"
  psql -h "$DAM_DB_HOST" -p "$DAM_DB_PORT" -U "$DAM_DB_USER" -d "$DAM_DB_NAME" -v ON_ERROR_STOP=1 -f "$DB_DIR/v4_lineage_layers.sql"
  psql -h "$DAM_DB_HOST" -p "$DAM_DB_PORT" -U "$DAM_DB_USER" -d "$DAM_DB_NAME" -v ON_ERROR_STOP=1 -f "$DB_DIR/v5_ai_tasks.sql"
  psql -h "$DAM_DB_HOST" -p "$DAM_DB_PORT" -U "$DAM_DB_USER" -d "$DAM_DB_NAME" -v ON_ERROR_STOP=1 -f "$DB_DIR/v7_lifecycle_execution.sql"
  unset PGPASSWORD
  msg "v4/v5/v7 迁移完成（v6 请按 db/v6_lineage_identity_README.md 单独执行）"
}

start_backend() {
  if ls "$PID_DIR"/dam-*.pid "$PID_DIR"/frontend.pid >/dev/null 2>&1; then
    warn "检测到上次启动的进程，先停止..."
    stop_all
  fi
  local s p
  for s in "${SERVICES[@]}"; do
    p="$(port_for "$s")"
    if port_in_use "$p"; then
      die "端口 $p 已被占用（dam-$s），请先停止占用进程：lsof -iTCP:$p -sTCP:LISTEN"
    fi
  done

  for s in "${SERVICES[@]}"; do
    msg "启动 dam-$s ..."
    nohup java -Xmx512m -jar "$BACKEND/dam-$s/target/dam-$s-1.0.0-exec.jar" \
      > "$LOG_DIR/dam-$s.log" 2>&1 &
    echo $! > "$PID_DIR/dam-$s.pid"
    sleep 2
    if ! pid_alive "$PID_DIR/dam-$s.pid"; then
      err "dam-$s 启动失败，最近日志："
      tail -n 30 "$LOG_DIR/dam-$s.log" || true
      stop_all
      exit 1
    fi
  done

  msg "等待网关就绪 ..."
  if ! wait_http "http://127.0.0.1:8080/api/auth/public-key" 120; then
    err "网关未在 120 秒内就绪，最近日志："
    tail -n 40 "$LOG_DIR/dam-gateway.log" || true
    tail -n 20 "$LOG_DIR/dam-auth.log" || true
    stop_all
    exit 1
  fi
}

start_frontend() {
  if port_in_use "$FRONTEND_PORT"; then
    die "端口 $FRONTEND_PORT 已被占用，请先停止占用进程：lsof -iTCP:$FRONTEND_PORT -sTCP:LISTEN"
  fi
  if [ ! -f "$FRONTEND/node_modules/vite/bin/vite.js" ]; then
    msg "安装前端依赖 ..."
    ( cd "$FRONTEND" && npm ci --cache "$RUN_DIR/npm-cache" ) || \
      ( cd "$FRONTEND" && npm install --cache "$RUN_DIR/npm-cache" )
  fi
  msg "启动前端 Vite ..."
  nohup node "$FRONTEND/node_modules/vite/bin/vite.js" --host 0.0.0.0 --port "$FRONTEND_PORT" --strictPort \
    > "$LOG_DIR/frontend.log" 2>&1 &
  echo $! > "$PID_DIR/frontend.pid"
  sleep 2
  if ! pid_alive "$PID_DIR/frontend.pid"; then
    err "前端启动失败，最近日志："
    tail -n 40 "$LOG_DIR/frontend.log" || true
    stop_all
    exit 1
  fi
  if ! wait_http "http://127.0.0.1:$FRONTEND_PORT/" 60; then
    err "前端未在 60 秒内就绪，请查看 $LOG_DIR/frontend.log"
    stop_all
    exit 1
  fi
}

open_browser() {
  [ "$OPEN_BROWSER" = "1" ] || return 0
  if command -v open >/dev/null 2>&1; then
    open "http://localhost:$FRONTEND_PORT" >/dev/null 2>&1 || true
  elif command -v xdg-open >/dev/null 2>&1 && { [ -n "${DISPLAY:-}" ] || [ -n "${WAYLAND_DISPLAY:-}" ]; }; then
    xdg-open "http://localhost:$FRONTEND_PORT" >/dev/null 2>&1 || true
  fi
}

start_all() {
  setup_env
  validate_env
  check_tools
  run_migrations
  check_schema
  build_backend
  start_backend
  start_frontend

  echo
  msg "本地环境已启动"
  echo "  前端：http://localhost:$FRONTEND_PORT"
  echo "  网关：http://127.0.0.1:8080"
  echo "  演示账号：admin / admin123（以实际库为准）"
  echo "  日志：$LOG_DIR/"
  echo "  停止：$0 stop"
  echo "  状态：$0 status"
  open_browser
}

case "$ACTION" in
  setup) setup_env; msg "配置完成：$ENV_FILE" ;;
  start) start_all ;;
  restart) stop_all; start_all ;;
  stop) stop_all ;;
  status) status_all ;;
  logs) ls -1 "$LOG_DIR"/*.log 2>/dev/null || true; echo; tail -n 80 "$LOG_DIR"/*.log 2>/dev/null || true ;;
  *) usage ;;
esac
