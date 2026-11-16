import type { Component } from 'vue'
import {
  Bell,
  Box,
  Coin,
  Collection,
  Connection,
  Cpu,
  DataAnalysis,
  Document,
  FolderOpened,
  Grid,
  Histogram,
  Link,
  Lock,
  MagicStick,
  Monitor,
  Odometer,
  Platform,
  Reading,
  Setting,
  Share,
  Shop,
  Tickets,
  Timer,
  Tools,
  TrendCharts
} from '@element-plus/icons-vue'

/** 能力域/模块状态：AVAILABLE 已上线（可进入），PLANNED 规划中（禁用） */
export type PortalModuleStatus = 'AVAILABLE' | 'PLANNED'

export interface PortalModule {
  /** 模块编码，与后端能力域模块编码保持一致 */
  code: string
  name: string
  description: string
  /** Element Plus 图标名称，通过 resolvePortalIcon 解析 */
  icon: string
  /** 现有前端路由地址；PLANNED 时为目标地址，不参与跳转 */
  route: string
  status: PortalModuleStatus
}

export interface PortalDomain {
  /** 能力域编码，同时作为 /domains/:domainCode 的路由参数 */
  code: string
  name: string
  description: string
  icon: string
  route: string
  modules: PortalModule[]
}

/** Element Plus 图标白名单：未匹配时回退到通用图标，避免动态组件解析失败 */
const PORTAL_ICONS: Record<string, Component> = {
  Bell,
  Box,
  Coin,
  Collection,
  Connection,
  Cpu,
  DataAnalysis,
  Document,
  FolderOpened,
  Grid,
  Histogram,
  Link,
  Lock,
  MagicStick,
  Monitor,
  Odometer,
  Platform,
  Reading,
  Setting,
  Share,
  Shop,
  Tickets,
  Timer,
  Tools,
  TrendCharts
}

/** 解析能力域/模块图标，缺失时统一回退到 Grid */
export function resolvePortalIcon(name?: string): Component {
  if (!name) return Grid
  return PORTAL_ICONS[name] || Grid
}

/**
 * 8 个能力域 / 23 个模块。
 * 模块路由只指向当前前端已存在的页面；尚无页面的模块标记为 PLANNED，门户内禁用，避免死链。
 */
