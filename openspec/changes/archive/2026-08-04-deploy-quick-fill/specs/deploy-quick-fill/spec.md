## ADDED Requirements

### Requirement: 部署参数自动缓存
系统 SHALL 在用户每次点击「开始部署」时，自动将当前部署表单的完整参数保存到浏览器 localStorage。

#### Scenario: 点击部署后保存缓存
- **WHEN** 用户在部署页面填写表单并点击「开始部署」按钮
- **THEN** 系统将当前表单参数（taskId、serverIds、version、jenkinsBuildNumber）保存到 localStorage
- **AND** 以 taskId 作为分组键
- **AND** 记录当前时间戳

#### Scenario: 同任务超出 3 条自动淘汰最旧
- **WHEN** 同一 taskId 下已有 3 条历史记录，用户再次点击部署
- **THEN** 系统新增当前记录为最新一条
- **AND** 删除时间最早的一条记录
- **AND** 该任务下始终保持最多 3 条记录

#### Scenario: 同参数重复部署不新增记录
- **WHEN** 用户连续两次部署使用完全相同的参数（相同 taskId、serverIds、version、jenkinsBuildNumber）
- **THEN** 系统只更新已有记录的时间戳，使其变为最新
- **AND** 不新增重复记录

### Requirement: 历史记录列表展示
系统 SHALL 在部署操作页面展示当前所选任务的最近部署历史记录列表。

#### Scenario: 选择任务后显示历史记录
- **WHEN** 用户在部署页面选择了一个任务
- **AND** 该任务在 localStorage 中有历史记录
- **THEN** 在表单项上方显示「快捷填充」区域
- **AND** 按时间倒序展示该任务的所有历史记录（最多 3 条）

#### Scenario: 无历史记录时隐藏区域
- **WHEN** 用户选择的任务没有历史记录
- **OR** 用户未选择任何任务
- **THEN** 快捷填充区域不显示

#### Scenario: 切换任务后更新列表
- **WHEN** 用户切换到另一个任务
- **THEN** 快捷填充区域的内容立即更新为新任务的历史记录

### Requirement: 一键回填表单
系统 SHALL 支持用户点击历史记录后，将记录中的参数自动回填到部署表单。

#### Scenario: 点击历史记录回填表单
- **WHEN** 用户点击某条历史记录
- **THEN** 系统将记录中的 taskId、serverIds、version、jenkinsBuildNumber 回填到表单对应字段
- **AND** 页面上对应字段的值同步更新显示

#### Scenario: 回填后 Jenkins 构建号字段正确显示
- **WHEN** 用户点击一条包含 jenkinsBuildNumber 的历史记录
- **AND** 该任务启用了 Jenkins
- **THEN** Jenkins 构建号输入框显示记录中的值

### Requirement: 历史记录信息展示
系统 SHALL 在每条历史记录中展示足够的关键信息，帮助用户快速识别。

#### Scenario: 显示版本号和服务器数量
- **WHEN** 展示历史记录列表时
- **THEN** 每条记录显示版本号（若有）
- **AND** 显示选中的服务器数量
- **AND** 显示相对时间（如「2 小时前」「昨天」）

#### Scenario: Jenkins 构建号可见
- **WHEN** 历史记录包含 jenkinsBuildNumber
- **THEN** 在记录中显示构建号信息
