## 1. 项目初始化

- [x] 1.1 创建服务端Maven项目结构（Spring Boot）
- [x] 1.2 配置pom.xml依赖（Spring Boot Web, SQLite, Jackson等）
- [x] 1.3 创建application.yml配置文件
- [x] 1.4 创建代理端Go模块结构
- [x] 1.5 创建Go模块依赖管理（go.mod）

## 2. 数据库与模型

- [x] 2.1 创建SQLite数据库schema（servers, tasks, deploy_history, artifacts表）
- [x] 2.2 实现Server实体类和Repository
- [x] 2.3 实现Task实体类和Repository
- [x] 2.4 实现DeployHistory实体类和Repository
- [x] 2.5 实现Artifact实体类和Repository
- [x] 2.6 配置SQLite数据源和JPA

## 3. 服务端核心API

- [x] 3.1 实现服务器管理CRUD接口（/api/servers）
- [x] 3.2 实现服务器连通性测试接口（/api/servers/{id}/test）
- [x] 3.3 实现任务模板CRUD接口（/api/tasks）
- [x] 3.4 实现构建产物管理接口（/api/artifacts）
- [x] 3.5 实现Agent注册接口（/api/agents/register）
- [x] 3.6 实现Agent心跳接口（/api/agents/heartbeat）
- [x] 3.7 实现认证拦截器（Token验证）

## 4. 代理端核心实现

- [x] 4.1 实现配置管理（读取/生成config.yaml）
- [x] 4.2 实现Token自动生成和持久化
- [x] 4.3 实现HTTP路由器和中间件
- [x] 4.4 实现健康检查接口（/api/health）
- [x] 4.5 实现系统信息接口（/api/info）
- [x] 4.6 实现Agent启动注册逻辑

## 5. 文件传输功能

- [x] 5.1 实现代理端分块上传接口（/api/upload/init, /api/upload/{id}/chunk, /api/upload/{id}/complete）
- [x] 5.2 实现MD5校验逻辑
- [x] 5.3 实现断点续传支持（查询上传状态）
- [x] 5.4 实现上传会话超时清理
- [x] 5.5 实现服务端Artifact存储服务
- [x] 5.6 实现服务端向Agent推送文件的客户端逻辑

## 6. 任务执行引擎

- [x] 6.1 实现代理端任务执行接口（/api/task/execute）
- [x] 6.2 实现代理端任务状态查询接口（/api/task/{id}/status）
- [x] 6.3 实现代理端任务取消接口（/api/task/{id}/cancel）
- [x] 6.4 实现Shell命令执行器
- [x] 6.5 实现复合任务执行器（多步骤顺序执行）
- [x] 6.6 实现步骤状态记录和日志捕获

## 7. 部署流程整合

- [x] 7.1 实现服务端部署触发接口（/api/deploy）
- [x] 7.2 实现从Jenkins下载构建产物的逻辑
- [x] 7.3 实现多服务器并行部署调度
- [x] 7.4 实现部署状态查询接口（/api/deploy/{id}/status）
- [x] 7.5 实现部署历史查询接口（/api/deploy/history）
- [x] 7.6 实现部署取消接口（/api/deploy/{id}/cancel）

## 8. 告警通知

- [x] 8.1 实现钉钉Webhook通知服务
- [x] 8.2 实现部署失败自动触发告警
- [x] 8.3 实现通知重试机制
- [x] 8.4 实现通知日志记录

## 9. 日志与清理

- [x] 9.1 实现服务端日志配置（7天保留）
- [x] 9.2 实现代理端日志配置（7天保留）
- [x] 9.3 实现部署历史自动清理任务
- [x] 9.4 实现代理端心跳定时任务

## 10. Agent分发

- [x] 10.1 实现Agent二进制下载接口（/api/agent/download/{os}/{arch}）
- [x] 10.2 创建Agent一键安装脚本（install.sh）
- [x] 10.3 创建systemd service模板
- [x] 10.4 创建Agent卸载脚本

## 11. Web管理界面

- [x] 11.1 创建前端项目结构（可选，或使用简单HTML）
- [x] 11.2 实现服务器管理页面
- [x] 11.3 实现任务管理页面
- [x] 11.4 实现部署操作页面
- [x] 11.5 实现部署历史页面

## 12. 文档

- [x] 12.1 编写README.md项目介绍
- [x] 12.2 编写快速开始文档（docs/guide/quick-start.md）
- [x] 12.3 编写安装部署文档（docs/guide/installation.md）
- [x] 12.4 编写配置说明文档（docs/guide/configuration.md）
- [x] 12.5 编写用户手册（docs/guide/user-guide.md）
- [x] 12.6 编写服务端API文档（docs/api/server-api.md）
- [x] 12.7 编写代理端API文档（docs/api/agent-api.md）
- [x] 12.8 编写FAQ文档（docs/faq.md）

## 13. 测试与验证

- [ ] 13.1 编写服务端单元测试
- [ ] 13.2 编写代理端单元测试
- [ ] 13.3 执行端到端部署测试
- [ ] 13.4 测试多服务器并行部署
- [ ] 13.5 测试大文件传输（100MB+）
- [ ] 13.6 测试网络中断恢复场景
