## 1. 数据库变更

- [x] 1.1 在 `schema.sql` 添加 `groups` 表创建语句
- [x] 1.2 在 `servers` 表添加 `group_id`、`ssh_username`、`ssh_password`、`ssh_private_key`、`ssh_port` 字段（ALTER TABLE）
- [x] 1.3 在 `tasks` 表添加 `group_id` 字段
- [x] 1.4 添加数据迁移逻辑：启动时自动将现有服务器 `group_name` 迁移到分组

## 2. 后端 - 分组管理

- [x] 2.1 创建 `com.redeploy.model.Group` 模型类
- [x] 2.2 创建 `GroupMapper` 接口（MyBatis注解）
- [x] 2.3 创建 `GroupController` 实现CRUD API
  - GET /api/groups - 获取列表
  - POST /api/groups - 创建
  - PUT /api/groups/{id} - 更新
  - DELETE /api/groups/{id} - 删除
- [x] 2.4 添加数据初始化：启动自动创建 default 分组
- [x] 2.5 修改 `ServerController` 和 `TaskController` 添加分组筛选支持
- [x] 2.6 修改创建服务器逻辑：未指定分组时默认分配到 default

## 3. 后端 - SSH加密和模型更新

- [x] 3.1 修改 `Server` 模型添加SSH相关字段
- [x] 3.2 创建 `SshEncryptionUtils` 加密/解密工具类（AES）
- [x] 3.3 添加配置项 `redeploy.ssh-encryption-key` 到 `application.yml`
- [x] 3.4 启动时检查密钥，未配置自动生成并打印日志
- [x] 3.5 修改 `ServerMapper`，确保查询/插入包含新字段
- [x] 3.6 修改API响应，排除 `sshPassword` 和 `sshPrivateKey`

## 4. 后端 - 服务器调试功能

- [x] 4.1 在 `ServerController` 添加 `POST /api/servers/{id}/debug/exec` 端点
- [x] 4.2 实现命令转发到Agent执行，返回输出结果

## 5. 后端 - 堡垒机WebSocket终端

- [x] 5.1 添加JSch依赖到pom.xml
- [x] 5.2 创建 `SshSessionManager` 管理SSH连接池
- [x] 5.3 创建 `BastionWebSocketHandler` 处理WebSocket连接
- [x] 5.4 实现WebSocket消息转发：browser → SSH → browser
- [x] 5.5 处理窗口大小变化通知
- [x] 5.6 连接关闭时释放SSH资源
- [x] 5.7 配置WebSocket端点

## 6. 前端 - UI升级到AdminLTE 4

- [x] 6.1 修改 `index.html` 布局结构，改用AdminLTE 4 CDN和布局
- [x] 6.2 添加侧边栏"分组管理"菜单项
- [x] 6.3 添加深色模式切换按钮
- [x] 6.4 调整CSS样式适配AdminLTE
- [ ] 6.5 验证所有原有页面功能正常

## 7. 前端 - 分组管理页面

- [x] 7.1 实现 `loadGroups()` 函数加载分组列表
- [x] 7.2 实现添加/编辑/删除分组模态框
- [x] 7.3 实现删除分组校验（非空分组不能删除）
- [x] 7.4 在服务器列表和任务列表添加分组筛选下拉框
- [x] 7.5 在添加服务器/任务表单修改分组为下拉选择（必填）

## 8. 前端 - 调试和堡垒机功能

- [x] 8.1 在服务器列表添加"调试"按钮
- [x] 8.2 实现调试模态框，命令输入和结果显示
- [x] 8.3 在服务器列表添加"终端"按钮
- [x] 8.4 根据SSH配置是否完整启用/禁用按钮
- [x] 8.5 集成xterm.js via CDN
- [x] 8.6 实现终端模态框，WebSocket连接管理
- [x] 8.7 处理终端输入输出转发
- [x] 8.8 处理窗口大小变化自适应

## 9. 测试验证

- [ ] 9.1 验证分组CRUD功能正常
- [ ] 9.2 验证SSH加密存储解密正常
- [ ] 9.3 验证调试命令执行正常
- [ ] 9.4 验证交互式SSH终端正常工作
- [ ] 9.5 验证AdminLTE UI显示正常，深色模式切换正常
- [ ] 9.6 验证原有功能保持正常
