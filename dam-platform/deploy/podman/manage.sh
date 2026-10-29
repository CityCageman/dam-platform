#!/usr/bin/env bash
# ============================================================================
# 生产端 Podman 管理脚本（完全离线，不依赖 docker-compose / podman-compose）
#
# 前置：
#   1) 已把 dist-images/*.tar 与 deploy/podman 目录拷贝到服务器
#   2) cp deploy/podman/dam.env.example deploy/podman/dam.env 并修改（数据库/口令/网关地址）
#
# 常用命令：
#   bash manage.sh load ./dist-images      # 导入镜像（podman load）
#   bash manage.sh start-backend           # 启动 10 个后端服务（含网关 8080）
#   bash manage.sh start-frontend          # 启动前端 Nginx（80）
#   bash manage.sh status                  # 查看容器状态
#   bash manage.sh logs dam-catalog        # 跟踪某个服务日志
#   bash manage.sh stop                    # 停止并移除全部容器
#
# 说明：
#   - 无 Nacos 部署：dam.env 中 NACOS_ENABLED=false，网关自动按容器名路由
#   - rootless podman 绑定 80 端口需：sudo sysctl -w net.ipv4.ip_unprivileged_port_start=80
#   - 开机自启：systemctl enable --now podman-restart.service（podman 的 restart 策略依赖它）
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/dam.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$SCRIPT_DIR/dam.env"
  set +a
fi

VERSION="${VERSION:-1.0.0}"
IMAGE_PREFIX="${IMAGE_PREFIX:-localhost}"
NET="${NET:-dam-net}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-damplatform}"
DB_USER="${DB_USER:-dam}"
DB_PASSWORD="${DB_PASSWORD:-}"
NACOS_ENABLED="${NACOS_ENABLED:-false}"
NACOS_SERVER_ADDR="${NACOS_SERVER_ADDR:-}"
GATEWAY_PORT="${GATEWAY_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-80}"
BACKEND_GATEWAY="${BACKEND_GATEWAY:-dam-gateway:8080}"
DAM_AGENT_CHAT_BASE_URL="${DAM_AGENT_CHAT_BASE_URL:-}"
DAM_AGENT_CHAT_API_KEY="${DAM_AGENT_CHAT_API_KEY:-}"
DAM_AGENT_CHAT_MODEL="${DAM_AGENT_CHAT_MODEL:-}"
DAM_AGENT_CODE_BASE_URL="${DAM_AGENT_CODE_BASE_URL:-}"
DAM_AGENT_CODE_API_KEY="${DAM_AGENT_CODE_API_KEY:-}"
DAM_AGENT_CODE_MODEL="${DAM_AGENT_CODE_MODEL:-}"

SERVICES="auth catalog lineage standard quality security lifecycle operations agent gateway"
DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"

COMMON_ENV=(
  -e "TZ=Asia/Shanghai"
  -e "SPRING_PROFILES_ACTIVE=prod"
  -e "SPRING_DATASOURCE_URL=${DB_URL}"
  -e "SPRING_DATASOURCE_USERNAME=${DB_USER}"
  -e "SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}"
  -e "SPRING_CLOUD_NACOS_DISCOVERY_ENABLED=${NACOS_ENABLED}"
  -e "SPRING_CLOUD_NACOS_CONFIG_ENABLED=${NACOS_ENABLED}"
)
if [ -n "$NACOS_SERVER_ADDR" ]; then
  COMMON_ENV+=(-e "NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR}")
fi

ensure_network() {
  if ! podman network exists "$NET" >/dev/null 2>&1; then
    podman network create "$NET" >/dev/null
    echo "created network: $NET"
  fi
}

