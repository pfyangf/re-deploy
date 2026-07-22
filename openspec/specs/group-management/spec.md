## ADDED Requirements

### Requirement: 系统默认创建default分组
系统初始化时 SHALL 自动创建名为 `default` 的默认分组。

#### Scenario: 首次启动创建默认分组
- **WHEN** 系统首次启动
- **THEN** 数据库自动创建名为 `default` 的分组
- **AND** 该分组无法删除

#### Scenario: 非首次启动不重复创建
- **WHEN** 系统启动且default分组已存在
- **THEN** 不创建重复分组，正常启动

---

### Requirement: 分组CRUD管理
系统 SHALL 支持分组的创建、查询、修改、删除操作。

#### Scenario: 获取分组列表
- **WHEN** 客户端请求 `GET /api/groups`
- **THEN** 系统返回所有分组列表，包含id、name、description

#### Scenario: 创建分组
- **WHEN** 客户端请求 `POST /api/groups` 携带 `name` 和 `description`
- **THEN** 系统创建新分组并返回分组信息
- **AND** 分组名称唯一，重复创建返回错误

#### Scenario: 修改分组
- **WHEN** 客户端请求 `PUT /api/groups/{id}` 携带更新信息
- **THEN** 系统更新分组信息并返回成功

#### Scenario: 删除分组
- **WHEN** 客户端请求 `DELETE /api/groups/{id}` 且分组不为空（存在服务器）
- **THEN** 返回错误，不允许删除有服务器的分组

#### Scenario: 删除空分组
- **WHEN** 客户端请求 `DELETE /api/groups/{id}` 且分组为空
- **THEN** 删除该分组

---

### Requirement: 服务器必须属于一个分组
所有服务器 SHALL 必须关联一个分组，不允许无分组。

#### Scenario: 新建服务器未指定分组
- **WHEN** 创建新服务器时未指定分组
- **THEN** 自动分配到 `default` 分组

#### Scenario: 已有服务器无分组
- **WHEN** 系统启动发现已有服务器 `group_id` 为 NULL
- **THEN** 自动分配到 `default` 分组

---

### Requirement: 服务器和任务按分组筛选
系统 SHALL 在服务器列表和任务列表支持按分组筛选。

#### Scenario: 按分组筛选服务器
- **WHEN** 用户在服务器管理页面选择某个分组
- **THEN** 只显示该分组下的服务器

#### Scenario: 按分组筛选任务
- **WHEN** 用户在任务管理页面选择某个分组
- **THEN** 只显示该分组下的任务

---

### Requirement: 分组选择下拉框
创建/编辑服务器和任务时 SHALL 提供分组下拉选择框，必填项。

#### Scenario: 显示分组选项
- **WHEN** 打开添加服务器模态框
- **THEN** 显示分组下拉选择框，列出所有分组
- **AND** 默认选中 `default` 分组

#### Scenario: 必须选择分组
- **WHEN** 用户提交表单且未选择分组
- **THEN** 表单验证失败，提示必须选择分组
