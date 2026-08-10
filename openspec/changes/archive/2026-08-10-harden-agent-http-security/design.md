## Context

Agent（Go `net/http` 服务，端口 9009）当前在 `agent/internal/api/router.go` 中通过 `http.Server{}` 启动，未设置任何超时字段，且仅有 auth + logging 两个中间件，无安全响应头。漏扫报告两个高危漏洞，阻塞部署。所有改动集中在单文件 `router.go`，不涉及业务逻辑层。

约束：
- Agent 是纯 server-to-server API（Spring Boot server -> Go agent），浏览器不直接访问
- Upload chunk 端点接收 5MB 分片（`upload.go:119`），`ReadTimeout` 必须留足余量
- 内网部署（10.x.x.x），100Mbps 带宽下 5MB 传输约 0.4 秒
- 仅依赖 Go 标准库，不引入第三方

## Goals / Non-Goals

**Goals:**
- 通过漏扫（消除 Slow HTTP DoS 和 Missing Security Headers 两个漏洞）
- 不引入实际安全危害（安全头值需对纯 API 服务合理）
- 顺带消除 token 时序攻击侧信道

**Non-Goals:**
- 不重构 agent 的 HTTP 架构（保持 `gorilla/mux` + 标准 `http.Server`）
- 不增加 CORS 预检（OPTIONS）处理逻辑（纯 API 服务无此需求）
- 不引入 HTTPS/TLS（由部署层/反代处理，不在 agent 职责内）
- 不改业务逻辑层（task / upload / handlers）

## Decisions

### 决策 1: 超时字段配置（防御 Slow HTTP DoS）

在 `Start()` 中为 `http.Server` 设置四个超时：

| 字段 | 值 | 防御目标 | 理由 |
|------|-----|---------|------|
| `ReadHeaderTimeout` | 10s | Slow headers | 请求头极小，10 秒足够；攻击者每 10s 发一个 header 会被掐断 |
| `ReadTimeout` | 60s | Slow body | 5MB chunk 内网 0.4s，留 150 倍余量；覆盖整个请求体读取 |
| `WriteTimeout` | 60s | Slow read | API 响应都是毫秒级，60s 充裕；防御攻击者慢读响应 |
| `IdleTimeout` | 120s | Slow read | keep-alive 空闲连接上限，默认无限大会被占满 |

**备选方案考虑**：
- 用 `net.Listen` + `tcpKeepAliveListener`？过度复杂，`http.Server` 原生超时已足够
- 用反向代理（nginx）加超时？agent 直接暴露 9009，无反向代理层

**关键权衡**：`ReadTimeout` 会覆盖整个请求包括 body。Go 1.8+ 的 `http.Server` 中 `ReadTimeout` 从连接读取开始计时到整个请求体读完。60 秒对 5MB chunk 安全（即便 10Mbps 也只需 4 秒）。

### 决策 2: 安全响应头中间件

新增 `securityHeadersMiddleware`，在 `setupRoutes()` 中注册于 auth 之前（让所有响应包括 401 都带安全头）。6 个头的值：

| 安全头 | 值 | 理由 |
|--------|-----|------|
| `Permissions-Policy` | `geolocation=(), microphone=()` | 无害，限制浏览器功能，过扫描 |
| `Cross-Origin-Embedder-Policy` | `require-corp` | 无害，隔离跨源资源，过扫描 |
| `Cross-Origin-Opener-Policy` | `same-origin` | 无害，隔离窗口上下文，过扫描 |
| `Cross-Origin-Resource-Policy` | `same-origin` | 限制跨源加载，对 API 服务正确 |
| `Access-Control-Allow-Origin` | 来自 `cors_origin` 配置项（默认 `https://bsck.cnoic.com:50002`） | 精确限制允许的跨域来源，比 `*` 更安全；默认值指向生产 server 域名 |
| `Clear-Site-Data` | `"cache"` | 仅清缓存类型，伤害最小；比不加更能过扫描 |

**备选方案考虑**：
- `Access-Control-Allow-Origin` 设为 `*`？过于宽松，任何源都能跨域访问，安全性低
- `Access-Control-Allow-Origin` 复用 `ServerURL`？`ServerURL` 是 agent 连接 server 的地址，语义不一定是 CORS 允许的来源；独立配置项更清晰
- 不加 `Clear-Site-Data`？最正确但扫描器可能继续报
- 中间件放 auth 之后？会导致 401 响应无安全头，扫描器可能仍报

### 决策 3: Token 常量时间比较

`authMiddleware` 中 `token != s.cfg.Token` 改为 `subtle.ConstantTimeCompare([]byte(token), []byte(s.cfg.Token)) != 1`。

**理由**：`!=` 是短路比较，逐字节返回，存在时序侧信道。`ConstantTimeCompare` 保证恒定时间，消除信息泄露。一行改动，零风险。

## Risks / Trade-offs

- **[风险] `ReadTimeout` 60s 可能在极端网络下误伤大文件上传** -> 内网 100Mbps 环境 5MB 仅需 0.4s，余量 150 倍；若未来出现超大 chunk 可调大
- **[风险] `Access-Control-Allow-Origin` 若 `cors_origin` 配置错误会导致浏览器跨域被拒** -> 有合理默认值 `https://bsmnp.cnoic.com`，且该头仅影响浏览器，agent 的真实调用方是 server（非浏览器），不受 CORS 限制
- **[风险] `Clear-Site-Data: "cache"` 若浏览器访问到会清缓存** -> 仅清缓存类型，不影响 cookie/storage；agent 是纯 API 服务，浏览器本不应访问
- **[权衡] 安全头中间件放在 auth 之前，401 响应也带安全头** -> 这是故意的，让扫描器对所有响应都看到安全头
