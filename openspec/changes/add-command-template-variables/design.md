# Design: 命令模板变量

## 1. 替换语义（与 script 步骤对齐）

- 语法：命令文本中的 `{{key}}` 做**字面量全量替换**（Go `strings.ReplaceAll`），不做表达式求值/默认值/转义。
- 替换时机：`sh -c` 执行前。值会被 shell 正常解释（引号、空格、元字符）——命令本身就是任务配置者编写，参数由部署人填写，与既有 script 模板同一信任边界。
- 未提供的 key：保持原文 `{{key}}`（与 script 现状一致），写错变量名会在命令执行时显性报错。
- 同时适用于 `shell`、`command`、`script` 三类步骤；`deploy` 步骤不用 params 渲染。
- UI 调试执行（`AgentService.executeCommand`）不发 params，不替换。

## 2. 变量清单

| key | 来源 | 示例 |
|---|---|---|
| `jenkinsBuildNumber` | 部署表单（Jenkins 任务） | `123` |
| `artifactFilename` | Jenkins 制品上传后注入 | `gateway-api.jar` |
| `deploy.id` | deploy_history 自增 ID（每次部署唯一） | `456` |
| `deploy.version` | 部署版本标识；未填且非 Jenkins 时为空串 | `#123` |
| `deploy.time` | history.started_at 格式化 `yyyyMMdd-HHmmss` | `20260917-094500` |
| `deploy.timestamp` | history.started_at 的 epoch 秒 | `1758083100` |
| `task.id` | 任务配置 ID（固定） | `7` |
| `task.name` | 任务名称 | `网关部署` |
| `server.name` | 目标服务器名称（每台不同） | `录像合成服务器-dat` |
| `server.host` | 目标服务器主机/IP（每台不同） | `10.28.3.44` |

## 3. 注入点与并发

- `DeployService.deploy()` fan-out 前构建公共参数（用户 params + deploy.* + task.*）；`deployToServer` 入口 **new 一份 HashMap 拷贝**后再加 server.* 与 artifactFilename，杜绝多服务器并行改写同一 HashMap（现状隐患）。
- 时间以 `history.startedAt`（DeployController insert 前设置）为准，一次部署内各服务器取值一致。

## 4. agent 改动

- `ExecuteShell(ctx, command, timeout)` → `ExecuteShell(ctx, command string, params map[string]string, timeout int)`；执行前循环 ReplaceAll。
- `ExecuteScript` 已自行替换脚本正文，随后调用 ExecuteShell 时传 nil（幂等，正文已无占位符）。
- `task.go` 的 shell/command 分支传 `req.Params`；旧 server 不带 params（nil）时零变化。

## 5. 前端

TaskDialog 前置/后置命令 textarea 下方放静态说明块：分组「部署/任务/服务器/Jenkins」列出变量名、含义、示例。纯展示，不做点击插入。
