## Why

现在 re-deploy 只有「上行」能力：server 把构建产物推到 agent。实际运维场景里经常反过来需要「下行」：

- 部署失败时，运维想从客户服务器抓日志（`/var/log/myapp/*.log`、`/opt/myapp/nohup.out`）到本地分析；
- 出问题时想下载配置文件（`/etc/myapp/application.yml`、`/opt/myapp/conf/*`）对比版本；
- 运维想让 release 后把 `nohup.out` 存档到部署历史里做合规留痕；
- agent 抓不到 shell 输出完整 stdout（只回了 truncated 摘要）时，要从文件系统反查原始文件。

当前的 100-200MB 分块上传协议是 server → agent 单向。需要对称地加一条 agent → server 的下载通道。

## What Changes

- 新增「下载会话」概念：server 发起下载请求 → agent 把文件分块（5 MB）流回 server → server 落盘到 `./data/downloads/<serverId>/<remotePath>-<timestamp>.bin` → 浏览器从 server 下载。
- 新增 `download_session` 表（记录下载任务、状态、目标 server、远端路径、本地落盘路径、大小、MD5、发起人、时间戳）。
- agent 端新增 HTTP API：
  - `POST /api/download/init`（server 调）— 校验文件存在、返回 file_size + 初始 session
  - `POST /api/download/{id}/chunk?seq=N` — 流回第 N 个 chunk（与现有上传对称）
  - `POST /api/download/{id}/complete` — 校验 MD5、标记完成
  - `GET  /api/download/{id}/status` — 断点续传查询已收 seq
  - `POST /api/download/{id}/cancel` — 取消
- server 端新增服务：
  - `RemoteFileDownloadService`：调 agent 下载协议 → 落盘 → 写 download_session 历史
  - 复用 `FileTransferService` 已有的 5 MB chunk size 习惯
- 新增 API：
  - `POST /api/servers/{id}/download` — 触发下载（参数：remotePath，可选 subdir 黑名单）
  - `GET  /api/downloads` — 下载历史列表
  - `GET  /api/downloads/{id}` — 详情
  - `GET  /api/downloads/{id}/file` — 浏览器下载落盘文件
  - `DELETE /api/downloads/{id}` — 删除落盘文件
- 前端：服务器详情页加「下载文件」入口；新增下载历史页面（带文件链接、状态、MD5、下载按钮）。

## Capabilities

### New Capabilities

- `remote-file-download`：agent → server 文件下载协议、服务端落盘与历史、浏览器取件

### Modified Capabilities

- `file-transfer`：扩展协议对称性，agent 既能收 chunk 也能发 chunk；保留原有 upload 接口不变

## Impact

- **agent**：新增 `internal/api/download.go` 路由 + handler + chunk reader；新增 `internal/storage/download_session.go` 会话状态管理
- **server**：新增 `RemoteFileDownloadService` + `DownloadSession` 实体 + Mapper；新增 `RemoteFileDownloadController`
- **数据库**：`schema.sql` 新增 `download_session` 表；`migration/V003__add_download_session.sql` 增量 DDL
- **前端**：服务器详情页面加下载按钮；新增下载历史页面
- **磁盘**：每次下载落盘 `./data/downloads/<serverId>/`，需要磁盘空间。30 天清理策略沿用 deploy_history
- **不破坏现有**：上传协议、artifact 管理、Jenkins 流程完全不动
- **配置**：agent 端新增 `agent.download.allowed-paths` 白名单（默认允许 `/var/log/` `/opt/*/log/` `/tmp/` 之外的路径需白名单），防误下载敏感文件（如 `/etc/shadow`）
- **安全**：每次下载记录发起人 IP + 时间戳 + 远端绝对路径；不暴露到公网