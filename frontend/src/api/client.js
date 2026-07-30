import axios from 'axios'

const API_BASE = import.meta.env.VITE_API_BASE ?? ''

const api = axios.create({
  baseURL: API_BASE,
  timeout: 30000,
})

// Request interceptor - add auth token
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('adminToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Response interceptor - handle 401
api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error.response?.status === 401) {
      const token = prompt('请输入管理Token:')
      if (token) {
        localStorage.setItem('adminToken', token)
        window.location.reload()
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
  getDeployDetail: (id) => api.get(`/api/deploy/${id}`),
  getJenkinsBuildHistory: (taskId) => api.get('/api/deploy/jenkins/builds', { params: { taskId } }),

  // Artifacts
  getArtifacts: () => api.get('/api/artifacts'),
  deleteArtifact: (id) => api.delete(`/api/artifacts/${id}`),
  downloadArtifact: (id) => `${API_BASE}/api/artifacts/${id}/download`,
}
