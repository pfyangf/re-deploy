## 1. 数据库

- [x] 1.1 `schema.sql` 新增 `download_session` 表（id, server_id, remote_path, file_size, bytes_received, md5, status, local_path, error_message, initiator, initiator_ip, created_at, updated_at, completed_at，3 个索引）
- [x] 1.2 新建 `migration/V003__add_download_session.sql`（IF NOT EXISTS 兼容老库）
- [x] 1.3 验证三层 schema 演进机制走通（schema.sql 新库 / migration 老库 IF NOT EXISTS 兜底）

## 2. 实体与 Mapper

- [x] 2.1 `DownloadSession.java` 实体
- [x] 2.2 `DownloadSessionMapper.java`（annotation-based）：insert / updateStatus / updateBytes / markSuccess / markFailed / markCancelled / findById / listByServer / listByFilter / countByFilter / deleteById / markStaleDownloadingAsFailed / deleteSuccessOlderThan
- [x] 2.3 状态枚举：常量 `STATUS_PENDING / STATUS_DOWNLOADING / STATUS_SUCCESS / STATUS_FAILED / STATUS_CANCELLED`

## 3. Agent 端：下载协议

- [x] 3.1 `agent/internal/api/download.go`：`POST /api/download/init` — 校验路径白名单 + 文件存在 + 算 file_size + 创建本地 session
- [x] 3.2 `GET /api/download/{id}/chunk?seq=N` — Seek + LimitReader 流回 5MB chunk
- [x] 3.3 `GET /api/download/{id}/status` — 返回 session 状态（断点续传查询）
- [x] 3.4 `POST /api/download/{id}/complete` — 返回 MD5（≤100MB 算全量）+ 标记完成
- [x] 3.5 `POST /api/download/{id}/cancel` — 清理本地 session
- [x] 3.6 协议复用 upload 协议的内存 map + 周期清理（不需要单独的 storage 抽象）
- [x] 3.7 路径白名单校验：绝对路径 + 禁 `..` 段 + null byte 检查 + `EvalSymlinks` 后再匹配 + glob 支持 `**` 任意子路径
- [x] 3.8 配置文件新增 `agent.download.allowed-paths` 字段（默认 `/var/log/**` `/opt/*/log/**` `/tmp/redeploy-**`）+ `enabled` / `max_file_size` / `session_ttl`

## 4. Server 端：落盘服务

- [x] 4.1 `RemoteFileDownloadService`：调 agent init → 拉 chunk → `BufferedOutputStream` 写盘 → 算 MD5 → 更新 session
- [x] 4.2 异步执行：`@Async("downloadExecutor")` + 独立线程池（4 core / 16 max / 64 queue）
- [x] 4.3 落盘路径生成：`./data/downloads/session-{id}/{sanitized-path}-{unixSeconds}-{id}.bin`
- [x] 4.4 单 chunk 大小 = 5 MB，与 agent 协议对齐
- [x] 4.5 agent 30 秒无响应（ReadTimeout）则 session 标 `FAILED`
- [x] 4.6 server 启动 + 每 10 分钟扫 `status='downloading' AND updated_at < NOW() - 1h` 标 FAILED（崩溃恢复）

## 5. 清理任务

- [x] 5.1 `DownloadSessionCleanupTask`：`@Scheduled(cron="0 0 4 * * ?")` 每天 04:00 清理 `status='success' AND created_at < NOW() - 30d`
- [x] 5.2 落盘文件删除用 `Files.deleteIfExists`，失败记 WARN 不抛错

## 6. Controller API

- [x] 6.1 `POST /api/servers/{id}/download`：参数 `{remotePath}`，立即返回 sessionId（异步落盘）
- [x] 6.2 `GET /api/servers/{id}/downloads`：分页历史
- [x] 6.3 `GET /api/downloads?serverId&status&limit&offset`：全量分页历史
- [x] 6.4 `GET /api/downloads/{id}`：详情
- [x] 6.5 `GET /api/downloads/{id}/file`：二进制流，Content-Disposition: attachment
- [x] 6.6 `DELETE /api/downloads/{id}`：删文件 + DB 记录
- [x] 6.7 全部 API 走 admin-token 拦截器（沿用现有 `Authorization: Bearer <token>` 机制）
- [x] 6.8 追加 `POST /api/downloads/{id}/cancel` 取消进行中的下载

## 7. 前端

- [x] 7.1 新增「远端下载」页面：触发下载对话框（选 server + 输入路径）+ 历史表格 + 进度条
- [ ] 7.2 服务器详情页加「下载文件」按钮（**留到下个迭代**：当前 Downloads 页面已可按 server 过滤，UI 入口足够；详情页直跳是优化项）
- [x] 7.3 表格支持取消 / 取文件 / 删除操作 + 3 秒轮询 DOWNLOADING 进度

## 8. 文档

- [x] 8.1 `docs/guide/remote-file-download.md`：使用说明（数据流 + 白名单 + 大文件 + 清理 + 故障排查 + API 速查）
- [ ] 8.2 `docs/api/server-api.md`：补 `/api/servers/{id}/download` + `/api/downloads/*` 接口（**留到下一轮与通知管理 API 一起补**）
- [x] 8.3 `docs/guide/remote-file-download.md` 中已含 agent 端 `/api/download/*` 接口速查（合并到使用指南）
- [x] 8.4 `docs/guide/remote-file-download.md` 中已含 `download.allowed_paths` 配置说明

## 9. 测试与验证

- [ ] 9.1 单元测试：路径白名单校验（合法路径 / `..` 穿越 / 不存在的文件 / 目录）
- [ ] 9.2 单元测试：sanitized-path 生成（绝对路径、`/`、特殊字符）
- [ ] 9.3 单元测试：`DownloadSessionCleanupTask` 30 天边界
- [ ] 9.4 集成测试：500MB 文件端到端下载（md5 校验）
- [ ] 9.5 集成测试：网络抖动 chunk 重传（模拟 seq=3 失败后重试）
- [ ] 9.6 安全测试：路径白名单绕过（symlink 攻击、相对路径、null byte）

> 备注：9.x 测试任务暂留不勾，原因同通知管理变更 10.x——项目无测试基线；本变更以「可工作」为目标落地，测试待后续单独 PR 补齐。

## 实际交付文件清单

**新增（Go）**
- `agent/internal/api/download.go` — 5 个 handler + 路径白名单 + session 管理

**修改（Go）**
- `agent/internal/api/router.go` — 注册 5 条 `/api/download/*` 路由
- `agent/internal/config/config.go` — 新增 `DownloadConfig` + 默认白名单
- `agent/internal/logging/context.go` — 新增 `WithDownloadID` + `FromContext` 输出 download_id 字段

**新增（Java）**
- `server/src/main/java/com/redeploy/model/DownloadSession.java`
- `server/src/main/java/com/redeploy/repository/DownloadSessionMapper.java`
- `server/src/main/java/com/redeploy/service/RemoteFileDownloadService.java`
- `server/src/main/java/com/redeploy/service/DownloadSessionCleanupTask.java`
- `server/src/main/java/com/redeploy/controller/RemoteFileDownloadController.java`
- `server/src/main/java/com/redeploy/config/DownloadAsyncConfig.java`

**修改（Java/资源）**
- `server/src/main/resources/schema.sql` — 追加 `download_session` 表
- `server/src/main/resources/migration/V003__add_download_session.sql` — 新建
- `server/src/main/resources/application.yml` — 加 `redeploy.download.*` 配置

**新增（前端）**
- `frontend/src/views/Downloads.vue` — 远端下载页面

**修改（前端）**
- `frontend/src/api/client.js` — 加 7 个 `/api/downloads/*` 方法
- `frontend/src/router/index.js` — 加 `/downloads` 路由
- `frontend/src/components/AppSidebar.vue` — 加「远端下载」菜单

**新增（文档）**
- `docs/guide/remote-file-download.md` — 使用指南

**新增（OpenSpec）**
- `openspec/changes/remote-file-download/{proposal,design,tasks}.md` + `specs/*`
- `openspec/specs/remote-file-download/spec.md`（apply 后）
