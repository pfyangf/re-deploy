## Context

re-deploy 的现有文件传输能力是「上行」(server → agent)，用于部署时的产物分发。运维场景里强烈需要反向能力：从客户服务器下载文件到开发机分析。原因是客户服务器多为客户内网环境，运维没有直连权限，必须借 server 当中转。

设计时必须考虑：

1. **大文件**：客户服务器上的日志/备份可能单文件 500MB-2GB；不能整文件加载到内存
2. **磁盘空间**：多次下载会累积落盘文件，需要 30 天清理
3. **路径白名单**：客户服务器上 `/etc/shadow` `/root/.ssh/id_rsa` 不能让运维随便下载
4. **断点续传**：与现有 upload 协议对称的下载协议
5. **审计**：必须能回答「谁在什么时候下载了哪个文件」

## Goals / Non-Goals

**Goals:**
- 提供 agent → server 的分块下载协议（5 MB chunk，与 upload 对称）
- 服务端落盘到 `./data/downloads/<serverId>/`，并提供浏览器下载
- 落盘文件 30 天自动清理
- agent 端路径白名单机制
- 下载会话可断点续传
- 下载历史可查询（发起人、文件路径、大小、MD5、时间）
- 不破坏现有上传协议

**Non-Goals:**
- 不实现「目录打包下载」（单文件场景，不做 tar/zip 合并）
- 不实现「流式不落盘」（本次按鹏飞决策落盘中转，磁盘换简洁）
- 不实现「文件预览」（下载到本地用编辑器看）
- 不实现「增量同步 / rsync」（一次性拉取）
- 不实现「上传到云存储」（仅本地落盘）
- 不做 agent 多文件并发下载（一次只一个文件，多文件让用户发多个请求）

## Decisions

### D1: 协议对称性

download 协议镜像 upload 协议：

| 阶段 | upload (server→agent) | download (server←agent) |
|------|------------------------|--------------------------|
| 初始化 | `POST /api/upload/init` | `POST /api/download/init` |
| 分块 | `POST /api/upload/{id}/chunk` | `POST /api/download/{id}/chunk` |
| 状态 | `GET /api/upload/{id}/status` | `GET /api/download/{id}/status` |
| 完成 | `POST /api/upload/{id}/complete` | `POST /api/download/{id}/complete` |
| 取消 | `POST /api/upload/{id}/cancel` | `POST /api/download/{id}/cancel` |

**理由**：心智模型一致，agent 端代码可参考 upload 模块实现。

### D2: chunk size 与内存控制

- chunk size = **5 MB**（与 upload 一致）
- agent 端用 `os.Open` + `Seek` + `io.LimitReader`，每 chunk 读完后 GC 释放
- server 端用 `BufferedOutputStream` 顺序写盘
- **不**整文件加载到内存（避免 2GB 文件炸 JVM）

### D3: 路径白名单（安全）

`agent.download.allowed-paths` 配置项：JSON 数组，每项是 glob 模式。

```yaml
agent:
  download:
    allowed-paths:
      - "/var/log/**"
      - "/opt/*/log/**"
      - "/tmp/redeploy-**"
      - "/opt/*/conf/*.yml"
      - "/opt/*/conf/*.yaml"
```

agent 收到 download init 时：
1. 校验 remotePath 是绝对路径
2. 校验 remotePath 匹配 allowed-paths 之一（glob 比对，**禁用** `..` 路径穿越）
3. 校验文件存在 + 是 regular file（非目录、非设备、非 socket）
4. 拒绝 → 返回 403 + 原因

**理由**：默认开放 `/var/log` `/opt/*/log` 等运维常用路径；要访问其他路径需在 agent config 里显式加白名单。

### D4: server 端落盘布局

```
./data/downloads/
└── <serverId>/
    └── <sanitized-remote-path>-<timestamp>.bin
```

- `serverId`：整数目录隔离
- `sanitized-remote-path`：把 `/` 替换成 `__`，去掉前导 `/`，保留目录结构信息
- `<timestamp>`：Unix 秒（避免同名文件覆盖）
- 真实远端路径写进 `download_session.remote_path` 表里供追溯

例：下载 `/var/log/myapp/app.log` from server 1 @ 2026-09-04T10:30:00Z：
```
./data/downloads/1/var__log__myapp__app.log-1725445800.bin
```

### D5: 下载会话表

```sql
CREATE TABLE download_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    server_id BIGINT NOT NULL,
    remote_path VARCHAR(1024) NOT NULL,
    file_size BIGINT NOT NULL,
    md5 VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,         -- 'pending' | 'downloading' | 'success' | 'failed' | 'cancelled'
    local_path VARCHAR(1024),
    bytes_received BIGINT DEFAULT 0,
    error_message VARCHAR(2048),
    initiator VARCHAR(64),               -- admin-token 持有者标识（暂用 "admin"）
    initiator_ip VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    INDEX idx_server (server_id),
    INDEX idx_status (status),
    INDEX idx_created (created_at)
);
```

### D6: API 设计

```
POST /api/servers/{id}/download
  Body: { "remotePath": "/var/log/myapp/app.log" }
  Returns: { "id": 123, "fileSize": 104857600, "md5": "..." }
  注：返回前已落盘完成（同步等待或异步？见 D7）

GET  /api/downloads
  Query: serverId, status, from, to, page, size
  Returns: paginated list of download sessions

GET  /api/downloads/{id}
  Returns: session detail with metadata

GET  /api/downloads/{id}/file
  Returns: file as binary stream (Content-Disposition: attachment; filename=...)
  仅 status='success' 才允许下载

DELETE /api/downloads/{id}
  删除落盘文件 + 标记 session 为 deleted
```

