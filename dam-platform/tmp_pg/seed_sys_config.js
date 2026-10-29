const { Client } = require('pg')

// 配置中心各类型种子数据（config_type, config_code, config_name, config_value, description, sort_order）
const data = [
  // navigation（导航配置，兼容查看；实际侧边栏以 sys_menu 为准）
  ['navigation', 'nav_dashboard', '运营看板', '/dashboard', '运营看板导航', 1],
  ['navigation', 'nav_catalog', '资产目录', '/catalog', '资产目录导航', 2],
  ['navigation', 'nav_metadata', '元数据管理', '/metadata', '元数据管理导航', 3],
  ['navigation', 'nav_model', '模型管理', '/models', '模型管理导航', 4],
  ['navigation', 'nav_lineage', '数据血缘', '/lineage', '数据血缘导航', 5],
  ['navigation', 'nav_standard', '数据标准', '/standard', '数据标准导航', 6],
  ['navigation', 'nav_quality', '数据质量', '/quality', '数据质量导航', 7],
  ['navigation', 'nav_security', '安全管控', '/security', '安全管控导航', 8],
  ['navigation', 'nav_lifecycle', '生命周期', '/lifecycle', '生命周期导航', 9],
  ['navigation', 'nav_operations', '工单中心', '/operations', '工单中心导航', 10],
  ['navigation', 'nav_agent', '智能助手', '/agent', '智能助手导航', 11],

  // security_level 安全等级
  ['security_level', 'LEVEL_1', '1级：公开数据', 'public', '对外公开数据', 1],
  ['security_level', 'LEVEL_2', '2级：内部一般数据', 'internal', '内部一般数据', 2],
  ['security_level', 'LEVEL_3', '3级：内部敏感数据', 'confidential', '内部敏感数据', 3],
  ['security_level', 'LEVEL_4', '4级：重要数据/个人信息', 'restricted', '重要数据/个人信息', 4],
  ['security_level', 'LEVEL_5', '5级：核心数据', 'critical', '核心数据', 5],

  // asset_type 资产类型
  ['asset_type', 'table', '数据表', 'table', '关系型数据表', 1],
  ['asset_type', 'view', '视图', 'view', '数据库视图', 2],
  ['asset_type', 'file', '文件', 'file', '文件类资产', 3],
  ['asset_type', 'api', 'API', 'api', '接口类资产', 4],

  // asset_status 资产状态
  ['asset_status', 'published', '已发布', '1', '已发布状态', 1],
  ['asset_status', 'draft', '草稿', '2', '草稿状态', 2],
  ['asset_status', 'offline', '已下线', '3', '已下线状态', 3],

  // sensitive_level 敏感级别
  ['sensitive_level', 'LOW', '低', 'low', '低敏感', 1],
  ['sensitive_level', 'MEDIUM', '中', 'medium', '中敏感', 2],
  ['sensitive_level', 'HIGH', '高', 'high', '高敏感', 3],

  // tech_asset_type 技术元数据类型
  ['tech_asset_type', 'TABLE', '数据表', 'TABLE', '技术元数据-表', 1],
  ['tech_asset_type', 'VIEW', '视图', 'VIEW', '技术元数据-视图', 2],
  ['tech_asset_type', 'FILE', '文件', 'FILE', '技术元数据-文件', 3],
  ['tech_asset_type', 'API', 'API', 'API', '技术元数据-接口', 4],
  ['tech_asset_type', 'REPORT', '报表', 'REPORT', '技术元数据-报表', 5]
]

;(async () => {
  const c = new Client({
    host: '10.162.3.16',
    port: 11001,
    database: 'damplatform',
    user: 'dam',
    password: 'Cuhkszmc_2026',
    connectionTimeoutMillis: 15000
  })
  await c.connect()
  try {
    await c.query('BEGIN')
    for (const [type, code, name, value, desc, sort] of data) {
      await c.query(
        `INSERT INTO dam_sys.sys_config (config_type, config_code, config_name, config_value, description, sort_order, status)
         SELECT $1, $2, $3, $4, $5, $6, 1
         WHERE NOT EXISTS (
           SELECT 1 FROM dam_sys.sys_config WHERE config_type = $1 AND config_code = $2
         )`,
        [type, code, name, value, desc, sort]
      )
    }
    await c.query('COMMIT')
  } catch (e) {
    await c.query('ROLLBACK')
    throw e
  }

  const t = await c.query(
    'SELECT config_type, count(*) AS n FROM dam_sys.sys_config GROUP BY config_type ORDER BY config_type'
  )
  console.log('CONFIG_TYPES', JSON.stringify(t.rows))
  await c.end()
})().catch(e => { console.error('ERR', e.message); process.exit(1) })
