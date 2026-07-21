# 代理端 API 文档

## 认证

所有 API 请求需要在 Header 中携带 Token（健康检查除外）：

```
Authorization: Bearer <token>
```

## 健康检查

### 检查 Agent 状态

```
GET /api/health
```

**响应示例：**

```json
{
  "status": "ok"
}
```

## 系统信息

### 获取 Agent 信息

```
GET /api/info
```

**响应示例：**

```json
{
  "version": "1.0.0",
  "hostname": "server-01",
  "go_version": "go1.21.0",
  "os": "linux",
  "arch": "amd64",
  "uptime": "24h30m"
}
```

## 文件上传

### 初始化上传

```
POST /api/upload/init
```

**请求参数：**

```json
{
  "filename": "app.jar",
  "file_size": 104857600,
  "md5": "abc123..."
}
```

**响应示例：**

```json
{
  "upload_id": "uuid-string",
  "chunk_size": 5242880
}
```

### 上传分块

```
POST /api/upload/{uploadId}/chunk
```

**请求格式：** multipart/form-data

| 字段 | 类型 | 说明 |
|------|------|------|
| chunk | file | 分块文件 |
| seq | string | 分块序号（从0开始） |

**响应示例：**

```json
{
  "received_seq": "0"
}
```

### 完成上传

```
POST /api/upload/{uploadId}/complete
```

**响应示例：**

```json
{
  "status": "complete",
  "filename": "app.jar",
  "path": "/opt/deploy-agent/data/artifacts/app.jar"
}
```

### 查询上传状态

```
GET /api/upload/{uploadId}/status
```

## 任务执行

### 执行任务

```
POST /api/task/execute
```

**请求参数：**

```json
{
  "task_name": "deploy-app",
  "steps": [
    {
      "name": "stop",
      "type": "shell",
      "command": "systemctl stop myapp",
      "timeout": 30
    },
    {
      "name": "deploy",
      "type": "shell",
      "command": "cp /data/artifacts/app.jar /opt/myapp/",
      "timeout": 60
    }
  ],
  "params": {
    "version": "v1.0.0"
  }
}
```

**响应示例：**

```json
{
  "task_id": "uuid-string",
  "status": "running"
}
```

### 查询任务状态

```
GET /api/task/{taskId}/status
```

**响应示例：**

```json
{
  "id": "uuid-string",
  "task_name": "deploy-app",
  "status": "success",
  "steps": [
    {
      "name": "stop",
      "type": "shell",
      "status": "success",
      "output": "",
      "exit_code": 0
    }
  ],
  "start_time": "2024-01-01T00:00:00Z",
  "end_time": "2024-01-01T00:01:00Z"
}
```

### 取消任务

```
POST /api/task/{taskId}/cancel
```