### D7: 同步 vs 异步下载

**同步**：
- `POST /api/servers/{id}/download` 同步等待（最长超时 10 分钟）
- 优点：API 简单，前端可以直接跳下载链接
- 缺点：100MB+ 文件耗时可能几分钟，长连接容易断

**异步**：
- `POST /api/servers/{id}/download` 立即返回 session id
- 前端轮询 `GET /api/downloads/{id}` 直到 status='success'
- 优点：不阻塞 HTTP 连接，大文件友好
- 缺点：前端要轮询

**决策**：**异步**。原因：
- 100MB+ 文件下载容易 504（nginx 默认 60s）
- 复用现有 deploy_history 的轮询模式
- 大文件场景是常态（500MB+ 日志），同步不可靠

### D9: MD5 校验

agent 端 `init` 时计算整个文件 MD5（异步，避免阻塞）。`complete` 时 server 端拿到落盘文件再算一次比对。

**注意**：init 时拿不到 MD5 就先占位 `"calculating"`，等异步算完更新到 session。这样 init 接口不能立即返回 md5，前端首次看到的是 `"calculating"`。

**取舍**：这是次优方案——理想是 init 同步算 MD5，但 2GB 文件全文件读 IO 太重。改为：init 返回 file_size，不返回 md5；complete 时再校验。

### D10: 30 天清理

`DownloadSessionCleaner`：`@Scheduled` 每天凌晨 4 点清理 `status='success' AND created_at < NOW() - 30 day` 的 session + 落盘文件。

落盘文件删除用 `java.nio.file.Files.deleteIfExists`，删除失败记 WARN 不抛错（防止 session 表被清理但磁盘文件残留时被误导）。

### D11: agent 端下载会话状态

agent 端也需要 `download_session` 概念，存到本地 `/opt/deploy-agent/data/download_sessions/`：

```go
type downloadSession struct {
    ID         string
    FilePath   string
    FileSize   int64
    MD5        string
    Status     string  // pending | sending | success | failed
    NextSeq    int     // 下一个待发 chunk
    CreatedAt  time.Time
}
```

agent 端使用 BoltDB 或纯文件持久化（参考现有 upload_session 实现，复用 storage 层）。

### D12: 失败处理

- init 失败（文件不存在 / 白名单拒绝 / agent 端 IO 错误）→ session 直接标 `failed`
- chunk 中途网络断 → server 端 polling 检测超时（30s 无响应）→ 标记 `failed`
- MD5 不匹配 → 删除落盘文件 → 标 `failed`
- 任何失败留下 `error_message` 供排查

## Risks / Trade-offs

- **[风险]** agent 端路径白名单被绕过（路径穿越） → 缓解：禁用 `..`、用绝对路径 + glob 严格匹配（用 `path/filepath.Match` 而非正则）；init 时 `stat` + `EvalSymlinks` 拿到真实路径再匹配
- **[风险]** 大文件下载中 agent 端 OOM → 缓解：每 chunk 用 LimitReader，文件描述符读完即关
- **[风险]** `./data/downloads/` 磁盘写满 → 缓解：30 天自动清理 + 给 Jenkins 触发器/手动触发限速（每秒最大 50 MB）
- **[风险]** 下载过程中 server 重启 → 缓解：server 启动时扫所有 `status='downloading'` 的 session，标记为 `failed`（让用户重发）
- **[取舍]** 不做目录打包下载 → 单目录场景用户得下载多次 → 接受（YAGNI，按需再加）
- **[取舍]** 路径白名单在 config 文件而非 UI → 接受（agent 端 config 由运维维护，前端改不了）
- **[取舍]** MD5 而非 SHA256 → 沿用现有 upload 的 MD5 习惯 → 接受

## 项目结构变化

```
agent/
└── internal/
    ├── api/
    │   └── download.go                  # 新增
    ├── storage/
    │   └── download_session.go          # 新增（参考 upload_session）
    └── ...

server/src/main/java/com/redeploy/
├── controller/
│   ├── RemoteFileDownloadController.java   # 新增
│   └── ...
├── service/
│   ├── RemoteFileDownloadService.java      # 新增
│   ├── DownloadSessionCleaner.java         # 新增（@Scheduled）
│   └── ...
├── model/
│   ├── DownloadSession.java                # 新增
│   └── ...
└── repository/
    ├── DownloadSessionMapper.java          # 新增
    └── ...
```

## API 设计概要

```
POST   /api/servers/{id}/download          # 触发下载，返回 session id
GET    /api/servers/{id}/downloads         # 单个服务器的下载历史
GET    /api/downloads                      # 全量下载历史（分页）
GET    /api/downloads/{id}                 # 下载会话详情
GET    /api/downloads/{id}/file            # 浏览器取落盘文件
DELETE /api/downloads/{id}                 # 删除落盘文件 + 标记
```

## Open Questions

1. **多文件并发**：是否允许同一 server 同时跑多个 download session？默认允许（agent 端无锁，OS 调度），但要不要在 session 表加 unique key 防重复？
2. **agent 端权限**：路径白名单是否要支持基于用户/任务模板的动态授权？目前只走 config 文件，不做
3. **文件类型识别**：落盘文件要不要按 `.log` `.yml` 后缀猜 MIME type 给浏览器？目前统一 `application/octet-stream`
4. **审计导出**：要不要提供「下载历史 CSV 导出」接口？目前不做，按需再加