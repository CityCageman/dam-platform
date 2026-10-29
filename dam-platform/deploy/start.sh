#!/bin/bash
# 传统方式启动全部后端服务（非 Docker）
# 前置：先执行 deploy/build.sh 编译出 *-exec.jar
# 用法：bash deploy/start.sh
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/../codebase/backend" && pwd)"
LOG_DIR="${LOG_DIR:-$BACKEND_DIR/logs}"
mkdir -p "$LOG_DIR"

# 可选：加载环境变量文件（数据库/智能体密钥等）
[ -f "$SCRIPT_DIR/.env" ] && set -a && source "$SCRIPT_DIR/.env" && set +a

JAVA_OPTS="${JAVA_OPTS:--Xmx512m -Xms256m -XX:+UseParallelGC}"

# 启动顺序：先起业务服务，最后起网关（网关只验证 token 存在，不强依赖，但放在最后更稳）
SERVICES=(
  "dam-auth:8081"
  "dam-catalog:8092"
  "dam-lineage:8093"
  "dam-standard:8084"
  "dam-quality:8085"
  "dam-security:8086"
  "dam-lifecycle:8087"
  "dam-operations:8088"
  "dam-agent:8089"
  "dam-gateway:8080"
)

for svc in "${SERVICES[@]}"; do
  name="${svc%%:*}"
  port="${svc##*:}"
  jar="$BACKEND_DIR/$name/target/$name-1.0.0-exec.jar"
  if [ ! -f "$jar" ]; then
    echo "[跳过] $name：未找到 $jar（请先执行 deploy/build.sh）"
    continue
  fi
  echo "[启动] $name (端口 $port)"
  nohup java $JAVA_OPTS -jar "$jar" --spring.profiles.active=prod \
    > "$LOG_DIR/$name.log" 2>&1 &
  echo $! > "$LOG_DIR/$name.pid"
  sleep 1
done

echo ""
echo "全部启动完成，日志目录：$LOG_DIR"
echo "网关：http://localhost:8080"
echo "前端请用 Nginx 托管 codebase/frontend/dist 并反代 /api 到 8080（参考 deploy/nginx.conf）"
