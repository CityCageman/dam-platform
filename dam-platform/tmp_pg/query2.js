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

  // 1. 数据源信息
  const dsRes = await client.query(
    `SELECT id, ds_name, ds_type, ds_ip, ds_port, jdbc_url, username, driver_class, status
     FROM dam_metadata.metadata_datasource WHERE id = 1`)
  console.log('=== 数据源1 ===')
  console.log(JSON.stringify(dsRes.rows, null, 2))

  // 2. RB_Resource 完整字段（含更多元信息）
  const colRes = await client.query(
    `SELECT column_name, column_type, column_length, column_comment, is_primary_key, is_foreign_key, ordinal_position
     FROM dam_metadata.metadata_column WHERE table_id = 16954 ORDER BY ordinal_position`)
  console.log('\n=== RB_Resource 字段 ===')
  for (const c of colRes.rows) {
    console.log(`${c.ordinal_position}\t${c.column_name}\t${c.column_type}\t${c.column_comment || '(无)'}${c.is_primary_key ? '\tPK' : ''}`)
  }

  await client.end()
}

main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })
