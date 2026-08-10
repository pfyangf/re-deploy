## 1. 超时配置（防御 Slow HTTP DoS）

- [x] 1.1 在 `agent/internal/api/router.go` 的 `Start()` 方法中，为 `http.Server` 添加 `ReadHeaderTimeout: 10 * time.Second`
- [x] 1.2 添加 `ReadTimeout: 60 * time.Second`
- [x] 1.3 添加 `WriteTimeout: 60 * time.Second`
- [x] 1.4 添加 `IdleTimeout: 120 * time.Second`
- [x] 1.5 添加 `time` 包到 import 声明（若未导入）

## 2. 安全响应头中间件（防御 Missing Security Headers）

- [x] 2.1 在 `agent/internal/api/router.go` 中新增 `securityHeadersMiddleware` 方法，设置 6 个安全响应头：`Permissions-Policy` / `Cross-Origin-Embedder-Policy` / `Cross-Origin-Opener-Policy` / `Cross-Origin-Resource-Policy` / `Access-Control-Allow-Origin` / `Clear-Site-Data`。其中 `Access-Control-Allow-Origin` 的值取自 `s.cfg.CorsOrigin`
- [x] 2.2 在 `setupRoutes()` 中注册 `securityHeadersMiddleware`，位置在 `authMiddleware` 之前（确保 401 响应也带安全头）
- [x] 2.3 在 `agent/internal/config/config.go` 的 `Config` 和 `configFile` 结构体中添加 `CorsOrigin` 字段（yaml tag `cors_origin`）
- [x] 2.4 在 `config.go` 添加默认常量 `defaultCorsOrigin = "https://bsck.cnoic.com:50002"`，并在 `Load()` 中为 `cfg.CorsOrigin` 设置默认值
- [x] 2.5 在 `config.go` 的 `Load()` 读取已有配置分支中，若 `file.CorsOrigin` 非空则覆盖默认值；在 `Save()` 中写入 `CorsOrigin` 字段

## 3. Token 常量时间比较（消除时序攻击侧信道）

- [x] 3.1 在 `agent/internal/api/router.go` 的 import 中添加 `crypto/subtle`
- [x] 3.2 将 `authMiddleware` 中的 `token != s.cfg.Token` 改为 `subtle.ConstantTimeCompare([]byte(token), []byte(s.cfg.Token)) != 1`

## 4. 构建与验证

- [x] 4.1 执行 `go build ./...`（在 `agent/` 目录）确认编译通过
- [x] 4.2 执行 `go vet ./...` 确认无静态检查告警
- [x] 4.3 交叉编译 linux-amd64 和 linux-arm64 二进制，确认产出正常
