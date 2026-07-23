<#
.SYNOPSIS
  scripts/build.ps1 — 本地构建脚本（不推送、不改 git）

.DESCRIPTION
  与 scripts/build.sh 行为一致，改动请两边同步。

  1. 交叉编译 agent（linux/amd64 + linux/arm64）到 server/data/agents/
  2. mvn package 生成 server jar
  3. docker buildx build 生成本地 image（默认 --load 单平台，供本地验证）

.PARAMETER Version
  显式指定镜像 tag 版本，默认读 server/pom.xml 去掉 -SNAPSHOT。

.PARAMETER NoAgent
  跳过 agent 交叉编译。

.PARAMETER Platforms
  docker 构建目标平台，逗号分隔，默认 linux/amd64。

.PARAMETER Push
  推送镜像到 Docker Hub（供 release.ps1 调用；直接用不推荐）。

.NOTES
  版本号真源：server/pom.xml <version>
#>
[CmdletBinding()]
param(
    [string]$Version = "",
    [switch]$NoAgent,
    [string]$Platforms = "linux/amd64",
    [switch]$Push
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$ImageRepo = "pengfei2022/redeploy-server"

function Log($msg)  { Write-Host "[build] $msg" -ForegroundColor Cyan }
function Warn($msg) { Write-Host "[build] $msg" -ForegroundColor Yellow }
function Fail($msg) { Write-Host "[build] $msg" -ForegroundColor Red; exit 1 }

# ---- 依赖检查 -------------------------------------------------------------
foreach ($cmd in @("mvn", "go", "docker")) {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
        Fail "缺少依赖：$cmd（请先安装）"
    }
}
& docker buildx version 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[build] docker buildx 未就绪。请执行：" -ForegroundColor Red
    Write-Host "  docker buildx create --use --name redeploy-builder" -ForegroundColor Red
    exit 1
}

# ---- 版本决定 -------------------------------------------------------------
if ([string]::IsNullOrEmpty($Version)) {
    $raw = & mvn -f "$RepoRoot\server\pom.xml" -q help:evaluate -Dexpression=project.version -DforceStdout
    $Version = ($raw -replace "-SNAPSHOT$", "").Trim()
}
Log "目标版本: $Version"
Log "目标平台: $Platforms"
Log ("推送镜像: " + $(if ($Push) { "yes" } else { "no" }))

# ---- Agent 交叉编译 -------------------------------------------------------
$AgentDir = Join-Path $RepoRoot "server\data\agents"
New-Item -ItemType Directory -Force -Path $AgentDir | Out-Null

if (-not $NoAgent) {
    Log "清理旧 agent 二进制"
    Get-ChildItem -Path $AgentDir -Filter "deploy-agent-*" -ErrorAction SilentlyContinue |
        Remove-Item -Force

    foreach ($arch in @("amd64", "arm64")) {
        Log "交叉编译 agent: linux/$arch"
        Push-Location (Join-Path $RepoRoot "agent")
        try {
            $env:GOOS = "linux"
            $env:GOARCH = $arch
            $env:CGO_ENABLED = "0"
            $outFile = Join-Path $AgentDir "deploy-agent-linux-$arch"
            & go build -trimpath -ldflags "-s -w" -o $outFile ./cmd/agent
            if ($LASTEXITCODE -ne 0) { Fail "go build 失败: linux/$arch" }
        } finally {
            Remove-Item Env:GOOS, Env:GOARCH, Env:CGO_ENABLED -ErrorAction SilentlyContinue
            Pop-Location
        }
    }
} else {
    Log "跳过 agent 交叉编译（-NoAgent）"
    foreach ($arch in @("amd64", "arm64")) {
        $f = Join-Path $AgentDir "deploy-agent-linux-$arch"
        if (-not (Test-Path $f)) {
            Fail "缺少 $f（-NoAgent 需要预先放置）"
        }
    }
}

# ---- Maven 打包 -----------------------------------------------------------
Log "mvn package"
& mvn -f "$RepoRoot\server\pom.xml" -B -DskipTests clean package
if ($LASTEXITCODE -ne 0) { Fail "mvn package 失败" }

# ---- Docker 构建 ----------------------------------------------------------
$jars = Get-ChildItem -Path (Join-Path $RepoRoot "server\target") -Filter "redeploy-server-*.jar" -ErrorAction SilentlyContinue
if ($jars.Count -ne 1) {
    Fail "server/target/ 下期望 1 个 redeploy-server-*.jar，实际 $($jars.Count)"
}

$tagVersion = "${ImageRepo}:$Version"
$tagLatest  = "${ImageRepo}:latest"

$buildxArgs = @("buildx", "build", "--platform", $Platforms, "-t", $tagVersion, "-t", $tagLatest, "-f", (Join-Path $RepoRoot "Dockerfile"))

if ($Push) {
    $buildxArgs += "--push"
} else {
    if ($Platforms -match ",") {
        Warn "多平台构建且未 -Push，切换到 --output=type=cacheonly（本地不 load）"
        $buildxArgs += @("--output", "type=cacheonly")
    } else {
        $buildxArgs += "--load"
    }
}

$buildxArgs += "$RepoRoot"

Log "docker $($buildxArgs -join ' ')"
& docker @buildxArgs
if ($LASTEXITCODE -ne 0) { Fail "docker buildx 失败" }

Log "完成: $tagVersion"
if (-not $Push -and $Platforms -notmatch ",") {
    Log "本地验证: docker run --rm -p 9006:9006 -e REDEPLOY_ADMIN_TOKEN=test $tagVersion"
}
