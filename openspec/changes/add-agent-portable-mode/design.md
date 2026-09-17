## Context

见 proposal.md - Why。当前路径决策全部集中在 `agent/internal/config/config.go` 的 `Load()`：config 目录硬编码 `/opt/deploy-agent/conf`（仅 `AGENT_CONFIG_DIR` 可整体覆盖），DataDir 缺省 `/opt/deploy-agent/data`，Log.Dir 缺省 `/opt/deploy-agent/log`，启动时无条件 `MkdirAll`。`main.go` 不解析任何命令行参数。

有利条件：data 下全部子目录（`uploads/`、`artifacts/`、`scripts/`，见 `upload.go`、`executor.go`、`cleaner.go`）与 log 下的 `tasks/`（`logging/task_writer.go`）都从 `cfg.DataDir` / `cfg.Log.Dir` 派生，根目录可配置后无散落硬编码需要处理。

已知既有缺陷（本变更顺带修复一处）：`config.go:134` `cfg.DataDir = file.DataDir` 无条件覆盖，配置存在但缺 `data_dir` 时 DataDir 变空串。

## Goals / Non-Goals

**Goals:**

- 普通用户账号（无 sudo、无 /opt 写权限、无 systemd）可完整运行 agent：首启生成 token、自建目录、nohup 后台常驻、重启与开机自启。
- 默认启动路径（systemd、容器镜像、`AGENT_CONFIG_DIR`）行为零变化。
- 提供可直接上传使用的完整 `config.yaml` 模板与启停脚本，以及中文部署指引。

**Non-Goals:**

- 不改 agent HTTP API、鉴权协议、token 注册流程（仍在 server「服务器管理」页面手工注册）。
- 不改 server 的 `/api/agent/install.sh`、二进制下载端点与前端安装弹窗。
- 不做 agent 自我 daemonize / fork（采用外层 nohup 脚本）。
- 不支持 Windows 便携模式。
- 不修复 `upload.go:312` 过期会话清理硬编码 `./data/uploads` 的遗留问题（仅可能漏清临时分片，不影响功能，单独处理）。

## Decisions

### D1：用标准库 `flag` 定义 `-c` 与 `--config` 两个同名变量

`main.go` 用 Go 1.21 标准库 `flag`（与"日志只用标准库"的既有取向一致，不引入 cobra/pflag）：

```go
var configPath string
flag.StringVar(&configPath, "config", "", "config.yaml 路径")
flag.StringVar(&configPath, "c", "", "config.yaml 路径（简写）")
flag.Parse()
cfg, err := config.Load(configPath)
```

标准 `flag` 包同时接受单横杠与双横杠，注册两个名字绑定同一变量即可同时支持 `-c` 与 `--config`。

**备选**：改 `Load()` 读环境变量 `AGENT_CONFIG`——被否决，用户明确要求命令行显式指定，且 env 方式在 nohup/cron 场景隐蔽、难排查。

### D2：`Load(configPath string)` 内部分两种模式，缺省目录锚定 config 同级

```
Load(configPath)
  ├─ configPath != ""（便携模式）
  │     path = Abs(configPath)
  │     base = Dir(path)                      ← 配置文件所在目录
  │     缺省 DataDir = base/data
  │     缺省 Log.Dir = base/log
  │     文件不存在：生成 token、MkdirAll(base)、回写该文件
  │
  └─ configPath == ""（legacy 模式，现状逻辑不动）
        configDir = $AGENT_CONFIG_DIR 或 /opt/deploy-agent/conf
        缺省 DataDir = /opt/deploy-agent/data
        缺省 Log.Dir = /opt/deploy-agent/log
```

加载已有配置后的合并规则两种模式一致：**空值不覆盖缺省**。现有 Log 段已如此处理；DataDir 由无条件赋值改为 `if file.DataDir != "" { cfg.DataDir = file.DataDir }`。相对路径先 `filepath.Abs` 解析，保证锚定与 `-c` 传相对路径时行为确定（spec「Relative config path」场景）。

**备选**：锚定 config 父目录（`../data`，对齐 /opt 下 conf 与 data 平级的布局）——被否决，用户已选择"配置文件同级目录"，平铺布局下用户只需要管一个目录，脚本与文档更简单。

### D3：`logging/logger.go` 的 `/opt` 兜底保留不动

`logger.Init` 在 `c.Dir == ""` 时兜底 `/opt/deploy-agent/log`。D2 后 config 层在两种模式下都保证 Log.Dir 非空，该分支实际不可达。保留可避免不必要的改动面（surgical），且对未来绕过 config 直接调用 Init 的代码仍是安全兜底。

### D4：仓库新增 `scripts/agent/`，脚本与二进制、config.yaml 同目录平铺

部署目录约定（用户家目录下任意位置，如 `~/deploy-agent/`）：

