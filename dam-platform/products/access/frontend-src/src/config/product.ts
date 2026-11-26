/** 产品级前端构建配置：VITE_PRODUCT_CODE 为空表示全量开发模式 */
export const PRODUCT_CODE = String(import.meta.env.VITE_PRODUCT_CODE || '').toUpperCase()

const PRODUCT_MODULES: Record<string, string[]> = {
  DAM: ['DAM'],
  DPM: ['SYS', 'TC', 'OPS', 'MGW'],
  DAP: ['INT'],
  DGP: ['STD', 'DQ', 'SEC', 'LIF', 'LIN'],
  DPP: ['MDL', 'DEV', 'SCH'],
  DSP: ['DS'],
  DAN: ['MET', 'RPT'],
  DAPP: []
}

export function isProductMode(): boolean {
  return PRODUCT_CODE.length > 0
}

export function productModules(): string[] {
  return PRODUCT_MODULES[PRODUCT_CODE] || []
}

export function productAllowsModule(code?: string | null): boolean {
  if (!isProductMode()) return true
  if (!code) return true
  return productModules().includes(code)
}

export function productAllowsPath(path: string): boolean {
  if (!isProductMode()) return true
  if (path === '/login' || path === '/profile') return true
  if (path === '/portal' || path === '/') return true
  const allowed: Record<string, string[]> = {
    DAM: ['/catalog', '/catalog-detail', '/metadata'],
    DPM: ['/portal', '/task-center', '/operations', '/system-config', '/cockpit'],
    DAP: ['/integration', '/data-sources'],
    DGP: ['/standard', '/quality', '/security', '/lifecycle', '/lineage'],
    DPP: ['/models', '/develop', '/scheduler'],
    DSP: ['/data-service'],
    DAN: ['/metrics', '/reports'],
    DAPP: ['/app-home', '/portal']
  }
  return (allowed[PRODUCT_CODE] || []).some(prefix => path === prefix || path.startsWith(prefix + '/'))
}

export const PRODUCT_HOME: Record<string, string> = {
  DAM: '/catalog',
  DPM: '/operations',
  DAP: '/integration',
  DGP: '/quality',
  DPP: '/models',
  DSP: '/data-service',
  DAN: '/reports',
  DAPP: '/app-home'
}

export function productHomePath(): string {
  return PRODUCT_HOME[PRODUCT_CODE] || '/portal'
}

export function productTitle(): string {
  return {
    DAM: '数据资产与主数据', DPM: '数据中台管理', DAP: '数据接入',
    DGP: '数据治理', DPP: '数据处理', DSP: '数据服务',
    DAN: '数据分析', DAPP: '数据应用'
  }[PRODUCT_CODE] || '数据资产管理平台'
}
