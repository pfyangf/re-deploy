# API 密钥允许的路径

基础地址是 `REDEPLOY_BASE_URL`。除下列 POST 外，业务接口只允许 GET。所有 PUT、DELETE、PATCH 都会 403。

## GET

- `/api/auth/me`
- `/api/groups` 及其子路径
- `/api/servers` 及其子路径（含下载记录）
- `/api/tasks` 及其子路径
- `/api/artifacts` 及其子路径
- `/api/deploy` 及其子路径（历史、状态、详情、Jenkins 构建）
- `/api/notification` 及其子路径
- `/api/downloads` 及其子路径
- `/api/agents` 及其子路径

## POST

- `POST /api/groups`
- `POST /api/servers`
- `POST /api/servers/{id}/test`
- `POST /api/servers/{id}/download`
- `POST /api/tasks`
- `POST /api/notification/configs`
- `POST /api/deploy`
- `POST /api/deploy/{id}/cancel`

## 拒绝

- `/api/settings/**`、`/api/system/**`（含密钥管理、用户、角色、菜单）
- `POST /api/servers/{id}/debug/exec`
- `POST /api/deploy/cleanup`
- `POST /api/downloads/{id}/cancel`
- `POST /api/notification/configs/{id}/test`
- 任何修改和删除

Agent 注册 `POST /api/agents/register` 与心跳不使用这把密钥。
