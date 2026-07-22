## Context

re-deploy 是一个中心化部署管理系统，当前支持手动上传构建物后分发部署到多台服务器。但实际使用中用户通常通过 Jenkins 做持续集成，需要手动下载再上传非常繁琐。

同时，当前任务管理功能不完整：
- "查看"按钮没有绑定点击事件，无法查看详情
- 没有编辑功能，创建任务后无法修改
- 操作列宽度不够，多个按钮会换行
- `deploy` 步骤类型在服务端已定义但 Agent 端未实现

## Goals / Non-Goals

**Goals:**
- 新增 Jenkins 集成，支持任务级别配置 Jenkins Job，部署时输入构建号自动下载
- 实现构件自动清理，每个 Job 保留最近 3 次下载，避免磁盘占用过多
- 补全任务管理完整 CRUD：创建 → 查看 → 编辑 → 删除
- 在 Agent 端实现 `deploy` 步骤类型，将构件复制到目标部署路径
- 前端交互优化：部署页面动态显示/隐藏构建号输入框
- 修复现有 bug：查看按钮无点击事件、操作按钮换行

**Non-Goals:**
- 不支持 Jenkins webhook 自动触发部署
- 不支持多 Jenkins 实例管理（每个任务独立配置即可）
- 不支持构件解压（用户通过后置命令自己处理）
- 不改变现有大文件上传内存模型（仍然全量读入内存，沿用现有设计）

## Decisions

### 1. 数据模型设计 - Jenkins 配置存在任务级别

**Decision**: 在 `tasks` 表新增 6 个字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `jenkins_enabled` | BOOLEAN | 是否启用 Jenkins 构建物下载 |
| `jenkins_url` | VARCHAR(500) | Jenkins 根地址，如 `http://jenkins:8080` |
| `jenkins_job_name` | VARCHAR(200) | Jenkins Job 完整路径，如 `job/group/job-name` |
| `jenkins_artifact_path` | VARCHAR(500) | 构件相对路径，如 `project/target/app.war` |
| `jenkins_user` | VARCHAR(100) | Jenkins 用户名（认证用）|
| `jenkins_token` | VARCHAR(200) | Jenkins API Token |

**Rationale**: 需求要求任务级配置，每个任务可以对接不同 Jenkins 实例和不同 Job，符合用户使用场景。

### 2. 下载 URL 拼接方式

**Decision**: 按照用户提供的示例格式拼接：

```java
String url = String.format("%s/%s/%s/artifact/%s",
    jenkinsUrl, jenkinsJobName, buildNumber, jenkinsArtifactPath);
```

示例（用户提供）：
```
http://192.168.0.155:9001/job/中煤-接驳认领系统/job/iclaim-vision-api/164/artifact/media-server-api/target/media-server-api.war
[          jenkinsUrl           ][     jenkinsJobName     ][164][         jenkinsArtifactPath        ]
```

**Rationale**: 直接匹配用户实际使用的 URL 格式，用户只需要填入和 Jenkins 上一致的路径即可，最直观。

### 3. 缓存清理策略

**Decision**: 按 Job 分组存储文件，文件名格式 `{jobName}-{buildNumber}-{filename}`，每次下载新构建后：
1. 列出该 Job 所有已下载文件
2. 按最后修改时间排序
3. 如果超过 3 个，删除最旧的文件直到只剩 3 个

**Rationale**: 满足需求"保留最近 3 次"，简单有效，避免无用文件占用磁盘空间。

### 4. 部署流程设计

```
┌─────────────┐
│ 用户触发部署 │
└─────┬───────┘
      │
      ▼
┌──────────────────────┐
│ 任务启用 Jenkins?    │
│  NO → 原有流程继续   │
└─────┬────────────────┘
      │ YES
      ▼
┌──────────────────────┐
│ 有构建号参数 ?        │
│  NO → 部署失败       │
└─────┬────────────────┘
      │ YES
      ▼
┌──────────────────────┐
│ 调用 Jenkins 下载    │
└─────┬────────────────┘
      │
      ▼
┌──────────────────────┐
│ 清理旧缓存（保留 3） │
└─────┬────────────────┘
      │
      ▼
┌──────────────────────┐
│ FileTransferService  │
│ 上传到目标 Agent     │
└─────┬────────────────┘
      │
      ▼
┌──────────────────────┐
│ 添加文件路径到 params│
│ 继续原有步骤执行     │
└──────────────────────┘
```

### 5. Agent 端 deploy 步骤实现

**Decision**: `deploy` 步骤接收 `deployPath`（目标路径），将已经上传好的构件文件直接复制到 `deployPath`。如果目标路径是目录，则复制文件进去；如果是文件路径则直接覆盖。

**Rationale**: 用户要求"扩展性高一点"，简单直接复制，解压等复杂操作留给用户后置命令处理，保持灵活性。

### 6. 任务管理 CRUD 设计

- **查看**: 点击查看按钮弹出只读弹窗，展示所有任务配置信息
- **编辑**: 复用现有 `TaskDialog` 组件，支持编辑模式，打开时填充已有数据
- **操作列**: 宽度从 `200px` 调整为 `280px`，容纳三个按钮（查看/编辑/删除）不换行

### 7. 部署页面交互

**Decision**: 监听选中任务 ID 变化，从任务列表中找到该任务，检查是否启用 Jenkins，如果启用则显示"构建号"输入框，用户输入作为参数传给后端。

**Rationale**: 动态显示减少界面复杂度，只在需要时显示输入框，符合用户需求。

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| 大文件下载占用 server 内存 | 沿用现有设计 `Files.readAllBytes`，当前系统没有处理 GB 级文件需求，保持简单一致 |
| Jenkins 网络下载超时 | 由调用者异常处理，直接失败返回给用户，符合现有错误处理模式 |
| 认证信息明文存储在数据库 | 系统本身是内网工具，密码存储需求不高，当前其他密码（如 SSH）也是明文存储，保持一致 |

## Migration Plan

1. 数据库：SQLite 自动执行 `ALTER TABLE` 添加列，不需要手动迁移
2. 无需数据迁移，向前兼容，现有任务不需要 Jenkins 配置可以继续使用
3. 回滚只需停止使用新功能，数据库已新增列不影响旧代码

## Open Questions

所有设计决策已经通过探索讨论确认，没有未决问题。
