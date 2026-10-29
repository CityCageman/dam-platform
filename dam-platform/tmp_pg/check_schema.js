const { Client } = require('pg')

async function main() {
  const client = new Client({
    host: '10.162.3.16',
    port: 11001,
    database: 'damplatform',
    user: 'dam',
    password: 'Cuhkszmc_2026',
    connectionTimeoutMillis: 15000
  })
  await client.connect()

  // 1. 所有非系统 schema
  const schemaRes = await client.query(`
    SELECT nspname AS schema
    FROM pg_namespace
    WHERE nspname NOT LIKE 'pg_%' AND nspname <> 'information_schema'
    ORDER BY nspname`)
  console.log('=== 现有 schema ===')
  for (const s of schemaRes.rows) console.log('  ' + s.schema)

  // 2. 每个 schema 的表清单
  const tableRes = await client.query(`
    SELECT table_schema, table_name
    FROM information_schema.tables
    WHERE table_type = 'BASE TABLE'
      AND table_schema NOT LIKE 'pg_%' AND table_schema <> 'information_schema'
    ORDER BY table_schema, table_name`)
  console.log('\n=== 现有表清单（' + tableRes.rows.length + ' 张）===')
  let cur = ''
  for (const t of tableRes.rows) {
    if (t.table_schema !== cur) { cur = t.table_schema; console.log('\n[' + cur + ']') }
    console.log('  ' + t.table_name)
  }

  await client.end()
}

main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })
