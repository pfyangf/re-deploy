# Design: agent 通信载荷加密 v1

## 1. 威胁模型与边界

本方案**只**对抗无状态内容特征检测（主机 WAF/主机安全软件对 HTTP body 中 shell 特征、绝对路径、回显内容的关键字/语义检测）。

- Bearer 头、请求行（METHOD PATH）、UA 对中间盒始终明文——如果 WAF 拦 URL 词（`execute`/`upload`）、Java 默认 UA、或 9009 端口本身，本方案无效，见第 7 节诊断矩阵。
- 不防重放（明文时代 Bearer 即可重放，加 nonce 缓存/时间戳需状态与时钟同步，与止血目标不符）。
- 不提供机密性：能看到 Bearer 的人同样是合法调用方。TLS/mTLS 属独立提案。

## 2. 协议 v1（线格式）

- 算法：AES-256-GCM，tag 128 bit，nonce 12 字节随机（Go `crypto/rand`，Java `SecureRandom` 单例）。
- 密钥派生（二义性写死）：`key = SHA-256(token 字符串的 UTF-8 字节)`。token 是 config.yaml 里的 32-hex 字符串，**两端都不做 hex decode**。
- 线格式：`nonce(12) || ciphertext || tag(16)`。最短合法报文 28 字节（空明文）。Go `gcm.Seal` 输出 `ciphertext||tag`；Java `AES/GCM/NoPadding` + `GCMParameterSpec(128, nonce)` 同布局。
- AAD（二义性写死）：`AAD = METHOD + " " + PATH`，例如 `POST /api/task/execute`、`GET /api/task/abc/status`。
  - Go：`r.Method` + `r.URL.Path`；Java：`method.name()` + " " + `URI.create(url).getPath()`。
  - 不含 query（唯一带 query 的 chunk 端点豁免）、不含 host/port。
  - 响应报文复用同一请求的 AAD。
- 请求/响应体明文均为现有 JSON 文本（UTF-8）；线上 Content-Type 覆写为 `application/octet-stream`。

## 3. 协商与豁免

- 请求头 `X-Payload-Version: 1`：
  - agent：带头且非豁免路径时，body 非空则解密，响应整体加密并回写同头；未知版本值回 400。
  - server：开关开时对 JSON 类调用加头，body 非空加密；按响应是否有同头决定是否解密。
- 无 `X-Payload-Version` 头 = 现有明文协议，agent 永久双栈，无 agent 侧开关。
- 豁免路径（即使误带头也忽略，响应保持明文）：
  - 后缀 `/chunk`（upload multipart POST、download octet-stream GET）；
  - `/api/health`。
- 401：加密中间件位于 auth 之后，认证失败返回明文 JSON 且无头；server 按"响应无头即原文"解析。
- 预留 `X-Payload-Version: 2` = base64 JSON 信封（`{"n":"<b64 nonce>","d":"<b64 ct||tag>"}`），v1 不实现；若实测 WAF 拦高熵二进制 body，server 切配置、agent 双版本识别即可。

## 4. 空 body 语义

- GET：永不尝试解密请求体；响应是否加密只看请求头。
- 空 body POST 带头（如 upload complete）：`Content-Length: 0` 时不解密请求，响应仍加密。
- 带头且 body 非空但短于 28 字节 / GCM 验证失败：统一 **400 明文 JSON** `{"error":"invalid encrypted payload"}`，不带头，不区分失败原因。

## 5. agent 中间件

链顺序：`securityHeaders → auth → payloadCrypto → logging → handler`。

- mux `Use` 只对匹配路由生效；404/405 不经中间件，保持现状明文。
- 加密分支：`http.MaxBytesReader(w, r.Body, 16MiB)` → 读全量 → `gcm.Open(nonce, rest, aad)` → 替换 `r.Body`（NopCloser bytes.Reader）与 `ContentLength`。
- 缓冲型 ResponseWriter：WriteHeader 只记状态码（默认 200），Write 入 buffer；返回后按 AAD Seal，覆写 Content-Type=octet-stream、写 `X-Payload-Version: 1`、按密文长度写 Content-Length，输出密文；securityHeaders 预设头自然透传。Seal 失败（理论不会）回 500 明文。
- agent 无流式响应/SSE/Hijack（已 grep 确认）；唯一长响应 download chunk 豁免；logs 本就是整文件读出发送，多一次拷贝可接受。

## 6. server AgentHttp

- `@Component`，`@Value("${redeploy.agent.payload-encryption:true}")`；密钥结果按 token 缓存（`ConcurrentHashMap`，数量=历史 server 数）。`Cipher` 每次新建（非线程安全），`SecureRandom`/`ObjectMapper` 单例。
- 两档 RestTemplate：DEFAULT（无超时，对齐现状）、SHORT（connect/read 5s，对齐 AgentService.executeCommand 现状）；converters 只留 ByteArrayHttpMessageConverter，全部收发 `byte[]`。
- 接口：`postJson(url, token, body, type/SHORT)`、`getJson(...)`、`getText(...)`（UTF-8 明文 ndjson）、`postEmpty(...)`；GET 加版本头宣告可解加密响应。
- `ResponseErrorHandler`：4xx/5xx 读 byte[]，有版本头先解密；用 `HttpClientErrorException.create` / `HttpServerErrorException.create` 工厂抛出，保留 NotFound 等子类（fetchTaskLogs 的低版本 agent 兼容分支继续工作），异常体为明文。
- 无头错误响应且 Content-Type 含 html/text：WARN `疑似 WAF/中间盒拦截（非 agent JSON 响应）`，HTML 片段保留在异常消息中作为定位证据。
- 关开关：纯 JSON/空体、不带头；响应无头按原文解析（旧 agent、401、WAF 页均可透传）。
- 明文 server 日志中的命令打印（AgentService:123、DeployService:299）属既有行为，本次只对超长 body 截断，不做脱敏改造。

## 7. WAF 诊断矩阵（加密无效时）

在 **server 主机**对 `http://10.28.3.44:9009` 带合法 Bearer 对照实验，按 Content-Type 区分 agent JSON vs WAF HTML：

| 实验 | 判定 |
|---|---|
| `GET /api/health`（curl UA） | 基线：GET/端口是否通 |
| `GET /api/info`，curl UA vs `-A "Java/17"` | 是否 UA 特征 |
| POST execute 良性 body（echo） | body 内容是否触发点 |
| POST execute body 含 `whoami`/shell 元字符 | 确认命令特征 |
| POST 不存在路径（认证后） | JSON 404=到达 agent；HTML=URL/POST 被拦 |
| 升级后带头真实加密 execute | 验证止血；响应应是 octet-stream |

分支：① 拦 URL 词 → 预案 B：中性路径别名（`/api/v1/op`，备案不实现）；② 拦 UA → AgentHttp 加普通 UA 拦截器（一行级改动，确诊后再做）；③ 拦端口 → 加密无效，只能加白/换端口/反向通道；④ 拦高熵二进制 → 启用 v2 base64 信封；⑤ chunk 上传被拦 → 后续分片加密扩展，不在本次范围。

## 8. 升级与回退

1. 先逐台替换 agent 二进制并重启（双栈，升级瞬间不影响旧 server）；server 机 curl 明文 health 回归。
2. 再升级 server jar（默认加密开启），验证一次完整部署 + 上传 + 远端下载。
3. 回退：`REDEPLOY_AGENT_PAYLOAD_ENCRYPTION=false` 重启 server 即全明文，agent 不动。
4. 全局开关，混合机队期间未升级 agent 会对密文返 400；故要求先全量 agent 后 server，不做自动明文重试（现场重试仍被 WAF 拦，无意义且混淆判定）。

## 9. 金标向量（Go/JUnit 共享）

- token：`0123456789abcdef0123456789abcdef`
- key = SHA-256(UTF-8(token))（32 字节，hex 值在实现时由 Go 测试打印，抄入两端断言）
- 固定 nonce（仅测试用）：12 字节 0x00
- AAD：`POST /api/v1/ping`
- plaintext：`{"msg":"ping"}`
- 期望 wire（hex）：实现时先由 Go 金标测试产出固定值并提交，Java JUnit 硬编码同一值做 open/seal 双向对账；同时断言两端对同一 token 算出的 key hex 完全一致。
