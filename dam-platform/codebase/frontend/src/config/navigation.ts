import { getPortalDomain } from './portal'

/**
 * 登录后的导航模型：
 * 1. 门户模式（/portal、/domains/*、/profile、/system-config）只展示三个固定入口；
 * 2. 模块模式由当前路由的 moduleCode 推导（系统配置的 SYS/MGW 使用独立路径与 meta），
 *    刷新页面后仍能恢复该模块的详细菜单，不依赖内存状态。
 */

/** 门户模式下的三个固定入口 */
export interface PortalFixedMenuItem {
  title: string
  path: string
  icon: string
}

export const portalFixedMenuItems: PortalFixedMenuItem[] = [
  { title: '统一门户', path: '/portal', icon: 'Grid' },
  { title: '个人中心', path: '/profile', icon: 'User' },
  { title: '系统配置', path: '/system-config', icon: 'Setting' }
]

/** 模块详细菜单项 */
export interface ModuleMenuItem {
  title: string
  icon: string
  /**
   * 目标路径。动态项使用 `:参数名` 占位，例如 /catalog-detail/:id；
   * 动态参数缺失时该菜单项会禁用，避免生成死链。
   */
  path: string
  /** 动态参数名（从当前路由 params 中读取） */
  param?: string
  /** 当前路由匹配前缀；默认从 path 中自动截取 */
  matchPrefix?: string
  /** 动态参数缺失时的提示文案 */
  hint?: string
}

/** 一个能力域模块对应的导航上下文 */
export interface ModuleNavigation {
  code: string
  name: string
  /** 所属能力域编码；运营看板等独立页面可以没有 */
  domainCode?: string
  domainName?: string
  /** 模块入口路径，用于面包屑回到模块首页 */
  basePath: string
  menu: ModuleMenuItem[]
}

/** 解析后的菜单项：可直接用于渲染 */
export interface ResolvedModuleMenuItem extends ModuleMenuItem {
  target: string
  disabled: boolean
  active: boolean
  hint: string
}

interface ModuleRouteContext {
  path: string
  params: Record<string, string | string[]>
}

/** 从 portal.ts 的能力域配置中读取模块/能力域名称，避免两处配置漂移 */
function defineModule(
  code: string,
  domainCode: string | undefined,
  basePath: string,
  menu: ModuleMenuItem[]
): ModuleNavigation {
  const domain = domainCode ? getPortalDomain(domainCode) : undefined
  const portalModule = domain?.modules.find(module => module.code === code)
  return {
    code,
    name: portalModule?.name || code,
    domainCode,
    domainName: domain?.name,
    basePath,
    menu
  }
}

/**
 * 模块 -> 详细菜单映射。
 * 这里只收录当前前端真实存在的路由，规划中模块不在此表中，门户内保持禁用。
 */
