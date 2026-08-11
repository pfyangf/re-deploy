## Context

当前发布脚本 `release.sh` 和 `release.ps1` 存在三个已验证的缺陷：

1. **缺少 SNAPSHOT 校验**：当 `pom.xml` 版本不带 `-SNAPSHOT` 后缀时，`patch` bump 计算出的目标版本与当前版本相同，`mvn versions:set` 无变化，最终 `git commit` 报 "nothing to commit"。
2. **缺少版本变化校验**：即使目标版本等于当前版本（用户误操作或异常状态），脚本也不提前报错。
3. **轻量级 tag 不被推送**：`git tag vX.Y.Z` 创建的是 lightweight tag，而 `git push --follow-tags` 只推送 annotated tag，导致 tag 永远到不了远端。

`release.sh` 和 `release.ps1` 双脚本需同步维护，改动需两边一致。

## Goals / Non-Goals

**Goals:**
- 修复上述三个已知缺陷，防止再次踩坑。
- 增加发布后自动验证步骤（tag 存在性、镜像 manifest 检查），脚本成功即代表发布结果可信。
- 更新 `docs/guide/deployment.md` 反映新的校验逻辑和验证方法。
- `release.sh` 与 `release.ps1` 保持行为完全一致。

**Non-Goals:**
- 不做幂等/断点续跑（复杂度高，收益与成本不匹配）。
- 不调整发布步骤顺序（镜像先推 vs git 先提交的架构取舍不在本次范围）。
- 不引入新的外部依赖。

## Decisions

### 1. 前置校验放在 Step 2（版本计算）之后、Step 3（改 pom）之前

**位置**：版本号计算完成后立即校验。

**校验逻辑**：

```
如果 bump 类型是 patch/minor/major（非显式版本）：
  - 当前版本必须以 -SNAPSHOT 结尾，否则报错退出。
目标版本必须 != 当前版本（去掉 SNAPSHOT 后的 base 版本），否则报错退出。
```

**理由**：越早失败越好，在任何不可逆操作（改文件、build、push）之前就拦住。

### 2. 改用 annotated tag

```bash
# 旧
git tag "v$TARGET"

# 新
git tag -a "v$TARGET" -m "release v$TARGET"
```

**理由**：
- `git push --follow-tags` 只推送 annotated tag，这是 Git 的设计约定。
- 发布 tag 本身就应该有注释，符合语义化版本管理最佳实践。
- 备选方案是把 push 改为 `git push --tags`，但那样会把所有本地 tag（包括临时 tag）都推上去，风险更大。

### 3. 发布后验证放在脚本末尾，失败不触发回滚

**验证内容**：
- 本地 git tag `v$TARGET` 存在
- 远端 git tag `v$TARGET` 存在（`git ls-remote --tags origin`）
- Docker Hub 镜像存在且多架构 manifest 正确（`docker buildx imagetools inspect`）

**验证失败处理**：打印警告和手动修复指引，但不触发 ERR trap 回滚。

**理由**：验证是"确认结果"而非"发布步骤"，如果验证失败（比如网络抖动导致远端 tag 查不到），不应该自动回滚已经成功的发布。

### 4. release.ps1 与 release.sh 完全对齐

逐项对比现有 `release.ps1`，确保：
- 同样的校验逻辑
- 同样的 tag 类型
- 同样的验证步骤
- 同样的输出格式

**理由**：两边不一致是未来 bug 的温床。

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| PowerShell 与 bash 语义差异导致逻辑不一致 | 修改后人工逐段对比，并用相同场景验证 |
| 发布后验证依赖网络（Docker Hub / GitHub），可能偶发失败 | 验证失败只警告不回滚，给出手动验证命令 |
| annotated tag 对已有脚本/CI 的兼容性 | annotated tag 与 lightweight tag 在大多数场景下行为一致，`git describe` 等命令正常工作 |
