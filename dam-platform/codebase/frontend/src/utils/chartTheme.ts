/** 图表主题：所有图表颜色从全局 CSS 设计令牌读取，避免页面内散落硬编码色值。 */
export interface ChartTheme {
  primary: string
  primaryDark: string
  accent: string
  gold: string
  green: string
  danger: string
  text: string
  textLight: string
  border: string
  split: string
  palette: string[]
}

function cssVar(name: string, fallback: string): string {
  if (typeof document === 'undefined') return fallback
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

export function getChartTheme(): ChartTheme {
  const primary = cssVar('--med-primary', '#72156A')
  const accent = cssVar('--med-accent', '#2FA6A1')
  const gold = cssVar('--med-gold', '#D99A1C')
  const green = cssVar('--med-green', '#5BAE47')
  const danger = cssVar('--med-danger', '#D9534F')
  return {
    primary,
    primaryDark: cssVar('--med-primary-dark', '#560E50'),
    accent,
    gold,
    green,
    danger,
    text: cssVar('--med-text-secondary', '#655D68'),
    textLight: cssVar('--med-text-light', '#9B929F'),
    border: cssVar('--med-border', '#EAE2EA'),
    split: cssVar('--med-border-light', '#F3EDF3'),
    palette: [primary, accent, gold, green, danger]
  }
}
