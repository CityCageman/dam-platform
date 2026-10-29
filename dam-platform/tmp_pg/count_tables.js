const { Client } = require('pg')
async function main() {
  const c = new Client({ host: '10.162.3.16', port: 11001, database: 'damplatform', user: 'dam', password: 'Cuhkszmc_2026' })
  await c.connect()
  const r = await c.query("SELECT table_schema, count(*) AS n FROM information_schema.tables WHERE table_type='BASE TABLE' AND table_schema NOT LIKE 'pg_%' AND table_schema <> 'information_schema' GROUP BY table_schema ORDER BY table_schema")
  console.log('schema | 表数')
  for (const x of r.rows) console.log(x.table_schema + ' | ' + x.n)
  const total = r.rows.reduce((s, x) => s + Number(x.n), 0)
  console.log('合计 | ' + total)
  await c.end()
}
main().catch(e => { console.error(e.message); process.exit(1) })
