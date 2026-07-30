## ADDED Requirements

### Requirement: Server 集成测试覆盖

系统 SHALL 为 `server/` 提供基于 JUnit 5 和 Spring Boot Test 的集成测试，覆盖 Controller、Service、Mapper 层。

#### Scenario: Mapper 层测试
- **WHEN** 测试类标注 `@MybatisTest` 或 `@SpringBootTest`
- **THEN** 测试 MUST 使用 H2 内存数据库或 Testcontainers SQLite
- **AND** 对 `GroupMapper`、`ServerMapper`、`TaskMapper`、`DeployHistoryMapper` 等核心 mapper 的 CRUD 操作 MUST 有测试覆盖

#### Scenario: Service 层测试
- **WHEN** 测试 `DeployService`、`AgentService`、`ArtifactService` 等业务 service
- **THEN** 外部依赖（HTTP 调用、文件系统、Jenkins）MUST 用 `@MockBean` 或 WireMock 替换
- **AND** 测试 MUST 验证正常路径与异常路径

#### Scenario: Controller 层测试
- **WHEN** 测试 `@WebMvcTest` 或 `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- **THEN** 测试 MUST 验证认证拦截、参数校验、返回格式

### Requirement: Agent 测试覆盖

系统 SHALL 为 `agent/` 提供 Go 标准测试，覆盖 HTTP handlers、executor 和 config。

#### Scenario: Handler 测试
- **WHEN** 测试 `/api/task/execute`、`/api/task/{taskId}/status`、`/api/upload/*` 等 handler
- **THEN** 测试 MUST 使用 `httptest.NewRecorder()` 构造请求
- **AND** 测试 MUST 覆盖认证失败、参数错误、成功执行三种情况

#### Scenario: Executor 测试
- **WHEN** 测试 `executor.ExecuteShell`
- **THEN** 测试 MUST 覆盖命令成功、命令失败、超时、模板参数替换

#### Scenario: Config 测试
- **WHEN** 测试 `config.Load`
- **THEN** 测试 MUST 覆盖默认配置、YAML 文件加载、环境变量覆盖

### Requirement: Frontend 测试覆盖

系统 SHALL 为 `frontend/` 提供 Vitest 测试，优先覆盖 API client 和纯函数逻辑。

#### Scenario: API client 测试
- **WHEN** 测试 `src/api/client.js`
- **THEN** 测试 MUST 使用 `msw` 或 `axios-mock-adapter` 拦截请求
- **AND** 测试 MUST 覆盖请求发送、401 处理、错误提示

#### Scenario: 组件测试
- **WHEN** 测试核心组件（如 Dashboard 统计卡片、TaskDialog）
- **THEN** 测试 MUST 使用 `@vue/test-utils` 渲染组件
- **AND** 测试 MUST 验证 props 渲染、事件触发

### Requirement: GitHub Actions CI

系统 SHALL 在 `.github/workflows/ci.yml` 中定义 CI，在 PR 与 push 到 main 时触发。

#### Scenario: PR 触发
- **WHEN** 用户提交 Pull Request
- **THEN** CI MUST 并行执行以下 job：
  - `server-test`：运行 `mvn -f server/pom.xml test`
  - `agent-test`：运行 `go test ./...`（在 `agent/` 目录）
  - `frontend-test`：运行 `npm run test`（在 `frontend/` 目录）
  - `build`：构建 server jar、agent 二进制、前端静态资源

#### Scenario: main 分支构建
- **WHEN** 代码合并到 main
- **THEN** CI MUST 额外执行 Docker 多架构镜像构建并推送到 Docker Hub
- **AND** tag 必须为 `latest` 和当前 pom 版本（去除 `-SNAPSHOT`）

### Requirement: Java 代码格式化与风格检查

系统 SHALL 引入 Spotless Maven Plugin 和 Checkstyle，统一 Java 代码风格。

#### Scenario: 格式化检查
- **WHEN** 执行 `mvn spotless:check`
- **THEN** 所有 Java 文件 MUST 符合统一格式（Google Java Format 或 palantir-java-format）

#### Scenario: 风格检查
- **WHEN** 执行 `mvn checkstyle:check`
- **THEN** 代码 MUST 通过 Checkstyle 规则检查

### Requirement: Go 代码风格检查

系统 SHALL 引入 `golangci-lint` 配置，统一 Go 代码风格并捕获常见问题。

#### Scenario: Lint 检查
- **WHEN** 在 `agent/` 目录执行 `golangci-lint run`
- **THEN** MUST 无 error 级别问题
- **AND** CI 中 MUST 执行此检查

### Requirement: Frontend 代码风格检查

系统 SHALL 引入 ESLint 和 Prettier，统一 JavaScript/Vue 代码风格。

#### Scenario: Lint 与格式化
- **WHEN** 执行 `npm run lint` 和 `npm run format:check`
- **THEN** MUST 无错误
- **AND** CI 中 MUST 执行此检查

### Requirement: 测试报告与覆盖率

系统 SHALL 在 CI 中生成测试报告与覆盖率数据。

#### Scenario: Server 覆盖率
- **WHEN** CI 执行 server 测试
- **THEN** MUST 生成 JaCoCo 报告
- **AND** 覆盖率低于 30% 时 CI 可标记为不稳定（不强制阻塞，但可见）

#### Scenario: Agent 覆盖率
- **WHEN** CI 执行 `go test -cover`
- **THEN** MUST 输出覆盖率百分比

## MODIFIED Requirements

无。

## REMOVED Requirements

无。
