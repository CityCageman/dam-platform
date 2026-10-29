const { Client } = require('pg')
async function main() {
  const c = new Client({ host: '10.162.3.16', port: 11001, database: 'damplatform', user: 'dam', password: 'Cuhkszmc_2026' })
  await c.connect()

  // 1. 确认 schema 是否存在
  const r = await c.query(`SELECT nspname FROM pg_namespace WHERE nspname = 'PROD.CIS_Model'`)
  console.log('[检查] PROD.CIS_Model schema 存在: ' + (r.rows.length > 0))

  // 2. 执行 COMMENT
  try {
    await c.query(`COMMENT ON SCHEMA "PROD.CIS_Model" IS 'SRC 业务贴源区（模拟 HIS 落地结构，生产为 InterSystems IRIS）'`)
    console.log('[OK] COMMENT ON SCHEMA "PROD.CIS_Model"')
  } catch (e) { console.log('[FAIL] COMMENT:', e.message) }

  // 3. 验证注释
  const r2 = await c.query(`SELECT obj_description(oid) AS comment FROM pg_namespace WHERE nspname = 'PROD.CIS_Model'`)
  console.log('[验证] 注释 = ' + (r2.rows[0] && r2.rows[0].comment ? r2.rows[0].comment : '(空)'))
  await c.end()
}
main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })
