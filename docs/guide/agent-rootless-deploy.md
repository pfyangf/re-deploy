# Agent 无 sudo 部署指引（nohup 便携模式）

适用场景：目标服务器**不提供 sudo / root 权限**，无法写 `/opt`、无法注册 systemd 服务。Agent 以普通用户身份运行在用户家目录下，通过 `nohup` 后台常驻。

> 有 sudo 的环境仍推荐[一键安装（systemd）](installation.md#代理端安装)，自启与拉起更省心。两种模式的 Agent 二进制相同、与 Server 的通信协议完全一致。

## 1. 前置条件

| 条件 | 说明 |
|------|------|
| 普通用户账号 | 对部署目录（如 `~/deploy-agent`）有读写执行权限 |
| 架构 | Linux x86_64（amd64）或 aarch64（arm64） |
| 可用端口 | Agent 默认监听 **9009**，端口未被占用 |
| 网络可达 | 目标机能访问 re-deploy Server（默认 9006）；Server 也能访问目标机的 `IP:9009`（部署时 Server 主动连 Agent） |
| 基础命令 | `bash`、`curl`/`wget`、`nohup`（一般系统自带）；开机自启需要 `crontab` |

确认架构：

```bash
uname -m    # x86_64 → amd64；aarch64 → arm64
```

## 2. 目录布局

```
~/deploy-agent/                # 部署目录，名字和位置随意，整个目录可整体拷贝/删除
├── deploy-agent               # Agent 二进制
├── config.yaml                # 配置文件（启动时用 -c 指定）
├── start.sh                   # 启动脚本
├── stop.sh                    # 停止脚本
├── status.sh                  # 状态脚本
├── agent.pid                  # 运行后自动生成
├── agent.out                  # 运行后自动生成（启动输出，首启 Token 在这里）
├── data/                      # 首启自动生成：artifacts / uploads / scripts
└── log/                       # 首启自动生成：agent-YYYY-MM-DD.log 与 tasks/
```

便携模式下，`config.yaml` 中 `data_dir`、`log.dir` 留空时自动锚定到**配置文件所在目录**的 `data/`、`log/`，不会访问 `/opt`。

## 3. 安装步骤

以下命令假设 Server 地址为 `http://<SERVER>:9006`，请替换为实际地址。

```bash
# 3.1 创建部署目录
mkdir -p ~/deploy-agent && cd ~/deploy-agent

# 3.2 下载二进制（uname -m 为 x86_64 用 amd64，aarch64 用 arm64）
curl -fsSL http://<SERVER>:9006/api/agent/download/linux/amd64 -o deploy-agent
chmod +x deploy-agent
```

然后将仓库 `scripts/agent/` 下的三个脚本和配置模板上传到该目录（或按下文第 4 节手工创建 `config.yaml`）：

```
scripts/agent/start.sh
scripts/agent/stop.sh
scripts/agent/status.sh
scripts/agent/config.yaml
```

上传方式不限：`scp`、SFTP 工具、或直接在服务器上新建文件粘贴内容。上传后赋权：

```bash
chmod +x start.sh stop.sh status.sh
```

编辑 `config.yaml`，**至少修改 `cors_origin`**（填实际访问 re-deploy 前端的浏览器地址，协议+域名+端口）；`token`、`data_dir`、`log.dir` 保持留空。

## 4. 完整 config.yaml

```yaml
# re-deploy Agent 配置文件（无 sudo / nohup 便携模式）
# 启动方式：./deploy-agent -c /绝对路径/config.yaml
# data_dir / log.dir 留空时，自动使用本文件所在目录下的 data/ 与 log/

# re-deploy 服务端地址（当前版本为预留字段，可暂时留空）
server_url: http://<SERVER>:9006

# Agent 鉴权 Token：【留空即可】首次启动自动生成并回写本文件。
# 启动后从 agent.out 提取：grep -A2 "Agent Token" agent.out
token: ""

# Agent 监听端口（在 Server「服务器管理」页面注册时填写）
port: 9009

# 运行数据目录（制品、上传分片、临时脚本）。留空 = 本文件同级 data/
data_dir: ""

# 浏览器跨域来源：【必填】实际访问 re-deploy 前端的地址（协议+域名+端口）
cors_origin: https://<你的前端访问地址>

log:
  # 运行时日志目录（JSON 日志 + tasks/ 每任务日志）。留空 = 本文件同级 log/
  dir: ""
  # 日志级别：debug / info / warn / error
  level: info
  # 日志保留天数；0 = 永不清理
  max_age_days: 30

download:
  # 是否允许 Server 从本机回拉文件
  enabled: true
  # 回拉路径白名单（glob，** 递归匹配），按应用日志实际位置修改
  allowed_paths:
    - /home/<用户名>/logs/**
    - /tmp/redeploy-**
  # 单文件大小上限（字节），0 = 不限
  max_file_size: 0
  # 拉取会话有效期（秒）
  session_ttl: 3600
```

配置项的完整说明见 [配置说明 - 代理端](configuration.md#代理端配置)。

## 5. 首次启动与注册

```bash
cd ~/deploy-agent
./start.sh
```

输出示例：

```
agent 已启动 (PID 12345)
启动输出:   /home/app/deploy-agent/agent.out
运行时日志: /home/app/deploy-agent/log/agent-2026-09-15.log
健康检查:   curl http://127.0.0.1:9009/api/health
```

**第一步：取 Token**（首启只在 `agent.out` 打印一次，也已回写 config.yaml）：

```bash
grep -A2 "Agent Token" agent.out
# 或
grep '^token:' config.yaml
```

**第二步：本机健康检查**：

```bash
curl http://127.0.0.1:9009/api/health      # {"status":"ok"}
./status.sh                                # 运行中 (PID ...), 健康检查: 正常
```

**第三步：在 re-deploy Server 注册**：打开「服务器管理」页面添加服务器，填写目标机 IP、端口 `9009` 与上一步的 Token。注册成功后即可发起部署。

## 6. 日常运维

```bash
./status.sh       # 查看运行状态、PID、健康检查
./stop.sh         # 优雅停止（发 TERM，等待最多 10 秒，不会自动 kill -9）
./start.sh        # 启动；已在运行时自动拒绝重复启动
tail -f agent.out                                   # 启动/生命周期输出
tail -f log/agent-$(date +%F).log                   # 运行时 JSON 日志
grep '"task_id":"<任务ID>"' log/agent-$(date +%F).log   # 按任务过滤
```

## 7. 开机自启

### 方式一：crontab @reboot（推荐，无需 sudo）

```bash
crontab -e
```

加入一行（路径换成实际部署目录）：

```
@reboot cd /home/app/deploy-agent && ./start.sh >/dev/null 2>&1
```

验证条目已保存：`crontab -l`。`@reboot` 是否生效取决于本机 cron 服务是否运行（大多数发行版默认运行）；重启后用 `./status.sh` 确认。

### 方式二：systemd --user（需管理员预先开启 linger）

若管理员已执行 `loginctl enable-linger <用户名>`，可创建 `~/.config/systemd/user/deploy-agent.service`：

```ini
[Unit]
Description=Deploy Agent (rootless)
After=network.target

[Service]
Type=simple
WorkingDirectory=%h/deploy-agent
ExecStart=%h/deploy-agent/deploy-agent -c %h/deploy-agent/config.yaml
Restart=always
RestartSec=5

[Install]
WantedBy=default.target
```

```bash
systemctl --user daemon-reload
systemctl --user enable --now deploy-agent
journalctl --user -u deploy-agent -f
```

未开启 linger 时用户服务不会在未登录状态运行，这种情况下请用方式一。

## 8. 升级 Agent

配置、`data/`、`log/` 都不受影响，只替换二进制：

```bash
cd ~/deploy-agent
./stop.sh
curl -fsSL http://<SERVER>:9006/api/agent/download/linux/amd64 -o deploy-agent.new
chmod +x deploy-agent.new && mv deploy-agent.new deploy-agent
./start.sh
./status.sh
```

## 9. 卸载

```bash
./stop.sh
crontab -e    # 删除 @reboot 那一行（如配置过）
cd ~ && rm -rf ~/deploy-agent    # 整个目录删除即可，无系统残留
```

## 10. 故障排查

| 现象 | 排查 |
|------|------|
| `start.sh` 提示找不到二进制 | 二进制文件名必须是 `deploy-agent` 且有执行权限（`chmod +x`） |
| 启动后立即退出 | 查看 `agent.out` 末尾错误；常见为 9009 端口占用（改 `config.yaml` 的 `port`）或配置 YAML 语法错误 |
| 健康检查失败 | `ss -lntp | grep 9009` 确认监听；本机 curl 不通先看 `agent.out` 与 `log/` 日志 |
| Server 注册/部署连不上 | 确认 Server 到目标机 `IP:9009` 的网络与防火墙放通；`curl http://<目标机IP>:9009/api/health` 从 Server 侧测试 |
| 浏览器控制台报 CORS 错误 | `config.yaml` 的 `cors_origin` 必须与浏览器访问前端的地址（协议/域名/端口）完全一致，修改后 `./stop.sh && ./start.sh` |
| 找不到 Token | `grep '^token:' config.yaml`（首启已回写）；若 Token 曾被清空，重启时会自动重新生成并打印到 `agent.out` |
| `@reboot` 未生效 | `crontab -l` 核对条目；确认 cron 服务运行（`ps -ef | grep crond`）；部分精简系统不支持 `@reboot`，改用 systemd --user 或登录后手工 `./start.sh` |
| `stop.sh` 提示 10 秒未退出 | 按提示手工 `kill -9 <PID>`，再 `rm -f agent.pid` |
