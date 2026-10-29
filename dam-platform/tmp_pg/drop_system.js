const { Client } = require('pg')
async function main() {
  const c = new Client({ host: '10.162.3.16', port: 11001, database: 'damplatform', user: 'dam', password: 'Cuhkszmc_2026' })
  await c.connect()

  // 删除前统计 dam_system 各表数据量（供报告）
  const r = await c.query(`SELECT table_name, (xpath('/row/c/text()', query_to_xml('SELECT count(*) AS c FROM dam_system.'||quote_ident(table_name), true, false, '')))[1]::text::int AS cnt
    FROM information_schema.tables WHERE table_schema='dam_system' AND table_type='BASE TABLE' ORDER BY table_name`)
  console.log('=== dam_system 各表数据量 ===')
  let total = 0
  for (const x of r.rows) {
    const n = Number(x.cnt)
    total += n
    if (n > 0) console.log('  ' + x.table_name + ' = ' + n)
  }
  console.log('  总计 ' + total + ' 行')

  // 删除 dam_system
  try { await c.query('DROP SCHEMA IF EXISTS dam_system CASCADE'); console.log('\n[OK] DROP SCHEMA dam_system') }
  catch (e) { console.log('[FAIL] ' + e.message) }

  // 验证
  const r2 = await c.query("SELECT count(*) AS n FROM information_schema.tables WHERE table_type='BASE TABLE' AND table_schema NOT LIKE 'pg_%' AND table_schema <> 'information_schema'")
  console.log('[验证] 当前总表数 = ' + r2.rows[0].n)
  const r3 = await c.query("SELECT table_schema, count(*) n FROM information_schema.tables WHERE table_type='BASE TABLE' AND table_schema NOT LIKE 'pg_%' AND table_schema <> 'information_schema' GROUP BY table_schema ORDER BY table_schema")
  for (const x of r3.rows) console.log('  ' + x.table_schema + ' | ' + x.n)
  await c.end()
}
main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })
