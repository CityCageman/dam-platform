const { Client } = require('pg')

const dirs = [
  { name: '总览', icon: 'DataBoard', sort: 1 },
  { name: '数据资产', icon: 'Folder', sort: 2 },
  { name: '治理能力', icon: 'Setting', sort: 3 },
  { name: '平台运营', icon: 'Connection', sort: 4 }
]

const children = {
  '总览': [
    { name: '运营看板', path: '/dashboard', icon: 'DataAnalysis', sort: 1 }
  ],
  '数据资产': [
    { name: '资产目录', path: '/catalog', icon: 'Folder', sort: 1 },
    { name: '元数据管理', path: '/metadata', icon: 'DataBoard', sort: 2 },
    { name: '模型管理', path: '/models', icon: 'Collection', sort: 3 },
    { name: '数据血缘', path: '/lineage', icon: 'Connection', sort: 4 }
  ],
  '治理能力': [
    { name: '数据标准', path: '/standard', icon: 'Document', sort: 1 },
    { name: '数据质量', path: '/quality', icon: 'List', sort: 2 },
    { name: '安全管控', path: '/security', icon: 'Lock', sort: 3 },
    { name: '生命周期', path: '/lifecycle', icon: 'Timer', sort: 4 }
  ],
  '平台运营': [
    { name: '工单中心', path: '/operations', icon: 'Tickets', sort: 1 },
    { name: '智能助手', path: '/agent', icon: 'ChatDotRound', sort: 2 }
  ]
}

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

  // 当前库缺失 sys_menu，先建表（幂等）
  await c.query(`
    CREATE TABLE IF NOT EXISTS dam_sys.sys_menu (
      id BIGSERIAL PRIMARY KEY,
      menu_name VARCHAR(100) NOT NULL,
      menu_type VARCHAR(20) NOT NULL DEFAULT 'C',
      parent_id BIGINT,
      tree_level INTEGER DEFAULT 1,
      path VARCHAR(200),
      component VARCHAR(200),
      redirect VARCHAR(200),
      icon VARCHAR(100),
      sort_order INTEGER DEFAULT 0,
      visible SMALLINT NOT NULL DEFAULT 1,
      status SMALLINT NOT NULL DEFAULT 1,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      created_by VARCHAR(100) NOT NULL DEFAULT '系统',
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_by VARCHAR(100) NOT NULL DEFAULT '系统',
      deleted_at TIMESTAMP
    )
  `)
  await c.query('CREATE INDEX IF NOT EXISTS idx_sys_menu_parent ON dam_sys.sys_menu(parent_id)')
  await c.query('CREATE INDEX IF NOT EXISTS idx_sys_menu_path ON dam_sys.sys_menu(path)')

  try {
    await c.query('BEGIN')

    // 1. 目录（大模块，parent_id 为空）
    for (const d of dirs) {
      await c.query(
        `INSERT INTO dam_sys.sys_menu (menu_name, menu_type, parent_id, tree_level, icon, sort_order, visible, status)
         SELECT $1, 'M', NULL, 1, $2, $3, 1, 1
         WHERE NOT EXISTS (
           SELECT 1 FROM dam_sys.sys_menu WHERE menu_name = $1 AND parent_id IS NULL AND deleted_at IS NULL
         )`,
        [d.name, d.icon, d.sort]
      )
    }

    // 2. 页面菜单（parent_id 指向目录）
    for (const [dirName, items] of Object.entries(children)) {
      const dr = await c.query(
        'SELECT id FROM dam_sys.sys_menu WHERE menu_name = $1 AND parent_id IS NULL AND deleted_at IS NULL LIMIT 1',
        [dirName]
      )
      const pid = dr.rows[0].id
      for (const it of items) {
        await c.query(
          `INSERT INTO dam_sys.sys_menu (menu_name, menu_type, parent_id, tree_level, path, icon, sort_order, visible, status)
           SELECT $1, 'C', $2, 2, $3, $4, $5, 1, 1
           WHERE NOT EXISTS (
             SELECT 1 FROM dam_sys.sys_menu WHERE path = $3 AND deleted_at IS NULL
           )`,
          [it.name, pid, it.path, it.icon, it.sort]
        )
      }
    }

    await c.query('COMMIT')
  } catch (e) {
    await c.query('ROLLBACK')
    throw e
  }

  const res = await c.query(
    'SELECT id, menu_name, parent_id, path, sort_order FROM dam_sys.sys_menu ORDER BY parent_id NULLS FIRST, sort_order'
  )
  console.log('MENU_COUNT', res.rows.length)
  console.log('MENU_ROWS', JSON.stringify(res.rows))
  await c.end()
})().catch(e => { console.error('ERR', e.message); process.exit(1) })
