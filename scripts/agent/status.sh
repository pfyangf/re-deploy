#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PID_FILE="$SCRIPT_DIR/agent.pid"
CONF="$SCRIPT_DIR/config.yaml"

if [ ! -f "$PID_FILE" ]; then
  echo "未运行（无 PID 文件）"
  exit 1
fi

PID=$(cat "$PID_FILE")
if ! kill -0 "$PID" 2>/dev/null; then
  echo "未运行（PID 文件陈旧，PID $PID 已不存在），可执行 ./start.sh 启动"
  exit 1
fi

PORT=$(awk '/^port:/{gsub(/[^0-9]/, "", $2); print $2; exit}' "$CONF" 2>/dev/null || true)
PORT=${PORT:-9009}

echo "运行中 (PID $PID, 端口 $PORT)"
if curl -fsS "http://127.0.0.1:$PORT/api/health" >/dev/null 2>&1; then
  echo "健康检查: 正常"
else
  echo "健康检查: 失败（进程存活但端口无响应）"
  exit 1
fi
