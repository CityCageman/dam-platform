const { Client } = require('pg')
async function main() {
  const c = new Client({ host: '10.162.3.16', port: 11001, database: 'damplatform', user: 'dam', password: 'Cuhkszmc_2026' })
  await c.connect()

  // 1. DROP 废弃 schema
  for (const s of ['"PROD.CIS_Model"', '"PROD"']) {
    try { await c.query('DROP SCHEMA IF EXISTS ' + s + ' CASCADE'); console.log('[OK] DROP SCHEMA ' + s) }
    catch (e) { console.log('[FAIL] DROP ' + s + ': ' + e.message) }
  }

  // 2. 逐表 ANALYZE
  for (const t of ['hospital_demo.biz_patient_master', 'hospital_demo.biz_encounter', 'hospital_demo.biz_diagnosis']) {
    try { await c.query('ANALYZE ' + t); console.log('[OK] ANALYZE ' + t) }
    catch (e) { console.log('[FAIL] ANALYZE ' + t + ': ' + e.message) }
  }

  // 3. 验证：当前表总数 + PROD 残留
  const r = await c.query("SELECT count(*) AS n FROM information_schema.tables WHERE table_type='BASE TABLE' AND table_schema NOT LIKE 'pg_%' AND table_schema <> 'information_schema'")
  console.log('\n[验证] 当前总表数 = ' + r.rows[0].n)
  const r2 = await c.query("SELECT count(*) AS n FROM information_schema.tables WHERE table_schema ILIKE '%PROD%' OR table_schema ILIKE '%CIS%'")
  console.log('[验证] PROD/CIS 残留表数 = ' + r2.rows[0].n)
  await c.end()
}
main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })
