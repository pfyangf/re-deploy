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
