## ADDED Requirements

### Requirement: Agent SHALL 支持加密载荷协议（v1）双栈协商

Agent MUST 在现有明文 HTTP API 之外支持载荷加密协议 v1，通过请求头 `X-Payload-Version: 1` 协商。无该头的请求 MUST 完全按现有明文协议处理；agent MUST NOT 提供强制加密开关。

v1 线格式为 `nonce(12B 随机) || ciphertext || GCM tag(16B)`，算法 AES-256-GCM（128-bit tag）；密钥 MUST 为 `SHA-256(agent token 字符串的 UTF-8 字节)`，MUST NOT 对 token 做 hex 解码；AAD MUST 为 `METHOD + " " + URL.Path`（不含 query、host），加密响应复用同一请求的 AAD。加密报文的 Content-Type MUST 为 `application/octet-stream`。

#### Scenario: 带头的加密 execute 请求正常执行并返回加密响应
- **WHEN** server 以 `X-Payload-Version: 1` POST `/api/task/execute`，body 为合法 v1 密文（AAD=`POST /api/task/execute`）
- **THEN** agent 成功解密出明文 JSON 并执行任务
- **AND** 响应状态码为 200，带 `X-Payload-Version: 1` 头，body 为同一 AAD 加密的密文

#### Scenario: 无头明文请求行为不变
- **WHEN** 调用方不带 `X-Payload-Version` 头发送任意现有请求
- **THEN** agent 按现有明文协议处理，响应为明文 JSON，不带版本头

#### Scenario: 密文跨端点搬运被拒绝
- **WHEN** 发给 `/api/task/execute` 的密文被重放到 `/api/upload/init`
- **THEN** 因 AAD 不匹配 GCM 校验失败，agent 返回 400 明文 JSON

#### Scenario: 未知协议版本
- **WHEN** 请求带 `X-Payload-Version` 且值不为 `1`
- **THEN** agent 返回 400 明文 JSON

### Requirement: 加密中间件 SHALL 按位置挂在认证之后并豁免分片与健康检查

加密中间件 MUST 注册在 auth 中间件之后、logging 中间件之前。以下路径 MUST 跳过加解密（即使误带版本头，响应也保持明文）：`/api/health` 与任何以后缀 `/chunk` 结尾的路径（upload multipart 分片、download 二进制分片）。

认证失败（401）响应 MUST 保持明文 JSON 且不带版本头。404/405（路由未匹配）MUST NOT 经过加密中间件。

#### Scenario: 401 不加密
- **WHEN** 加密请求携带错误 Bearer token
- **THEN** auth 中间件先返回明文 401 JSON，无版本头

#### Scenario: chunk 端点双向豁免
- **WHEN** server POST `/api/upload/{id}/chunk`（multipart）或 GET `/api/download/{id}/chunk`
- **THEN** 即使请求误带版本头，请求体与响应体均保持原明文/二进制格式

### Requirement: 加密请求 SHALL 限定体积并对非法密文返回统一 400

加密中间件 MUST 在解密前用 `MaxBytesReader` 将请求体限制为 16 MiB。带版本头且 body 非空时，长度不足 28 字节或 GCM 校验失败 MUST 返回统一的 400 明文 JSON（`{"error":"invalid encrypted payload"}`），不带版本头，且不区分具体失败原因。带版本头的空 body 请求（Content-Length 为 0，如 upload complete）MUST 跳过请求体解密，但其响应 MUST 仍加密。GET 请求 MUST NOT 尝试解密请求体。

#### Scenario: 篡改密文
- **WHEN** v1 请求体任意字节被改动
- **THEN** GCM 校验失败，返回 400 明文 JSON

#### Scenario: 空 body POST 带头
- **WHEN** server POST `/api/upload/{id}/complete` 带版本头且 Content-Length 为 0
- **THEN** agent 不解密请求，正常完成合并并返回加密响应

#### Scenario: 加密 body 超 16 MiB
- **WHEN** v1 请求体超过 16 MiB
- **THEN** 请求被拒绝且不进入业务 handler

### Requirement: 加密响应 SHALL 保留原状态码与安全响应头

中间件 MUST 缓冲下游 handler 的响应，加密后使用相同 HTTP 状态码输出，覆写 Content-Type 为 `application/octet-stream`、Content-Length 为密文长度，并保留 securityHeaders 中间件注入的全部安全响应头。业务错误响应（4xx/5xx JSON）在加密模式下 MUST 同样被加密。

#### Scenario: 业务 400 也加密
- **WHEN** 加密 execute 请求解密成功但业务 JSON 缺少字段，handler 返回 400
- **THEN** 客户端收到 400 状态码、带版本头、body 为加密后的错误 JSON
