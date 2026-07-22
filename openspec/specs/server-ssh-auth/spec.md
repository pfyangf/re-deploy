## ADDED Requirements

### Requirement: 服务器存储SSH连接信息
服务器模型 SHALL 支持存储可选的SSH连接信息：
- SSH用户名 (可选)
- SSH密码 (可选)
- SSH私钥 (可选)
- SSH端口 (默认 22)

**说明**: 密码和私钥至少提供一个才能建立SSH连接。

#### Scenario: 添加服务器带SSH信息
- **WHEN** 用户创建服务器时填写了SSH用户名、密码和端口
- **THEN** 系统加密存储密码后保存到数据库
- **AND** API返回不包含加密的敏感信息

#### Scenario: 添加服务器不带SSH信息
- **WHEN** 用户创建服务器时未填写SSH信息
- **THEN** 服务器正常创建，SSH字段为NULL
- **AND** 堡垒机功能对该服务器不可用

---

### Requirement: 敏感信息加密存储
SSH密码和私钥 SHALL 使用配置的AES密钥加密存储在数据库。

#### Scenario: 加密存储
- **WHEN** 用户保存SSH密码或私钥
- **THEN** 系统使用AES对称加密后再存储到数据库

#### Scenario: 使用前解密
- **WHEN** 需要建立SSH连接
- **THEN** 系统从数据库读取加密数据，解密后使用
- **AND** 解密后的明文仅存在内存中，不落地存储

---

### Requirement: 加密密钥配置
系统 SHALL 通过配置文件 `redeploy.ssh-encryption-key` 配置AES加密密钥。

#### Scenario: 密钥未配置
- **WHEN** 用户启用SSH功能但未配置加密密钥
- **THEN** 系统启动时自动生成随机16字节密钥并打印到日志
- **AND** 提示用户保存该密钥

#### Scenario: 密钥长度不正确
- **WHEN** 配置的密钥长度不是16/24/32字节（对应AES-128/192/256）
- **THEN** 启动时打印警告日志

---

### Requirement: API不返回敏感信息
REST API SHALL 不在响应中返回加密的SSH密码和私钥。

#### Scenario: 获取服务器列表
- **WHEN** 请求 `GET /api/servers`
- **THEN** 响应包含 `sshUsername` 和 `sshPort`，不包含 `sshPassword` 和 `sshPrivateKey`

#### Scenario: 获取单个服务器
- **WHEN** 请求 `GET /api/servers/{id}`
- **THEN** 响应不包含 `sshPassword` 和 `sshPrivateKey`
