## Why

发布脚本 `release.sh` / `release.ps1` 存在多个健壮性缺陷，已在实际发版中触发两次故障：
1. 当 pom 版本不是 SNAPSHOT 格式时，脚本静默继续，最终在 git commit 步骤报 "nothing to commit"；
2. 轻量级 tag + `git push --follow-tags` 组合导致 tag 从未推送到远端，Docker 镜像与源码 tag 不同步。

这些缺陷在低频发布场景下容易被忽视，但每一次故障都需要手动回滚，存在误操作风险。

## What Changes

- **新增前置校验**：release 脚本执行前校验当前 pom 版本必须为 SNAPSHOT 格式（显式指定版本号除外），且目标版本不得等于当前版本。
- **修复 tag 推送**：`git tag` 改为 annotated tag（`git tag -a -m`），确保 `git push --follow-tags` 能正确推送。
- **同步更新 Windows PowerShell 版本**：`release.ps1` 与 `release.sh` 保持逻辑一致。
- **更新发布文档**：`docs/guide/deployment.md` 补充前置校验说明、tag 类型说明、以及发布后验证清单。
- **增加发布后验证步骤**：脚本末尾增加 tag 存在性、镜像 manifest 等自动检查，发布成功即给出确认。

## Capabilities

### New Capabilities
- `release-script-hardening`: 发布脚本的健壮性加固，包括前置校验、annotated tag、发布后验证等工程质量改进。

### Modified Capabilities

（无现有 capability 的需求变更，纯工程脚本质量改进）

## Impact

- **文件修改**：
  - `scripts/release.sh`
  - `scripts/release.ps1`
  - `docs/guide/deployment.md`
- **无 API / 数据模型 / 对外行为变更**：纯内部发布工具链改进。
- **风险**：低。脚本改动不影响运行时服务。
