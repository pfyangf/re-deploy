## Why

保险客户内网目标机（如 10.28.3.44「录像合成服务器-dat」）部署了主机安全软件/WAF，server → agent:9009 的部署请求被拦截，返回 403 HTML 拦截页（"您的访问请求可能对网站造成安全威胁，请求已被阻断"），请求未到达 agent。当前协议全部明文：`/api/task/execute` 的 JSON body 含 shell 命令/脚本，`/api/upload/init` 含文件名与绝对路径，`/api/download/init` 含远端绝对路径，status/logs 响应含命令回显——这些特征极易命中 WAF 的 WebShell/命令执行规则。

需要一层透明的载荷加密，让中间盒无法解析报文内容，快速恢复部署能力。

## What Changes

- server ↔ agent 业务调用增加 **AES-256-GCM 载荷加密**（v1）：密钥由 agent token 确定性派生 `SHA-256(UTF-8(token))`，无需协商；线格式 `nonce(12B) || ciphertext || tag(16B)`，裸二进制 `application/octet-stream`；GCM AAD 绑定请求行 `METHOD PATH`，防密文跨端点搬运。
- 请求头 `X-Payload-Version: 1` 协商：带头且 body 非空时请求体为密文，agent 对该请求的响应体整体加密并回写同头；无头请求/响应完全走现有明文协议（**agent 双栈**，旧 server 零影响）。
- 豁免：所有 `/chunk` 结尾的端点（upload multipart 分片、download 二进制分片）双向不加密；`GET /api/health` 不加密；401 响应不加密（auth 中间件先于加密中间件短路）。
- agent 新增加密中间件（auth 之后、logging 之前）：解密失败统一返回 400 明文 JSON；请求体上限 16 MiB（`MaxBytesReader`）；响应缓冲后整体加密，保留原状态码与安全响应头。
- server 新增 `AgentHttp` 组件统一承载到 agent 的 JSON 调用：加密开关 `redeploy.agent.payload-encryption`（默认 true，环境变量 `REDEPLOY_AGENT_PAYLOAD_ENCRYPTION`）；4xx/5xx 错误体先解密再抛异常，保留 `HttpClientErrorException.NotFound` 等子类；识别无版本头的 HTML 错误响应并打"疑似 WAF 拦截"WARN。
- 改造全部 4 个调用方：DeployService（execute/status/logs）、AgentService（UI 调试执行 + 轮询，health 不动）、FileTransferService（upload init/complete，chunk 保持 multipart 明文直发）、RemoteFileDownloadService（仅 download init）。
- 跨语言金标向量：Go/JUnit 单测共享固定 token/nonce/明文/期望密文，防 GCM 参数与 AAD 不一致。

### Non-goals

- 不提供链路机密性/不防中间人/不防重放（链路仍是 HTTP，Bearer 头明文可见；仅对抗无状态内容特征检测）。
- 不做 TLS/mTLS、不做 agent 反向长轮询（加密实测无效时再立项）。
- 不加密 chunk 二进制分片；不做强制加密开关（agent 永远双栈）。
- 不改 DB schema、不改前端、不改 agent→server 的 register/heartbeat。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `agent-http-hardening`：新增载荷加密中间件需求（双栈协商、AAD、豁免端点、400/401 语义、body 上限、响应加密）。

## Impact

- **agent**：新增 `internal/security/payload.go`；`internal/api/router.go` 挂中间件（新增缓冲 ResponseWriter）；新增单测。HTTP API 对无头调用方零变化。
- **server**：新增 `util/AgentPayloadCrypto.java`、`service/AgentHttp.java`；改 4 个 service 的调用点；`application.yml` 新增开关。
- **前端/DB**：无改动。
- **升级顺序**：必须先逐台升级 agent，再升级默认开启加密的 server；混合期可设 `REDEPLOY_AGENT_PAYLOAD_ENCRYPTION=false` 回退（agent 无需回退）。
- **风险面**：中。跨语言加解密为主要联调风险，靠金标向量单测前置消除；加密只对 body 特征拦截有效，若 WAF 拦 URL 词/UA/端口则无效（design.md 有诊断矩阵与预案 B）。
