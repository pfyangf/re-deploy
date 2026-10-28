## 1. 密钥与认证

- [x] 1.1 新增 `api_key` 表，启动期 migration 建表
- [x] 1.2 创建、停用、删除密钥；明文只返回一次，按 SHA-256 查找
- [x] 1.3 `AuthService` 识别 `rdk_` 密钥；停用和过期返回未登录
- [x] 1.4 `ApiKeyAccess` 限制查询、新增和部署，拒绝系统管理与改删

## 2. 管理页

- [x] 2.1 菜单「API 密钥」和 `/api/system/api-keys`
- [x] 2.2 前端列表、创建、一次性展示密钥和环境变量

## 3. Skill

- [x] 3.1 `.cursor/skills/redeploy/SKILL.md` 与 `reference.md`
- [x] 3.2 管理页说明如何把 Skill 复制到个人目录
