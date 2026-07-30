## Why

当前部署操作页面（/deploy）效率不高：任务和服务器列表全量加载，分组多的时候找起来慢；Jenkins 构建号需要手动输入，容易输错也不知道最近有哪些构建。本次优化聚焦于部署操作页的两个高频痛点：按分组快速筛选、一键拉取 Jenkins 构建历史，降低操作成本、减少人工输入错误。

## What Changes

- 部署操作页面顶部增加「分组」下拉选择器，默认「全部分组」；选择分组后，任务列表和服务器列表同步过滤为该分组下的内容
- 切换分组时，清空已选中的任务和服务器，避免跨分组误选
- 服务器列表在选中单分组时平铺显示（不折叠），选中「全部」时保持按分组折叠
- 新增后端 API：通过任务 ID 获取该任务关联的 Jenkins 构建历史列表（代理调用 Jenkins JSON API）
- 部署页面中，当选中的任务启用了 Jenkins 时，Jenkins 构建号输入框旁增加「拉取」按钮，点击弹出构建历史列表，点击某条自动填入构建号
- 构建历史列表展示构建号、状态（成功/失败）、时间、描述，最新构建在最上，默认拉取最近 20 条
- 构建号输入框仍保留手动输入能力

## Capabilities

### New Capabilities

- `deploy-page-group-filter`：部署页按分组筛选任务和服务器的交互能力
- `jenkins-build-history-lookup`：查询 Jenkins 任务构建历史列表并用于部署页构建号选择的能力

### Modified Capabilities

- `jenkins-build-integration`：在 Jenkins 集成中新增构建历史查询能力，构建号选择方式从纯手动输入扩展为"手动输入 + 历史列表选择"

## Impact

- **前端**：`Deploy.vue` 页面重写交互逻辑（分组联动、构建号下拉）；`api/client.js` 新增接口
- **后端**：`DeployController` 或新增 `JenkinsController`，增加构建历史查询接口；`JenkinsService` 增加获取构建列表方法
- **数据库**：无表结构变更
- **兼容性**：完全向后兼容，现有部署流程不受影响