load_images() {
  local dir="${1:-$SCRIPT_DIR/dist-images}"
  if [ ! -d "$dir" ]; then echo "ERROR: 目录不存在: $dir"; exit 1; fi
  for f in "$dir"/*.tar; do
    [ -e "$f" ] || continue
    echo ">>> load $f"
    podman load -i "$f"
  done
  echo ">>> 镜像导入完成"
  podman images | grep -E "dam-(auth|catalog|lineage|standard|quality|security|lifecycle|operations|agent|gateway|frontend)" || true
}

start_backend() {
  ensure_network
  for svc in $SERVICES; do
    local name="dam-$svc"
    podman rm -f "$name" >/dev/null 2>&1 || true
    local args=(--name "$name" --network "$NET" --restart unless-stopped)
    local envs=("${COMMON_ENV[@]}")

    case "$svc" in
      catalog)
        envs+=(-e "DAM_LINEAGE_URL=http://dam-lineage:8093" -e "DAM_AGENT_URL=http://dam-agent:8089")
        ;;
      agent)
        envs+=(-e "DAM_AGENT_CHAT_BASE_URL=${DAM_AGENT_CHAT_BASE_URL}"
               -e "DAM_AGENT_CHAT_API_KEY=${DAM_AGENT_CHAT_API_KEY}"
               -e "DAM_AGENT_CHAT_MODEL=${DAM_AGENT_CHAT_MODEL}"
               -e "DAM_AGENT_CODE_BASE_URL=${DAM_AGENT_CODE_BASE_URL}"
               -e "DAM_AGENT_CODE_API_KEY=${DAM_AGENT_CODE_API_KEY}"
               -e "DAM_AGENT_CODE_MODEL=${DAM_AGENT_CODE_MODEL}")
        # 智能体不需要数据库，去掉数据源变量避免误连
        envs=(-e "TZ=Asia/Shanghai" -e "SPRING_PROFILES_ACTIVE=prod"
              -e "SPRING_CLOUD_NACOS_DISCOVERY_ENABLED=${NACOS_ENABLED}"
              -e "SPRING_CLOUD_NACOS_CONFIG_ENABLED=${NACOS_ENABLED}"
              -e "DAM_AGENT_CHAT_BASE_URL=${DAM_AGENT_CHAT_BASE_URL}"
              -e "DAM_AGENT_CHAT_API_KEY=${DAM_AGENT_CHAT_API_KEY}"
              -e "DAM_AGENT_CHAT_MODEL=${DAM_AGENT_CHAT_MODEL}"
              -e "DAM_AGENT_CODE_BASE_URL=${DAM_AGENT_CODE_BASE_URL}"
              -e "DAM_AGENT_CODE_API_KEY=${DAM_AGENT_CODE_API_KEY}"
              -e "DAM_AGENT_CODE_MODEL=${DAM_AGENT_CODE_MODEL}")
        ;;
      gateway)
        args+=(-p "${GATEWAY_PORT}:8080")
        envs+=(-e "DAM_ROUTE_AUTH=http://dam-auth:8081"
               -e "DAM_ROUTE_CATALOG=http://dam-catalog:8092"
               -e "DAM_ROUTE_LINEAGE=http://dam-lineage:8093"
               -e "DAM_ROUTE_STANDARD=http://dam-standard:8084"
               -e "DAM_ROUTE_QUALITY=http://dam-quality:8085"
               -e "DAM_ROUTE_SECURITY=http://dam-security:8086"
               -e "DAM_ROUTE_LIFECYCLE=http://dam-lifecycle:8087"
               -e "DAM_ROUTE_OPERATIONS=http://dam-operations:8088"
               -e "DAM_ROUTE_AGENT=http://dam-agent:8089")
        ;;
    esac

    podman run -d "${args[@]}" "${envs[@]}" "${IMAGE_PREFIX}/dam-${svc}:${VERSION}" >/dev/null
    echo "started $name"
  done
  echo ">>> 后端启动完成，网关端口: ${GATEWAY_PORT}"
}

start_frontend() {
  podman rm -f dam-frontend >/dev/null 2>&1 || true
  local args=(--name dam-frontend --restart unless-stopped -p "${FRONTEND_PORT}:80"
              -e "BACKEND_GATEWAY=${BACKEND_GATEWAY}")
  if podman network exists "$NET" >/dev/null 2>&1; then
    args+=(--network "$NET")
  fi
  podman run -d "${args[@]}" "${IMAGE_PREFIX}/dam-frontend:${VERSION}" >/dev/null
  echo "started dam-frontend，访问端口: ${FRONTEND_PORT}，反代网关: ${BACKEND_GATEWAY}"
}

stop_all() {
  for name in dam-frontend dam-gateway dam-agent dam-operations dam-lifecycle dam-security \
              dam-quality dam-standard dam-lineage dam-catalog dam-auth; do
    if podman container exists "$name" >/dev/null 2>&1; then
      podman rm -f "$name" >/dev/null 2>&1 || true
      echo "removed $name"
    fi
  done
}

status() {
  podman ps -a --filter "name=dam-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
}

case "${1:-}" in
  load)            load_images "${2:-$SCRIPT_DIR/dist-images}" ;;
  start-backend)   start_backend ;;
  start-frontend)  start_frontend ;;
  stop)            stop_all ;;
  status)          status ;;
  logs)            podman logs -f --tail 200 "dam-${2:?用法: manage.sh logs dam-catalog}" ;;
  *)
    echo "用法: bash manage.sh {load [tar-dir]|start-backend|start-frontend|status|logs <svc>|stop}"
    exit 1
    ;;
esac
