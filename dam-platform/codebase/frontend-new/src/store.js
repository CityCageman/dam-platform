import { reactive } from 'vue'

const TOKEN_KEY = 'dap_token'
const USER_KEY = 'dap_user'
const PERM_KEY = 'dap_permissions'

export const auth = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: JSON.parse(localStorage.getItem(USER_KEY) || 'null'),
  permissions: JSON.parse(localStorage.getItem(PERM_KEY) || '[]')
})

export function setLogin(token, user, permissions) {
  auth.token = token
  auth.user = user
  auth.permissions = permissions || []
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
  localStorage.setItem(PERM_KEY, JSON.stringify(permissions || []))
}

export function clearLogin() {
  auth.token = ''
  auth.user = null
  auth.permissions = []
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(PERM_KEY)
}

export function hasPermission(code) {
  return auth.permissions.includes(code)
}

export const ROLE_META = {
  ADMIN: { name: '数据管理员', color: '#F5222D' },
  STEWARD: { name: '数据管家', color: '#FA8C16' },
  ANALYST: { name: '数据分析师', color: '#1677FF' }
}
