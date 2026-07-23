# Re-Deploy - 自动化部署平台

Re-Deploy 是一个轻量级的自动化部署中间件，用于解决 Jenkins 无法直接连接客户服务器的部署场景。

## 架构

```
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│   Jenkins    │ ──────► │   服务端     │ ──────► │   代理端     │
│   服务器     │  curl   │  (Java)      │  HTTP   │   (Go)       │
└──────────────┘         └──────────────┘         └──────────────┘
                              开发机器                  客户服务器
```

## 特性

- Jenkins 构建完成后自动触发部署
- 支持 100-200MB 大文件分块上传和断点续传
- 支持多服务器并行部署
- 支持复杂部署流程（多步骤任务编排）
- Web 管理界面
- 部署失败钉钉告警通知

## 快速开始

> 生产部署建议直接看 [构建与部署指南](docs/guide/deployment.md)（Docker Compose 一键起）。以下为本地开发运行方式。

### 1. 启动服务端

```bash
# Windows
java -jar redeploy-server.jar

# Linux
java -jar redeploy-server.jar
```

服务端将在 http://localhost:9006 启动

### 2. 安装代理端

在客户服务器上执行：

```bash
curl -fsSL http://YOUR_SERVER_IP:9006/api/agent/install.sh | bash -s -- \
  --server http://YOUR_SERVER_IP:9006
```

安装完成后，从日志中获取 Agent Token：

```bash
journalctl -u deploy-agent -n 50
```

### 3. 配置服务器

访问 http://YOUR_SERVER_IP:9006，在服务器管理页面添加客户服务器，填入 Agent Token。

### 4. 创建部署任务

在任务管理页面创建部署任务，定义部署步骤：

```json
[
  {"name": "stop", "type": "shell", "command": "systemctl stop myapp", "timeout": 30},
  {"name": "deploy", "type": "shell", "command": "cp /data/artifacts/app.jar /opt/myapp/", "timeout": 60},
  {"name": "start", "type": "shell", "command": "systemctl start myapp", "timeout": 30},
  {"name": "health", "type": "shell", "command": "curl -f http://localhost:8080/health", "timeout": 30}
]
```

### 5. 触发部署

通过 Jenkins 或 Web 界面触发部署：

```bash
curl -X POST http://YOUR_SERVER_IP:9006/api/deploy \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "taskId": 1,
    "serverIds": [1, 2, 3],
    "version": "v1.0.0"
  }'
```

## 技术栈

- **服务端**: Java 17, Spring Boot 3.x, SQLite
- **代理端**: Go 1.21+
- **前端**: Bootstrap 5, JavaScript

## 端口

- 服务端: 9006
- 代理端: 9009

## 文档

- [构建与部署指南](docs/guide/deployment.md)
- [快速开始](docs/guide/quick-start.md)
- [安装部署](docs/guide/installation.md)
- [配置说明](docs/guide/configuration.md)
- [用户手册](docs/guide/user-guide.md)
- [服务端 API](docs/api/server-api.md)
- [代理端 API](docs/api/agent-api.md)
- [FAQ](docs/faq.md)

## License

MIT
