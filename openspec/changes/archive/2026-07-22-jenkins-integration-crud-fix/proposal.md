## Why

当前系统缺少从 Jenkins 动态下载构建物的能力，用户需要手动下载后上传才能部署，流程繁琐。另外任务管理功能不完整，缺少查看详情和编辑功能，列表操作按钮存在换行问题，需要一并修复补全 CRUD。

## What Changes

- **新增 Jenkins 集成**：在任务配置级别增加 Jenkins 构建物下载支持，部署时输入构建号自动下载并部署
- **补全任务管理 CRUD**：
  - 新增任务详情查看（只读弹窗）
  - 新增任务编辑功能
  - 修复查看按钮无点击事件的 bug
  - 调整操作列宽度，解决多按钮换行问题
- **新增 deploy 步骤支持**：在 Agent 端实现 `deploy` 步骤类型，自动将上传构件复制到目标部署路径
- **缓存清理策略**：Jenkins 下载构件按 Job 分组保留最近 3 个，自动清理旧文件节省空间
- **前端交互优化**：部署页面根据任务是否启用 Jenkins 动态显示/隐藏构建号输入框

## Capabilities

### New Capabilities
- `jenkins-build-integration`: Jenkins 构建物动态下载集成，支持任务级别配置和部署时指定构建号
- `task-management-crud`: 补全任务管理的完整 CRUD 功能（查看 + 编辑）

### Modified Capabilities
- 无（现有能力没有需求变更，只新增能力）

## Impact

- **后端代码**：
  - `server/src/main/java/com/redeploy/model/Task.java` - 新增字段
  - `server/src/main/java/com/redeploy/repository/TaskMapper.java` - 更新 SQL
  - `server/src/main/java/com/redeploy/service/DeployService.java` - 集成下载流程
  - `server/src/main/java/com/redeploy/service/JenkinsService.java` - 增加缓存清理
  - `server/src/main/resources/schema.sql` - 数据库新增字段
- **前端代码**：
  - `frontend/src/components/TaskDialog.vue` - 新增 Jenkins 配置，支持编辑模式
  - `frontend/src/views/Tasks.vue` - 新增详情弹窗、编辑按钮，修复 bug，调整宽度
  - `frontend/src/views/Deploy.vue` - 动态显示构建号输入框
- **Agent 代码**：
  - `agent/internal/api/task.go` - 新增 `deploy` 步骤处理
