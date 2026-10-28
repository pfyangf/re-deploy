# Re-Deploy

Re-Deploy 是部署中间件：服务端编排任务，目标机上的代理执行命令并接收制品。Jenkins 或浏览器不直连客户服务器。

```
Jenkins / 浏览器 / 智能体
        │  HTTP :9006
        ▼
   服务端（Java + Web 界面）
        │  HTTP :9009
        ▼
   代理端（Go，跑在目标 Linux 上）
```

## 能做什么

- 按分组管理服务器，多机并行部署，查看分段日志
- 任务步骤编排，对接 Jenkins 制品
- 大文件分块上传
- 账号、角色、菜单权限
- 给智能体签发可吊销的 API 密钥
- 失败告警、从目标机拉取诊断文件、浏览器 SSH

## 组成

| 部分 | 技术 | 端口 |
|---|---|---|
| `server/` | Java 17、Spring Boot 3.2、MyBatis、MySQL 8 | 9006 |
| `frontend/` | Vue 3、Element Plus、Vite，构建进服务端静态资源 | 开发时 3000 |
| `agent/` | Go 1.21 | 9009 |

版本号以 `server/pom.xml` 的 `<version>` 为准。MySQL 需单独准备，连不上时服务端不会启动。

## 快速开始

生产环境用 Docker Compose，步骤和变量见 [构建与部署指南](docs/guide/deployment.md)。

```bash
cp .env.example .env
# 编辑 REDEPLOY_ADMIN_TOKEN，并准备好 MySQL
docker compose up -d
```

本地开发：

```bash
# Windows：交叉编译代理、打包服务端
./scripts/build.ps1

# 服务端
cd server
mvn spring-boot:run

# 前端热更新（接口代理到 localhost:9006）
cd frontend
npm run dev
```

在目标机安装代理：

```bash
curl -fsSL http://YOUR_SERVER_IP:9006/api/agent/install.sh | bash -s -- \
  --server http://YOUR_SERVER_IP:9006
```

没有 sudo 时看 [便携模式部署](docs/guide/agent-rootless-deploy.md)。装好后到 Web 的「服务器管理」登记代理令牌。

## 给智能体用的 Skill

仓库自带 Cursor Skill：`.cursor/skills/redeploy/`。用 Cursor 打开本仓库时会自动加载。在别的项目里用，把这个目录复制到个人技能目录：

```text
~/.cursor/skills/redeploy/
```

Windows 上是 `%USERPROFILE%\.cursor\skills\redeploy\`。

Skill 通过环境变量调用服务端，不要把密钥写进 Skill 或仓库：

```bash
REDEPLOY_BASE_URL=http://127.0.0.1:9006
REDEPLOY_API_KEY=rdk_...
```

密钥在 Web「权限 → API 密钥」创建，明文只显示一次。它能查询业务数据、新增分组/服务器/任务，以及发起、取消部署并查看状态和日志。不能修改、删除，也不能操作系统设置、用户、角色、菜单和远程命令。允许的路径见 [`.cursor/skills/redeploy/reference.md`](.cursor/skills/redeploy/reference.md)。

调用时使用 `Authorization: Bearer <REDEPLOY_API_KEY>`。这和管理员令牌、网页登录会话是分开的。

## 文档

- [构建与部署](docs/guide/deployment.md)
- [快速开始](docs/guide/quick-start.md)
- [安装](docs/guide/installation.md)
- [配置](docs/guide/configuration.md)
- [通知](docs/guide/notification.md)
- [远端下载](docs/guide/remote-file-download.md)
- [服务端 API](docs/api/server-api.md)
- [代理端 API](docs/api/agent-api.md)
- [FAQ](docs/faq.md)

## License

MIT
