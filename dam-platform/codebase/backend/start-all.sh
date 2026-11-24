#!/bin/bash
# 启动所有后端服务
BACKEND_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$BACKEND_DIR/logs"
mkdir -p "$LOG_DIR"
if [ -z "${SA_TOKEN_JWT_SECRET_KEY:-}" ]; then
  echo "WARN: SA_TOKEN_JWT_SECRET_KEY 未设置，服务会启动失败；请先 source /etc/dam/env.conf 或 export 该变量"
fi

SERVICES=(
  "platform-gateway:8080"
  "platform-auth:8081"
  "asset-catalog:8092"
  "governance-lineage:8093"
  "governance-standard:8084"
  "governance-quality:8085"
  "governance-security:8086"
  "governance-lifecycle:8087"
  "platform-operations:8088"
  "ai-model-gateway:8089"
  "ai-workbench:8090"
  "access-integration:8095"
  "platform-scheduler:8096"
  "service-api:8097"
  "platform-portal:8091"
  "analytics-metric:8094"
  "ai-knowledge:8098"
  "platform-task-center:8099"
  "processing-develop:8100"
)

for svc in "${SERVICES[@]}"; do
  name="${svc%%:*}"
  port="${svc##*:}"
  echo "Starting $name on port $port..."
  cd "$BACKEND_DIR/$name"
  nohup mvn spring-boot:run -q > "$LOG_DIR/$name.log" 2>&1 &
  echo "  PID: $!"
  sleep 2
done

echo ""
echo "All services started. Check logs in $LOG_DIR"
echo "Gateway: http://localhost:8080"
echo "Frontend: http://localhost:3000"
