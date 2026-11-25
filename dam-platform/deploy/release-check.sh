#!/usr/bin/env bash
#
# 最小发版门禁（默认只做静态检查，不重复 CI 已完成的构建）
#
# 用法：
#   bash deploy/release-check.sh          # 静态检查
#   bash deploy/release-check.sh --build  # 额外执行后端 Maven package 与前端构建
#
# 任一项检查失败都会输出 [FAIL] 明细并以退出码 1 结束。

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$REPO_ROOT/codebase/backend"
FRONTEND_DIR="$REPO_ROOT/codebase/frontend"

# 阶段 1 新增的后端模块（目录名与微服务名一致）
MODULES=(
  platform-portal
  analytics-metric
  ai-knowledge
  access-integration
  platform-scheduler
  service-api
  ai-workbench
  platform-task-center
  processing-develop
)

# 数据库增量脚本主版本号
DB_VERSIONS=(21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44)

# 门户中已补齐、不应再标记为 PLANNED 的模块
RELEASED_MODULES=(KNO DS SCH INT DEV)

BUILD=0
FAIL_COUNT=0
FAIL_MESSAGES=()

usage() {
  cat <<'EOF'
用法: bash deploy/release-check.sh [--build]

  --build   额外执行 Maven package（含测试）与前端安装/类型检查/构建
  -h,--help 显示本帮助
EOF
}

section() {
  printf '\n== %s ==\n' "$1"
}

ok() {
  printf '  [ OK ] %s\n' "$1"
}

fail() {
  printf '  [FAIL] %s\n' "$1"
  FAIL_COUNT=$((FAIL_COUNT + 1))
  FAIL_MESSAGES+=("$1")
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --build)
      BUILD=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      printf '未知参数: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

printf '仓库根目录: %s\n' "$REPO_ROOT"
printf '模式: %s\n' "$([ "$BUILD" -eq 1 ] && echo '静态检查 + 构建' || echo '静态检查')"

# ---------------------------------------------------------------------------
# 1. 后端模块目录
# ---------------------------------------------------------------------------
section "1/5 后端模块目录"
for module in "${MODULES[@]}"; do
  if [ -d "$BACKEND_DIR/$module" ]; then
    ok "后端模块存在: codebase/backend/$module"
  else
    fail "缺少后端模块目录: codebase/backend/$module"
  fi
done

# 后端门户 PLANNED 集合必须与前端一致：只剩 MDM/DEV/STO/MKT/OBS
if [ -f "$BACKEND_DIR/platform-portal/src/main/java/com/dam/portal/service/PortalCatalogService.java" ]; then
  if grep -q 'Set.of("MDM", "STO", "MKT", "OBS")' "$BACKEND_DIR/platform-portal/src/main/java/com/dam/portal/service/PortalCatalogService.java"; then
    ok "后端门户 PLANNED 集合已同步"
  else
    fail "后端 PortalCatalogService PLANNED 集合未同步（应为 MDM/DEV/STO/MKT/OBS）"
  fi
fi

# ---------------------------------------------------------------------------
# 2. Docker Compose 服务名
# ---------------------------------------------------------------------------
section "2/5 Docker Compose 服务"
for compose_file in docker-compose.app.yml docker-compose.backend.yml; do
  compose_path="$REPO_ROOT/deploy/$compose_file"
  if [ ! -f "$compose_path" ]; then
    fail "缺少 Compose 文件: deploy/$compose_file"
    continue
  fi

  for service in "${MODULES[@]}"; do
    # 只匹配 services: 下两空格缩进的服务键，避免命中镜像名/容器名/注释
    if grep -qE "^  ${service}:[[:space:]]*$" "$compose_path"; then
      ok "deploy/$compose_file 包含服务 $service"
    else
      fail "deploy/$compose_file 缺少服务 $service"
    fi
  done
done

# ---------------------------------------------------------------------------
# 3. 前端遗留路由
# ---------------------------------------------------------------------------
section "3/5 前端遗留路由"
if [ ! -d "$FRONTEND_DIR/src" ]; then
  fail "缺少前端源码目录: codebase/frontend/src"
