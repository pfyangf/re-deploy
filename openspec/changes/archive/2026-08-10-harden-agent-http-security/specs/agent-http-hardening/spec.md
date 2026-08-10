## ADDED Requirements

### Requirement: Agent HTTP 服务 SHALL 配置慢速请求超时

Agent 的 `http.Server` MUST 设置以下四个超时字段，防御 Slow HTTP DoS 攻击（Slow headers / Slow body / Slow read）：
- `ReadHeaderTimeout`: 10 秒
- `ReadTimeout`: 60 秒
- `WriteTimeout`: 60 秒
- `IdleTimeout`: 120 秒

#### Scenario: Slow headers 攻击被防御
- **WHEN** 攻击者建立 TCP 连接后，每 10 秒才发送一个 HTTP 头部字节
- **THEN** 服务器在 `ReadHeaderTimeout`（10 秒）后关闭连接，不等待完整头部

#### Scenario: Slow body 攻击被防御
- **WHEN** 攻击者发送完整头部后，以极慢速率发送请求体
- **THEN** 服务器在 `ReadTimeout`（60 秒）后关闭连接

#### Scenario: 正常 5MB chunk 上传不被误伤
- **WHEN** server 通过 `/api/upload/{uploadId}/chunk` 上传 5MB 分片
- **THEN** 在内网环境下传输时间远小于 60 秒，连接正常完成

#### Scenario: Slow read 攻击被防御
- **WHEN** 攻击者建立连接后以极慢速率读取响应或保持空闲
- **THEN** 服务器在 `WriteTimeout`（60 秒）或 `IdleTimeout`（120 秒）后关闭连接

### Requirement: Agent HTTP 服务 SHALL 注入安全响应头

Agent MUST 通过中间件为所有 HTTP 响应注入以下 6 个安全响应头：
- `Permissions-Policy: geolocation=(), microphone=()`
- `Cross-Origin-Embedder-Policy: require-corp`
- `Cross-Origin-Opener-Policy: same-origin`
- `Cross-Origin-Resource-Policy: same-origin`
- `Access-Control-Allow-Origin: <cors_origin 配置项的值>`（默认 `https://bsck.cnoic.com:50002`）
- `Clear-Site-Data: "cache"`

安全头中间件 MUST 注册在 auth 中间件之前，确保即使 401 未授权响应也携带安全头。

`cors_origin` 配置项 MUST 支持通过 `config.yaml` 的 `cors_origin` 字段自定义。未配置时 MUST 使用默认值 `https://bsck.cnoic.com:50002`。

#### Scenario: 所有响应携带安全头
- **WHEN** 任何 HTTP 请求到达 agent（包括未认证请求）
- **THEN** 响应头中包含全部 6 个安全响应头，且值符合规定

#### Scenario: 健康检查端点携带安全头
- **WHEN** 请求 `GET /api/health`（免认证端点）
- **THEN** 200 响应中包含全部 6 个安全响应头

#### Scenario: 未授权请求响应携带安全头
- **WHEN** 请求未携带 Authorization 头或 token 错误
- **THEN** 401 响应中仍包含全部 6 个安全响应头

#### Scenario: Access-Control-Allow-Origin 使用默认值
- **WHEN** `config.yaml` 未配置 `cors_origin` 字段
- **THEN** 响应的 `Access-Control-Allow-Origin` 头值为 `https://bsck.cnoic.com:50002`

#### Scenario: Access-Control-Allow-Origin 使用自定义值
- **WHEN** `config.yaml` 配置 `cors_origin: https://example.com`
- **THEN** 响应的 `Access-Control-Allow-Origin` 头值为 `https://example.com`

### Requirement: Agent token 校验 SHALL 使用常量时间比较

Agent 的 auth 中间件 MUST 使用 `crypto/subtle.ConstantTimeCompare` 进行 token 比较，禁止使用 `==` / `!=` 等短路比较运算符。

#### Scenario: token 正确时通过认证
- **WHEN** 请求携带的 Bearer token 与配置中的 token 完全一致
- **THEN** 请求通过认证，进入后续处理

#### Scenario: token 错误时返回 401
- **WHEN** 请求携带的 Bearer token 与配置中的 token 不一致
- **THEN** 返回 401 Unauthorized，且比较耗时与正确 token 一致（不泄露匹配进度）

#### Scenario: token 长度不同时返回 401
- **WHEN** 请求携带的 token 长度与配置 token 不同
- **THEN** 返回 401 Unauthorized，`ConstantTimeCompare` 自动处理长度不等的情况
