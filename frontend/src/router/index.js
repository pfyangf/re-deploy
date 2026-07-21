import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import Groups from '../views/Groups.vue'
import Servers from '../views/Servers.vue'
import Tasks from '../views/Tasks.vue'
import Deploy from '../views/Deploy.vue'
import History from '../views/History.vue'
import Artifacts from '../views/Artifacts.vue'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: Dashboard,
    meta: { title: '仪表盘' }
  },
  {
    path: '/groups',
    name: 'groups',
    component: Groups,
    meta: { title: '分组管理' }
  },
  {
    path: '/servers',
    name: 'servers',
    component: Servers,
    meta: { title: '服务器管理' }
  },
  {
    path: '/tasks',
    name: 'tasks',
    component: Tasks,
    meta: { title: '任务管理' }
  },
  {
    path: '/deploy',
    name: 'deploy',
    component: Deploy,
    meta: { title: '部署操作' }
  },
  {
    path: '/history',
    name: 'history',
    component: History,
    meta: { title: '部署历史' }
  },
  {
    path: '/artifacts',
    name: 'artifacts',
    component: Artifacts,
    meta: { title: '构建产物' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
