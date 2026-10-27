import { createRouter, createWebHashHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import Groups from '../views/Groups.vue'
import Servers from '../views/Servers.vue'
import Tasks from '../views/Tasks.vue'
import Deploy from '../views/Deploy.vue'
import History from '../views/History.vue'
import Artifacts from '../views/Artifacts.vue'
import Notifications from '../views/Notifications.vue'
import Downloads from '../views/Downloads.vue'
import Settings from '../views/Settings.vue'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: Dashboard,
    meta: { title: '仪表盘', subtitle: '集群概览、进行中的队列与最近部署' }
  },
  {
    path: '/groups',
    name: 'groups',
    component: Groups,
    meta: { title: '分组管理', subtitle: '按环境组织目标服务器' }
  },
  {
    path: '/servers',
    name: 'servers',
    component: Servers,
    meta: { title: '服务器管理', subtitle: '主机、Agent 与在线状态' }
  },
  {
    path: '/tasks',
    name: 'tasks',
    component: Tasks,
    meta: { title: '任务管理', subtitle: '可复用的部署步骤与参数' }
  },
  {
    path: '/deploy',
    name: 'deploy',
    component: Deploy,
    meta: { title: '部署操作', subtitle: '选择目标、制品或模板后发起' }
  },
  {
    path: '/history',
    name: 'history',
    component: History,
    meta: { title: '部署历史', subtitle: '按状态回看结果与分段日志' }
  },
  {
    path: '/artifacts',
    name: 'artifacts',
    component: Artifacts,
    meta: { title: '构建产物', subtitle: 'CI 产出与可部署的构建包' }
  },
  {
    path: '/notifications',
    name: 'notifications',
    component: Notifications,
    meta: { title: '通知管理', subtitle: '失败与离线告警渠道' }
  },
  {
    path: '/downloads',
    name: 'downloads',
    component: Downloads,
    meta: { title: '远端下载', subtitle: '从目标机拉取日志与诊断文件' }
  },
  {
    path: '/settings',
    name: 'settings',
    component: Settings,
    meta: { title: '系统设置', subtitle: '令牌、保留策略与通知开关' }
  },
  {
    // 未匹配路由统一回到仪表盘，避免空白页
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

// 使用 hash 模式（/#/servers）：刷新时浏览器只请求 /，Spring Boot 静态资源直接返回 index.html，
// 不依赖服务端 SPA fallback，避免子路径刷新 404
const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
