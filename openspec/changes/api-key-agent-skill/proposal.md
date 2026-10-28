## Why

智能体如果直接使用管理员令牌，就能改系统设置、管用户，也能远程执行命令。需要一把可吊销的 API 密钥，只覆盖查询、新增和部署，并给 Cursor 一份知道如何调用的 Skill。

## What Changes

- 新增 `api_key` 表和管理接口。明文密钥只在创建时返回一次，库中保存 SHA-256。
- `Authorization: Bearer rdk_...` 走独立权限：允许业务 GET、指定的新增 POST，以及发起/取消部署；拒绝 PUT/DELETE、系统管理和远程命令。
- 权限菜单增加「API 密钥」。
- 仓库增加 `.cursor/skills/redeploy/`，Cursor 打开本仓库时可加载。

## Capabilities

### New Capabilities

- `api-key`：可吊销的智能体密钥，以及与登录会话、管理员令牌分离的接口权限。

### Modified Capabilities

（无）

## Impact

- 服务端认证解析、菜单权限和启动期建表。
- 前端新增 `/access/api-keys`。
- 现有业务接口路径不变。网页登录和管理员令牌的权限不变。
