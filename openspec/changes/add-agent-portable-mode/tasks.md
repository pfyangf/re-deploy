## 1. agent 配置加载改造

- [x] 1.1 修改 `internal/config/config.go`：`Load()` 改为 `Load(configPath string)`；当 `configPath != ""` 时进入便携模式——`filepath.Abs` 解析路径，以配置文件所在目录为 base，缺省 `DataDir=base/data`、`Log.Dir=base/log`；`configPath == ""` 时完整保留现有 `AGENT_CONFIG_DIR` → `/opt/deploy-agent/conf` 逻辑与缺省值。验证：`go vet ./...` 通过
- [x] 1.2 修复 DataDir 空值覆盖：加载已有配置时改为 `if file.DataDir != "" { cfg.DataDir = file.DataDir }`，与 Log 段合并规则一致。验证：构造缺 `data_dir` 的 config.yaml，启动后文件落在缺省目录而非进程 CWD
- [x] 1.3 确认便携模式首启流程：config.yaml 不存在时在 `-c` 指定路径（其父目录）生成 token 并回写（`Save()` 已使用 `cfg.ConfigPath`，核对赋值正确即可），data/log 目录自建于 base 下。验证：空目录首启后生成 `config.yaml`、`data/`、`log/`，stdout 打印 token
- [x] 1.4 错误路径处理：`-c` 指向的已存在文件不可读、父目录不可创建时返回明确错误；不静默回退到 `/opt`。验证：传不可读路径，进程非零退出且 stderr 有错误信息

## 2. 启动参数

- [x] 2.1 修改 `cmd/agent/main.go`：标准库 `flag` 注册 `-c` 与 `--config`（绑定同一变量），`flag.Parse()` 后将路径传入 `config.Load`；补充 `-h` 用法说明（中文）。验证：`./deploy-agent -h` 同时展示 `-c` 与 `-config`
- [x] 2.2 核对不带参数运行行为与现状一致（legacy 模式）。验证：无参数启动时仍尝试 `/opt/deploy-agent/conf/config.yaml`，与改造前一致

## 3. 构建验证

- [x] 3.1 `go vet ./...` 与本地 `go build ./...` 通过
- [x] 3.2 交叉编译 `GOOS=linux GOARCH=amd64` 与 `GOOS=linux GOARCH=arm64` 成功，产物可正常 `-h`（在 Linux 环境/WSL 验证）

## 4. 无 sudo 部署脚本与配置模板（新增 `scripts/agent/`）

- [x] 4.1 新增 `scripts/agent/config.yaml` 完整模板：包含全部键（server_url、token、port、data_dir、cors_origin、log.dir/level/max_age_days、download.enabled/allowed_paths/max_file_size/session_ttl），环境相关值中文注释占位（server_url、cors_origin 必填），`token`/`data_dir`/`log.dir` 留空并注释"留空时自动锚定配置文件同级目录"。验证：与代码支持的键逐一比对无遗漏、无多余
- [x] 4.2 新增 `scripts/agent/start.sh`：脚本目录自定位、`cd` 到部署目录、`nohup ./deploy-agent -c <dir>/config.yaml >> agent.out 2>&1 &`、写 `agent.pid`；PID 存活时拒绝重复启动，陈旧 PID 文件自动清理。验证：`bash -n start.sh`；连续执行两次第二次提示已在运行
- [x] 4.3 新增 `scripts/agent/stop.sh`（读 PID → TERM → 等待退出 → 清理 PID 文件，超时提示可手工 KILL，不自动 `kill -9`）与 `scripts/agent/status.sh`（存活状态 + PID + 端口）。验证：`bash -n` 通过；start/status/stop 全流程状态输出正确
- [x] 4.4 三个 shell 脚本 `chmod +x`（在仓库中设置可执行位）并写明 `#!/usr/bin/env bash`、`set -euo pipefail`

## 5. 部署文档

- [x] 5.1 新增 `docs/guide/agent-rootless-deploy.md`：适用前提（普通账号、端口 9009 可达 server）、目录布局图、二进制下载（curl/wget 到家目录）、上传 config.yaml 与脚本、首次启动与从 `agent.out` 取 token、server 页面注册、start/stop/status 用法、crontab `@reboot` 自启（含验证方法）、systemd --user + linger 备选、升级流程（停 → 替换二进制 → 启，config/data/log 保留）、故障排查（端口占用、cors_origin 漏填、token 找不到、cron 未生效）。验证：按文档从头操作一遍可走通
- [x] 5.2 在 `docs/guide/installation.md` 代理端章节增加"无 sudo 环境"小节并链接新文档；更新 `docs/guide/configuration.md`：配置位置补充 `-c` 便携模式说明，补全代理端全部配置键表格（含 log/download 段与 AGENT_CONFIG_DIR）
- [x] 5.3 更新 `AGENTS.md`：Auth model 段补充便携模式 token 位置（`<部署目录>/config.yaml`），日志段补充便携模式日志路径（`<部署目录>/log/`）

## 6. 集成实测（Linux 普通用户账号）

- [ ] 6.1 全新普通用户目录走通：下载二进制 → 放 config.yaml/脚本 → `start.sh` → `agent.out` 取 token → `curl http://127.0.0.1:9009/api/health` 200 → server 页面注册成功
- [ ] 6.2 触发一次真实部署任务，验证 `data/uploads`、`data/artifacts`、`data/scripts` 与 `log/agent-<date>.log`、`log/tasks/<taskID>.log` 全部落在部署目录内，`/opt` 下无任何写入
- [ ] 6.3 重启幂等：`stop.sh` 后再 `start.sh` 正常；配置 crontab `@reboot` 条目（可用 `crontab -l` 核对语法，重启机器或与用户确认后验证自启）
- [x] 6.4 legacy 回归：使用 `AGENT_CONFIG_DIR=<tmpdir>` 以无 `-c` 方式启动，确认缺省目录与配置行为与改造前一致（或代码走查确认 legacy 分支零改动）

## 7. 归档前置

- [x] 7.1 archive 前修复历史主 spec 结构问题：`openspec/specs/agent-management/spec.md` 与 `openspec/specs/agent-logging/spec.md` 顶层误用 `## ADDED Requirements`，需改为 `## Requirements`（`openspec validate --strict` 已提示否则 archive 拒绝）；修复后重新 `openspec validate add-agent-portable-mode --strict` 通过
