## Why

部分目标服务器不向部署账号开放 sudo / root，现有 agent 只能安装在 `/opt/deploy-agent/`（conf、data、log 全部硬编码在该目录下），普通用户既无法创建目录也无法注册 systemd 服务，agent 完全无法运行。需要一种"便携模式"：整个 agent 放在用户家目录下，通过命令行参数显式指定配置文件，以 nohup 方式后台运行。

## What Changes

- agent 启动新增 `-c/--config` 参数，显式指定 `config.yaml` 文件路径；解析优先级：`-c` 参数 > `AGENT_CONFIG_DIR` 环境变量 > `/opt/deploy-agent/conf/config.yaml`（默认，行为不变）。
- 指定配置文件时（便携模式），`data_dir` 与 `log.dir` 若未在配置中给出，缺省锚定到**配置文件所在目录**下的 `data/` 与 `log/`，全程不访问 `/opt/deploy-agent`。
- 修复既有缺陷：配置文件存在但未写 `data_dir` 时，当前代码会把 DataDir 覆盖为空串（`config.go:134` 无条件赋值），导致文件落到进程 CWD；改为与 `log.dir` 一致的"空值不覆盖"。
- 新增无 sudo 部署产物：仓库 `scripts/agent/` 下提供 `start.sh` / `stop.sh` / `status.sh`（nohup + PID 文件）与完整 `config.yaml` 模板；新增中文部署指引文档，覆盖目录布局、启停、开机自启（crontab `@reboot`）、token 注册、升级流程。
- 不变项：root 场景的 `/api/agent/install.sh`、systemd unit、前端安装弹窗、agent HTTP API、认证协议均不改动。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `agent-management`: 新增通过 `-c/--config` 指定配置文件的启动方式及便携模式路径解析规则；新增无 sudo（nohup）部署形态的启停脚本与文档要求；修复 `data_dir` 缺省值被空值覆盖的行为。
- `agent-logging`: 便携模式下 `log.dir` 缺省值由固定 `/opt/deploy-agent/log` 改为锚定配置文件同级目录；默认模式（未指定 `-c`）行为不变。

## Impact

- **agent 代码**：`cmd/agent/main.go`（flag 解析）、`internal/config/config.go`（路径决策、`Load` 签名、DataDir 空值修复）；`internal/logging/logger.go` 中 `/opt/deploy-agent/log` 兜底默认仅在默认模式下生效（由 config 层保证传入值，logger 兜底保留或同步收敛）。
- **脚本/文档**：新增 `scripts/agent/`（启停脚本 + config.yaml 模板）与 `docs/guide/` 下无 sudo 部署指引；同步更新 `docs/guide/configuration.md`、`AGENTS.md` 中配置位置说明。
- **server / 前端**：无改动，不新增下载端点。
- **兼容性**：默认启动方式（systemd、容器、`AGENT_CONFIG_DIR`）路径与行为零变化；便携模式为纯增量能力。
- **风险面**：低。需交叉编译验证 amd64/arm64，并在无 sudo 账号下实测首次启动生成 token、目录自建、nohup 重启与 `@reboot` 自启。