```
~/deploy-agent/
├── deploy-agent        # 从 server 下载的二进制，chmod +x
├── config.yaml         # scripts/agent/config.yaml 模板
├── start.sh            # scripts/agent/start.sh
├── stop.sh
├── status.sh
├── data/               # agent 首启自建（模板留空 data_dir 时锚定于此）
├── log/                # agent 首启自建
├── agent.out           # start.sh 自建，nohup stdout（首启 token 在这里）
└── agent.pid           # start.sh 自建
```

脚本一律以 `SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)` 自定位，不依赖调用者 CWD；`start.sh` 先 `cd "$SCRIPT_DIR"` 再 `nohup ./deploy-agent -c "$SCRIPT_DIR/config.yaml" >> agent.out 2>&1 &`，写 PID 文件。单实例保护：PID 文件存在且 `kill -0` 验活则拒绝重复启动；PID 失效则清理陈旧 PID 文件。`stop.sh` 发 TERM 并等待进程退出（超时再提示 KILL，不自动 `-9`）。`status.sh` 输出运行状态与 PID。

**备选**：在 server 增加 `/api/agent/install-user.sh` 免 sudo 一键端点——本期不做（用户已选仓库脚本 + 文档方案），脚本成熟后可作为后续 change。

### D5：`config.yaml` 模板全部键齐全，环境相关值由用户填写，路径留空走同级锚定

模板包含全部支持键；`data_dir` 与 `log.dir` 给空值并注释"留空 = 本目录下 data/、log/"，`token` 留空（首启自动生成），`server_url`、`cors_origin`、`port`、`download.allowed_paths` 给占位与中文注释。

注意：现有代码在 `cors_origin` 为空时保留硬编码默认 `https://bsck.cnoic.com:50002`（`config.go:135-137`）。本变更**不改该默认值**，模板注释明确要求用户填自己的前端来源，避免静默使用客户特定域名。

### D6：开机自启走 crontab `@reboot`，systemd --user 仅作文档备选

无 sudo 环境唯一通用的自启手段是用户 crontab：

```
@reboot cd $HOME/deploy-agent && ./start.sh >/dev/null 2>&1
```

`systemd --user` 需要管理员预先 `loginctl enable-linger <user>`，多数受限环境不可用，文档作为备选列出。cron 不可用时的最后兜底是手工 `start.sh`，文档明确说明。

### D7：文档落点

新建 `docs/guide/agent-rootless-deploy.md`（完整无 sudo 部署指引：目录准备、二进制下载、配置填写、首启取 token、server 注册、启停、开机自启、升级、故障排查 FAQ）。在 `docs/guide/installation.md` 的"代理端安装"加一节链接；更新 `docs/guide/configuration.md` 代理端配置位置与全量键说明；同步 `AGENTS.md` 的 Auth model / 日志路径段落，补充便携模式说明。

## Risks / Trade-offs

- [用户漏填 `cors_origin` 导致浏览器跨域被拒（静默使用硬编码客户域名）] → 模板中该键必填、占位醒目；文档故障排查章节列出症状与处理。
- [`@reboot` 在部分精简 cron 实现（如 busybox crond）上不支持] → 文档给出验证方法（重启或查 `/var/log/cron`），并提供 systemd --user 与手工启动兜底。
- [nohup 进程在 OOM / 机器重启后不会自动拉起（无 systemd Restart=always）] → 文档提供 `status.sh` 巡检与 `@reboot` 自启；start.sh 的 PID 校验可支撑外部巡检/定时重入。
- [首启 token 只写进 `agent.out`，用户用 `>>` 追加长期增长] → 文档指明取 token 命令（`grep -A2 'Agent Token' agent.out`）；agent.out 仅生命周期输出，量级极小，不做轮转。
- [便携模式 agent 监听 9009 仍需本机端口可用、防火墙对 server 可达] → 与 root 模式相同的网络前提，文档前置说明。
- [既有主 spec 文件 `agent-management` / `agent-logging` 结构不规范（误用 `## ADDED Requirements` 主规格头），`openspec validate` 已提示 archive 会拒绝] → 属历史遗留，不在本变更内修复；archive 本 change 前需先单独修正这两个主 spec 的章节头（任务清单中列为前置检查项）。

## Migration Plan

纯增量变更，无数据迁移：

1. 随 agent 新版本发布二进制（构建流程不变，`scripts/build.ps1` 交叉编译产物自带 `-c` 能力）。
2. 既有 /opt + systemd 部署升级二进制后行为不变；`scripts/agent/` 与新文档对旧部署无影响。
3. 无 sudo 服务器按新文档部署；失败时直接删除部署目录即可完全清理（无系统残留）。

回滚：还原旧二进制即可；旧二进制不识别 `-c` 参数，停用新脚本、改用旧部署方式，无配置格式变化。
