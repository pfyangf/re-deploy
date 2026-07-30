## ADDED Requirements

### Requirement: 参数作用域

系统 SHALL 支持三层参数作用域：全局参数、流水线参数、运行时参数。

#### Scenario: 全局参数
- **THEN** 全局参数 MUST 存储在 `global_parameters` 表中
- **AND** 所有 Pipeline 和 Run 均可读取
- **AND** 全局参数 MUST 包含 `name`、`value`、`type`、`isSecret`、`description`

#### Scenario: 流水线参数
- **THEN** 流水线参数 MUST 存储在 `pipeline_parameters` 表中
- **AND** 仅所属 Pipeline 及其 Run 可读取
- **AND** 流水线参数 MUST 支持默认值

#### Scenario: 运行时参数
- **WHEN** 创建 Run 时
- **THEN** 调用方 MUST 可传入运行时参数（如 `version`、`jenkinsBuildNumber`）
- **AND** 运行时参数优先级 MUST 高于流水线参数和全局参数

### Requirement: 参数类型

系统 SHALL 支持 `string`、`number`、`boolean`、`secret` 四种参数类型。

#### Scenario: string 类型
- **WHEN** 参数类型为 `string`
- **THEN** 值按字符串处理
- **AND** 模板替换时直接插入

#### Scenario: number 类型
- **WHEN** 参数类型为 `number`
- **THEN** 值 MUST 为合法数字
- **AND** 模板替换时按数字字符串插入

#### Scenario: boolean 类型
- **WHEN** 参数类型为 `boolean`
- **THEN** 值 MUST 为 `true` 或 `false`
- **AND** 模板替换时按 `true`/`false` 字符串插入

#### Scenario: secret 类型
- **WHEN** 参数类型为 `secret`
- **THEN** 值在数据库中 MUST 加密存储
- **AND** 在日志、UI、API 响应中 MUST 脱敏显示为 `***`
- **AND** 仅在执行时解密并用于命令或文件路径

### Requirement: 模板替换语法

系统 SHALL 支持 `{{param}}` 语法，并扩展默认值和环境变量读取。

#### Scenario: 基本替换
- **WHEN** 模板中包含 `{{version}}`
- **THEN** 系统 MUST 从参数作用域中查找 `version`
- **AND** 用实际值替换

#### Scenario: 默认值
- **WHEN** 模板中包含 `{{timeout:30}}`
- **THEN** 如果 `timeout` 未定义，MUST 使用默认值 `30`

#### Scenario: 环境变量
- **WHEN** 模板中包含 `{{env.HOME}}`
- **THEN** 系统 MUST 读取 Server 进程的环境变量 `HOME`
- **AND** 不支持的环境变量 MUST 返回空字符串或抛出可识别错误

#### Scenario: 嵌套与转义
- **THEN** 模板引擎 SHOULD 支持嵌套表达式
- **AND** 提供 `\{{` 作为转义语法，避免被替换

### Requirement: 参数校验

系统 SHALL 在创建/更新 Pipeline 参数和创建 Run 时校验参数合法性。

#### Scenario: 必填校验
- **WHEN** 参数未设置默认值且运行时未提供值
- **THEN** 系统 MUST 拒绝创建 Run 并返回清晰错误

#### Scenario: 类型校验
- **WHEN** 参数声明为 `number` 但传入非数字值
- **THEN** 系统 MUST 拒绝并返回类型错误

#### Scenario: 名称校验
- **WHEN** 参数名包含非法字符（如空格、特殊符号）
- **THEN** 系统 MUST 拒绝并提示合法命名规则

### Requirement: Secret 参数管理

系统 SHALL 对 secret 类型参数提供加密与访问控制基础。

#### Scenario: 加密存储
- **WHEN** 保存 secret 参数
- **THEN** 系统 MUST 使用 AES 加密（复用现有 `SshEncryptionUtils` 或等效实现）
- **AND** 加密密钥 MUST 来自 `redeploy.ssh-encryption-key` 配置

#### Scenario: 脱敏显示
- **WHEN** API 返回包含 secret 的参数列表
- **THEN** `value` 字段 MUST 为 `***`
- **AND** 必须提供单独接口用于更新 secret，不允许前端读取明文

#### Scenario: 执行时解密
- **WHEN** Run 执行到引用 secret 的步骤
- **THEN** 系统 MUST 在内存中解密并使用
- **AND** MUST NOT 将明文写入持久化日志

## MODIFIED Requirements

- 现有 `Task` 中的简单字符串替换机制被参数系统统一替换，但保持 `{{param}}` 语法向后兼容。

## REMOVED Requirements

无。
