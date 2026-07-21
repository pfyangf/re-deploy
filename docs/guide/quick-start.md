# 快速开始

## 前置条件

- Java 17+
- Go 1.21+（如需编译代理端）
- 客户服务器为 Linux 系统

## 步骤 1: 启动服务端

### 下载服务端

从 Release 页面下载 `redeploy-server.jar`

### 配置服务端

编辑 `application.yml`：

```yaml
server:
  port: 9006

redeploy:
  admin-token: your-secure-token-here
```

### 启动服务端

```bash
java -jar redeploy-server.jar
```

访问 http://localhost:9006 验证服务端启动成功

## 步骤 2: 安装代理端

### 方式一：一键安装（推荐）

在客户服务器上执行：

```bash
curl -fsSL http://YOUR_SERVER_IP:9006/api/agent/install.sh | bash
```

### 方式二：手动安装

1. 下载代理端二进制：

```bash
# x86_64 架构
wget http://YOUR_SERVER_IP:9006/api/agent/download/linux/amd64 -O deploy-agent

# ARM 架构
wget http://YOUR_SERVER_IP:9006/api/agent/download/linux/arm64 -O deploy-agent
```

2. 安装：

```bash
chmod +x deploy-agent
sudo ./deploy-agent install
```

3. 获取 Token：

```bash
sudo journalctl -u deploy-agent -n 50 | grep "Agent Token"
```

## 步骤 3: 配置服务器

1. 访问 http://YOUR_SERVER_IP:9006
2. 进入「服务器管理」页面
3. 点击「添加服务器」
4. 填写服务器信息：
   - 名称：自定义名称
   - 主机：客户服务器 IP
   - 端口：9009
   - Agent Token：步骤 2 中获取的 Token

## 步骤 4: 创建部署任务

进入「任务管理」页面，创建部署任务：

```json
{
  "name": "部署示例应用",
  "taskType": "composite",
  "stepsDefinition": "[{\"name\":\"stop\",\"type\":\"shell\",\"command\":\"systemctl stop myapp\",\"timeout\":30},{\"name\":\"deploy\",\"type\":\"shell\",\"command\":\"cp /data/artifacts/app.jar /opt/myapp/\",\"timeout\":60},{\"name\":\"start\",\"type\":\"shell\",\"command\":\"systemctl start myapp\",\"timeout\":30}]"
}
```

## 步骤 5: 触发部署

### 通过 Web 界面

进入「部署操作」页面，选择任务和服务器，点击「开始部署」

### 通过 API（Jenkins 集成）

```bash
curl -X POST http://YOUR_SERVER_IP:9006/api/deploy \
  -H "Authorization: Bearer your-secure-token-here" \
  -H "Content-Type: application/json" \
  -d '{
    "taskId": 1,
    "serverIds": [1],
    "version": "v1.0.0"
  }'
```

## 查看部署结果

进入「部署历史」页面查看部署状态和日志
