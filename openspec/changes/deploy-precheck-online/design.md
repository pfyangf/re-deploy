## Context

当前部署流程在 `DeployController.triggerDeploy` 中校验参数后，直接创建部署记录并异步执行部署，不检查目标服务器是否在线。`AgentService` 使用的 `RestTemplate` 未配置超时，在服务器不可达时可能长时间阻塞。

服务器列表的 `status` 字段目前仅在用户手动点击"测试连接"时更新，状态可能过期，不能作为部署前的可靠依据。

## Goals / Non-Goals

**Goals:**
- 部署提交前并行检查所有目标服务器的 Agent 健康状态
- 任一服务器离线则取消部署，不创建部署历史记录
- 预检时同步更新服务器 status 字段
- AgentService RestTemplate 配置 5 秒连接和读取超时
- 前端展示后端返回的具体错误信息

**Non-Goals:**
- 不实现心跳机制或自动定期检测
- 不增加批量更新数据库的 Mapper 方法
- 不修改 agent 端代码
- 不引入新的数据库字段或表
- 不做"跳过离线服务器继续部署"的选项

## Decisions

### 1. 预检位置：DeployController 触发部署之前
- **选择**：在 `DeployController.triggerDeploy` 中，创建部署历史记录之前执行预检
- **理由**：预检失败时不产生部署记录，保持历史记录干净；同步返回错误给前端，用户体验好
- **备选**：放在 DeployService 里 → 已经异步了，预检失败也要创建历史记录，产生无意义的失败记录

### 2. 并行模式：ExecutorService + CompletableFuture
- **选择**：使用 `Executors.newFixedThreadPool(10)` + `CompletableFuture.supplyAsync`，与 DeployService 现有模式一致
- **理由**：与项目现有并行模式保持一致，代码风格统一；10 线程足够处理绝大多数场景
- **备选**：复用 DeployService 的线程池 → 线程池用途不同，分开更清晰

### 3. 超时配置：5 秒连接+读取超时
- **选择**：AgentService 的 RestTemplate 配置 `connectTimeout=5000ms`、`readTimeout=5000ms`
- **理由**：健康检查是轻量接口，正常毫秒级返回，5 秒足够；顺便修复现有 testConnection 无超时的隐患
- **备选**：只给预检单独配超时 → 重复配置，且 testConnection 也应该有超时

### 4. 状态更新方式：循环调用 serverMapper.update
- **选择**：对每台离线服务器循环调用 `serverMapper.update(server)` 更新 status
- **理由**：服务器数量不多（一般几台到十几台），性能无压力；无需新增批量更新方法，改动最小
- **备选**：新增 batchUpdateStatus 方法 → 需要改 Mapper 加 SQL，改动更大

### 5. 错误返回格式
- **选择**：HTTP 400，body 为 `{ "error": "以下服务器离线: server1 (192.168.1.1), server2 (192.168.1.2)" }`
- **理由**：与项目现有错误格式 `{ "error": "..." }` 一致；前端已有的 catch 逻辑只需调整消息显示
- **备选**：返回结构化的 offlineServers 数组 → 前端需要额外解析，目前只是展示文字，没必要

### 6. 前端错误展示
- **选择**：从错误响应中提取 error 字段显示，替换固定的"启动部署失败"
- **理由**：用户能看到具体哪些服务器离线，快速定位问题
- **备选**：保持通用错误 → 用户不知道失败原因

## Risks / Trade-offs

- [风险] 预检增加了部署启动的延迟 → 全部在线时延迟约等于最慢的那台的健康检查时间（正常几十毫秒），可接受
- [风险] AgentService 加超时可能影响其他调用 → 5 秒对所有现有场景都足够，反而消除了无限阻塞的隐患
- [限制] 10 线程池在服务器数量 >10 时会分批执行，最坏情况 ceil(N/10) × 5s → 一般场景服务器数量 <10，影响可忽略