export const moduleNavigations: Record<string, ModuleNavigation> = {
  // 数据资产与主数据
  DAM: defineModule('DAM', 'asset', '/catalog', [
    { title: '资产目录', icon: 'FolderOpened', path: '/catalog' },
    { title: '数据源', icon: 'Connection', path: '/data-sources' },
    { title: '元数据采集', icon: 'DataBoard', path: '/metadata' },
    {
      title: '资产详情',
      icon: 'Document',
      path: '/catalog-detail/:id',
      param: 'id',
      matchPrefix: '/catalog-detail',
      hint: '请先在资产目录中打开一个资产'
    }
  ]),

  // 标准与模型
  STD: defineModule('STD', 'standard', '/standard', [
    { title: '数据标准', icon: 'Document', path: '/standard' }
  ]),
  MDL: defineModule('MDL', 'standard', '/models', [
    { title: '模型管理', icon: 'Collection', path: '/models' }
  ]),

  // 数据工程
  INT: defineModule('INT', 'engineering', '/integration', [
    { title: '数据集成', icon: 'Connection', path: '/integration' }
  ]),
  DEV: defineModule('DEV', 'engineering', '/develop', [
    { title: '数据开发', icon: 'Cpu', path: '/develop' }
  ]),
  SCH: defineModule('SCH', 'engineering', '/scheduler', [
    { title: '调度中心', icon: 'Timer', path: '/scheduler' }
  ]),

  // 数据治理
  DQ: defineModule('DQ', 'governance', '/quality', [
    { title: '数据质量', icon: 'Histogram', path: '/quality' }
  ]),
  LIN: defineModule('LIN', 'governance', '/lineage', [
    { title: '数据血缘', icon: 'Share', path: '/lineage' }
  ]),
  SEC: defineModule('SEC', 'governance', '/security', [
    { title: '安全管控', icon: 'Lock', path: '/security' }
  ]),
  LIF: defineModule('LIF', 'governance', '/lifecycle', [
    { title: '生命周期', icon: 'Odometer', path: '/lifecycle' }
  ]),

  // 指标与分析
  MET: defineModule('MET', 'analytics', '/metrics', [
    { title: '指标管理', icon: 'TrendCharts', path: '/metrics' }
  ]),
  RPT: defineModule('RPT', 'analytics', '/reports', [
    { title: '报表体系', icon: 'DataAnalysis', path: '/reports' }
  ]),

  // 数据服务与市场
  DS: defineModule('DS', 'service', '/data-service', [
    { title: '数据服务', icon: 'Link', path: '/data-service' }
  ]),

  // AI 与知识
  KNO: defineModule('KNO', 'ai', '/knowledge', [
    { title: '知识中心', icon: 'Reading', path: '/knowledge' }
  ]),
  AI: defineModule('AI', 'ai', '/agent', [
    { title: 'AI 工作台', icon: 'MagicStick', path: '/agent' },
    { title: 'AI 任务中心', icon: 'Tickets', path: '/agent/tasks' }
  ]),
  MGW: defineModule('MGW', 'ai', '/system-config/MGW', [
    { title: '模型网关', icon: 'Platform', path: '/system-config/MGW' }
  ]),

  // 平台与运维
  TC: defineModule('TC', 'platform', '/task-center', [
    { title: '任务中心', icon: 'Tickets', path: '/task-center' }
  ]),
  OPS: defineModule('OPS', 'platform', '/operations', [
    { title: '工单中心', icon: 'Tools', path: '/operations' }
  ]),
  SYS: defineModule('SYS', 'platform', '/system-config/SYS', [
    { title: '系统配置', icon: 'Setting', path: '/system-config/SYS' }
  ]),

  // 独立业务页（不在 8 个能力域中，但保留模块模式与返回门户入口）
  DASH: {
    code: 'DASH',
    name: '运营看板',
    basePath: '/dashboard',
    menu: [{ title: '运营看板', icon: 'DataAnalysis', path: '/dashboard' }]
  }
}

/** 按编码获取模块导航；未收录的编码返回 undefined */
export function getModuleNavigation(code?: string | null): ModuleNavigation | undefined {
  if (!code) return undefined
  return moduleNavigations[code]
}

/** 去掉末尾斜杠，保证 /catalog/ 与 /catalog 的高亮/布局判断一致 */
function normalizePath(path: string): string {
  const normalized = path.replace(/\/+$/, '')
  return normalized || '/'
}

/** 是否是只展示三个固定入口的门户/系统页面 */
export function isPortalLayoutPath(path: string): boolean {
  const normalized = normalizePath(path)
  return normalized === '/portal'
    || normalized === '/profile'
    || normalized === '/system-config'
    || normalized.startsWith('/domains/')
}

/** 门户模式下需要高亮的固定入口 */
export function resolvePortalActiveMenu(path: string): string {
  const normalized = normalizePath(path)
  if (normalized === '/profile') return '/profile'
  if (normalized === '/system-config') return '/system-config'
  return '/portal'
}

/**
 * 将模块菜单解析为渲染项：
 * - 普通项按当前路径精确匹配高亮；
 * - 动态项（如资产详情）从当前路由参数取值，缺参时禁用，绝不产生死链。
 */
export function resolveModuleMenu(
  navigation: ModuleNavigation,
  current: ModuleRouteContext
): ResolvedModuleMenuItem[] {
  const currentPath = normalizePath(current.path)
  return navigation.menu.map(item => {
    if (!item.param) {
      return {
        ...item,
        target: item.path,
        disabled: false,
        active: currentPath === item.path,
        hint: item.hint || ''
      }
    }

    const rawValue = current.params[item.param]
    const value = Array.isArray(rawValue) ? rawValue[0] : rawValue
    const prefix = item.matchPrefix || item.path.split('/:')[0]
    const disabled = !value
    return {
      ...item,
      target: disabled ? item.path : item.path.replace(`:${item.param}`, String(value)),
      disabled,
      active: !disabled && currentPath.startsWith(prefix),
      hint: disabled ? (item.hint || '缺少页面参数') : ''
    }
  })
}
