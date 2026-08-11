#!/usr/bin/env bash
###############################################################################
# scripts/release.sh — 发版脚本（自动 bump / build / push / commit / tag / push）
#
# 用法：
#   ./scripts/release.sh patch          # 0.1.0-SNAPSHOT → 0.1.0，next 0.1.1-SNAPSHOT
#   ./scripts/release.sh minor          # 0.1.5-SNAPSHOT → 0.2.0，next 0.2.1-SNAPSHOT
#   ./scripts/release.sh major          # 0.2.3-SNAPSHOT → 1.0.0，next 1.0.1-SNAPSHOT
#   ./scripts/release.sh 2.5.0          # 显式版本
#
# 前置条件：
#   - git working tree 干净
#   - docker buildx 已创建并选中
#   - docker login -u pengfei2022 已登录
#   - Go / Maven / Docker 可用
#
# 版本号真源：server/pom.xml <version>；镜像 tag、Git tag 均从其派生。
#
# 与 scripts/release.ps1 保持一致，改动请两边同步。
###############################################################################
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_REPO="pengfei2022/redeploy-server"
BUILD_SCRIPT="build.sh"

if [[ $# -ne 1 ]]; then
  echo "用法: $0 <patch|minor|major|X.Y.Z>" >&2
  exit 2
fi
BUMP="$1"

log()  { printf '\033[1;36m[release]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[release]\033[0m %s\n' "$*"; }
err()  { printf '\033[1;31m[release]\033[0m %s\n' "$*" >&2; }

hint_rollback() {
  cat >&2 <<EOF

──────── 回滚提示 ────────
  git status
  git reset --hard HEAD~N        # 回退未 push 的 commit
  git tag -d v<版本>              # 删除本地 tag
  git push -d origin v<版本>      # 删除远端 tag（若已 push）
──────────────────────────
EOF
}

trap 'err "release 失败"; hint_rollback; exit 1' ERR

# ---- Step 1: 前置校验 -----------------------------------------------------
log "校验 git working tree"
if [[ -n "$(git -C "$REPO_ROOT" status --porcelain)" ]]; then
  err "git working tree 不干净，请先 commit 或 stash："
  git -C "$REPO_ROOT" status --short
  exit 1
fi

log "校验依赖工具"
for cmd in mvn go docker git; do
  command -v "$cmd" >/dev/null 2>&1 || { err "缺少 $cmd"; exit 1; }
done
docker buildx version >/dev/null 2>&1 || {
  err "docker buildx 未就绪：docker buildx create --use --name redeploy-builder"
  exit 1
}

log "校验 Docker Hub 登录"
#DOCKER_USER=$(docker info 2>/dev/null | awk -F': ' '/Username:/ {print $2}' | tr -d ' ')
DOCKER_USER=pengfei2022
if [[ -z "$DOCKER_USER" ]]; then
  err "未检测到 docker login 状态，请先 docker login -u pengfei2022"
  exit 1
fi
log "docker user: $DOCKER_USER"

# ---- Step 2: 计算版本 -----------------------------------------------------
CURRENT=$(mvn -f "$REPO_ROOT/server/pom.xml" -q help:evaluate -Dexpression=project.version -DforceStdout)
log "当前 pom 版本: $CURRENT"

BASE="${CURRENT%-SNAPSHOT}"
IFS='.' read -r MAJ MIN PAT <<< "$BASE"

case "$BUMP" in
  patch) TARGET="${MAJ}.${MIN}.${PAT}" ;;
  minor) TARGET="${MAJ}.$((MIN+1)).0" ;;
  major) TARGET="$((MAJ+1)).0.0" ;;
  *.*.*) TARGET="$BUMP" ;;
  *) err "未知 bump 参数: $BUMP"; exit 2 ;;
esac

# 计算下一版 SNAPSHOT（在 TARGET patch+1 上加 -SNAPSHOT）
IFS='.' read -r TMAJ TMIN TPAT <<< "$TARGET"
NEXT_SNAPSHOT="${TMAJ}.${TMIN}.$((TPAT+1))-SNAPSHOT"

log "发布版本: $TARGET"
log "下一版:   $NEXT_SNAPSHOT"

