# 安装部署

## 服务端安装

### 系统要求

- Java 17+
- 2GB+ RAM
- 10GB+ 磁盘空间

### 安装步骤

1. 下载 `redeploy-server.jar`
2. 创建配置文件 `application.yml`
3. 启动服务：`java -jar redeploy-server.jar`

### 配置项说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| server.port | 9006 | 服务端口 |
| redeploy.admin-token | - | 管理Token |
| redeploy.upload-dir | ./data/uploads | 上传目录 |
| redeploy.log-retention-days | 7 | 日志保留天数 |
| redeploy.dingtalk.enabled | false | 钉钉告警开关 |
| redeploy.dingtalk.webhook-url | - | 钉钉Webhook地址 |

## 代理端安装

### 系统要求

- Linux (x86_64 或 aarch64)
- 64MB+ RAM
- 1GB+ 磁盘空间

### 一键安装

```bash
curl -fsSL http://SERVER:9006/api/agent/install.sh | bash
```

### 手动安装

```bash
# 下载
wget http://SERVER:9006/api/agent/download/linux/amd64 -O deploy-agent

# 安装
chmod +x deploy-agent
sudo ./deploy-agent install

# 启动
sudo systemctl start deploy-agent
sudo systemctl enable deploy-agent
```

### 无 sudo 环境（nohup 便携模式）

目标机不提供 sudo / root、无法写 `/opt` 或注册 systemd 服务时，Agent 可整体部署在普通用户家目录下，通过 `-c` 指定配置文件、以 nohup 方式后台运行，配置中的 `data_dir`、`log.dir` 缺省自动锚定到配置文件所在目录。

详见 [Agent 无 sudo 部署指引](agent-rootless-deploy.md)，仓库 `scripts/agent/` 目录提供启停脚本与完整 `config.yaml` 模板。

### 卸载

```bash
curl -fsSL http://SERVER:9006/api/agent/install.sh | bash -s -- --uninstall
```

或手动卸载：

```bash
sudo systemctl stop deploy-agent
sudo systemctl disable deploy-agent
sudo rm -rf /opt/deploy-agent
sudo rm /etc/systemd/system/deploy-agent.service
sudo systemctl daemon-reload
```
