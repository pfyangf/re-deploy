## Why

Agent 端 9009 端口漏扫检测出两个高危漏洞：缓慢的 HTTP 拒绝服务攻击（Slow HTTP DoS）和 HTTP 缺少安全头漏洞。当前 `http.Server` 未配置任何超时字段，且无安全响应头中间件，导致无法通过安全扫描、阻塞部署。此外 token 校验使用非常量时间比较，存在时序攻击侧信道，应一并加固。

## What Changes

- 为 agent 的 `http.Server` 配置四个超时字段（`ReadHeaderTimeout` / `ReadTimeout` / `WriteTimeout` / `IdleTimeout`），防御 Slow headers / Slow body / Slow read 三种慢速攻击
- 新增 `securityHeadersMiddleware`，统一注入 6 个安全响应头（`Permissions-Policy` / `Cross-Origin-Embedder-Policy` / `Cross-Origin-Opener-Policy` / `Cross-Origin-Resource-Policy` / `Access-Control-Allow-Origin` / `Clear-Site-Data`），满足扫描器要求。其中 `Access-Control-Allow-Origin` 使用新增的 `cors_origin` 配置项，默认 `https://bsck.cnoic.com:50002`，精确限制允许的跨域来源
- 将 `authMiddleware` 中的 token 比较从 `!=` 改为 `crypto/subtle.ConstantTimeCompare`，消除时序攻击侧信道

## Capabilities

### New Capabilities
- `agent-http-hardening`: Agent HTTP 服务端的安全加固能力，涵盖慢速 HTTP DoS 防护（超时配置）、安全响应头注入、以及 token 常量时间比较

### Modified Capabilities
<!-- 无现有 spec 需要修改 -->

## Impact

- **受影响代码**：
  - `agent/internal/api/router.go`：增加超时字段、新增安全头中间件、token 常量时间比较
  - `agent/internal/config/config.go`：新增 `CorsOrigin` 字段与默认值
- **API 兼容性**：无 breaking change。超时配置对正常内网请求（5MB chunk 上传、毫秒级 API 响应）有 15 倍以上余量，不影响业务。`cors_origin` 配置项有默认值，已部署的 agent 即使旧 config.yaml 没有该字段也能正常工作
- **依赖**：仅依赖 Go 标准库（`crypto/subtle`），无新增外部依赖
- **部署**：修复后需重新交叉编译 agent 二进制（linux-amd64 / linux-arm64）并放入 server 的 `data/agents/` 目录
