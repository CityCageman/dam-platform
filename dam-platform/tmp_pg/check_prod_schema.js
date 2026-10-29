const { Client } = require('pg')
async function main() {
  const c = new Client({ host: '10.162.3.16', port: 11001, database: 'damplatform', user: 'dam', password: 'Cuhkszmc_2026' })
  await c.connect()
  // 列出所有含 PROD/CIS/prod/cis 的 schema 实际名
  const r = await c.query(`SELECT nspname FROM pg_namespace WHERE nspname ILIKE '%PROD%' OR nspname ILIKE '%CIS%' OR nspname ILIKE '%prod%' ORDER BY nspname`)
  console.log('=== pg_namespace 中含 PROD/CIS 的 schema ===')
  for (const x of r.rows) console.log('  [' + x.nspname + ']')

  // information_schema.tables 里 PROD 相关
  const r2 = await c.query(`SELECT DISTINCT table_schema FROM information_schema.tables WHERE table_schema ILIKE '%PROD%' OR table_schema ILIKE '%CIS%'`)
  console.log('=== information_schema 中 PROD/CIS schema ===')
  for (const x of r2.rows) console.log('  [' + x.table_schema + ']')

  await c.end()
}
main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })
