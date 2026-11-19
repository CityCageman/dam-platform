import { portalDomains } from './portal'

/**
 * 角色化前端配置：
 * - 角色编码、默认驾驶舱与菜单矩阵集中在此，路由守卫与 MainLayout 共用，避免两处漂移；
 * - 后端 /auth/login、/auth/me 尚未返回 roles/defaultCockpit 时，前端按旧 role 字段或
 *   超管兜底，保证已有账号不会因字段缺省被挡在门外。
 */

export type RoleCode =
  | 'super_admin'
  | 'executive'
  | 'department_head'
  | 'data_ops'
  | 'developer'
  | 'governor'
  | 'business'
  | 'security'

export const ALL_ROLES: RoleCode[] = [
  'super_admin',
  'executive',
  'department_head',
  'data_ops',
  'developer',
  'governor',
  'business',
  'security'
]

export interface RoleDefinition {
  code: RoleCode
  /** 角色全称 */
  name: string
  /** 驾驶舱名称（左侧菜单文案） */
  cockpitTitle: string
  /** 默认驾驶舱路径 */
  cockpitPath: string
  /** 驾驶舱图标（Element Plus 图标名） */
  icon: string
  /** 驾驶舱说明 */
  description: string
}

export const ROLE_DEFINITIONS: Record<RoleCode, RoleDefinition> = {
  super_admin: {
    code: 'super_admin',
    name: '超级管理员',
    cockpitTitle: '超级管理驾驶舱',
    cockpitPath: '/cockpit/super',
    icon: 'Setting',
    description: '平台全量总览：运行、资产、任务、API 与待办一屏掌握。'
  },
  executive: {
    code: 'executive',
    name: '院领导',
    cockpitTitle: '院领导驾驶舱',
    cockpitPath: '/cockpit/executive',
    icon: 'TrendCharts',
    description: '全院资产规模、服务调用、质量水位与风险概览。'
  },
  department_head: {
    code: 'department_head',
    name: '科主任',
    cockpitTitle: '科室驾驶舱',
    cockpitPath: '/cockpit/department',
    icon: 'DataBoard',
    description: '本科室资产、质量异常、数据申请与指标待办。'
  },
  data_ops: {
    code: 'data_ops',
    name: '数据运维',
    cockpitTitle: '平台驾驶舱',
    cockpitPath: '/cockpit/ops',
    icon: 'Monitor',
    description: '服务状态、失败任务、集成与 API 运行情况。'
  },
  developer: {
    code: 'developer',
    name: '数据开发',
    cockpitTitle: '开发工作台',
    cockpitPath: '/cockpit/developer',
    icon: 'Cpu',
    description: '我的开发任务、待发布、调度失败与最近执行。'
  },
  governor: {
    code: 'governor',
    name: '数据管家',
    cockpitTitle: '数据管家驾驶舱',
    cockpitPath: '/cockpit/governance',
    icon: 'Histogram',
    description: '数据质量、标准与治理协同概览。'
  },
  business: {
    code: 'business',
    name: '业务用户',
    cockpitTitle: '业务驾驶舱',
    cockpitPath: '/cockpit/business',
    icon: 'User',
    description: '可申请资产、数据服务与个人待办。'
  },
  security: {
    code: 'security',
    name: '安全管理员',
    cockpitTitle: '安全驾驶舱',
    cockpitPath: '/cockpit/security',
    icon: 'Lock',
    description: '数据访问审批、安全规则与审计概览。'
  }
}

/** 所有驾驶舱路径，用于校验后端 defaultCockpit 是否合法 */
export const COCKPIT_PATHS = new Set(ALL_ROLES.map(role => ROLE_DEFINITIONS[role].cockpitPath))

/** 可进入系统配置的角色 */
export const SYSTEM_CONFIG_ROLES: RoleCode[] = ['super_admin', 'data_ops', 'security', 'governor']

/** 旧账号角色字段 -> 新角色编码（后端字段就绪前兼容） */
const LEGACY_ROLE_MAP: Record<string, RoleCode> = {
  admin: 'super_admin',
  administrator: 'super_admin',
  super_admin: 'super_admin',
  superadmin: 'super_admin',
  系统管理员: 'super_admin',
  超级管理员: 'super_admin',
  executive: 'executive',
  leader: 'executive',
  hospital_leader: 'executive',
  院领导: 'executive',
  department_head: 'department_head',
  dept_head: 'department_head',
  department: 'department_head',
  科主任: 'department_head',
  data_ops: 'data_ops',
  ops: 'data_ops',
  operator: 'data_ops',
  数据运维: 'data_ops',
  developer: 'developer',
  dev: 'developer',
  data_developer: 'developer',
  数据开发: 'developer',
  governor: 'governor',
  steward: 'governor',
  data_governor: 'governor',
  数据管家: 'governor',
  business: 'business',
  analyst: 'business',
  user: 'business',
  业务用户: 'business',
  数据分析师: 'business',
  security: 'security',
  security_admin: 'security',
  安全管理员: 'security'
}

/** 字段缺省时的兜底角色：沿用原平台管理员可见全量入口的行为 */
export const FALLBACK_ROLE: RoleCode = 'business'

/** 单个角色值归一化，支持编码、中文名、旧角色名 */
export function normalizeRoleCode(value: unknown): RoleCode | undefined {
  if (typeof value !== 'string') return undefined
  const key = value.trim()
  if (!key) return undefined
  const lower = key.toLowerCase()
  if (LEGACY_ROLE_MAP[key]) return LEGACY_ROLE_MAP[key]
  if (LEGACY_ROLE_MAP[lower]) return LEGACY_ROLE_MAP[lower]
  return (ALL_ROLES as string[]).includes(lower) ? (lower as RoleCode) : undefined
}

/**
 * 归一化角色数组：
 * 支持 string[]、逗号分隔字符串、单个字符串，以及旧 userInfo.role。
 * 全部无法识别时回退到 FALLBACK_ROLE，避免角色字段缺失导致菜单与路由全部不可用。
 */
export function normalizeRoles(raw: unknown, legacyRole?: unknown): RoleCode[] {
  const values: unknown[] = Array.isArray(raw)
    ? raw
    : typeof raw === 'string'
      ? raw.split(/[,，\s]+/).filter(Boolean)
      : raw == null
        ? []
        : [raw]

  const result: RoleCode[] = []
  for (const value of values) {
    const code = normalizeRoleCode(value)
    if (code && !result.includes(code)) result.push(code)
  }
  if (!result.length) {
    const legacy = normalizeRoleCode(legacyRole)
    if (legacy) result.push(legacy)
  }
  return result.length ? result : [FALLBACK_ROLE]
}

/** defaultCockpit 支持 "department" 与 "/cockpit/department" 两种写法 */
const COCKPIT_ALIASES: Record<string, string> = {
  super: '/cockpit/super',
  super_admin: '/cockpit/super',
  executive: '/cockpit/executive',
  department: '/cockpit/department',
  department_head: '/cockpit/department',
  ops: '/cockpit/ops',
  data_ops: '/cockpit/ops',
  developer: '/cockpit/developer',
  dev: '/cockpit/developer',
  governance: '/cockpit/governance',
  governor: '/cockpit/governance',
  business: '/cockpit/business',
  security: '/cockpit/security'
}

/** 将后端 defaultCockpit 归一化为合法路由路径；非法值返回 undefined */
export function normalizeCockpitPath(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined
  const raw = value.trim()
  if (!raw) return undefined
  if (raw.startsWith('/')) {
    const normalized = raw.replace(/\/+$/, '')
    return COCKPIT_PATHS.has(normalized) ? normalized : undefined
  }
  const mapped = COCKPIT_ALIASES[raw.toLowerCase()]
  return mapped && COCKPIT_PATHS.has(mapped) ? mapped : undefined
}

/**
 * 计算用户默认驾驶舱：
 * 1. 优先使用后端 defaultCockpit；
 * 2. 其次使用第一个已知角色的驾驶舱；
 * 3. 最后回退超管驾驶舱。
 */
export function resolveDefaultCockpit(roles: RoleCode[], defaultCockpit?: unknown): string {
  const explicit = normalizeCockpitPath(defaultCockpit)
  if (explicit) return explicit
  const primary = roles.find(role => ROLE_DEFINITIONS[role])
  return primary ? ROLE_DEFINITIONS[primary].cockpitPath : ROLE_DEFINITIONS[FALLBACK_ROLE].cockpitPath
}

/** 驾驶舱路径 -> 所属角色（用于校验 defaultCockpit 与角色是否匹配） */
const COCKPIT_OWNER: Record<string, RoleCode> = Object.fromEntries(
  ALL_ROLES.map(role => [ROLE_DEFINITIONS[role].cockpitPath, role])
) as Record<string, RoleCode>

/** 角色是否可进入指定驾驶舱；super_admin 可进入全部 */
export function canAccessCockpit(path: string, roles: RoleCode[]): boolean {
  if (isSuperAdmin(roles)) return true
  const owner = COCKPIT_OWNER[path]
  return !owner || roles.includes(owner)
}

/** 判断角色是否可进入系统配置 */
export function canManageSystem(roles: RoleCode[]): boolean {
  return roles.some(role => SYSTEM_CONFIG_ROLES.includes(role))
}

/** 判断角色是否具备超管全量视图 */
export function isSuperAdmin(roles: RoleCode[]): boolean {
  return roles.includes('super_admin')
}

/** 路由 meta.roles 匹配；未声明 roles 视为所有登录用户可访问 */
export function matchRouteRoles(routeRoles: unknown, roles: RoleCode[]): boolean {
  if (!Array.isArray(routeRoles) || routeRoles.length === 0) return true
  if (isSuperAdmin(roles)) return true
  return routeRoles.some(role => typeof role === 'string' && (roles as string[]).includes(role))
}

