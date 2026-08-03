## Why

当前部署流程在提交后直接向所有目标服务器发起部署，不预先检查服务器是否在线。如果有服务器离线，用户需要等待部署执行到失败才能知道，浪费时间且体验差。同时 AgentService 的 RestTemplate 没有配置超时，服务器不可达时可能长时间阻塞。

## What Changes

- 部署提交前，并行检查所有目标服务器的 Agent 健康状态
- 任一台服务器离线则取消部署，返回离线服务器列表，不创建部署记录
- 预检时同步更新离线服务器的 status 字段为 offline
- AgentService 的 RestTemplate 增加 5 秒连接和读取超时
- 前端部署错误提示显示后端返回的具体错误信息

## Capabilities

### New Capabilities

- `deploy-precheck`：部署前在线检查，并行验证所有目标服务器的 Agent 可用性，离线则取消部署并更新状态

### Modified Capabilities

- `task-execution`：部署发起时增加前置健康检查环节，行为从"盲发"变为"预检后执行"

## Impact

- 后端：`AgentService`（RestTemplate 加超时）、`DeployController`（部署前预检逻辑）
- 前端：`Deploy.vue`（错误信息展示优化）
- 数据库：无 schema 变更（status 字段已存在）
- API：`POST /api/deploy` 增加前置检查，可能返回新的 400 错误格式（离线服务器列表）