# ---- Step 2.5: 版本合法性校验 ---------------------------------------------
if [[ "$BUMP" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  log "显式指定版本，跳过 SNAPSHOT 校验"
  if [[ "$TARGET" == "$BASE" ]]; then
    err "目标版本 $TARGET 与当前版本（去 SNAPSHOT 后）相同，没有变化。"
    exit 1
  fi
elif [[ "$CURRENT" != *-SNAPSHOT ]]; then
  err "当前版本 $CURRENT 不是 SNAPSHOT 格式，不符合 master 约定。"
  err "请先手动将 pom.xml 改为 X.Y.Z-SNAPSHOT 再执行发布。"
  exit 1
fi

# ---- Step 3: 设置 pom 到目标版本 ------------------------------------------
log "mvn versions:set → $TARGET"
mvn -f "$REPO_ROOT/server/pom.xml" -q versions:set -DnewVersion="$TARGET" -DgenerateBackupPoms=false

# ---- Step 4: 构建 + 推送 --------------------------------------------------
log "调用 build.sh --version $TARGET --platforms linux/amd64,linux/arm64 --push"
sh "$BUILD_SCRIPT" --version "$TARGET" --platforms "linux/amd64,linux/arm64" --push

# ---- Step 5: 提交 release commit + tag ------------------------------------
log "git commit release: v$TARGET"
git -C "$REPO_ROOT" add server/pom.xml
git -C "$REPO_ROOT" commit -m "release: v$TARGET"
git -C "$REPO_ROOT" tag -a "v$TARGET" -m "release v$TARGET"

# ---- Step 6: bump 回 -SNAPSHOT --------------------------------------------
log "mvn versions:set → $NEXT_SNAPSHOT"
mvn -f "$REPO_ROOT/server/pom.xml" -q versions:set -DnewVersion="$NEXT_SNAPSHOT" -DgenerateBackupPoms=false
git -C "$REPO_ROOT" add server/pom.xml
git -C "$REPO_ROOT" commit -m "chore: bump to $NEXT_SNAPSHOT"

# ---- Step 7: push ---------------------------------------------------------
log "git push --follow-tags"
git -C "$REPO_ROOT" push --follow-tags

# ---- Verification: 发布后验证 ---------------------------------------------
log "验证发布结果..."

verify_pass=0
verify_fail=0

check() {
  local label="$1" shift
  if "$@" >/dev/null 2>&1; then
    log "  ✓ $label"
    verify_pass=$((verify_pass + 1))
  else
    warn "  ✗ $label"
    verify_fail=$((verify_fail + 1))
  fi
}

check "本地 tag v$TARGET 存在" \
  git -C "$REPO_ROOT" rev-parse "v$TARGET"

check "远端 tag v$TARGET 存在" \
  git -C "$REPO_ROOT" ls-remote --tags origin "v$TARGET"

check "Docker 镜像 $IMAGE_REPO:$TARGET manifest 有效" \
  docker buildx imagetools inspect "$IMAGE_REPO:$TARGET"

if [[ $verify_fail -eq 0 ]]; then
  log "全部验证通过 ($verify_pass/$verify_pass)"
else
  warn "验证 $verify_fail 项失败 / $((verify_pass + verify_fail)) 项，发布本身已完成，以下为手动验证命令："
  cat <<EOF
  # 检查本地 tag
  git tag -l v$TARGET
  git show v$TARGET

  # 检查远端 tag
  git ls-remote --tags origin v$TARGET
  # 如缺失，手动推送：
  #   git push origin v$TARGET

  # 检查镜像多架构
  docker buildx imagetools inspect $IMAGE_REPO:$TARGET
EOF
fi

# ---- Done -----------------------------------------------------------------
trap - ERR
cat <<EOF

✓ Release 成功: $IMAGE_REPO:$TARGET

Docker Hub:
  https://hub.docker.com/r/$IMAGE_REPO/tags

拉取镜像:
  docker pull $IMAGE_REPO:$TARGET
  docker pull $IMAGE_REPO:latest

验证多架构 manifest:
  docker buildx imagetools inspect $IMAGE_REPO:$TARGET

Git tag:
  v$TARGET  (已推送到 origin)

master 现在处于 $NEXT_SNAPSHOT
EOF
