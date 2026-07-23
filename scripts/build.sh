#!/usr/bin/env bash
###############################################################################
# scripts/build.sh — 本地构建脚本（不推送、不改 git）
#
# 用途：
#   1. 交叉编译 agent（linux/amd64 + linux/arm64）到 server/data/agents/
#   2. mvn package 生成 server jar
#   3. docker buildx build 生成本地 image（默认 --load 单平台，供本地验证）
#
# 参数（与 scripts/build.ps1 保持一致，改动请两边同步）：
#   --version X.Y.Z        显式指定镜像 tag 版本（默认读 pom 去掉 -SNAPSHOT）
#   --no-agent             跳过 agent 交叉编译（用于已就绪场景）
#   --platforms P[,P...]   docker 构建目标平台，默认 linux/amd64
#   --push                 推送镜像到 Docker Hub（供 release.sh 调用；直接用不推荐）
#   -h, --help             显示帮助
#
# 版本号真源：server/pom.xml <version>
###############################################################################
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_REPO="pengfei2022/redeploy-server"

VERSION=""
BUILD_AGENT=1
PLATFORMS="linux/amd64"
PUSH=0

usage() {
  grep -E '^#( |$)' "$0" | sed 's/^# \{0,1\}//'
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)    VERSION="$2"; shift 2 ;;
    --no-agent)   BUILD_AGENT=0; shift ;;
    --platforms)  PLATFORMS="$2"; shift 2 ;;
    --push)       PUSH=1; shift ;;
    -h|--help)    usage ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done

log()  { printf '\033[1;36m[build]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[build]\033[0m %s\n' "$*"; }
err()  { printf '\033[1;31m[build]\033[0m %s\n' "$*" >&2; }

# ---- 依赖检查 -------------------------------------------------------------
for cmd in mvn go docker; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    err "缺少依赖：$cmd（请先安装）"
    exit 1
  fi
done
if ! docker buildx version >/dev/null 2>&1; then
  err "docker buildx 未就绪。请执行："
  err "  docker buildx create --use --name redeploy-builder"
  exit 1
fi

# ---- 版本决定 -------------------------------------------------------------
if [[ -z "$VERSION" ]]; then
  RAW=$(mvn -f "$REPO_ROOT/server/pom.xml" -q help:evaluate -Dexpression=project.version -DforceStdout)
  VERSION="${RAW%-SNAPSHOT}"
fi
log "目标版本: $VERSION"
log "目标平台: $PLATFORMS"
log "推送镜像: $([[ $PUSH -eq 1 ]] && echo yes || echo no)"

# ---- Agent 交叉编译 -------------------------------------------------------
AGENT_DIR="$REPO_ROOT/server/data/agents"
mkdir -p "$AGENT_DIR"

if [[ $BUILD_AGENT -eq 1 ]]; then
  log "清理旧 agent 二进制"
  rm -f "$AGENT_DIR"/deploy-agent-*

  for ARCH in amd64 arm64; do
    log "交叉编译 agent: linux/$ARCH"
    (
      cd "$REPO_ROOT/agent"
      GOOS=linux GOARCH="$ARCH" CGO_ENABLED=0 \
        go build -trimpath -ldflags="-s -w" \
        -o "$AGENT_DIR/deploy-agent-linux-$ARCH" ./cmd/agent
    )
  done
else
  log "跳过 agent 交叉编译（--no-agent）"
  for ARCH in amd64 arm64; do
    if [[ ! -f "$AGENT_DIR/deploy-agent-linux-$ARCH" ]]; then
      err "缺少 $AGENT_DIR/deploy-agent-linux-$ARCH（--no-agent 需要预先放置）"
      exit 1
    fi
  done
fi

# ---- Maven 打包 -----------------------------------------------------------
log "mvn package"
mvn -f "$REPO_ROOT/server/pom.xml" -B -DskipTests clean package

# ---- Docker 构建 ----------------------------------------------------------
JAR_COUNT=$(ls "$REPO_ROOT/server/target/"redeploy-server-*.jar 2>/dev/null | wc -l | tr -d ' ')
if [[ "$JAR_COUNT" -ne 1 ]]; then
  err "server/target/ 下期望 1 个 redeploy-server-*.jar，实际 $JAR_COUNT"
  exit 1
fi

TAG_VERSION="$IMAGE_REPO:$VERSION"
TAG_LATEST="$IMAGE_REPO:latest"

BUILDX_ARGS=(
  buildx build
  --platform "$PLATFORMS"
  -t "$TAG_VERSION"
  -t "$TAG_LATEST"
  -f "$REPO_ROOT/Dockerfile"
)

if [[ $PUSH -eq 1 ]]; then
  BUILDX_ARGS+=(--push)
else
  # --load 只支持单平台
  if [[ "$PLATFORMS" == *","* ]]; then
    warn "多平台构建且未 --push，切换到 --output=type=cacheonly（本地不 load）"
    BUILDX_ARGS+=(--output "type=cacheonly")
  else
    BUILDX_ARGS+=(--load)
  fi
fi

log "docker ${BUILDX_ARGS[*]} $REPO_ROOT"
docker "${BUILDX_ARGS[@]}" "$REPO_ROOT"

log "完成: $TAG_VERSION"
if [[ $PUSH -eq 0 && "$PLATFORMS" != *","* ]]; then
  log "本地验证: docker run --rm -p 9006:9006 -e REDEPLOY_ADMIN_TOKEN=test $TAG_VERSION"
fi
