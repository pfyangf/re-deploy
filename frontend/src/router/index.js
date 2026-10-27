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
import Login from '../views/Login.vue'
import NoAccess from '../views/NoAccess.vue'
import AccessUsers from '../views/AccessUsers.vue'
import AccessRoles from '../views/AccessRoles.vue'
import AccessMenus from '../views/AccessMenus.vue'
import api from '../api/client'
import { applyProfile, authState, canAccess, firstMenuPath, isLoggedIn } from '../utils/session'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: Login,
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: Dashboard,
    meta: { title: '仪表盘', subtitle: '集群概览、进行中的队列与最近部署', menu: '/dashboard' }
  },
  {
    path: '/groups',
    name: 'groups',
    component: Groups,
    meta: { title: '分组管理', subtitle: '按环境组织目标服务器', menu: '/groups' }
  },
  {
    path: '/servers',
    name: 'servers',
    component: Servers,
    meta: { title: '服务器管理', subtitle: '主机、Agent 与在线状态', menu: '/servers' }
  },
  {
    path: '/tasks',
    name: 'tasks',
    component: Tasks,
    meta: { title: '任务管理', subtitle: '可复用的部署步骤与参数', menu: '/tasks' }
  },
  {
    path: '/deploy',
    name: 'deploy',
    component: Deploy,
    meta: { title: '部署操作', subtitle: '选择目标、制品或模板后发起', menu: '/deploy' }
  },
  {
    path: '/history',
    name: 'history',
    component: History,
    meta: { title: '部署历史', subtitle: '按状态回看结果与分段日志', menu: '/history' }
  },
  {
    path: '/artifacts',
    name: 'artifacts',
    component: Artifacts,
    meta: { title: '构建产物', subtitle: 'CI 产出与可部署的构建包', menu: '/artifacts' }
  },
  {
    path: '/notifications',
    name: 'notifications',
    component: Notifications,
    meta: { title: '通知管理', subtitle: '失败与离线告警渠道', menu: '/notifications' }
  },
  {
    path: '/downloads',
    name: 'downloads',
    component: Downloads,
    meta: { title: '远端下载', subtitle: '从目标机拉取日志与诊断文件', menu: '/downloads' }
  },
  {
    path: '/settings',
    name: 'settings',
    component: Settings,
    meta: { title: '系统设置', subtitle: '令牌、保留策略与通知开关', menu: '/settings' }
  },
  {
    path: '/access/users',
    name: 'access-users',
    component: AccessUsers,
    meta: { title: '用户管理', subtitle: '账号、启用状态与所属角色', menu: '/access/users' }
  },
  {
    path: '/access/roles',
    name: 'access-roles',
    component: AccessRoles,
    meta: { title: '角色管理', subtitle: '角色与可访问的菜单', menu: '/access/roles' }
  },
  {
    path: '/access/menus',
    name: 'access-menus',
    component: AccessMenus,
    meta: { title: '菜单管理', subtitle: '导航分组、路径与显示', menu: '/access/menus' }
  },
  {
    path: '/no-access',
    name: 'no-access',
    component: NoAccess,
    meta: { title: '无权访问', subtitle: '当前账号没有可进入的菜单' }
  },
  {
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

router.beforeEach(async (to) => {
  if (to.meta.public) {
    if (to.path === '/login' && isLoggedIn()) return firstMenuPath()
    return true
  }
  if (!isLoggedIn()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!authState.ready) {
    try {
      applyProfile(await api.me())
    } catch {
      if (!isLoggedIn()) return { path: '/login', query: { redirect: to.fullPath } }
      authState.ready = true
    }
  }
  if (to.meta.menu && !canAccess(to.meta.menu)) {
    const fallback = firstMenuPath()
    if (fallback && fallback !== to.path) return fallback
    if (to.path !== '/no-access') return '/no-access'
  }
  return true
})

export default router
