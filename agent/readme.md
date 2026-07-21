构建与运行（在 agent/ 目录）：
# 构建（amd64 目标机）
docker build -t redeploy-agent:latest .

# 交叉构建 arm64
docker build --build-arg TARGETARCH=arm64 -t redeploy-agent:latest-arm64 .

# 运行：持久化 conf/data，暴露 9009
docker run -d --name deploy-agent \
-p 9009:9009 \
-v deploy-agent-conf:/opt/deploy-agent/conf \
-v deploy-agent-data:/opt/deploy-agent/data \
redeploy-agent:latest

# 首次启动查看 token（stdout 只打印一次）
docker logs deploy-agent | grep -A2 "Agent Token"
关键点：
- go.mod 存在但仓库无 go.sum。Dockerfile 用 go.su[m] 通配 + go mod download || true 兜底，能在没 sum 的情况下正常构建；建议后续执行 go mod tidy 生成 go.sum 让层缓存更精准。
- 未内建 AGENT_SERVER_URL 等环境变量：config.Load() 首次启动只会生成 token 并写空的 server_url 到 conf/config.yaml，仍需按现有流程在服务端注册 token（与 systemd 安装体验一致，未改协议）。
- 未创建非 root 用户：与 AgentDownloadController.getInstallScript() 里 User=root 保持一致，避免部署脚本 sudo/写系统目录时权限踩坑。
- 时区固定 Asia/Shanghai，与 server 端 Jackson 时区对齐。
- HEALTHCHECK 打的是免鉴权的 /api/health（router.go:69）
- cd agent
  $env:GOOS="linux"; $env:GOARCH="amd64"; go build -o deploy-agent-linux-amd64 ./cmd/agent