## ADDED Requirements

### Requirement: 服务器在线调试命令执行
系统 SHALL 支持在服务器管理页面直接向Agent发送shell命令执行并查看结果。

#### Scenario: 打开调试模态框
- **WHEN** 用户在服务器列表点击"调试"按钮
- **THEN** 打开调试命令输入模态框
- **AND** 输入框默认聚焦

#### Scenario: 执行命令成功
- **WHEN** 用户输入命令 `ls -la` 并点击执行
- **THEN** 系统通过Agent执行命令
- **AND** 在页面显示命令输出和退出码

#### Scenario: 执行命令超时
- **WHEN** 命令执行超过60秒
- **THEN** 返回超时错误
- **AND** 显示错误信息给用户

#### Scenario: 命令执行失败
- **WHEN** 命令执行返回非0退出码
- **THEN** 仍然显示输出内容
- **AND** 标明退出码非0

---

### Requirement: 调试API端点
系统 SHALL 提供 `POST /api/servers/{id}/debug/exec` 端点执行调试命令。

#### Scenario: API请求格式
- **WHEN** 客户端POST请求 `/api/servers/{id}/debug/exec`
- **AND** 请求体 `{ "command": "ls" }`
- **THEN** 返回 `{ "output": "...", "exitCode": 0, "success": true }`

#### Scenario: 服务器不在线
- **WHEN** 请求执行命令但Agent不在线
- **THEN** 返回错误 `connected: false` 和错误信息
