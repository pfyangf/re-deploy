import axios from 'axios'
import { authToken, clearSession } from '../utils/session'

const API_BASE = import.meta.env.VITE_API_BASE ?? ''

const api = axios.create({
  baseURL: API_BASE,
  timeout: 180000,
})

// Request interceptor - add auth token
api.interceptors.request.use((config) => {
  const token = authToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 401：会话失效后回到登录页。登录接口自己的 401 是密钥或密码错误，不在这里打断。
api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const url = error.config?.url || ''
    if (error.response?.status === 401 && !url.includes('/api/auth/login')) {
      clearSession()
      const hash = window.location.hash || ''
      if (!hash.startsWith('#/login')) {
        const redirect = hash.replace(/^#/, '') || '/dashboard'
        window.location.hash = `#/login?redirect=${encodeURIComponent(redirect)}`
      }
    }
    return Promise.reject(error)
  }
)

export default {
  // Dashboard
  getServers: (params) => api.get('/api/servers', { params }),
  getDeployHistory: () => api.get('/api/deploy/history'),

  // Groups
  getGroups: () => api.get('/api/groups'),
  createGroup: (data) => api.post('/api/groups', data),
  updateGroup: (id, data) => api.put(`/api/groups/${id}`, data),
  deleteGroup: (id) => api.delete(`/api/groups/${id}`),

  // Servers
  createServer: (data) => api.post('/api/servers', data),
  updateServer: (id, data) => api.put(`/api/servers/${id}`, data),
  deleteServer: (id) => api.delete(`/api/servers/${id}`),
  testServer: (id) => api.post(`/api/servers/${id}/test`),
  executeDebug: (id, command) => api.post(`/api/servers/${id}/debug/exec`, { command }),

  // Tasks
  getTasks: (params) => api.get('/api/tasks', { params }),
  createTask: (data) => api.post('/api/tasks', data),
  updateTask: (id, data) => api.put(`/api/tasks/${id}`, data),
  deleteTask: (id) => api.delete(`/api/tasks/${id}`),

  // Deploy
  createDeploy: (data) => api.post('/api/deploy', data),
  getDeployDetail: (id) => api.get(`/api/deploy/${id}/detail`),
  getJenkinsBuildHistory: (taskId) => api.get('/api/deploy/jenkins/builds', { params: { taskId } }),

  // Artifacts
  getArtifacts: () => api.get('/api/artifacts'),
  deleteArtifact: (id) => api.delete(`/api/artifacts/${id}`),
  downloadArtifact: (id) => `${API_BASE}/api/artifacts/${id}/download`,

  // Notifications
  getNotificationChannels: () => api.get('/api/notification/channels'),
  getNotificationEventTypes: () => api.get('/api/notification/event-types'),
  getNotificationConfigs: () => api.get('/api/notification/configs'),
  createNotificationConfig: (data) => api.post('/api/notification/configs', data),
  updateNotificationConfig: (id, data) => api.put(`/api/notification/configs/${id}`, data),
  deleteNotificationConfig: (id) => api.delete(`/api/notification/configs/${id}`),
  testNotificationConfig: (id, body) => api.post(`/api/notification/configs/${id}/test`, { body }),
  getNotificationHistory: (params) => api.get('/api/notification/history', { params }),

  // Remote file download
  triggerRemoteDownload: (serverId, remotePath) => api.post(`/api/servers/${serverId}/download`, { remotePath }),
  cancelRemoteDownload: (id) => api.post(`/api/downloads/${id}/cancel`),
  deleteRemoteDownload: (id) => api.delete(`/api/downloads/${id}`),
  getRemoteDownload: (id) => api.get(`/api/downloads/${id}`),
  getRemoteDownloadFileUrl: (id) => `${API_BASE}/api/downloads/${id}/file`,
  listRemoteDownloads: (params) => api.get('/api/downloads', { params }),
  listServerDownloads: (serverId, params) => api.get(`/api/servers/${serverId}/downloads`, { params }),

  // System settings
  getSystemSettings: () => api.get('/api/settings'),
  updateSystemSettings: (data) => api.put('/api/settings', data),

  // Auth
  login: (data) => api.post('/api/auth/login', data),
  logout: () => api.post('/api/auth/logout'),
  me: () => api.get('/api/auth/me'),
  getCaptchaStatus: (clientKey) => api.get('/api/auth/captcha/status', { params: { clientKey } }),
  getCaptcha: (clientKey) => api.get('/api/auth/captcha', { params: { clientKey }, responseType: 'blob' }),

  // RBAC
  getUsers: () => api.get('/api/system/users'),
  createUser: (data) => api.post('/api/system/users', data),
  updateUser: (id, data) => api.put(`/api/system/users/${id}`, data),
  deleteUser: (id) => api.delete(`/api/system/users/${id}`),
  getRoles: () => api.get('/api/system/roles'),
  createRole: (data) => api.post('/api/system/roles', data),
  updateRole: (id, data) => api.put(`/api/system/roles/${id}`, data),
  deleteRole: (id) => api.delete(`/api/system/roles/${id}`),
  assignRoleMenus: (id, menuIds) => api.put(`/api/system/roles/${id}/menus`, { menuIds }),
  getMenus: () => api.get('/api/system/menus'),
  createMenu: (data) => api.post('/api/system/menus', data),
  updateMenu: (id, data) => api.put(`/api/system/menus/${id}`, data),
  deleteMenu: (id) => api.delete(`/api/system/menus/${id}`),
  getApiKeys: () => api.get('/api/system/api-keys'),
  getApiKeyOwners: () => api.get('/api/system/api-keys/owners'),
  createApiKey: (data) => api.post('/api/system/api-keys', data),
  updateApiKey: (id, data) => api.put(`/api/system/api-keys/${id}`, data),
  deleteApiKey: (id) => api.delete(`/api/system/api-keys/${id}`),
  getApiKeyStats: (params) => api.get('/api/system/api-keys/stats', { params }),
}
