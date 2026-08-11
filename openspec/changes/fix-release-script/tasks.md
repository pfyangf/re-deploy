## 1. release.sh 加固

- [x] 1.1 新增 SNAPSHOT 前置校验（patch/minor/major bump 时检查当前版本以 -SNAPSHOT 结尾）
- [x] 1.2 新增版本变化校验（显式指定版本时目标版本不得等于当前 base 版本）
- [x] 1.3 `git tag` 改为 annotated tag（`git tag -a -m "release v$TARGET"`）
- [x] 1.4 新增发布后验证：本地 tag 存在性、远端 tag 存在性、Docker 镜像 manifest 检查
- [x] 1.5 验证失败仅警告不回滚，输出手动验证命令

## 2. release.ps1 同步加固

- [x] 2.1 对照 release.sh 逐项同步：SNAPSHOT 校验
- [x] 2.2 对照 release.sh 逐项同步：版本变化校验
- [x] 2.3 对照 release.sh 逐项同步：annotated tag
- [x] 2.4 对照 release.sh 逐项同步：发布后验证步骤

## 3. 文档更新

- [x] 3.1 更新 `docs/guide/deployment.md`：补充前置校验说明
- [x] 3.2 更新 `docs/guide/deployment.md`：补充 annotated tag 说明
- [x] 3.3 更新 `docs/guide/deployment.md`：更新发布后验证清单

## 4. 验证

- [x] 4.1 手动验证：非 SNAPSHOT 版本执行 `release.sh patch` 应报错退出
- [x] 4.2 手动验证：目标版本与当前版本相同时应报错退出
- [x] 4.3 手动验证：annotated tag 能被 `git push --follow-tags` 正确推送
