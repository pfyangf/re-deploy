# 远端文件下载使用指南

> 对应变更：`remote-file-download`

让 server 通过 agent 把目标服务器上的文件拉到 server 本地，浏览器从 server 下载。典型场景：部署失败后一键拉日志、应用配置审计、临时备份。

## 1. 数据流

```
浏览器(选server + 输入路径)
   ↓ POST /api/servers/{id}/download
server.RemoteFileDownloadService.initDownload
   ↓ HTTP POST /api/download/init
agent.downloadInitHandler
   ↓ 校验白名单 + 解析符号链接
返回 download_id + chunk_size + total_bytes
   ↓
server.async.runDownload
   ↓ HTTP GET /api/download/{id}/chunk?seq=N  (5MB 一片，循环)
agent.downloadChunkHandler
   ↓ Seek + LimitReader 流式
server 写盘到 ./data/downloads/session-{id}/{filename}-{ts}-{sid}.bin
   ↓ 全部 chunk 拉完
HTTP POST /api/download/{id}/complete  (清理 agent session)
server 算 MD5 → DB 标 SUCCESS
```

## 2. 触发下载

### 2.1 UI 入口

侧边栏「远端下载」页面：
1. 右上角「触发新下载」
2. 选服务器 + 填绝对路径（如 `/var/log/nginx/access.log`）
3. 启动后表格立即出现一条 `DOWNLOADING` 记录，3 秒轮询自动刷新进度

### 2.2 API

```bash
curl -X POST http://server:9006/api/servers/3/download \
  -H "Authorization: Bearer <adminToken>" \
  -H "Content-Type: application/json" \
  -d '{"remotePath":"/var/log/nginx/access.log"}'
# → 202 Accepted
# { "sessionId": 42, "status": "DOWNLOADING", "message": "下载任务已启动" }
```

## 3. 路径白名单（关键安全点）

agent 端 **默认只允许**：

```yaml
# /opt/deploy-agent/conf/config.yaml
download:
  enabled: true
  allowed_paths:
    - /var/log/**
    - /opt/*/log/**
    - /tmp/redeploy-**
  max_file_size: 0        # 0 = 不限；单位字节
  session_ttl: 3600       # session 在 agent 端存活秒数
```

**白名单校验**：
- 必须绝对路径
- 显式禁 `..` 段（防 `../../etc/passwd` 攻击）
- 解析符号链接后再 glob 匹配（防 `/var/log/safe.log` 软链到 `/etc/shadow`）
- 父目录必须可达

要访问 `/etc/` `/root/` 等敏感目录必须**显式在 yml 添加**，且**重启 agent** 生效。

> `/var/log/**` 中的 `**` 是「任意子路径」语义（不是正则），匹配 `/var/log/x`、`/var/log/a/b/c` 等。

## 4. 大文件处理

- **流式 chunk**：5 MB 一片，`io.LimitReader` 防止读多
- **服务端落盘不读全**：使用 `BufferedOutputStream` 边拉边写，server 内存占用仅一个 chunk (~5MB)
- **MD5 校验**：≤ 200 MB 的文件会算全量 MD5 并入库；更大的文件跳过 MD5（避免 hash 阻塞 IO）
- **断点续传**：客户端拉 chunk 失败会抛异常，session 标 FAILED。需要续传重新触发下载即可（agent 端 session 也会在 `session_ttl` 后清理）

## 5. 落盘路径

```
./data/downloads/
  session-42/
    var_log_nginx_access.log-1725436800-42.bin
  session-43/
    opt_app_log_app-1725436900-43.bin
```

- 一个 session 一个目录（方便清理时整目录删）
- 文件名格式：`<sanitized-path>-<unixSeconds>-<sessionId>.bin`
- sanitized 规则：去掉开头 `/`，把 `/\:?"<>| ` 替换为 `_`

## 6. 清理策略

- **每 10 分钟**：扫 `status IN (PENDING, DOWNLOADING) AND updated_at < NOW() - 1h` 标 FAILED（崩溃恢复）
- **每天 04:00**：删除 `status='SUCCESS' AND created_at < NOW() - 30d` 的记录 + 落盘文件
- 保留天数可由 `REDEPLOY_DOWNLOAD_RETENTION_DAYS` 调整
- 手工清理：直接调 `DELETE /api/downloads/{id}`

## 7. 故障排查

| 现象 | 排查 |
| --- | --- |
| 启动下载报 `403 forbidden` | agent 端白名单拒了：路径不在 `allowed_paths` 内；agent 日志会有 `path not in whitelist: ...` |
| 启动下载报 `404 file not found` | 路径不对；agent `os.Stat` 失败；建议 SSH 上去先 `ls` 确认 |
| 进度卡在某个百分比不动 | 30s 读超时，可能是网络抖动或文件被外部占用（`flock`）；`runDownload` 线程会捕获并标 FAILED |
| agent 端 session 没清 | `session_ttl` 默认 3600s；可改 yml 缩短；或调 agent 的 `POST /api/download/{id}/cancel` |
| 文件 0 字节 | 远端就是空文件（正常）；或远端路径指向目录（agent 拒：返回 400） |
| 下载大文件很慢 | 改 agent `download.allowed_paths` 没用；瓶颈在网络；可以考虑 ssh 走 jump host |

## 8. API 速查

| Method | Path | 说明 |
| --- | --- | --- |
| POST | `/api/servers/{id}/download` | body `{remotePath}` → 202 + `{sessionId, status}` |
| GET | `/api/servers/{id}/downloads` | 单服务器下载历史 |
| GET | `/api/downloads?serverId=&status=&limit=&offset=` | 全量下载历史 |
| GET | `/api/downloads/{id}` | 详情（含 bytes_received / md5 / error_message） |
| GET | `/api/downloads/{id}/file` | 取文件（二进制流 + Content-Disposition） |
| POST | `/api/downloads/{id}/cancel` | 取消进行中的下载 |
| DELETE | `/api/downloads/{id}` | 删记录 + 落盘文件 |

## 9. Agent 端 API 速查

| Method | Path | 说明 |
| --- | --- | --- |
| POST | `/api/download/init` | body `{remote_path}` → `{download_id, chunk_size, total_bytes, total_chunks}` |
| GET | `/api/download/{id}/chunk?seq=N` | 流式返回第 N 片（5MB） |
| GET | `/api/download/{id}/status` | 会话状态（断点续传查询） |
| POST | `/api/download/{id}/complete` | 服务端拉完通知，agent 清理本地 session；200MB 内返回 MD5 |
| POST | `/api/download/{id}/cancel` | 主动取消 |

所有 agent 端 API 走 `Authorization: Bearer <agent-token>` 鉴权，与现有 `/api/upload/*` / `/api/task/*` 一致。
