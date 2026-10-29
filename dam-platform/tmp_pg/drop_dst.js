const { Client } = require('pg')
async function main() {
  const c = new Client({ host: '10.162.3.16', port: 11001, database: 'damplatform', user: 'dam', password: 'Cuhkszmc_2026' })
  await c.connect()
  try {
    await c.query('DROP TABLE IF EXISTS dam_metadata.dam_data_source_type CASCADE')
    console.log('[OK] DROP TABLE dam_metadata.dam_data_source_type')
  } catch (e) { console.log('[FAIL] ' + e.message) }
  const r = await c.query(`SELECT count(*) AS n FROM information_schema.tables WHERE table_schema='dam_metadata' AND table_name='dam_data_source_type'`)
  console.log('[验证] 表残留 = ' + r.rows[0].n)
  const r2 = await c.query("SELECT count(*) AS n FROM information_schema.tables WHERE table_type='BASE TABLE' AND table_schema NOT LIKE 'pg_%' AND table_schema <> 'information_schema'")
  console.log('[验证] 当前总表数 = ' + r2.rows[0].n)
  await c.end()
}
main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })
