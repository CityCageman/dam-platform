#!/bin/bash
# 启动所有后端服务
BACKEND_DIR="/Volumes/ExternalHDD/Projects/dam-platform/codebase/backend"
LOG_DIR="$BACKEND_DIR/logs"
mkdir -p "$LOG_DIR"

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
  "dam-agent:8089"
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