else
  for legacy in /catalog/metrics /catalog/knowledge; do
    matches="$(grep -rnF -- "$legacy" "$FRONTEND_DIR/src" 2>/dev/null || true)"
    if [ -n "$matches" ]; then
      fail "前端源码仍包含遗留路由 $legacy"
      printf '%s\n' "$matches" | head -10 | sed 's/^/         /'
      extra="$(printf '%s\n' "$matches" | wc -l | tr -d ' ')"
      if [ "$extra" -gt 10 ]; then
        printf '         ... 其余 %s 处未展开\n' "$((extra - 10))"
      fi
    else
      ok "前端源码无 $legacy 残留"
    fi
  done
fi

# ---------------------------------------------------------------------------
# 4. 数据库增量脚本
# ---------------------------------------------------------------------------
section "4/5 数据库增量脚本"
shopt -s nullglob
for version in "${DB_VERSIONS[@]}"; do
  files=( "$REPO_ROOT"/db/v"${version}"_*.sql )
  if [ "${#files[@]}" -gt 0 ]; then
    ok "数据库脚本存在: db/v${version}_*.sql"
  else
    fail "缺少数据库脚本: db/v${version}_*.sql"
  fi
done
shopt -u nullglob

# ---------------------------------------------------------------------------
# 5. 门户状态：已补齐模块不应再标记 PLANNED
# ---------------------------------------------------------------------------
section "5/5 门户模块状态"
PORTAL_TS="$FRONTEND_DIR/src/config/portal.ts"
if [ ! -f "$PORTAL_TS" ]; then
  ok "portal.ts 不存在，跳过模块状态检查"
else
  for code in "${RELEASED_MODULES[@]}"; do
    # 模块块内 status 位于 code 之后约 5 行；取 8 行窗口，避免跨入下一模块
    if grep -A 8 -F "code: '${code}'" "$PORTAL_TS" 2>/dev/null \
        | grep -qF "status: 'PLANNED'"; then
      fail "portal.ts 仍将模块 ${code} 标记为 PLANNED（应为 AVAILABLE）"
    else
      ok "portal.ts 中模块 ${code} 未标记为 PLANNED"
    fi
  done
fi

# ---------------------------------------------------------------------------
# 可选构建（默认不执行，避免与 CI 的显式构建步骤重复）
# ---------------------------------------------------------------------------
if [ "$BUILD" -eq 1 ]; then
  section "可选构建"
  LOG_DIR="$REPO_ROOT/.release-check-logs"
  mkdir -p "$LOG_DIR"

  if ! command -v mvn >/dev/null 2>&1; then
    fail "未找到 mvn，无法执行 --build 后端构建"
  else
    printf '  [RUN ] 后端: mvn -B -DskipTests=false package\n'
    if (
      cd "$BACKEND_DIR" &&
      set -o pipefail &&
      mvn -B -DskipTests=false package 2>&1 | tee "$LOG_DIR/backend-build.log"
    ); then
      ok "后端 Maven package 成功"
    else
      fail "后端 Maven package 失败，日志: $LOG_DIR/backend-build.log"
    fi
  fi

  if ! command -v npm >/dev/null 2>&1; then
    fail "未找到 npm，无法执行 --build 前端构建"
  elif [ ! -d "$FRONTEND_DIR" ]; then
    fail "缺少前端目录: codebase/frontend"
  else
    printf '  [RUN ] 前端: npm ci/install + vue-tsc --noEmit + npm run build\n'
    if (
      cd "$FRONTEND_DIR" &&
      set -o pipefail &&
      {
        if [ -f package-lock.json ]; then
          npm ci
        else
          npm install
        fi
        npx vue-tsc --noEmit
        npm run build
      } 2>&1 | tee "$LOG_DIR/frontend-build.log"
    ); then
      ok "前端类型检查与构建成功"
    else
      fail "前端类型检查或构建失败，日志: $LOG_DIR/frontend-build.log"
    fi
  fi
fi

# ---------------------------------------------------------------------------
# 汇总
# ---------------------------------------------------------------------------
printf '\n================ 发版门禁结果 ================\n'
if [ "$FAIL_COUNT" -eq 0 ]; then
  printf '全部检查通过，可以进入发版流程。\n'
  exit 0
fi

printf '失败项: %s\n' "$FAIL_COUNT"
for message in "${FAIL_MESSAGES[@]}"; do
  printf '  - %s\n' "$message"
done
printf '静态门禁未通过，禁止发版。\n'
exit 1
