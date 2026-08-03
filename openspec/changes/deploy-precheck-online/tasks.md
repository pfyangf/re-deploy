## 1. AgentService 超时配置

- [x] 1.1 为 AgentService 的 RestTemplate 配置 5 秒连接超时和读取超时

## 2. 部署预检逻辑

- [x] 2.1 在 DeployController 中新增服务器健康检查方法（并行调用 health 接口）
- [x] 2.2 部署提交前执行预检，离线则返回 400 错误并更新服务器状态
- [x] 2.3 全部在线时更新所有服务器 status 为 online 并继续部署

## 3. 前端适配

- [x] 3.1 Deploy.vue 错误提示改为显示后端返回的具体 error 信息

## 4. 验证

- [x] 4.1 后端编译通过（mvn compile）
- [x] 4.2 验证：全在线服务器正常部署
- [x] 4.3 验证：离线服务器触发预检失败，状态更新，不创建部署记录
