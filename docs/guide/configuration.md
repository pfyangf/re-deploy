# 配置说明

## 服务端配置

配置文件位置：`application.yml`

### 完整配置示例

```yaml
server:
  port: 9006

spring:
  datasource:
    url: jdbc:sqlite:./data/redeploy.db

redeploy:
  admin-token: your-secure-token
  upload-dir: ./data/uploads
  agent-dir: ./data/agents
  log-retention-days: 7
  history-retention-days: 7
  
  dingtalk:
    enabled: false
    webhook-url: https://oapi.dingtalk.com/robot/send?access_token=xxx
    notify-mode: failure-only  # failure-only 或 all

logging:
  level:
    root: INFO
    com.redeploy: DEBUG
  file:
    name: ./logs/redeploy-server.log
```

## 代理端配置

配置文件位置：`/opt/deploy-agent/conf/config.yaml`

### 配置示例

```yaml
server_url: http://your-server:9006
token: agent-generated-token
port: 9009
data_dir: /opt/deploy-agent/data
```

## 环境变量

### 服务端

| 变量 | 说明 |
|------|------|
| JAVA_OPTS | JVM 参数 |

### 代理端

| 变量 | 说明 |
|------|------|
| AGENT_CONFIG_DIR | 配置目录 |
