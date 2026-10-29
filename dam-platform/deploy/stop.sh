#!/bin/bash
# 停止全部后端服务（传统方式）
# 用法：bash deploy/stop.sh
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/../codebase/backend" && pwd)"
LOG_DIR="${LOG_DIR:-$BACKEND_DIR/logs}"

stopped=0
for pidfile in "$LOG_DIR"/*.pid; do
  [ -f "$pidfile" ] || continue
  name="$(basename "$pidfile" .pid)"
  pid="$(cat "$pidfile" 2>/dev/null || true)"
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
    echo "[停止] $name (PID $pid)"
    kill "$pid" 2>/dev/null || true
    stopped=$((stopped + 1))
  else
    echo "[跳过] $name（进程不存在）"
  fi
  rm -f "$pidfile"
done

# 等待进程退出
sleep 2
echo "已停止 $stopped 个服务"
