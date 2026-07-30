## 1. 后端：Jenkins 构建历史查询 API

- [x] 1.1 在 `JenkinsService` 中新增 `getBuildHistory(url, jobName, username, token, limit)` 方法，调用 Jenkins JSON API 返回构建列表
- [x] 1.2 在 `DeployController` 中新增 `GET /api/deploy/jenkins/builds` 接口，按 taskId 查询构建历史
- [x] 1.3 处理异常：任务不存在、未启用 Jenkins、Jenkins 连接失败等情况返回合适的 HTTP 状态和错误信息
- [x] 1.4 验证接口可正常调用并返回正确的数据结构

## 2. 前端 API 层

- [x] 2.1 在 `api/client.js` 中新增 `getJenkinsBuildHistory(taskId)` 方法
- [x] 2.2 在 `api/client.js` 中检查并确认分组相关接口（`getTasks` 支持 `groupId`、`getServers` 支持 `groupId`）可直接复用

## 3. 前端：部署页分组筛选

- [x] 3.1 在 `Deploy.vue` 顶部添加分组下拉选择器（含「全部分组」选项）
- [x] 3.2 分组切换时，按选中的 groupId 重新拉取任务列表和服务器列表
- [x] 3.3 切换分组时清空已选任务和已选服务器
- [x] 3.4 选中「全部分组」时服务器按分组折叠显示；选中单分组时服务器平铺显示
- [x] 3.5 验证：初始加载、切换分组、任务/服务器过滤正确

## 4. 前端：Jenkins 构建号历史选择

- [x] 4.1 在 `Deploy.vue` 的 Jenkins 构建号输入框旁添加「拉取」按钮
- [x] 4.2 点击拉取按钮调用构建历史接口，弹出构建历史列表（展示构建号、状态、时间）
- [x] 4.3 点击列表中的构建项，自动将构建号填入输入框并关闭列表
- [x] 4.4 构建历史列表加载中、加载失败状态的提示处理
- [x] 4.5 手动输入构建号仍然可用，不受影响
- [x] 4.6 验证：拉取成功、点击选择、手动输入、错误提示

## 5. 联调验证

- [x] 5.1 端到端验证：进入部署页 → 选择分组 → 选择任务 → 拉取构建历史 → 选择构建号 → 触发部署
- [x] 5.2 验证边界场景：无分组、无任务、Jenkins 不可达、非 Jenkins 任务不显示拉取按钮
- [x] 5.3 前端构建并确认无编译错误
