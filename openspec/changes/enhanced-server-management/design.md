## Context

Re-Deploy 当前架构：
- **Server**: Spring Boot 3 + Java 17 + MyBatis + SQLite，提供REST API和静态Web UI
- **Agent**: Go 1.21 运行在目标服务器，已经支持 shell 命令执行
- **Frontend**: 静态 HTML + 原生 JavaScript，当前使用 Bootstrap 5.3.0 基础样式
- **Database**: SQLite，每次启动重新应用 schema.sql (CREATE TABLE IF NOT EXISTS)

现有服务器模型已有 `group_name` 字段，但仅是自由文本，没有完整分组管理。当前缺少 SSH 连接信息存储，不支持直接从Server端建立SSH连接。

## Goals / Non-Goals

**Goals:**
- 提供完整的分组CRUD管理，服务器和任务必须属于一个分组
- 支持存储SSH用户名密码/私钥，并使用可解密加密保护敏感信息
- 提供在线调试功能（单次命令执行）
- 提供基于网页的交互式SSH终端（堡垒机功能）
- 升级UI到更美观的AdminLTE模板，保持现有 vanilla JS 架构不变

**Non-Goals:**
- 不改变现有的Agent部署模式
- 不支持多用户权限管理（始终是单管理员模式）
- 不支持SSH会话持久化（刷新页面重新连接）
- 不引入前端构建工具（保持CDN引用方式，无需npm build）

## Decisions

### 1. 分组管理数据模型

**Decision**: 新增独立 `groups` 表，`servers` 和 `tasks` 新增 `group_id` 外键关联

**Alternatives**:
- A) 保持自由文本 `group_name` （已存在但不支持管理）→ 拒绝，需求要求完整CRUD
- B) 独立表 + 外键 → 接受，真正的分组管理

Schema:
```sql
CREATE TABLE IF NOT EXISTS groups (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name VARCHAR(100) NOT NULL UNIQUE,
  description TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

数据迁移：
- 现有服务器 `group_name` → 自动创建对应分组（如果不存在），设置 `group_id`
- 空分组 → 分配到默认 `default` 分组
- 系统初始化自动创建 `default` 分组

### 2. SSH敏感信息加密

**Decision**: 使用 AES/CBC/PKCS5Padding 对称加密，密钥通过配置参数 `redeploy.ssh-encryption-key` 配置

**Alternatives**:
- A) 明文存储 → 拒绝，密码和私钥是敏感信息
- B) 单向哈希 → 拒绝，需要解密才能使用
- C) 对称加密 → 接受，满足"需要可解密"需求

**实现**:
- Spring Boot 配置文件提供加密密钥（16/24/32字节对应AES-128/192/256）
- 提供工具类 `SshEncryptionUtils` 加密/解密
- 只有从数据库读取到内存使用时才解密，不会在API响应中返回敏感信息

### 3. 交互式堡垒机终端架构

**Decision**: Server 端作为SSH代理，WebSocket 转发 browser <-> server <-> target 数据流

```
┌──────────┐  WebSocket  ┌──────────┐     SSH      ┌──────────┐
│ Browser  │────────────▶│  Server  │────────────▶│  Target  │
│  xterm   │◀────────────│  SSH     │◀────────────│  SSHd    │
│          │   双向流     │  Client  │             │          │
└──────────┘              └──────────┘             └──────────┘
```

**Alternatives**:
- A) 直接从Browser连接到目标 → 拒绝，目标通常在内网，Browser无法直连
- B) Server作为代理转发 → 接受，符合现有架构

**依赖选择**: [JSch](https://github.com/mwiede/jsch) - 纯Java实现，活跃维护，支持所有SSH功能

**Terminal 组件**: [xterm.js](https://xtermjs.org/) - 通过CDN引入，业界标准，支持终端仿真和交互式输入

### 4. UI框架选择

**Decision**: 使用 [AdminLTE 4](https://github.com/ColorlibHQ/AdminLTE) (v4.x)，CDN引用方式

**Why AdminLTE**:
- 45.5k GitHub stars，最流行的开源后台模板
- 基于 Bootstrap 5.3，与当前版本兼容
- 无 jQuery，纯 vanilla JS，匹配我们的架构
- 支持深色模式，响应式布局
- CDN 直接引用，不需要构建步骤，对现有改动最小

**Alternatives**:
- Tailwind CSS → 需要构建流程，改变现有开发模式 → 不选
- 其他React/Vue模板 → 需要完全重构前端 → 不选，保持简单静态HTML

### 5. 在线调试功能实现

**Decision**: 复用 Agent 现有的 `ExecuteShell` 能力，通过 Server → Agent 执行命令返回结果

路径: `POST /api/servers/{id}/debug/exec`
- Request: `{ "command": "ls -la" }`
- Response: `{ "output": "...", "exitCode": 0 }`

Why: 不需要Server端直接SSH，利用现有Agent能力，更快更简单

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| AES 密钥如果丢失，所有SSH信息无法解密 → | 文档提示用户备份密钥，第一次启用生成随机密钥提示保存 |
| WebSocket 连接断开后终端状态丢失 → | 这是设计限制，用户刷新页面需要重新连接，可接受 |
| Server 作为SSH代理需要持有私钥/密码在内存 → | 用完即丢，不会持久化在内存，已加密存储在数据库，可接受 |
| AdminLTE 布局变化可能需要调整所有页面JS → | 保持现有单页JS架构，只改HTML布局结构，事件处理不变 |

## Migration Plan

1. 数据库 schema 变更自动完成（SQLite CREATE TABLE IF NOT EXISTS，ALTER TABLE ADD COLUMN）
2. 启动时自动迁移现有数据：`group_name` → 创建分组 → 设置 `group_id`
3. 默认分组 `default` 自动创建
4. 回滚：删除新增表和字段即可，现有数据不受影响

## Open Questions

None - 所有设计决策已明确。
