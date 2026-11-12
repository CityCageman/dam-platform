/** 报表下钻的通用判断和参数映射，避免 AgentChat / ReportCenter 各维护一套。 */

export function isDrillable(drillSql: string | undefined, col: string, row: any): boolean {
  if (!drillSql) return false
  const value = row?.[col]
  if (value == null || value === '') return false
  return /人数|数量|人次|例数|工作量|count|total|num/i.test(col) && !isNaN(Number(value))
}

export function drillParamValue(row: any, param: string): any {
  if (row?.[param] != null) return row[param]
  const keys = Object.keys(row || {})
  const lower = param.toLowerCase()
  const exact = keys.find(k => k.toLowerCase() === lower)
  if (exact) return row[exact]
  if (/date/.test(lower)) return row[keys.find(k => /日期|时间|date/i.test(k)) || '']
  if (/dept/.test(lower)) return row[keys.find(k => /科室|部门|dept/i.test(k)) || '']
  if (/count|num|total/.test(lower)) return row[keys.find(k => /人数|数量|人次|例数|count|total/i.test(k)) || '']
  return undefined
}

export function buildDrillParams(drillSql: string, row: any): { params: Record<string, any>; missing: string[] } {
  const names = [...String(drillSql).matchAll(/(?<!:):([A-Za-z_][A-Za-z0-9_]*)/g)].map(m => m[1])
  const params: Record<string, any> = {}
  const missing: string[] = []
  for (const name of names) {
    const value = drillParamValue(row, name)
    if (value == null || value === '') missing.push(name)
    else params[name] = value
  }
  return { params, missing }
}
