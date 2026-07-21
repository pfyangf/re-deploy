## Why

当前部署流程存在瓶颈：Jenkins服务器无法直接连接客户服务器，需要开发人员手动下载构建产物（100-200MB）再手动部署到客户服务器。这种手动流程效率低、易出错，且无法满足多服务器并行部署的需求。

需要开发一个自动化部署中间件，打通Jenkins到客户服务器的部署链路，实现构建完成后自动触发部署。

## What Changes

- 新增**服务端**（Java Spring Boot），部署在开发机器上，提供Web管理界面和REST API
- 新增**代理端**（Go），部署在每个客户服务器上，负责接收文件、执行脚本、重启服务等操作
- 支持Jenkins构建完成后通过curl触发自动部署
- 支持100-200MB构建产物的分块上传和断点续传
- 支持多服务器并行部署
- 支持复杂部署流程：前置脚本 → 上传文件 → 部署脚本 → 重启服务 → 健康检查
- 部署失败时发送钉钉告警通知

## Capabilities

### New Capabilities

- `server-management`: 服务端服务器管理、配置管理Web界面、任务调度引擎
- `agent-management`: 代理端安装部署、心跳监控、自动注册
- `file-transfer`: 构建产物分块上传、断点续传、MD5校验
- `task-execution`: 部署任务定义、多步骤执行、并行调度
- `deploy-history`: 部署历史记录、日志查看、状态追踪
- `alert-notification`: 部署失败钉钉告警通知

### Modified Capabilities

（无，这是全新项目）

## Impact

- **新增代码**：服务端Java项目、代理端Go项目、安装脚本、文档
- **运行环境**：服务端运行在Windows开发机器，代理端运行在Linux客户服务器（Kylin Linux V10兼容）
- **网络要求**：开发机器需同时连接Jenkins服务器和客户服务器（VPN）
- **依赖**：Java 17+、Go 1.21+、SQLite
- **端口**：服务端9006、代理端9009
