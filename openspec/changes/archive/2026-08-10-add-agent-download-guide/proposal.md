## Why

用户在添加新服务器时，需要先在目标机器上安装部署 Agent，但目前前端没有任何 Agent 下载或安装指引的入口。用户必须手动拼接下载 URL 或查阅外部文档，新手门槛高、流程断裂。需要在服务器管理页面提供一个直观的 Agent 安装指南入口，让下载、安装、获取 Token 的流程完整闭环。

## What Changes

- 服务器管理页面工具栏新增"下载 Agent"按钮
- 点击按钮弹出"Agent 安装指南"对话框，包含：
  - 第一步：下载二进制文件的命令（支持 amd64/arm64 切换）
  - 第二步：配置 systemd 服务的完整命令
  - 第三步：查看 Token 的命令
  - 常用运维命令参考
  - 目录结构说明
- 所有命令中的 server 地址由前端根据当前页面 origin 自动拼接，用户复制即用
- 对话框底部提供"去添加服务器"快捷入口，引导用户完成注册

## Capabilities

### New Capabilities

- `agent-install-guide`：前端 UI 中 Agent 安装指南弹窗，包含下载命令、systemd 配置、Token 查看、常用命令和目录结构说明

### Modified Capabilities

- 无

## Impact

- 前端：服务器管理页面（Vue 组件）增加按钮和对话框
- 后端：无代码变更（下载接口 `/api/agent/download/{os}/{arch}` 已存在）
- 数据库：无变更
