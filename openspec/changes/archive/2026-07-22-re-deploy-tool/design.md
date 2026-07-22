## Context

当前部署流程存在网络拓扑限制：Jenkins服务器无法直连客户服务器，开发人员作为中间桥梁需要手动下载和部署构建产物。本设计旨在构建一个自动化部署中间件（re-deploy），通过服务端-代理端架构打通整个部署链路。

**网络拓扑**：
- 开发机器（VPN）→ 客户服务器 ✓
- 开发机器 → Jenkins服务器 ✓
- Jenkins服务器 → 客户服务器 ✗

**约束条件**：
- 客户服务器为国产信创系统（Kylin Linux V10），需兼容x86_64和aarch64架构
- 代理端需轻量化，内存占用最小化
- 文件传输需支持100-200MB大文件，网络可能不稳定

## Goals / Non-Goals

**Goals:**
- 实现Jenkins构建完成后自动触发部署到客户服务器
- 支持多服务器并行部署
- 支持复杂部署流程（多步骤任务编排）
- 提供Web管理界面进行配置管理
- 支持大文件分块传输和断点续传
- 部署失败时发送钉钉告警

**Non-Goals:**
- 不实现服务端高可用（单点即可）
- 不实现灰度发布
- 不实现审批流程
- 不实现权限管理（单用户）
- 不使用WebSocket等长连接

## Decisions

### 1. 技术栈选型

**服务端：Java 17 + Spring Boot 3.x**
- 理由：生态成熟，Web界面开发便捷，团队熟悉
- 替代方案：Go（开发效率略低）、Python（性能不足）

**代理端：Go**
- 理由：编译为单二进制，内存占用小（<20MB），无运行时依赖，启动快
- 替代方案：Java（JVM开销大100-300MB）、Python（需要运行时）

**数据库：SQLite**
- 理由：内嵌数据库，无需额外安装，适合单机部署
- 替代方案：H2（Java生态更好但增加复杂度）、MySQL（需要额外安装）

### 2. 代理端部署方式

**决策：直接运行在宿主机，不使用Docker**
- 理由：需要直接操作系统（systemctl重启服务、文件操作），Docker内操作宿主机权限复杂
- 替代方案：Docker部署（权限隔离好但操作宿主机困难）

**安装方式**：一键安装脚本 + systemd管理

### 3. 文件传输方案

**决策：分块上传 + 断点续传**
- 理由：100-200MB文件在VPN不稳定环境下需要可靠性保障
- 分块大小：5MB
- 校验：MD5校验确保完整性

**流程**：
1. POST /upload/init → 返回upload_id
2. POST /upload/{id}/chunk × N次
3. POST /upload/{id}/complete → 校验MD5

### 4. 认证机制

**双Token机制**：
- **Agent Token**：每个Agent启动时自动生成，打印到日志，运维人员手动配置到服务端
- **运维Token**：全局固定Token，用于Jenkins等自动化场景调用

### 5. 端口规划

- 服务端：9006
- 代理端：9009

### 6. 日志保留策略

- 保留时间：7天
- 清理方式：服务端和Agent各自定时清理

## Risks / Trade-offs

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| VPN网络不稳定 | 大文件传输可能中断 | 分块上传+断点续传机制 |
| 代理端升级困难 | 分散在各客户服务器 | Agent内置自更新功能 |
| 并发部署资源竞争 | 多任务同时执行可能冲突 | 任务队列+并发控制 |
| 客户服务器环境差异 | 脚本执行可能失败 | 预置脚本模板+自定义脚本 |
| SQLite并发性能 | 大量并发写入可能瓶颈 | WAL模式+连接池，单机场景足够 |

## 项目结构

```
re-deploy/
├── server/                          # 服务端 (Java)
│   ├── src/main/java/com/redeploy/
│   │   ├── config/                  # 配置类
│   │   ├── controller/              # REST控制器
│   │   ├── service/                 # 业务逻辑
│   │   ├── model/                   # 数据模型
│   │   └── repository/              # 数据访问
│   ├── src/main/resources/
│   │   └── application.yml
│   └── pom.xml
│
├── agent/                           # 代理端 (Go)
│   ├── cmd/agent/                   # 入口
│   ├── internal/
│   │   ├── api/                     # HTTP API
│   │   ├── config/                  # 配置
│   │   ├── executor/                # 任务执行器
│   │   └── uploader/                # 文件上传
│   └── go.mod
│
├── scripts/                         # 安装脚本
├── docs/                            # 文档
└── README.md
```

## API设计概要

**服务端API（端口9006）**：
- 服务器管理：CRUD + 连通性测试
- 任务管理：任务模板CRUD
- 部署操作：触发部署、查询状态、部署历史
- Agent管理：注册、心跳、下载

**代理端API（端口9009）**：
- 文件上传：init → chunk → complete
- 任务执行：execute、status、cancel
- 系统信息：info、health

## Open Questions

（无，所有关键决策已确认）
