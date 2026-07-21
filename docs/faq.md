# 常见问题

## 安装问题

### Q: Agent 安装失败怎么办？

A: 检查以下几点：
1. 确认网络连接正常
2. 确认服务端已启动
3. 检查是否有 sudo 权限
4. 查看错误日志：`journalctl -u deploy-agent -n 50`

### Q: 如何在 ARM 架构服务器上安装？

A: 安装脚本会自动检测架构。如果自动检测失败，手动下载 ARM 版本：

```bash
wget http://SERVER:9006/api/agent/download/linux/arm64 -O deploy-agent
```

## 使用问题

### Q: 如何获取 Agent Token？

A: 查看 Agent 日志：

```bash
journalctl -u deploy-agent -n 50 | grep "Agent Token"
```

### Q: 部署失败如何排查？

A: 
1. 查看部署历史页面的详细日志
2. 检查 Agent 日志：`journalctl -u deploy-agent -f`
3. 检查服务端日志：`./logs/redeploy-server.log`

### Q: 如何配置钉钉告警？

A: 编辑 `application.yml`：

```yaml
redeploy:
  dingtalk:
    enabled: true
    webhook-url: https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN
    notify-mode: failure-only
```

### Q: 大文件上传失败怎么办？

A: 
1. 检查网络连接稳定性
2. 确认磁盘空间充足
3. 上传支持断点续传，可以重试

### Q: 如何升级 Agent？

A: 

```bash
# 下载新版本
wget http://SERVER:9006/api/agent/download/linux/amd64 -O deploy-agent-new

# 替换
sudo systemctl stop deploy-agent
sudo mv deploy-agent-new /opt/deploy-agent/bin/deploy-agent
sudo chmod +x /opt/deploy-agent/bin/deploy-agent
sudo systemctl start deploy-agent
```

## 配置问题

### Q: 如何修改服务端端口？

A: 编辑 `application.yml`：

```yaml
server:
  port: 8080  # 修改为其他端口
```

### Q: 如何修改 Agent 端口？

A: 编辑 `/opt/deploy-agent/conf/config.yaml`：

```yaml
port: 9090  # 修改为其他端口
```

然后重启 Agent：`sudo systemctl restart deploy-agent`

### Q: 数据库在哪里？

A: 服务端使用 SQLite，数据库文件位于 `./data/redeploy.db`
