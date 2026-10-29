#!/bin/bash
# 一键编译：后端可执行 jar + 前端 dist
# 用法：bash deploy/build.sh
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== [1/2] 构建后端（Maven）==="
cd "$ROOT/codebase/backend"
mvn clean package -DskipTests

echo ""
echo "=== [2/2] 构建前端（Vite）==="
cd "$ROOT/codebase/frontend"
npm install
npm run build

echo ""
echo "=== 构建完成 ==="
echo "后端 jar：codebase/backend/<模块>/target/<模块>-1.0.0-exec.jar"
echo "前端 dist：codebase/frontend/dist"
echo ""
echo "下一步（二选一）："
echo "  Docker 部署：cd deploy && docker compose up -d --build"
echo "  传统部署：  bash deploy/start.sh"
