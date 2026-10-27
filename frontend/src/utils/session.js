import { reactive } from 'vue'

const STORAGE_KEY = 'rd-session'

export const authState = reactive({
  token: '',
  expiresAt: 0,
  loginType: '',
  username: '',
  displayName: '',
  admin: false,
  menus: [],
  ready: false,
})

function persist() {
  if (!authState.token) {
    localStorage.removeItem(STORAGE_KEY)
    return
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify({
    token: authState.token,
    expiresAt: authState.expiresAt,
    loginType: authState.loginType,
    username: authState.username,
    displayName: authState.displayName,
    admin: authState.admin,
    menus: authState.menus,
  }))
}

function load() {
  // 旧版本把管理密钥本身放在浏览器里。会话改为服务端签发，启动时丢掉旧值。
  localStorage.removeItem('adminToken')
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return
  try {
    const data = JSON.parse(raw)
    authState.token = data.token || ''
    authState.expiresAt = Number(data.expiresAt) || 0
    authState.loginType = data.loginType || ''
    authState.username = data.username || ''
    authState.displayName = data.displayName || ''
    authState.admin = !!data.admin
    authState.menus = Array.isArray(data.menus) ? data.menus : []
  } catch {
    localStorage.removeItem(STORAGE_KEY)
  }
}

load()

export function isLoggedIn() {
  if (!authState.token) return false
  if (authState.expiresAt && Date.now() > authState.expiresAt) {
    clearSession()
    return false
  }
  return true
}

export function authToken() {
  return isLoggedIn() ? authState.token : ''
}

export function clientKey() {
  let key = localStorage.getItem('rd-client')
  if (!key || !/^[a-zA-Z0-9]{16,64}$/.test(key)) {
    const uuid = globalThis.crypto?.randomUUID?.() || `${Date.now().toString(16)}${Math.random().toString(16).slice(2)}`
    key = uuid.replace(/-/g, '').slice(0, 32)
    localStorage.setItem('rd-client', key)
  }
  return key
}

export function applyLogin(data) {
  authState.token = data.token || ''
  authState.expiresAt = Number(data.expiresAt) || 0
  authState.loginType = data.loginType || ''
  authState.username = data.username || ''
  authState.displayName = data.displayName || data.username || ''
  authState.admin = !!data.admin
  authState.menus = Array.isArray(data.menus) ? data.menus : []
  authState.ready = true
  localStorage.removeItem('adminToken')
  persist()
}

export function applyProfile(data) {
  authState.username = data.username || authState.username
  authState.displayName = data.displayName || authState.displayName
  authState.admin = !!data.admin
  authState.loginType = data.loginType || authState.loginType
  if (data.expiresAt) authState.expiresAt = Number(data.expiresAt)
  authState.menus = Array.isArray(data.menus) ? data.menus : []
  authState.ready = true
  persist()
}

export function clearSession() {
  authState.token = ''
  authState.expiresAt = 0
  authState.loginType = ''
  authState.username = ''
  authState.displayName = ''
  authState.admin = false
  authState.menus = []
  authState.ready = false
  localStorage.removeItem(STORAGE_KEY)
  localStorage.removeItem('adminToken')
}

export function canAccess(path) {
  if (authState.admin) return true
  return authState.menus.some((menu) => menu.path === path)
}

export function firstMenuPath() {
  const items = authState.menus
    .filter((menu) => menu.menuType === 'menu' && menu.path)
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
  if (items[0]?.path) return items[0].path
  return authState.admin ? '/dashboard' : '/no-access'
}
