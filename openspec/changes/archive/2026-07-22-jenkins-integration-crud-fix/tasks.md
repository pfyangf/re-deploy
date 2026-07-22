## 1. 数据库和后端模型

- [x] 1.1 修改 `schema.sql`，给 `tasks` 表新增 Jenkins 相关 6 个字段
- [x] 1.2 修改 `model/Task.java`，新增 Jenkins 字段的属性、getter、setter
- [x] 1.3 修改 `repository/TaskMapper.java`，更新 insert 和 update 语句包含新字段

## 2. 后端服务层修改

- [x] 2.1 修改 `JenkinsService.java`，新增缓存清理逻辑（保留同 Job 最近 3 次构建）
- [x] 2.2 修改 `DeployService.java`，在部署流程中集成 Jenkins 下载和上传到 Agent

## 3. 后端控制器修改

- [x] 3.1 修改 `TaskController.java`，createTask 和 updateTask 处理 Jenkins 新字段

## 4. Agent 端实现 deploy 步骤

- [x] 4.1 修改 `agent/internal/api/task.go`，在 `executeTask` 中添加 `deploy` 类型步骤处理，复制文件到目标路径

## 5. 前端修改 - TaskDialog

- [x] 5.1 修改 `components/TaskDialog.vue`，新增 Jenkins 配置表单项（启用勾选 + 5 个输入框）
- [x] 5.2 改造 `TaskDialog.vue` 支持编辑模式，打开时填充已有数据

## 6. 前端修改 - Tasks 列表页面

- [x] 6.1 修改 `views/Tasks.vue`，新增只读详情弹窗
- [x] 6.2 修改 `views/Tasks.vue`，给"查看"按钮绑定点击事件（修复 bug）
- [x] 6.3 修改 `views/Tasks.vue`，新增"编辑"按钮和处理函数
- [x] 6.4 修改 `views/Tasks.vue`，调整操作列宽度从 200px 到 280px 防止按钮换行
- [x] 6.5 编辑成功后刷新任务列表

## 7. 前端修改 - Deploy 页面

- [x] 7.1 修改 `views/Deploy.vue`，监听 taskId 变化
- [x] 7.2 根据选中任务是否启用 Jenkins，动态显示/隐藏构建号输入框
- [x] 7.3 将构建号作为参数传递给后端部署 API
