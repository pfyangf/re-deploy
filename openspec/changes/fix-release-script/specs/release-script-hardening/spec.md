## ADDED Requirements

### Requirement: SNAPSHOT 前置校验
当 bump 类型为 `patch` / `minor` / `major` 时，release 脚本 MUST 校验当前 pom 版本以 `-SNAPSHOT` 结尾，否则立即报错退出，不执行任何后续修改操作。

#### Scenario: 当前版本非 SNAPSHOT，使用 patch bump
- **WHEN** 当前 pom 版本为 `0.1.0`（无 SNAPSHOT 后缀），用户执行 `release.sh patch`
- **THEN** 脚本立即输出错误信息，退出码非 0，pom 文件未被修改

#### Scenario: 当前版本为 SNAPSHOT，使用 patch bump
- **WHEN** 当前 pom 版本为 `0.1.0-SNAPSHOT`，用户执行 `release.sh patch`
- **THEN** 校验通过，脚本继续执行

#### Scenario: 显式指定版本号，跳过 SNAPSHOT 校验
- **WHEN** 当前 pom 版本为 `0.1.0`，用户执行 `release.sh 0.2.0`
- **THEN** 不执行 SNAPSHOT 校验，脚本继续执行（目标版本与当前版本不同）

### Requirement: 版本变化校验
release 脚本 MUST 校验目标版本号不等于当前版本号（去掉 `-SNAPSHOT` 后缀后的基础版本），否则立即报错退出。

#### Scenario: 目标版本与当前版本相同
- **WHEN** 当前 pom 版本为 `0.1.0`，用户执行 `release.sh 0.1.0`
- **THEN** 脚本输出"目标版本与当前版本相同"错误，退出码非 0

#### Scenario: 目标版本与当前版本不同
- **WHEN** 当前 pom 版本为 `0.1.0-SNAPSHOT`，用户执行 `release.sh patch`
- **THEN** 校验通过，脚本继续执行

### Requirement: Annotated Git Tag
release 脚本 MUST 使用 annotated tag（`git tag -a -m`）创建发布 tag，确保 `git push --follow-tags` 能将 tag 推送到远端。

#### Scenario: 发布成功后远端存在 tag
- **WHEN** release 脚本成功执行完毕
- **THEN** 远端 origin 上存在 `v<版本>` annotated tag

#### Scenario: tag 包含发布信息
- **WHEN** 执行 `git show v<版本>` 查看 tag
- **THEN** tag message 中包含 "release v<版本>" 文本

### Requirement: 发布后验证
release 脚本成功执行后，MUST 执行发布结果验证并输出验证摘要。

#### Scenario: 验证全部通过
- **WHEN** release 脚本成功执行
- **THEN** 输出验证摘要，包含：本地 tag 存在、远端 tag 存在、Docker 镜像多架构 manifest 有效

#### Scenario: 部分验证失败
- **WHEN** release 核心步骤成功，但某项验证失败（如网络问题导致远端 tag 查询超时）
- **THEN** 脚本输出警告和手动验证命令，退出码仍为 0（不触发回滚）

### Requirement: 双脚本一致性
`release.sh` 与 `release.ps1` MUST 在以下方面保持行为完全一致：前置校验逻辑、tag 创建方式、验证步骤、错误信息格式。

#### Scenario: 两个脚本相同输入产生相同结果
- **WHEN** 在 Windows 执行 `release.ps1 patch` 和在 Linux 执行 `release.sh patch`，pom 初始状态相同
- **THEN** 两个脚本的校验逻辑、输出格式、最终 git 状态、Docker 推送结果一致
