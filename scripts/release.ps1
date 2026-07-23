<#
.SYNOPSIS
  scripts/release.ps1 — 发版脚本

.DESCRIPTION
  与 scripts/release.sh 行为一致，改动请两边同步。

  自动完成：bump pom → build → docker push（multi-arch）→ git commit + tag →
  bump 到下一版 -SNAPSHOT → git push --follow-tags。

.PARAMETER Bump
  patch / minor / major / 显式 X.Y.Z 版本号。

.NOTES
  前置：git clean、docker buildx 就绪、docker login -u pengfei2022 已完成。
  版本号真源：server/pom.xml <version>
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true, Position=0)]
    [string]$Bump
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$ImageRepo = "pengfei2022/redeploy-server"
$BuildScript = Join-Path $PSScriptRoot "build.ps1"

function Log($m)  { Write-Host "[release] $m" -ForegroundColor Cyan }
function Warn($m) { Write-Host "[release] $m" -ForegroundColor Yellow }
function Fail($m) {
    Write-Host "[release] $m" -ForegroundColor Red
    Write-Host ""
    Write-Host "──────── 回滚提示 ────────" -ForegroundColor Yellow
    Write-Host "  git status"
    Write-Host "  git reset --hard HEAD~N        # 回退未 push 的 commit"
    Write-Host "  git tag -d v<版本>              # 删除本地 tag"
    Write-Host "  git push -d origin v<版本>      # 删除远端 tag（若已 push）"
    Write-Host "──────────────────────────" -ForegroundColor Yellow
    exit 1
}

# ---- Step 1: 前置校验 -----------------------------------------------------
Log "校验 git working tree"
$dirty = & git -C $RepoRoot status --porcelain
if ($LASTEXITCODE -ne 0) { Fail "git 命令失败" }
if (-not [string]::IsNullOrWhiteSpace($dirty)) {
    & git -C $RepoRoot status --short
    Fail "git working tree 不干净，请先 commit 或 stash"
}

Log "校验依赖工具"
foreach ($cmd in @("mvn","go","docker","git")) {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) { Fail "缺少 $cmd" }
}
& docker buildx version 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Fail "docker buildx 未就绪：docker buildx create --use --name redeploy-builder"
}

Log "校验 Docker Hub 登录"
$dockerInfo = & docker info 2>$null
$userLine = $dockerInfo | Select-String -Pattern "^\s*Username:\s*(.+)$"
if (-not $userLine) {
    Fail "未检测到 docker login 状态，请先 docker login -u pengfei2022"
}
$dockerUser = $userLine.Matches[0].Groups[1].Value.Trim()
Log "docker user: $dockerUser"

# ---- Step 2: 计算版本 -----------------------------------------------------
$current = & mvn -f "$RepoRoot\server\pom.xml" -q help:evaluate -Dexpression=project.version -DforceStdout
$current = $current.Trim()
Log "当前 pom 版本: $current"

$base = $current -replace "-SNAPSHOT$", ""
$parts = $base.Split('.')
if ($parts.Count -ne 3) { Fail "无法解析 pom 版本: $current" }
$maj = [int]$parts[0]; $min = [int]$parts[1]; $pat = [int]$parts[2]

switch ($Bump) {
    "patch" { $target = "$maj.$min.$pat" }
    "minor" { $target = "$maj.$($min+1).0" }
    "major" { $target = "$($maj+1).0.0" }
    default {
        if ($Bump -match '^\d+\.\d+\.\d+$') { $target = $Bump }
        else { Fail "未知 bump 参数: $Bump" }
    }
}
$tParts = $target.Split('.')
$nextSnapshot = "$($tParts[0]).$($tParts[1]).$([int]$tParts[2]+1)-SNAPSHOT"

Log "发布版本: $target"
Log "下一版:   $nextSnapshot"

try {
    # ---- Step 3: 设置 pom 到目标版本 --------------------------------------
    Log "mvn versions:set → $target"
    & mvn -f "$RepoRoot\server\pom.xml" -q versions:set -DnewVersion=$target -DgenerateBackupPoms=false
    if ($LASTEXITCODE -ne 0) { throw "versions:set 失败" }

    # ---- Step 4: 构建 + 推送 ---------------------------------------------
    Log "调用 build.ps1 -Version $target -Platforms linux/amd64,linux/arm64 -Push"
    & $BuildScript -Version $target -Platforms "linux/amd64,linux/arm64" -Push
    if ($LASTEXITCODE -ne 0) { throw "build.ps1 失败" }

    # ---- Step 5: 提交 release commit + tag -------------------------------
    Log "git commit release: v$target"
    & git -C $RepoRoot add server/pom.xml
    & git -C $RepoRoot commit -m "release: v$target"
    if ($LASTEXITCODE -ne 0) { throw "git commit 失败" }
    & git -C $RepoRoot tag "v$target"
    if ($LASTEXITCODE -ne 0) { throw "git tag 失败" }

    # ---- Step 6: bump 回 -SNAPSHOT ---------------------------------------
    Log "mvn versions:set → $nextSnapshot"
    & mvn -f "$RepoRoot\server\pom.xml" -q versions:set -DnewVersion=$nextSnapshot -DgenerateBackupPoms=false
    if ($LASTEXITCODE -ne 0) { throw "versions:set(snapshot) 失败" }
    & git -C $RepoRoot add server/pom.xml
    & git -C $RepoRoot commit -m "chore: bump to $nextSnapshot"
    if ($LASTEXITCODE -ne 0) { throw "git commit(snapshot) 失败" }

    # ---- Step 7: push -----------------------------------------------------
    Log "git push --follow-tags"
    & git -C $RepoRoot push --follow-tags
    if ($LASTEXITCODE -ne 0) { throw "git push 失败" }
}
catch {
    Fail $_.Exception.Message
}

Write-Host ""
Write-Host "✓ Release 成功: ${ImageRepo}:$target" -ForegroundColor Green
Write-Host ""
Write-Host "Docker Hub:"
Write-Host "  https://hub.docker.com/r/$ImageRepo/tags"
Write-Host ""
Write-Host "拉取镜像:"
Write-Host "  docker pull ${ImageRepo}:$target"
Write-Host "  docker pull ${ImageRepo}:latest"
Write-Host ""
Write-Host "验证多架构 manifest:"
Write-Host "  docker buildx imagetools inspect ${ImageRepo}:$target"
Write-Host ""
Write-Host "Git tag:"
Write-Host "  v$target  (已推送到 origin)"
Write-Host ""
Write-Host "master 现在处于 $nextSnapshot"
