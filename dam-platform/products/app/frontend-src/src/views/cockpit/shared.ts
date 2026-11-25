/** 驾驶舱共用的数据兼容与展示工具：接口缺失/字段变更时统一降级为空态，避免白屏 */

/** request 返回 { code, data, message }；部分接口可能直接返回数据 */
export function unwrap(res: any): any {
  return res && typeof res === 'object' && 'data' in res ? res.data : res
}

/** 兼容 PageResult.records、rows/list 与裸数组 */
export function recordsOf(res: any): any[] {
  const data = unwrap(res)
  if (Array.isArray(data)) return data
  if (!data || typeof data !== 'object') return []
  const rows = data.records ?? data.rows ?? data.list ?? data.items
  return Array.isArray(rows) ? rows : []
}

export function totalOf(res: any): number {
  const data = unwrap(res)
  const total = Number(data?.total)
  if (Number.isFinite(total)) return total
  return recordsOf(res).length
}

export function settledValue(result: PromiseSettledResult<any>): any | undefined {
  return result.status === 'fulfilled' ? result.value : undefined
}

export function toNumber(value: any, fallback = 0): number {
  const num = Number(value)
  return Number.isFinite(num) ? num : fallback
}

export function display(value: any, fallback = '—'): string {
  if (value === null || value === undefined || value === '') return fallback
  return String(value)
}

/** 比率展示：后端可能返回 0-1 或 0-100 */
export function percentText(value: any, fallback = '—'): string {
  const num = toNumber(value, NaN)
  if (!Number.isFinite(num)) return fallback
  const normalized = num > 0 && num <= 1 ? num * 100 : num
  return `${normalized.toFixed(1)}%`
}

export function normalizeStatus(value: any): string {
  return String(value ?? '').trim().toUpperCase()
}

export function countStatus(rows: any[], status: string): number {
  const target = normalizeStatus(status)
  return rows.filter(row => normalizeStatus(row?.status) === target).length
}

export function sumBy(rows: any[], pick: (row: any) => any): number {
  return rows.reduce((total, row) => total + toNumber(pick(row), 0), 0)
}

/** Element Plus 标签类型 */
export function statusTagType(status: any): 'success' | 'danger' | 'warning' | 'info' {
  const value = normalizeStatus(status)
  if (['UP', 'SUCCESS', 'SUCCEEDED', 'ENABLED', 'PUBLISHED', 'ONLINE', 'COMPLETED', 'NORMAL', 'PASS'].includes(value)) return 'success'
  if (['DOWN', 'FAILED', 'FAILURE', 'ERROR', 'OFFLINE'].includes(value)) return 'danger'
  if (['RUNNING', 'PENDING', 'WAITING', 'PROCESSING', 'DRAFT', 'QUEUED'].includes(value)) return 'warning'
  return 'info'
}

/** 中文状态文案（未知状态原样展示） */
const STATUS_TEXT: Record<string, string> = {
  UP: '正常',
  DOWN: '异常',
  SUCCESS: '成功',
  SUCCEEDED: '成功',
  FAILED: '失败',
  FAILURE: '失败',
  ERROR: '失败',
  RUNNING: '执行中',
  PENDING: '待执行',
  WAITING: '等待中',
  PROCESSING: '处理中',
  CANCELLED: '已取消',
  ENABLED: '启用',
  DISABLED: '停用',
  PUBLISHED: '已发布',
  DRAFT: '草稿',
  OFFLINE: '已下线',
  ONLINE: '在线',
  COMPLETED: '已完成',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  NORMAL: '正常',
  PASS: '通过'
}

export function statusText(status: any): string {
  if (status === null || status === undefined || status === '') return '未知'
  const value = normalizeStatus(status)
  return STATUS_TEXT[value] || String(status)
}

/** 时间展示：兼容 ISO 字符串与常见后端格式 */
export function formatDateTime(value: any): string {
  if (value === null || value === undefined || value === '') return '—'
  const text = String(value).replace('T', ' ')
  return text.length > 19 ? text.slice(0, 19) : text
}

/** 当前时间（本地）文案 */
export function nowText(): string {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

/** 安全取数组字段 */
export function arrayOf(value: any): any[] {
  return Array.isArray(value) ? value : []
}

/** 取前 n 条 */
export function take<T>(rows: T[], n: number): T[] {
  return rows.slice(0, Math.max(0, n))
}
