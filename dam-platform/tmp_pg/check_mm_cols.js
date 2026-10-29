const { Client } = require('pg')

;(async () => {
  const c = new Client({
    host: '10.162.3.16',
    port: 11001,
    database: 'damplatform',
    user: 'dam',
    password: 'Cuhkszmc_2026',
    connectionTimeoutMillis: 15000
  })
  await c.connect()
  const cols = await c.query(
    "SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns WHERE table_schema='dam_metadata' AND table_name='metadata_management' ORDER BY ordinal_position"
  )
  console.log(JSON.stringify(cols.rows, null, 2))
  await c.end()
})().catch(e => { console.error('ERR', e.message); process.exit(1) })
