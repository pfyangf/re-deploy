# 服务端 API 文档

## 认证

所有 API 请求需要在 Header 中携带 Token：

```
Authorization: Bearer <token>
```

## 服务器管理

### 获取服务器列表

```
GET /api/servers
```

**响应示例：**

```json
[
  {
    "id": 1,
    "name": "生产服务器A",
    "host": "192.168.1.100",
    "port": 9009,
    "status": "online",
    "groupName": "production"
  }
]
```

### 添加服务器

```
POST /api/servers
```

**请求参数：**

```json
{
  "name": "服务器名称",
  "host": "192.168.1.100",
  "port": 9009,
  "agentToken": "agent-token",
  "groupName": "production",
  "description": "描述"
}
```

### 测试连通性

```
POST /api/servers/{id}/test
```

## 任务管理

### 获取任务列表

```
GET /api/tasks
```

### 创建任务

```
POST /api/tasks
```

**请求参数：**

```json
{
  "name": "部署任务名称",
  "description": "任务描述",
  "taskType": "composite",
  "stepsDefinition": "[{\"name\":\"stop\",\"type\":\"shell\",\"command\":\"systemctl stop myapp\",\"timeout\":30}]"
}
```

## 部署操作

### 触发部署

```
POST /api/deploy
```

**请求参数：**

```json
{
  "taskId": 1,
  "serverIds": [1, 2, 3],
  "version": "v1.0.0",
  "params": {
    "key": "value"
  }
}
```

**响应示例：**

```json
{
  "deployId": 1,
  "status": "running"
}
```

### 查询部署状态

```
GET /api/deploy/{id}/status
```

### 部署历史

```
GET /api/deploy/history
```

### 取消部署

```
POST /api/deploy/{id}/cancel
```

## 构建产物

### 产物列表

```
GET /api/artifacts
```

### 下载产物

```
GET /api/artifacts/{id}/download
```

## Agent 管理

### Agent 列表

```
GET /api/agents
```

### 下载 Agent

```
GET /api/agent/download/{os}/{arch}
```

### 获取安装脚本

```
GET /api/agent/install.sh
```
