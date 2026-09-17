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

### server ↔ agent 载荷加密（主机安全/WAF 环境）

目标机装有主机安全软件、server → agent:9009 的请求体含 shell 命令/路径明文被拦截（403 HTML 拦截页）时，两端默认启用 AES-256-GCM 载荷加密（协议 v1，请求头 `X-Payload-Version: 1`，密钥由 agent token 派生）。agent 永久双栈（无头请求仍走明文），无需 agent 侧配置。

```yaml
redeploy:
  agent:
    payload-encryption: true   # 默认 true，可用环境变量 REDEPLOY_AGENT_PAYLOAD_ENCRYPTION 覆盖
```

升级顺序：**先逐台升级 agent 二进制并重启，再升级/重启 server**。机队未全部升级期间，server 可临时设 `REDEPLOY_AGENT_PAYLOAD_ENCRYPTION=false` 走全明文（agent 无需回退）。注意：载荷加密只隐藏请求/响应体；若 WAF 拦的是 URL 词、User-Agent 或 9009 端口本身，加密无效，需另行加白或联系运维。

## 前置/后置命令的模板变量

任务的前置命令、后置命令（以及脚本步骤）中可以使用 `{{变量名}}`，部署执行前按字面量替换；未提供的变量保持原样。变量值会被 shell 正常解释，请注意引号。任务配置窗口的「可用变量」表格与下表一致：

| 变量 | 含义 | 示例 |
|------|------|------|
| `{{deploy.id}}` | 本次部署记录号（每次部署唯一） | `456` |
| `{{deploy.version}}` | 版本标识，未填时为空（Jenkins 任务默认 `#构建号`） | `#123` |
| `{{deploy.time}}` | 部署开始时间 | `20260917-094500` |
| `{{deploy.timestamp}}` | 部署开始时间戳（秒） | `1758083100` |
| `{{task.id}}` / `{{task.name}}` | 任务 ID / 任务名称 | `7` / `网关部署` |
| `{{server.name}}` / `{{server.host}}` | 目标服务器名称 / IP（每台目标分别取值） | `录像合成服务器-dat` / `10.28.3.44` |
| `{{jenkinsBuildNumber}}` | Jenkins 构建号（部署时填写） | `123` |
| `{{artifactFilename}}` | 制品文件名（Jenkins 任务自动注入） | `gateway-api.jar` |

示例（后置命令写入发布版本标记）：

```bash
mkdir -p /opt/app && echo '{{deploy.version}} {{deploy.time}} {{server.host}}' > /opt/app/RELEASE
```

注意：shell/command 步骤变量替换需要 2026-09 之后的 agent 版本；旧版 agent 仅脚本步骤支持替换。UI「调试执行」无部署上下文，不注入这些变量。

## 代理端配置

### 配置文件位置（三种启动方式，优先级从高到低）

1. **便携模式**：启动参数 `./deploy-agent -c /路径/config.yaml`（`--config` 同义）。`data_dir`、`log.dir` 留空时自动锚定到配置文件所在目录的 `data/`、`log/`，全程不访问 `/opt`，适用于无 sudo 环境，见 [Agent 无 sudo 部署指引](agent-rootless-deploy.md)。
2. **环境变量**：`AGENT_CONFIG_DIR=<目录>`，加载该目录下的 `config.yaml`。
3. **默认（systemd 一键安装）**：`/opt/deploy-agent/conf/config.yaml`。

### 完整配置示例

```yaml
server_url: http://your-server:9006   # 服务端地址（预留字段）
token:                                # 留空则首启自动生成并回写
port: 9009
data_dir: ""                          # 留空：便携模式锚定配置同级 data/，传统模式为 /opt/deploy-agent/data
cors_origin: https://deploy.example.com:50002  # 浏览器访问前端的地址，必填

log:
  dir: ""                             # 留空：便携模式锚定配置同级 log/，传统模式为 /opt/deploy-agent/log
  level: info                         # debug / info / warn / error
  max_age_days: 30                    # 0 = 永不清理

download:
  enabled: true                       # 是否允许 Server 从本机回拉文件
  allowed_paths:                      # 回拉路径白名单（glob，** 递归匹配）
    - /var/log/**
    - /opt/*/log/**
    - /tmp/redeploy-**
  max_file_size: 0                    # 字节，0 = 不限
  session_ttl: 3600                   # 拉取会话有效期（秒）
```

### 配置项说明

| 键 | 默认值 | 说明 |
|----|--------|------|
| `server_url` | 空 | re-deploy 服务端地址（当前版本为预留字段） |
| `token` | 首启自动生成 | Agent 鉴权 Token，需在 Server「服务器管理」页面注册 |
| `port` | 9009 | Agent 监听端口 |
| `data_dir` | 见上 | 制品 / 上传分片 / 临时脚本目录 |
| `cors_origin` | 内置默认值 | 浏览器跨域来源，部署时应显式填写实际前端地址 |
| `log.dir` | 见上 | 运行时 JSON 日志目录（含 `tasks/` 每任务日志） |
| `log.level` | info | 日志级别 |
| `log.max_age_days` | 30 | 日志保留天数，0 = 永不清理 |
| `download.enabled` | true | Server 从目标机回拉文件开关 |
| `download.allowed_paths` | `/var/log/**,/opt/*/log/**,/tmp/redeploy-**` | 回拉白名单（glob） |
| `download.max_file_size` | 0 | 单文件大小上限（字节） |
| `download.session_ttl` | 3600 | 拉取会话有效期（秒） |

## 环境变量

### 服务端

| 变量 | 说明 |
|------|------|
| JAVA_OPTS | JVM 参数 |
| REDEPLOY_AGENT_PAYLOAD_ENCRYPTION | server → agent 载荷加密开关（默认 true；旧版 agent 未升级时临时设 false） |

### 代理端

| 变量 | 说明 |
|------|------|
| AGENT_CONFIG_DIR | 配置目录（未使用 `-c` 参数时生效，加载其下 `config.yaml`） |