export const portalDomains: PortalDomain[] = [
  {
    code: 'asset',
    name: '数据资产与主数据',
    description: '统一登记数据资产与主数据，摸清数据家底，沉淀可复用的数据目录。',
    icon: 'FolderOpened',
    route: '/domains/asset',
    modules: [
      {
        code: 'DAM',
        name: '数据资产管理',
        description: '资产目录、元数据采集与资产详情管理，掌握资产分布与使用情况。',
        icon: 'FolderOpened',
        route: '/catalog',
        status: 'AVAILABLE'
      },
      {
        code: 'MDM',
        name: '主数据管理',
        description: '主数据模型、编码规则与分发管理，保障核心数据一致可信。',
        icon: 'Coin',
        route: '/mdm',
        status: 'PLANNED'
      }
    ]
  },
  {
    code: 'standard',
    name: '标准与模型',
    description: '以数据标准和数据模型为抓手，统一业务口径与数据结构。',
    icon: 'Document',
    route: '/domains/standard',
    modules: [
      {
        code: 'STD',
        name: '数据标准',
        description: '维护码表、标准值域与字段映射，发布后自动校验并生成偏差报告。',
        icon: 'Document',
        route: '/standard',
        status: 'AVAILABLE'
      },
      {
        code: 'MDL',
        name: '数据建模',
        description: '维护主题域模型与字段映射，支持模型发布、DDL 预览和合规对比。',
        icon: 'Collection',
        route: '/models',
        status: 'AVAILABLE'
      }
    ]
  },
  {
    code: 'engineering',
    name: '数据工程',
    description: '覆盖数据集成、开发、调度与存储计算，支撑数据加工链路高效运转。',
    icon: 'Connection',
    route: '/domains/engineering',
    modules: [
      {
        code: 'INT',
        name: '数据集成',
        description: '接入多源数据源并采集表结构与字段，形成统一的技术元数据视图。',
        icon: 'Connection',
        route: '/metadata',
        status: 'AVAILABLE'
      },
      {
        code: 'DEV',
        name: '数据开发',
        description: '面向数仓分层的表结构设计与开发协同，当前暂未开放独立工作台。',
        icon: 'Cpu',
        route: '/metadata',
        status: 'PLANNED'
      },
      {
        code: 'SCH',
        name: '调度中心',
        description: '统一编排数据任务依赖与调度周期，监控任务运行状态与失败重试。',
        icon: 'Timer',
        route: '/operations',
        status: 'PLANNED'
      },
      {
        code: 'STO',
        name: '数据存储与计算',
        description: '管理存储资源、计算引擎与分层策略，平衡性能、成本与合规要求。',
        icon: 'Box',
        route: '/storage',
        status: 'PLANNED'
      }
    ]
  },
  {
    code: 'governance',
    name: '数据治理',
    description: '围绕质量、血缘、安全与生命周期建立全链路治理闭环。',
    icon: 'Lock',
    route: '/domains/governance',
    modules: [
      {
        code: 'DQ',
        name: '数据质量',
        description: '配置质量规则并执行稽核，跟踪通过率、异常样例与整改闭环。',
        icon: 'Histogram',
        route: '/quality',
        status: 'AVAILABLE'
      },
      {
        code: 'LIN',
        name: '数据血缘',
        description: '还原表级与字段级加工链路，快速定位影响范围与问题根因。',
        icon: 'Share',
        route: '/lineage',
        status: 'AVAILABLE'
      },
      {
        code: 'SEC',
        name: '数据安全',
        description: '数据分级分类、脱敏策略与访问审批，守住数据使用安全边界。',
        icon: 'Lock',
        route: '/security',
        status: 'AVAILABLE'
      },
      {
        code: 'LIF',
        name: '数据生命周期',
        description: '冷热分层、归档与清理策略管理，让数据在正确阶段发挥价值。',
        icon: 'Odometer',
        route: '/lifecycle',
        status: 'AVAILABLE'
      }
    ]
  },
  {
    code: 'analytics',
    name: '指标与分析',
    description: '统一指标口径并构建报表体系，让数据洞察可复用、可追溯。',
    icon: 'TrendCharts',
    route: '/domains/analytics',
    modules: [
      {
        code: 'MET',
        name: '指标中心',
        description: '统一管理原子指标、派生指标与业务口径，支撑跨部门一致分析。',
        icon: 'TrendCharts',
        route: '/metrics',
        status: 'AVAILABLE'
      },
      {
        code: 'RPT',
        name: '报表分析',
        description: '集中管理报表与看板，支持自助分析与多主题数据呈现。',
        icon: 'DataAnalysis',
        route: '/reports',
        status: 'AVAILABLE'
      }
    ]
  },
  {
    code: 'service',
    name: '数据服务与市场',
    description: '把数据封装为可复用服务，通过数据市场促进供需对接与价值流通。',
    icon: 'Shop',
    route: '/domains/service',
    modules: [
      {
        code: 'DS',
        name: '数据服务',
        description: '将数据能力封装为 API 或查询服务，统一管理发布、授权与调用。',
        icon: 'Link',
        route: '/data-services',
        status: 'PLANNED'
      },
      {
        code: 'MKT',
        name: '数据市场',
        description: '面向业务提供数据产品浏览、申请与订阅，促进数据供需高效匹配。',
        icon: 'Shop',
        route: '/market',
        status: 'PLANNED'
      }
    ]
  },
  {
    code: 'ai',
    name: 'AI 与知识',
    description: '汇聚 AI 工作台、知识中心与模型网关，让智能能力融入数据工作流。',
    icon: 'MagicStick',
    route: '/domains/ai',
    modules: [
      {
        code: 'KNO',
        name: '知识中心',
        description: '沉淀数据标准、指标口径与治理经验，形成可检索的组织知识资产。',
        icon: 'Reading',
        route: '/knowledge',
        status: 'PLANNED'
      },
      {
        code: 'AI',
        name: 'AI 工作台',
        description: 'AI 任务队列、结果审核与知识沉淀，智能结果先审核再写回。',
        icon: 'MagicStick',
        route: '/agent',
        status: 'AVAILABLE'
      },
      {
        code: 'MGW',
        name: '模型网关',
        description: '统一配置模型接入、路由策略与调用参数，管理 AI 模型能力供给。',
        icon: 'Platform',
        route: '/system-config',
        status: 'AVAILABLE'
      }
    ]
  },
  {
    code: 'platform',
    name: '平台与运维',
    description: '提供任务、运维、系统配置与可观测能力，保障平台稳定高效运行。',
    icon: 'Monitor',
    route: '/domains/platform',
    modules: [
      {
        code: 'TC',
        name: '任务中心',
        description: '集中查看 AI 与数据任务队列、执行进度和处理结果。',
        icon: 'Tickets',
        route: '/agent',
        status: 'AVAILABLE'
      },
      {
        code: 'OPS',
        name: '运维中心',
        description: '处理数据申请、质量问题与变更工单，跟踪 SLA 与处理过程。',
        icon: 'Tools',
        route: '/operations',
        status: 'AVAILABLE'
      },
      {
        code: 'SYS',
        name: '系统管理',
        description: '维护导航菜单、系统参数、数据字典与数据源类型等平台配置。',
        icon: 'Setting',
        route: '/system-config',
        status: 'AVAILABLE'
      },
      {
        code: 'OBS',
        name: '可观测与告警',
        description: '统一采集指标、日志与链路数据，配置告警规则并跟踪处置结果。',
        icon: 'Bell',
        route: '/observability',
        status: 'PLANNED'
      }
    ]
  }
]

export const portalDomainMap: ReadonlyMap<string, PortalDomain> = new Map(
  portalDomains.map(domain => [domain.code, domain])
)

/** 按编码获取能力域，找不到时返回 undefined */
export function getPortalDomain(code?: string | null): PortalDomain | undefined {
  if (!code) return undefined
  return portalDomainMap.get(code)
}

/** 模块总数（8 个能力域，共 23 个模块） */
export const portalModuleTotal = portalDomains.reduce((total, domain) => total + domain.modules.length, 0)

/** 已上线模块数 */
export const portalAvailableTotal = portalDomains.reduce(
  (total, domain) => total + domain.modules.filter(module => module.status === 'AVAILABLE').length,
  0
)

/** 规划中模块数 */
export const portalPlannedTotal = portalModuleTotal - portalAvailableTotal
