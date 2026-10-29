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

  // 1. 查 RB_Resource 相关表
  const tableRes = await client.query(
    `SELECT id, table_name, table_comment, datasource_id, schema_id, table_type, subject_domain
     FROM dam_metadata.metadata_table
     WHERE table_name ILIKE '%RB_Resource%' OR table_name ILIKE '%resource%'
     ORDER BY id`)
  console.log('=== 匹配的表 ===')
  console.log(JSON.stringify(tableRes.rows, null, 2))

  // 2. 查字段
  for (const t of tableRes.rows) {
    const colRes = await client.query(
      `SELECT column_name, column_type, column_comment, is_primary_key, is_foreign_key, ordinal_position
       FROM dam_metadata.metadata_column WHERE table_id = $1 ORDER BY ordinal_position`, [t.id])
    console.log(`\n=== 字段: ${t.table_name} (table_id=${t.id}) ===`)
    for (const c of colRes.rows) {
      const tag = (c.is_primary_key ? ' [PK]' : '') + (c.is_foreign_key ? ' [FK]' : '')
      console.log(`${c.column_name}\t| ${c.column_type}\t| ${c.column_comment || '(无注释)'}${tag}`)
    }
  }

  await client.end()
}

main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })
