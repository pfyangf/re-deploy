---
name: redeploy
description: >-
  通过 Re-Deploy 查询分组、服务器、任务、制品、通知和部署记录，新增这些资源，并发起或取消部署。
  当用户提到 Re-Deploy、redeploy、部署、发布、查服务器在线、查任务或用 API 密钥操作部署平台时使用。
---

# Re-Deploy

用环境变量里的 API 密钥调用 Re-Deploy 现有 HTTP 接口。密钥权限是固定的：可以查询和新增，可以发起、取消部署并查看状态与日志；不能修改、删除，也不能操作系统设置、用户、角色、菜单和密钥管理。

## 配置

- `REDEPLOY_BASE_URL`：服务根地址，例如 `http://127.0.0.1:9006`，不要带末尾斜杠。
- `REDEPLOY_API_KEY`：以 `rdk_` 开头的密钥。

缺任一变量时先问用户，不要猜测，也不要改仓库文件来存放密钥。不要把密钥、`agentToken`、Jenkins token 或 SSH 凭据写进对话、日志或代码。

请求头：

```http
Authorization: Bearer <REDEPLOY_API_KEY>
Content-Type: application/json
```

先 `GET /api/auth/me`。401 表示密钥无效、已停用或已过期；403 表示该动作不在密钥权限内。

## 部署

1. `GET /api/groups`、`GET /api/servers`、`GET /api/tasks`，必要时 `GET /api/tasks/{id}`。
2. 只对在线服务器发起。向用户确认任务名称和目标主机后再调用。
3. `POST /api/deploy`，正文至少包含 `taskId` 和 `serverIds`。可选 `version`、`params`。Jenkins 任务先 `GET /api/deploy/jenkins/builds?taskId=`，把构建号放进 `params.jenkinsBuildNumber`。
4. 响应里的 `deployId` 用 `GET /api/deploy/{id}/status` 轮询，直到状态离开 `pending` 和 `running`。
5. 需要日志时再 `GET /api/deploy/{id}/detail`。取消进行中的部署用 `POST /api/deploy/{id}/cancel`。

```http
POST /api/deploy
{"taskId": 1, "serverIds": [2], "version": "1.2.3"}
```

## 新增

优先复用已有分组、服务器和任务。必须新建时先读一条同类资源，再提交同样的字段：

- `POST /api/groups`：`name`
- `POST /api/servers`：`name`、`host`，可选 `port`（默认 9009）、`groupId`、`agentToken`
- `POST /api/tasks`：先看现有任务的 `taskType`、`deployPath`、命令和 Jenkins 字段
- `POST /api/servers/{id}/test` 检测 Agent
- `POST /api/servers/{id}/download`，正文 `{"remotePath":"..."}` 发起远端下载

不要调用 PUT、DELETE。路径是否允许见 [reference.md](reference.md)。
