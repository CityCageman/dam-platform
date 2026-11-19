import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  buildRoleMenu,
  canAccessCockpit,
  matchRouteRoles,
  normalizeRoles,
  resolveDefaultCockpit,
  ROLE_DEFINITIONS,
  FALLBACK_ROLE,
  type RoleCode
} from '@/config/roles'

/** 角色化用户信息缓存：刷新后无需等待 /auth/me 即可生成菜单与默认首页 */
const PROFILE_KEY = 'dam.userProfile'

interface CachedProfile {
  userInfo?: Record<string, any> | null
  roles?: RoleCode[]
  dataScope?: string
  defaultCockpit?: string
  permissions?: string[]
  department?: string
}

function readCachedProfile(): CachedProfile {
  try {
    const raw = localStorage.getItem(PROFILE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

export const useUserStore = defineStore('user', () => {
  const cached = readCachedProfile()

  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref<any>(cached.userInfo || null)
  /** 角色编码（后端字段未就绪时按旧 role 或超管兜底） */
  const roles = ref<RoleCode[]>(normalizeRoles(cached.roles, userInfo.value?.role))
  /** 数据范围：DEPARTMENT / ALL / SELF 等，后端返回前保持空字符串 */
  const dataScope = ref<string>(cached.dataScope || '')
  /** 后端返回的默认驾驶舱（可能是 department 或 /cockpit/department） */
  const defaultCockpit = ref<string>(cached.defaultCockpit || '')
  /** 权限码，当前用于展示与后续扩展，不阻塞页面 */
  const permissions = ref<string[]>(Array.isArray(cached.permissions) ? cached.permissions : [])
  /** 科室名称，部门驾驶舱展示与后续查询使用 */
  const department = ref<string>(cached.department || cached.userInfo?.department || '')

  /** 主角色（多角色时用于文案展示） */
  const primaryRole = computed<RoleCode>(() => roles.value[0] || FALLBACK_ROLE)

  /** 当前默认首页（登录后 / 与 /dashboard 均跳转至此；defaultCockpit 与角色不匹配时回退） */
  const homePath = computed(() => {
    const resolved = resolveDefaultCockpit(roles.value, defaultCockpit.value)
    if (canAccessCockpit(resolved, roles.value)) return resolved
    return ROLE_DEFINITIONS[primaryRole.value].cockpitPath
  })

  /** 当前角色的左侧菜单矩阵 */
  const roleMenu = computed(() => buildRoleMenu(roles.value))

  /** 角色中文名列表 */
  const roleNames = computed(() => roles.value.map(role => ROLE_DEFINITIONS[role].name))

  const isSuperAdmin = computed(() => roles.value.includes('super_admin'))

  function persist() {
    const profile: CachedProfile = {
      userInfo: userInfo.value,
      roles: roles.value,
      dataScope: dataScope.value,
      defaultCockpit: defaultCockpit.value,
      permissions: permissions.value,
      department: department.value
    }
    localStorage.setItem(PROFILE_KEY, JSON.stringify(profile))
  }

  function setToken(t: string) {
    token.value = t
    if (t) localStorage.setItem('token', t)
    else localStorage.removeItem('token')
  }

  function setUserInfo(info: any) {
    userInfo.value = info
    persist()
  }

  /** 单值归一化为字符串数组（兼容逗号分隔字符串） */
  function toStringArray(value: unknown): string[] {
    if (Array.isArray(value)) return value.filter(item => item != null).map(String)
    if (typeof value === 'string') return value.split(/[,，\s]+/).filter(Boolean)
    return []
  }

  /**
   * 统一接收 /auth/login 与 /auth/me 的返回：
   * - 登录：{ token, userInfo }，角色字段可能在顶层或 userInfo 内；
   * - 当前用户：userInfo 平铺，也可能包一层 { userInfo }。
   * 只覆盖后端实际返回的字段，避免 /auth/me 暂未返回 roles 时冲掉登录时拿到的角色。
   */
  function applyAuthPayload(payload: any, options: { login?: boolean } = {}) {
    if (!payload || typeof payload !== 'object') return

    const hasWrappedUser = payload.userInfo && typeof payload.userInfo === 'object'
    const info = hasWrappedUser ? payload.userInfo : payload

    if (typeof payload.token === 'string' && payload.token) {
      setToken(payload.token)
    }

    if (info && typeof info === 'object') {
      // 登录是权威快照，直接替换；/auth/me 增量合并，避免缺失字段冲掉已有信息
      userInfo.value = options.login
        ? { ...info }
        : { ...(userInfo.value || {}), ...info }
    }

    const rawRoles = payload.roles
      ?? payload.roleCodes
      ?? info?.roles
      ?? info?.roleCodes
    if (rawRoles !== undefined && rawRoles !== null) {
      roles.value = normalizeRoles(rawRoles, info?.role)
    } else if (options.login || !roles.value.length) {
      // 登录未返回 roles 时按旧 role 兼容；仍无角色则回退超管，避免登录后被全部拦截
      roles.value = normalizeRoles(undefined, info?.role)
    }

    const nextDataScope = payload.dataScope ?? info?.dataScope
    if (nextDataScope !== undefined && nextDataScope !== null) {
      dataScope.value = String(nextDataScope)
    } else if (options.login) {
      dataScope.value = ''
    }

    const nextCockpit = payload.defaultCockpit ?? info?.defaultCockpit
    if (nextCockpit !== undefined && nextCockpit !== null && String(nextCockpit).trim()) {
      defaultCockpit.value = String(nextCockpit)
    } else if (options.login) {
      defaultCockpit.value = ''
    }

    const rawPermissions = payload.permissions ?? info?.permissions
    if (rawPermissions !== undefined && rawPermissions !== null) {
      permissions.value = toStringArray(rawPermissions)
    } else if (options.login) {
      permissions.value = []
    }

    const nextDepartment = payload.department ?? info?.department
    if (nextDepartment !== undefined && nextDepartment !== null) {
      department.value = String(nextDepartment)
    } else if (options.login) {
      department.value = info?.department || ''
    } else if (!department.value) {
      department.value = info?.department || ''
    }

    persist()
  }

  /** 判断当前用户是否可访问某路由 meta.roles（超管始终放行） */
  function canAccess(allowed?: string[]): boolean {
    return matchRouteRoles(allowed, roles.value)
  }

  /** 是否具备权限码（后端未返回权限时保持宽松） */
  function hasPermission(code: string): boolean {
    if (!permissions.value.length) return true
    return permissions.value.includes(code)
  }

  let profileLoaded = false
  let profilePromise: Promise<void> | null = null

  /**
   * 从 /auth/me 刷新角色等用户信息：
   * - 首次进入路由时调用一次；
   * - 接口失败时保留登录/缓存中的信息，不阻塞页面渲染。
   */
  async function ensureProfile(force = false): Promise<void> {
    if (!token.value) return
    if (profileLoaded && !force) return
    if (profilePromise) return profilePromise

    profilePromise = (async () => {
      try {
        // 动态导入，避免 store -> api -> request -> router 的静态循环依赖
        const { authApi } = await import('@/api')
        const res: any = await authApi.getCurrentUser()
        applyAuthPayload(res?.data ?? res)
      } catch {
        // /auth/me 暂不可用时继续使用本地缓存的角色信息
      } finally {
        profileLoaded = true
        profilePromise = null
      }
    })()

    return profilePromise
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    roles.value = [FALLBACK_ROLE]
    dataScope.value = ''
    defaultCockpit.value = ''
    permissions.value = []
    department.value = ''
    profileLoaded = false
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    localStorage.removeItem(PROFILE_KEY)
  }

  return {
    token,
    userInfo,
    roles,
    dataScope,
    defaultCockpit,
    permissions,
    department,
    homePath,
    primaryRole,
    roleMenu,
    roleNames,
    isSuperAdmin,
    setToken,
    setUserInfo,
    applyAuthPayload,
    canAccess,
    hasPermission,
    ensureProfile,
    logout
  }
})
