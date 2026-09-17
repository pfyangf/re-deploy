#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PID_FILE="$SCRIPT_DIR/agent.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "agent 未运行（无 PID 文件）"
  exit 0
fi

PID=$(cat "$PID_FILE")
if ! kill -0 "$PID" 2>/dev/null; then
  echo "进程 $PID 已不存在，清理陈旧 PID 文件"
  rm -f "$PID_FILE"
  exit 0
fi

echo "正在停止 agent (PID $PID)..."
kill "$PID" 2>/dev/null || true

# 最多等待 10 秒，不自动 kill -9
for _ in $(seq 1 10); do
  if ! kill -0 "$PID" 2>/dev/null; then
    rm -f "$PID_FILE"
    echo "agent 已停止"
    exit 0
  fi
  sleep 1
done

echo "警告: 进程 $PID 在 10 秒内未退出，确认需要强制终止时手工执行: kill -9 $PID"
exit 1
