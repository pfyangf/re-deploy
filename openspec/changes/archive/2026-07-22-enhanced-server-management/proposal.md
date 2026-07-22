## Why

当前 Re-Deploy 平台缺少分组管理功能，大量服务器无法有效组织；用户无法直接在网页端对服务器进行调试和SSH管理；原界面样式过于简陋，用户体验不佳。本次变更增强服务器管理能力，添加分组管理、在线调试、交互式SSH堡垒机功能，并升级UI界面。

## What Changes

1. **新增完整分组管理功能**
   - 独立 `groups` 表支持分组CRUD操作
   - 默认自动创建 `default` 分组
   - 服务器和任务必须关联分组，必填项
   - 列表页支持按分组筛选

2. **服务器新增SSH连接信息**
   - 新增SSH用户名、密码、私钥、端口字段
   - 敏感信息**可解密AES加密存储**
   - 所有字段为可选项，保持向后兼容

3. **服务器调试功能**
   - 在服务器管理页面新增"调试"按钮
   - 可以直接输入shell命令并查看返回结果
   - 复用现有Agent命令执行能力

4. **新增交互式堡垒机功能**
   - 对于已配置SSH信息的服务器，可以直接在网页端打开交互式终端
   - 基于WebSocket + xterm.js 实现完整的SSH交互
   - 支持vim、top、sudo等交互式操作

5. **UI界面升级**
   - 采用最流行的开源后台模板 **AdminLTE 4**
   - 基于Bootstrap 5，原生支持深色模式
   - 更好的布局、配色和用户体验

## Capabilities

### New Capabilities
- `group-management`: 完整分组管理CRUD功能
- `server-ssh-auth`: 服务器SSH认证信息存储与加密管理
- `server-debug`: 服务器在线调试命令执行功能
- `bastion-terminal`: 交互式网页SSH堡垒机终端
- `ui-adminlte-upgrade`: AdminLTE界面升级

### Modified Capabilities
- 无（现有能力保持兼容，仅新增字段和选项）

## Impact

- **数据库**: 新增 `groups` 表，`servers` 表新增字段
- **Server (Java)**: 新增Group相关模型、Mapper、Controller；新增SSH加密工具类；新增WebSocket端点处理堡垒机终端；修改ServerController新增调试API
- **Agent (Go)**: 无变化，调试功能复用现有的shell执行能力
- **Frontend**: 完整重构index.html和app.js布局，采用AdminLTE框架；新增分组管理页面；新增终端UI组件
- **Dependencies**: Server端新增SSH client依赖（JSch或Apache SSHD）；前端新增xterm.js通过CDN引入
