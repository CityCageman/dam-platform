#!/bin/bash
# 启动所有后端服务
BACKEND_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$BACKEND_DIR/logs"
mkdir -p "$LOG_DIR"
if [ -z "${SA_TOKEN_JWT_SECRET_KEY:-}" ]; then
  echo "WARN: SA_TOKEN_JWT_SECRET_KEY 未设置，服务会启动失败；请先 source /etc/dam/env.conf 或 export 该变量"
fi

SERVICES=(
  "dam-gateway:8080"
  "dam-auth:8081"
  "dam-catalog:8092"
  "dam-lineage:8093"
  "dam-standard:8084"
  "dam-quality:8085"
  "dam-security:8086"
  "dam-lifecycle:8087"
  "dam-operations:8088"
  "dam-model-gateway:8089"
  "dam-ai-workbench:8090"
  "dam-integration:8095"
  "dam-scheduler:8096"
  "dam-data-service:8097"
  "dam-portal:8091"
  "dam-metric:8094"
  "ai-knowledge:8098"
  "dam-task-center:8099"
  "dam-develop:8100"
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
