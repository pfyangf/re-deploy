# Tasks

## 1. OpenSpec
- [x] 1.1 proposal/design/tasks + `specs/task-execution/spec.md`
- [x] 1.2 `openspec validate add-command-template-variables --strict` 通过（顺带修复主 spec 历史 delta 头）

## 2. agent
- [x] 2.1 `executor.go`：ExecuteShell 增加 params 参数并在执行前做 `{{key}}` ReplaceAll（抽 renderParams，ExecuteScript 复用）
- [x] 2.2 `task.go` shell/command 分支传 req.Params
- [x] 2.3 `internal/executor` 新增单测：替换生效、点号变量、未提供保持原样、nil 兼容、特殊字符字面替换
- [x] 2.4 `go vet ./... && go test ./...`，交叉编译 amd64/arm64

## 3. server
- [x] 3.1 DeployService 抽出 `buildServerParams(userParams, history, task, server)`：拷贝 map，注入 deploy/task/server 命名空间变量（时间取 history.startedAt，Asia/Shanghai）
- [x] 3.2 deployToServer 每机独立 params（保留 artifactFilename 注入，删掉旧 null 分支）
- [x] 3.3 DeployServiceVariablesTest：注入键值、version 空值、两机 map 独立
- [x] 3.4 `mvn test` 通过（13 tests）

## 4. 前端
- [x] 4.1 TaskDialog.vue 后置命令下加「可用变量」静态表格（变量/说明/示例，max-height 滚动，v-pre 防 mustache 解析）
- [x] 4.2 `npm run build` 通过

## 5. 文档与实测
- [x] 5.1 `docs/guide/configuration.md` 新增「前置/后置命令的模板变量」章节
- [ ] 5.2 现场：升级 agent+server 后在任务前置命令 `echo {{deploy.id}} {{server.host}} {{jenkinsBuildNumber}}` 跑一次部署验证
