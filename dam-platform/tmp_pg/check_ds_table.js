const { Client } = require('pg')
async function main() {
  const c = new Client({ host: '10.162.3.16', port: 11001, database: 'damplatform', user: 'dam', password: 'Cuhkszmc_2026' })
  await c.connect()
  const r = await c.query(`SELECT column_name, data_type, character_maximum_length, is_nullable, column_default
    FROM information_schema.columns WHERE table_schema='dam_metadata' AND table_name='metadata_datasource'
    ORDER BY ordinal_position`)
  console.log('=== dam_metadata.metadata_datasource 列结构 ===')
  for (const x of r.rows) {
    console.log(`${x.column_name} | ${x.data_type}${x.character_maximum_length ? '(' + x.character_maximum_length + ')' : ''} | null=${x.is_nullable} | default=${x.column_default || ''}`)
  }
  await c.end()
}
main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })
