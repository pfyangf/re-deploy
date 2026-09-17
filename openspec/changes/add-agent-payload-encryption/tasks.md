# Tasks

## 1. OpenSpec 提案
- [x] 1.1 新建 `openspec/changes/add-agent-payload-encryption/`：proposal/design/tasks + `specs/agent-http-hardening/spec.md`
- [x] 1.2 `openspec validate add-agent-payload-encryption --strict` 通过（顺带修复主 spec 历史遗留的 delta 头问题）

## 2. agent（Go）
- [x] 2.1 新增 `internal/security/payload.go`：deriveKey（SHA-256 UTF-8 token，不 hex decode）、seal/open（AES-256-GCM，wire=nonce(12)||ct||tag(16)，AAD=`METHOD PATH`）；open 含 <28B 校验
- [x] 2.2 固定 nonce 金标向量 Go 测试，固化期望 wire hex + key hex
- [x] 2.3 `internal/api/router.go` 挂 `payloadCryptoMiddleware`（auth 之后、logging 之前）：豁免 /health 与 `/chunk` 后缀；带头非空 body 解密并重写 Body/ContentLength；空 body 不解密但响应仍加密；16MiB MaxBytesReader；篡改/<28B 返 400 明文 JSON；响应缓冲整体加密，保留状态码与安全头
- [x] 2.4 httptest：加密 execute 往返、无头明文回归、chunk 带头也豁免、错误 token 明文 401、篡改 400、未知版本 400、加密 GET、404 不经中间件
- [x] 2.5 `go vet ./...`、`gofmt -l`（仅历史文件）、`go test ./...`

## 2b. 交叉编译
- [x] 2b.1 `GOOS=linux GOARCH=amd64/arm64 CGO_ENABLED=0 go build -o ../server/data/agents/deploy-agent-linux-* ./cmd/agent`

## 3. server（Java）
- [x] 3.1 新增 `util/AgentPayloadCrypto.java`：deriveKey/seal/open，与 Go 同格式；金标向量 JUnit（key hex 与 wire hex 对账 Go 产出值）
- [x] 3.2 新增 `service/AgentHttp.java`：@Value 开关、双档 RestTemplate（DEFAULT 无超时 / SHORT 5s）、byte[] converter、错误体先解密后用工厂方法抛异常、WAF HTML 识别 WARN
- [x] 3.3 AgentHttp 单测（com.sun.net.httpserver 假 agent）：加密往返、无头透传、403 HTML 透传+WARN、404 异常子类、AAD 不匹配失败、关开关明文
- [x] 3.4 改造 DeployService（execute/status/logs 三处）、AgentService.executeCommand（两处，保留 5s）、FileTransferService（init/complete，chunk 不动）、RemoteFileDownloadService（仅 init）
- [x] 3.5 `application.yml` 加 `redeploy.agent.payload-encryption: ${REDEPLOY_AGENT_PAYLOAD_ENCRYPTION:true}`
- [x] 3.6 `mvn -q -DskipTests compile`、`mvn test`（10 tests 全绿）

## 4. 文档
- [x] 4.1 `docs/guide/configuration.md` 加开关、升级顺序（先 agent 后 server）、回退
- [x] 4.2 `AGENTS.md` Deploy flow gotchas 补加密通道与 chunk 豁免、旧 agent 临时关开关
- [x] 4.3 design.md 诊断矩阵（WAF 拦 URL/UA/端口时的对照实验与预案）

## 5. 现场验证（用户操作）
- [ ] 5.1 替换 10.28.3.44 agent 二进制并重启，server 机 curl 明文 health 回归
- [ ] 5.2 server 升级后对该机执行真实部署（含 Jenkins 制品上传），确认无 400/无 WAF 403、status/logs 密文往返正常
- [ ] 5.3 若仍 403：按 design.md 诊断矩阵做 curl 对照，定位 URL/UA/端口/binary-body 触发点后走预案
