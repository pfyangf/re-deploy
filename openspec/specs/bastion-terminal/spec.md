## ADDED Requirements

### Requirement: 网页交互式SSH终端
对于已配置SSH信息的服务器，系统 SHALL 支持在网页端打开交互式SSH终端。

#### Scenario: 打开终端按钮
- **WHEN** 服务器已配置SSH信息（用户名 + 密码/私钥）
- **THEN** 服务器列表显示"终端"按钮

#### Scenario: 打开终端按钮不可用
- **WHEN** 服务器未配置完整SSH信息
- **THEN** "终端"按钮禁用，鼠标悬浮提示"请先配置SSH信息"

#### Scenario: 打开终端会话
- **WHEN** 用户点击"终端"按钮
- **THEN** 打开新模态框，显示xterm.js终端
- **AND** 系统建立WebSocket连接
- **AND** 系统连接目标服务器SSH
- **AND** 登录成功后显示shell提示符

#### Scenario: 用户输入
- **WHEN** 用户在终端按下按键
- **THEN** 按键数据通过WebSocket发送到Server
- **AND** Server转发到SSH会话
- **AND** 输出回显到浏览器终端

#### Scenario: 服务器输出
- **WHEN** SSH会话有输出
- **THEN** Server通过WebSocket转发到浏览器
- **AND** 输出显示在终端中

#### Scenario: 关闭终端
- **WHEN** 用户关闭模态框
- **THEN** WebSocket关闭
- **AND** SSH会话断开连接
- **AND** 释放Server端资源

#### Scenario: WebSocket断开
- **WHEN** WebSocket连接异常断开
- **THEN** Server端主动关闭SSH会话释放资源

---

### Requirement: 支持全交互
终端 SHALL 支持完整的交互式操作，包括但不限于：
- vim文本编辑
- top/htop进程查看
- sudo需要密码交互

**说明**: 这是真正的PTY终端，不是简单命令执行。

#### Scenario: 分配PTY
- **WHEN** 建立SSH会话
- **THEN** 请求分配伪终端(PTY)
- **AND** 设置正确的终端环境变量

---

### Requirement: 终端尺寸自适应
终端 SHALL 适应模态框大小，窗口大小变化时通知SSH端。

#### Scenario: 窗口大小改变
- **WHEN** 用户调整浏览器窗口大小或模态框尺寸
- **THEN** 终端自动调整尺寸
- **AND** 发送新尺寸到SSH服务器端
