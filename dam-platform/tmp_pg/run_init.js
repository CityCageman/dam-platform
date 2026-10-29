const fs = require('fs')
const { Client } = require('pg')

const sqlPath = 'D:/Project Management/dam-platform/dam-platform/db/初始化.sql'

;(async () => {
  const sql = fs.readFileSync(sqlPath, 'utf8')
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
    await c.query(sql)
    await c.query('COMMIT')
    console.log('IMPORT_OK')
  } catch (e) {
    await c.query('ROLLBACK')
    console.error('IMPORT_ERR', e.message)
    await c.end()
    process.exit(1)
  }

  const checks = [
    ['sys_role', 'dam_sys.sys_role'],
    ['sys_user', 'dam_sys.sys_user'],
    ['sys_user_role', 'dam_sys.sys_user_role'],
    ['sys_menu', 'dam_sys.sys_menu'],
    ['sys_role_menu', 'dam_sys.sys_role_menu'],
    ['sys_config', 'dam_sys.sys_config'],
    ['sys_dict_type', 'dam_sys.sys_dict_type'],
    ['sys_dict_data', 'dam_sys.sys_dict_data'],
    ['source_system', 'dam_metadata.source_system'],
    ['metadata_category', 'dam_metadata.metadata_category'],
    ['asset_category', 'dam_asset.asset_category']
  ]
  for (const [name, table] of checks) {
    try {
      const r = await c.query('SELECT count(*) AS n FROM ' + table)
      console.log(name.toUpperCase() + '_COUNT', r.rows[0].n)
    } catch (e) {
      console.log(name.toUpperCase() + '_ERR', e.message)
    }
  }

  const cfg = await c.query('SELECT config_type, count(*) AS n FROM dam_sys.sys_config GROUP BY config_type ORDER BY config_type')
  console.log('CONFIG_BY_TYPE', JSON.stringify(cfg.rows))

  await c.end()
})().catch(e => { console.error('ERR', e.message); process.exit(1) })
