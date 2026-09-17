#!/usr/bin/env bash
set -euo pipefail

# 以脚本所在目录为部署目录（整个 agent 便携运行，不依赖 /opt、systemd、sudo）
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$SCRIPT_DIR"

BIN="$SCRIPT_DIR/deploy-agent"
CONF="$SCRIPT_DIR/config.yaml"
PID_FILE="$SCRIPT_DIR/agent.pid"
OUT_FILE="$SCRIPT_DIR/agent.out"

if [ ! -x "$BIN" ]; then
  echo "错误: 找不到可执行文件 $BIN（请先下载二进制并 chmod +x）"
  exit 1
fi
if [ ! -f "$CONF" ]; then
  echo "错误: 找不到配置文件 $CONF"
  exit 1
fi

# 单实例保护：PID 存活则拒绝重复启动；陈旧 PID 文件自动清理
if [ -f "$PID_FILE" ]; then
  OLD_PID=$(cat "$PID_FILE")
  if kill -0 "$OLD_PID" 2>/dev/null; then
    echo "agent 已在运行 (PID $OLD_PID)，无需重复启动"
    exit 0
  fi
  echo "发现陈旧 PID 文件，已清理"
  rm -f "$PID_FILE"
fi

nohup "$BIN" -c "$CONF" >> "$OUT_FILE" 2>&1 &
PID=$!
echo "$PID" > "$PID_FILE"
sleep 1

if kill -0 "$PID" 2>/dev/null; then
  echo "agent 已启动 (PID $PID)"
  echo "启动输出:   $OUT_FILE"
  echo "运行时日志: $SCRIPT_DIR/log/agent-$(date +%F).log"
  echo "健康检查:   curl http://127.0.0.1:$(awk '/^port:/{gsub(/[^0-9]/,"",$2);print $2}' "$CONF" | head -n1)/api/health"
else
  echo "错误: agent 启动失败，请查看 $OUT_FILE"
  rm -f "$PID_FILE"
  exit 1
fi
