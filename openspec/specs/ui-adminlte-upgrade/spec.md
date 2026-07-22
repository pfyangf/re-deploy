## ADDED Requirements

### Requirement: 升级到AdminLTE 4界面
系统前端界面 SHALL 使用 AdminLTE 4 模板重新设计，保持现有的单页JavaScript架构不变。

#### Scenario: CDN引用
- **WHEN** 页面加载
- **THEN** AdminLTE CSS和JS通过jsdelivr CDN加载
- **AND** 不需要本地构建

#### Scenario: 保持功能不变
- **WHEN** 升级完成后
- **THEN** 所有现有功能（仪表盘、服务器管理、任务管理、部署等）正常工作
- **AND** API调用方式不变

---

### Requirement: 深色模式支持
系统 SHALL 支持深色模式切换。

#### Scenario: 切换深色模式
- **WHEN** 用户点击切换主题按钮
- **THEN** 页面在浅色/深色模式之间切换
- **AND** 用户偏好保存到localStorage

#### Scenario: 记住用户偏好
- **WHEN** 用户刷新页面
- **THEN** 使用用户上次保存的主题偏好

---

### Requirement: 响应式布局
界面 SHALL 支持响应式布局，适配不同屏幕尺寸。

#### Scenario: 移动端访问
- **WHEN** 用户在手机屏幕访问
- **THEN** 侧边栏自动折叠
- **AND** 内容正常显示

---

### Requirement: 分组管理页面
系统 SHALL 在侧边栏添加"分组管理"菜单项。

#### Scenario: 导航到分组管理
- **WHEN** 用户点击侧边栏"分组管理"
- **THEN** 加载分组管理页面
- **AND** 显示分组列表和CRUD操作按钮
