#!/usr/bin/env bash
# ============================================================================
# 构建离线镜像包（在有 Docker 或 Podman 的构建机上执行，需可访问依赖仓库）
#
#   bash deploy/podman/build-images.sh
#
# 可选环境变量：
#   ENGINE=docker|podman     容器引擎（默认 podman，未安装则自动用 docker）
#   VERSION=1.0.0            镜像 tag
#   OUT=./dist-images        输出目录
#   SKIP_BUILD=1             跳过 mvn/npm 构建（jar 与 dist 已存在）
#   WITH_MIDDLEWARE=1        同时导出 postgres:15-alpine 与 nacos/nacos-server:v3.1.1
#
# 产物：
#   dist-images/dam-backend-images-<ver>.tar   （10 个后端镜像，一次 load）
#   dist-images/dam-frontend-<ver>.tar          （前端 Nginx 镜像）
#   dist-images/dam-middleware-<ver>.tar        （可选：PG + Nacos）
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT"

VERSION="${VERSION:-1.0.0}"
OUT="${OUT:-$ROOT/dist-images}"
SKIP_BUILD="${SKIP_BUILD:-0}"
WITH_MIDDLEWARE="${WITH_MIDDLEWARE:-0}"

ENGINE="${ENGINE:-}"
if [ -z "$ENGINE" ]; then
  if command -v podman >/dev/null 2>&1; then ENGINE=podman
  elif command -v docker >/dev/null 2>&1; then ENGINE=docker
  else echo "ERROR: 未找到 podman/docker，无法构建镜像"; exit 1; fi
fi
echo ">>> 使用引擎: $ENGINE, 版本: $VERSION, 输出: $OUT"

SERVICES="auth catalog lineage standard quality security lifecycle operations agent gateway"

if [ "$SKIP_BUILD" != "1" ]; then
  echo ">>> [1/4] 构建后端 jar 与前端 dist"
  bash deploy/build.sh
else
  echo ">>> [1/4] 跳过编译（SKIP_BUILD=1）"
fi

echo ">>> [2/4] 构建后端镜像"
for s in $SERVICES; do
  JAR="codebase/backend/dam-$s/target/dam-$s-$VERSION-exec.jar"
  if [ ! -f "$JAR" ]; then echo "ERROR: 缺少 $JAR"; exit 1; fi
  echo "    - dam-$s"
  $ENGINE build -f deploy/podman/Dockerfile.backend \
    --build-arg JAR_FILE="dam-$s/target/dam-$s-$VERSION-exec.jar" \
    --build-arg APP_NAME="dam-$s" \
    -t "localhost/dam-$s:$VERSION" codebase/backend
done

echo ">>> [3/4] 构建前端镜像（内置 dist）"
if [ ! -d codebase/frontend/dist ]; then echo "ERROR: 缺少 codebase/frontend/dist"; exit 1; fi
$ENGINE build -f deploy/podman/Dockerfile.frontend -t "localhost/dam-frontend:$VERSION" .

mkdir -p "$OUT"

echo ">>> [4/4] 导出镜像 tar"
BACKEND_IMAGES=""
for s in $SERVICES; do BACKEND_IMAGES="$BACKEND_IMAGES localhost/dam-$s:$VERSION"; done
# shellcheck disable=SC2086
$ENGINE save -o "$OUT/dam-backend-images-$VERSION.tar" $BACKEND_IMAGES
$ENGINE save -o "$OUT/dam-frontend-$VERSION.tar" "localhost/dam-frontend:$VERSION"

if [ "$WITH_MIDDLEWARE" = "1" ]; then
  echo "    - 拉取并导出中间件镜像"
  $ENGINE pull postgres:15-alpine
  $ENGINE pull nacos/nacos-server:v3.1.1
  $ENGINE save -o "$OUT/dam-middleware-$VERSION.tar" postgres:15-alpine nacos/nacos-server:v3.1.1
fi

echo ""
echo "=== 完成 ==="
ls -lh "$OUT"
echo ""
echo "拷贝到生产环境后执行："
echo "  bash deploy/podman/manage.sh load $OUT"
echo "  cp deploy/podman/dam.env.example deploy/podman/dam.env   # 按实际环境修改"
echo "  bash deploy/podman/manage.sh start-backend"
echo "  bash deploy/podman/manage.sh start-frontend   # 前端服务器执行"