/** 模块级路由授权：未列出的模块默认仅 super_admin 可直连 */
export const MODULE_ROLE_MAP: Record<string, RoleCode[]> = {
  DAM: ['super_admin', 'executive', 'department_head', 'governor', 'business', 'security'],
  STD: ['super_admin', 'executive', 'governor', 'department_head'],
  MDL: ['super_admin', 'developer', 'governor'],
  INT: ['super_admin', 'data_ops', 'developer', 'governor'],
  DEV: ['super_admin', 'developer'],
  SCH: ['super_admin', 'data_ops', 'developer'],
  LIN: ['super_admin', 'executive', 'department_head', 'governor', 'developer', 'security'],
  DQ: ['super_admin', 'executive', 'department_head', 'governor', 'security'],
  SEC: ['super_admin', 'executive', 'department_head', 'governor', 'security'],
  LIF: ['super_admin', 'governor', 'department_head'],
  MET: ['super_admin', 'executive', 'department_head', 'business', 'governor'],
  RPT: ['super_admin', 'executive', 'department_head', 'business', 'governor'],
  DS: ['super_admin', 'data_ops', 'developer', 'business'],
  KNO: ['super_admin', 'executive', 'department_head', 'business', 'governor'],
  AI: ['super_admin', 'executive', 'department_head', 'business', 'governor', 'developer'],
  TC: ['super_admin', 'data_ops', 'developer', 'executive'],
  OPS: ['super_admin', 'executive', 'department_head', 'data_ops', 'governor'],
  SYS: ['super_admin', 'governor', 'data_ops', 'security'],
  MGW: ['super_admin', 'data_ops', 'developer']
}

/** 返回模块的允许角色；未知模块只允许超管 */
export function rolesForModule(code: string): string[] {
  return MODULE_ROLE_MAP[code] || ['super_admin']
}

/** 当前角色能否访问模块 */
export function canAccessModule(code: string, roles: RoleCode[]): boolean {
  if (isSuperAdmin(roles)) return true
  const allowed = MODULE_ROLE_MAP[code]
  return !allowed || allowed.some(role => roles.includes(role))
}

export interface RoleMenuItem {
  title: string
  path: string
  icon: string
}

export interface RoleMenuGroup {
  title: string
  items: RoleMenuItem[]
}

/**
 * 按角色生成左侧菜单分组：
 * - 院领导/科主任/业务：驾驶舱、产品中心、个人中心；
 * - 数据运维：平台驾驶舱、产品中心、个人中心、系统配置；
 * - 数据开发：开发工作台、产品中心、个人中心；
 * - 超级管理员：所有驾驶舱、产品中心、所有已上线产品模块、个人中心、系统配置。
 */
export function buildRoleMenu(roles: RoleCode[]): RoleMenuGroup[] {
  const safeRoles = roles.length ? roles : [FALLBACK_ROLE]
  const superAdmin = isSuperAdmin(safeRoles)
  const groups: RoleMenuGroup[] = []
  const seen = new Set<string>()

  const addGroup = (title: string, items: RoleMenuItem[]) => {
    const unique = items.filter(item => {
      if (!item.path || seen.has(item.path)) return false
      seen.add(item.path)
      return true
    })
    if (unique.length) groups.push({ title, items: unique })
  }

  // 驾驶舱入口
  if (superAdmin) {
    addGroup('全部驾驶舱', ALL_ROLES.map(role => {
      const definition = ROLE_DEFINITIONS[role]
      return { title: definition.cockpitTitle, path: definition.cockpitPath, icon: definition.icon }
    }))
  } else {
    addGroup('我的驾驶舱', safeRoles.map(role => {
      const definition = ROLE_DEFINITIONS[role]
      return { title: definition.cockpitTitle, path: definition.cockpitPath, icon: definition.icon }
    }))
  }

  // 统一入口
  const entryItems: RoleMenuItem[] = [{ title: '产品中心', path: '/portal', icon: 'Grid' }]
  entryItems.push({ title: '个人中心', path: '/profile', icon: 'User' })
  if (canManageSystem(safeRoles)) {
    entryItems.push({ title: '系统配置', path: '/system-config', icon: 'Setting' })
  }
  addGroup('统一入口', entryItems)

  // 超级管理员额外展示全部已上线产品模块（按能力域分组）
  if (superAdmin) {
    for (const domain of portalDomains) {
      const modules = domain.modules
        .filter(module => module.status === 'AVAILABLE')
        .map(module => ({ title: module.name, path: module.route, icon: module.icon }))
      if (modules.length) addGroup(domain.name, modules)
    }
  }

  return groups
}

/** 当前路径命中的角色菜单项（用于左侧菜单高亮） */
export function resolveRoleMenuActive(path: string, groups: RoleMenuGroup[]): string {
  const normalized = (path.replace(/\/+$/, '') || '/')
  for (const group of groups) {
    const matched = group.items.find(item => item.path === normalized)
    if (matched) return matched.path
  }
  return ''
}
