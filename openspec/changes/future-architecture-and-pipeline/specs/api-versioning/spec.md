## ADDED Requirements

### Requirement: API 版本化路由

系统 SHALL 新增 `/api/v1/*` 路由，所有新能力优先在 v1 暴露。

#### Scenario: v1 路由存在
- **THEN** 以下路由 MUST 可用：
  - `GET /api/v1/pipelines`
  - `POST /api/v1/pipelines`
  - `GET /api/v1/pipelines/{id}`
  - `PUT /api/v1/pipelines/{id}`
  - `DELETE /api/v1/pipelines/{id}`
  - `GET /api/v1/pipelines/{id}/runs`
  - `POST /api/v1/runs`
  - `GET /api/v1/runs/{id}`
  - `POST /api/v1/runs/{id}/cancel`
  - `POST /api/v1/runs/{id}/steps/{stepId}/approve`
  - `GET /api/v1/runs/{id}/logs`
  - `GET /api/v1/parameters`
  - `POST /api/v1/parameters`

#### Scenario: 认证保持
- **WHEN** 调用 v1 API
- **THEN** 认证方式 MUST 与旧 API 一致：Header `Authorization: Bearer {admin-token}`
- **AND** `WebConfig.AuthInterceptor` MUST 同时覆盖 `/api/**` 和 `/api/v1/**`

### Requirement: 旧 API 兼容层

系统 SHALL 保留现有 `/api/*` 路由，内部转发或适配到 v1 实现。

#### Scenario: 部署接口兼容
- **WHEN** 调用 `POST /api/deploy`
- **THEN** 系统 MUST 仍能创建并执行部署
- **AND** 内部 MUST 创建对应的 Pipeline 和 Run

#### Scenario: Task 接口兼容
- **WHEN** 调用 `GET /api/tasks`、`POST /api/tasks` 等
- **THEN** 系统 MUST 继续返回数据
- **AND** 内部可从 Pipeline 表读取并做 DTO 适配

#### Scenario: 兼容层文档
- **THEN** `docs/api/server-api.md` MUST 更新，旧接口标记为 deprecated，并指向 v1 替代接口

### Requirement: OpenAPI 文档

系统 SHALL 提供 OpenAPI 3.0 规范文件，描述 v1 API。

#### Scenario: 文件位置
- **THEN** MUST 存在 `docs/api/openapi-v1.yaml`
- **AND** 该文件 MUST 包含所有 v1 端点的路径、方法、参数、请求体、响应定义

#### Scenario: 校验
- **WHEN** 使用 Swagger Editor 或 `swagger-codegen-cli validate` 校验
- **THEN** MUST 无结构性错误

### Requirement: v1 响应格式统一

系统 SHALL 统一 v1 API 的响应格式。

#### Scenario: 成功响应
- **THEN** 成功响应 SHOULD 使用以下结构：
  ```json
  {
    "code": 0,
    "data": { ... },
    "message": "ok"
  }
  ```

#### Scenario: 错误响应
- **THEN** 错误响应 MUST 使用以下结构：
  ```json
  {
    "code": 10001,
    "data": null,
    "message": "错误描述"
  }
  ```
- **AND** HTTP 状态码 MUST 与错误语义匹配（400 参数错误、401 未认证、404 不存在、500 服务器错误）

### Requirement: 分页与过滤

系统 SHALL 为列表型 v1 API 提供分页与基础过滤。

#### Scenario: 分页参数
- **WHEN** 调用 `GET /api/v1/pipelines?page=1&size=20`
- **THEN** 响应 MUST 包含 `page`、`size`、`total`、`list` 字段

#### Scenario: 状态过滤
- **WHEN** 调用 `GET /api/v1/runs?status=failed`
- **THEN** 响应 MUST 只返回状态为 `failed` 的 Run

## MODIFIED Requirements

- 现有 `/api/*` 接口的内部实现可能被替换为调用 v1 service，但外部契约保持不变。

## REMOVED Requirements

无。
